package com.basiclab.iot.device.alarm.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TD-006 冻结的告警 Envelope 事件类型。
 *
 * <p>来源事件使用 {@code device.alarm.source-event.v1} 作为 Envelope
 * 事件类型，来源动作 {@code RAISED/RECOVERED} 仍由来源载荷表达；两者不
 * 构成对外领域事件 {@code created.v1/recovered.v1} 的别名。</p>
 */
public enum AlarmEventType {

    CREATED("device.alarm.created.v1"),
    OCCURRENCE_RECORDED("device.alarm.occurrence-recorded.v1"),
    RECOVERED("device.alarm.recovered.v1"),
    STATUS_CHANGED("device.alarm.status-changed.v1"),
    ESCALATED("device.alarm.escalated.v1"),
    SUPPRESSION_DECIDED("device.alarm.suppression-decided.v1"),
    SOURCE_EVENT("device.alarm.source-event.v1");

    private static final Map<String, AlarmEventType> BY_VALUE;

    static {
        Map<String, AlarmEventType> values = new LinkedHashMap<>();
        for (AlarmEventType type : values()) {
            values.put(type.value, type);
        }
        BY_VALUE = Collections.unmodifiableMap(values);
    }

    private final String value;

    AlarmEventType(String value) {
        this.value = value;
    }

    /** 完整、带版本的事件名称。 */
    public String value() {
        return value;
    }

    /** JavaBean 风格别名，便于序列化适配器使用。 */
    public String getValue() {
        return value;
    }

    /** 事件类型文本别名。 */
    public String eventType() {
        return value;
    }

    /** 按冻结事件名称查找，未知值返回 {@code null}。 */
    public static AlarmEventType fromValue(String value) {
        return value == null ? null : BY_VALUE.get(value);
    }

    /** 判断是否为冻结事件名称。 */
    public static boolean isKnown(String value) {
        return fromValue(value) != null;
    }
}
