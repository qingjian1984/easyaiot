package com.basiclab.iot.common.security.internal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "easyaiot.security.internal")
public class InternalServiceAuthProperties {

    /** 默认关闭，02A-0 完成前不激活 collector release API。 */
    private boolean enabled = false;
    private long maxClockSkewSeconds = 300;
    private long nonceTtlSeconds = 600;
    private String nonceKeyPrefix = "easyaiot:internal-auth:nonce:";
    /** key 为 serviceId:keyId，value 为配置中心/环境 secret 引用名。 */
    private Map<String, String> keyReferences = new HashMap<>();
    private List<Route> routes = new ArrayList<>();

    @Data
    public static class Route {
        private String method;
        private String path;
    }
}
