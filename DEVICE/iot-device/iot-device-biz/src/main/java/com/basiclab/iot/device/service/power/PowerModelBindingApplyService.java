package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.common.utils.SnowflakeIdUtil;
import com.basiclab.iot.device.controller.power.dto.PowerModelBindingApplyRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelBindingApplyResponse;
import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.basiclab.iot.device.service.event.CollectorConfigSnapshotContract;
import com.basiclab.iot.device.service.event.OutboxEntry;
import com.basiclab.iot.device.service.event.PowerModelOutboxService;
import com.basiclab.iot.device.service.idempotency.IdempotencyArbiter;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.basiclab.iot.device.service.model.JcsCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * TD-005 §11.4/§17：首次绑定与 VALIDATED collector 候选的原子创建事务。
 *
 * <p>本服务只接受已经具备全部采集源事实的请求。服务端锁定产品与 workload，分配单调版本，
 * 并在同一事务写入绑定、领域审计、Outbox 和不可变候选；事务内绝不调用 NODE。</p>
 */
@Service
public class PowerModelBindingApplyService {

    public static final String CAPABILITY_CODE = "power.device.model";
    public static final String EVENT_TYPE = PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1;
    public static final String AGGREGATE_TYPE = "power_product_model_binding";
    public static final String IDEMPOTENCY_OPERATION = "POWER_MODEL_BINDING_APPLY";
    private static final int OUTBOX_MAX_RETRIES = 12;
    private static final Set<String> COLLECTOR_SOURCE_FIELDS = new HashSet<String>(Arrays.asList(
            "schemaVersion", "workloadId", "tenantId", "siteId", "siteCode", "serialBuses"));

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CapabilityService capabilityService;
    private final TenantFrameworkService tenantFrameworkService;
    private final PowerModelOutboxService outboxService;
    private final JdbcPowerIdempotencyStore idempotencyStore;
    private final byte[] idempotencySecret;
    private final CollectorConfigSnapshotContract collectorContract =
            new CollectorConfigSnapshotContract();
    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();

    public PowerModelBindingApplyService(DataSource dataSource, ObjectMapper mapper,
                                         CapabilityService capabilityService,
                                         TenantFrameworkService tenantFrameworkService,
                                         PowerModelOutboxService outboxService,
                                         JdbcPowerIdempotencyStore idempotencyStore,
                                         @Value("${easyaiot.power-model.idempotency-hmac-secret:}")
                                         String idempotencySecret) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.capabilityService = Objects.requireNonNull(capabilityService, "capabilityService");
        this.tenantFrameworkService = Objects.requireNonNull(
                tenantFrameworkService, "tenantFrameworkService");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.idempotencySecret = Objects.requireNonNull(idempotencySecret, "idempotencySecret")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(rollbackFor = Exception.class)
    public PowerModelBindingApplyResponse apply(long tenantId, String productIdentification,
                                                PowerModelBindingApplyRequest request,
                                                long actorId, String idempotencyKey,
                                                String requestId, String traceId) {
        requireCapability();
        validateRequest(tenantId, productIdentification, request, actorId,
                idempotencyKey, requestId);
        tenantFrameworkService.validTenant(tenantId);

        byte[] keyHash = IdempotencyArbiter.keyHash(idempotencySecret, idempotencyKey);
        byte[] requestHash = requestHash(productIdentification, request);
        PowerModelBindingApplyResponse replay = claimIdempotency(
                tenantId, actorId, keyHash, requestHash);
        if (replay != null) return replay;

        ProductFact product = lockProduct(tenantId, productIdentification);
        TemplateFact template = requirePublishedTemplate(
                tenantId, request.getTemplateCode(), request.getTemplateVersion());
        ActiveBinding previous = lockActiveBinding(tenantId, product.id);
        long bindingRevision = nextBindingRevision(tenantId, product.id);

        String workloadId = text(request.getCollectorSnapshot(), "workloadId");
        lockWorkload(tenantId, workloadId);
        long configVersion = nextConfigVersion(tenantId, workloadId);
        Instant now = Instant.now();

        JsonNode bindingSnapshot = request.getBindingSnapshot();
        String bindingCanonical = canonicalizer.canonicalize(bindingSnapshot);
        String bindingHash = canonicalizer.contentHash(bindingSnapshot);
        ObjectNode collectorPayload = enrichCollectorSnapshot(
                request.getCollectorSnapshot(), configVersion, now);
        CollectorConfigSnapshotContract.Artifact collectorArtifact =
                collectorContract.validateAndCanonicalize(collectorPayload);

        long bindingId = nextId();
        long auditId = nextId();
        long outboxId = nextId();
        long releaseId = nextId();
        String auditEventId = uuid();
        String eventId = uuid();
        String actor = Long.toString(actorId);

        supersedePrevious(previous, tenantId, product.id);
        insertBinding(bindingId, tenantId, product, bindingRevision, template,
                bindingCanonical, bindingHash, previous, actor);
        insertAudit(auditId, auditEventId, tenantId, bindingId, product, bindingRevision,
                template, previous, bindingHash, actor, requestId, traceId);

        String payload = eventPayload(eventId, tenantId, bindingId, product, bindingRevision,
                template, actor, requestId, traceId, now);
        outboxService.enqueue(OutboxEntry.of(outboxId, eventId, tenantId, auditEventId,
                AGGREGATE_TYPE, Long.toString(bindingId), EVENT_TYPE, 1, payload,
                OUTBOX_MAX_RETRIES));
        insertCollectorCandidate(releaseId, tenantId, request.getNodeId().longValue(),
                product.id, bindingRevision, template, collectorPayload, collectorArtifact,
                eventId, actor);
        publishAndProject(releaseId, tenantId, request.getNodeId().longValue(), product.id,
                bindingRevision, template, collectorPayload, actorId, actor);

        PowerModelBindingApplyResponse response = new PowerModelBindingApplyResponse(
                Long.toString(bindingId), bindingRevision,
                Long.toString(releaseId), configVersion, eventId, "PUBLISHED");
        completeIdempotency(tenantId, actorId, keyHash, response);
        return response;
    }

    private void requireCapability() {
        if (!capabilityService.isEnabled(CAPABILITY_CODE)) {
            throw new ServiceException("CAPABILITY_NOT_SUPPORTED: 当前部署不支持能力 "
                    + CAPABILITY_CODE);
        }
    }

    private static void validateRequest(long tenantId, String productIdentification,
                                        PowerModelBindingApplyRequest request, long actorId,
                                        String idempotencyKey, String requestId) {
        if (tenantId <= 0 || actorId <= 0) invalid("tenant/actor 必须是正整数");
        nonBlank(productIdentification, "productIdentification");
        Objects.requireNonNull(request, "request");
        nonBlank(request.getTemplateCode(), "templateCode");
        nonBlank(request.getTemplateVersion(), "templateVersion");
        nonBlank(requestId, "requestId");
        nonBlank(idempotencyKey, "Idempotency-Key");
        if (idempotencyKey.length() > 256) invalid("Idempotency-Key 超过 256 字符");
        if (request.getNodeId() == null || request.getNodeId().longValue() <= 0) {
            invalid("nodeId 必须是正整数");
        }
        if (request.getBindingSnapshot() == null || !request.getBindingSnapshot().isObject()) {
            invalid("bindingSnapshot 必须是对象");
        }
        JsonNode source = request.getCollectorSnapshot();
        if (source == null || !source.isObject()) invalid("collectorSnapshot 必须是对象");
        Set<String> names = new HashSet<String>();
        source.fieldNames().forEachRemaining(names::add);
        if (!COLLECTOR_SOURCE_FIELDS.equals(names)) {
            invalid("collectorSnapshot 源字段集合不匹配");
        }
        if (!Long.toString(tenantId).equals(text(source, "tenantId"))) {
            invalid("collectorSnapshot.tenantId 与安全上下文不一致");
        }
        nonBlank(text(source, "workloadId"), "collectorSnapshot.workloadId");
    }

    private byte[] requestHash(String productIdentification,
                               PowerModelBindingApplyRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("productIdentification", productIdentification);
        root.put("templateCode", request.getTemplateCode());
        root.put("templateVersion", request.getTemplateVersion());
        root.put("nodeId", request.getNodeId().longValue());
        root.set("bindingSnapshot", request.getBindingSnapshot());
        root.set("collectorSnapshot", request.getCollectorSnapshot());
        String path = "/api/v1/products/" + productIdentification + "/model-binding:apply";
        return IdempotencyArbiter.requestHash("POST", path, canonicalizer.canonicalize(root));
    }

    private PowerModelBindingApplyResponse claimIdempotency(long tenantId, long actorId,
                                                             byte[] keyHash,
                                                             byte[] requestHash) {
        if (idempotencySecret.length < 32) {
            fail("IDEMPOTENCY_SECRET_UNAVAILABLE",
                    "幂等 HMAC secret 未配置或少于 32 UTF-8 字节");
        }
        JdbcPowerIdempotencyStore.Claim claim = idempotencyStore.claim(
                idempotencyScope(tenantId, actorId, keyHash), requestHash);
        if (claim.outcome() == JdbcPowerIdempotencyStore.Claim.Outcome.PROCEED) return null;
        if (!"SUCCEEDED".equals(claim.state()) || claim.httpStatus() == null
                || claim.httpStatus().intValue() != 200 || claim.responsePayload() == null) {
            fail("IDEMPOTENCY_RESPONSE_INVALID", "已存终态不是可重放的成功响应");
        }
        try {
            JsonNode value = mapper.readTree(claim.responsePayload());
            return new PowerModelBindingApplyResponse(
                    text(value, "bindingId"), Long.parseLong(text(value, "bindingRevision")),
                    text(value, "collectorConfigReleaseId"),
                    Long.parseLong(text(value, "configVersion")),
                    text(value, "sourceEventId"), text(value, "status"));
        } catch (Exception e) {
            throw new IllegalStateException("IDEMPOTENCY_RESPONSE_INVALID: 已存响应无法重放", e);
        }
    }

    private void completeIdempotency(long tenantId, long actorId, byte[] keyHash,
                                     PowerModelBindingApplyResponse response) {
        String canonicalResponse = canonicalizer.canonicalize(mapper.valueToTree(response));
        idempotencyStore.completeSuccess(idempotencyScope(tenantId, actorId, keyHash), 200,
                canonicalResponse, response.getCollectorConfigReleaseId());
    }

    private static JdbcPowerIdempotencyStore.Scope idempotencyScope(long tenantId, long actorId,
                                                                    byte[] keyHash) {
        return new JdbcPowerIdempotencyStore.Scope(tenantId, "USER", Long.toString(actorId),
                IDEMPOTENCY_OPERATION, keyHash);
    }

    private ProductFact lockProduct(long tenantId, String identification) {
        List<ProductFact> rows = jdbc.query(
                "SELECT id, product_identification FROM public.product"
                        + " WHERE tenant_id=:tenantId AND product_identification=:identification"
                        + " FOR UPDATE",
                new MapSqlParameterSource("tenantId", tenantId)
                        .addValue("identification", identification),
                (rs, rowNum) -> new ProductFact(rs.getLong("id"),
                        rs.getString("product_identification")));
        if (rows.size() != 1) fail("MODEL_PRODUCT_NOT_FOUND", "当前租户产品不存在或标识不唯一");
        return rows.get(0);
    }

    private TemplateFact requirePublishedTemplate(long tenantId, String code, String version) {
        List<TemplateFact> rows = jdbc.query(
                "SELECT v.id, t.template_code, v.version, v.content_hash"
                        + " FROM public.power_model_template t"
                        + " JOIN public.power_model_template_version v"
                        + " ON v.tenant_id=t.tenant_id AND v.template_id=t.id"
                        + " WHERE t.tenant_id=:tenantId AND t.template_code=:code"
                        + " AND t.status='ACTIVE' AND v.version=:version AND v.lifecycle='PUBLISHED'",
                new MapSqlParameterSource("tenantId", tenantId).addValue("code", code)
                        .addValue("version", version),
                (rs, rowNum) -> new TemplateFact(rs.getLong("id"),
                        rs.getString("template_code"), rs.getString("version"),
                        rs.getString("content_hash")));
        if (rows.size() != 1) fail("MODEL_TEMPLATE_VERSION_NOT_PUBLISHED",
                "当前租户不存在唯一已发布模板版本");
        return rows.get(0);
    }

    private ActiveBinding lockActiveBinding(long tenantId, long productId) {
        List<ActiveBinding> rows = jdbc.query(
                "SELECT id,binding_revision,binding_snapshot_hash"
                        + " FROM public.power_product_model_binding"
                        + " WHERE tenant_id=:tenantId AND product_id=:productId AND status='ACTIVE'"
                        + " FOR UPDATE",
                ids(tenantId, productId),
                (rs, rowNum) -> new ActiveBinding(rs.getLong("id"),
                        rs.getLong("binding_revision"), rs.getString("binding_snapshot_hash")));
        if (rows.size() > 1) fail("MODEL_BINDING_STATE_CORRUPT", "产品存在多个 ACTIVE 绑定");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long nextBindingRevision(long tenantId, long productId) {
        Long value = jdbc.queryForObject(
                "SELECT COALESCE(MAX(binding_revision),0)+1 FROM public.power_product_model_binding"
                        + " WHERE tenant_id=:tenantId AND product_id=:productId",
                ids(tenantId, productId), Long.class);
        return Objects.requireNonNull(value, "bindingRevision");
    }

    private void lockWorkload(long tenantId, String workloadId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey,0))",
                new MapSqlParameterSource("lockKey", tenantId + ":" + workloadId), rs -> null);
    }

    private long nextConfigVersion(long tenantId, String workloadId) {
        Long value = jdbc.queryForObject(
                "SELECT COALESCE(MAX(config_version),0)+1 FROM public.iot_collector_config_release"
                        + " WHERE tenant_id=:tenantId AND workload_id=:workloadId",
                new MapSqlParameterSource("tenantId", tenantId).addValue("workloadId", workloadId),
                Long.class);
        return Objects.requireNonNull(value, "configVersion");
    }

    private ObjectNode enrichCollectorSnapshot(JsonNode source, long configVersion, Instant now) {
        ObjectNode result = source.deepCopy();
        result.put("configVersion", configVersion);
        result.put("generatedAt", now.toString());
        return result;
    }

    private void supersedePrevious(ActiveBinding previous, long tenantId, long productId) {
        if (previous == null) return;
        int updated = jdbc.update("UPDATE public.power_product_model_binding"
                        + " SET status='SUPERSEDED',effective_to=CURRENT_TIMESTAMP"
                        + " WHERE tenant_id=:tenantId AND product_id=:productId"
                        + " AND id=:id AND status='ACTIVE'",
                ids(tenantId, productId).addValue("id", previous.id));
        if (updated != 1) fail("MODEL_BINDING_CONCURRENT_MODIFICATION", "ACTIVE 绑定并发变化");
    }

    private void insertBinding(long id, long tenantId, ProductFact product, long revision,
                               TemplateFact template, String canonical, String hash,
                               ActiveBinding previous, String actor) {
        jdbc.update("INSERT INTO public.power_product_model_binding"
                        + " (id,tenant_id,product_id,product_identification,binding_revision,status,"
                        + " template_version_id,template_code,template_version,content_hash,"
                        + " binding_snapshot_canonical,binding_snapshot_json,binding_snapshot_hash,"
                        + " previous_binding_id,effective_from,created_by)"
                        + " VALUES (:id,:tenantId,:productId,:identification,:revision,'ACTIVE',"
                        + " :templateVersionId,:templateCode,:templateVersion,:contentHash,"
                        + " :canonical,CAST(:canonical AS jsonb),:snapshotHash,:previousId,"
                        + " CURRENT_TIMESTAMP,:actor)",
                ids(tenantId, product.id).addValue("id", id)
                        .addValue("identification", product.identification)
                        .addValue("revision", revision).addValue("templateVersionId", template.id)
                        .addValue("templateCode", template.code).addValue("templateVersion", template.version)
                        .addValue("contentHash", template.contentHash).addValue("canonical", canonical)
                        .addValue("snapshotHash", hash)
                        .addValue("previousId", previous == null ? null : previous.id)
                        .addValue("actor", actor));
    }

    private void insertAudit(long id, String auditEventId, long tenantId, long bindingId,
                             ProductFact product, long revision, TemplateFact template,
                             ActiveBinding previous, String afterHash, String actor,
                             String requestId, String traceId) {
        jdbc.update("INSERT INTO public.power_model_audit"
                        + " (id,audit_event_id,tenant_id,operation,aggregate_type,aggregate_id,"
                        + " template_code,template_version,product_id,product_identification,"
                        + " binding_revision,principal_type,principal_id,request_id,trace_id,"
                        + " before_hash,after_hash,reason_code,reason_summary,diff_summary)"
                        + " VALUES (:id,CAST(:auditEventId AS uuid),:tenantId,'BINDING_APPLIED',"
                        + " :aggregateType,:aggregateId,:templateCode,:templateVersion,:productId,"
                        + " :identification,:revision,'USER',:actor,:requestId,:traceId,:beforeHash,"
                        + " :afterHash,'BINDING_APPLIED','产品物模型绑定已应用','{}'::jsonb)",
                ids(tenantId, product.id).addValue("id", id).addValue("auditEventId", auditEventId)
                        .addValue("aggregateType", AGGREGATE_TYPE)
                        .addValue("aggregateId", Long.toString(bindingId))
                        .addValue("templateCode", template.code).addValue("templateVersion", template.version)
                        .addValue("identification", product.identification).addValue("revision", revision)
                        .addValue("actor", actor).addValue("requestId", requestId)
                        .addValue("traceId", traceId == null ? "" : traceId)
                        .addValue("beforeHash", previous == null ? null : previous.snapshotHash)
                        .addValue("afterHash", afterHash));
    }

    private String eventPayload(String eventId, long tenantId, long bindingId, ProductFact product,
                                long revision, TemplateFact template, String actor,
                                String requestId, String traceId, Instant now) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("productId", Long.toString(product.id));
        data.put("productIdentification", product.identification);
        data.put("templateCode", template.code);
        data.put("templateVersion", template.version);
        data.put("bindingRevision", Long.toString(revision));
        data.put("contentHash", template.contentHash);
        data.put("effectiveFrom", now.toString());
        data.put("appliedAt", now.toString());
        data.put("appliedBy", actor);
        PowerModelEventEnvelope envelope = PowerModelEventEnvelope.of(eventId, EVENT_TYPE, 1,
                Long.toString(tenantId), AGGREGATE_TYPE, Long.toString(bindingId), now.toString(),
                requestId, traceId == null ? "" : traceId, data);
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

    private void insertCollectorCandidate(long id, long tenantId, long nodeId, long productId,
                                          long bindingRevision, TemplateFact template,
                                          ObjectNode payload,
                                          CollectorConfigSnapshotContract.Artifact artifact,
                                          String eventId, String actor) {
        jdbc.update("INSERT INTO public.iot_collector_config_release"
                        + " (id,tenant_id,site_id,site_code,workload_id,node_id,config_version,"
                        + " schema_version,canonicalization_version,payload_canonical,payload,"
                        + " payload_sha256,canonical_length_bytes,status,product_id,template_code,"
                        + " template_version,binding_revision,source_event_id,source_reason_code,"
                        + " created_by,updated_by)"
                        + " VALUES (:id,:tenantId,:siteId,:siteCode,:workloadId,:nodeId,:configVersion,"
                        + " :schemaVersion,:canonicalizationVersion,:canonical,CAST(:canonical AS jsonb),"
                        + " :sha256,:length,'VALIDATED',:productId,:templateCode,:templateVersion,"
                        + " :bindingRevision,CAST(:eventId AS uuid),'BINDING_APPLIED',:actor,:actor)",
                new MapSqlParameterSource("id", id).addValue("tenantId", tenantId)
                        .addValue("siteId", Long.parseLong(text(payload, "siteId")))
                        .addValue("siteCode", text(payload, "siteCode"))
                        .addValue("workloadId", text(payload, "workloadId"))
                        .addValue("nodeId", nodeId).addValue("configVersion", payload.get("configVersion").longValue())
                        .addValue("schemaVersion", CollectorConfigSnapshotContract.SCHEMA_VERSION)
                        .addValue("canonicalizationVersion", CollectorConfigSnapshotContract.CANONICALIZATION_VERSION)
                        .addValue("canonical", artifact.canonical()).addValue("sha256", artifact.sha256())
                        .addValue("length", artifact.lengthBytes()).addValue("productId", productId)
                        .addValue("templateCode", template.code).addValue("templateVersion", template.version)
                        .addValue("bindingRevision", bindingRevision).addValue("eventId", eventId)
                        .addValue("actor", actor));
    }

    /** ADR-015 人工发布路径：发布单与 revision=1/递增投影必须在同一事务可见。 */
    private void publishAndProject(long releaseId, long tenantId, long nodeId, long productId,
                                   long bindingRevision, TemplateFact template,
                                   ObjectNode payload, long actorId, String actor) {
        String workloadId = text(payload, "workloadId");
        long siteId = Long.parseLong(text(payload, "siteId"));
        String siteCode = text(payload, "siteCode");
        long configVersion = payload.get("configVersion").longValue();
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId)
                .addValue("workloadId", workloadId).addValue("releaseId", releaseId)
                .addValue("siteId", siteId).addValue("siteCode", siteCode)
                .addValue("nodeId", nodeId).addValue("productId", productId)
                .addValue("templateCode", template.code)
                .addValue("templateVersion", template.version)
                .addValue("bindingRevision", bindingRevision)
                .addValue("configVersion", configVersion)
                .addValue("actorId", actorId).addValue("actor", actor);

        List<String> otherWorkloads = jdbc.query(
                "SELECT workload_id FROM public.collector_workload_binding_projection"
                        + " WHERE tenant_id=:tenantId AND product_id=:productId"
                        + " AND lifecycle_status='ACTIVE' AND workload_id<>:workloadId FOR UPDATE",
                params, (rs, rowNum) -> rs.getString("workload_id"));
        if (!otherWorkloads.isEmpty()) {
            fail("MODEL_BINDING_APPLY_SCOPE_INCOMPLETE",
                    "产品存在请求未覆盖的活动 workload，禁止部分发布");
        }

        List<ProjectionFact> projections = jdbc.query(
                "SELECT id,site_id,site_code,node_id,product_id,release_id,projection_revision"
                        + " FROM public.collector_workload_binding_projection"
                        + " WHERE tenant_id=:tenantId AND workload_id=:workloadId FOR UPDATE",
                params, (rs, rowNum) -> new ProjectionFact(rs.getLong("id"),
                        rs.getLong("site_id"), rs.getString("site_code"),
                        rs.getLong("node_id"), rs.getLong("product_id"),
                        rs.getLong("release_id"), rs.getLong("projection_revision")));
        if (projections.size() > 1) fail("COLLECTOR_PROJECTION_STATE_CORRUPT",
                "workload 投影不唯一");
        ProjectionFact previous = projections.isEmpty() ? null : projections.get(0);
        if (previous != null && (previous.siteId != siteId || previous.nodeId != nodeId
                || previous.productId != productId || !previous.siteCode.equals(siteCode))) {
            fail("COLLECTOR_PROJECTION_IDENTITY_CONFLICT",
                    "workload 站点、节点或产品身份发生漂移");
        }

        int published = jdbc.update("UPDATE public.iot_collector_config_release"
                        + " SET status='PUBLISHED',published_by=:actorId,published_at=CURRENT_TIMESTAMP,"
                        + " updated_by=:actor,updated_at=CURRENT_TIMESTAMP,row_version=row_version+1"
                        + " WHERE tenant_id=:tenantId AND id=:releaseId AND status='VALIDATED'",
                params);
        if (published != 1) fail("COLLECTOR_CONFIG_RELEASE_CONFLICT",
                "VALIDATED 候选发布 CAS 失败");

        if (previous == null) {
            params.addValue("projectionId", nextId());
            int inserted = jdbc.update("INSERT INTO public.collector_workload_binding_projection"
                            + " (id,tenant_id,workload_id,site_id,site_code,node_id,product_id,"
                            + " template_code,template_version,binding_revision,config_version,"
                            + " release_id,projection_revision,lifecycle_status)"
                            + " VALUES (:projectionId,:tenantId,:workloadId,:siteId,:siteCode,:nodeId,"
                            + " :productId,:templateCode,:templateVersion,:bindingRevision,:configVersion,"
                            + " :releaseId,1,'ACTIVE')", params);
            if (inserted != 1) fail("COLLECTOR_CONFIG_RELEASE_CONFLICT",
                    "首次 workload 投影插入失败");
            return;
        }

        params.addValue("projectionId", previous.id)
                .addValue("previousReleaseId", previous.releaseId)
                .addValue("projectionRevision", previous.projectionRevision)
                .addValue("nextProjectionRevision", previous.projectionRevision + 1);
        int advanced = jdbc.update("UPDATE public.collector_workload_binding_projection SET"
                        + " template_code=:templateCode,template_version=:templateVersion,"
                        + " binding_revision=:bindingRevision,config_version=:configVersion,"
                        + " release_id=:releaseId,projection_revision=:nextProjectionRevision,"
                        + " lifecycle_status='ACTIVE',last_synced_at=CURRENT_TIMESTAMP,"
                        + " updated_at=CURRENT_TIMESTAMP"
                        + " WHERE tenant_id=:tenantId AND id=:projectionId"
                        + " AND release_id=:previousReleaseId"
                        + " AND projection_revision=:projectionRevision",
                params);
        if (advanced != 1) fail("COLLECTOR_CONFIG_RELEASE_CONFLICT",
                "workload 投影推进 CAS 失败");
    }

    private static MapSqlParameterSource ids(long tenantId, long productId) {
        return new MapSqlParameterSource("tenantId", tenantId).addValue("productId", productId);
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isTextual()) invalid(field + " 必须是字符串");
        return value.textValue();
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) invalid(field + " 不得为空");
        return value;
    }

    private static long nextId() { return Long.parseLong(SnowflakeIdUtil.nextId()); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static void invalid(String detail) { fail("MODEL_BINDING_APPLY_INVALID", detail); }
    private static void fail(String code, String detail) {
        throw new IllegalArgumentException(code + ": " + detail);
    }

    private static final class ProductFact {
        final long id; final String identification;
        ProductFact(long id, String identification) { this.id = id; this.identification = identification; }
    }

    private static final class TemplateFact {
        final long id; final String code; final String version; final String contentHash;
        TemplateFact(long id, String code, String version, String contentHash) {
            this.id = id; this.code = code; this.version = version; this.contentHash = contentHash;
        }
    }

    private static final class ActiveBinding {
        final long id; final long revision; final String snapshotHash;
        ActiveBinding(long id, long revision, String snapshotHash) {
            this.id = id; this.revision = revision; this.snapshotHash = snapshotHash;
        }
    }

    private static final class ProjectionFact {
        final long id; final long siteId; final String siteCode; final long nodeId;
        final long productId; final long releaseId; final long projectionRevision;
        ProjectionFact(long id, long siteId, String siteCode, long nodeId, long productId,
                       long releaseId, long projectionRevision) {
            this.id = id; this.siteId = siteId; this.siteCode = siteCode; this.nodeId = nodeId;
            this.productId = productId; this.releaseId = releaseId;
            this.projectionRevision = projectionRevision;
        }
    }
}
