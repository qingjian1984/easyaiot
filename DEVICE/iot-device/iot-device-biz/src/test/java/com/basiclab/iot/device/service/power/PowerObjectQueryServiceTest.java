package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotReqDTO;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PowerObjectQueryServiceTest {

    private static final long TENANT = 910_006_101L;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void mapsReadyNotBoundAndInactiveInRequestOrder() {
        TenantContextHolder.setTenantId(TENANT);
        List<PowerObjectSnapshotRow> rows = Arrays.asList(
                row("inactive", "DISABLE", true, "ACTIVE", "ACTIVE", 3L),
                row("ready", "ENABLE", true, "ACTIVE", "ACTIVE", 2L),
                row("not-bound", "ENABLE", false, null, null, null));
        PowerObjectQueryService service = service((tenantId, ids) -> rows, true);

        List<PowerCollectorObjectSnapshotRespDTO> result = service.queryCollectorSnapshots(
                request("ready", "not-bound", "inactive"));

        assertEquals(List.of("ready", "not-bound", "inactive"), result.stream()
                .map(PowerCollectorObjectSnapshotRespDTO::getDeviceIdentification).toList());
        assertEquals("READY", result.get(0).getStatus());
        assertTrue(result.get(0).isActive());
        assertTrue(result.get(0).getObjectRevision().matches("sha256:[0-9a-f]{64}"));
        assertEquals("NOT_BOUND", result.get(1).getStatus());
        assertFalse(result.get(1).isActive());
        assertNull(result.get(1).getObjectRevision());
        assertEquals("INACTIVE", result.get(2).getStatus());
        assertFalse(result.get(2).isActive());
    }

    @Test
    void rejectsUnknownDuplicateAndAmbiguousIdentifiersFailClosed() {
        TenantContextHolder.setTenantId(TENANT);
        PowerObjectQueryService missing = service((tenantId, ids) -> Collections.emptyList(), true);
        assertThrows(ServiceException.class,
                () -> missing.queryCollectorSnapshots(request("unknown")));
        assertThrows(ServiceException.class,
                () -> missing.queryCollectorSnapshots(request("duplicate", "duplicate")));

        PowerObjectSnapshotRow first = row("ambiguous", "ENABLE", true, "ACTIVE", "ACTIVE", 1L);
        PowerObjectSnapshotRow second = new PowerObjectSnapshotRow(
                first.tenantId(), first.deviceId() + 1, first.deviceIdentification(),
                first.deviceStatus(), first.assetId(), first.assetStatus(), first.assetVersion(),
                first.assignmentId(), first.assignmentVersion(), first.siteId(), first.siteCode(),
                first.siteStatus(), first.siteVersion(), first.spaceId(), first.spaceCode(),
                first.spaceStatus(), first.spaceVersion(), first.circuitId(), first.circuitCode(),
                first.circuitStatus(), first.circuitVersion());
        PowerObjectQueryService ambiguous = service((tenantId, ids) -> List.of(first, second), true);
        assertThrows(ServiceException.class,
                () -> ambiguous.queryCollectorSnapshots(request("ambiguous")));
    }

    @Test
    void capabilityAndTenantContextAreMandatory() {
        TenantContextHolder.setTenantId(TENANT);
        PowerObjectQueryService disabled = service((tenantId, ids) -> Collections.emptyList(), false);
        assertThrows(ServiceException.class,
                () -> disabled.queryCollectorSnapshots(request("device-a")));

        TenantContextHolder.clear();
        PowerObjectQueryService enabled = service((tenantId, ids) -> Collections.emptyList(), true);
        assertThrows(NullPointerException.class,
                () -> enabled.queryCollectorSnapshots(request("device-a")));
    }

    @Test
    void objectRevisionIsDeterministicAndVersionSensitive() {
        PowerObjectSnapshotRow revision2 = row("ready", "ENABLE", true, "ACTIVE", "ACTIVE", 2L);
        PowerObjectSnapshotRow revision3 = row("ready", "ENABLE", true, "ACTIVE", "ACTIVE", 3L);

        assertEquals(PowerObjectQueryService.objectRevision(revision2),
                PowerObjectQueryService.objectRevision(revision2));
        assertNotEquals(PowerObjectQueryService.objectRevision(revision2),
                PowerObjectQueryService.objectRevision(revision3));
    }

    private static PowerObjectQueryService service(PowerObjectSnapshotMapper mapper, boolean enabled) {
        CapabilityService capability = mock(CapabilityService.class);
        when(capability.isEnabled(PowerObjectQueryService.CAPABILITY_CODE)).thenReturn(enabled);
        return new PowerObjectQueryService(mapper, capability);
    }

    private static PowerCollectorObjectSnapshotReqDTO request(String... values) {
        PowerCollectorObjectSnapshotReqDTO request = new PowerCollectorObjectSnapshotReqDTO();
        request.setDeviceIdentifications(Arrays.asList(values));
        return request;
    }

    private static PowerObjectSnapshotRow row(String identification, String deviceStatus,
                                               boolean bound, String assetStatus,
                                               String siteStatus, Long assignmentVersion) {
        long suffix = Math.abs(identification.hashCode());
        return new PowerObjectSnapshotRow(
                TENANT, 920_000_000L + suffix, identification, deviceStatus,
                bound ? 930_000_000L + suffix : null, assetStatus, bound ? 4L : null,
                bound ? 940_000_000L + suffix : null, assignmentVersion,
                bound ? 950_000_000L + suffix : null, bound ? "site-a" : null,
                siteStatus, bound ? 5L : null,
                bound ? 960_000_000L + suffix : null, bound ? "room-a" : null,
                bound ? "ACTIVE" : null, bound ? 6L : null,
                bound ? 970_000_000L + suffix : null, bound ? "line-a" : null,
                bound ? "ACTIVE" : null, bound ? 7L : null);
    }
}
