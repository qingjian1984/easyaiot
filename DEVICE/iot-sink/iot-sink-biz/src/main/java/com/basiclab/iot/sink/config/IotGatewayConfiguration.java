package com.basiclab.iot.sink.config;

import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.protocol.emqx.IotEmqxAuthEventProtocol;
import com.basiclab.iot.sink.protocol.emqx.IotEmqxDownstreamSubscriber;
import com.basiclab.iot.sink.protocol.emqx.IotEmqxUpstreamProtocol;
import com.basiclab.iot.sink.protocol.http.IotHttpDownstreamSubscriber;
import com.basiclab.iot.sink.protocol.http.IotHttpUpstreamProtocol;
import com.basiclab.iot.sink.protocol.mqtt.IotMqttDownstreamSubscriber;
import com.basiclab.iot.sink.protocol.mqtt.IotMqttUpstreamProtocol;
import com.basiclab.iot.sink.protocol.mqtt.manager.IotMqttConnectionManager;
import com.basiclab.iot.sink.protocol.mqtt.router.IotMqttDownstreamHandler;
import com.basiclab.iot.sink.protocol.modbus.IotModbusPollingProtocol;
import com.basiclab.iot.sink.protocol.modbus.CenterModbusRtuPollingAdapter;
import com.basiclab.iot.sink.protocol.opcua.IotOpcUaPollingProtocol;
import com.basiclab.iot.sink.protocol.polling.CollectorTelemetryWriter;
import com.basiclab.iot.sink.protocol.polling.CollectorPollingRuntime;
import com.basiclab.iot.sink.protocol.polling.LocalFilePollingConfigProvider;
import com.basiclab.iot.sink.protocol.polling.LocalFilePollingStatusReporter;
import com.basiclab.iot.sink.polling.PollingConfigProvider;
import com.basiclab.iot.sink.polling.PollingStatusReporter;
import com.basiclab.iot.sink.protocol.modbus.IotModbusRtuPollingProtocol;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.sink.protocol.tcp.IotTcpDownstreamSubscriber;
import com.basiclab.iot.sink.protocol.tcp.IotTcpUpstreamProtocol;
import com.basiclab.iot.sink.protocol.tcp.manager.IotTcpConnectionManager;
import com.basiclab.iot.common.service.RedisService;
import com.basiclab.iot.sink.messagebus.publisher.IotDeviceService;
import com.basiclab.iot.sink.messagebus.publisher.message.IotDeviceMessageService;
import com.basiclab.iot.sink.dal.mapper.DeviceMapper;
import com.basiclab.iot.sink.util.IotDeviceMessageUtils;
import com.basiclab.iot.sink.service.DeviceServerIdService;
import com.basiclab.iot.sink.service.impl.DeviceServerIdServiceImpl;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(IotGatewayProperties.class)
@Slf4j
public class IotGatewayConfiguration {

    @Bean
    @Profile("collector")
    public CollectorTelemetryWriter collectorTelemetryWriter(TelemetryOutboxPort telemetryOutboxPort) {
        return new CollectorTelemetryWriter(telemetryOutboxPort, Duration.ofSeconds(5));
    }

    @Bean
    @Profile("collector")
    public LocalFilePollingConfigProvider collectorPollingConfigProvider(
            @Value("${easyaiot.collector.config-directory:/var/lib/easyaiot/config}") String configDirectory,
            @Value("${easyaiot.collector.workload-id:}") String workloadId) {
        return new LocalFilePollingConfigProvider(java.nio.file.Path.of(configDirectory), workloadId);
    }

    @Bean
    @Profile("collector")
    public PollingStatusReporter pollingStatusReporter(LocalFilePollingConfigProvider provider) {
        return new LocalFilePollingStatusReporter(provider);
    }

    @Bean
    @Profile("collector")
    public IotModbusRtuPollingProtocol collectorModbusRtuEngine(
            @Value("${basiclab.iot.sink.protocol.modbus-rtu.request-timeout-ms:5000}") long requestTimeoutMs) {
        return new IotModbusRtuPollingProtocol(requestTimeoutMs);
    }

    @Bean(destroyMethod = "close")
    @Profile("collector")
    public CollectorPollingRuntime collectorPollingRuntime(PollingConfigProvider provider,
                                                           PollingStatusReporter statusReporter,
                                                           IotModbusRtuPollingProtocol engine,
                                                           CollectorTelemetryWriter telemetryWriter,
                                                           @Value("${easyaiot.collector.reconcile-interval-ms:1000}") long reconcileIntervalMs) {
        return new CollectorPollingRuntime(provider, statusReporter, engine, telemetryWriter,
                Duration.ofMillis(Math.max(1L, reconcileIntervalMs)));
    }

    /**
 * IotGatewayConfiguration
 *
 * @author 翱翔的雄库鲁
 * @email andywebjava@163.com
 * @wechat EasyAIoT2025
 */

    @Bean
    @ConditionalOnMissingBean(DeviceServerIdService.class)
    @Profile("!collector")
    public DeviceServerIdService deviceServerIdService(RedisService redisService) {
        return new DeviceServerIdServiceImpl(redisService);
    }

@Configuration
    @Profile("!collector")
    @ConditionalOnProperty(prefix = "basiclab.iot.sink.protocol.http", name = "enabled", havingValue = "true")
    @Slf4j
    public static class HttpProtocolConfiguration {

        @Bean
        public IotHttpUpstreamProtocol iotHttpUpstreamProtocol(IotGatewayProperties gatewayProperties) {
            return new IotHttpUpstreamProtocol(gatewayProperties.getProtocol().getHttp());
        }

        @Bean
        public IotHttpDownstreamSubscriber iotHttpDownstreamSubscriber(IotHttpUpstreamProtocol httpUpstreamProtocol,
                @org.springframework.context.annotation.Lazy IotMessageBus messageBus) {
            return new IotHttpDownstreamSubscriber(httpUpstreamProtocol, messageBus);
        }
    }

    /**
     * IoT 网关 EMQX 协议配置类
     */
    @Configuration
    @Profile("!collector")
    @ConditionalOnProperty(prefix = "basiclab.iot.sink.protocol.emqx", name = "enabled", havingValue = "true")
    @Slf4j
    public static class EmqxProtocolConfiguration {

        @Bean(destroyMethod = "close")
        public Vertx emqxVertx() {
            return Vertx.vertx();
        }

        @Bean
        public IotEmqxAuthEventProtocol iotEmqxAuthEventProtocol(IotGatewayProperties gatewayProperties,
                Vertx emqxVertx) {
            return new IotEmqxAuthEventProtocol(gatewayProperties.getProtocol().getEmqx(), emqxVertx);
        }

        @Bean
        public IotEmqxUpstreamProtocol iotEmqxUpstreamProtocol(IotGatewayProperties gatewayProperties,
                Vertx emqxVertx) {
            return new IotEmqxUpstreamProtocol(gatewayProperties.getProtocol().getEmqx(), emqxVertx);
        }

        @Bean
        public IotEmqxDownstreamSubscriber iotEmqxDownstreamSubscriber(IotEmqxUpstreamProtocol mqttUpstreamProtocol,
                @org.springframework.context.annotation.Lazy IotMessageBus messageBus) {
            return new IotEmqxDownstreamSubscriber(mqttUpstreamProtocol, messageBus);
        }
    }

    /**
     * IoT 网关 TCP 协议配置类
     */
    @Configuration
    @Profile("!collector")
    @ConditionalOnProperty(prefix = "basiclab.iot.sink.protocol.tcp", name = "enabled", havingValue = "true")
    @Slf4j
    public static class TcpProtocolConfiguration {

        @Bean(destroyMethod = "close")
        public Vertx tcpVertx() {
            return Vertx.vertx();
        }

        @Bean
        public IotTcpUpstreamProtocol iotTcpUpstreamProtocol(IotGatewayProperties gatewayProperties,
                                                             IotDeviceService deviceService,
                                                             IotDeviceMessageService messageService,
                                                             IotTcpConnectionManager connectionManager,
                                                             Vertx tcpVertx) {
            return new IotTcpUpstreamProtocol(gatewayProperties.getProtocol().getTcp(),
                    deviceService, messageService, connectionManager, tcpVertx);
        }

        @Bean
        public IotTcpDownstreamSubscriber iotTcpDownstreamSubscriber(IotTcpUpstreamProtocol protocolHandler,
                                                                     IotDeviceMessageService messageService,
                                                                     IotDeviceService deviceService,
                                                                     IotTcpConnectionManager connectionManager,
                                                                     @Lazy IotMessageBus messageBus) {
            return new IotTcpDownstreamSubscriber(protocolHandler, messageService, deviceService, connectionManager,
                    messageBus);
        }

    }

    @Configuration
    @Profile("!collector")
    @ConditionalOnProperty(prefix = "basiclab.iot.sink.protocol.modbus", name = "enabled", havingValue = "true")
    public static class ModbusProtocolConfiguration {

        @Bean
        public IotModbusPollingProtocol iotModbusPollingProtocol(IotGatewayProperties gatewayProperties,
                                                                  DeviceMapper deviceMapper,
                                                                  IotDeviceMessageService messageService,
                                                                  @Lazy IotMessageBus messageBus,
                                                                  DeviceServerIdService deviceServerIdService,
                                                                  org.springframework.beans.factory.ObjectProvider<CollectorTelemetryWriter> writer) {
            return new IotModbusPollingProtocol(gatewayProperties.getProtocol().getModbus(), deviceMapper,
                    messageService, messageBus, deviceServerIdService, IotDeviceMessageUtils.generateServerId(1502),
                    writer.getIfAvailable());
        }
    }

    @Configuration
    @Profile("!collector")
    @ConditionalOnProperty(prefix = "basiclab.iot.sink.protocol.modbus-rtu", name = "enabled", havingValue = "true")
    public static class ModbusRtuProtocolConfiguration {

        @Bean
        public CenterModbusRtuPollingAdapter centerModbusRtuPollingAdapter(IotGatewayProperties gatewayProperties,
                                                                            DeviceMapper deviceMapper,
                                                                            IotDeviceMessageService messageService,
                                                                            @Lazy IotMessageBus messageBus,
                                                                            DeviceServerIdService deviceServerIdService) {
            return new CenterModbusRtuPollingAdapter(gatewayProperties.getProtocol().getModbusRtu(), deviceMapper,
                    messageService, messageBus, deviceServerIdService,
                    IotDeviceMessageUtils.generateServerId(1503));
        }
    }

    @Configuration
    @Profile("!collector")
    @ConditionalOnProperty(prefix = "basiclab.iot.sink.protocol.opcua", name = "enabled", havingValue = "true")
    public static class OpcUaProtocolConfiguration {

        @Bean
        public IotOpcUaPollingProtocol iotOpcUaPollingProtocol(IotGatewayProperties gatewayProperties,
                                                                DeviceMapper deviceMapper,
                                                                IotDeviceMessageService messageService,
                                                                @Lazy IotMessageBus messageBus,
                                                                DeviceServerIdService deviceServerIdService,
                                                                org.springframework.beans.factory.ObjectProvider<CollectorTelemetryWriter> writer) {
            return new IotOpcUaPollingProtocol(gatewayProperties.getProtocol().getOpcua(), deviceMapper,
                    messageService, messageBus, deviceServerIdService, IotDeviceMessageUtils.generateServerId(14840),
                    writer.getIfAvailable());
        }
    }

    /**
     * IoT 网关 MQTT 协议配置类
     */
    @Configuration
    @Profile("!collector")
    @ConditionalOnProperty(prefix = "basiclab.iot.sink.protocol.mqtt", name = "enabled", havingValue = "true")
    @Slf4j
    public static class MqttProtocolConfiguration {

        @Bean(destroyMethod = "close")
        public Vertx mqttVertx() {
            return Vertx.vertx();
        }

        @Bean
        public IotMqttUpstreamProtocol iotMqttUpstreamProtocol(IotGatewayProperties gatewayProperties,
                                                               IotDeviceMessageService messageService,
                                                               IotMqttConnectionManager connectionManager,
                                                               Vertx mqttVertx) {
            return new IotMqttUpstreamProtocol(gatewayProperties.getProtocol().getMqtt(), messageService,
                    connectionManager, mqttVertx);
        }

        @Bean
        public IotMqttDownstreamHandler iotMqttDownstreamHandler(IotDeviceMessageService messageService,
                                                                 IotMqttConnectionManager connectionManager) {
            return new IotMqttDownstreamHandler(messageService, connectionManager);
        }

        @Bean
        public IotMqttDownstreamSubscriber iotMqttDownstreamSubscriber(IotMqttUpstreamProtocol mqttUpstreamProtocol,
                                                                       IotMqttDownstreamHandler downstreamHandler,
                                                                       @Lazy IotMessageBus messageBus) {
            return new IotMqttDownstreamSubscriber(mqttUpstreamProtocol, downstreamHandler, messageBus);
        }

    }

}
