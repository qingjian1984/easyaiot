package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ADR-014：{@link JdbcPowerModelOutboxRepository} 的真实 PostgreSQL 合同。
 * 表结构由本测试用 V001 DDL 资产在临时评审库自检（禁止在共享/生产库执行；
 * 执行环境为本地临时评审库，用后删除）。覆盖：首插/唯一约束/hash CHECK、
 * 原子认领（到期/未到期/租约/SKIP LOCKED 并发互斥/批量上限）、三种回写与
 * countByStatus gauge 数据源。
 */
class JdbcPowerModelOutboxRepositoryPostgresIntegrationTest {

    private static final String V001_DDL =
            ".doc/技术设计/电力运维云平台/assets/td005-migration/V001__power_model_version_audit_outbox.sql";
    private static final long TENANT = 910_005_201L;
    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcPowerModelOutboxRepository repository;

    @BeforeEach
    void setUp() throws java.io.IOException, java.sql.SQLException {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD005_PG_ENABLED")),
                "Set TD005_PG_ENABLED=true to run the PostgreSQL outbox contract");
        String password = System.getenv("TD005_PG_PASSWORD");
        assumeTrue(password != null && !password.isEmpty(),
                "Set TD005_PG_PASSWORD without committing credentials");
        String url = environmentOrDefault("TD005_PG_URL",
                "jdbc:postgresql://localhost:5432/td005_contract_review");
        String username = environmentOrDefault("TD005_PG_USERNAME", "postgres");

        dataSource = new SingleConnectionDataSource(url, username, password, true);
        jdbc = new JdbcTemplate(dataSource);
        if (jdbc.queryForObject(
                "SELECT to_regclass('public.power_model_release_outbox')", String.class) == null) {
            // 临时评审库首跑：用 V001 资产建表（含审计表/触发器；资产本身一并获得可执行性证据）。
            // 整文件单语句执行：ScriptUtils 不识别 PL/pgSQL $$ 美元引号会在函数体内截断；
            // PostgreSQL JDBC 支持无参数多语句单 execute（资产无 psql 元命令，已核实）。
            java.sql.Statement statement = dataSource.getConnection().createStatement();
            try {
                statement.execute(new String(
                        Files.readAllBytes(resolveRepoFile(V001_DDL)), java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                statement.close();
            }
        }
        repository = new JdbcPowerModelOutboxRepository(dataSource);
        jdbc.update("DELETE FROM public.power_model_release_outbox WHERE tenant_id = ?", TENANT);
    }

    @AfterEach
    void tearDown() {
        if (jdbc != null) {
            jdbc.update("DELETE FROM public.power_model_release_outbox WHERE tenant_id = ?", TENANT);
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void insertPendingPersistsAndDuplicateEventIdRejected() {
        String auditEventId = insertAuditRow();
        String eventId = uuid();
        repository.insertPending(entry(1L, eventId, auditEventId));

        String status = jdbc.queryForObject(
                "SELECT status FROM public.power_model_release_outbox WHERE event_id = CAST(? AS uuid)",
                String.class, eventId);
        assertEquals("PENDING", status);
        assertNull(jdbc.queryForObject(
                "SELECT lease_owner FROM public.power_model_release_outbox WHERE event_id = CAST(? AS uuid)",
                String.class, eventId));
        assertEquals(1L, repository.countByStatus("PENDING"));

        // UNIQUE(event_id) 由数据库裁决首插争抢。
        String secondAudit = insertAuditRow();
        assertThrows(DataAccessException.class,
                () -> repository.insertPending(entry(2L, eventId, secondAudit)));
    }

    @Test
    void payloadHashCheckConstraintEnforced() {
        String auditEventId = insertAuditRow();
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO public.power_model_release_outbox ("
                        + " id, event_id, tenant_id, audit_event_id, aggregate_type, aggregate_id,"
                        + " event_type, payload, payload_hash)"
                        + " VALUES (?, CAST(? AS uuid), ?, CAST(? AS uuid), 'power_model_template', '1001',"
                        + " 'POWER_MODEL_TEMPLATE_PUBLISHED_V1', CAST('{}' AS jsonb), ?)",
                9L, uuid(), TENANT, auditEventId, "md5:not-a-sha256"));
    }

    @Test
    void claimDueClaimsDueAndReclaimsExpiredLeaseOnly() {
        String dueEvent = uuid();
        String notDueEvent = uuid();
        String leasedEvent = uuid();
        String expiredEvent = uuid();
        repository.insertPending(entry(1L, dueEvent, insertAuditRow()));
        repository.insertPending(entry(2L, notDueEvent, insertAuditRow()));
        repository.insertPending(entry(3L, leasedEvent, insertAuditRow()));
        repository.insertPending(entry(4L, expiredEvent, insertAuditRow()));
        makeDue(dueEvent);
        jdbc.update("UPDATE public.power_model_release_outbox SET next_attempt_at = ?"
                        + " WHERE event_id = CAST(? AS uuid)",
                java.sql.Timestamp.from(NOW.plusSeconds(3600)), notDueEvent);
        jdbc.update("UPDATE public.power_model_release_outbox SET status = 'PUBLISHING',"
                        + " lease_owner = 'other', lease_until = ? WHERE event_id = CAST(? AS uuid)",
                java.sql.Timestamp.from(NOW.plusSeconds(3600)), leasedEvent);
        jdbc.update("UPDATE public.power_model_release_outbox SET status = 'PUBLISHING',"
                        + " lease_owner = 'crashed', lease_until = ? WHERE event_id = CAST(? AS uuid)",
                java.sql.Timestamp.from(NOW.minusSeconds(1)), expiredEvent);

        List<ClaimedOutboxEntry> claimed =
                repository.claimDue(NOW, "it-owner", Duration.ofSeconds(60), 100);

        assertEquals(2, claimed.size(), "到期 PENDING + 租约过期 PUBLISHING 才可认领");
        assertTrue(claimed.stream().anyMatch(e -> dueEvent.equals(e.eventId())));
        assertTrue(claimed.stream().anyMatch(e -> expiredEvent.equals(e.eventId())));
        Integer publishing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.power_model_release_outbox"
                        + " WHERE tenant_id = ? AND status = 'PUBLISHING' AND lease_owner = 'it-owner'",
                Integer.class, TENANT);
        assertEquals(2, publishing.intValue());
    }

    @Test
    void claimRespectsBatchSizeAndInsertionOrder() {
        String first = uuid();
        String second = uuid();
        repository.insertPending(entry(1L, first, insertAuditRow()));
        repository.insertPending(entry(2L, second, insertAuditRow()));
        repository.insertPending(entry(3L, uuid(), insertAuditRow()));
        makeDue(first);
        makeDue(second);
        jdbc.update("UPDATE public.power_model_release_outbox SET next_attempt_at = ?"
                        + " WHERE tenant_id = ? AND status = 'PENDING'",
                java.sql.Timestamp.from(NOW.minusSeconds(1)), TENANT);

        List<ClaimedOutboxEntry> claimed =
                repository.claimDue(NOW, "it-owner", Duration.ofSeconds(60), 2);

        assertEquals(2, claimed.size(), "批量上限 2");
        assertEquals(first, claimed.get(0).eventId(), "ORDER BY created_at, id：先插先认领");
        assertEquals(second, claimed.get(1).eventId());
    }

    @Test
    void concurrentClaimIsDisjointViaSkipLocked() {
        String eventA = uuid();
        String eventB = uuid();
        repository.insertPending(entry(1L, eventA, insertAuditRow()));
        repository.insertPending(entry(2L, eventB, insertAuditRow()));
        makeDue(eventA);
        makeDue(eventB);

        // 连接 1 在显式事务中认领 1 条（行锁持有中），连接 2 认领时必须跳过。
        SingleConnectionDataSource first = new SingleConnectionDataSource(
                dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword(), true);
        try {
            first.getConnection().setAutoCommit(false);
            JdbcPowerModelOutboxRepository firstRepo = new JdbcPowerModelOutboxRepository(first);
            List<ClaimedOutboxEntry> claimedByFirst =
                    firstRepo.claimDue(NOW, "owner-1", Duration.ofSeconds(60), 1);
            assertEquals(1, claimedByFirst.size());

            List<ClaimedOutboxEntry> claimedBySecond =
                    repository.claimDue(NOW, "owner-2", Duration.ofSeconds(60), 100);
            assertEquals(1, claimedBySecond.size(), "SKIP LOCKED：另一副本只能认领剩余条目");
            assertTrue(!claimedByFirst.get(0).eventId().equals(claimedBySecond.get(0).eventId()),
                    "并发副本不得认领到同一条目");
            first.getConnection().rollback();
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("concurrent claim fixture failed", ex);
        } finally {
            first.destroy();
        }
        // 连接 1 回滚后其认领行回到 PENDING（残留由 tearDown 清理）。
        assertEquals(1L, repository.countByStatus("PENDING"));
    }

    @Test
    void markPublishedRetryDeadLetterTransitions() {
        String publishedEvent = uuid();
        String retryEvent = uuid();
        String deadEvent = uuid();
        repository.insertPending(entry(1L, publishedEvent, insertAuditRow()));
        repository.insertPending(entry(2L, retryEvent, insertAuditRow()));
        repository.insertPending(entry(3L, deadEvent, insertAuditRow()));
        makeDue(publishedEvent);
        makeDue(retryEvent);
        makeDue(deadEvent);
        repository.claimDue(NOW, "it-owner", Duration.ofSeconds(60), 100);

        repository.markPublished(publishedEvent, NOW);
        assertEquals("PUBLISHED", statusOf(publishedEvent));
        assertNotNull(jdbc.queryForObject(
                "SELECT published_at FROM public.power_model_release_outbox WHERE event_id = CAST(? AS uuid)",
                java.sql.Timestamp.class, publishedEvent));

        StringBuilder longDigest = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longDigest.append('d');
        }
        repository.markRetry(retryEvent, 1, NOW.plusSeconds(1), "MODEL_EVENT_SEND_RETRYABLE",
                longDigest.toString());
        assertEquals("PENDING", statusOf(retryEvent));
        Integer digestLength = jdbc.queryForObject(
                "SELECT length(last_error_digest) FROM public.power_model_release_outbox"
                        + " WHERE event_id = CAST(? AS uuid)", Integer.class, retryEvent);
        assertEquals(128, digestLength.intValue(), "错误摘要截断到 128（DDL VARCHAR 边界内）");

        repository.markDeadLetter(deadEvent, "MODEL_EVENT_SEND_FINAL", "digest");
        assertEquals("DEAD_LETTER", statusOf(deadEvent));
        assertEquals(1L, repository.countByStatus("DEAD_LETTER"));
        assertEquals(1L, repository.countByStatus("PUBLISHED"));
        assertEquals(1L, repository.countByStatus("PENDING"));

        // 防漂移守卫：非 PUBLISHING 行上的回写必须为空操作。
        repository.markPublished(retryEvent, NOW);
        assertEquals("PENDING", statusOf(retryEvent), "status 守卫禁止跨状态回写");
    }

    /** 将条目置为到期可认领（insertPending 默认 CURRENT_TIMESTAMP，测试用固定时钟须显式回拨）。 */
    private void makeDue(String eventId) {
        jdbc.update("UPDATE public.power_model_release_outbox SET next_attempt_at = ?"
                        + " WHERE event_id = CAST(? AS uuid)",
                java.sql.Timestamp.from(NOW.minusSeconds(1)), eventId);
    }

    private OutboxEntry entry(long id, String eventId, String auditEventId) {
        return OutboxEntry.of(id, eventId, TENANT, auditEventId,
                "power_model_template", "1001",
                "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, "{\"k\":1}", 12);
    }

    private String insertAuditRow() {
        String auditEventId = uuid();
        jdbc.update("INSERT INTO public.power_model_audit ("
                        + " id, audit_event_id, tenant_id, operation, aggregate_type, aggregate_id,"
                        + " principal_type, principal_id, request_id)"
                        + " VALUES (?, CAST(? AS uuid), ?, 'TEMPLATE_PUBLISHED',"
                        + " 'power_model_template', '1001', 'SERVICE', 'it-runner', ?)",
                System.nanoTime(), auditEventId, TENANT, "it-" + auditEventId);
        return auditEventId;
    }

    private String statusOf(String eventId) {
        return jdbc.queryForObject(
                "SELECT status FROM public.power_model_release_outbox WHERE event_id = CAST(? AS uuid)",
                String.class, eventId);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** 从 user.dir 向上回溯仓库根（surefire 工作目录为模块 basedir）。 */
    private static Path resolveRepoFile(String relativePath) {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(relativePath))) {
                return current.resolve(relativePath);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("workspace root not found for " + relativePath);
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
