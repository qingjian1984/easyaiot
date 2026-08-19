package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.config.IotGatewayProperties;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.protocol.modbus.CenterModbusRtuPollingAdapter;
import com.basiclab.iot.sink.protocol.modbus.IotModbusRtuPollingProtocol;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CenterModbusRtuPollingAdapterTest {
    @Test
    void centerUsesTheSingleEngineBridge() throws Exception {
        assertEquals("com.basiclab.iot.sink.protocol.polling.AbstractIndustrialPollingProtocol",
                CenterModbusRtuPollingAdapter.class.getSuperclass().getName());
        String source = Files.readString(Path.of("src", "main", "java", "com", "basiclab", "iot", "sink",
                "protocol", "modbus", "CenterModbusRtuPollingAdapter.java"));
        assertTrue(source.contains("IotModbusRtuPollingProtocol"));
        assertTrue(source.contains("engine.poll(config)"));
        assertTrue(source.contains("engine.write(config, values)"));
    }

    @Test
    void pollAndWriteDelegateToTheSingleRtuEngineWithoutRemappingValues() throws Exception {
        FakeEngine engine = new FakeEngine();
        IotGatewayProperties.PollingProtocolProperties properties =
                new IotGatewayProperties.PollingProtocolProperties();
        CenterModbusRtuPollingAdapter adapter = new CenterModbusRtuPollingAdapter(
                properties, null, null, null, null, "center-test", engine);
        IndustrialDeviceConfig config = new IndustrialDeviceConfig();
        config.setSerialPort("COM-test");
        IndustrialDeviceConfig.Point point = new IndustrialDeviceConfig.Point();
        point.setPropertyCode("voltage");
        config.setPoints(List.of(point));

        Method poll = CenterModbusRtuPollingAdapter.class.getDeclaredMethod("poll",
                com.basiclab.iot.sink.dal.dataobject.DeviceDO.class, IndustrialDeviceConfig.class);
        poll.setAccessible(true);
        assertEquals(Map.of("voltage", 220), poll.invoke(adapter, null, config));
        assertEquals(config, engine.polledConfig);

        Method write = CenterModbusRtuPollingAdapter.class.getDeclaredMethod("write",
                com.basiclab.iot.sink.dal.dataobject.DeviceDO.class, IndustrialDeviceConfig.class,
                IotDeviceMessage.class);
        write.setAccessible(true);
        write.invoke(adapter, null, config, IotDeviceMessage.requestOf("thing.property.set",
                Map.of("voltage", 221)));
        assertEquals(Map.of("voltage", 221), engine.writtenValues);
        assertEquals(config, engine.writtenConfig);
    }

    private static final class FakeEngine extends IotModbusRtuPollingProtocol {
        private IndustrialDeviceConfig polledConfig;
        private IndustrialDeviceConfig writtenConfig;
        private Map<String, Object> writtenValues;

        @Override
        public Map<String, Object> poll(IndustrialDeviceConfig config) {
            polledConfig = config;
            return Map.of("voltage", 220);
        }

        @Override
        public void write(IndustrialDeviceConfig config, Map<String, Object> values) {
            writtenConfig = config;
            writtenValues = Map.copyOf(values);
        }
    }
}
