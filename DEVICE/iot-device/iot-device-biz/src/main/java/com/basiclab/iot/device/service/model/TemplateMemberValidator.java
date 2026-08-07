package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

/**
 * TD-005 §5.1：成员 code 在各自成员类型内唯一（Schema 外语义合同；
 * JSON Schema 无法表达数组内唯一性，故由本校验器承担）。
 * 参数级唯一性限定在所属服务/事件的 inputs/outputs 内部。Java 8 兼容。
 */
public final class TemplateMemberValidator {

    private static final String[][] MEMBER_TYPES = {
            {"properties", "propertyCode"},
            {"events", "eventCode"},
            {"services", "serviceCode"},
    };

    private static final String[] PARAMETER_CONTAINERS = {"inputs", "outputs"};

    public void requireUniqueMemberCodes(JsonNode template) {
        for (String[] memberType : MEMBER_TYPES) {
            JsonNode members = template.path(memberType[0]);
            if (!members.isArray()) {
                continue;
            }
            requireUniqueCodes(members, memberType[1], memberType[0]);
            for (JsonNode member : members) {
                requireUniqueParameters(member, memberType[0]);
            }
        }
    }

    private void requireUniqueParameters(JsonNode member, String ownerType) {
        for (String container : PARAMETER_CONTAINERS) {
            JsonNode parameters = member.path(container);
            if (parameters.isArray()) {
                requireUniqueCodes(parameters, "parameterCode",
                        ownerType + "." + container);
            }
        }
    }

    private void requireUniqueCodes(JsonNode members, String codeField, String scope) {
        Set<String> seen = new HashSet<>();
        for (JsonNode member : members) {
            JsonNode code = member.path(codeField);
            if (!code.isTextual()) {
                continue;
            }
            if (!seen.add(code.textValue())) {
                throw new IllegalArgumentException(
                        "MODEL_MEMBER_CODE_DUPLICATE: " + scope + " 内重复 code " + code.textValue());
            }
        }
    }
}
