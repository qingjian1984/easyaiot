package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TD-005 §7.1：服务端按结构化 diff 计算最低 SemVer 增量（不允许调用方
 * 通过 PATCH 标记绕过结构判断）。成员身份键 = memberType + memberCode。
 * 未在 §7.1 表中列明的结构变化按保守策略归 MAJOR（设计解释，见进度文档）。
 * Java 8 兼容。
 */
public final class TemplateDiffEngine {

    /** diff 结论：最低增量 + 逐项变化描述（供审计/预览）。 */
    public static final class DiffResult {
        private final ModelSemVer.Bump minimumBump;
        private final List<String> changes;

        DiffResult(ModelSemVer.Bump minimumBump, List<String> changes) {
            this.minimumBump = minimumBump;
            this.changes = Collections.unmodifiableList(new ArrayList<>(changes));
        }

        public ModelSemVer.Bump minimumBump() {
            return minimumBump;
        }

        public List<String> changes() {
            return changes;
        }
    }

    private static final String[][] MEMBER_TYPES = {
            {"properties", "propertyCode"},
            {"events", "eventCode"},
            {"services", "serviceCode"},
    };

    private static final String[][] DISPLAY_FIELDS = {
            {"properties", "propertyName", "description", "sampleHint"},
            {"events", "eventName", "description"},
            {"services", "serviceName", "description"},
    };

    private static final String[] ROOT_DISPLAY_FIELDS = {"templateName", "description"};

    public DiffResult diff(JsonNode base, JsonNode target) {
        List<String> changes = new ArrayList<>();
        ModelSemVer.Bump bump = ModelSemVer.Bump.PATCH;
        for (String[] memberType : MEMBER_TYPES) {
            bump = max(bump, diffMemberType(memberType[0], memberType[1], base, target, changes));
        }
        bump = max(bump, diffRoot(base, target, changes));
        return new DiffResult(bump, changes);
    }

    private ModelSemVer.Bump diffMemberType(String arrayField, String codeField,
                                            JsonNode base, JsonNode target, List<String> changes) {
        Map<String, JsonNode> baseMembers = indexByCode(base.path(arrayField), codeField);
        Map<String, JsonNode> targetMembers = indexByCode(target.path(arrayField), codeField);
        ModelSemVer.Bump bump = ModelSemVer.Bump.PATCH;

        for (String code : baseMembers.keySet()) {
            if (!targetMembers.containsKey(code)) {
                changes.add(arrayField + ":" + code + " removed");
                bump = max(bump, ModelSemVer.Bump.MAJOR);
            }
        }
        for (Map.Entry<String, JsonNode> entry : targetMembers.entrySet()) {
            String code = entry.getKey();
            JsonNode targetMember = entry.getValue();
            JsonNode baseMember = baseMembers.get(code);
            if (baseMember == null) {
                boolean requiredNew = "properties".equals(arrayField)
                        && targetMember.path("required").asBoolean(false);
                changes.add(arrayField + ":" + code + " added" + (requiredNew ? " (required)" : ""));
                bump = max(bump, requiredNew ? ModelSemVer.Bump.MAJOR : ModelSemVer.Bump.MINOR);
            } else {
                bump = max(bump, classifyMemberChange(arrayField, code, baseMember, targetMember, changes));
            }
        }
        return bump;
    }

    private ModelSemVer.Bump classifyMemberChange(String arrayField, String code,
                                                  JsonNode baseMember, JsonNode targetMember,
                                                  List<String> changes) {
        if (baseMember.equals(targetMember)) {
            return ModelSemVer.Bump.PATCH;
        }
        String label = arrayField + ":" + code;
        JsonNode baseStripped = stripDisplay(baseMember, displayFieldsOf(arrayField));
        JsonNode targetStripped = stripDisplay(targetMember, displayFieldsOf(arrayField));
        if (baseStripped.equals(targetStripped)) {
            changes.add(label + " display-only");
            return ModelSemVer.Bump.PATCH;
        }
        ModelSemVer.Bump bump = ModelSemVer.Bump.PATCH;
        for (String field : unionFieldNames(baseStripped, targetStripped)) {
            JsonNode before = baseStripped.path(field);
            JsonNode after = targetStripped.path(field);
            if (before.equals(after)) {
                continue;
            }
            ModelSemVer.Bump fieldBump = classifyFieldChange(field, before, after);
            changes.add(label + " field " + field + " -> " + fieldBump);
            bump = max(bump, fieldBump);
        }
        return bump;
    }

    /** §7.1 字段级分类：类型/单位/required 提升/范围收紧/枚举收缩等 → MAJOR；放宽类 → MINOR；其余结构变化保守 MAJOR。 */
    private ModelSemVer.Bump classifyFieldChange(String field, JsonNode before, JsonNode after) {
        switch (field) {
            case "min":
                return rangeChange(before, after, true);
            case "max":
                return rangeChange(before, after, false);
            case "enumValues":
            case "bitmapValues":
            case "standardMappings":
                return containsAll(after, before) ? ModelSemVer.Bump.MINOR : ModelSemVer.Bump.MAJOR;
            case "required":
                return after.asBoolean(false) ? ModelSemVer.Bump.MAJOR : ModelSemVer.Bump.MINOR;
            default:
                return ModelSemVer.Bump.MAJOR;
        }
    }

    /** 下界上调或新增下界 = 收紧；上界下调或新增上界 = 收紧；反之为放宽。 */
    private ModelSemVer.Bump rangeChange(JsonNode before, JsonNode after, boolean lowerBound) {
        if (before.isMissingNode() || before.isNull()) {
            return ModelSemVer.Bump.MAJOR;
        }
        if (after.isMissingNode() || after.isNull()) {
            return ModelSemVer.Bump.MINOR;
        }
        int comparison = decimalOf(after).compareTo(decimalOf(before));
        boolean tightened = lowerBound ? comparison > 0 : comparison < 0;
        return tightened ? ModelSemVer.Bump.MAJOR : ModelSemVer.Bump.MINOR;
    }

    private ModelSemVer.Bump diffRoot(JsonNode base, JsonNode target, List<String> changes) {
        JsonNode baseStripped = stripRoot(base);
        JsonNode targetStripped = stripRoot(target);
        if (baseStripped.equals(targetStripped)) {
            return base.equals(target) ? ModelSemVer.Bump.PATCH : ModelSemVer.Bump.PATCH;
        }
        changes.add("root structural fields changed");
        return ModelSemVer.Bump.MAJOR;
    }

    private JsonNode stripRoot(JsonNode template) {
        if (!template.isObject()) {
            return template;
        }
        ObjectNode stripped = ((ObjectNode) template).deepCopy();
        for (String field : ROOT_DISPLAY_FIELDS) {
            stripped.remove(field);
        }
        for (String[] memberType : MEMBER_TYPES) {
            stripped.remove(memberType[0]);
        }
        return stripped;
    }

    private static Map<String, JsonNode> indexByCode(JsonNode members, String codeField) {
        Map<String, JsonNode> index = new LinkedHashMap<>();
        if (members.isArray()) {
            for (JsonNode member : members) {
                JsonNode code = member.path(codeField);
                if (code.isTextual()) {
                    index.put(code.textValue(), member);
                }
            }
        }
        return index;
    }

    private static JsonNode stripDisplay(JsonNode member, String[] displayFields) {
        if (!member.isObject()) {
            return member;
        }
        ObjectNode stripped = ((ObjectNode) member).deepCopy();
        for (String field : displayFields) {
            stripped.remove(field);
        }
        return stripped;
    }

    private static String[] displayFieldsOf(String arrayField) {
        for (String[] mapping : DISPLAY_FIELDS) {
            if (mapping[0].equals(arrayField)) {
                String[] fields = new String[mapping.length - 1];
                System.arraycopy(mapping, 1, fields, 0, fields.length);
                return fields;
            }
        }
        return new String[0];
    }

    private static List<String> unionFieldNames(JsonNode left, JsonNode right) {
        List<String> names = new ArrayList<>();
        collectFieldNames(left, names);
        collectFieldNames(right, names);
        Collections.sort(names);
        return names;
    }

    private static void collectFieldNames(JsonNode node, List<String> names) {
        if (!node.isObject()) {
            return;
        }
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            String name = iterator.next();
            if (!names.contains(name)) {
                names.add(name);
            }
        }
    }

    private static boolean containsAll(JsonNode candidateSuperset, JsonNode subset) {
        if (!candidateSuperset.isArray() || !subset.isArray()) {
            return false;
        }
        for (JsonNode item : subset) {
            boolean found = false;
            for (JsonNode candidate : candidateSuperset) {
                if (candidate.equals(item)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal decimalOf(JsonNode node) {
        return new BigDecimal(node.asText());
    }

    private static ModelSemVer.Bump max(ModelSemVer.Bump left, ModelSemVer.Bump right) {
        return left.compareTo(right) <= 0 ? left : right;
    }
}
