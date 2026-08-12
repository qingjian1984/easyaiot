package com.basiclab.iot.sink.protocol.polling;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class IndustrialDeviceConfig {

    private String type;
    private Boolean enabled = true;
    /** collector 已发布快照中的站点编码；中心轮询形态可不提供。 */
    private String siteCode;
    /** collector 已发布快照版本；中心轮询形态可不提供。 */
    private Long configVersion;
    private String host;
    private Integer port;
    private Integer unitId = 1;
    private String serialPort;
    private Integer baudRate = 9600;
    private Integer dataBits = 8;
    private String stopBits = "1";
    private String parity = "NONE";
    private Integer transmitDelayMs = 0;
    private Boolean rs485Mode = true;
    private String endpointUrl;
    private String username;
    private String password;
    private Long pollIntervalMs = 5000L;
    private List<Point> points = new ArrayList<>();

    @Data
    public static class Point {
        /**
         * 绑定的物模型属性标识（上下行键）。未填时回退到 {@link #identifier}。
         */
        private String propertyCode;
        /**
         * 点位本地名称；兼容旧配置时也可作为物模型键。
         */
        private String identifier;
        private String function = "HOLDING_REGISTER";
        private Integer address;
        private Integer quantity = 1;
        private String dataType = "UINT16";
        private Integer valueRadix = 16;
        private String byteOrder = "BIG_ENDIAN";
        private String wordOrder = "BIG_ENDIAN";
        private Double scale = 1D;
        private Double offset = 0D;
        /** collector 快照固化的数据优先级；不得由中心按当前模板重算。 */
        private String dataPriority;
        private String nodeId;
        private Boolean writable = false;

        /** 上报/下发使用的物模型属性键：优先 propertyCode，兼容旧 identifier。 */
        public String resolvedPropertyCode() {
            return StrUtil.blankToDefault(propertyCode, identifier);
        }

        public boolean hasResolvedPropertyCode() {
            return StrUtil.isNotBlank(resolvedPropertyCode());
        }
    }

    @SuppressWarnings("unchecked")
    public static IndustrialDeviceConfig parse(String extension) {
        if (StrUtil.isBlank(extension) || !JSONUtil.isTypeJSON(extension)) {
            return null;
        }
        Map<String, Object> root = JSONUtil.toBean(extension, Map.class);
        Object config = root.get("protocolConfig");
        if (config == null) {
            config = root;
        }
        return JSONUtil.toBean(JSONUtil.parseObj(config), IndustrialDeviceConfig.class);
    }

    public boolean isEnabled() {
        return !Boolean.FALSE.equals(enabled);
    }

    public long pollingInterval() {
        return pollIntervalMs == null ? 5000L : Math.max(1000L, pollIntervalMs);
    }
}
