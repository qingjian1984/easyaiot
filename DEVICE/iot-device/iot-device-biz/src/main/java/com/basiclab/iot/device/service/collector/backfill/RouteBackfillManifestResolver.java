package com.basiclab.iot.device.service.collector.backfill;

import com.basiclab.iot.device.service.collector.backfill.RouteBackfillFactRepository.ProductFact;
import com.basiclab.iot.device.service.collector.backfill.RouteBackfillFactRepository.ProjectionFact;
import com.basiclab.iot.device.service.collector.backfill.RouteBackfillFactRepository.ReleaseFact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillIssue;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestEntry;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillResolutionResult;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryEntry;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryPage;
import com.basiclab.iot.device.service.model.JcsCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * LC02-04B center authority resolver.  The resolver only reads the three
 * authoritative fact sets and emits one complete manifest or one rejection.
 */
public final class RouteBackfillManifestResolver {

    private static final Pattern DECIMAL_ID = Pattern.compile("[0-9]+");
    private static final String JCS_VERSION = "jcs-rfc8785-v1";
    private static final String V1 = "1.0";
    private static final String V11 = "1.1";
    private static final String READ_FAILED = "ROUTE_BACKFILL_READ_FAILED";
    private static final String INPUT_INVALID = "ROUTE_BACKFILL_INPUT_INVALID";
    private static final String INVENTORY_INTEGRITY_FAILED =
            "ROUTE_BACKFILL_INVENTORY_INTEGRITY_FAILED";
    private static final String MANIFEST_INVALID = "ROUTE_BACKFILL_MANIFEST_INVALID";
    private static final String MANIFEST_INTEGRITY_FAILED =
            "ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED";
    private static final String[] ALLOWED_RELEASE_STATUSES = {
            "PUBLISHED", "APPLIED", "APPLY_TIMEOUT", "ROLLED_BACK"
    };
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final RouteBackfillFactRepository facts;
    private final TransactionTemplate readTransaction;
    private final ObjectMapper mapper;
    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();

    public RouteBackfillManifestResolver(RouteBackfillFactRepository facts) {
        this(facts, null, new ObjectMapper());
    }

    public RouteBackfillManifestResolver(RouteBackfillFactRepository facts,
                                         PlatformTransactionManager transactionManager) {
        this(facts, transactionManager, new ObjectMapper());
    }

    public RouteBackfillManifestResolver(DataSource dataSource,
                                         PlatformTransactionManager transactionManager) {
        this(new JdbcRouteBackfillFactRepository(dataSource), transactionManager,
                new ObjectMapper());
    }

    public RouteBackfillManifestResolver(RouteBackfillFactRepository facts,
                                         PlatformTransactionManager transactionManager,
                                         ObjectMapper mapper) {
        this.facts = Objects.requireNonNull(facts, "facts");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        if (transactionManager == null) {
            this.readTransaction = null;
        } else {
            this.readTransaction = new TransactionTemplate(transactionManager);
            this.readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            this.readTransaction.setReadOnly(true);
        }
    }

    /** Resolve one source inventory page in one repeatable-read read-only transaction. */
    public RouteBackfillResolutionResult resolve(RouteInventoryArtifact inventoryArtifact) {
        RouteInventoryPage page = validateInventory(inventoryArtifact);
        try {
            if (readTransaction == null) {
                return resolveFacts(inventoryArtifact, page);
            }
            return readTransaction.execute(status -> resolveFacts(inventoryArtifact, page));
        } catch (DataAccessException e) {
            throw readFailed(e);
        } catch (RuntimeException e) {
            if (hasStablePrefix(e.getMessage())) {
                throw e;
            }
            throw readFailed(e);
        }
    }

    private RouteBackfillResolutionResult resolveFacts(RouteInventoryArtifact inventoryArtifact,
                                                       RouteInventoryPage page) {
        if (page.entries().isEmpty()) {
            return resolvedManifest(inventoryArtifact, page, List.of());
        }

        List<RouteBackfillManifestEntry> resolved = new ArrayList<>(page.entries().size());
        List<RouteBackfillIssue> issues = new ArrayList<>();
        for (RouteInventoryEntry inventoryEntry : page.entries()) {
            KeyResolution resolution = resolveOne(page, inventoryEntry);
            if (resolution.issue() != null) {
                issues.add(resolution.issue());
            } else {
                resolved.add(resolution.entry());
            }
        }
        if (!issues.isEmpty()) {
            return new RouteBackfillResolutionResult.Rejected(
                    inventoryArtifact.contentSha256(), page.workloadId(), issues);
        }
        return resolvedManifest(inventoryArtifact, page, resolved);
    }

    private KeyResolution resolveOne(RouteInventoryPage page, RouteInventoryEntry inventoryEntry) {
        RouteBackfillKey key = inventoryEntry.key();
        Long tenantId = parsePositiveTenantId(key.tenantId());
        if (tenantId == null) {
            return issue(key, "ROUTE_BACKFILL_TENANT_ID_INVALID");
        }

        List<ReleaseFact> releases = facts.findReleaseFacts(tenantId, page.workloadId(),
                key.siteCode(), key.configVersion());
        if (releases == null || releases.size() != 1
                || !isAllowedReleaseStatus(releases.get(0).status())) {
            return issue(key, "ROUTE_BACKFILL_RELEASE_NOT_UNIQUE");
        }
        ReleaseFact release = releases.get(0);

        JsonNode payload = parseAndVerifyPayload(release);
        if (payload == null) {
            return issue(key, "ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED");
        }
        if (!releaseRowIdentityMatches(page.workloadId(), key, release)
                || !payloadIdentityMatches(page.workloadId(), key, release, payload)) {
            return issue(key, "ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH");
        }
        if (!targetDeviceOccursExactlyOnce(payload, key.deviceIdentification())) {
            return issue(key, "ROUTE_BACKFILL_DEVICE_NOT_UNIQUE");
        }

        List<ProjectionFact> projections = facts.findProjectionFacts(tenantId, page.workloadId());
        if (projections == null || projections.size() != 1) {
            return issue(key, "ROUTE_BACKFILL_PROJECTION_NOT_UNIQUE");
        }
        ProjectionFact projection = projections.get(0);
        if (!projectionIdentityMatches(page.workloadId(), release, projection)) {
            return issue(key, "ROUTE_BACKFILL_PROJECTION_IDENTITY_MISMATCH");
        }

        List<ProductFact> products = facts.findProductFacts(tenantId, release.productId());
        if (products == null || products.size() != 1) {
            return issue(key, "ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE");
        }
        ProductFact product = products.get(0);
        if (product.tenantId() != tenantId || product.productId() != release.productId()) {
            return issue(key, "ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE");
        }
        if (!validProductIdentification(product.productIdentification())) {
            return issue(key, "ROUTE_BACKFILL_PRODUCT_IDENTIFICATION_INVALID");
        }
        if (V11.equals(release.schemaVersion())
                && !product.productIdentification().equals(payload.path("productIdentification").asText(null))) {
            return issue(key, "ROUTE_BACKFILL_PRODUCT_IDENTITY_MISMATCH");
        }

        return new KeyResolution(new RouteBackfillManifestEntry(key, inventoryEntry.rowCount(),
                product.productIdentification(), page.workloadId(), release.releaseId(),
                release.payloadSha256()), null);
    }

    private JsonNode parseAndVerifyPayload(ReleaseFact release) {
        if (!V1.equals(release.schemaVersion()) && !V11.equals(release.schemaVersion())) {
            return null;
        }
        if (!JCS_VERSION.equals(release.canonicalizationVersion())
                || release.payloadCanonical() == null || release.payloadSha256() == null
                || !SHA256_HEX.matcher(release.payloadSha256()).matches()
                || release.payloadProjection() == null) {
            return null;
        }
        byte[] canonicalBytes = release.payloadCanonical().getBytes(StandardCharsets.UTF_8);
        if (release.canonicalLengthBytes() != canonicalBytes.length
                || !sha256Hex(canonicalBytes).equals(release.payloadSha256())) {
            return null;
        }
        try {
            JsonNode payload = mapper.readTree(release.payloadCanonical());
            if (payload == null || !payload.isObject()
                    || !release.schemaVersion().equals(payload.path("schemaVersion").asText(null))
                    || !canonicalizer.canonicalize(payload).equals(release.payloadCanonical())) {
                return null;
            }
            JsonNode projection = mapper.readTree(release.payloadProjection());
            if (projection == null || !canonicalizer.canonicalize(projection)
                    .equals(release.payloadCanonical())) {
                return null;
            }
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean releaseRowIdentityMatches(String workloadId, RouteBackfillKey key,
                                                     ReleaseFact release) {
        Long parsedTenant = parsePositiveTenantId(key.tenantId());
        return parsedTenant != null && release.tenantId() == parsedTenant
                && workloadId.equals(release.workloadId())
                && key.siteCode().equals(release.siteCode())
                && key.configVersion() == release.configVersion();
    }

    private static boolean payloadIdentityMatches(String workloadId, RouteBackfillKey key,
                                                  ReleaseFact release, JsonNode payload) {
        JsonNode configVersion = payload.get("configVersion");
        return key.tenantId().equals(payload.path("tenantId").asText(null))
                && workloadId.equals(payload.path("workloadId").asText(null))
                && key.siteCode().equals(payload.path("siteCode").asText(null))
                && configVersion != null && configVersion.isIntegralNumber()
                && configVersion.canConvertToLong()
                && key.configVersion() == configVersion.longValue()
                && release.configVersion() == configVersion.longValue();
    }

    private static boolean targetDeviceOccursExactlyOnce(JsonNode payload, String target) {
        JsonNode buses = payload.get("serialBuses");
        if (buses == null || !buses.isArray()) {
            return false;
        }
        int matches = 0;
        for (JsonNode bus : buses) {
            JsonNode devices = bus == null ? null : bus.get("devices");
            if (devices == null || !devices.isArray()) {
                return false;
            }
            for (JsonNode device : devices) {
                if (device != null && target.equals(device.path("deviceIdentification").asText(null))) {
                    matches++;
                }
            }
        }
        return matches == 1;
    }

    private static boolean projectionIdentityMatches(String workloadId, ReleaseFact release,
                                                     ProjectionFact projection) {
        return projection.tenantId() == release.tenantId()
                && workloadId.equals(projection.workloadId())
                && release.siteCode().equals(projection.siteCode())
                && release.nodeId() == projection.nodeId()
                && release.productId() == projection.productId()
                && projection.configVersion() >= release.configVersion();
    }

    private RouteBackfillResolutionResult resolvedManifest(RouteInventoryArtifact source,
                                                           RouteInventoryPage page,
                                                           List<RouteBackfillManifestEntry> entries) {
        try {
            RouteBackfillManifest manifest = new RouteBackfillManifest(
                    RouteBackfillManifest.SCHEMA_VERSION,
                    RouteBackfillManifest.CANONICALIZATION_VERSION,
                    source.contentSha256(), page.workloadId(), entries, page.nextCursor());
            return new RouteBackfillResolutionResult.Resolved(
                    new RouteBackfillManifestArtifact(manifest));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("canonical")) {
                throw new IllegalStateException(MANIFEST_INTEGRITY_FAILED);
            }
            throw new IllegalStateException(MANIFEST_INVALID);
        }
    }

    private static RouteInventoryPage validateInventory(RouteInventoryArtifact source) {
        if (source == null || source.page() == null) {
            throw new IllegalArgumentException(INPUT_INVALID);
        }
        try {
            RouteInventoryArtifact expected = new RouteInventoryArtifact(source.page());
            if (!java.util.Arrays.equals(expected.canonicalBytes(), source.canonicalBytes())
                    || !expected.contentSha256().equals(source.contentSha256())) {
                throw new IllegalArgumentException(INVENTORY_INTEGRITY_FAILED);
            }
            return source.page();
        } catch (IllegalArgumentException e) {
            if (INVENTORY_INTEGRITY_FAILED.equals(e.getMessage())) {
                throw e;
            }
            throw new IllegalArgumentException(INVENTORY_INTEGRITY_FAILED);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(INPUT_INVALID);
        }
    }

    private static Long parsePositiveTenantId(String value) {
        if (value == null || !DECIMAL_ID.matcher(value).matches()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean validProductIdentification(String value) {
        return value != null && !value.trim().isEmpty()
                && value.codePointCount(0, value.length()) <= 128;
    }

    private static boolean isAllowedReleaseStatus(String status) {
        for (String allowed : ALLOWED_RELEASE_STATUSES) {
            if (allowed.equals(status)) return true;
        }
        return false;
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(b & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(READ_FAILED);
        }
    }

    private static KeyResolution issue(RouteBackfillKey key, String code) {
        return new KeyResolution(null, new RouteBackfillIssue(key, code));
    }

    private static boolean hasStablePrefix(String message) {
        return message != null && (message.startsWith("ROUTE_BACKFILL_")
                || message.startsWith("ROUTE_BACKFILL"));
    }

    private static IllegalStateException readFailed(Exception cause) {
        return new IllegalStateException(READ_FAILED, cause);
    }

    private record KeyResolution(RouteBackfillManifestEntry entry, RouteBackfillIssue issue) {
    }
}
