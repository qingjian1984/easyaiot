package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * TD-005 §11.3/§12：对发布内容执行冻结的 Draft 2020-12 Schema 与领域语义校验。
 * Schema 只能包含本地 fragment 引用，禁止远程或自定义 resolver。
 */
public final class PowerModelTemplateContentValidator {

    private static final int MAX_ERRORS = 1000;

    private final JsonSchema schema;
    private final TemplateMemberValidator memberValidator = new TemplateMemberValidator();
    private final TemplateHighRiskValidator highRiskValidator = new TemplateHighRiskValidator();

    public PowerModelTemplateContentValidator(JsonNode trustedSchema) {
        Objects.requireNonNull(trustedSchema, "trustedSchema");
        requireLocalReferencesOnly(trustedSchema, "#");
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(trustedSchema);
    }

    /** 返回当前可判定的全部错误；顺序稳定，便于 API、审计和契约测试复用。 */
    public List<ValidationError> validate(JsonNode content) {
        List<ValidationError> errors = new ArrayList<>();
        if (content == null) {
            errors.add(new ValidationError("MODEL_TEMPLATE_SCHEMA_INVALID", "$", "模板内容不得为空"));
            return errors;
        }
        Set<ValidationMessage> schemaErrors = schema.validate(content);
        schemaErrors.stream()
                .sorted(Comparator.comparing(ValidationMessage::getPath)
                        .thenComparing(ValidationMessage::getMessage))
                .limit(MAX_ERRORS)
                .forEach(error -> errors.add(new ValidationError(
                        "MODEL_TEMPLATE_SCHEMA_INVALID", error.getPath(), error.getMessage())));
        collectSemanticError(errors, () -> memberValidator.requireUniqueMemberCodes(content));
        collectSemanticError(errors, () -> highRiskValidator.requireCompleteHighRiskPolicy(content));
        return errors;
    }

    public void requirePublishable(JsonNode content) {
        List<ValidationError> errors = validate(content);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("MODEL_TEMPLATE_SCHEMA_INVALID: "
                    + errors.size() + " 个校验错误；首个错误 " + errors.get(0));
        }
    }

    private static void collectSemanticError(List<ValidationError> errors, Runnable validation) {
        try {
            validation.run();
        } catch (IllegalArgumentException error) {
            String message = error.getMessage() == null ? "领域语义校验失败" : error.getMessage();
            int separator = message.indexOf(':');
            String code = separator > 0 ? message.substring(0, separator) : "MODEL_TEMPLATE_SCHEMA_INVALID";
            errors.add(new ValidationError(code, "$", message));
        }
    }

    private static void requireLocalReferencesOnly(JsonNode node, String path) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = path + "/" + field.getKey();
                if ("$ref".equals(field.getKey())) {
                    if (!field.getValue().isTextual()
                            || !field.getValue().textValue().startsWith("#")) {
                        throw new IllegalArgumentException(
                                "MODEL_TEMPLATE_SCHEMA_UNTRUSTED_REF: 禁止外部 Schema 引用 " + childPath);
                    }
                }
                requireLocalReferencesOnly(field.getValue(), childPath);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                requireLocalReferencesOnly(node.get(i), path + "/" + i);
            }
        }
    }

    public static final class ValidationError {
        private final String code;
        private final String path;
        private final String message;

        public ValidationError(String code, String path, String message) {
            this.code = code;
            this.path = path;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getPath() { return path; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return code + "@" + path + ": " + message;
        }
    }
}
