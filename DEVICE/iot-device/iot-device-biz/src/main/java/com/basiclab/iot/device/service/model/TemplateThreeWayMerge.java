package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * TD-005 §9：厂家基线升级三方合并（旧标准 B、厂家当前 V、新标准 N）。
 * 成员身份键 = memberType + memberCode；指纹 = 成员 JCS canonical 字节。
 * 纯函数：相同输入必须得到相同 diff（预览按输入三份哈希寻址可复现）。
 * 冲突决策的持久化（快照/哈希/决策人）属后续工作包，本类只产出结论。
 * Java 8 兼容。
 */
public final class TemplateThreeWayMerge {

    private static final String[][] MEMBER_TYPES = {
            {"properties", "propertyCode"},
            {"events", "eventCode"},
            {"services", "serviceCode"},
    };

    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();

    public List<MergeOutcome> merge(JsonNode base, JsonNode vendor, JsonNode standard) {
        List<MergeOutcome> outcomes = new ArrayList<>();
        for (String[] memberType : MEMBER_TYPES) {
            mergeMemberType(memberType[0], memberType[1], base, vendor, standard, outcomes);
        }
        return outcomes;
    }

    private void mergeMemberType(String arrayField, String codeField,
                                 JsonNode base, JsonNode vendor, JsonNode standard,
                                 List<MergeOutcome> outcomes) {
        Map<String, JsonNode> b = indexByCode(base.path(arrayField), codeField);
        Map<String, JsonNode> v = indexByCode(vendor.path(arrayField), codeField);
        Map<String, JsonNode> n = indexByCode(standard.path(arrayField), codeField);

        TreeSet<String> codes = new TreeSet<>();
        codes.addAll(b.keySet());
        codes.addAll(v.keySet());
        codes.addAll(n.keySet());

        for (String code : codes) {
            MergeOutcome outcome = mergeMember(arrayField, code, b.get(code), v.get(code), n.get(code));
            if (outcome != null) {
                outcomes.add(outcome);
            }
        }
    }

    private MergeOutcome mergeMember(String memberType, String code,
                                     JsonNode b, JsonNode v, JsonNode n) {
        String bFp = fingerprint(b);
        String vFp = fingerprint(v);
        String nFp = fingerprint(n);

        if (b != null && v != null && n != null) {
            if (vFp.equals(bFp) && nFp.equals(bFp)) {
                return null; // 三方一致：无变化
            }
            if (vFp.equals(bFp)) {
                return new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_STANDARD, n);
            }
            if (nFp.equals(bFp)) {
                return new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_VENDOR, v);
            }
            if (vFp.equals(nFp)) {
                return new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_COMMON, v);
            }
            return new MergeOutcome(memberType, code, MergeOutcome.Resolution.CONFLICT, null);
        }
        if (b != null && v != null) {
            // 标准删除：厂家未改随标准删除；厂家已改构成删改冲突
            return vFp.equals(bFp)
                    ? new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_DROP, null)
                    : new MergeOutcome(memberType, code, MergeOutcome.Resolution.DELETE_MODIFY_CONFLICT, null);
        }
        if (b != null && n != null) {
            // 厂家删除：标准未改保留厂家删除；标准已改构成删改冲突（对称解释）
            return nFp.equals(bFp)
                    ? new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_VENDOR, null)
                    : new MergeOutcome(memberType, code, MergeOutcome.Resolution.DELETE_MODIFY_CONFLICT, null);
        }
        if (b != null) {
            return new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_DROP, null);
        }
        if (v != null && n != null) {
            return vFp.equals(nFp)
                    ? new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_COMMON, v)
                    : new MergeOutcome(memberType, code, MergeOutcome.Resolution.ADD_ADD_CONFLICT, null);
        }
        if (v != null) {
            return new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_VENDOR, v);
        }
        return new MergeOutcome(memberType, code, MergeOutcome.Resolution.AUTO_STANDARD, n);
    }

    private String fingerprint(JsonNode member) {
        return member == null ? "" : canonicalizer.canonicalize(member);
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
}
