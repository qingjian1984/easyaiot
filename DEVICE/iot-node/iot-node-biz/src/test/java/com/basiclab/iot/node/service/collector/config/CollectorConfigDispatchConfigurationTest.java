package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.device.CollectorConfigReleaseInternalApi;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseDetailDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedRequestDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedResponseDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedStatus;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleasePendingDTO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.security.NodeAgentRequestSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorConfigDispatchConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CollectorConfigDispatchConfiguration.class, TestBeans.class);

    @Test
    void disabledConfigurationCreatesNoPollingGraph() {
        contextRunner.withPropertyValues(
                        "easyaiot.collector.config-dispatch.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CollectorConfigDispatchService.class);
                    assertThat(context).doesNotHaveBean(CollectorConfigDispatchJob.class);
                    assertThat(context).doesNotHaveBean(CollectorConfigDispatchProperties.class);
                });
    }

    @Test
    void enabledConfigurationWiresTypedApiMapperSignerServiceAndJob() {
        contextRunner.withPropertyValues(
                        "easyaiot.collector.config-dispatch.enabled=true",
                        "easyaiot.collector.config-dispatch.batch-limit=7",
                        "easyaiot.collector.config-dispatch.fixed-delay-ms=60000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CollectorConfigReleaseClientPort.class);
                    assertThat(context.getBean(CollectorConfigReleaseClientPort.class))
                            .isInstanceOf(CollectorConfigReleaseClientAdapter.class);
                    assertThat(context).hasSingleBean(CollectorNodeAuthorityPort.class);
                    assertThat(context.getBean(CollectorNodeAuthorityPort.class))
                            .isInstanceOf(ComputeNodeCollectorAuthorityAdapter.class);
                    assertThat(context).hasSingleBean(NodeAgentRequestSigner.class);
                    assertThat(context).hasSingleBean(CollectorAgentPort.class);
                    assertThat(context).hasSingleBean(CollectorConfigDispatchService.class);
                    assertThat(context).hasSingleBean(CollectorConfigDispatchJob.class);
                });
    }

    @Test
    void typedReleaseAdapterMapsDtosAndRequiresSuccessfulR() {
        CollectorConfigReleaseInternalApi api = Mockito.mock(CollectorConfigReleaseInternalApi.class);
        CollectorConfigReleasePendingDTO pending = new CollectorConfigReleasePendingDTO();
        pending.setReleaseId("1001");
        pending.setTenantId("10");
        pending.setNodeId("21");
        pending.setWorkloadId("collector-site-1001-a");
        pending.setConfigVersion("1");
        pending.setSchemaVersion("1.1");
        pending.setCanonicalizationVersion("jcs-rfc8785-v1");
        pending.setPayloadSha256("a".repeat(64));
        pending.setCanonicalLengthBytes(2L);
        pending.setPublishedAt("2026-08-17T10:00:00Z");
        Mockito.when(api.listPending(5)).thenReturn(R.ok(List.of(pending)));

        CollectorConfigReleaseDetailDTO detail = new CollectorConfigReleaseDetailDTO();
        detail.setReleaseId("1001");
        detail.setTenantId("10");
        detail.setNodeId("21");
        detail.setWorkloadId("collector-site-1001-a");
        detail.setConfigVersion("1");
        detail.setSchemaVersion("1.1");
        detail.setCanonicalizationVersion("jcs-rfc8785-v1");
        detail.setPayloadCanonical("{}");
        detail.setPayloadSha256("a".repeat(64));
        detail.setCanonicalLengthBytes(2L);
        detail.setPublishedAt("2026-08-17T10:00:00Z");
        Mockito.when(api.getDetail("1001")).thenReturn(R.ok(detail));

        CollectorConfigReleaseObservedResponseDTO observed = new CollectorConfigReleaseObservedResponseDTO();
        observed.setReleaseId("1001");
        observed.setStatus(CollectorConfigReleaseObservedStatus.AGENT_ACCEPTED);
        observed.setAccepted(true);
        observed.setTerminal(false);
        observed.setIdempotent(true);
        Mockito.when(api.reportObserved(Mockito.eq("1001"),
                        Mockito.any(CollectorConfigReleaseObservedRequestDTO.class)))
                .thenReturn(R.ok(observed));

        CollectorConfigReleaseClientAdapter adapter = new CollectorConfigReleaseClientAdapter(api);
        CollectorConfigReleasePending mappedPending = adapter.listPending(5).get(0);
        assertEquals("1001", mappedPending.getReleaseId());
        assertEquals("collector-site-1001-a", mappedPending.getWorkloadId());
        assertEquals(2L, mappedPending.getCanonicalLengthBytes());
        assertEquals("{}", adapter.getDetail("1001").orElseThrow().getPayloadCanonical());

        CollectorConfigObservedResponse mappedObserved = adapter.reportObserved(
                new CollectorConfigReleaseObservedReport("1001", "10", "21",
                        "collector-site-1001-a", "1", "a".repeat(64),
                        CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED,
                        "2026-08-17T10:01:00Z", ""));
        assertEquals(CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED,
                mappedObserved.getStatus());
        assertTrue(mappedObserved.isAccepted());
        assertTrue(mappedObserved.isIdempotent());

        CollectorConfigReleaseObservedRequestDTO request = Mockito.mockingDetails(api)
                .getInvocations().stream()
                .filter(invocation -> "reportObserved".equals(invocation.getMethod().getName()))
                .findFirst()
                .map(invocation -> (CollectorConfigReleaseObservedRequestDTO) invocation.getArguments()[1])
                .orElseThrow();
        assertEquals(CollectorConfigReleaseObservedStatus.AGENT_ACCEPTED, request.getStatus());
        assertEquals("", request.getErrorCode());
        assertNull(request.getErrorDetailSanitized());
    }

    @Test
    void typedReleaseAdapterRejectsFailedOrNullEnvelope() {
        CollectorConfigReleaseInternalApi api = Mockito.mock(CollectorConfigReleaseInternalApi.class);
        Mockito.when(api.listPending(1)).thenReturn(R.<List<CollectorConfigReleasePendingDTO>>fail("down"));
        CollectorConfigReleaseClientAdapter adapter = new CollectorConfigReleaseClientAdapter(api);

        assertThrows(IllegalStateException.class, () -> adapter.listPending(1));
        Mockito.when(api.getDetail("1001")).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> adapter.getDetail("1001"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CollectorConfigReleaseInternalApi collectorConfigReleaseInternalApi() {
            return Mockito.mock(CollectorConfigReleaseInternalApi.class);
        }

        @Bean
        ComputeNodeMapper computeNodeMapper() {
            return Mockito.mock(ComputeNodeMapper.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
