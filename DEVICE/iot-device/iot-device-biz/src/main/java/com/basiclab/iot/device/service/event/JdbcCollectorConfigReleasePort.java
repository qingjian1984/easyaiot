package com.basiclab.iot.device.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * TD-001 1.0.13：发布已确认的 VALIDATED collector 候选单并原子推进 workload 投影。
 *
 * <p>本实现绝不从上一发布单、device.extension 或 poller 默认值生成点表。候选单必须已由
 * 绑定应用/回滚事务以同一个 Outbox eventId 写入，并通过 V007 的同租户 binding/Outbox 外键。
 * Bean 默认关闭；只有 V007 已受控落库且显式启用配置后才装配第四协调端口。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "easyaiot.power-model",
        name = "collector-release-port-enabled", havingValue = "true")
public class JdbcCollectorConfigReleasePort implements CollectorConfigReleasePort {

    static final String CODE_CONFLICT = "COLLECTOR_CONFIG_RELEASE_CONFLICT";

    private static final String DESIRED_SQL =
            "SELECT product_id, template_code, template_version, binding_revision"
                    + " FROM public.collector_workload_binding_projection"
                    + " WHERE tenant_id=:tenantId AND workload_id=:workloadId"
                    + " AND lifecycle_status='ACTIVE'";
    private static final String LOCK_PROJECTION_SQL =
            "SELECT id, site_id, site_code, node_id, product_id, template_code, template_version,"
                    + " binding_revision, config_version, release_id, projection_revision"
                    + " FROM public.collector_workload_binding_projection"
                    + " WHERE tenant_id=:tenantId AND workload_id=:workloadId"
                    + " AND lifecycle_status='ACTIVE' FOR UPDATE";
    private static final String BINDING_SQL =
            "SELECT template_code, template_version FROM public.power_product_model_binding"
                    + " WHERE tenant_id=:tenantId AND product_id=:productId"
                    + " AND binding_revision=:bindingRevision";
    private static final String CANDIDATE_SQL =
            "SELECT id, site_id, site_code, node_id, config_version, schema_version,"
                    + " canonicalization_version, payload_canonical, payload::text AS payload_text,"
                    + " payload_sha256, canonical_length_bytes, row_version, source_reason_code"
                    + " FROM public.iot_collector_config_release"
                    + " WHERE tenant_id=:tenantId AND workload_id=:workloadId"
                    + " AND product_id=:productId AND template_code=:templateCode"
                    + " AND template_version=:templateVersion AND binding_revision=:bindingRevision"
                    + " AND source_event_id=CAST(:sourceEventId AS uuid) AND status='VALIDATED'"
                    + " FOR UPDATE";
    private static final String MAX_VERSION_SQL =
            "SELECT max(config_version) FROM public.iot_collector_config_release"
                    + " WHERE tenant_id=:tenantId AND workload_id=:workloadId";
    private static final String PUBLISH_SQL =
            "UPDATE public.iot_collector_config_release SET status='PUBLISHED',"
                    + " published_by=:confirmedBy, published_at=CURRENT_TIMESTAMP,"
                    + " updated_by=:confirmedByText, updated_at=CURRENT_TIMESTAMP,"
                    + " row_version=row_version+1"
                    + " WHERE tenant_id=:tenantId AND id=:releaseId"
                    + " AND status='VALIDATED' AND row_version=:rowVersion";
    private static final String FAIL_SQL =
            "UPDATE public.iot_collector_config_release SET status='FAILED',"
                    + " error_code=:errorCode, error_detail=:errorDetail,"
                    + " updated_at=CURRENT_TIMESTAMP, row_version=row_version+1"
                    + " WHERE tenant_id=:tenantId AND id=:releaseId"
                    + " AND status='VALIDATED' AND row_version=:rowVersion";
    private static final String ADVANCE_PROJECTION_SQL =
            "UPDATE public.collector_workload_binding_projection SET"
                    + " template_code=:templateCode, template_version=:templateVersion,"
                    + " binding_revision=:bindingRevision, config_version=:configVersion,"
                    + " release_id=:releaseId, projection_revision=:nextProjectionRevision,"
                    + " last_synced_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP"
                    + " WHERE tenant_id=:tenantId AND workload_id=:workloadId"
                    + " AND projection_revision=:projectionRevision"
                    + " AND release_id=:previousReleaseId AND lifecycle_status='ACTIVE'";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final CollectorConfigSnapshotContract snapshotContract =
            new CollectorConfigSnapshotContract();

    public JdbcCollectorConfigReleasePort(DataSource dataSource, ObjectMapper mapper,
                                          PlatformTransactionManager transactionManager) {
        DataSource required = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbc = new NamedParameterJdbcTemplate(required);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public boolean desiredMatches(long tenantId, String workloadId, String templateCode,
                                  String templateVersion, long bindingRevision) {
        MapSqlParameterSource params = base(tenantId, workloadId);
        List<Projection> rows = jdbc.query(DESIRED_SQL, params, (rs, rowNum) ->
                new Projection(rs.getLong("product_id"), rs.getString("template_code"),
                        rs.getString("template_version"), rs.getLong("binding_revision")));
        if (rows.isEmpty()) return false;
        if (rows.size() != 1) throw conflict("projection duplicate");
        Projection value = rows.get(0);
        if (value.bindingRevision != bindingRevision) return false;
        return templateCode == null || (templateCode.equals(value.templateCode)
                && templateVersion != null && templateVersion.equals(value.templateVersion));
    }

    @Override
    public void createRegenerationDraft(String workloadId, long tenantId, long productId,
                                        String templateCode, String templateVersion,
                                        long bindingRevision, String reasonCode,
                                        String sourceEventId, long confirmedBy) {
        if (confirmedBy <= 0) throw conflict("confirmedBy invalid");
        if (!"BINDING_APPLIED".equals(reasonCode)
                && !"BINDING_ROLLED_BACK".equals(reasonCode)) {
            throw conflict("reasonCode invalid");
        }
        IllegalArgumentException terminal = transaction.execute(status -> publishCandidate(
                workloadId, tenantId, productId, templateCode, templateVersion,
                bindingRevision, reasonCode, sourceEventId, confirmedBy));
        if (terminal != null) throw terminal;
    }

    private IllegalArgumentException publishCandidate(String workloadId, long tenantId,
                                                       long productId, String suppliedTemplateCode,
                                                       String suppliedTemplateVersion,
                                                       long bindingRevision, String reasonCode,
                                                       String sourceEventId, long confirmedBy) {
        MapSqlParameterSource params = base(tenantId, workloadId)
                .addValue("productId", productId)
                .addValue("bindingRevision", bindingRevision)
                .addValue("sourceEventId", sourceEventId)
                .addValue("confirmedBy", confirmedBy)
                .addValue("confirmedByText", Long.toString(confirmedBy));

        WorkloadProjection projection = exactlyOne(jdbc.query(LOCK_PROJECTION_SQL, params,
                (rs, rowNum) -> new WorkloadProjection(
                        rs.getLong("site_id"), rs.getString("site_code"), rs.getLong("node_id"),
                        rs.getLong("product_id"), rs.getLong("config_version"),
                        rs.getLong("release_id"), rs.getLong("projection_revision"))),
                "collector_workload_binding_projection");
        if (projection.productId != productId) throw conflict("projection product mismatch");

        TemplateIdentity binding = exactlyOne(jdbc.query(BINDING_SQL, params,
                (rs, rowNum) -> new TemplateIdentity(rs.getString("template_code"),
                        rs.getString("template_version"))), "power_product_model_binding");
        if (suppliedTemplateCode != null && (!suppliedTemplateCode.equals(binding.code)
                || suppliedTemplateVersion == null
                || !suppliedTemplateVersion.equals(binding.version))) {
            throw conflict("event template mismatch");
        }
        params.addValue("templateCode", binding.code).addValue("templateVersion", binding.version);

        Candidate candidate = exactlyOne(jdbc.query(CANDIDATE_SQL, params,
                (rs, rowNum) -> new Candidate(rs.getLong("id"), rs.getLong("site_id"),
                        rs.getString("site_code"), rs.getLong("node_id"),
                        rs.getLong("config_version"), rs.getString("schema_version"),
                        rs.getString("canonicalization_version"), rs.getString("payload_canonical"),
                        rs.getString("payload_text"), rs.getString("payload_sha256").trim(),
                        rs.getLong("canonical_length_bytes"), rs.getLong("row_version"),
                        rs.getString("source_reason_code"))), "validated collector release");
        params.addValue("releaseId", candidate.id).addValue("rowVersion", candidate.rowVersion);

        try {
            validateCandidate(candidate, projection, tenantId, workloadId, reasonCode);
        } catch (IllegalArgumentException e) {
            params.addValue("errorCode", CollectorConfigSnapshotContract.CODE_INVALID)
                    .addValue("errorDetail", "validated candidate failed canonical contract");
            requireOne(jdbc.update(FAIL_SQL, params), "mark candidate FAILED");
            return e;
        }

        Long maxVersion = jdbc.queryForObject(MAX_VERSION_SQL, params, Long.class);
        if (candidate.configVersion <= projection.configVersion
                || maxVersion == null || maxVersion.longValue() != candidate.configVersion) {
            throw conflict("configVersion is not the latest monotonic candidate");
        }
        params.addValue("configVersion", candidate.configVersion)
                .addValue("previousReleaseId", projection.releaseId)
                .addValue("projectionRevision", projection.projectionRevision)
                .addValue("nextProjectionRevision", projection.projectionRevision + 1);
        requireOne(jdbc.update(PUBLISH_SQL, params), "publish candidate CAS");
        requireOne(jdbc.update(ADVANCE_PROJECTION_SQL, params), "advance projection CAS");
        return null;
    }

    private void validateCandidate(Candidate candidate, WorkloadProjection projection,
                                   long tenantId, String workloadId, String reasonCode) {
        try {
            JsonNode canonicalRoot = mapper.readTree(candidate.payloadCanonical);
            JsonNode payloadProjection = mapper.readTree(candidate.payloadText);
            CollectorConfigSnapshotContract.Artifact artifact =
                    snapshotContract.validateAndCanonicalize(canonicalRoot);
            if (!canonicalRoot.equals(payloadProjection)
                    || !artifact.canonical().equals(candidate.payloadCanonical)
                    || !artifact.sha256().equals(candidate.sha256)
                    || artifact.lengthBytes() != candidate.lengthBytes
                    || !CollectorConfigSnapshotContract.SCHEMA_VERSION.equals(candidate.schemaVersion)
                    || !CollectorConfigSnapshotContract.CANONICALIZATION_VERSION.equals(
                    candidate.canonicalizationVersion)
                    || candidate.siteId != projection.siteId
                    || candidate.nodeId != projection.nodeId
                    || !candidate.siteCode.equals(projection.siteCode)
                    || !reasonCode.equals(candidate.reasonCode)
                    || !Long.toString(tenantId).equals(canonicalRoot.path("tenantId").asText())
                    || !workloadId.equals(canonicalRoot.path("workloadId").asText())
                    || candidate.configVersion != canonicalRoot.path("configVersion").asLong()
                    || !Long.toString(candidate.siteId).equals(canonicalRoot.path("siteId").asText())
                    || !candidate.siteCode.equals(canonicalRoot.path("siteCode").asText())) {
                throw conflict("candidate canonical facts mismatch");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(CollectorConfigSnapshotContract.CODE_INVALID
                    + ": canonical payload cannot be parsed", e);
        }
    }

    private static MapSqlParameterSource base(long tenantId, String workloadId) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("workloadId", workloadId);
    }

    private static <T> T exactlyOne(List<T> rows, String fact) {
        if (rows == null || rows.size() != 1) {
            throw CollectorConfigSnapshotContract.missingFact(fact + " expected exactly one row");
        }
        return rows.get(0);
    }

    private static void requireOne(int count, String action) {
        if (count != 1) throw conflict(action + " affected " + count + " rows");
    }

    private static IllegalArgumentException conflict(String detail) {
        return new IllegalArgumentException(CODE_CONFLICT + ": " + detail);
    }

    private static final class Projection {
        final long productId;
        final String templateCode;
        final String templateVersion;
        final long bindingRevision;
        Projection(long productId, String templateCode, String templateVersion, long bindingRevision) {
            this.productId = productId;
            this.templateCode = templateCode;
            this.templateVersion = templateVersion;
            this.bindingRevision = bindingRevision;
        }
    }

    private static final class WorkloadProjection {
        final long siteId;
        final String siteCode;
        final long nodeId;
        final long productId;
        final long configVersion;
        final long releaseId;
        final long projectionRevision;
        WorkloadProjection(long siteId, String siteCode, long nodeId, long productId,
                           long configVersion, long releaseId, long projectionRevision) {
            this.siteId = siteId;
            this.siteCode = siteCode;
            this.nodeId = nodeId;
            this.productId = productId;
            this.configVersion = configVersion;
            this.releaseId = releaseId;
            this.projectionRevision = projectionRevision;
        }
    }

    private static final class TemplateIdentity {
        final String code;
        final String version;
        TemplateIdentity(String code, String version) { this.code = code; this.version = version; }
    }

    private static final class Candidate {
        final long id;
        final long siteId;
        final String siteCode;
        final long nodeId;
        final long configVersion;
        final String schemaVersion;
        final String canonicalizationVersion;
        final String payloadCanonical;
        final String payloadText;
        final String sha256;
        final long lengthBytes;
        final long rowVersion;
        final String reasonCode;
        Candidate(long id, long siteId, String siteCode, long nodeId, long configVersion,
                  String schemaVersion, String canonicalizationVersion, String payloadCanonical,
                  String payloadText, String sha256, long lengthBytes, long rowVersion,
                  String reasonCode) {
            this.id = id;
            this.siteId = siteId;
            this.siteCode = siteCode;
            this.nodeId = nodeId;
            this.configVersion = configVersion;
            this.schemaVersion = schemaVersion;
            this.canonicalizationVersion = canonicalizationVersion;
            this.payloadCanonical = payloadCanonical;
            this.payloadText = payloadText;
            this.sha256 = sha256;
            this.lengthBytes = lengthBytes;
            this.rowVersion = rowVersion;
            this.reasonCode = reasonCode;
        }
    }
}
