package com.basiclab.iot.device.service.collector.backfill;

import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillIssue;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillResolutionResult;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryEntry;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryPage;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** LC02-04B §16.5: opt-in PostgreSQL fact read and rollback-only fixtures. */
class JdbcRouteBackfillFactRepositoryPostgresIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicLong IDS = new AtomicLong(926_040_821_000_000L);
    private static final Comparator<RouteBackfillKey> KEY_ORDER = Comparator
            .comparing(RouteBackfillKey::tenantId)
            .thenComparing(RouteBackfillKey::siteCode)
            .thenComparingLong(RouteBackfillKey::configVersion)
            .thenComparing(RouteBackfillKey::deviceIdentification);

    @Test
    void acceptsV10V11AndAllAllowedStatusesWithIndependentPositiveFixtures() {
        withDatabase(db -> {
            runCase(db, new ScenarioOptions("v10-published", "1.0", "PUBLISHED",
                    Failure.NONE, ProjectionDrift.NONE),
                    (caseDb, fixture) -> assertResolved(caseDb, fixture));
            runCase(db, new ScenarioOptions("v11-applied", "1.1", "APPLIED",
                    Failure.NONE, ProjectionDrift.NONE),
                    (caseDb, fixture) -> assertResolved(caseDb, fixture));
            runCase(db, new ScenarioOptions("v11-rolled-back", "1.1", "ROLLED_BACK",
                    Failure.NONE, ProjectionDrift.NONE),
                    (caseDb, fixture) -> assertResolved(caseDb, fixture));
        });
    }

    @Test
    void excludesDraftValidatedAndFailedReleasesBeforeFactResolution() {
        withDatabase(db -> {
            runCase(db, new ScenarioOptions("draft", "1.1", "DRAFT",
                    Failure.NONE, ProjectionDrift.NONE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_RELEASE_NOT_UNIQUE"));
            runCase(db, new ScenarioOptions("validated", "1.1", "VALIDATED",
                    Failure.NONE, ProjectionDrift.NONE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_RELEASE_NOT_UNIQUE"));
            runCase(db, new ScenarioOptions("failed", "1.1", "FAILED",
                    Failure.NONE, ProjectionDrift.NONE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_RELEASE_NOT_UNIQUE"));
        });
    }

    @Test
    void rejectsCrossTenantProductAndProjectionIdentityDrift() {
        withDatabase(db -> {
            runCase(db, new ScenarioOptions("cross-tenant-product", "1.1", "PUBLISHED",
                    Failure.CROSS_TENANT_PRODUCT, ProjectionDrift.NONE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_PRODUCT_IDENTITY_MISMATCH"));
            runCase(db, new ScenarioOptions("projection-product-drift", "1.1", "PUBLISHED",
                    Failure.NONE, ProjectionDrift.PRODUCT),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_PROJECTION_IDENTITY_MISMATCH"));
            runCase(db, new ScenarioOptions("projection-site-drift", "1.1", "PUBLISHED",
                    Failure.NONE, ProjectionDrift.SITE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_PROJECTION_IDENTITY_MISMATCH"));
            runCase(db, new ScenarioOptions("projection-node-drift", "1.1", "PUBLISHED",
                    Failure.NONE, ProjectionDrift.NODE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_PROJECTION_IDENTITY_MISMATCH"));
        });
    }

    @Test
    void rejectsPayloadDigestLengthRootIdentityAndMissingDevice() {
        withDatabase(db -> {
            runCase(db, new ScenarioOptions("digest-integrity", "1.1", "PUBLISHED",
                    Failure.DIGEST, ProjectionDrift.NONE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED"));
            assertCanonicalLengthConstraint(db);
            runCase(db, new ScenarioOptions("root-identity", "1.1", "PUBLISHED",
                    Failure.ROOT_IDENTITY, ProjectionDrift.NONE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH"));
            runCase(db, new ScenarioOptions("device-missing", "1.1", "PUBLISHED",
                    Failure.DEVICE_MISSING, ProjectionDrift.NONE),
                    (ignored, fixture) -> assertRejected(fixture,
                            "ROUTE_BACKFILL_DEVICE_NOT_UNIQUE"));
        });
    }

    @Test
    void enabledGateRequiresAllConnectionVariables() {
        if (!"true".equalsIgnoreCase(System.getenv("LC02_ROUTE_BACKFILL_PG_ENABLED"))) {
            return;
        }
        assertNotBlankEnvironment("LC02_ROUTE_BACKFILL_PG_URL");
        assertNotBlankEnvironment("LC02_ROUTE_BACKFILL_PG_USER");
        assertNotBlankEnvironment("LC02_ROUTE_BACKFILL_PG_PASSWORD");
    }

    private static void withDatabase(DatabaseAction action) {
        assumeTrue("true".equalsIgnoreCase(System.getenv("LC02_ROUTE_BACKFILL_PG_ENABLED")),
                "Set LC02_ROUTE_BACKFILL_PG_ENABLED=true for the explicit PostgreSQL gate");
        String url = requiredEnvironment("LC02_ROUTE_BACKFILL_PG_URL");
        String username = requiredEnvironment("LC02_ROUTE_BACKFILL_PG_USER");
        String password = requiredEnvironment("LC02_ROUTE_BACKFILL_PG_PASSWORD");

        PooledDataSource dataSource = new PooledDataSource("org.postgresql.Driver",
                url, username, password);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);
        DatabaseContext db = new DatabaseContext(dataSource, jdbc, transactionManager);
        try {
            assertCurrentSchema(db.jdbc);
            assertFixtureTablesEmpty(db);
            try {
                action.accept(db);
            } finally {
                assertFixtureTablesEmpty(db);
            }
        } finally {
            dataSource.forceCloseAll();
        }
    }

    private static void assertCurrentSchema(JdbcTemplate jdbc) {
        assertEquals("product", jdbc.queryForObject(
                "SELECT to_regclass('public.product')", String.class));
        assertEquals("power_product_model_binding", jdbc.queryForObject(
                "SELECT to_regclass('public.power_product_model_binding')", String.class));
        assertEquals("power_model_release_outbox", jdbc.queryForObject(
                "SELECT to_regclass('public.power_model_release_outbox')", String.class));
        assertEquals("iot_collector_config_release", jdbc.queryForObject(
                "SELECT to_regclass('public.iot_collector_config_release')", String.class));
        assertEquals("collector_workload_binding_projection", jdbc.queryForObject(
                "SELECT to_regclass('public.collector_workload_binding_projection')", String.class));
    }

    private static void runCase(DatabaseContext db, ScenarioOptions options,
                                BiConsumer<DatabaseContext, ScenarioFixture> assertion) {
        try {
            db.transaction.execute(status -> {
                ScenarioFixture fixture = insertFixture(db, options);
                assertion.accept(db, fixture);
                status.setRollbackOnly();
                return null;
            });
        } finally {
            // Rollback removes every fixture row, including append-only audit dependencies.
            assertFixtureTablesEmpty(db);
        }
    }

    private static void assertCanonicalLengthConstraint(DatabaseContext db) {
        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> db.transaction.execute(status -> {
                        insertFixture(db, new ScenarioOptions("length-check", "1.1", "PUBLISHED",
                                Failure.LENGTH, ProjectionDrift.NONE));
                        return null;
                    }));
        } finally {
            assertFixtureTablesEmpty(db);
        }
    }

    private static ScenarioFixture insertFixture(DatabaseContext db, ScenarioOptions options) {
        long base = IDS.getAndAdd(1_000L);
        long tenantId = db.tenantId;
        long foreignTenantId = db.foreignTenantId;
        String workloadId = db.marker + options.name + "-workload";
        String siteCode = db.marker + options.name + "-site";
        String deviceIdentification = db.marker + options.name + "-device";
        String productIdentification = db.marker + options.name + "-product";
        String templateCode = db.marker + options.name + "-template";
        String aggregateId = db.marker + options.name + "-binding";
        long siteId = base + 1;
        long nodeId = base + 2;
        long templateId = base + 3;
        long templateVersionId = base + 4;
        long bindingId = base + 5;
        long auditId = base + 6;
        long outboxId = base + 7;
        long releaseId = base + 8;
        long projectionId = base + 9;
        long configVersion = 1;

        long productId = insertProduct(db.jdbc, tenantId, productIdentification);
        long foreignProductId = -1;
        String foreignProductIdentification = db.marker + options.name + "-foreign-product";
        if (options.failure == Failure.CROSS_TENANT_PRODUCT) {
            foreignProductId = insertProduct(db.jdbc, foreignTenantId, foreignProductIdentification);
        }
        insertTemplate(db.jdbc, tenantId, templateId, templateVersionId, templateCode);
        insertBinding(db.jdbc, tenantId, productId, productIdentification,
                templateVersionId, bindingId, templateCode);
        UUID auditEventId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        insertAudit(db.jdbc, tenantId, auditId, auditEventId, productId,
                productIdentification, bindingId, templateCode, aggregateId);
        insertOutbox(db.jdbc, tenantId, outboxId, sourceEventId, auditEventId,
                aggregateId);

        ObjectNode payload = payload(options, tenantId, foreignTenantId, workloadId, siteCode,
                configVersion, deviceIdentification, productIdentification,
                foreignProductIdentification);
        String canonical = db.canonicalizer.canonicalize(payload);
        String payloadSha256 = options.failure == Failure.DIGEST
                ? "b".repeat(64) : sha256(canonical);
        int actualCanonicalLengthBytes = canonical.getBytes(StandardCharsets.UTF_8).length;
        int storedCanonicalLengthBytes = options.failure == Failure.LENGTH
                ? actualCanonicalLengthBytes + 1 : actualCanonicalLengthBytes;
        insertRelease(db.jdbc, tenantId, releaseId, siteId, siteCode, workloadId, nodeId,
                configVersion, options.schemaVersion, canonical, payloadSha256,
                storedCanonicalLengthBytes, options.status, productId, templateCode, sourceEventId);

        if (isEligible(options.status)) {
            long projectionProductId = productId;
            long projectionNodeId = nodeId;
            String projectionSiteCode = siteCode;
            if (options.projectionDrift == ProjectionDrift.PRODUCT) {
                projectionProductId = productId + 999;
            } else if (options.projectionDrift == ProjectionDrift.NODE) {
                projectionNodeId = nodeId + 999;
            } else if (options.projectionDrift == ProjectionDrift.SITE) {
                projectionSiteCode = siteCode + "-drift";
            }
            insertProjection(db.jdbc, tenantId, projectionId, workloadId, siteId,
                    projectionSiteCode, projectionNodeId, projectionProductId,
                    configVersion, releaseId, templateCode);
        }

        RouteBackfillKey key = new RouteBackfillKey(Long.toString(tenantId), siteCode,
                configVersion, deviceIdentification);
        RouteInventoryPage page = new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                workloadId, List.of(new RouteInventoryEntry(key, 4)), null);
        RouteBackfillManifestResolver resolver = new RouteBackfillManifestResolver(
                new JdbcRouteBackfillFactRepository(db.dataSource), db.transactionManager);
        return new ScenarioFixture(new RouteInventoryArtifact(page), key, releaseId,
                productIdentification, foreignProductId, actualCanonicalLengthBytes, resolver);
    }

    private static void assertResolved(DatabaseContext db, ScenarioFixture fixture) {
        RouteBackfillResolutionResult result = fixture.resolver.resolve(fixture.inventory);
        RouteBackfillResolutionResult.Resolved resolved = assertInstanceOf(
                RouteBackfillResolutionResult.Resolved.class, result);
        assertEquals(RouteBackfillManifest.SCHEMA_VERSION,
                resolved.artifact().manifest().schemaVersion());
        assertEquals(1, resolved.artifact().manifest().entries().size());
        RouteBackfillManifestEntry entry = resolved.artifact().manifest().entries().get(0);
        assertEquals(fixture.key, entry.key());
        assertEquals(4, entry.rowCount());
        assertEquals(fixture.productIdentification, entry.productIdentification());
        assertEquals(fixture.inventory.page().workloadId(), entry.workloadId());
        assertEquals(fixture.releaseId, entry.releaseId());
        assertTrue(entry.payloadSha256().matches("[0-9a-f]{64}"));
        Long persistedLength = db.jdbc.queryForObject(
                "SELECT canonical_length_bytes FROM public.iot_collector_config_release"
                        + " WHERE tenant_id=? AND id=?", Long.class,
                Long.parseLong(fixture.key.tenantId()), fixture.releaseId);
        assertEquals((long) fixture.canonicalLengthBytes, persistedLength);
    }

    private static void assertRejected(ScenarioFixture fixture, String expectedCode) {
        RouteBackfillResolutionResult result = fixture.resolver.resolve(fixture.inventory);
        RouteBackfillResolutionResult.Rejected rejected = assertInstanceOf(
                RouteBackfillResolutionResult.Rejected.class, result);
        assertEquals(List.of(expectedCode), rejected.issues().stream()
                .map(RouteBackfillIssue::code).toList());
        assertEquals(fixture.inventory.contentSha256(), rejected.sourceInventorySha256());
        assertEquals(fixture.inventory.page().workloadId(), rejected.workloadId());
        assertTrue(rejected.issues().stream().noneMatch(issue -> issue.toString().contains("payload")));
    }

    private static long insertProduct(JdbcTemplate jdbc, long tenantId, String identification) {
        Long productId = jdbc.queryForObject(
                "INSERT INTO public.product"
                        + " (app_id,product_name,product_identification,product_type,manufacturer_id,"
                        + " manufacturer_name,model,data_format,device_type,protocol_type,status,tenant_id)"
                        + " VALUES ('lc02-04b','LC02-04B fixture',?,'COMMON','lc02-04b','LC02-04B',"
                        + " 'fixture','JSON','COMMON','MQTT','0',?) RETURNING id",
                Long.class, identification, tenantId);
        if (productId == null || productId <= 0) {
            throw new IllegalStateException("positive product fixture id required");
        }
        return productId;
    }

    private static void insertTemplate(JdbcTemplate jdbc, long tenantId, long templateId,
                                       long templateVersionId, String templateCode) {
        jdbc.update("INSERT INTO public.power_model_template"
                        + " (id,tenant_id,template_code,template_name,device_type,template_kind,owner_scope)"
                        + " VALUES (?,?,?,'LC02-04B fixture','METER','STANDARD','TENANT')",
                templateId, tenantId, templateCode);
        jdbc.update("INSERT INTO public.power_model_template_version"
                        + " (id,tenant_id,template_id,version,major,minor,patch,lifecycle,schema_version,"
                        + " canonicalization_version,hash_algorithm,content_canonical,content_json,"
                        + " content_hash,source_type,published_by,published_at)"
                        + " VALUES (?,?,?,'1.0.0',1,0,0,'PUBLISHED','1.0.0','jcs-rfc8785-v1',"
                        + " 'SHA-256','{}','{}'::jsonb,?,'UI','1',CURRENT_TIMESTAMP)",
                templateVersionId, tenantId, templateId, "sha256:" + "1".repeat(64));
    }

    private static void insertBinding(JdbcTemplate jdbc, long tenantId, long productId,
                                      String productIdentification, long templateVersionId,
                                      long bindingId, String templateCode) {
        jdbc.update("INSERT INTO public.power_product_model_binding"
                        + " (id,tenant_id,product_id,product_identification,binding_revision,status,"
                        + " template_version_id,template_code,template_version,content_hash,"
                        + " binding_snapshot_canonical,binding_snapshot_json,binding_snapshot_hash,"
                        + " effective_from,created_by) VALUES (?,?,?,?,1,'ACTIVE',?,?,'1.0.0',"
                        + " ?,'{}','{}'::jsonb,?,CURRENT_TIMESTAMP,'1')",
                bindingId, tenantId, productId, productIdentification, templateVersionId,
                templateCode, "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64));
    }

    private static void insertAudit(JdbcTemplate jdbc, long tenantId, long auditId,
                                    UUID auditEventId, long productId,
                                    String productIdentification, long bindingId,
                                    String templateCode, String aggregateId) {
        jdbc.update("INSERT INTO public.power_model_audit"
                        + " (id,audit_event_id,tenant_id,operation,aggregate_type,aggregate_id,"
                        + " template_code,template_version,product_id,product_identification,"
                        + " binding_revision,principal_type,principal_id,request_id)"
                        + " VALUES (?,CAST(? AS uuid),?,'BINDING_APPLIED','power_product_model_binding',"
                        + " ?,?,'1.0.0',?,?,1,'USER','1','lc02-04b-request')",
                auditId, auditEventId, tenantId, aggregateId, templateCode, productId,
                productIdentification);
    }

    private static void insertOutbox(JdbcTemplate jdbc, long tenantId, long outboxId,
                                     UUID sourceEventId, UUID auditEventId, String aggregateId) {
        jdbc.update("INSERT INTO public.power_model_release_outbox"
                        + " (id,event_id,tenant_id,audit_event_id,aggregate_type,aggregate_id,event_type,"
                        + " payload,payload_hash) VALUES (?,CAST(? AS uuid),?,CAST(? AS uuid),"
                        + " 'power_product_model_binding',?,'POWER_PRODUCT_MODEL_BINDING_APPLIED_V1',"
                        + " '{}'::jsonb,?)",
                outboxId, sourceEventId, tenantId, auditEventId, aggregateId,
                "sha256:" + "4".repeat(64));
    }

    private static void insertRelease(JdbcTemplate jdbc, long tenantId, long releaseId,
                                      long siteId, String siteCode, String workloadId, long nodeId,
                                      long configVersion, String schemaVersion, String canonical,
                                      String payloadSha256, int canonicalLengthBytes, String status,
                                      long productId, String templateCode, UUID sourceEventId) {
        jdbc.update("INSERT INTO public.iot_collector_config_release"
                        + " (id,tenant_id,site_id,site_code,workload_id,node_id,config_version,"
                        + " schema_version,canonicalization_version,payload_canonical,payload,"
                        + " payload_sha256,canonical_length_bytes,status,published_by,published_at,"
                        + " product_id,template_code,template_version,binding_revision,source_event_id,"
                        + " source_reason_code) VALUES (?,?,?, ?,?,?,?, ?, 'jcs-rfc8785-v1',?,"
                        + " CAST(? AS jsonb),?,?,?, ?,CURRENT_TIMESTAMP,? ,?,'1.0.0',1,"
                        + " CAST(? AS uuid),'BINDING_APPLIED')",
                releaseId, tenantId, siteId, siteCode, workloadId, nodeId, configVersion,
                schemaVersion, canonical, canonical, payloadSha256, canonicalLengthBytes, status,
                isEligible(status) ? 1L : null, productId, templateCode, sourceEventId);
    }

    private static void insertProjection(JdbcTemplate jdbc, long tenantId, long projectionId,
                                         String workloadId, long siteId, String siteCode,
                                         long nodeId, long productId, long configVersion,
                                         long releaseId, String templateCode) {
        jdbc.update("INSERT INTO public.collector_workload_binding_projection"
                        + " (id,tenant_id,workload_id,site_id,site_code,node_id,product_id,template_code,"
                        + " template_version,binding_revision,config_version,release_id,projection_revision,"
                        + " lifecycle_status) VALUES (?,?,?, ?,?,?,?,?,'1.0.0',1,?, ?,1,'STOPPED')",
                projectionId, tenantId, workloadId, siteId, siteCode, nodeId, productId,
                templateCode, configVersion, releaseId);
    }

    private static ObjectNode payload(ScenarioOptions options, long tenantId,
                                      long foreignTenantId, String workloadId, String siteCode,
                                      long configVersion, String deviceIdentification,
                                      String productIdentification,
                                      String foreignProductIdentification) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", options.schemaVersion);
        if ("1.1".equals(options.schemaVersion)) {
            root.put("productIdentification", options.failure == Failure.CROSS_TENANT_PRODUCT
                    ? foreignProductIdentification : productIdentification);
        }
        root.put("tenantId", options.failure == Failure.ROOT_IDENTITY
                ? Long.toString(foreignTenantId) : Long.toString(tenantId));
        root.put("workloadId", workloadId);
        root.put("siteCode", siteCode);
        root.put("configVersion", configVersion);
        ObjectNode device = root.putArray("serialBuses").addObject()
                .putArray("devices").addObject();
        device.put("deviceIdentification", options.failure == Failure.DEVICE_MISSING
                ? deviceIdentification + "-other" : deviceIdentification);
        return root;
    }

    private static boolean isEligible(String status) {
        return "PUBLISHED".equals(status) || "APPLIED".equals(status)
                || "APPLY_TIMEOUT".equals(status) || "ROLLED_BACK".equals(status);
    }

    private static void assertFixtureTablesEmpty(DatabaseContext db) {
        String marker = db.marker + "%";
        assertEquals(0L, count(db.jdbc,
                "SELECT count(*) FROM public.product WHERE product_identification LIKE ?", marker));
        assertEquals(0L, count(db.jdbc,
                "SELECT count(*) FROM public.power_product_model_binding WHERE template_code LIKE ?",
                marker));
        assertEquals(0L, count(db.jdbc,
                "SELECT count(*) FROM public.power_model_release_outbox WHERE aggregate_id LIKE ?",
                marker));
        assertEquals(0L, count(db.jdbc,
                "SELECT count(*) FROM public.iot_collector_config_release WHERE workload_id LIKE ?",
                marker));
        assertEquals(0L, count(db.jdbc,
                "SELECT count(*) FROM public.collector_workload_binding_projection WHERE workload_id LIKE ?",
                marker));
    }

    private static long count(JdbcTemplate jdbc, String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        return value == null ? -1L : value.longValue();
    }

    private static String requiredEnvironment(String name) {
        return assertNotBlankEnvironment(name, System.getenv(name));
    }

    private static String assertNotBlankEnvironment(String name, String value) {
        assertNotNull(value, name + " is required when PG gate is enabled");
        assertTrue(!value.isBlank(), name + " is required when PG gate is enabled");
        return value;
    }

    private static void assertNotBlankEnvironment(String name) {
        assertNotBlankEnvironment(name, System.getenv(name));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) {
                result.append(Character.forDigit((b >>> 4) & 15, 16));
                result.append(Character.forDigit(b & 15, 16));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private enum Failure {
        NONE, CROSS_TENANT_PRODUCT, DIGEST, LENGTH, ROOT_IDENTITY, DEVICE_MISSING
    }

    private enum ProjectionDrift {
        NONE, PRODUCT, SITE, NODE
    }

    private record ScenarioOptions(String name, String schemaVersion, String status,
                                   Failure failure, ProjectionDrift projectionDrift) {
    }

    private static final class DatabaseContext {
        private final DataSource dataSource;
        private final JdbcTemplate jdbc;
        private final DataSourceTransactionManager transactionManager;
        private final TransactionTemplate transaction;
        private final long tenantId = IDS.getAndAdd(1_000_000L);
        private final long foreignTenantId = tenantId + 1;
        private final String marker = "lc02b-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "-";
        private final com.basiclab.iot.device.service.model.JcsCanonicalizer canonicalizer =
                new com.basiclab.iot.device.service.model.JcsCanonicalizer();

        private DatabaseContext(DataSource dataSource, JdbcTemplate jdbc,
                                DataSourceTransactionManager transactionManager) {
            this.dataSource = dataSource;
            this.jdbc = jdbc;
            this.transactionManager = transactionManager;
            this.transaction = new TransactionTemplate(transactionManager);
        }
    }

    @FunctionalInterface
    private interface DatabaseAction {
        void accept(DatabaseContext db);
    }

    private static final class ScenarioFixture {
        private final RouteInventoryArtifact inventory;
        private final RouteBackfillKey key;
        private final long releaseId;
        private final String productIdentification;
        private final long foreignProductId;
        private final int canonicalLengthBytes;
        private final RouteBackfillManifestResolver resolver;

        private ScenarioFixture(RouteInventoryArtifact inventory, RouteBackfillKey key,
                                long releaseId, String productIdentification,
                                long foreignProductId, int canonicalLengthBytes,
                                RouteBackfillManifestResolver resolver) {
            this.inventory = inventory;
            this.key = key;
            this.releaseId = releaseId;
            this.productIdentification = productIdentification;
            this.foreignProductId = foreignProductId;
            this.canonicalLengthBytes = canonicalLengthBytes;
            this.resolver = resolver;
        }
    }
}
