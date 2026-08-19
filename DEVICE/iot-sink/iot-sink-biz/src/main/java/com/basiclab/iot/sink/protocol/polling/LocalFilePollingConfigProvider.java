package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigErrorCode;
import com.basiclab.iot.sink.polling.CollectorConfigObservation;
import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorConfigStatus;
import com.basiclab.iot.sink.polling.PollingConfigProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-only collector ConfigSnapshot provider.  The lock is a Java
 * FileChannel record lock, the same POSIX record-lock family used by NODE's
 * fcntl.lockf implementation.
 */
public final class LocalFilePollingConfigProvider implements PollingConfigProvider {
    public static final int LINUX_CONFIG_DIRECTORY_MODE = 02770;
    public static final int LINUX_CONFIG_FILE_MODE = 0660;
    public static final String ACTIVE_FILE = "active.json";
    public static final String DESIRED_FILE = "desired.json";
    public static final String OBSERVED_FILE = "observed.json";
    public static final String LOCK_FILE = ".state.lock";

    private static final Pattern HISTORY_FILE = Pattern.compile("^(\\d+)-([0-9a-f]{8,64})\\.json$");
    private static final Pattern OWN_TEMP_FILE = Pattern.compile(
            "^\\.[0-9]+-[0-9a-f]{8,64}\\.json\\.[0-9a-f]{32}\\.(?:tmp|restore)$");
    private static final Set<String> FORMAL_FILES = Set.of(ACTIVE_FILE, DESIRED_FILE, OBSERVED_FILE, LOCK_FILE);
    private final Path configDirectory;
    private final String workloadId;
    private final CollectorConfigSnapshotCodec codec;

    public LocalFilePollingConfigProvider(Path configDirectory, String workloadId) {
        this(configDirectory, workloadId, new CollectorConfigSnapshotCodec());
    }

    public LocalFilePollingConfigProvider(Path configDirectory, String workloadId,
                                          CollectorConfigSnapshotCodec codec) {
        this.configDirectory = normalize(configDirectory);
        this.workloadId = requireWorkload(workloadId);
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public Path configDirectory() {
        return configDirectory;
    }

    public String workloadId() {
        return workloadId;
    }

    /** Read active.json under the shared state lock. */
    @Override
    public Optional<CollectorConfigSnapshot> current() {
        return withLock(false, () -> readPayload(ACTIVE_FILE).map(DecodedState::snapshot));
    }

    /** Read desired.json under the shared state lock, restricted to a version. */
    @Override
    public Optional<CollectorConfigSnapshot> candidate(long version) {
        return withLock(false, () -> readPayload(DESIRED_FILE)
                .filter(value -> value.snapshot().configVersion() == version)
                .map(DecodedState::snapshot));
    }

    public StateSnapshot readState() {
        return withLock(false, this::readStateLocked);
    }

    /**
     * Apply the current desired candidate in the frozen order:
     * history raw bytes -> active persistence -> graph -> observed.
     */
    public CollectorConfigObservation reconcile(Consumer<CollectorConfigSnapshot> graphApplier) {
        Objects.requireNonNull(graphApplier, "graphApplier");
        return reconcile(new GraphApplier() {
            @Override
            public void prepare(CollectorConfigSnapshot snapshot) {
                graphApplier.accept(snapshot);
            }

            @Override
            public void replace(CollectorConfigSnapshot snapshot) {
                // Consumer-style callers use the callback as a pre-commit graph
                // validation hook; the runtime uses the two-phase API below.
            }

            @Override
            public void restore(CollectorConfigSnapshot snapshot) {
                // Consumer-style callers do not own a published graph.
            }
        });
    }

    @Override
    public CollectorConfigObservation reconcile(PollingConfigProvider.GraphApplier graphApplier) {
        Objects.requireNonNull(graphApplier, "graphApplier");
        return withLock(true, () -> reconcileLocked(graphApplier));
    }

    public void writeObserved(CollectorConfigObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (!workloadId.equals(observation.workloadId())) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_WORKLOAD_MISMATCH);
        }
        withLock(true, () -> {
            writeObservedLocked(observation);
            return null;
        });
    }

    /** Collector only writes active/history/observed; desired has no writer in this class. */
    public void persistActiveAndHistory(CollectorConfigSnapshotCodec.DecodedSnapshot candidate) {
        Objects.requireNonNull(candidate, "candidate");
        withLock(true, () -> {
            DecodedState value = toDecoded(candidate);
            writeHistoryLocked(value);
            atomicWrite(configDirectory.resolve(ACTIVE_FILE), value.canonicalBytes());
            return null;
        });
    }

    private CollectorConfigObservation reconcileLocked(PollingConfigProvider.GraphApplier graphApplier) {
        StateSnapshot state;
        try {
            state = readStartupStateLocked(graphApplier);
        } catch (CollectorConfigStateException e) {
            CollectorConfigObservation failed = CollectorConfigObservation.failed(workloadId, null, null,
                    e.errorCode());
            safelyWriteObserved(failed);
            return failed;
        }

        if (state.active().isEmpty() && state.desired().isEmpty()) {
            CollectorConfigObservation waiting = CollectorConfigObservation.waiting(workloadId);
            safelyWriteObserved(waiting);
            return waiting;
        }

        DecodedState oldActive = state.active().orElse(null);
        DecodedState candidate = state.desired().orElse(oldActive);
        if (candidate == null) {
            CollectorConfigObservation waiting = CollectorConfigObservation.waiting(workloadId);
            safelyWriteObserved(waiting);
            return waiting;
        }
        if (oldActive != null && candidate.snapshot().configVersion() < oldActive.snapshot().configVersion()) {
            return failLocked(CollectorConfigErrorCode.COLLECTOR_CONFIG_VERSION_STALE, candidate);
        }
        if (oldActive != null && candidate.snapshot().configVersion() == oldActive.snapshot().configVersion()
                && !candidate.payloadSha256().equals(oldActive.payloadSha256())) {
            return failLocked(CollectorConfigErrorCode.COLLECTOR_CONFIG_VERSION_CONFLICT, candidate);
        }

        boolean candidateIsNew = oldActive == null || candidate.snapshot().configVersion() > oldActive.snapshot().configVersion()
                || !candidate.payloadSha256().equals(oldActive.payloadSha256());
        try {
            CollectorConfigSnapshot prepared = candidate.snapshot().withPayloadSha256(candidate.payloadSha256());
            try {
                graphApplier.prepare(prepared);
            } catch (RuntimeException e) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_APPLY_FAILED, e);
            }
            if (candidateIsNew) {
                writeHistoryLocked(candidate);
                atomicWrite(configDirectory.resolve(ACTIVE_FILE), candidate.canonicalBytes());
            }
            try {
                // Memory is replaced only after active persistence has completed.
                graphApplier.replace(prepared);
            } catch (RuntimeException e) {
                graphApplier.restore(oldActive == null ? null : oldActive.snapshot().withPayloadSha256(oldActive.payloadSha256()));
                rollbackActive(oldActive);
                return failLocked(CollectorConfigErrorCode.COLLECTOR_CONFIG_APPLY_FAILED, candidate);
            }
            CollectorConfigObservation applied = CollectorConfigObservation.applied(workloadId,
                    candidate.snapshot().configVersion(), candidate.payloadSha256());
            try {
                writeObservedLocked(applied);
            } catch (RuntimeException e) {
                graphApplier.restore(oldActive == null ? null : oldActive.snapshot().withPayloadSha256(oldActive.payloadSha256()));
                rollbackActive(oldActive);
                return failLocked(CollectorConfigErrorCode.COLLECTOR_CONFIG_WRITE_FAILED, candidate);
            }
            return applied;
        } catch (CollectorConfigStateException e) {
            rollbackActive(oldActive);
            CollectorConfigObservation failed = CollectorConfigObservation.failed(workloadId,
                    candidate.snapshot().configVersion(), candidate.payloadSha256(), e.errorCode());
            safelyWriteObserved(failed);
            return failed;
        } catch (RuntimeException e) {
            rollbackActive(oldActive);
            CollectorConfigObservation failed = CollectorConfigObservation.failed(workloadId,
                    candidate.snapshot().configVersion(), candidate.payloadSha256(),
                    CollectorConfigErrorCode.COLLECTOR_CONFIG_WRITE_FAILED);
            safelyWriteObserved(failed);
            return failed;
        }
    }

    private CollectorConfigObservation failLocked(CollectorConfigErrorCode code, DecodedState candidate) {
        CollectorConfigObservation failed = CollectorConfigObservation.failed(workloadId,
                candidate == null ? null : candidate.snapshot().configVersion(),
                candidate == null ? null : candidate.payloadSha256(), code);
        safelyWriteObserved(failed);
        return failed;
    }

    private void safelyWriteObserved(CollectorConfigObservation observation) {
        try {
            writeObservedLocked(observation);
        } catch (RuntimeException ignored) {
            // Preserve the original closed error; a failed observed write is
            // deliberately fail-closed and is never reported as APPLIED.
        }
    }

    private void rollbackActive(DecodedState oldActive) {
        try {
            Path active = configDirectory.resolve(ACTIVE_FILE);
            if (oldActive == null) {
                Files.deleteIfExists(active);
            } else {
                atomicWrite(active, oldActive.canonicalBytes());
            }
        } catch (Exception ignored) {
            // A rollback failure cannot safely claim the old state remains.
        }
    }

    private StateSnapshot readStateLocked() {
        Optional<DecodedState> active = readPayload(ACTIVE_FILE);
        Optional<DecodedState> desired = readPayload(DESIRED_FILE);
        List<DecodedState> history = readHistoryLocked();
        readObservedLocked();
        verifyStateVersions(active, desired, history);
        return new StateSnapshot(active, desired);
    }

    /**
     * Startup deliberately installs a valid active graph before touching desired.
     * This keeps a known-good active graph available when a newly delivered desired
     * file is malformed or otherwise rejected.
     */
    private StateSnapshot readStartupStateLocked(PollingConfigProvider.GraphApplier graphApplier) {
        Optional<DecodedState> active = readPayload(ACTIVE_FILE);
        active.ifPresent(value -> {
            try {
                CollectorConfigSnapshot prepared = value.snapshot().withPayloadSha256(value.payloadSha256());
                graphApplier.prepare(prepared);
                graphApplier.replace(prepared);
            } catch (RuntimeException e) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_APPLY_FAILED, e);
            }
        });
        Optional<DecodedState> desired = readPayload(DESIRED_FILE);
        List<DecodedState> history = readHistoryLocked();
        readObservedLocked();
        verifyStateVersions(active, desired, history);
        return new StateSnapshot(active, desired);
    }

    private void verifyStateVersions(Optional<DecodedState> active, Optional<DecodedState> desired,
                                     List<DecodedState> history) {
        List<DecodedState> all = new ArrayList<>();
        active.ifPresent(all::add);
        desired.ifPresent(all::add);
        all.addAll(history);
        Map<Long, String> versions = new HashMap<>();
        for (DecodedState value : all) {
            String prior = versions.putIfAbsent(value.snapshot().configVersion(), value.payloadSha256());
            if (prior != null && !prior.equals(value.payloadSha256())) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
            }
        }
    }

    private Optional<DecodedState> readPayload(String fileName) {
        Path path = configDirectory.resolve(fileName);
        ensureFormalPath(path, fileName);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        ensureRegular(path, CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
        ensureFileMode(path);
        try {
            byte[] raw = Files.readAllBytes(path);
            return Optional.of(toDecoded(codec.decode(raw, workloadId)));
        } catch (CollectorConfigStateException e) {
            throw e;
        } catch (IOException e) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT, e);
        }
    }

    private List<DecodedState> readHistoryLocked() {
        Path history = configDirectory.resolve("history");
        ensureNoSymlinkComponents(history);
        if (!Files.exists(history, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        ensureRegularDirectory(history);
        ensureDirectoryMode(history);
        List<DecodedState> values = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(history)) {
            for (Path path : entries) {
                String filename = path.getFileName().toString();
                if (Files.isSymbolicLink(path)) {
                    throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
                }
                ensureNoSymlinkComponents(path);
                if (OWN_TEMP_FILE.matcher(filename).matches()) {
                    continue;
                }
                Matcher matcher = HISTORY_FILE.matcher(filename);
                if (!matcher.matches()) {
                    throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
                }
                ensureRegular(path, CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
                ensureFileMode(path);
                try {
                    DecodedState decoded = toDecoded(codec.decode(Files.readAllBytes(path), workloadId));
                    if (decoded.snapshot().configVersion() != Long.parseLong(matcher.group(1))
                            || !decoded.payloadSha256().toLowerCase().startsWith(matcher.group(2).toLowerCase())) {
                        throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
                    }
                    values.add(decoded);
                } catch (CollectorConfigStateException e) {
                    throw e;
                } catch (IOException | NumberFormatException e) {
                    throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT, e);
                }
            }
        } catch (IOException e) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT, e);
        }
        values.sort(Comparator.comparingLong(item -> item.snapshot().configVersion()));
        return values;
    }

    private Optional<CollectorConfigObservation> readObservedLocked() {
        Path path = configDirectory.resolve(OBSERVED_FILE);
        ensureFormalPath(path, OBSERVED_FILE);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        ensureRegular(path, CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
        ensureFileMode(path);
        try {
            JsonNode node = codec.mapper().readTree(Files.readAllBytes(path));
            if (node == null || !node.isObject() || node.size() > 6
                    || !node.has("workloadId") || !node.has("status") || !node.has("observedAt")) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
            }
            Set<String> allowed = Set.of("workloadId", "status", "configVersion", "payloadSha256",
                    "observedAt", "errorCode");
            var fields = node.fieldNames();
            while (fields.hasNext()) {
                if (!allowed.contains(fields.next())) {
                    throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
                }
            }
            String observedWorkload = node.path("workloadId").textValue();
            String statusValue = node.path("status").textValue();
            String observedAt = node.path("observedAt").textValue();
            if (!workloadId.equals(observedWorkload) || statusValue == null || observedAt == null
                    || observedAt.isBlank()) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
            }
            CollectorConfigStatus status = CollectorConfigStatus.valueOf(statusValue);
            Long version = node.has("configVersion") ? node.get("configVersion").longValue() : null;
            String hash = node.has("payloadSha256") ? node.get("payloadSha256").textValue() : null;
            CollectorConfigErrorCode error = node.has("errorCode")
                    ? CollectorConfigErrorCode.valueOf(node.get("errorCode").textValue()) : null;
            CollectorConfigObservation observation = new CollectorConfigObservation(workloadId, status, version,
                    hash, observedAt, error);
            byte[] raw = Files.readAllBytes(path);
            if (!java.security.MessageDigest.isEqual(raw,
                    codec.encodeObservation((ObjectNode) node))) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
            }
            return Optional.of(observation);
        } catch (CollectorConfigStateException e) {
            throw e;
        } catch (Exception e) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT, e);
        }
    }

    private void writeHistoryLocked(DecodedState candidate) {
        Path history = configDirectory.resolve("history");
        ensureNoSymlinkComponents(history);
        ensureDirectory(configDirectory);
        if (Files.exists(history, LinkOption.NOFOLLOW_LINKS)) {
            ensureRegularDirectory(history);
            ensureDirectoryMode(history);
        } else {
            try {
                Files.createDirectory(history);
                applyDirectoryMode(history, true);
            } catch (IOException e) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_WRITE_FAILED, e);
            }
        }
        Path target = history.resolve(candidate.snapshot().configVersion() + "-"
                + candidate.payloadSha256().substring(0, 8) + ".json");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            ensureRegular(target, CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
            ensureFileMode(target);
            try {
                if (!MessageDigestHolder.equal(Files.readAllBytes(target), candidate.canonicalBytes())) {
                    throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT);
                }
            } catch (IOException e) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT, e);
            }
            return;
        }
        atomicWrite(target, candidate.canonicalBytes());
    }

    private void writeObservedLocked(CollectorConfigObservation observation) {
        ObjectNode node = codec.mapper().createObjectNode();
        node.put("workloadId", observation.workloadId());
        node.put("status", observation.status().name());
        if (observation.configVersion() != null) {
            node.put("configVersion", observation.configVersion());
        }
        if (observation.payloadSha256() != null) {
            node.put("payloadSha256", observation.payloadSha256().toLowerCase());
        }
        node.put("observedAt", observation.observedAt());
        if (observation.errorCode() != null) {
            node.put("errorCode", observation.errorCode().name());
        }
        atomicWrite(configDirectory.resolve(OBSERVED_FILE), codec.encodeObservation(node));
    }

    private <T> T withLock(boolean create, LockedAction<T> action) {
        if (create) {
            ensureDirectory(configDirectory);
        } else if (!Files.exists(configDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return action.runWithoutLock();
        }
        ensureRegularDirectory(configDirectory);
        ensureDirectoryMode(configDirectory);
        Path lockPath = configDirectory.resolve(LOCK_FILE);
        ensureFormalPath(lockPath, LOCK_FILE);
        boolean lockExists = Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS);
        if (lockExists) {
            ensureRegular(lockPath, CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
            ensureFileMode(lockPath);
        }
        try (FileChannel channel = FileChannel.open(lockPath,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS))) {
            if (lockExists) {
                ensureFileMode(lockPath);
            } else {
                applyFileMode(lockPath, true);
            }
            try (FileLock ignored = channel.lock()) {
                return action.run();
            }
        } catch (CollectorConfigStateException e) {
            throw e;
        } catch (IOException | UnsupportedOperationException e) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_WRITE_FAILED, e);
        }
    }

    private void atomicWrite(Path target, byte[] bytes) {
        ensureFormalPath(target, target.getFileName().toString());
        ensureDirectory(target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            ensureRegular(target, CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
            ensureFileMode(target);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Path temporary = target.resolveSibling("." + target.getFileName() + "." + token + ".tmp");
        ensureNoSymlinkComponents(temporary);
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                applyFileMode(temporary, true);
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (hasPosix(target.getParent())) {
                try (FileChannel directory = FileChannel.open(target.getParent(), StandardOpenOption.READ)) {
                    directory.force(true);
                } catch (UnsupportedOperationException ignored) {
                    // Filesystems without directory fsync remain portable.
                }
            }
            applyFileMode(target, false);
        } catch (IOException | UnsupportedOperationException e) {
            try {
                if (!Files.isSymbolicLink(temporary)) {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException ignored) {
                // Fail closed with the stable write error below.
            }
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_WRITE_FAILED, e);
        }
    }

    private void ensureDirectory(Path directory) {
        if (directory == null) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
        }
        ensureNoSymlinkComponents(directory);
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                ensureRegularDirectory(directory);
                ensureDirectoryMode(directory);
                return;
            }
            Files.createDirectories(directory);
            applyDirectoryMode(directory, true);
        } catch (IOException e) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_WRITE_FAILED, e);
        }
    }

    private static void ensureFormalPath(Path path, String name) {
        if (!FORMAL_FILES.contains(name) && !"history".equals(name)
                && !name.endsWith(".json")) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
        }
        ensureNoSymlinkComponents(path);
    }

    /** Reject a symlink at any existing component, not only at the final file. */
    private static void ensureNoSymlinkComponents(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current != null && Files.isSymbolicLink(current)) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
        }
        for (Path component : absolute) {
            current = current == null ? component : current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                break;
            }
        }
    }

    private static void ensureRegular(Path path, CollectorConfigErrorCode code) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw state(code);
        }
    }

    private static void ensureRegularDirectory(Path path) {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
        }
    }

    private static void ensureDirectoryMode(Path path) {
        checkMode(path, LINUX_CONFIG_DIRECTORY_MODE, CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
    }

    private static void ensureFileMode(Path path) {
        checkMode(path, LINUX_CONFIG_FILE_MODE, CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
    }

    private static boolean hasPosix(Path path) {
        return Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS) != null;
    }

    private static void checkMode(Path path, int expected, CollectorConfigErrorCode code) {
        if (!hasPosix(path)) {
            return;
        }
        try {
            Object value = Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS);
            int actual = ((Number) value).intValue() & 07777;
            if (actual != expected) {
                throw state(code);
            }
        } catch (IOException e) {
            throw state(code, e);
        }
    }

    private static void applyDirectoryMode(Path path, boolean newlyCreated) {
        if (!hasPosix(path)) {
            return;
        }
        try {
            if (newlyCreated) {
                Files.setAttribute(path, "unix:mode", LINUX_CONFIG_DIRECTORY_MODE, LinkOption.NOFOLLOW_LINKS);
            }
            checkMode(path, LINUX_CONFIG_DIRECTORY_MODE, CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
        } catch (IOException e) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, e);
        }
    }

    private static void applyFileMode(Path path, boolean newlyCreated) {
        if (!hasPosix(path)) {
            return;
        }
        try {
            if (newlyCreated) {
                Files.setAttribute(path, "unix:mode", LINUX_CONFIG_FILE_MODE, LinkOption.NOFOLLOW_LINKS);
            }
            checkMode(path, LINUX_CONFIG_FILE_MODE, CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
        } catch (IOException e) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, e);
        }
    }

    private static DecodedState toDecoded(CollectorConfigSnapshotCodec.DecodedSnapshot value) {
        return new DecodedState(value.snapshot(), value.canonicalBytes(), value.payloadSha256());
    }

    private static String requireWorkload(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || value.indexOf('\0') >= 0) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_WORKLOAD_MISMATCH);
        }
        return value;
    }

    private static Path normalize(Path value) {
        if (value == null || !value.isAbsolute() || value.toString().indexOf('\0') >= 0) {
            throw state(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID);
        }
        Path normalized = value.normalize();
        ensureNoSymlinkComponents(normalized);
        return normalized;
    }

    private static CollectorConfigStateException state(CollectorConfigErrorCode code) {
        return new CollectorConfigStateException(code);
    }

    private static CollectorConfigStateException state(CollectorConfigErrorCode code, Throwable cause) {
        return new CollectorConfigStateException(code, cause);
    }

    /** Compatibility alias for existing local-file tests; the port contract lives in the API module. */
    @Deprecated
    public interface GraphApplier extends PollingConfigProvider.GraphApplier {
    }

    public record StateSnapshot(Optional<DecodedState> active, Optional<DecodedState> desired) {
        public StateSnapshot {
            active = active == null ? Optional.empty() : active;
            desired = desired == null ? Optional.empty() : desired;
        }
    }

    public record DecodedState(CollectorConfigSnapshot snapshot, byte[] canonicalBytes, String payloadSha256) {
        public DecodedState {
            canonicalBytes = canonicalBytes.clone();
        }
    }

    @FunctionalInterface
    private interface LockedAction<T> {
        T run();

        default T runWithoutLock() {
            return run();
        }
    }

    private static final class MessageDigestHolder {
        static boolean equal(byte[] left, byte[] right) {
            return java.security.MessageDigest.isEqual(left, right);
        }
    }
}
