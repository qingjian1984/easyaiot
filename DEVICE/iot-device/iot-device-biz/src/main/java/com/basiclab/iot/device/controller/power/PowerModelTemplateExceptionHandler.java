package com.basiclab.iot.device.controller.power;

import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** 仅作用于模板 Controller，避免改变平台其他 API 的既有错误合同。 */
@RestControllerAdvice(assignableTypes = PowerModelTemplateController.class)
@ConditionalOnProperty(prefix = "easyaiot.power-model",
        name = "template-api-enabled", havingValue = "true")
public class PowerModelTemplateExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PowerModelTemplateExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, ServiceException.class})
    public ResponseEntity<PowerModelTemplateErrorResponse> business(
            RuntimeException error, HttpServletRequest request) {
        ErrorFact fact = parse(error.getMessage());
        return response(status(fact.code), fact, request);
    }

    @ExceptionHandler({MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class,
            BindException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<PowerModelTemplateErrorResponse> malformed(
            Exception error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                new ErrorFact("MODEL_TEMPLATE_REQUEST_INVALID", "请求参数或 Header 不合法"), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PowerModelTemplateErrorResponse> unexpected(
            Exception error, HttpServletRequest request) {
        LOG.error("Unexpected power model template API failure", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorFact("MODEL_TEMPLATE_INTERNAL_ERROR", "物模型模板服务内部错误"), request);
    }

    private static ResponseEntity<PowerModelTemplateErrorResponse> response(
            HttpStatus status, ErrorFact fact, HttpServletRequest request) {
        String traceId = validTraceId(firstNonBlank(MDC.get("traceId"),
                request.getHeader("X-Trace-Id")));
        if (traceId == null) traceId = UUID.randomUUID().toString();
        PowerModelTemplateErrorResponse body = new PowerModelTemplateErrorResponse(
                fact.code, fact.message, traceId,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()),
                retryable(fact.code));
        return ResponseEntity.status(status).body(body);
    }

    private static ErrorFact parse(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new ErrorFact("MODEL_TEMPLATE_REQUEST_INVALID", "请求不合法");
        }
        int separator = message.indexOf(':');
        if (separator <= 0) {
            return new ErrorFact("MODEL_TEMPLATE_REQUEST_INVALID", message);
        }
        String code = message.substring(0, separator).trim();
        String detail = message.substring(separator + 1).trim();
        return new ErrorFact(code, detail.isEmpty() ? code : detail);
    }

    private static HttpStatus status(String code) {
        if ("MODEL_PRECONDITION_FAILED".equals(code)) return HttpStatus.PRECONDITION_FAILED;
        if ("MODEL_AUTH_REQUIRED".equals(code)) return HttpStatus.UNAUTHORIZED;
        if (code.endsWith("_NOT_FOUND")) return HttpStatus.NOT_FOUND;
        if ("CAPABILITY_NOT_SUPPORTED".equals(code)) return HttpStatus.NOT_IMPLEMENTED;
        if ("IDEMPOTENCY_KEY_REUSED".equals(code)
                || "IDEMPOTENCY_IN_PROGRESS".equals(code)
                || "MODEL_TEMPLATE_PUBLISH_LOCK_TIMEOUT".equals(code)
                || code.endsWith("_CONFLICT") || code.endsWith("_ALREADY_EXISTS")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static boolean retryable(String code) {
        return "IDEMPOTENCY_IN_PROGRESS".equals(code)
                || "MODEL_TEMPLATE_PUBLISH_LOCK_TIMEOUT".equals(code);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first;
        if (second != null && !second.trim().isEmpty()) return second;
        return null;
    }

    private static String validTraceId(String value) {
        if (value == null || value.length() > 128 || !value.matches("[A-Za-z0-9._:-]+")) {
            return null;
        }
        return value;
    }

    private static final class ErrorFact {
        final String code;
        final String message;

        ErrorFact(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
