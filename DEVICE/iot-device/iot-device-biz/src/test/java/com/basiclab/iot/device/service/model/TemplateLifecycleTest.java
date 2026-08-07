package com.basiclab.iot.device.service.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-005 §7：生命周期状态机与 §10.2 不变量。
 */
class TemplateLifecycleTest {

    @Test
    void forwardTransitionsAreAccepted() {
        assertEquals(TemplateLifecycle.Lifecycle.PUBLISHED,
                TemplateLifecycle.requireTransition(TemplateLifecycle.Lifecycle.DRAFT,
                        TemplateLifecycle.Lifecycle.PUBLISHED));
        assertEquals(TemplateLifecycle.Lifecycle.DEPRECATED,
                TemplateLifecycle.requireTransition(TemplateLifecycle.Lifecycle.PUBLISHED,
                        TemplateLifecycle.Lifecycle.DEPRECATED));
        assertEquals(TemplateLifecycle.Lifecycle.DRAFT,
                TemplateLifecycle.requireTransition(TemplateLifecycle.Lifecycle.PUBLISHED,
                        TemplateLifecycle.Lifecycle.DRAFT));
        assertEquals(TemplateLifecycle.Lifecycle.DRAFT,
                TemplateLifecycle.requireTransition(TemplateLifecycle.Lifecycle.DEPRECATED,
                        TemplateLifecycle.Lifecycle.DRAFT));
    }

    @Test
    void illegalTransitionsAreRejected() {
        // DRAFT 直接 RETIRED、PUBLISHED 直接 RETIRED、RETIRED 任何出边均非法
        assertIllegal(TemplateLifecycle.Lifecycle.DRAFT, TemplateLifecycle.Lifecycle.RETIRED);
        assertIllegal(TemplateLifecycle.Lifecycle.PUBLISHED, TemplateLifecycle.Lifecycle.RETIRED);
        assertIllegal(TemplateLifecycle.Lifecycle.RETIRED, TemplateLifecycle.Lifecycle.DRAFT);
        assertIllegal(TemplateLifecycle.Lifecycle.DRAFT, TemplateLifecycle.Lifecycle.DEPRECATED);
    }

    @Test
    void retireRequiresStructuredMigrationNoticeAndPublishedAlternative() {
        TemplateLifecycle.MigrationNotice notice = new TemplateLifecycle.MigrationNotice(
                "standard-meter", "2.0.0", java.util.Arrays.asList("rebind", "verify"),
                Instant.now().plus(180, ChronoUnit.DAYS));

        // 替代版本未发布 → 拒绝
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TemplateLifecycle.requireRetirable(notice, false, true));
        assertTrue(error.getMessage().startsWith("MODEL_RETIRE_PRECONDITION_FAILED"));
        // 影响报告未确认 → 拒绝
        assertThrows(IllegalArgumentException.class,
                () -> TemplateLifecycle.requireRetirable(notice, true, false));
        // 齐全 → 通过
        assertDoesNotThrow(() -> TemplateLifecycle.requireRetirable(notice, true, true));
        // migrationNotice 缺字段 → 拒绝
        assertThrows(IllegalArgumentException.class,
                () -> TemplateLifecycle.requireRetirable(
                        new TemplateLifecycle.MigrationNotice("", "2.0.0",
                                java.util.Collections.emptyList(), null), true, true));
    }

    @Test
    void deprecatedTemplateDeniesNewBinding() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TemplateLifecycle.requireBindable(TemplateLifecycle.Lifecycle.DEPRECATED));
        assertTrue(error.getMessage().startsWith("MODEL_DEPRECATED_NEW_BINDING_DENIED"));
        assertDoesNotThrow(() -> TemplateLifecycle.requireBindable(TemplateLifecycle.Lifecycle.PUBLISHED));
        assertThrows(IllegalArgumentException.class,
                () -> TemplateLifecycle.requireBindable(TemplateLifecycle.Lifecycle.DRAFT));
    }

    @Test
    void publishedContentIsImmutable() {
        assertDoesNotThrow(() -> TemplateLifecycle.requireContentMutable(TemplateLifecycle.Lifecycle.DRAFT));
        for (TemplateLifecycle.Lifecycle frozen : new TemplateLifecycle.Lifecycle[]{
                TemplateLifecycle.Lifecycle.PUBLISHED, TemplateLifecycle.Lifecycle.DEPRECATED,
                TemplateLifecycle.Lifecycle.RETIRED}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> TemplateLifecycle.requireContentMutable(frozen));
            assertTrue(error.getMessage().startsWith("MODEL_TEMPLATE_PUBLISHED_IMMUTABLE"));
        }
    }

    @Test
    void draftAbandonsAfterNinetyDaysInactive() {
        Instant now = Instant.now();
        assertEquals(TemplateLifecycle.DraftState.ACTIVE,
                TemplateLifecycle.draftStateAfterCleanup(now.minus(89, ChronoUnit.DAYS), now));
        assertEquals(TemplateLifecycle.DraftState.ABANDONED,
                TemplateLifecycle.draftStateAfterCleanup(now.minus(91, ChronoUnit.DAYS), now));
    }

    @Test
    void abandonedDraftIsNotEditable() {
        assertDoesNotThrow(() -> TemplateLifecycle.requireDraftEditable(TemplateLifecycle.DraftState.ACTIVE));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TemplateLifecycle.requireDraftEditable(TemplateLifecycle.DraftState.ABANDONED));
        assertTrue(error.getMessage().startsWith("MODEL_PRECONDITION_FAILED"));
    }

    private static void assertIllegal(TemplateLifecycle.Lifecycle from, TemplateLifecycle.Lifecycle to) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TemplateLifecycle.requireTransition(from, to));
        assertTrue(error.getMessage().startsWith("MODEL_PRECONDITION_FAILED"),
                from + " -> " + to + " 必须拒绝");
    }
}
