package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ADR-014：{@link JdbcPowerModelInboxRepository} 的真实 PostgreSQL 合同。
 * 表结构由版本化 V005 迁移资产提供；测试仍可在临时评审库自检资产可重入。
 * 对目标实例运行时必须通过独立迁移窗口先落库。覆盖：
 * 首插 ON CONFLICT 裁决、findByEventId 视图、hash CHECK、markProcessed、
 * 隔离 upsert（未知主版本直落隔离行 / 同 ID 更新既有行）。
 */
class JdbcPowerModelInboxRepositoryPostgresIntegrationTest {

    private static final String INBOX_DDL =
            ".doc/技术设计/电力运维云平台/assets/td005-migration/V005__power_model_event_inbox.sql";
    private static final long TENANT = 910_005_202L;
    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final String HASH_A =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B =
            "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcPowerModelInboxRepository repository;

    @BeforeEach
    void setUp() throws java.sql.SQLException {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD005_PG_ENABLED")),
                "Set TD005_PG_ENABLED=true to run the PostgreSQL inbox contract");
        String password = System.getenv("TD005_PG_PASSWORD");
        assumeTrue(password != null && !password.isEmpty(),
                "Set TD005_PG_PASSWORD without committing credentials");
        String url = environmentOrDefault("TD005_PG_URL",
                "jdbc:postgresql://localhost:5432/td005_contract_review");
        String username = environmentOrDefault("TD005_PG_USERNAME", "postgres");

        dataSource = new SingleConnectionDataSource(url, username, password, true);
        jdbc = new JdbcTemplate(dataSource);
        // 候选 DDL 自带 IF NOT EXISTS；每次运行都执行以验证资产可重入。
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new FileSystemResource(resolveRepoFile(INBOX_DDL).toFile()));
        repository = new JdbcPowerModelInboxRepository(dataSource);
        jdbc.update("DELETE FROM public.power_model_event_inbox WHERE tenant_id = ?", TENANT);
    }

    @AfterEach
    void tearDown() {
        if (jdbc != null) {
            jdbc.update("DELETE FROM public.power_model_event_inbox WHERE tenant_id = ?", TENANT);
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void insertReceivedFirstTrueConflictFalse() {
        String eventId = uuid();
        assertTrue(repository.insertReceived(eventId, TENANT,
                "POWER_MODEL_TEMPLATE_PUBLISHED_V1", HASH_A));
        assertFalse(repository.insertReceived(eventId, TENANT,
                "POWER_MODEL_TEMPLATE_PUBLISHED_V1", HASH_A),
                "ON CONFLICT (event_id) DO NOTHING：首插争抢由数据库裁决");
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.power_model_event_inbox WHERE event_id = CAST(? AS uuid)",
                Integer.class, eventId);
        assertEquals(1, rows.intValue());
    }

    @Test
    void findByEventIdReturnsView() {
        String eventId = uuid();
        assertNull(repository.findByEventId(eventId), "未知事件返回 null（触发首插路径）");

        repository.insertReceived(eventId, TENANT, "POWER_MODEL_TEMPLATE_PUBLISHED_V1", HASH_A);
        InboxArbiter.RecordView view = repository.findByEventId(eventId);
        assertNotNull(view);
        assertEquals(HASH_A, view.payloadHash());
        assertEquals(InboxArbiter.Status.RECEIVED, view.status());
    }

    @Test
    void payloadHashCheckConstraintEnforced() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO public.power_model_event_inbox (tenant_id, event_id, event_type, payload_hash, status)"
                        + " VALUES (?, CAST(? AS uuid), 'POWER_MODEL_TEMPLATE_PUBLISHED_V1', ?, 'RECEIVED')",
                TENANT, uuid(), "crc32:not-a-sha256"));
    }

    @Test
    void markProcessedTransitionsStatus() {
        String eventId = uuid();
        repository.insertReceived(eventId, TENANT, "POWER_MODEL_TEMPLATE_PUBLISHED_V1", HASH_A);

        repository.markProcessed(eventId, NOW);

        InboxArbiter.RecordView view = repository.findByEventId(eventId);
        assertEquals(InboxArbiter.Status.PROCESSED, view.status());
        assertNotNull(jdbc.queryForObject(
                "SELECT processed_at FROM public.power_model_event_inbox WHERE event_id = CAST(? AS uuid)",
                java.sql.Timestamp.class, eventId));
    }

    @Test
    void upsertQuarantinedInsertsThenUpdates() {
        String eventId = uuid();
        // 未知主版本入口：无既有行，直落隔离行。
        repository.upsertQuarantined(eventId, TENANT, "POWER_MODEL_TEMPLATE_PUBLISHED_V9",
                HASH_A, "MODEL_EVENT_UNKNOWN_MAJOR_VERSION", "schemaVersion=9");
        InboxArbiter.RecordView inserted = repository.findByEventId(eventId);
        assertEquals(InboxArbiter.Status.QUARANTINED, inserted.status());
        assertEquals(HASH_A, inserted.payloadHash());

        // 异 hash 入口：同 eventId 既有行被更新（ON CONFLICT DO UPDATE），行数不变；
        // payload_hash 保留首次隔离事实，状态与错误摘要刷新为最新冲突。
        repository.upsertQuarantined(eventId, TENANT, "POWER_MODEL_TEMPLATE_PUBLISHED_V9",
                HASH_B, "MODEL_EVENT_HASH_CONFLICT", "same eventId different payload_hash");
        InboxArbiter.RecordView updated = repository.findByEventId(eventId);
        assertEquals(InboxArbiter.Status.QUARANTINED, updated.status());
        assertEquals(HASH_A, updated.payloadHash(), "隔离 upsert 保留首次隔离的 payload_hash 事实");
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.power_model_event_inbox WHERE event_id = CAST(? AS uuid)",
                Integer.class, eventId);
        assertEquals(1, rows.intValue());
        String errorCode = jdbc.queryForObject(
                "SELECT last_error_code FROM public.power_model_event_inbox WHERE event_id = CAST(? AS uuid)",
                String.class, eventId);
        assertEquals("MODEL_EVENT_HASH_CONFLICT", errorCode);
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
