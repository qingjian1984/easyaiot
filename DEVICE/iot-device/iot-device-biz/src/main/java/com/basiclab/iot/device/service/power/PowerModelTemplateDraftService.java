package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.common.utils.SnowflakeIdUtil;
import com.basiclab.iot.device.config.PowerModelIdempotencySecretProvider;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateDraftResponse;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateDraftWriteRequest;
import com.basiclab.iot.device.service.idempotency.IdempotencyArbiter;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.basiclab.iot.device.service.model.JcsCanonicalizer;
import com.basiclab.iot.device.service.model.ModelSemVer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TD-005 §7/§11.1/§17：租户模板草稿创建与 If-Match 完整替换。 */
@Service
public class PowerModelTemplateDraftService {

    public static final String CAPABILITY_CODE = "power.device.model";
    public static final String IDEMPOTENCY_OPERATION = "DRAFT_UPDATE";
    private static final Pattern ETAG = Pattern.compile("^\\\"(0|[1-9][0-9]*)\\\"$");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CapabilityService capabilityService;
    private final TenantFrameworkService tenantFrameworkService;
    private final JdbcPowerIdempotencyStore idempotencyStore;
    private final byte[] idempotencySecret;
    private final int maxCanonicalBytes;
    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();

    public PowerModelTemplateDraftService(DataSource dataSource, ObjectMapper mapper,
                                          CapabilityService capabilityService,
                                          TenantFrameworkService tenantFrameworkService,
                                          JdbcPowerIdempotencyStore idempotencyStore,
                                          PowerModelIdempotencySecretProvider secretProvider,
                                          @Value("${easyaiot.power-model.max-template-canonical-bytes:1048576}")
                                          int maxCanonicalBytes) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.capabilityService = Objects.requireNonNull(capabilityService, "capabilityService");
        this.tenantFrameworkService = Objects.requireNonNull(tenantFrameworkService,
                "tenantFrameworkService");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.idempotencySecret = Objects.requireNonNull(secretProvider, "secretProvider")
                .getSecret();
        if (maxCanonicalBytes <= 0) throw new IllegalArgumentException("maxCanonicalBytes 必须为正数");
        this.maxCanonicalBytes = maxCanonicalBytes;
    }

    @Transactional(rollbackFor = Exception.class)
    public PowerModelTemplateDraftResponse create(long tenantId, String templateCode,
                                                  PowerModelTemplateDraftWriteRequest request,
                                                  long actorId, String idempotencyKey) {
        requireCapability();
        PreparedContent prepared = prepare(tenantId, templateCode, request, actorId,
                idempotencyKey);
        tenantFrameworkService.validTenant(tenantId);
        String path = "/api/v1/power/model-templates/" + templateCode + "/drafts";
        JdbcPowerIdempotencyStore.Scope scope = scope(tenantId, actorId, idempotencyKey);
        JdbcPowerIdempotencyStore.Claim claim = idempotencyStore.claim(scope,
                requestHash("POST", path, prepared.canonical));
        if (claim.outcome() == JdbcPowerIdempotencyStore.Claim.Outcome.REPLAY) {
            return replay(claim, 201);
        }

        TemplateFact template = lockTemplate(tenantId, templateCode);
        requireContentIdentity(template, prepared);
        BaseFact base = resolveBase(tenantId, template, prepared.content);
        requireNoConflict(tenantId, template.id, 0, prepared.version, prepared.contentHash);
        long draftId = Long.parseLong(SnowflakeIdUtil.nextId());
        MapSqlParameterSource params = writeParams(tenantId, template, draftId, prepared, base,
                actorId).addValue("draftRevision", 0L);
        int inserted = jdbc.update("INSERT INTO public.power_model_template_version"
                        + " (id,tenant_id,template_id,version,major,minor,patch,prerelease,lifecycle,"
                        + " base_template_version_id,base_version,base_content_hash,schema_version,"
                        + " canonicalization_version,hash_algorithm,content_canonical,content_json,"
                        + " content_hash,source_type,diff_summary,draft_revision,draft_state,"
                        + " last_activity_at,expires_at,created_by,updated_by) VALUES"
                        + " (:draftId,:tenantId,:templateId,:version,:major,:minor,:patch,:prerelease,'DRAFT',"
                        + " :baseId,:baseVersion,:baseHash,'1.0.0','jcs-rfc8785-v1','SHA-256',"
                        + " :canonical,CAST(:content AS jsonb),:contentHash,'UI','{}'::jsonb,"
                        + " :draftRevision,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '90 days',"
                        + " :actor,:actor)", params);
        if (inserted != 1) fail("MODEL_TEMPLATE_DRAFT_WRITE_FAILED", "草稿创建失败");
        PowerModelTemplateDraftResponse response = response(draftId, templateCode, prepared, 0);
        complete(scope, 201, response);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public PowerModelTemplateDraftResponse replace(long tenantId, String templateCode,
                                                   long draftId,
                                                   PowerModelTemplateDraftWriteRequest request,
                                                   long actorId, String idempotencyKey,
                                                   String ifMatch) {
        requireCapability();
        PreparedContent prepared = prepare(tenantId, templateCode, request, actorId,
                idempotencyKey);
        nonBlank(ifMatch, "If-Match"); // 只检查存在；首次执行才解析 revision。
        tenantFrameworkService.validTenant(tenantId);
        String path = "/api/v1/power/model-templates/" + templateCode + "/drafts/" + draftId;
        JdbcPowerIdempotencyStore.Scope scope = scope(tenantId, actorId, idempotencyKey);
        JdbcPowerIdempotencyStore.Claim claim = idempotencyStore.claim(scope,
                requestHash("PUT", path, prepared.canonical));
        if (claim.outcome() == JdbcPowerIdempotencyStore.Claim.Outcome.REPLAY) {
            return replay(claim, 200);
        }

        long expectedRevision = parseEtag(ifMatch);
        TemplateFact template = lockTemplate(tenantId, templateCode);
        requireContentIdentity(template, prepared);
        DraftFact draft = lockDraft(tenantId, template.id, draftId);
        if (draft.revision != expectedRevision) {
            fail("MODEL_PRECONDITION_FAILED", "If-Match 与当前 draftRevision 不一致");
        }
        BaseFact base = resolveBase(tenantId, template, prepared.content);
        requireNoConflict(tenantId, template.id, draftId, prepared.version, prepared.contentHash);
        long nextRevision = expectedRevision + 1;
        MapSqlParameterSource params = writeParams(tenantId, template, draftId, prepared, base,
                actorId).addValue("expectedRevision", expectedRevision)
                .addValue("nextRevision", nextRevision);
        int updated = jdbc.update("UPDATE public.power_model_template_version SET"
                        + " version=:version,major=:major,minor=:minor,patch=:patch,prerelease=:prerelease,"
                        + " base_template_version_id=:baseId,base_version=:baseVersion,"
                        + " base_content_hash=:baseHash,schema_version='1.0.0',"
                        + " canonicalization_version='jcs-rfc8785-v1',hash_algorithm='SHA-256',"
                        + " content_canonical=:canonical,content_json=CAST(:content AS jsonb),"
                        + " content_hash=:contentHash,source_type='UI',source_artifact_id=NULL,"
                        + " diff_summary='{}'::jsonb,draft_revision=:nextRevision,"
                        + " last_activity_at=CURRENT_TIMESTAMP,expires_at=CURRENT_TIMESTAMP + INTERVAL '90 days',"
                        + " updated_by=:actor,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE tenant_id=:tenantId AND template_id=:templateId AND id=:draftId"
                        + " AND lifecycle='DRAFT' AND draft_state='ACTIVE'"
                        + " AND draft_revision=:expectedRevision", params);
        if (updated != 1) fail("MODEL_PRECONDITION_FAILED", "草稿 CAS 更新失败");
        PowerModelTemplateDraftResponse response = response(draftId, templateCode, prepared,
                nextRevision);
        complete(scope, 200, response);
        return response;
    }

    private PreparedContent prepare(long tenantId, String templateCode,
                                    PowerModelTemplateDraftWriteRequest request, long actorId,
                                    String idempotencyKey) {
        if (tenantId <= 0 || actorId <= 0) invalid("tenant/actor 必须是正整数");
        nonBlank(templateCode, "templateCode");
        Objects.requireNonNull(request, "request");
        nonBlank(idempotencyKey, "Idempotency-Key");
        if (idempotencyKey.length() > 256) invalid("Idempotency-Key 超过 256 字符");
        if (idempotencySecret.length < 32) {
            fail("IDEMPOTENCY_SECRET_UNAVAILABLE", "幂等 HMAC secret 未配置或少于 32 UTF-8 字节");
        }
        JsonNode content = request.getContent();
        if (content == null || !content.isObject()) invalid("content 必须是对象");
        String schemaVersion = text(content, "schemaVersion");
        if (!"1.0.0".equals(schemaVersion)) invalid("M1 只接受 schemaVersion=1.0.0");
        String contentCode = text(content, "templateCode");
        if (!templateCode.equals(contentCode)) invalid("content.templateCode 与路径不一致");
        String rawVersion = text(content, "version");
        ModelSemVer semVer = ModelSemVer.parse(rawVersion);
        if (!rawVersion.equals(semVer.toString())) invalid("version 禁止 build metadata 或非规范形式");
        String canonical = canonicalizer.canonicalize(content);
        if (canonical.getBytes(StandardCharsets.UTF_8).length > maxCanonicalBytes) {
            invalid("canonical 内容超过 maxTemplateCanonicalBytes");
        }
        return new PreparedContent(content, rawVersion, semVer, text(content, "templateKind"),
                text(content, "deviceType"), canonical, canonicalizer.contentHash(content));
    }

    private TemplateFact lockTemplate(long tenantId, String code) {
        List<TemplateFact> rows = jdbc.query("SELECT id,template_kind,device_type,status"
                        + " FROM public.power_model_template WHERE tenant_id=:tenantId"
                        + " AND template_code=:code FOR UPDATE",
                new MapSqlParameterSource("tenantId", tenantId).addValue("code", code),
                (rs, rowNum) -> new TemplateFact(rs.getLong("id"),
                        rs.getString("template_kind"), rs.getString("device_type"),
                        rs.getString("status")));
        if (rows.size() != 1) fail("MODEL_TEMPLATE_NOT_FOUND", "当前租户模板不存在");
        if (!"ACTIVE".equals(rows.get(0).status)) {
            fail("MODEL_PRECONDITION_FAILED", "模板身份不是 ACTIVE");
        }
        return rows.get(0);
    }

    private DraftFact lockDraft(long tenantId, long templateId, long draftId) {
        List<DraftFact> rows = jdbc.query("SELECT draft_revision FROM public.power_model_template_version"
                        + " WHERE tenant_id=:tenantId AND template_id=:templateId AND id=:draftId"
                        + " AND lifecycle='DRAFT' AND draft_state='ACTIVE' FOR UPDATE",
                new MapSqlParameterSource("tenantId", tenantId).addValue("templateId", templateId)
                        .addValue("draftId", draftId),
                (rs, rowNum) -> new DraftFact(rs.getLong("draft_revision")));
        if (rows.size() != 1) fail("MODEL_TEMPLATE_DRAFT_NOT_FOUND", "ACTIVE 草稿不存在");
        return rows.get(0);
    }

    private static void requireContentIdentity(TemplateFact template, PreparedContent prepared) {
        if (!template.kind.equals(prepared.kind)) invalid("content.templateKind 与模板身份不一致");
        if (!template.deviceType.equals(prepared.deviceType)) invalid("content.deviceType 与模板身份不一致");
    }

    private BaseFact resolveBase(long tenantId, TemplateFact template, JsonNode content) {
        JsonNode base = content.get("base");
        if ("STANDARD".equals(template.kind)) {
            if (base != null) invalid("STANDARD 模板禁止 base");
            return BaseFact.none();
        }
        if (!"VENDOR".equals(template.kind) || base == null || !base.isObject()) {
            invalid("VENDOR 模板必须提供 base");
        }
        String code = text(base, "templateCode");
        String version = text(base, "version");
        String hash = text(base, "contentHash");
        List<BaseFact> rows = jdbc.query("SELECT v.id,v.version,v.content_hash"
                        + " FROM public.power_model_template t JOIN public.power_model_template_version v"
                        + " ON v.tenant_id=t.tenant_id AND v.template_id=t.id"
                        + " WHERE t.tenant_id IN (0,:tenantId) AND t.template_code=:code"
                        + " AND t.template_kind='STANDARD' AND t.status='ACTIVE'"
                        + " AND v.version=:version AND v.content_hash=:hash AND v.lifecycle='PUBLISHED'",
                new MapSqlParameterSource("tenantId", tenantId).addValue("code", code)
                        .addValue("version", version).addValue("hash", hash),
                (rs, rowNum) -> new BaseFact(rs.getLong("id"), rs.getString("version"),
                        rs.getString("content_hash")));
        if (rows.size() != 1) {
            fail("MODEL_TEMPLATE_BASE_NOT_FOUND", "厂家基线不存在、不唯一或未发布");
        }
        return rows.get(0);
    }

    private void requireNoConflict(long tenantId, long templateId, long currentDraftId,
                                   String version, String contentHash) {
        Integer versionCount = jdbc.queryForObject("SELECT count(*)"
                        + " FROM public.power_model_template_version WHERE tenant_id=:tenantId"
                        + " AND template_id=:templateId AND version=:version AND id<>:currentDraftId",
                conflictParams(tenantId, templateId, currentDraftId, version, contentHash),
                Integer.class);
        if (versionCount != null && versionCount > 0) {
            fail("MODEL_TEMPLATE_VERSION_CONFLICT", "模板版本已存在");
        }
        Integer hashCount = jdbc.queryForObject("SELECT count(*)"
                        + " FROM public.power_model_template_version WHERE tenant_id=:tenantId"
                        + " AND template_id=:templateId AND content_hash=:contentHash"
                        + " AND id<>:currentDraftId",
                conflictParams(tenantId, templateId, currentDraftId, version, contentHash),
                Integer.class);
        if (hashCount != null && hashCount > 0) {
            fail("MODEL_TEMPLATE_CONTENT_DUPLICATE", "模板内容哈希已存在");
        }
    }

    private static MapSqlParameterSource conflictParams(long tenantId, long templateId,
                                                        long currentDraftId, String version,
                                                        String contentHash) {
        return new MapSqlParameterSource("tenantId", tenantId).addValue("templateId", templateId)
                .addValue("currentDraftId", currentDraftId).addValue("version", version)
                .addValue("contentHash", contentHash);
    }

    private MapSqlParameterSource writeParams(long tenantId, TemplateFact template, long draftId,
                                              PreparedContent prepared, BaseFact base,
                                              long actorId) {
        return new MapSqlParameterSource("draftId", draftId).addValue("tenantId", tenantId)
                .addValue("templateId", template.id).addValue("version", prepared.version)
                .addValue("major", prepared.semVer.major()).addValue("minor", prepared.semVer.minor())
                .addValue("patch", prepared.semVer.patch())
                .addValue("prerelease", prepared.semVer.isPrerelease()
                        ? prepared.semVer.prerelease() : null)
                .addValue("baseId", base.id).addValue("baseVersion", base.version)
                .addValue("baseHash", base.contentHash).addValue("canonical", prepared.canonical)
                .addValue("content", prepared.canonical).addValue("contentHash", prepared.contentHash)
                .addValue("actor", Long.toString(actorId));
    }

    private JdbcPowerIdempotencyStore.Scope scope(long tenantId, long actorId, String key) {
        return new JdbcPowerIdempotencyStore.Scope(tenantId, "USER", Long.toString(actorId),
                IDEMPOTENCY_OPERATION, IdempotencyArbiter.keyHash(idempotencySecret, key));
    }

    private byte[] requestHash(String method, String path, String canonicalContent) {
        ObjectNode payload = mapper.createObjectNode();
        try {
            payload.set("content", mapper.readTree(canonicalContent));
        } catch (Exception error) {
            throw new IllegalStateException("MODEL_TEMPLATE_CANONICAL_INVALID", error);
        }
        return IdempotencyArbiter.requestHash(method, path, canonicalizer.canonicalize(payload));
    }

    private void complete(JdbcPowerIdempotencyStore.Scope scope, int status,
                          PowerModelTemplateDraftResponse response) {
        idempotencyStore.completeSuccess(scope, status,
                canonicalizer.canonicalize(mapper.valueToTree(response)), response.getDraftId());
    }

    private PowerModelTemplateDraftResponse replay(JdbcPowerIdempotencyStore.Claim claim,
                                                   int expectedStatus) {
        if (!"SUCCEEDED".equals(claim.state()) || claim.httpStatus() == null
                || claim.httpStatus().intValue() != expectedStatus || claim.responsePayload() == null) {
            fail("IDEMPOTENCY_RESPONSE_INVALID", "已存终态不是可重放的草稿响应");
        }
        try {
            JsonNode value = mapper.readTree(claim.responsePayload());
            return new PowerModelTemplateDraftResponse(text(value, "draftId"),
                    text(value, "templateCode"), text(value, "version"),
                    text(value, "lifecycle"), Long.parseLong(text(value, "draftRevision")),
                    text(value, "contentHash"));
        } catch (Exception error) {
            throw new IllegalStateException("IDEMPOTENCY_RESPONSE_INVALID: 已存响应无法重放", error);
        }
    }

    private static PowerModelTemplateDraftResponse response(long draftId, String templateCode,
                                                            PreparedContent prepared,
                                                            long revision) {
        return new PowerModelTemplateDraftResponse(Long.toString(draftId), templateCode,
                prepared.version, "DRAFT", revision, prepared.contentHash);
    }

    private static long parseEtag(String ifMatch) {
        Matcher matcher = ETAG.matcher(ifMatch);
        if (!matcher.matches()) fail("MODEL_PRECONDITION_FAILED", "If-Match 必须是引号包裹的 revision");
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("MODEL_PRECONDITION_FAILED: If-Match 超出范围", error);
        }
    }

    private void requireCapability() {
        if (!capabilityService.isEnabled(CAPABILITY_CODE)) {
            throw new ServiceException("CAPABILITY_NOT_SUPPORTED: 当前部署不支持能力 " + CAPABILITY_CODE);
        }
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
    private static void invalid(String detail) { fail("MODEL_TEMPLATE_DRAFT_INVALID", detail); }
    private static void fail(String code, String detail) {
        throw new IllegalArgumentException(code + ": " + detail);
    }

    private static final class TemplateFact {
        final long id; final String kind; final String deviceType; final String status;
        TemplateFact(long id, String kind, String deviceType, String status) {
            this.id = id; this.kind = kind; this.deviceType = deviceType; this.status = status;
        }
    }
    private static final class DraftFact {
        final long revision;
        DraftFact(long revision) { this.revision = revision; }
    }
    private static final class BaseFact {
        final Long id; final String version; final String contentHash;
        BaseFact(Long id, String version, String contentHash) {
            this.id = id; this.version = version; this.contentHash = contentHash;
        }
        static BaseFact none() { return new BaseFact(null, null, null); }
    }
    private static final class PreparedContent {
        final JsonNode content; final String version; final ModelSemVer semVer;
        final String kind; final String deviceType; final String canonical; final String contentHash;
        PreparedContent(JsonNode content, String version, ModelSemVer semVer, String kind,
                        String deviceType, String canonical, String contentHash) {
            this.content = content; this.version = version; this.semVer = semVer; this.kind = kind;
            this.deviceType = deviceType; this.canonical = canonical; this.contentHash = contentHash;
        }
    }
}
