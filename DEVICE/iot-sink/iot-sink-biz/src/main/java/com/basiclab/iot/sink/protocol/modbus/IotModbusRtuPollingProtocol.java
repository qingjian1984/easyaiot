package com.basiclab.iot.sink.protocol.modbus;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.sink.config.IotGatewayProperties;
import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.dal.mapper.DeviceMapper;
import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.messagebus.publisher.message.IotDeviceMessageService;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.protocol.polling.AbstractIndustrialPollingProtocol;
import com.basiclab.iot.sink.protocol.polling.CollectorTelemetryWriter;
import com.basiclab.iot.sink.protocol.polling.IndustrialDeviceConfig;
import com.basiclab.iot.sink.service.DeviceServerIdService;
import com.basiclab.iot.sink.util.IotDeviceMessageUtils;
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.SerialParameters;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Modbus RTU master polling over an RS-485 serial interface. */
public class IotModbusRtuPollingProtocol extends AbstractIndustrialPollingProtocol {

    public static final String PROTOCOL_TYPE = "MODBUS_RTU";

    public IotModbusRtuPollingProtocol(IotGatewayProperties.PollingProtocolProperties properties,
                                       DeviceMapper deviceMapper,
                                       IotDeviceMessageService messageService,
                                       IotMessageBus messageBus,
                                       DeviceServerIdService deviceServerIdService,
                                       String serverId) {
        this(properties, deviceMapper, messageService, messageBus, deviceServerIdService, serverId, null);
    }

    public IotModbusRtuPollingProtocol(IotGatewayProperties.PollingProtocolProperties properties,
                                       DeviceMapper deviceMapper,
                                       IotDeviceMessageService messageService,
                                       IotMessageBus messageBus,
                                       DeviceServerIdService deviceServerIdService,
                                       String serverId,
                                       CollectorTelemetryWriter collectorTelemetryWriter) {
        super(PROTOCOL_TYPE, serverId, properties, deviceMapper, messageService, messageBus,
                deviceServerIdService, collectorTelemetryWriter);
    }

    @Override
    protected Map<String, Object> poll(DeviceDO device, IndustrialDeviceConfig config) throws Exception {
        synchronized (portLock(config)) {
            return pollSerial(config);
        }
    }

    private Map<String, Object> pollSerial(IndustrialDeviceConfig config) throws Exception {
        ModbusSerialMaster master = createMaster(config);
        int unitId = config.getUnitId() == null ? 1 : config.getUnitId();
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> rawValues = new LinkedHashMap<>();
        try {
            master.connect();
            for (IndustrialDeviceConfig.Point point : config.getPoints()) {
                if (point == null || !point.hasResolvedPropertyCode() || point.getAddress() == null) {
                    continue;
                }
                String propertyCode = point.resolvedPropertyCode();
                IotModbusPollingProtocol.PointReadResult result = readPoint(master, unitId, point);
                values.put(propertyCode, result.value());
                rawValues.put(propertyCode, result.rawPayload());
            }
        } finally {
            master.disconnect();
        }
        if (!rawValues.isEmpty()) {
            values.put("_raw", rawValues);
        }
        return values;
    }

    @Override
    protected void write(DeviceDO device, IndustrialDeviceConfig config, IotDeviceMessage message) throws Exception {
        synchronized (portLock(config)) {
            writeSerial(config, message);
        }
    }

    private void writeSerial(IndustrialDeviceConfig config, IotDeviceMessage message) throws Exception {
        ModbusSerialMaster master = createMaster(config);
        int unitId = config.getUnitId() == null ? 1 : config.getUnitId();
        try {
            master.connect();
            for (IndustrialDeviceConfig.Point point : config.getPoints()) {
                if (point == null || !Boolean.TRUE.equals(point.getWritable()) || point.getAddress() == null
                        || !point.hasResolvedPropertyCode()) {
                    continue;
                }
                Object value = IotDeviceMessageUtils.extractPropertyValue(message, point.resolvedPropertyCode());
                if (value == null) {
                    continue;
                }
                if ("COIL".equalsIgnoreCase(point.getFunction())) {
                    boolean state = value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
                    master.writeCoil(unitId, point.getAddress(), state);
                } else if (value instanceof Number number) {
                    Register[] registers = toRegisters(IotModbusPollingProtocol.encodeRegisters(number, point));
                    if (registers.length == 1) {
                        master.writeSingleRegister(unitId, point.getAddress(), registers[0]);
                    } else {
                        master.writeMultipleRegisters(unitId, point.getAddress(), registers);
                    }
                }
            }
        } finally {
            master.disconnect();
        }
    }

    private IotModbusPollingProtocol.PointReadResult readPoint(ModbusSerialMaster master, int unitId,
                                                               IndustrialDeviceConfig.Point point) throws Exception {
        String function = StrUtil.blankToDefault(point.getFunction(), "HOLDING_REGISTER").toUpperCase();
        int quantity = Math.max(defaultQuantity(point.getDataType()),
                point.getQuantity() == null ? 1 : point.getQuantity());
        return switch (function) {
            case "COIL" -> {
                boolean value = master.readCoils(unitId, point.getAddress(), quantity).getBit(0);
                byte[] raw = {(byte) (value ? 1 : 0)};
                yield new IotModbusPollingProtocol.PointReadResult(value,
                        IotModbusPollingProtocol.formatResponsePdu(unitId, 0x01, raw));
            }
            case "DISCRETE_INPUT" -> {
                boolean value = master.readInputDiscretes(unitId, point.getAddress(), quantity).getBit(0);
                byte[] raw = {(byte) (value ? 1 : 0)};
                yield new IotModbusPollingProtocol.PointReadResult(value,
                        IotModbusPollingProtocol.formatResponsePdu(unitId, 0x02, raw));
            }
            case "INPUT_REGISTER" -> {
                byte[] raw = toBytes(master.readInputRegisters(unitId, point.getAddress(), quantity));
                yield new IotModbusPollingProtocol.PointReadResult(
                        IotModbusPollingProtocol.decodeRegisters(raw, point),
                        IotModbusPollingProtocol.formatResponsePdu(unitId, 0x04, raw));
            }
            case "HOLDING_REGISTER" -> {
                byte[] raw = toBytes(master.readMultipleRegisters(unitId, point.getAddress(), quantity));
                yield new IotModbusPollingProtocol.PointReadResult(
                        IotModbusPollingProtocol.decodeRegisters(raw, point),
                        IotModbusPollingProtocol.formatResponsePdu(unitId, 0x03, raw));
            }
            default -> throw new IllegalArgumentException("Unsupported Modbus function: " + function);
        };
    }

    private ModbusSerialMaster createMaster(IndustrialDeviceConfig config) {
        if (StrUtil.isBlank(config.getSerialPort())) {
            throw new IllegalArgumentException("Modbus RTU serial port is missing");
        }
        SerialParameters parameters = new SerialParameters();
        parameters.setPortName(config.getSerialPort());
        parameters.setBaudRate(config.getBaudRate() == null ? 9600 : config.getBaudRate());
        parameters.setDatabits(config.getDataBits() == null ? 8 : config.getDataBits());
        parameters.setStopbits(StrUtil.blankToDefault(config.getStopBits(), "1"));
        parameters.setParity(StrUtil.blankToDefault(config.getParity(), "NONE"));
        parameters.setEncoding("rtu");
        parameters.setRs485Mode(!Boolean.FALSE.equals(config.getRs485Mode()));
        return new ModbusSerialMaster(parameters, (int) requestTimeoutMs(),
                config.getTransmitDelayMs() == null ? 0 : Math.max(0, config.getTransmitDelayMs()));
    }

    private Object portLock(IndustrialDeviceConfig config) {
        return ModbusSerialPortLocks.forPort(config.getSerialPort());
    }

    private byte[] toBytes(InputRegister[] registers) {
        ByteBuffer buffer = ByteBuffer.allocate(registers.length * 2).order(ByteOrder.BIG_ENDIAN);
        for (InputRegister register : registers) {
            buffer.putShort((short) register.getValue());
        }
        return buffer.array();
    }

    private Register[] toRegisters(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        Register[] registers = new Register[bytes.length / 2];
        for (int index = 0; index < registers.length; index++) {
            registers[index] = new SimpleRegister(buffer.getShort() & 0xffff);
        }
        return registers;
    }

    private int defaultQuantity(String dataType) {
        return switch (StrUtil.blankToDefault(dataType, "UINT16").toUpperCase()) {
            case "INT32", "UINT32", "FLOAT32" -> 2;
            case "INT64", "FLOAT64" -> 4;
            default -> 1;
        };
    }

    @Override
    protected String connectionAddress(DeviceDO device, IndustrialDeviceConfig config) {
        return config.getSerialPort();
    }
}
