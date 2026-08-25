package com.basiclab.iot.device.alarm.persistence;

import com.basiclab.iot.device.alarm.application.AlarmSourceTransactionService;
import com.basiclab.iot.device.alarm.infrastructure.persistence.JdbcAlarmOutboxRepository;
import com.basiclab.iot.device.alarm.infrastructure.persistence.JdbcAlarmSourcePersistence;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAlarmPersistenceSqlContractTest {

    @Test
    void sourceAdapterOnlyTouchesV011AlarmTablesAndContainsNoDdl() throws Exception {
        String sql = allSql(JdbcAlarmSourcePersistence.class);
        assertFalse(sql.matches("(?is).*\\b(CREATE|ALTER|DROP|TRUNCATE)\\b.*"));
        assertEquals(Set.of("alarm_source_inbox", "alarm_source_mapping", "alarm_record",
                "alarm_action_log", "alarm_outbox"), tables(sql));
    }

    @Test
    void alarmRecordCasIsTenantSiteAndExpectedVersionScoped() throws Exception {
        String raised = sql(JdbcAlarmSourcePersistence.class, "RECORD_RAISED");
        String recovered = sql(JdbcAlarmSourcePersistence.class, "RECORD_RECOVERED");
        for (String statement : new String[]{raised, recovered}) {
            assertTrue(statement.contains("tenant_id=:tenantId"));
            assertTrue(statement.contains("site_id=:siteId"));
            assertTrue(statement.contains("id=:id"));
            assertTrue(statement.contains("row_version=:expectedVersion"));
            assertTrue(statement.contains("row_version=row_version+1"));
        }
    }

    @Test
    void actionSequenceAllocationIsTenantSiteScopedAtomicReturningWithoutVersionCoupling()
            throws Exception {
        String allocate = sql(JdbcAlarmSourcePersistence.class, "ALLOCATE_NEXT_ACTION_SEQUENCE");
        assertTrue(allocate.contains("UPDATE public.alarm_record SET last_action_sequence=last_action_sequence+1"));
        assertTrue(allocate.contains("tenant_id=:tenantId"));
        assertTrue(allocate.contains("site_id=:siteId"));
        assertTrue(allocate.contains("id=:id"));
        assertTrue(allocate.contains("RETURNING last_action_sequence"));
        assertFalse(allocate.contains("row_version"));
        assertFalse(allocate.toUpperCase().contains("MAX("));

        String all = allSql(JdbcAlarmSourcePersistence.class);
        assertFalse(all.contains("MAX(sequence_no)"));
        assertFalse(all.contains("rowVersion() +"));
    }

    @Test
    void outboxClaimUsesSkipLockedLeaseRecoveryAndOwnerCas() throws Exception {
        String claim = sql(JdbcAlarmOutboxRepository.class, "CLAIM_DUE");
        assertTrue(claim.contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(claim.contains("status='PENDING'"));
        assertTrue(claim.contains("status='PUBLISHING'"));
        assertTrue(claim.contains("lease_until<=:now"));
        assertTrue(claim.contains("ORDER BY c.created_at,c.id"));
        for (String field : new String[]{"MARK_PUBLISHED", "MARK_RETRY", "MARK_DEAD"}) {
            String update = sql(JdbcAlarmOutboxRepository.class, field);
            assertTrue(update.contains("status='PUBLISHING'"));
            assertTrue(update.contains("lease_owner=:leaseOwner"));
        }
    }

    @Test
    void transactionEntryIsRequiresNewAndNotAutoActivated() throws Exception {
        Transactional tx = AlarmSourceTransactionService.class
                .getMethod("process", com.basiclab.iot.device.alarm.application.AlarmSourceCommand.class)
                .getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation());
        assertTrue(AlarmSourceTransactionService.class.getAnnotations().length == 0,
                "本任务不得通过 stereotype 自动启用来源处理");
    }

    private static String allSql(Class<?> type) throws Exception {
        StringBuilder value = new StringBuilder();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                value.append(sql(type, field.getName())).append('\n');
            }
        }
        return value.toString();
    }

    private static String sql(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static Set<String> tables(String sql) {
        Matcher matcher = Pattern.compile("public\\.(alarm_[a-z_]+)").matcher(sql);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) values.add(matcher.group(1));
        return values;
    }
}
