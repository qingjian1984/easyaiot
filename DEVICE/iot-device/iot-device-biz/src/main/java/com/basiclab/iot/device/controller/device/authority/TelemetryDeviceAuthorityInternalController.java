package com.basiclab.iot.device.controller.device.authority;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.common.security.internal.InternalServiceAuthException;
import com.basiclab.iot.common.security.internal.InternalServiceAuthVerifier;
import com.basiclab.iot.device.TelemetryDeviceAuthorityInternalApi;
import com.basiclab.iot.device.domain.device.authority.TelemetryDeviceAuthorityResolutionDTO;
import com.basiclab.iot.device.service.device.authority.TelemetryDeviceAuthorityInternalAuth;
import com.basiclab.iot.device.service.device.authority.TelemetryDeviceAuthorityInternalException;
import com.basiclab.iot.device.service.device.authority.TelemetryDeviceAuthorityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/** ADR-018 protected center authority endpoint. */
@Validated
@RestController
@ConditionalOnProperty(name = "easyaiot.security.internal.enabled", havingValue = "true")
@RequestMapping(TelemetryDeviceAuthorityInternalApi.BASE_PATH)
public class TelemetryDeviceAuthorityInternalController {

    private final TelemetryDeviceAuthorityService service;
    private final TelemetryDeviceAuthorityInternalAuth auth;

    public TelemetryDeviceAuthorityInternalController(
            TelemetryDeviceAuthorityService service,
            InternalServiceAuthVerifier verifier) {
        this.service = service;
        this.auth = new TelemetryDeviceAuthorityInternalAuth(verifier);
    }

    @GetMapping("/resolve")
    public R<TelemetryDeviceAuthorityResolutionDTO> resolve(
            @RequestParam("productIdentification") String productIdentification,
            @RequestParam("deviceIdentification") String deviceIdentification,
            HttpServletRequest request) {
        auth.verify(request);
        return R.ok(service.resolve(productIdentification, deviceIdentification));
    }

    @ExceptionHandler(InternalServiceAuthException.class)
    public ResponseEntity<R<Void>> authFailure(InternalServiceAuthException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(HttpStatus.UNAUTHORIZED.value(), exception.getCode()));
    }

    @ExceptionHandler(TelemetryDeviceAuthorityInternalException.class)
    public ResponseEntity<R<Void>> contractFailure(
            TelemetryDeviceAuthorityInternalException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(R.fail(exception.getHttpStatus(), exception.getCode()));
    }
}
