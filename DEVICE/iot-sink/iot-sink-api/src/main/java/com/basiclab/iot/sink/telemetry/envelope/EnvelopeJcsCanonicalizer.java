package com.basiclab.iot.sink.telemetry.envelope;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * JCS（RFC 8785，{@code jcs-rfc8785-v1}）canonical JSON。
 * 移植自 iot-device JcsCanonicalizer（TD-005 §6）。
 * 对象键按 UTF-16 代码单元排序（Java String.compareTo 与 ECMAScript 同序）。
 */
public final class EnvelopeJcsCanonicalizer {

    /** canonicalize JsonNode → JCS canonical String（无空白，键排序，数值 ECMAScript 格式化）。 */
    public String canonicalize(JsonNode value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private void write(JsonNode value, StringBuilder out) {
        if (value == null || value.isNull()) {
            out.append("null");
        } else if (value.isBoolean()) {
            out.append(value.booleanValue() ? "true" : "false");
        } else if (value.isTextual()) {
            writeString(value.textValue(), out);
        } else if (value.isIntegralNumber()) {
            out.append(value.bigIntegerValue().toString());
        } else if (value.isNumber()) {
            out.append(EnvelopeJcsNumberFormatter.format(value.doubleValue()));
        } else if (value.isArray()) {
            out.append('[');
            for (int i = 0; i < value.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(value.get(i), out);
            }
            out.append(']');
        } else if (value.isObject()) {
            List<Map.Entry<String, JsonNode>> members = new ArrayList<>();
            value.fields().forEachRemaining(members::add);
            members.sort(Comparator.comparing(Map.Entry::getKey));
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> member : members) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(member.getKey(), out);
                out.append(':');
                write(member.getValue(), out);
            }
            out.append('}');
        } else {
            throw new IllegalArgumentException("ENVELOPE_JCS_UNSUPPORTED_NODE: " + value.getNodeType());
        }
    }

    private void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
