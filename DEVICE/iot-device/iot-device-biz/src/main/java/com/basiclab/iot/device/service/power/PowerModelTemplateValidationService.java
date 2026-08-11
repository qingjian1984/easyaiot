package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateValidationError;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateValidationResponse;
import com.basiclab.iot.device.service.model.JcsCanonicalizer;
import com.basiclab.iot.device.service.model.ModelSemVer;
import com.basiclab.iot.device.service.model.PowerModelTemplateContentValidator;
import com.basiclab.iot.device.service.model.TemplateDiffEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** TD-005 §7.1/§11.1/§12：ACTIVE 草稿的无副作用全量校验与最低 SemVer 增量计算。 */
@Service
@ConditionalOnProperty(prefix = "easyaiot.power-model", name = "template-api-enabled",
        havingValue = "true")
public class PowerModelTemplateValidationService {

    public static final String CAPABILITY_CODE = "power.device.model";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CapabilityService capabilityService;
    private final TenantFrameworkService tenantFrameworkService;
    private final PowerModelTemplateContentValidator contentValidator;
    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();
    private final TemplateDiffEngine diffEngine = new TemplateDiffEngine();

    public PowerModelTemplateValidationService(DataSource dataSource, ObjectMapper mapper,
                                               CapabilityService capabilityService,
                                               TenantFrameworkService tenantFrameworkService,
                                               PowerModelTemplateContentValidator contentValidator) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.capabilityService = Objects.requireNonNull(capabilityService, "capabilityService");
        this.tenantFrameworkService = Objects.requireNonNull(tenantFrameworkService,
                "tenantFrameworkService");
        this.contentValidator = Objects.requireNonNull(contentValidator, "contentValidator");
    }

    @Transactional(readOnly = true)
    public PowerModelTemplateValidationResponse validate(long tenantId, String templateCode,
                                                         long draftId) {
        if (!capabilityService.isEnabled(CAPABILITY_CODE)) {
            throw new ServiceException("CAPABILITY_NOT_SUPPORTED: 当前部署不支持能力 "
                    + CAPABILITY_CODE);
        }
        if (tenantId <= 0 || draftId <= 0 || templateCode == null || templateCode.trim().isEmpty()) {
            throw new IllegalArgumentException("MODEL_TEMPLATE_REQUEST_INVALID: tenant/code/draftId 不合法");
        }
        tenantFrameworkService.validTenant(tenantId);
        DraftFact draft = loadDraft(tenantId, templateCode, draftId);
        JsonNode content = parseAndVerify(draft);
        List<PowerModelTemplateValidationError> errors = new ArrayList<>();
        for (PowerModelTemplateContentValidator.ValidationError error
                : contentValidator.validate(content)) {
            errors.add(new PowerModelTemplateValidationError(error.getCode(), templateCode,
                    draft.version, error.getPath(), "ERROR", error.getMessage()));
        }

        Baseline baseline = comparisonBaseline(tenantId, draft);
        String minimumBump = null;
        if (baseline != null) {
            TemplateDiffEngine.DiffResult diff = diffEngine.diff(baseline.content, content);
            minimumBump = diff.minimumBump().name();
            try {
                ModelSemVer.requireAllowedBump(ModelSemVer.parse(baseline.version),
                        ModelSemVer.parse(draft.version), diff.minimumBump());
            } catch (IllegalArgumentException error) {
                errors.add(new PowerModelTemplateValidationError(
                        "MODEL_TEMPLATE_SEMVER_BUMP_TOO_LOW", templateCode, draft.version,
                        "/version", "ERROR", error.getMessage()));
            }
        }
        errors.sort(Comparator.comparing(PowerModelTemplateValidationError::getPath)
                .thenComparing(PowerModelTemplateValidationError::getCode)
                .thenComparing(PowerModelTemplateValidationError::getMessage));

        PowerModelTemplateValidationResponse response = new PowerModelTemplateValidationResponse();
        response.setDraftId(Long.toString(draftId));
        response.setTemplateCode(templateCode);
        response.setTemplateVersion(draft.version);
        response.setContentHash(draft.contentHash);
        response.setComparisonVersion(baseline == null ? null : baseline.version);
        response.setMinimumBump(minimumBump);
        response.setErrors(errors);
        response.setValid(errors.isEmpty());
        return response;
    }

    private DraftFact loadDraft(long tenantId, String templateCode, long draftId) {
        List<DraftFact> rows = jdbc.query("SELECT v.version,v.content_canonical,v.content_hash"
                        + " FROM public.power_model_template t"
                        + " JOIN public.power_model_template_version v"
                        + " ON v.tenant_id=t.tenant_id AND v.template_id=t.id"
                        + " WHERE t.tenant_id=:tenantId AND t.template_code=:templateCode"
                        + " AND t.status='ACTIVE' AND v.id=:draftId AND v.lifecycle='DRAFT'"
                        + " AND v.draft_state='ACTIVE'",
                new MapSqlParameterSource("tenantId", tenantId)
                        .addValue("templateCode", templateCode).addValue("draftId", draftId),
                (rs, rowNum) -> new DraftFact(draftId, rs.getString("version"),
                        rs.getString("content_canonical"), rs.getString("content_hash")));
        if (rows.size() != 1) {
            fail("MODEL_TEMPLATE_DRAFT_NOT_FOUND", "当前租户 ACTIVE 草稿不存在");
        }
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

    private Baseline comparisonBaseline(long tenantId, DraftFact draft) {
        List<Baseline> ownPublished = jdbc.query("SELECT version,content_canonical"
                        + " FROM public.power_model_template_version"
                        + " WHERE tenant_id=:tenantId AND template_id=(SELECT template_id"
                        + " FROM public.power_model_template_version WHERE tenant_id=:tenantId AND id=:draftId)"
                        + " AND lifecycle='PUBLISHED'",
                new MapSqlParameterSource("tenantId", tenantId).addValue("draftId", draft.id),
                (rs, rowNum) -> baseline(rs.getString("version"),
                        rs.getString("content_canonical")));
        ModelSemVer target = ModelSemVer.parse(draft.version);
        Baseline nearest = ownPublished.stream()
                .filter(value -> ModelSemVer.parse(value.version).compareTo(target) < 0)
                .max(Comparator.comparing(value -> ModelSemVer.parse(value.version)))
                .orElse(null);
        return nearest;
    }

    private Baseline baseline(String version, String canonical) {
        try {
            return new Baseline(version, mapper.readTree(canonical));
        } catch (JsonProcessingException error) {
            fail("MODEL_TEMPLATE_STORED_CONTENT_INVALID", "比较基线 canonical 不是合法 JSON");
            return null;
        }
    }

    private static void fail(String code, String detail) {
        throw new IllegalArgumentException(code + ": " + detail);
    }

    private static final class DraftFact {
        final long id;
        final String version;
        final String canonical;
        final String contentHash;

        DraftFact(long id, String version, String canonical, String contentHash) {
            this.id = id;
            this.version = version;
            this.canonical = canonical;
            this.contentHash = contentHash;
        }
    }

    private static final class Baseline {
        final String version;
        final JsonNode content;

        Baseline(String version, JsonNode content) {
            this.version = version;
            this.content = content;
        }
    }
}
