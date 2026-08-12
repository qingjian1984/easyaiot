package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.common.utils.SnowflakeIdUtil;
import com.basiclab.iot.device.config.PowerModelIdempotencySecretProvider;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplatePublishRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplatePublishResponse;
import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.basiclab.iot.device.service.event.OutboxEntry;
import com.basiclab.iot.device.service.event.PowerModelOutboxService;
import com.basiclab.iot.device.service.idempotency.IdempotencyArbiter;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.basiclab.iot.device.service.model.JcsCanonicalizer;
import com.basiclab.iot.device.service.model.ModelSemVer;
import com.basiclab.iot.device.service.model.PowerModelTemplateContentValidator;
import com.basiclab.iot.device.service.model.TemplateDiffEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TD-005 §7/§10/§11/§17：模板版本、成员索引、审计与 Outbox 的原子发布事务。 */
@Service
@ConditionalOnProperty(prefix = "easyaiot.power-model", name = "template-api-enabled",
        havingValue = "true")
public class PowerModelTemplatePublishService {

    public static final String CAPABILITY_CODE = "power.device.model";
    public static final String IDEMPOTENCY_OPERATION = "PUBLISH";
    public static final String EVENT_TYPE = "POWER_MODEL_TEMPLATE_PUBLISHED_V1";
    public static final String AGGREGATE_TYPE = "power_model_template";
    private static final int OUTBOX_MAX_RETRIES = 12;
    private static final int MAX_DIFF_CHANGES = 128;
    private static final Pattern ETAG = Pattern.compile("^\\\"(0|[1-9][0-9]*)\\\"$");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CapabilityService capabilityService;
    private final TenantFrameworkService tenantFrameworkService;
    private final PowerModelTemplateContentValidator contentValidator;
    private final PowerModelOutboxService outboxService;
    private final JdbcPowerIdempotencyStore idempotencyStore;
    private final byte[] idempotencySecret;
    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();
    private final TemplateDiffEngine diffEngine = new TemplateDiffEngine();

    public PowerModelTemplatePublishService(DataSource dataSource, ObjectMapper mapper,
                                            CapabilityService capabilityService,
                                            TenantFrameworkService tenantFrameworkService,
                                            PowerModelTemplateContentValidator contentValidator,
                                            PowerModelOutboxService outboxService,
                                            JdbcPowerIdempotencyStore idempotencyStore,
                                            PowerModelIdempotencySecretProvider secretProvider) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.capabilityService = Objects.requireNonNull(capabilityService, "capabilityService");
        this.tenantFrameworkService = Objects.requireNonNull(tenantFrameworkService,
                "tenantFrameworkService");
        this.contentValidator = Objects.requireNonNull(contentValidator, "contentValidator");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.idempotencySecret = Objects.requireNonNull(secretProvider, "secretProvider")
                .getSecret();
    }

    @Transactional(rollbackFor = Exception.class)
    public PowerModelTemplatePublishResponse publish(long tenantId, String templateCode,
                                                      long draftId,
                                                      PowerModelTemplatePublishRequest request,
                                                      long actorId, String idempotencyKey,
                                                      String requestId, String traceId,
                                                      String ifMatch) {
        requireCapability();
        validateRequest(tenantId, templateCode, draftId, request, actorId, idempotencyKey,
                requestId, ifMatch);
        if (idempotencySecret.length < 32) {
            fail("IDEMPOTENCY_SECRET_UNAVAILABLE", "幂等 HMAC secret 未配置或少于 32 UTF-8 字节");
        }
        tenantFrameworkService.validTenant(tenantId);
        JdbcPowerIdempotencyStore.Scope scope = scope(tenantId, actorId, idempotencyKey);
        JdbcPowerIdempotencyStore.Claim claim = idempotencyStore.claim(scope,
                requestHash(templateCode, draftId, request));
        if (claim.outcome() == JdbcPowerIdempotencyStore.Claim.Outcome.REPLAY) {
            return replay(claim);
        }

        long expectedRevision = parseEtag(ifMatch);
        acquireTemplateLock(tenantId, templateCode);
        DraftFact draft = lockDraft(tenantId, templateCode, draftId);
        if (draft.revision != expectedRevision) {
            fail("MODEL_PRECONDITION_FAILED", "If-Match 与当前 draftRevision 不一致");
        }
        JsonNode content = parseAndVerify(draft);
        List<PowerModelTemplateContentValidator.ValidationError> validationErrors =
                contentValidator.validate(content);
        if (!validationErrors.isEmpty()) {
            fail("MODEL_TEMPLATE_SCHEMA_INVALID", "模板存在 " + validationErrors.size()
                    + " 个可判定错误；首个错误 " + validationErrors.get(0));
        }
        ModelSemVer target = ModelSemVer.parse(draft.version);
        ModelSemVer.requireProductionBindable(target);
        Baseline baseline = nearestPublished(tenantId, draft.templateId, target);
        TemplateDiffEngine.DiffResult diff = baseline == null ? null
                : diffEngine.diff(baseline.content, content);
        if (diff != null) {
            ModelSemVer.requireAllowedBump(ModelSemVer.parse(baseline.version), target,
                    diff.minimumBump());
        }
        String diffSummary = diffSummary(baseline, diff);
        Instant publishedAt = Instant.now();
        String actor = Long.toString(actorId);
        String auditEventId = uuid();
        String eventId = uuid();
        int updated = jdbc.update("UPDATE public.power_model_template_version SET"
                        + " lifecycle='PUBLISHED',draft_state=NULL,last_activity_at=NULL,expires_at=NULL,"
                        + " diff_summary=CAST(:diffSummary AS jsonb),published_by=:actor,"
                        + " published_at=CAST(:publishedAt AS timestamptz),updated_by=:actor,"
                        + " updated_at=CURRENT_TIMESTAMP WHERE tenant_id=:tenantId AND id=:draftId"
                        + " AND template_id=:templateId AND lifecycle='DRAFT' AND draft_state='ACTIVE'"
                        + " AND draft_revision=:revision",
                params(tenantId, draft).addValue("diffSummary", diffSummary)
                        .addValue("actor", actor).addValue("publishedAt", publishedAt.toString())
                        .addValue("revision", expectedRevision));
        if (updated != 1) fail("MODEL_PRECONDITION_FAILED", "草稿发布 CAS 失败");
        insertMemberIndex(tenantId, draftId, content);
        insertAudit(tenantId, draft, actor, requestId, traceId, request, auditEventId,
                baseline, diff, diffSummary);
        String payload = eventPayload(eventId, tenantId, draft, actor, requestId, traceId,
                publishedAt);
        outboxService.enqueue(OutboxEntry.of(nextId(), eventId, tenantId, auditEventId,
                AGGREGATE_TYPE, Long.toString(draftId), EVENT_TYPE, 1, payload,
                OUTBOX_MAX_RETRIES));
        PowerModelTemplatePublishResponse response = new PowerModelTemplatePublishResponse(
                Long.toString(draftId), templateCode, draft.version, draft.contentHash,
                publishedAt.toString(), eventId);
        complete(scope, response, draftId);
        return response;
    }

    private void requireCapability() {
        if (!capabilityService.isEnabled(CAPABILITY_CODE)) {
            throw new ServiceException("CAPABILITY_NOT_SUPPORTED: 当前部署不支持能力 "
                    + CAPABILITY_CODE);
        }
    }

    private void acquireTemplateLock(long tenantId, String templateCode) {
        try {
            jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout = '15s'");
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey,0))",
                    new MapSqlParameterSource("lockKey", tenantId + ":" + templateCode), rs -> null);
        } catch (DataAccessException error) {
            if (hasSqlState(error, "55P03")) {
                fail("MODEL_TEMPLATE_PUBLISH_LOCK_TIMEOUT", "模板发布锁等待超过 15 秒");
            }
            throw error;
        }
    }

    private DraftFact lockDraft(long tenantId, String templateCode, long draftId) {
        List<DraftFact> rows = jdbc.query("SELECT v.template_id,v.version,v.content_canonical,"
                        + " v.content_hash,v.source_type,v.source_artifact_id,v.draft_revision"
                        + " FROM public.power_model_template t JOIN public.power_model_template_version v"
                        + " ON v.tenant_id=t.tenant_id AND v.template_id=t.id"
                        + " WHERE t.tenant_id=:tenantId AND t.template_code=:templateCode"
                        + " AND t.status='ACTIVE' AND v.id=:draftId AND v.lifecycle='DRAFT'"
                        + " AND v.draft_state='ACTIVE' FOR UPDATE OF v",
                new MapSqlParameterSource("tenantId", tenantId)
                        .addValue("templateCode", templateCode).addValue("draftId", draftId),
                (rs, rowNum) -> new DraftFact(draftId, rs.getLong("template_id"), templateCode,
                        rs.getString("version"), rs.getString("content_canonical"),
                        rs.getString("content_hash"), rs.getString("source_type"),
                        rs.getString("source_artifact_id"), rs.getLong("draft_revision")));
        if (rows.size() != 1) fail("MODEL_TEMPLATE_DRAFT_NOT_FOUND", "ACTIVE 草稿不存在");
        return rows.get(0);
    }

    private JsonNode parseAndVerify(DraftFact draft) {
        try {
            JsonNode content = mapper.readTree(draft.canonical);
            if (content == null || !content.isObject()
                    || !draft.canonical.equals(canonicalizer.canonicalize(content))
                    || !draft.contentHash.equals(canonicalizer.contentHash(content))) {
                fail("MODEL_TEMPLATE_STORED_CONTENT_INVALID", "草稿 canonical/hash 完整性校验失败");
            }
            return content;
        } catch (JsonProcessingException error) {
            fail("MODEL_TEMPLATE_STORED_CONTENT_INVALID", "草稿 canonical 不是合法 JSON");
            return null;
        }
    }

    private Baseline nearestPublished(long tenantId, long templateId, ModelSemVer target) {
        List<Baseline> rows = jdbc.query("SELECT version,content_canonical,content_hash FROM"
                        + " public.power_model_template_version WHERE tenant_id=:tenantId"
                        + " AND template_id=:templateId AND lifecycle='PUBLISHED'",
                new MapSqlParameterSource("tenantId", tenantId).addValue("templateId", templateId),
                (rs, rowNum) -> baseline(rs.getString("version"),
                        rs.getString("content_canonical"), rs.getString("content_hash")));
        Baseline nearest = null;
        for (Baseline candidate : rows) {
            ModelSemVer version = ModelSemVer.parse(candidate.version);
            if (version.compareTo(target) >= 0) {
                fail("MODEL_TEMPLATE_SEMVER_BUMP_TOO_LOW",
                        "目标版本必须高于所有已发布版本；冲突版本 " + candidate.version);
            }
            if (nearest == null || version.compareTo(ModelSemVer.parse(nearest.version)) > 0) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    private Baseline baseline(String version, String canonical, String contentHash) {
        try {
            JsonNode content = mapper.readTree(canonical);
            if (content == null || !canonical.equals(canonicalizer.canonicalize(content))
                    || !contentHash.equals(canonicalizer.contentHash(content))) {
                fail("MODEL_TEMPLATE_STORED_CONTENT_INVALID", "比较基线 canonical/hash 不一致");
            }
            return new Baseline(version, content);
        } catch (JsonProcessingException error) {
            fail("MODEL_TEMPLATE_STORED_CONTENT_INVALID", "比较基线 canonical 不是合法 JSON");
            return null;
        }
    }

    private String diffSummary(Baseline baseline, TemplateDiffEngine.DiffResult diff) {
        ObjectNode root = mapper.createObjectNode();
        if (baseline == null || diff == null) {
            root.putNull("comparisonVersion");
            root.putNull("minimumBump");
            root.put("changeCount", 0);
            root.putArray("changes");
        } else {
            root.put("comparisonVersion", baseline.version);
            root.put("minimumBump", diff.minimumBump().name());
            root.put("changeCount", diff.changes().size());
            ArrayNode changes = root.putArray("changes");
            diff.changes().stream().limit(MAX_DIFF_CHANGES).forEach(changes::add);
        }
        return canonicalizer.canonicalize(root);
    }

    private void insertMemberIndex(long tenantId, long versionId, JsonNode content) {
        insertMembers(tenantId, versionId, content.path("properties"), "PROPERTY", "propertyCode",
                "properties", "semanticType");
        insertMembers(tenantId, versionId, content.path("events"), "EVENT", "eventCode",
                "events", "defaultSeverity");
        insertMembers(tenantId, versionId, content.path("services"), "SERVICE", "serviceCode",
                "services", "riskLevel");
    }

    private void insertMembers(long tenantId, long versionId, JsonNode members, String memberType,
                               String codeField, String pointerRoot, String semanticField) {
        for (JsonNode member : members) {
            String code = member.path(codeField).asText();
            jdbc.update("INSERT INTO public.power_model_member_index"
                            + " (id,tenant_id,template_version_id,member_type,member_code,json_pointer,"
                            + " member_fingerprint,required,semantic_type) VALUES"
                            + " (:id,:tenantId,:versionId,:memberType,:memberCode,:pointer,"
                            + " :fingerprint,:required,:semanticType)",
                    new MapSqlParameterSource("id", nextId()).addValue("tenantId", tenantId)
                            .addValue("versionId", versionId).addValue("memberType", memberType)
                            .addValue("memberCode", code)
                            .addValue("pointer", "/" + pointerRoot + "/" + escapePointer(code))
                            .addValue("fingerprint", canonicalizer.contentHash(member))
                            .addValue("required", "PROPERTY".equals(memberType)
                                    && member.path("required").asBoolean(false))
                            .addValue("semanticType", member.path(semanticField).isTextual()
                                    ? member.path(semanticField).textValue() : null));
        }
    }

    private void insertAudit(long tenantId, DraftFact draft, String actor, String requestId,
                             String traceId, PowerModelTemplatePublishRequest request,
                             String auditEventId, Baseline baseline,
                             TemplateDiffEngine.DiffResult diff, String diffSummary) {
        jdbc.update("INSERT INTO public.power_model_audit"
                        + " (id,audit_event_id,tenant_id,operation,aggregate_type,aggregate_id,"
                        + " template_code,template_version,principal_type,principal_id,request_id,"
                        + " trace_id,source_type,source_artifact_id,before_hash,after_hash,semver_bump,"
                        + " reason_code,reason_summary,diff_summary) VALUES"
                        + " (:id,CAST(:auditEventId AS uuid),:tenantId,'TEMPLATE_PUBLISHED',"
                        + " :aggregateType,:aggregateId,:templateCode,:version,'USER',:actor,:requestId,"
                        + " :traceId,:sourceType,:sourceArtifactId,:beforeHash,:afterHash,:semverBump,"
                        + " :reasonCode,:reasonSummary,CAST(:diffSummary AS jsonb))",
                params(tenantId, draft).addValue("id", nextId())
                        .addValue("auditEventId", auditEventId).addValue("aggregateType", AGGREGATE_TYPE)
                        .addValue("aggregateId", Long.toString(draft.id)).addValue("actor", actor)
                        .addValue("requestId", requestId).addValue("traceId", nullToEmpty(traceId))
                        .addValue("sourceType", draft.sourceType)
                        .addValue("sourceArtifactId", draft.sourceArtifactId)
                        .addValue("beforeHash", null)
                        .addValue("afterHash", draft.contentHash)
                        .addValue("semverBump", diff == null ? null : actualBump(
                                ModelSemVer.parse(baseline.version), ModelSemVer.parse(draft.version)))
                        .addValue("reasonCode", defaultValue(request.getReasonCode(), "TEMPLATE_PUBLISHED"))
                        .addValue("reasonSummary", defaultValue(request.getReasonSummary(), "模板版本发布"))
                        .addValue("diffSummary", diffSummary));
    }

    private String eventPayload(String eventId, long tenantId, DraftFact draft, String actor,
                                String requestId, String traceId, Instant publishedAt) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("templateCode", draft.templateCode);
        data.put("templateVersion", draft.version);
        data.put("semver", draft.version);
        data.put("contentHash", draft.contentHash);
        data.put("lifecycle", "PUBLISHED");
        data.put("publishedAt", publishedAt.toString());
        data.put("publisherId", actor);
        PowerModelEventEnvelope envelope = PowerModelEventEnvelope.of(eventId, EVENT_TYPE, 1,
                Long.toString(tenantId), AGGREGATE_TYPE, Long.toString(draft.id),
                publishedAt.toString(), requestId, nullToEmpty(traceId), data);
        ObjectNode root = mapper.createObjectNode();
        root.put("eventId", envelope.eventId());
        root.put("eventType", envelope.eventType());
        root.put("schemaVersion", envelope.schemaVersion());
        root.put("tenantId", envelope.tenantId());
        root.put("aggregateType", envelope.aggregateType());
        root.put("aggregateId", envelope.aggregateId());
        root.put("occurredAt", envelope.occurredAt().toString());
        root.put("requestId", envelope.requestId());
        root.put("traceId", envelope.traceId());
        root.set("data", mapper.valueToTree(envelope.data()));
        return canonicalizer.canonicalize(root);
    }

    private JdbcPowerIdempotencyStore.Scope scope(long tenantId, long actorId, String key) {
        return new JdbcPowerIdempotencyStore.Scope(tenantId, "USER", Long.toString(actorId),
                IDEMPOTENCY_OPERATION, IdempotencyArbiter.keyHash(idempotencySecret, key));
    }

    private byte[] requestHash(String templateCode, long draftId,
                               PowerModelTemplatePublishRequest request) {
        ObjectNode body = mapper.createObjectNode();
        if (request.getReasonCode() != null) body.put("reasonCode", request.getReasonCode());
        if (request.getReasonSummary() != null) body.put("reasonSummary", request.getReasonSummary());
        String path = "/api/v1/power/model-templates/" + templateCode + "/drafts/" + draftId
                + ":publish";
        return IdempotencyArbiter.requestHash("POST", path, canonicalizer.canonicalize(body));
    }

    private PowerModelTemplatePublishResponse replay(JdbcPowerIdempotencyStore.Claim claim) {
        if (!"SUCCEEDED".equals(claim.state()) || claim.httpStatus() != 200
                || claim.responsePayload() == null) {
            fail("IDEMPOTENCY_STATE_CORRUPT", "发布重放终态非法");
        }
        try {
            return mapper.readValue(claim.responsePayload(), PowerModelTemplatePublishResponse.class);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("IDEMPOTENCY_STATE_CORRUPT: 发布响应无法反序列化", error);
        }
    }

    private void complete(JdbcPowerIdempotencyStore.Scope scope,
                          PowerModelTemplatePublishResponse response, long draftId) {
        try {
            idempotencyStore.completeSuccess(scope, 200, mapper.writeValueAsString(response),
                    Long.toString(draftId));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("MODEL_TEMPLATE_RESPONSE_SERIALIZATION_FAILED", error);
        }
    }

    private static void validateRequest(long tenantId, String templateCode, long draftId,
                                        PowerModelTemplatePublishRequest request, long actorId,
                                        String idempotencyKey, String requestId, String ifMatch) {
        if (tenantId <= 0 || draftId <= 0 || actorId <= 0) invalid("tenant/draft/actor 必须为正数");
        nonBlank(templateCode, "templateCode");
        Objects.requireNonNull(request, "request");
        nonBlank(idempotencyKey, "Idempotency-Key");
        nonBlank(requestId, "requestId");
        nonBlank(ifMatch, "If-Match");
        if (idempotencyKey.length() > 256) invalid("Idempotency-Key 超过 256 字符");
        if (request.getReasonCode() != null && request.getReasonCode().length() > 64) {
            invalid("reasonCode 超过 64 字符");
        }
        if (request.getReasonSummary() != null && request.getReasonSummary().length() > 512) {
            invalid("reasonSummary 超过 512 字符");
        }
    }

    private static long parseEtag(String ifMatch) {
        Matcher matcher = ETAG.matcher(ifMatch);
        if (!matcher.matches()) fail("MODEL_PRECONDITION_FAILED", "If-Match 必须是引号包裹的 revision");
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException("MODEL_PRECONDITION_FAILED: If-Match 超出范围", error);
        }
    }

    private static MapSqlParameterSource params(long tenantId, DraftFact draft) {
        return new MapSqlParameterSource("tenantId", tenantId).addValue("draftId", draft.id)
                .addValue("templateId", draft.templateId).addValue("templateCode", draft.templateCode)
                .addValue("version", draft.version);
    }

    private static String actualBump(ModelSemVer base, ModelSemVer target) {
        if (target.major() > base.major()) return "MAJOR";
        if (target.minor() > base.minor()) return "MINOR";
        return "PATCH";
    }

    private static String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
    private static boolean hasSqlState(Throwable error, String expected) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException
                    && expected.equals(((SQLException) current).getSQLState())) return true;
            current = current.getCause();
        }
        return false;
    }
    private static long nextId() { return Long.parseLong(SnowflakeIdUtil.nextId()); }
    private static String uuid() { return UUID.randomUUID().toString().toLowerCase(); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String defaultValue(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
    private static String nonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) invalid(field + " 不得为空");
        return value;
    }
    private static void invalid(String detail) { fail("MODEL_TEMPLATE_PUBLISH_INVALID", detail); }
    private static void fail(String code, String detail) {
        throw new IllegalArgumentException(code + ": " + detail);
    }

    private static final class DraftFact {
        final long id; final long templateId; final String templateCode; final String version;
        final String canonical; final String contentHash; final String sourceType;
        final String sourceArtifactId; final long revision;
        DraftFact(long id, long templateId, String templateCode, String version, String canonical,
                  String contentHash, String sourceType, String sourceArtifactId, long revision) {
            this.id = id; this.templateId = templateId; this.templateCode = templateCode;
            this.version = version; this.canonical = canonical; this.contentHash = contentHash;
            this.sourceType = sourceType; this.sourceArtifactId = sourceArtifactId;
            this.revision = revision;
        }
    }
    private static final class Baseline {
        final String version; final JsonNode content;
        Baseline(String version, JsonNode content) { this.version = version; this.content = content; }
    }
}
