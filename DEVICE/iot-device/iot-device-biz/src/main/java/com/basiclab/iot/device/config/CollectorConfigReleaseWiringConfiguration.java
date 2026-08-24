package com.basiclab.iot.device.config;

import com.basiclab.iot.device.service.collector.CollectorConfigReleaseObservedFactRecorder;
import com.basiclab.iot.device.service.collector.StructuredCollectorConfigReleaseObservedFactRecorder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OPEN03-03 collector release 内部服务装配（2026-08-22 修复）。
 *
 * <p>真实容器中 EASYAIOT_INTERNAL_SERVICE_AUTH_ENABLED=true 首次开启时，
 * CollectorConfigReleaseInternalService 先于组件扫描注册的 Recorder 实例化，
 * 导致 NoSuchBeanDefinition 启动失败（组件扫描 + @ConditionalOnMissingBean
 * 在该装配顺序下不可靠）。改为显式 @Bean 装配，注册顺序确定。</p>
 */
@Configuration(proxyBeanMethods = false)
public class CollectorConfigReleaseWiringConfiguration {

    @Bean
    public CollectorConfigReleaseObservedFactRecorder collectorConfigReleaseObservedFactRecorder() {
        return new StructuredCollectorConfigReleaseObservedFactRecorder();
    }
}
