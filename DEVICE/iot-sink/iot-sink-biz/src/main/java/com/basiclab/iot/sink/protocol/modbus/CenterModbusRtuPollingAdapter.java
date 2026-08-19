package com.basiclab.iot.sink.protocol.modbus;

import com.basiclab.iot.sink.config.IotGatewayProperties;
import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.dal.mapper.DeviceMapper;
import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.messagebus.publisher.message.IotDeviceMessageService;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.protocol.polling.AbstractIndustrialPollingProtocol;
import com.basiclab.iot.sink.protocol.polling.IndustrialDeviceConfig;
import com.basiclab.iot.sink.service.DeviceServerIdService;
import com.basiclab.iot.sink.util.IotDeviceMessageUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Center-profile bridge for the central polling lifecycle. All RTU serial
 * reads, writes, and decoding remain in {@link IotModbusRtuPollingProtocol}.
 */
public final class CenterModbusRtuPollingAdapter extends AbstractIndustrialPollingProtocol {

    private final IotModbusRtuPollingProtocol engine;

    public CenterModbusRtuPollingAdapter(IotGatewayProperties.PollingProtocolProperties properties,
                                         DeviceMapper deviceMapper,
                                         IotDeviceMessageService messageService,
                                         IotMessageBus messageBus,
                                         DeviceServerIdService deviceServerIdService,
                                         String serverId) {
        this(properties, deviceMapper, messageService, messageBus, deviceServerIdService, serverId,
                new IotModbusRtuPollingProtocol(properties.getRequestTimeoutMs()));
    }

    public CenterModbusRtuPollingAdapter(IotGatewayProperties.PollingProtocolProperties properties,
                                         DeviceMapper deviceMapper,
                                         IotDeviceMessageService messageService,
                                         IotMessageBus messageBus,
                                         DeviceServerIdService deviceServerIdService,
                                         String serverId,
                                         IotModbusRtuPollingProtocol engine) {
        super(IotModbusRtuPollingProtocol.PROTOCOL_TYPE, serverId, properties, deviceMapper,
                messageService, messageBus, deviceServerIdService);
        this.engine = engine;
    }

    @Override
    protected Map<String, Object> poll(DeviceDO device, IndustrialDeviceConfig config) throws Exception {
        return engine.poll(config);
    }

    @Override
    protected void write(DeviceDO device, IndustrialDeviceConfig config, IotDeviceMessage message) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (IndustrialDeviceConfig.Point point : config.getPoints()) {
            if (point == null || !point.hasResolvedPropertyCode()) {
                continue;
            }
            Object value = IotDeviceMessageUtils.extractPropertyValue(message, point.resolvedPropertyCode());
            if (value != null) {
                values.put(point.resolvedPropertyCode(), value);
            }
        }
        engine.write(config, values);
    }

    @Override
    protected String connectionAddress(DeviceDO device, IndustrialDeviceConfig config) {
        return config.getSerialPort();
    }
}
