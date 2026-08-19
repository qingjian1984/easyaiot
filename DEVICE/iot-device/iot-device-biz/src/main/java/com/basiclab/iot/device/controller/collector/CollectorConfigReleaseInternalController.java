package com.basiclab.iot.device.controller.collector;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.common.security.internal.InternalServiceAuthException;
import com.basiclab.iot.common.security.internal.InternalServiceAuthVerifier;
import com.basiclab.iot.device.CollectorConfigReleaseInternalApi;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseDetailDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedRequestDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedResponseDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleasePendingDTO;
import com.basiclab.iot.device.service.collector.CollectorConfigReleaseInternalAuth;
import com.basiclab.iot.device.service.collector.CollectorConfigReleaseInternalException;
import com.basiclab.iot.device.service.collector.CollectorConfigReleaseInternalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import java.util.List;

/** ADR-018 保护的 collector release provider 内部端点。 */
@Validated
@RestController
@ConditionalOnBean(InternalServiceAuthVerifier.class)
@RequestMapping(CollectorConfigReleaseInternalApi.BASE_PATH)
public class CollectorConfigReleaseInternalController {

    private static final Set<String> OBSERVED_FIELDS = Set.of(
            "releaseId", "tenantId", "nodeId", "workloadId", "configVersion",
            "payloadSha256", "status", "observedAt", "errorCode", "errorDetailSanitized");

    private final CollectorConfigReleaseInternalService service;
    private final CollectorConfigReleaseInternalAuth auth;
    private final ObjectMapper objectMapper;

    public CollectorConfigReleaseInternalController(
            CollectorConfigReleaseInternalService service,
            InternalServiceAuthVerifier verifier,
            ObjectMapper objectMapper) {
        this.service = service;
        this.auth = new CollectorConfigReleaseInternalAuth(verifier);
        this.objectMapper = objectMapper;
    }

    @GetMapping("/pending")
    public R<List<CollectorConfigReleasePendingDTO>> pending(
            @RequestParam("limit") int limit, HttpServletRequest request) {
        auth.verify(request, new byte[0]);
        return R.ok(service.listPending(limit));
    }

    @GetMapping("/{releaseId}")
    public R<CollectorConfigReleaseDetailDTO> detail(
            @PathVariable("releaseId") String releaseId, HttpServletRequest request) {
        auth.verify(request, new byte[0]);
        return R.ok(service.detail(releaseId));
    }

    @PostMapping("/{releaseId}/observed")
    public R<CollectorConfigReleaseObservedResponseDTO> observed(
            @PathVariable("releaseId") String releaseId,
            @RequestBody byte[] rawBody, HttpServletRequest request) {
        auth.verify(request, rawBody);
        return R.ok(service.observe(releaseId, parseObserved(rawBody)));
    }

    private CollectorConfigReleaseObservedRequestDTO parseObserved(byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0) {
            throw new CollectorConfigReleaseInternalException("COLLECTOR_OBSERVED_INVALID");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root == null || !root.isObject()) {
                throw new CollectorConfigReleaseInternalException("COLLECTOR_OBSERVED_INVALID");
            }
            java.util.Iterator<String> fields = root.fieldNames();
            while (fields.hasNext()) {
                if (!OBSERVED_FIELDS.contains(fields.next())) {
                    throw new CollectorConfigReleaseInternalException("COLLECTOR_OBSERVED_FIELD_INVALID");
                }
            }
            for (String field : new String[]{"releaseId", "tenantId", "nodeId", "configVersion"}) {
                JsonNode value = root.get(field);
                if (value == null || !value.isTextual()) {
                    throw new CollectorConfigReleaseInternalException("COLLECTOR_OBSERVED_ID_INVALID");
                }
            }
            return objectMapper.treeToValue(root, CollectorConfigReleaseObservedRequestDTO.class);
        } catch (CollectorConfigReleaseInternalException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CollectorConfigReleaseInternalException("COLLECTOR_OBSERVED_INVALID");
        }
    }

    @ExceptionHandler(InternalServiceAuthException.class)
    public ResponseEntity<R<Void>> authFailure(InternalServiceAuthException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(HttpStatus.UNAUTHORIZED.value(), exception.getCode()));
    }

    @ExceptionHandler(CollectorConfigReleaseInternalException.class)
    public ResponseEntity<R<Void>> contractFailure(CollectorConfigReleaseInternalException exception) {
        int status = "COLLECTOR_RELEASE_NOT_FOUND".equals(exception.getCode())
                ? HttpStatus.NOT_FOUND.value() : HttpStatus.BAD_REQUEST.value();
        return ResponseEntity.status(status).body(R.fail(status, exception.getCode()));
    }
}
