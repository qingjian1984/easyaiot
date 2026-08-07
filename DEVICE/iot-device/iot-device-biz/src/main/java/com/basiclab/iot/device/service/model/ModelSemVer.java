package com.basiclab.iot.device.service.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TD-005 §5.1/§7.1：模板 SemVer。
 * 生产绑定只接受无 prerelease 的正式版本；服务端按结构化 diff 计算最低增量，
 * 目标版本低于最低增量阻止发布，不允许调用方通过 PATCH 标记绕过结构判断。
 * Java 8 兼容。
 */
public final class ModelSemVer implements Comparable<ModelSemVer> {

    /** 最低版本增量（MAJOR &gt; MINOR &gt; PATCH）。 */
    public enum Bump {
        MAJOR, MINOR, PATCH
    }

    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)"
                    + "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*)?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String prerelease;

    private ModelSemVer(int major, int minor, int patch, String prerelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
    }

    public static ModelSemVer parse(String raw) {
        if (raw == null) {
            throw invalid(null);
        }
        Matcher matcher = SEMVER.matcher(raw);
        if (!matcher.matches()) {
            throw invalid(raw);
        }
        return new ModelSemVer(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                matcher.group(4) == null ? "" : matcher.group(4));
    }

    private static IllegalArgumentException invalid(String raw) {
        return new IllegalArgumentException("MODEL_TEMPLATE_SEMVER_INVALID: 非法 SemVer 版本 " + raw);
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String prerelease() {
        return prerelease;
    }

    public boolean isPrerelease() {
        return !prerelease.isEmpty();
    }

    /** §7：生产产品禁止绑定 prerelease。 */
    public static void requireProductionBindable(ModelSemVer version) {
        if (version.isPrerelease()) {
            throw new IllegalArgumentException(
                    "MODEL_TEMPLATE_SEMVER_PRERELEASE_FORBIDDEN: 生产绑定只接受正式版本 " + version);
        }
    }

    /** §7.1：目标版本必须前进且不低于服务端计算的最低增量。 */
    public static void requireAllowedBump(ModelSemVer base, ModelSemVer target, Bump minimumBump) {
        Bump actual = actualBump(base, target);
        if (actual == null || actual.compareTo(minimumBump) > 0) {
            throw new IllegalArgumentException(
                    "MODEL_TEMPLATE_SEMVER_BUMP_TOO_LOW: 目标版本 " + target
                            + " 低于最低增量 " + minimumBump + "（基线 " + base + "）");
        }
    }

    private static Bump actualBump(ModelSemVer base, ModelSemVer target) {
        if (target.major > base.major) {
            return Bump.MAJOR;
        }
        if (target.major == base.major && target.minor > base.minor) {
            return Bump.MINOR;
        }
        if (target.major == base.major && target.minor == base.minor && target.patch > base.patch) {
            return Bump.PATCH;
        }
        return null;
    }

    @Override
    public int compareTo(ModelSemVer other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        if (patch != other.patch) {
            return Integer.compare(patch, other.patch);
        }
        return comparePrerelease(prerelease, other.prerelease);
    }

    /** SemVer §11.4：无 prerelease 高于有 prerelease；标识符逐段数值/字典序比较。 */
    private static int comparePrerelease(String left, String right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 0;
        }
        if (left.isEmpty()) {
            return 1;
        }
        if (right.isEmpty()) {
            return -1;
        }
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int i = 0; i < Math.min(leftParts.length, rightParts.length); i++) {
            int result = compareIdentifier(leftParts[i], rightParts[i]);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);
        if (leftNumeric && rightNumeric) {
            return Long.compare(Long.parseLong(left), Long.parseLong(right));
        }
        if (leftNumeric) {
            return -1;
        }
        if (rightNumeric) {
            return 1;
        }
        return left.compareTo(right);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelSemVer)) {
            return false;
        }
        ModelSemVer other = (ModelSemVer) o;
        return major == other.major && minor == other.minor && patch == other.patch
                && prerelease.equals(other.prerelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, prerelease);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (isPrerelease() ? "-" + prerelease : "");
    }
}
