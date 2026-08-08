package com.basiclab.iot.device.service.event;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-014 §档位行为：Outbox 入列守卫合同。
 * capability 未启用（mini 档）fail-closed 拒绝；启用时委托仓储同事务入列。
 * （Propagation.MANDATORY 的"无活动事务即拒绝"由 Spring 容器在集成层保证。）
 */
class PowerModelOutboxServiceTest {

    private static final String EVENT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String AUDIT_ID = "00000000-0000-0000-0000-0000000000aa";

    @Test
    void disabledCapabilityFailsClosed() {
        PowerModelOutboxService service = new PowerModelOutboxService(
                new FakeRepository(), capability(false));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.enqueue(entry()));
        assertTrue(error.getMessage().startsWith("MODEL_CAPABILITY_DISABLED"),
                "capability 未启用必须 fail-closed，不产生 Outbox 待投递残留");
    }

    @Test
    void enabledCapabilityInsertsPending() {
        FakeRepository repository = new FakeRepository();
        PowerModelOutboxService service = new PowerModelOutboxService(
                repository, capability(true));
        service.enqueue(entry());
        assertEquals(1, repository.inserted.size());
    }

    private static OutboxEntry entry() {
        return OutboxEntry.of(1L, EVENT_ID, 1L, AUDIT_ID,
                "power_model_template", "1001",
                "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, "{\"k\":1}", 12);
    }

    private static CapabilityService capability(boolean enabled) {
        return new CapabilityService() {
            @Override
            public com.basiclab.iot.common.capability.CapabilitySnapshot snapshot() {
                throw new UnsupportedOperationException("测试桩：isEnabled 已覆盖，不调用 snapshot");
            }

            @Override
            public boolean isEnabled(String capabilityCode) {
                assertEquals(PowerModelOutboxService.CAPABILITY_CODE, capabilityCode);
                return enabled;
            }
        };
    }

    private static final class FakeRepository implements PowerModelOutboxRepository {
        final List<OutboxEntry> inserted = new ArrayList<OutboxEntry>();

        @Override
        public void insertPending(OutboxEntry entry) {
            inserted.add(entry);
        }

        @Override
        public List<ClaimedOutboxEntry> claimDue(java.time.Instant now, String leaseOwner,
                                                 java.time.Duration leaseDuration, int batchSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markPublished(String eventId, java.time.Instant publishedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markRetry(String eventId, int retryCount, java.time.Instant nextAttemptAt,
                              String errorCode, String errorDigest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markDeadLetter(String eventId, String errorCode, String errorDigest) {
            throw new UnsupportedOperationException();
        }
    }
}
