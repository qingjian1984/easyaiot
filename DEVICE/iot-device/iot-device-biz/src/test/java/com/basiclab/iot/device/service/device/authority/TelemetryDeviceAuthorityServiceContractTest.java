package com.basiclab.iot.device.service.device.authority;

import com.basiclab.iot.device.dal.pgsql.device.DeviceMapper;
import com.basiclab.iot.device.domain.device.authority.ResolutionStatus;
import com.basiclab.iot.device.domain.device.authority.TelemetryDeviceAuthorityResolutionDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelemetryDeviceAuthorityServiceContractTest {

    private static final String PRODUCT = "authority-product";
    private static final String DEVICE = "authority-device";

    @Test
    void zeroRowsAreNotFoundWithoutTenantDisclosure() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.selectTelemetryDeviceAuthorityCandidates(PRODUCT, DEVICE))
                .thenReturn(List.of());

        TelemetryDeviceAuthorityResolutionDTO result =
                new TelemetryDeviceAuthorityService(mapper).resolve(PRODUCT, DEVICE);

        assertEquals(ResolutionStatus.NOT_FOUND, result.status());
        assertNull(result.tenantId());
        assertEquals(PRODUCT, result.productIdentification());
        assertEquals(DEVICE, result.deviceIdentification());
    }

    @Test
    void oneRowReturnsCanonicalTenantAndExactRouteEcho() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.selectTelemetryDeviceAuthorityCandidates(PRODUCT, DEVICE))
                .thenReturn(List.of(candidate(920_006_001L)));

        TelemetryDeviceAuthorityResolutionDTO result =
                new TelemetryDeviceAuthorityService(mapper).resolve(PRODUCT, DEVICE);

        assertEquals(ResolutionStatus.RESOLVED, result.status());
        assertEquals("920006001", result.tenantId());
        assertEquals(PRODUCT, result.productIdentification());
        assertEquals(DEVICE, result.deviceIdentification());
    }

    @Test
    void twoRowsAreAmbiguousEvenWhenTheyShareTenant() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.selectTelemetryDeviceAuthorityCandidates(PRODUCT, DEVICE))
                .thenReturn(List.of(candidate(920_006_001L), candidate(920_006_001L)));

        TelemetryDeviceAuthorityResolutionDTO result =
                new TelemetryDeviceAuthorityService(mapper).resolve(PRODUCT, DEVICE);

        assertEquals(ResolutionStatus.AMBIGUOUS, result.status());
        assertNull(result.tenantId());
    }

    @Test
    void crossTenantDuplicateIsAlsoAmbiguous() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.selectTelemetryDeviceAuthorityCandidates(PRODUCT, DEVICE))
                .thenReturn(List.of(candidate(920_006_001L), candidate(920_006_002L)));

        TelemetryDeviceAuthorityResolutionDTO result =
                new TelemetryDeviceAuthorityService(mapper).resolve(PRODUCT, DEVICE);

        assertEquals(ResolutionStatus.AMBIGUOUS, result.status());
        assertNull(result.tenantId());
    }

    @Test
    void invalidRouteIsRejectedBeforeMapperCall() {
        DeviceMapper mapper = mock(DeviceMapper.class);

        TelemetryDeviceAuthorityInternalException exception = assertThrows(
                TelemetryDeviceAuthorityInternalException.class,
                () -> new TelemetryDeviceAuthorityService(mapper).resolve("bad/route", DEVICE));

        assertEquals("TELEMETRY_DEVICE_AUTHORITY_REQUEST_INVALID", exception.getCode());
        assertEquals(400, exception.getHttpStatus());
        verifyNoInteractions(mapper);
    }

    @Test
    void softDeletedRowsAreRepresentedByAnEmptyMapperResult() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.selectTelemetryDeviceAuthorityCandidates(PRODUCT, DEVICE))
                .thenReturn(List.of());

        TelemetryDeviceAuthorityResolutionDTO result =
                new TelemetryDeviceAuthorityService(mapper).resolve(PRODUCT, DEVICE);

        assertEquals(ResolutionStatus.NOT_FOUND, result.status());
        assertNull(result.tenantId());
    }

    @Test
    void invalidTenantDataFailsClosedAsUnavailableData() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.selectTelemetryDeviceAuthorityCandidates(PRODUCT, DEVICE))
                .thenReturn(List.of(candidate(0L)));

        TelemetryDeviceAuthorityInternalException exception = assertThrows(
                TelemetryDeviceAuthorityInternalException.class,
                () -> new TelemetryDeviceAuthorityService(mapper).resolve(PRODUCT, DEVICE));

        assertEquals("TELEMETRY_DEVICE_AUTHORITY_DATA_INVALID", exception.getCode());
        assertEquals(500, exception.getHttpStatus());
    }

    @Test
    void mapperFailureIsNotMasqueradedAsNotFound() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.selectTelemetryDeviceAuthorityCandidates(PRODUCT, DEVICE))
                .thenThrow(new IllegalStateException("database unavailable"));

        TelemetryDeviceAuthorityInternalException exception = assertThrows(
                TelemetryDeviceAuthorityInternalException.class,
                () -> new TelemetryDeviceAuthorityService(mapper).resolve(PRODUCT, DEVICE));

        assertEquals("TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE", exception.getCode());
        assertEquals(503, exception.getHttpStatus());
    }

    private static TelemetryDeviceAuthorityCandidate candidate(long tenantId) {
        TelemetryDeviceAuthorityCandidate candidate = new TelemetryDeviceAuthorityCandidate();
        candidate.setTenantId(tenantId);
        candidate.setProductIdentification(PRODUCT);
        candidate.setDeviceIdentification(DEVICE);
        return candidate;
    }
}
