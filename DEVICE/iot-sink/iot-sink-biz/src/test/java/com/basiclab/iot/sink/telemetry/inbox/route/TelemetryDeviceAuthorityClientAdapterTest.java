package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.device.domain.device.authority.ResolutionStatus;
import com.basiclab.iot.device.domain.device.authority.TelemetryDeviceAuthorityResolutionDTO;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TelemetryDeviceAuthorityClientAdapterTest {

    private static final TelemetryRoute ROUTE = new TelemetryRoute("product-1", "device-1");

    @Test
    void mapsOnlyStrictBusinessResponsesToAuthorityStates() {
        assertInstanceOf(TelemetryDeviceAuthorityPort.Resolution.Resolved.class,
                adapter((product, device) -> R.ok(dto(ResolutionStatus.RESOLVED, "920006001")))
                        .resolve(ROUTE));
        assertInstanceOf(TelemetryDeviceAuthorityPort.Resolution.NotFound.class,
                adapter((product, device) -> R.ok(dto(ResolutionStatus.NOT_FOUND, null)))
                        .resolve(ROUTE));
        assertInstanceOf(TelemetryDeviceAuthorityPort.Resolution.Ambiguous.class,
                adapter((product, device) -> R.ok(dto(ResolutionStatus.AMBIGUOUS, null)))
                        .resolve(ROUTE));
    }

    @Test
    void passesTheExactRouteAndNeverSuppliesAClientDefault() {
        AtomicReference<String> product = new AtomicReference<>();
        AtomicReference<String> device = new AtomicReference<>();
        TelemetryDeviceAuthorityClient client = (receivedProduct, receivedDevice) -> {
            product.set(receivedProduct);
            device.set(receivedDevice);
            return R.ok(dto(ResolutionStatus.RESOLVED, "1"));
        };

        TelemetryDeviceAuthorityPort.Resolution resolution =
                new TelemetryDeviceAuthorityClientAdapter(client).resolve(ROUTE);

        assertEquals("product-1", product.get());
        assertEquals("device-1", device.get());
        TelemetryDeviceAuthorityPort.Resolution.Resolved resolved =
                assertInstanceOf(TelemetryDeviceAuthorityPort.Resolution.Resolved.class, resolution);
        assertEquals("1", resolved.tenantId());
    }

    @Test
    void mapsTransportAndContractFailuresToUnavailable() {
        TelemetryDeviceAuthorityClient throwing = (product, device) -> {
            throw new IllegalStateException("timeout");
        };
        assertUnavailable(new TelemetryDeviceAuthorityClientAdapter(throwing).resolve(ROUTE));
        assertUnavailable(adapter((product, device) -> null).resolve(ROUTE));
        assertUnavailable(adapter((product, device) -> R.fail("503")).resolve(ROUTE));
        assertUnavailable(adapter((product, device) ->
                R.ok(new TelemetryDeviceAuthorityResolutionDTO(
                        "other-product", "device-1", ResolutionStatus.RESOLVED, "1"))).resolve(ROUTE));
        assertUnavailable(adapter((product, device) ->
                R.ok(dto(ResolutionStatus.RESOLVED, "01"))).resolve(ROUTE));
        assertUnavailable(adapter((product, device) ->
                R.ok(dto(ResolutionStatus.RESOLVED, "0"))).resolve(ROUTE));
        assertUnavailable(adapter((product, device) ->
                R.ok(dto(ResolutionStatus.NOT_FOUND, "920006001"))).resolve(ROUTE));
        assertUnavailable(adapter((product, device) ->
                R.ok(dto(ResolutionStatus.AMBIGUOUS, "920006001"))).resolve(ROUTE));
        assertUnavailable(new TelemetryDeviceAuthorityClientAdapter(null).resolve(null));
    }

    @Test
    void neverLeaksCandidateOrTenantDataForNonResolvedStates() {
        TelemetryDeviceAuthorityClient client = (product, device) ->
                R.ok(new TelemetryDeviceAuthorityResolutionDTO(
                        product, device, ResolutionStatus.NOT_FOUND, null));
        TelemetryDeviceAuthorityPort.Resolution result =
                new TelemetryDeviceAuthorityClientAdapter(client).resolve(ROUTE);
        TelemetryDeviceAuthorityPort.Resolution.NotFound notFound =
                assertInstanceOf(TelemetryDeviceAuthorityPort.Resolution.NotFound.class, result);
        assertNotNull(notFound);
    }

    private static TelemetryDeviceAuthorityClientAdapter adapter(
            TelemetryDeviceAuthorityClient client) {
        return new TelemetryDeviceAuthorityClientAdapter(client);
    }

    private static TelemetryDeviceAuthorityResolutionDTO dto(
            ResolutionStatus status, String tenantId) {
        return new TelemetryDeviceAuthorityResolutionDTO(
                ROUTE.productIdentification(), ROUTE.deviceIdentification(), status, tenantId);
    }

    private static void assertUnavailable(TelemetryDeviceAuthorityPort.Resolution result) {
        assertInstanceOf(TelemetryDeviceAuthorityPort.Resolution.Unavailable.class, result);
    }
}
