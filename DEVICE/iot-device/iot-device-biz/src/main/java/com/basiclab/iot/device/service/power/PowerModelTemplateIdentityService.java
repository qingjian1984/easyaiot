package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.common.utils.SnowflakeIdUtil;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateCreateRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateCreateResponse;
import com.basiclab.iot.device.service.idempotency.IdempotencyArbiter;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.basiclab.iot.device.service.model.JcsCanonicalizer;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** TD-005 §11.1/§17：当前租户模板 identity 的幂等原子创建服务。 */
@Service
public class PowerModelTemplateIdentityService {

    public static final String CAPABILITY_CODE = "power.device.model";
    public static final String IDEMPOTENCY_OPERATION = "POWER_MODEL_TEMPLATE_CREATE";
    private static final Pattern CODE = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$");
    private static final Set<String> DEVICE_TYPES = new HashSet<String>(Arrays.asList(
            "HIGH_VOLTAGE_CABINET", "PROTECTION_DEVICE", "TRANSFORMER", "DC_PANEL",
            "LOW_VOLTAGE_CABINET", "CAPACITOR_CABINET", "METER", "CURRENT_TRANSFORMER",
            "VOLTAGE_TRANSFORMER", "ENVIRONMENT_MONITOR"));
    private static final Set<String> TEMPLATE_KINDS =
            new HashSet<String>(Arrays.asList("STANDARD", "VENDOR"));

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CapabilityService capabilityService;
    private final TenantFrameworkService tenantFrameworkService;
    private final JdbcPowerIdempotencyStore idempotencyStore;
    private final byte[] idempotencySecret;
    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();

    public PowerModelTemplateIdentityService(DataSource dataSource, ObjectMapper mapper,
                                             CapabilityService capabilityService,
                                             TenantFrameworkService tenantFrameworkService,
                                             JdbcPowerIdempotencyStore idempotencyStore,
                                             @Value("${easyaiot.power-model.idempotency-hmac-secret:}")
                                             String idempotencySecret) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.capabilityService = Objects.requireNonNull(capabilityService, "capabilityService");
        this.tenantFrameworkService = Objects.requireNonNull(tenantFrameworkService,
                "tenantFrameworkService");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.idempotencySecret = Objects.requireNonNull(idempotencySecret, "idempotencySecret")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(rollbackFor = Exception.class)
    public PowerModelTemplateCreateResponse create(long tenantId,
                                                    PowerModelTemplateCreateRequest request,
                                                    long actorId, String idempotencyKey) {
        requireCapability();
        validate(tenantId, request, actorId, idempotencyKey);
        tenantFrameworkService.validTenant(tenantId);
        if (idempotencySecret.length < 32) {
            fail("IDEMPOTENCY_SECRET_UNAVAILABLE", "幂等 HMAC secret 未配置或少于 32 UTF-8 字节");
        }

        byte[] keyHash = IdempotencyArbiter.keyHash(idempotencySecret, idempotencyKey);
        JdbcPowerIdempotencyStore.Scope scope = new JdbcPowerIdempotencyStore.Scope(
                tenantId, "USER", Long.toString(actorId), IDEMPOTENCY_OPERATION, keyHash);
        JdbcPowerIdempotencyStore.Claim claim = idempotencyStore.claim(scope, requestHash(request));
        if (claim.outcome() == JdbcPowerIdempotencyStore.Claim.Outcome.REPLAY) {
            return replay(claim);
        }

        long templateId = Long.parseLong(SnowflakeIdUtil.nextId());
        int inserted = jdbc.update("INSERT INTO public.power_model_template"
                        + " (id,tenant_id,template_code,template_name,device_type,template_kind,"
                        + " owner_scope,status,row_version,created_by,updated_by)"
                        + " VALUES (:id,:tenantId,:code,:name,:deviceType,:kind,'TENANT','ACTIVE',0,"
                        + " :actor,:actor) ON CONFLICT (tenant_id,template_code) DO NOTHING",
                new MapSqlParameterSource("id", templateId).addValue("tenantId", tenantId)
                        .addValue("code", request.getTemplateCode())
                        .addValue("name", request.getTemplateName())
                        .addValue("deviceType", request.getDeviceType())
                        .addValue("kind", request.getTemplateKind())
                        .addValue("actor", Long.toString(actorId)));
        if (inserted != 1) {
            fail("MODEL_TEMPLATE_ALREADY_EXISTS", "当前租户模板编码已存在");
        }

        PowerModelTemplateCreateResponse response = new PowerModelTemplateCreateResponse(
                Long.toString(templateId), request.getTemplateCode(), "TENANT", "ACTIVE", 0);
        idempotencyStore.completeSuccess(scope, 201,
                canonicalizer.canonicalize(mapper.valueToTree(response)), Long.toString(templateId));
        return response;
    }

    private byte[] requestHash(PowerModelTemplateCreateRequest request) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("templateCode", request.getTemplateCode());
        payload.put("templateName", request.getTemplateName());
        payload.put("deviceType", request.getDeviceType());
        payload.put("templateKind", request.getTemplateKind());
        return IdempotencyArbiter.requestHash("POST", "/api/v1/power/model-templates",
                canonicalizer.canonicalize(payload));
    }

    private PowerModelTemplateCreateResponse replay(JdbcPowerIdempotencyStore.Claim claim) {
        if (!"SUCCEEDED".equals(claim.state()) || claim.httpStatus() == null
                || claim.httpStatus().intValue() != 201 || claim.responsePayload() == null) {
            fail("IDEMPOTENCY_RESPONSE_INVALID", "已存终态不是可重放的模板创建响应");
        }
        try {
            JsonNode value = mapper.readTree(claim.responsePayload());
            return new PowerModelTemplateCreateResponse(text(value, "templateId"),
                    text(value, "templateCode"), text(value, "ownerScope"),
                    text(value, "status"), Long.parseLong(text(value, "rowVersion")));
        } catch (Exception error) {
            throw new IllegalStateException("IDEMPOTENCY_RESPONSE_INVALID: 已存响应无法重放", error);
        }
    }

    private void requireCapability() {
        if (!capabilityService.isEnabled(CAPABILITY_CODE)) {
            throw new ServiceException("CAPABILITY_NOT_SUPPORTED: 当前部署不支持能力 "
                    + CAPABILITY_CODE);
        }
    }

    private static void validate(long tenantId, PowerModelTemplateCreateRequest request,
                                 long actorId, String idempotencyKey) {
        if (tenantId <= 0 || actorId <= 0) invalid("tenant/actor 必须是正整数");
        Objects.requireNonNull(request, "request");
        String code = nonBlank(request.getTemplateCode(), "templateCode");
        if (!CODE.matcher(code).matches()) invalid("templateCode 格式非法");
        String name = nonBlank(request.getTemplateName(), "templateName");
        if (name.length() > 100) invalid("templateName 超过 100 字符");
        if (!DEVICE_TYPES.contains(request.getDeviceType())) invalid("deviceType 非法");
        if (!TEMPLATE_KINDS.contains(request.getTemplateKind())) invalid("templateKind 非法");
        String key = nonBlank(idempotencyKey, "Idempotency-Key");
        if (key.length() > 256) invalid("Idempotency-Key 超过 256 字符");
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

    private static void invalid(String detail) { fail("MODEL_TEMPLATE_CREATE_INVALID", detail); }
    private static void fail(String code, String detail) {
        throw new IllegalArgumentException(code + ": " + detail);
    }
}
