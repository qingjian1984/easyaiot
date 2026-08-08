package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry.PowerModelEventHandler;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry.PowerModelEventProcessingException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-001 §6.2 协调器处理器合同（手写 fake 端口，不触碰 DB/Kafka）。
 * 覆盖四事件语义：发布 noop-with-audit、生命周期引用标记、绑定应用/回滚
 * 影响面再生（空影响面审计、幂等跳过、回滚 templateVersion 由端口解析）、
 * 以及 final/retryable 分流（字段缺失、端口 IAE/RuntimeException、租户非法、
 * 影响面端口违反 null 合同、审计 detail ≤512 有界）。
 */
class PowerModelCollectorEventHandlersTest {

    private static final String EVENT_ID = "00000000-0000-0000-0000-0000000000a1";

    @Test
    void publishedWritesNoopAuditOnly() {
        Fixture f = new Fixture();
        handler(f, PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1)
                .handle(envelope(PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1),
                        "{\"templateCode\":\"power.hv_cabinet\",\"templateVersion\":\"1.2.0\"}");

        assertEquals(1, f.audit.records.size());
        FakeAudit.Record r = f.audit.records.get(0);
        assertEquals(EVENT_ID, r.eventId);
        assertEquals(1L, r.tenantId);
        assertEquals(PowerModelCollectorEventHandlers.ACTION_PUBLISHED_NOTED, r.action);
        assertTrue(r.detail.contains("templateCode=power.hv_cabinet"));
        assertTrue(f.impact.calls.isEmpty(), "发布事件绝不解析影响面");
        assertTrue(f.release.drafts.isEmpty(), "发布事件绝不生成发布单");
        assertTrue(f.reference.marks.isEmpty(), "发布事件绝不写引用标记");
    }

    @Test
    void publishedMissingFieldIsFinal() {
        Fixture f = new Fixture();
        PowerModelEventProcessingException e = assertThrows(PowerModelEventProcessingException.class,
                () -> handler(f, PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1)
                        .handle(envelope(PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1),
                                "{\"templateCode\":\"power.hv_cabinet\"}"));
        assertFalse(e.isRetryable());
        assertEquals(PowerModelCollectorEventHandlers.CODE_DATA_FIELD_MISSING, e.errorCode());
    }

    @Test
    void malformedDataIsFinal() {
        Fixture f = new Fixture();
        PowerModelEventProcessingException e = assertThrows(PowerModelEventProcessingException.class,
                () -> handler(f, PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1)
                        .handle(envelope(PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1),
                                "{not-json"));
        assertFalse(e.isRetryable());
        assertEquals(PowerModelCollectorEventHandlers.CODE_DATA_MALFORMED, e.errorCode());
    }

    @Test
    void lifecycleMarksReferenceAndAudits() {
        Fixture f = new Fixture();
        handler(f, PowerModelEventEnvelope.EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1)
                .handle(envelope(PowerModelEventEnvelope.EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1),
                        "{\"templateCode\":\"power.hv_cabinet\",\"templateVersion\":\"1.1.0\","
                                + "\"fromLifecycle\":\"PUBLISHED\",\"toLifecycle\":\"DEPRECATED\"}");

        assertEquals(1, f.reference.marks.size());
        FakeReference.Mark mark = f.reference.marks.get(0);
        assertEquals(1L, mark.tenantId);
        assertEquals("power.hv_cabinet", mark.templateCode);
        assertEquals("1.1.0", mark.templateVersion);
        assertEquals("PUBLISHED", mark.fromLifecycle);
        assertEquals("DEPRECATED", mark.toLifecycle);
        assertEquals(PowerModelCollectorEventHandlers.ACTION_LIFECYCLE_MARKED,
                f.audit.records.get(0).action);
        assertTrue(f.release.drafts.isEmpty(), "生命周期变更绝不生成发布单（不改写快照）");
    }

    @Test
    void portIllegalArgumentIsFinal() {
        Fixture f = new Fixture();
        f.reference.failWith = new IllegalArgumentException("MODEL_STATIC_CHECK_CONFLICT");
        PowerModelEventProcessingException e = assertThrows(PowerModelEventProcessingException.class,
                () -> handler(f, PowerModelEventEnvelope.EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1)
                        .handle(envelope(PowerModelEventEnvelope.EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1),
                                lifecycleJson()));
        assertFalse(e.isRetryable());
        assertEquals(PowerModelCollectorEventHandlers.CODE_PORT_FINAL, e.errorCode());
    }

    @Test
    void portRuntimeFailureIsRetryable() {
        Fixture f = new Fixture();
        f.reference.failWith = new IllegalStateException("connection reset");
        PowerModelEventProcessingException e = assertThrows(PowerModelEventProcessingException.class,
                () -> handler(f, PowerModelEventEnvelope.EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1)
                        .handle(envelope(PowerModelEventEnvelope.EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1),
                                lifecycleJson()));
        assertTrue(e.isRetryable());
        assertEquals(PowerModelCollectorEventHandlers.CODE_PORT_RETRYABLE, e.errorCode());
    }

    @Test
    void bindingAppliedEmptyImpactAuditsAndCreatesNothing() {
        Fixture f = new Fixture();
        handler(f, PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1)
                .handle(envelope(PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1),
                        appliedJson());

        assertEquals(1, f.audit.records.size());
        assertEquals(PowerModelCollectorEventHandlers.ACTION_IMPACT_EMPTY,
                f.audit.records.get(0).action);
        assertTrue(f.release.drafts.isEmpty());
    }

    @Test
    void bindingAppliedCreatesDraftsForNonIdempotentWorkloads() {
        Fixture f = new Fixture();
        f.impact.workloads = Arrays.asList("wl-a", "wl-b", "wl-c");
        f.release.alreadyDesired.add("wl-b");
        handler(f, PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1)
                .handle(envelope(PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1),
                        appliedJson());

        assertEquals(2, f.release.drafts.size());
        FakeRelease.Draft first = f.release.drafts.get(0);
        assertEquals("wl-a", first.workloadId);
        assertEquals(1L, first.tenantId);
        assertEquals(2001L, first.productId);
        assertEquals("power.hv_cabinet", first.templateCode);
        assertEquals("1.3.0", first.templateVersion);
        assertEquals(7L, first.bindingRevision);
        assertEquals("BINDING_APPLIED", first.reasonCode);
        assertEquals("wl-c", f.release.drafts.get(1).workloadId);
        FakeAudit.Record audit = f.audit.records.get(0);
        assertEquals(PowerModelCollectorEventHandlers.ACTION_DRAFTS_CREATED, audit.action);
        assertTrue(audit.detail.contains("impacted=3"));
        assertTrue(audit.detail.contains("draftsCreated=2"));
    }

    @Test
    void bindingRolledBackPassesNullTemplateVersionAndReasonCode() {
        Fixture f = new Fixture();
        f.impact.workloads = Collections.singletonList("wl-a");
        handler(f, PowerModelEventEnvelope.EVENT_BINDING_ROLLED_BACK_V1)
                .handle(envelope(PowerModelEventEnvelope.EVENT_BINDING_ROLLED_BACK_V1),
                        "{\"productId\":2001,\"templateCode\":\"power.hv_cabinet\","
                                + "\"fromBindingRevision\":7,\"toBindingRevision\":5,"
                                + "\"reasonCode\":\"OPS_ROLLBACK\"}");

        assertEquals(1, f.release.drafts.size());
        FakeRelease.Draft draft = f.release.drafts.get(0);
        assertNull(draft.templateVersion, "回滚事件不携带 templateVersion，由端口按 bindingRevision 解析");
        assertEquals(5L, draft.bindingRevision, "回滚目标版本取 toBindingRevision");
        assertEquals("BINDING_ROLLED_BACK:OPS_ROLLBACK", draft.reasonCode);
    }

    @Test
    void impactPortReturningNullViolatesContractAsFinal() {
        Fixture f = new Fixture();
        f.impact.returnNull = true;
        PowerModelEventProcessingException e = assertThrows(PowerModelEventProcessingException.class,
                () -> handler(f, PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1)
                        .handle(envelope(PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1),
                                appliedJson()));
        assertFalse(e.isRetryable());
        assertEquals(PowerModelCollectorEventHandlers.CODE_PORT_FINAL, e.errorCode());
    }

    @Test
    void nonNumericTenantIsFinal() {
        Fixture f = new Fixture();
        PowerModelEventEnvelope badTenant = PowerModelEventEnvelope.of(
                EVENT_ID, PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1, 1,
                "tenant-x", "power_model_template", "1001",
                "2026-08-08T00:00:00Z", "req-1", "", Collections.<String, Object>emptyMap());
        PowerModelEventProcessingException e = assertThrows(PowerModelEventProcessingException.class,
                () -> handler(f, PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1)
                        .handle(badTenant,
                                "{\"templateCode\":\"c\",\"templateVersion\":\"1.0.0\"}"));
        assertFalse(e.isRetryable());
        assertEquals(PowerModelCollectorEventHandlers.CODE_TENANT_INVALID, e.errorCode());
    }

    @Test
    void auditDetailIsBoundedTo512Chars() {
        Fixture f = new Fixture();
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            huge.append('x');
        }
        handler(f, PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1)
                .handle(envelope(PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1),
                        "{\"templateCode\":\"" + huge + "\",\"templateVersion\":\"1.0.0\"}");

        assertTrue(f.audit.records.get(0).detail.length()
                        <= PowerModelCollectorEventHandlers.AUDIT_DETAIL_MAX,
                "审计 detail 必须有界 ≤512 字符");
    }

    private static PowerModelEventHandler handler(Fixture f, String eventType) {
        PowerModelEventHandlerRegistry registry = new PowerModelEventHandlerRegistry(
                PowerModelCollectorEventHandlers.create(f.impact, f.release, f.reference, f.audit));
        PowerModelEventHandler handler = registry.find(eventType);
        if (handler == null) {
            throw new IllegalStateException("处理器未注册: " + eventType);
        }
        return handler;
    }

    private static PowerModelEventEnvelope envelope(String eventType) {
        return PowerModelEventEnvelope.of(EVENT_ID, eventType, 1, "1",
                "power_product", "2001", "2026-08-08T00:00:00Z", "req-1", "",
                Collections.<String, Object>emptyMap());
    }

    private static String lifecycleJson() {
        return "{\"templateCode\":\"power.hv_cabinet\",\"templateVersion\":\"1.1.0\","
                + "\"fromLifecycle\":\"PUBLISHED\",\"toLifecycle\":\"DEPRECATED\"}";
    }

    private static String appliedJson() {
        return "{\"productId\":2001,\"templateCode\":\"power.hv_cabinet\","
                + "\"templateVersion\":\"1.3.0\",\"bindingRevision\":7}";
    }

    private static final class Fixture {
        final FakeImpact impact = new FakeImpact();
        final FakeRelease release = new FakeRelease();
        final FakeReference reference = new FakeReference();
        final FakeAudit audit = new FakeAudit();
    }

    private static final class FakeImpact implements CollectorWorkloadImpactPort {
        List<String> workloads = Collections.emptyList();
        boolean returnNull;
        final List<String> calls = new ArrayList<String>();

        @Override
        public List<String> resolveActiveWorkloads(long tenantId, long productId) {
            calls.add(tenantId + ":" + productId);
            return returnNull ? null : workloads;
        }
    }

    private static final class FakeRelease implements CollectorConfigReleasePort {
        final List<String> alreadyDesired = new ArrayList<String>();
        final List<Draft> drafts = new ArrayList<Draft>();

        @Override
        public boolean desiredMatches(String workloadId, String templateCode,
                                      String templateVersion, long bindingRevision) {
            return alreadyDesired.contains(workloadId);
        }

        @Override
        public void createRegenerationDraft(String workloadId, long tenantId, long productId,
                                            String templateCode, String templateVersion,
                                            long bindingRevision, String reasonCode) {
            drafts.add(new Draft(workloadId, tenantId, productId, templateCode,
                    templateVersion, bindingRevision, reasonCode));
        }

        static final class Draft {
            final String workloadId;
            final long tenantId;
            final long productId;
            final String templateCode;
            final String templateVersion;
            final long bindingRevision;
            final String reasonCode;

            Draft(String workloadId, long tenantId, long productId, String templateCode,
                  String templateVersion, long bindingRevision, String reasonCode) {
                this.workloadId = workloadId;
                this.tenantId = tenantId;
                this.productId = productId;
                this.templateCode = templateCode;
                this.templateVersion = templateVersion;
                this.bindingRevision = bindingRevision;
                this.reasonCode = reasonCode;
            }
        }
    }

    private static final class FakeReference implements PowerModelTemplateReferencePort {
        final List<Mark> marks = new ArrayList<Mark>();
        RuntimeException failWith;

        @Override
        public void markLifecycleReference(long tenantId, String templateCode, String templateVersion,
                                           String fromLifecycle, String toLifecycle) {
            if (failWith != null) {
                throw failWith;
            }
            marks.add(new Mark(tenantId, templateCode, templateVersion, fromLifecycle, toLifecycle));
        }

        static final class Mark {
            final long tenantId;
            final String templateCode;
            final String templateVersion;
            final String fromLifecycle;
            final String toLifecycle;

            Mark(long tenantId, String templateCode, String templateVersion,
                 String fromLifecycle, String toLifecycle) {
                this.tenantId = tenantId;
                this.templateCode = templateCode;
                this.templateVersion = templateVersion;
                this.fromLifecycle = fromLifecycle;
                this.toLifecycle = toLifecycle;
            }
        }
    }

    private static final class FakeAudit implements PowerModelCoordinationAuditPort {
        final List<Record> records = new ArrayList<Record>();

        @Override
        public void record(String eventId, long tenantId, String action, String detail) {
            records.add(new Record(eventId, tenantId, action, detail));
        }

        static final class Record {
            final String eventId;
            final long tenantId;
            final String action;
            final String detail;

            Record(String eventId, long tenantId, String action, String detail) {
                this.eventId = eventId;
                this.tenantId = tenantId;
                this.action = action;
                this.detail = detail;
            }
        }
    }
}
