package com.basiclab.iot.sink.protocol.modbus;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.sink.protocol.polling.IndustrialDeviceConfig;
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.SerialParameters;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single Modbus RTU serial I/O engine.
 *
 * <p>This class intentionally has no central persistence or message-bus
 * dependency. Center polling uses the bridge adapter and the
 * collector runtime uses this engine directly from an applied local snapshot.
 */
public class IotModbusRtuPollingProtocol {

    public static final String PROTOCOL_TYPE = "MODBUS_RTU";

    private final long requestTimeoutMs;

    public IotModbusRtuPollingProtocol() {
        this(5_000L);
    }

    public IotModbusRtuPollingProtocol(long requestTimeoutMs) {
        if (requestTimeoutMs < 1) {
            throw new IllegalArgumentException("requestTimeoutMs must be positive");
        }
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /** Poll one resolved device. No central state is read or written here. */
    public Map<String, Object> poll(IndustrialDeviceConfig config) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("RTU config is required");
        }
        synchronized (ModbusSerialPortLocks.forPort(config.getSerialPort())) {
            return pollSerial(config);
        }
    }

    /** Write values from a center bridge or collector command adapter. */
    public void write(IndustrialDeviceConfig config, Map<String, Object> values) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("RTU config is required");
        }
        synchronized (ModbusSerialPortLocks.forPort(config.getSerialPort())) {
            writeSerial(config, values == null ? Map.of() : values);
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
                PointReadResult result = readPoint(master, unitId, point);
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

    private void writeSerial(IndustrialDeviceConfig config, Map<String, Object> values) throws Exception {
        ModbusSerialMaster master = createMaster(config);
        int unitId = config.getUnitId() == null ? 1 : config.getUnitId();
        try {
            master.connect();
            for (IndustrialDeviceConfig.Point point : config.getPoints()) {
                if (point == null || !Boolean.TRUE.equals(point.getWritable()) || point.getAddress() == null
                        || !point.hasResolvedPropertyCode()) {
                    continue;
                }
                Object value = values.get(point.resolvedPropertyCode());
                if (value == null) {
                    continue;
                }
                if ("COIL".equalsIgnoreCase(point.getFunction())) {
                    boolean state = value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
                    master.writeCoil(unitId, point.getAddress(), state);
                } else if (value instanceof Number number) {
                    Register[] registers = toRegisters(encodeRegisters(number, point));
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

    private PointReadResult readPoint(ModbusSerialMaster master, int unitId,
                                      IndustrialDeviceConfig.Point point) throws Exception {
        String function = StrUtil.blankToDefault(point.getFunction(), "HOLDING_REGISTER").toUpperCase();
        int quantity = Math.max(defaultQuantity(point.getDataType()),
                point.getQuantity() == null ? 1 : point.getQuantity());
        return switch (function) {
            case "COIL" -> {
                boolean value = master.readCoils(unitId, point.getAddress(), quantity).getBit(0);
                byte[] raw = {(byte) (value ? 1 : 0)};
                yield new PointReadResult(value, formatResponsePdu(unitId, 0x01, raw));
            }
            case "DISCRETE_INPUT" -> {
                boolean value = master.readInputDiscretes(unitId, point.getAddress(), quantity).getBit(0);
                byte[] raw = {(byte) (value ? 1 : 0)};
                yield new PointReadResult(value, formatResponsePdu(unitId, 0x02, raw));
            }
            case "INPUT_REGISTER" -> {
                byte[] raw = toBytes(master.readInputRegisters(unitId, point.getAddress(), quantity));
                yield new PointReadResult(decodeRegisters(raw, point), formatResponsePdu(unitId, 0x04, raw));
            }
            case "HOLDING_REGISTER" -> {
                byte[] raw = toBytes(master.readMultipleRegisters(unitId, point.getAddress(), quantity));
                yield new PointReadResult(decodeRegisters(raw, point), formatResponsePdu(unitId, 0x03, raw));
            }
            default -> throw new IllegalArgumentException("Unsupported Modbus function: " + function);
        };
    }

    /** Protected for deterministic unit tests; production uses j2mod. */
    protected ModbusSerialMaster createMaster(IndustrialDeviceConfig config) {
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
        return new ModbusSerialMaster(parameters, (int) requestTimeoutMs,
                config.getTransmitDelayMs() == null ? 0 : Math.max(0, config.getTransmitDelayMs()));
    }

    private static byte[] toBytes(InputRegister[] registers) {
        ByteBuffer buffer = ByteBuffer.allocate(registers.length * 2).order(ByteOrder.BIG_ENDIAN);
        for (InputRegister register : registers) {
            buffer.putShort((short) register.getValue());
        }
        return buffer.array();
    }

    private static Register[] toRegisters(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        Register[] registers = new Register[bytes.length / 2];
        for (int index = 0; index < registers.length; index++) {
            registers[index] = new SimpleRegister(buffer.getShort() & 0xffff);
        }
        return registers;
    }

    private record PointReadResult(Object value, String rawPayload) {
    }

    private static String formatResponsePdu(int unitId, int functionCode, byte[] data) {
        StringBuilder result = new StringBuilder();
        appendHexByte(result, unitId);
        appendHexByte(result, functionCode);
        appendHexByte(result, data.length);
        for (byte value : data) {
            appendHexByte(result, value & 0xff);
        }
        return result.toString();
    }

    private static void appendHexByte(StringBuilder target, int value) {
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(String.format("%02X", value & 0xff));
    }

    private static Object decodeRegisters(byte[] source, IndustrialDeviceConfig.Point point) {
        if (source == null || source.length < 2) {
            throw new IllegalArgumentException("Modbus register response is empty");
        }
        byte[] bytes = source.clone();
        if ("LITTLE_ENDIAN".equalsIgnoreCase(point.getByteOrder())) {
            for (int index = 0; index + 1 < bytes.length; index += 2) {
                byte value = bytes[index];
                bytes[index] = bytes[index + 1];
                bytes[index + 1] = value;
            }
        }
        if ("LITTLE_ENDIAN".equalsIgnoreCase(point.getWordOrder()) && bytes.length > 2) {
            for (int left = 0, right = bytes.length - 2; left < right; left += 2, right -= 2) {
                byte first = bytes[left];
                byte second = bytes[left + 1];
                bytes[left] = bytes[right];
                bytes[left + 1] = bytes[right + 1];
                bytes[right] = first;
                bytes[right + 1] = second;
            }
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        String dataType = StrUtil.blankToDefault(point.getDataType(), "UINT16").toUpperCase();
        Number raw = switch (dataType) {
            case "INT16" -> buffer.getShort();
            case "UINT16" -> buffer.getShort() & 0xffff;
            case "INT32" -> buffer.getInt();
            case "UINT32" -> Integer.toUnsignedLong(buffer.getInt());
            case "FLOAT32" -> buffer.getFloat();
            case "INT64" -> buffer.getLong();
            case "FLOAT64" -> buffer.getDouble();
            default -> throw new IllegalArgumentException("Unsupported Modbus data type: " + dataType);
        };
        double scale = point.getScale() == null ? 1D : point.getScale();
        double offset = point.getOffset() == null ? 0D : point.getOffset();
        if (scale == 1D && offset == 0D && !dataType.startsWith("FLOAT")) {
            return raw;
        }
        return raw.doubleValue() * scale + offset;
    }

    private static byte[] encodeRegisters(Number value, IndustrialDeviceConfig.Point point) {
        double scale = point.getScale() == null || point.getScale() == 0D ? 1D : point.getScale();
        double offset = point.getOffset() == null ? 0D : point.getOffset();
        double raw = (value.doubleValue() - offset) / scale;
        String dataType = StrUtil.blankToDefault(point.getDataType(), "UINT16").toUpperCase();
        ByteBuffer buffer = switch (dataType) {
            case "INT16", "UINT16" -> ByteBuffer.allocate(2).putShort((short) Math.round(raw));
            case "INT32", "UINT32" -> ByteBuffer.allocate(4).putInt((int) Math.round(raw));
            case "FLOAT32" -> ByteBuffer.allocate(4).putFloat((float) raw);
            case "INT64" -> ByteBuffer.allocate(8).putLong(Math.round(raw));
            case "FLOAT64" -> ByteBuffer.allocate(8).putDouble(raw);
            default -> throw new IllegalArgumentException("Unsupported Modbus data type: " + dataType);
        };
        byte[] bytes = buffer.array();
        if ("LITTLE_ENDIAN".equalsIgnoreCase(point.getWordOrder()) && bytes.length > 2) {
            for (int left = 0, right = bytes.length - 2; left < right; left += 2, right -= 2) {
                byte first = bytes[left];
                byte second = bytes[left + 1];
                bytes[left] = bytes[right];
                bytes[left + 1] = bytes[right + 1];
                bytes[right] = first;
                bytes[right + 1] = second;
            }
        }
        if ("LITTLE_ENDIAN".equalsIgnoreCase(point.getByteOrder())) {
            for (int index = 0; index + 1 < bytes.length; index += 2) {
                byte first = bytes[index];
                bytes[index] = bytes[index + 1];
                bytes[index + 1] = first;
            }
        }
        return bytes;
    }

    private static int defaultQuantity(String dataType) {
        return switch (StrUtil.blankToDefault(dataType, "UINT16").toUpperCase()) {
            case "INT32", "UINT32", "FLOAT32" -> 2;
            case "INT64", "FLOAT64" -> 4;
            default -> 1;
        };
    }
}
