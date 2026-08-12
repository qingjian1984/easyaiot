package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.common.core.util.TenantUtils;
import com.basiclab.iot.sink.config.IotGatewayProperties;
import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.dal.mapper.DeviceMapper;
import com.basiclab.iot.sink.enums.IotDeviceMessageMethodEnum;
import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;
import com.basiclab.iot.sink.messagebus.publisher.message.IotDeviceMessageService;
import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.messagebus.core.IotMessageSubscriber;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.service.DeviceServerIdService;
import com.basiclab.iot.sink.util.IotDeviceMessageUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractIndustrialPollingProtocol implements IotMessageSubscriber<IotDeviceMessage> {

    private final String protocolType;
    private final String serverId;
    private final IotGatewayProperties.PollingProtocolProperties properties;
    protected final DeviceMapper deviceMapper;
    protected final IotDeviceMessageService messageService;
    private final IotMessageBus messageBus;
    private final DeviceServerIdService deviceServerIdService;
    private final CollectorTelemetryWriter collectorTelemetryWriter;

    /** 同一设备连续失败时，完整堆栈仅首次打印；之后按该间隔汇总，避免刷屏 */
    private static final long FAIL_LOG_INTERVAL_MS = 60_000L;

    private final Map<Long, Long> nextPollTimes = new ConcurrentHashMap<>();
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<Long, DeviceFailLogState> failLogStates = new ConcurrentHashMap<>();
    private ScheduledExecutorService scanner;
    private ExecutorService workers;

    private static final class DeviceFailLogState {
        private long consecutiveFails;
        private long lastWarnAt;
        private long suppressedSinceLastWarn;
    }

    protected AbstractIndustrialPollingProtocol(String protocolType,
                                                String serverId,
                                                IotGatewayProperties.PollingProtocolProperties properties,
                                                DeviceMapper deviceMapper,
                                                IotDeviceMessageService messageService,
                                                IotMessageBus messageBus,
                                                DeviceServerIdService deviceServerIdService) {
        this(protocolType, serverId, properties, deviceMapper, messageService, messageBus,
                deviceServerIdService, null);
    }

    protected AbstractIndustrialPollingProtocol(String protocolType,
                                                String serverId,
                                                IotGatewayProperties.PollingProtocolProperties properties,
                                                DeviceMapper deviceMapper,
                                                IotDeviceMessageService messageService,
                                                IotMessageBus messageBus,
                                                DeviceServerIdService deviceServerIdService,
                                                CollectorTelemetryWriter collectorTelemetryWriter) {
        this.protocolType = protocolType;
        this.serverId = serverId;
        this.properties = properties;
        this.deviceMapper = deviceMapper;
        this.messageService = messageService;
        this.messageBus = messageBus;
        this.deviceServerIdService = deviceServerIdService;
        this.collectorTelemetryWriter = collectorTelemetryWriter;
    }

    @PostConstruct
    public void start() {
        workers = Executors.newFixedThreadPool(Math.max(1, properties.getWorkerThreads()), runnable -> {
            Thread thread = new Thread(runnable, "iot-" + protocolType.toLowerCase() + "-worker");
            thread.setDaemon(true);
            return thread;
        });
        scanner = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "iot-" + protocolType.toLowerCase() + "-scanner");
            thread.setDaemon(true);
            return thread;
        });
        scanner.scheduleWithFixedDelay(this::scanDevices, 0,
                Math.max(500L, properties.getScanIntervalMs()), TimeUnit.MILLISECONDS);
        messageBus.register(this);
        log.info("[start][{} protocol polling started, serverId: {}]", protocolType, serverId);
    }

    private void scanDevices() {
        try {
            List<DeviceDO> devices = deviceMapper.selectPollingDevices(protocolType);
            long now = System.currentTimeMillis();
            for (DeviceDO device : devices) {
                IndustrialDeviceConfig config = IndustrialDeviceConfig.parse(device.getExtension());
                if (config == null || !config.isEnabled() || config.getPoints() == null || config.getPoints().isEmpty()) {
                    continue;
                }
                long nextPollTime = nextPollTimes.getOrDefault(device.getId(), 0L);
                if (now < nextPollTime || !inFlight.add(device.getId())) {
                    continue;
                }
                nextPollTimes.put(device.getId(), now + config.pollingInterval());
                workers.submit(() -> pollSafely(device, config));
            }
        } catch (Exception e) {
            log.error("[scanDevices][failed to scan {} devices]", protocolType, e);
        }
    }

    private void pollSafely(DeviceDO device, IndustrialDeviceConfig config) {
        try {
            TenantUtils.execute(device.getTenantId(), () -> {
                try {
                    Map<String, Object> values = poll(device, config);
                    if (values != null && !values.isEmpty()) {
                        reportProperties(device, config, values);
                    }
                    markPollSuccess(device.getId());
                } catch (Exception e) {
                    deviceMapper.updatePollingDeviceStatus(device.getId(), device.getTenantId(), "OFFLINE", null);
                    logPollFailure(device, config, e);
                }
            });
        } finally {
            inFlight.remove(device.getId());
        }
    }

    private void markPollSuccess(Long deviceId) {
        DeviceFailLogState prev = failLogStates.remove(deviceId);
        if (prev != null && prev.consecutiveFails > 0) {
            log.info("[pollSafely][{} device poll recovered, deviceId: {}, previousFails: {}]",
                    protocolType, deviceId, prev.consecutiveFails);
        }
    }

    private void logPollFailure(DeviceDO device, IndustrialDeviceConfig config, Exception e) {
        Long deviceId = device.getId();
        String address = connectionAddress(device, config);
        String errMsg = rootCauseMessage(e);
        long now = System.currentTimeMillis();
        DeviceFailLogState state = failLogStates.computeIfAbsent(deviceId, id -> new DeviceFailLogState());
        state.consecutiveFails++;

        if (state.consecutiveFails == 1) {
            // 首次失败：带堆栈，便于排查
            log.warn("[pollSafely][{} device poll failed, deviceId: {}, address: {}]",
                    protocolType, deviceId, address, e);
            state.lastWarnAt = now;
            state.suppressedSinceLastWarn = 0;
            return;
        }

        long elapsed = now - state.lastWarnAt;
        if (elapsed >= FAIL_LOG_INTERVAL_MS) {
            log.warn("[pollSafely][{} device poll still failing, deviceId: {}, address: {}, consecutiveFails: {}, "
                            + "suppressed: {}, lastError: {}]",
                    protocolType, deviceId, address, state.consecutiveFails,
                    state.suppressedSinceLastWarn, errMsg);
            state.lastWarnAt = now;
            state.suppressedSinceLastWarn = 0;
            return;
        }

        state.suppressedSinceLastWarn++;
        if (log.isDebugEnabled()) {
            log.debug("[pollSafely][{} device poll failed, deviceId: {}, address: {}, consecutiveFails: {}]",
                    protocolType, deviceId, address, state.consecutiveFails, e);
        }
    }

    private static String rootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause.getMessage() != null && !cause.getMessage().isEmpty()) {
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return cause.getClass().getSimpleName();
    }

    private void reportProperties(DeviceDO device, IndustrialDeviceConfig config, Map<String, Object> values) {
        if (collectorTelemetryWriter != null) {
            collectorTelemetryWriter.store(device, config, values, protocolType);
            deviceMapper.updatePollingDeviceStatus(device.getId(), device.getTenantId(), "ONLINE", LocalDateTime.now());
            return;
        }
        String topic = IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT.buildTopic(
                device.getProductIdentification(), device.getDeviceIdentification());
        IotDeviceMessage message = IotDeviceMessage.requestOf(
                IotDeviceMessageMethodEnum.PROPERTY_POST.getMethod(), values);
        message.setTopic(topic);
        message.setNeedReply(false);
        messageService.sendDeviceMessage(message, device.getProductIdentification(),
                device.getDeviceIdentification(), serverId);
        deviceServerIdService.saveDeviceServerId(device.getId(), serverId);
        deviceMapper.updatePollingDeviceStatus(device.getId(), device.getTenantId(), "ONLINE", LocalDateTime.now());
    }

    @Override
    public String getTopic() {
        return IotDeviceMessageUtils.buildMessageBusGatewayDeviceMessageTopic(serverId);
    }

    @Override
    public String getGroup() {
        return getTopic();
    }

    @Override
    public void onMessage(IotDeviceMessage message) {
        if (message == null || message.getTenantId() == null) {
            return;
        }
        IotDeviceTopicEnum topicEnum = message.getTopic() != null
                ? IotDeviceTopicEnum.matchTopic(message.getTopic()) : null;
        if (topicEnum != null && !topicEnum.isNeedReply()) {
            return;
        }
        boolean propertySet = topicEnum == IotDeviceTopicEnum.PROPERTY_DOWNSTREAM_DESIRED_SET
                || topicEnum == IotDeviceTopicEnum.SUB_PROPERTY_DOWNSTREAM_DESIRED_SET
                || IotDeviceMessageMethodEnum.PROPERTY_SET.getMethod().equals(message.getMethod());
        if (!propertySet) {
            return;
        }
        Long deviceId = IotDeviceMessageUtils.parseLongDeviceIdOrNull(message.getDeviceId());
        if (deviceId == null) {
            return;
        }
        TenantUtils.execute(message.getTenantId(), () -> {
            DeviceDO device = deviceMapper.selectById(deviceId);
            IndustrialDeviceConfig config = device == null ? null : IndustrialDeviceConfig.parse(device.getExtension());
            if (device == null || config == null || !config.isEnabled()) {
                return;
            }
            if (config.getType() != null && !protocolType.equalsIgnoreCase(config.getType())) {
                return;
            }
            try {
                write(device, config, message);
                replyDesiredSetAck(device, message, true, "ok");
            } catch (Exception e) {
                log.error("[onMessage][{} device write failed, deviceId: {}]", protocolType, deviceId, e);
                replyDesiredSetAck(device, message, false,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
    }

    private void replyDesiredSetAck(DeviceDO device, IotDeviceMessage request, boolean success, String msg) {
        try {
            String topic = IotDeviceTopicEnum.PROPERTY_UPSTREAM_DESIRED_SET_ACK.buildTopic(
                    device.getProductIdentification(), device.getDeviceIdentification());
            IotDeviceMessage ack = IotDeviceMessage.replyOf(
                    request.getRequestId(),
                    IotDeviceMessageMethodEnum.PROPERTY_SET.getMethod(),
                    request.getParams(),
                    success ? 0 : 500,
                    msg);
            ack.setTopic(topic);
            ack.setNeedReply(false);
            messageService.sendDeviceMessage(ack, device.getProductIdentification(),
                    device.getDeviceIdentification(), serverId);
        } catch (Exception e) {
            log.warn("[replyDesiredSetAck][{} ACK failed, deviceId: {}]", protocolType, device.getId(), e);
        }
    }

    protected abstract Map<String, Object> poll(DeviceDO device, IndustrialDeviceConfig config) throws Exception;

    protected abstract void write(DeviceDO device, IndustrialDeviceConfig config,
                                  IotDeviceMessage message) throws Exception;

    protected abstract String connectionAddress(DeviceDO device, IndustrialDeviceConfig config);

    protected long requestTimeoutMs() {
        return Math.max(1000L, properties.getRequestTimeoutMs());
    }

    @PreDestroy
    public void stop() {
        if (scanner != null) {
            scanner.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }
}
