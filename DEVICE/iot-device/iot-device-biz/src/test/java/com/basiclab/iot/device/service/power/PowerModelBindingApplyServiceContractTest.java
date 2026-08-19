package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.device.config.PowerModelIdempotencySecretProvider;
import com.basiclab.iot.device.controller.power.dto.PowerModelBindingApplyRequest;
import com.basiclab.iot.device.service.event.PowerModelOutboxService;
import com.basiclab.iot.device.service.event.CollectorConfigSnapshotContract;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** OPEN03-01：客户端源快照不得伪造服务端注入的产品身份。 */
class PowerModelBindingApplyServiceContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rejectsForgedProductIdentityAndUnknownSourceFieldsBeforeDatabaseAccess() throws Exception {
        PowerModelBindingApplyRequest request = validRequest();
        ((com.fasterxml.jackson.databind.node.ObjectNode) request.getCollectorSnapshot())
                .put("productIdentification", "forged-product");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service().apply(100L, "trusted-product", request, 200L,
                        "idem-source-forged", "request-source-forged", "trace-source"));
        assertTrue(error.getMessage().startsWith(CollectorConfigSnapshotContract.CODE_INVALID));

        PowerModelBindingApplyRequest unknown = validRequest();
        ((com.fasterxml.jackson.databind.node.ObjectNode) unknown.getCollectorSnapshot())
                .put("unknown", true);
        IllegalArgumentException unknownError = assertThrows(IllegalArgumentException.class,
                () -> service().apply(100L, "trusted-product", unknown, 200L,
                        "idem-source-unknown", "request-source-unknown", "trace-source"));
        assertTrue(unknownError.getMessage().startsWith(CollectorConfigSnapshotContract.CODE_INVALID));
    }

    @Test
    void rejectsWrongSourceVersionAndInvalidPathIdentity() throws Exception {
        PowerModelBindingApplyRequest wrongVersion = validRequest();
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrongVersion.getCollectorSnapshot())
                .put("schemaVersion", CollectorConfigSnapshotContract.SCHEMA_VERSION_V1_1);
        IllegalArgumentException versionError = assertThrows(IllegalArgumentException.class,
                () -> service().apply(100L, "trusted-product", wrongVersion, 200L,
                        "idem-source-version", "request-source-version", "trace-source"));
        assertTrue(versionError.getMessage().startsWith(CollectorConfigSnapshotContract.CODE_INVALID));

        IllegalArgumentException blankError = assertThrows(IllegalArgumentException.class,
                () -> service().apply(100L, " ", validRequest(), 200L,
                        "idem-path-blank", "request-path-blank", "trace-source"));
        assertTrue(blankError.getMessage().startsWith(CollectorConfigSnapshotContract.CODE_INVALID));

        StringBuilder overlong = new StringBuilder();
        for (int i = 0; i < 129; i++) overlong.append('p');
        IllegalArgumentException overlongError = assertThrows(IllegalArgumentException.class,
                () -> service().apply(100L, overlong.toString(), validRequest(), 200L,
                        "idem-path-long", "request-path-long", "trace-source"));
        assertTrue(overlongError.getMessage().startsWith(CollectorConfigSnapshotContract.CODE_INVALID));
    }

    private static PowerModelBindingApplyService service() {
        CapabilityService capability = mock(CapabilityService.class);
        when(capability.isEnabled(PowerModelBindingApplyService.CAPABILITY_CODE)).thenReturn(true);
        PowerModelIdempotencySecretProvider secretProvider = mock(PowerModelIdempotencySecretProvider.class);
        when(secretProvider.getSecret()).thenReturn(
                "open03-01-test-secret-must-be-at-least-32".getBytes(StandardCharsets.UTF_8));
        return new PowerModelBindingApplyService(mock(DataSource.class), MAPPER, capability,
                mock(TenantFrameworkService.class), mock(PowerModelOutboxService.class),
                mock(JdbcPowerIdempotencyStore.class), secretProvider);
    }

    private static PowerModelBindingApplyRequest validRequest() throws Exception {
        PowerModelBindingApplyRequest request = new PowerModelBindingApplyRequest();
        request.setTemplateCode("tpl-open03");
        request.setTemplateVersion("1.0.0");
        request.setNodeId(300L);
        request.setBindingSnapshot(MAPPER.readTree("{\"templateCode\":\"tpl-open03\","
                + "\"templateVersion\":\"1.0.0\"}"));
        request.setCollectorSnapshot(MAPPER.readTree("{\"schemaVersion\":\"1.0\","
                + "\"workloadId\":\"collector-open03\",\"tenantId\":\"100\","
                + "\"siteId\":\"1001\",\"siteCode\":\"site-open03\","
                + "\"serialBuses\":[{\"busId\":\"bus-a\","
                + "\"serialPort\":\"/dev/easyaiot/rs485-0\",\"baudRate\":9600,"
                + "\"dataBits\":8,\"stopBits\":\"1\",\"parity\":\"NONE\","
                + "\"transmitDelayMs\":0,\"rs485Mode\":true,\"devices\":[{"
                + "\"deviceId\":\"20001\",\"deviceIdentification\":\"METER-01\","
                + "\"unitId\":1,\"pollIntervalMs\":5000,\"requestTimeoutMs\":1000,"
                + "\"maxRetries\":2,\"points\":[{\"propertyCode\":\"active-power\","
                + "\"function\":\"HOLDING_REGISTER\",\"address\":0,\"quantity\":2,"
                + "\"dataType\":\"FLOAT32\",\"byteOrder\":\"BIG_ENDIAN\","
                + "\"wordOrder\":\"BIG_ENDIAN\",\"scale\":\"1\",\"offset\":\"0\","
                + "\"dataPriority\":\"METERING_TOTAL\",\"writable\":false,"
                + "\"pollGroup\":\"normal\"}]}]}]}"));
        return request;
    }
}
