package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * TD-005 §9：单个成员的三方合并结论。value 为采用值（冲突/删除时为空）。
 */
public final class MergeOutcome {

    public enum Resolution {
        /** V == B 且 N != B：自动采用新标准值。 */
        AUTO_STANDARD,
        /** N == B 且 V != B：保留厂家值（含厂家删除）。 */
        AUTO_VENDOR,
        /** V == N：采用共同值。 */
        AUTO_COMMON,
        /** 标准删除且厂家未改，或双方均删除：随标准删除。 */
        AUTO_DROP,
        /** V != B、N != B 且 V != N：必须人工决策。 */
        CONFLICT,
        /** 一侧删除、另一侧修改同一成员。 */
        DELETE_MODIFY_CONFLICT,
        /** 两侧新增同 code 但指纹不同。 */
        ADD_ADD_CONFLICT
    }

    private final String memberType;
    private final String memberCode;
    private final Resolution resolution;
    private final JsonNode value;

    public MergeOutcome(String memberType, String memberCode, Resolution resolution, JsonNode value) {
        this.memberType = memberType;
        this.memberCode = memberCode;
        this.resolution = resolution;
        this.value = value;
    }

    public String memberType() {
        return memberType;
    }

    public String memberCode() {
        return memberCode;
    }

    public Resolution resolution() {
        return resolution;
    }

    public JsonNode value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MergeOutcome)) {
            return false;
        }
        MergeOutcome other = (MergeOutcome) o;
        return memberType.equals(other.memberType) && memberCode.equals(other.memberCode)
                && resolution == other.resolution && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberType, memberCode, resolution, value);
    }

    @Override
    public String toString() {
        return memberType + ":" + memberCode + " -> " + resolution;
    }
}
