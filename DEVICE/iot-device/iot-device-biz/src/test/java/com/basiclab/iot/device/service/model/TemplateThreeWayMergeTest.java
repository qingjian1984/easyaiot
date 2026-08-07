package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TD-005 §9：三方差异与冲突决策。成员身份键 = memberType + memberCode，
 * 指纹 = 成员 JCS canonical。预览相同输入必须得到相同 diff（纯函数）。
 */
class TemplateThreeWayMergeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateThreeWayMerge merger = new TemplateThreeWayMerge();

    @Test
    void standardOnlyChangeIsAutoAdopted() throws IOException {
        // V == B 且 N != B → 采用 N
        MergeOutcome outcome = mergeOne(
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}",
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}",
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"kV\"}");
        assertEquals(MergeOutcome.Resolution.AUTO_STANDARD, outcome.resolution());
    }

    @Test
    void vendorOnlyChangeIsKept() throws IOException {
        // N == B 且 V != B → 保留厂家 V
        MergeOutcome outcome = mergeOne(
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}",
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"mV\"}",
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}");
        assertEquals(MergeOutcome.Resolution.AUTO_VENDOR, outcome.resolution());
    }

    @Test
    void identicalChangeOnBothSidesIsCommon() throws IOException {
        // V == N → 采用共同值
        MergeOutcome outcome = mergeOne(
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}",
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"kV\"}",
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"kV\"}");
        assertEquals(MergeOutcome.Resolution.AUTO_COMMON, outcome.resolution());
    }

    @Test
    void divergentChangeIsConflict() throws IOException {
        // V != B、N != B 且 V != N → CONFLICT
        MergeOutcome outcome = mergeOne(
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}",
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"mV\"}",
                "{\"propertyCode\":\"voltage-a\",\"unit\":\"kV\"}");
        assertEquals(MergeOutcome.Resolution.CONFLICT, outcome.resolution());
    }

    @Test
    void standardDeleteWithVendorModifyIsDeleteModifyConflict() throws IOException {
        // 标准删除、厂家修改同一成员 → DELETE_MODIFY_CONFLICT
        JsonNode base = members("{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}");
        JsonNode vendor = members("{\"propertyCode\":\"voltage-a\",\"unit\":\"mV\"}");
        JsonNode standard = members();
        List<MergeOutcome> outcomes = merger.merge(base, vendor, standard);
        assertEquals(1, outcomes.size());
        assertEquals(MergeOutcome.Resolution.DELETE_MODIFY_CONFLICT, outcomes.get(0).resolution());
    }

    @Test
    void standardDeleteWithUntouchedVendorMemberIsDropped() throws IOException {
        // 标准删除且厂家未改 → 随标准删除
        JsonNode base = members("{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}");
        JsonNode vendor = members("{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}");
        JsonNode standard = members();
        List<MergeOutcome> outcomes = merger.merge(base, vendor, standard);
        assertEquals(1, outcomes.size());
        assertEquals(MergeOutcome.Resolution.AUTO_DROP, outcomes.get(0).resolution());
    }

    @Test
    void bothSidesAddedSameCodeWithDifferentFingerprintIsAddAddConflict() throws IOException {
        JsonNode base = members();
        JsonNode vendor = members("{\"propertyCode\":\"leakage-current\",\"unit\":\"mA\"}");
        JsonNode standard = members("{\"propertyCode\":\"leakage-current\",\"unit\":\"A\"}");
        List<MergeOutcome> outcomes = merger.merge(base, vendor, standard);
        assertEquals(1, outcomes.size());
        assertEquals(MergeOutcome.Resolution.ADD_ADD_CONFLICT, outcomes.get(0).resolution());
    }

    @Test
    void vendorSideAdditionsAndStandardAdditionsAreKept() throws IOException {
        JsonNode base = members();
        JsonNode vendor = members("{\"propertyCode\":\"vendor-x\",\"unit\":\"1\"}");
        JsonNode standard = members("{\"propertyCode\":\"standard-y\",\"unit\":\"1\"}");
        Map<String, MergeOutcome> byCode = merger.merge(base, vendor, standard).stream()
                .collect(Collectors.toMap(MergeOutcome::memberCode, Function.identity()));
        assertEquals(MergeOutcome.Resolution.AUTO_VENDOR, byCode.get("vendor-x").resolution());
        assertEquals(MergeOutcome.Resolution.AUTO_STANDARD, byCode.get("standard-y").resolution());
    }

    @Test
    void mergeIsDeterministicForSameInputs() throws IOException {
        JsonNode base = members("{\"propertyCode\":\"voltage-a\",\"unit\":\"V\"}");
        JsonNode vendor = members("{\"propertyCode\":\"voltage-a\",\"unit\":\"mV\"}");
        JsonNode standard = members("{\"propertyCode\":\"voltage-a\",\"unit\":\"kV\"}");
        assertEquals(merger.merge(base, vendor, standard), merger.merge(base, vendor, standard),
                "相同输入必须得到相同 diff（预览可复现）");
    }

    private MergeOutcome mergeOne(String baseMember, String vendorMember, String standardMember) throws IOException {
        List<MergeOutcome> outcomes = merger.merge(members(baseMember), members(vendorMember), members(standardMember));
        assertEquals(1, outcomes.size());
        return outcomes.get(0);
    }

    private JsonNode members(String... memberJson) throws IOException {
        return objectMapper.readTree("{\"properties\":[" + String.join(",", memberJson) + "]}");
    }
}
