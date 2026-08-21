package com.basiclab.iot.device.service.collector.backfill;

import com.basiclab.iot.device.service.collector.backfill.RouteBackfillFactRepository.ProductFact;
import com.basiclab.iot.device.service.collector.backfill.RouteBackfillFactRepository.ProjectionFact;
import com.basiclab.iot.device.service.collector.backfill.RouteBackfillFactRepository.ReleaseFact;
import com.basiclab.iot.device.service.model.JcsCanonicalizer;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillIssue;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillResolutionResult;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryEntry;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteBackfillManifestResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JcsCanonicalizer JCS = new JcsCanonicalizer();

    @Test
    void emptyPageProducesAnEmptyResolvedManifestWithoutRepositoryReads() {
        FakeRepository repository = new FakeRepository();
        RouteInventoryPage page = new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-a", List.of(), null);
        RouteBackfillResolutionResult.Resolved result = assertInstanceOf(
                RouteBackfillResolutionResult.Resolved.class,
                new RouteBackfillManifestResolver(repository).resolve(new RouteInventoryArtifact(page)));
        assertTrue(result.artifact().manifest().entries().isEmpty());
        assertEquals("workload-a", result.artifact().manifest().workloadId());
        assertEquals(0, repository.reads);
    }

    @Test
    void resolvesV10AndV11AndRepeatsDeterministically() {
        Fixture v10 = fixture(false);
        RouteBackfillResolutionResult.Resolved first = resolved(v10);
        RouteBackfillResolutionResult.Resolved second = resolved(v10);
        assertEquals(first.artifact().contentSha256(), second.artifact().contentSha256());
        assertEquals("product-a", first.artifact().manifest().entries().get(0).productIdentification());
        assertEquals(101L, first.artifact().manifest().entries().get(0).releaseId());

        Fixture v11 = fixture(true);
        RouteBackfillResolutionResult.Resolved v11Result = resolved(v11);
        assertEquals("product-a", v11Result.artifact().manifest().entries().get(0)
                .productIdentification());
    }

    @Test
    void rejectsEveryStableFailureAtTheFirstApplicableBoundary() {
        Fixture invalidTenant = fixture(true);
        invalidTenant.key = new RouteBackfillKey("not-a-number", "site-a", 1, "device-a");
        assertCode(invalidTenant, "ROUTE_BACKFILL_TENANT_ID_INVALID");

        Fixture noRelease = fixture(true);
        noRelease.repository.releases.put(noRelease.lookup(), List.of());
        assertCode(noRelease, "ROUTE_BACKFILL_RELEASE_NOT_UNIQUE");

        Fixture badPayload = fixture(true);
        badPayload.release = copyRelease(badPayload.release, badPayload.release.payloadSha256(),
                "b".repeat(64));
        badPayload.repository.put(badPayload);
        assertCode(badPayload, "ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED");

        Fixture badLength = fixture(true);
        badLength.release = new ReleaseFact(badLength.release.releaseId(),
                badLength.release.tenantId(), badLength.release.siteId(), badLength.release.siteCode(),
                badLength.release.workloadId(), badLength.release.nodeId(),
                badLength.release.configVersion(), badLength.release.schemaVersion(),
                badLength.release.canonicalizationVersion(), badLength.release.payloadCanonical(),
                badLength.release.payloadProjection(), badLength.release.payloadSha256(),
                badLength.release.canonicalLengthBytes() + 1, badLength.release.status(),
                badLength.release.productId());
        badLength.repository.put(badLength);
        assertCode(badLength, "ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED");

        Fixture whitespaceHash = fixture(true);
        whitespaceHash.release = copyRelease(whitespaceHash.release,
                whitespaceHash.release.payloadSha256(),
                " " + whitespaceHash.release.payloadSha256());
        whitespaceHash.repository.put(whitespaceHash);
        assertCode(whitespaceHash, "ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED");

        Fixture leadingZeroTenant = fixture(true);
        leadingZeroTenant.key = new RouteBackfillKey("001", "site-a", 1, "device-a");
        leadingZeroTenant.repository.put(leadingZeroTenant);
        assertCode(leadingZeroTenant, "ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH");

        Fixture rowIdentity = fixture(true);
        rowIdentity.release = new ReleaseFact(rowIdentity.release.releaseId(),
                rowIdentity.release.tenantId(), rowIdentity.release.siteId(), "other-site",
                rowIdentity.release.workloadId(), rowIdentity.release.nodeId(),
                rowIdentity.release.configVersion(), rowIdentity.release.schemaVersion(),
                rowIdentity.release.canonicalizationVersion(), rowIdentity.release.payloadCanonical(),
                rowIdentity.release.payloadProjection(), rowIdentity.release.payloadSha256(),
                rowIdentity.release.canonicalLengthBytes(), rowIdentity.release.status(),
                rowIdentity.release.productId());
        rowIdentity.repository.put(rowIdentity);
        assertCode(rowIdentity, "ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH");

        Fixture schemaMismatch = fixture(true);
        schemaMismatch.release = new ReleaseFact(schemaMismatch.release.releaseId(),
                schemaMismatch.release.tenantId(), schemaMismatch.release.siteId(),
                schemaMismatch.release.siteCode(), schemaMismatch.release.workloadId(),
                schemaMismatch.release.nodeId(), schemaMismatch.release.configVersion(), "1.0",
                schemaMismatch.release.canonicalizationVersion(), schemaMismatch.release.payloadCanonical(),
                schemaMismatch.release.payloadProjection(), schemaMismatch.release.payloadSha256(),
                schemaMismatch.release.canonicalLengthBytes(), schemaMismatch.release.status(),
                schemaMismatch.release.productId());
        schemaMismatch.repository.put(schemaMismatch);
        assertCode(schemaMismatch, "ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED");

        Fixture identity = fixture(true);
        ObjectNode wrongRoot = identity.payload.deepCopy();
        wrongRoot.put("tenantId", "999");
        identity.replacePayload(wrongRoot);
        assertCode(identity, "ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH");

        Fixture stringConfigVersion = fixture(true);
        ObjectNode textualConfig = stringConfigVersion.payload.deepCopy();
        textualConfig.put("configVersion", "1");
        stringConfigVersion.replacePayload(textualConfig);
        assertCode(stringConfigVersion, "ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH");

        Fixture noDevice = fixture(true);
        ObjectNode missingDevice = noDevice.payload.deepCopy();
        ((ObjectNode) missingDevice.at("/serialBuses/0/devices/0")).put(
                "deviceIdentification", "other-device");
        noDevice.replacePayload(missingDevice);
        assertCode(noDevice, "ROUTE_BACKFILL_DEVICE_NOT_UNIQUE");

        Fixture noProjection = fixture(true);
        noProjection.repository.projections.put(noProjection.projectionLookup(), List.of());
        assertCode(noProjection, "ROUTE_BACKFILL_PROJECTION_NOT_UNIQUE");

        Fixture driftingProjection = fixture(true);
        ProjectionFact projection = driftingProjection.repository.projections
                .get(driftingProjection.projectionLookup()).get(0);
        driftingProjection.repository.projections.put(driftingProjection.projectionLookup(),
                List.of(new ProjectionFact(projection.tenantId(), projection.workloadId(),
                        projection.siteId(), "other-site", projection.nodeId(), projection.productId(),
                        projection.configVersion(), projection.releaseId(), projection.lifecycleStatus())));
        assertCode(driftingProjection, "ROUTE_BACKFILL_PROJECTION_IDENTITY_MISMATCH");

        Fixture noProduct = fixture(true);
        noProduct.repository.products.put(noProduct.productLookup(), List.of());
        assertCode(noProduct, "ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE");

        Fixture wrongProductTenant = fixture(true);
        wrongProductTenant.repository.products.put(wrongProductTenant.productLookup(), List.of(
                new ProductFact(999, 20, "product-a")));
        assertCode(wrongProductTenant, "ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE");

        Fixture wrongProductId = fixture(true);
        wrongProductId.repository.products.put(wrongProductId.productLookup(), List.of(
                new ProductFact(1, 999, "product-a")));
        assertCode(wrongProductId, "ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE");

        Fixture invalidProduct = fixture(true);
        invalidProduct.repository.products.put(invalidProduct.productLookup(), List.of(
                new ProductFact(1, 20, " ")));
        assertCode(invalidProduct, "ROUTE_BACKFILL_PRODUCT_IDENTIFICATION_INVALID");

        Fixture productDrift = fixture(true);
        productDrift.repository.products.put(productDrift.productLookup(), List.of(
                new ProductFact(1, 20, "product-b")));
        assertCode(productDrift, "ROUTE_BACKFILL_PRODUCT_IDENTITY_MISMATCH");
    }

    @Test
    void rejectsWholePageAndPreservesSourceOrderWhenSeveralKeysFail() {
        Fixture first = fixture(true);
        first.repository.products.put(first.productLookup(), List.of());
        RouteBackfillKey secondKey = new RouteBackfillKey("bad", "site-a", 1, "device-b");
        RouteInventoryPage page = new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-a",
                List.of(new RouteInventoryEntry(first.key, 2),
                        new RouteInventoryEntry(secondKey, 3)), null);
        RouteBackfillResolutionResult result = new RouteBackfillManifestResolver(first.repository)
                .resolve(new RouteInventoryArtifact(page));
        RouteBackfillResolutionResult.Rejected rejected = assertInstanceOf(
                RouteBackfillResolutionResult.Rejected.class, result);
        assertEquals(List.of("ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE",
                        "ROUTE_BACKFILL_TENANT_ID_INVALID"),
                rejected.issues().stream().map(RouteBackfillIssue::code).toList());
        assertEquals(2, rejected.issues().size());
    }

    @Test
    void doesNotNormalizeProductIdentityOrLeakPayloadInRejection() {
        Fixture fixture = fixture(false);
        String nfd = "cafe\u0301-product";
        fixture.repository.products.put(fixture.productLookup(), List.of(
                new ProductFact(1, 20, nfd)));
        RouteBackfillResolutionResult result = resolvedResult(fixture);
        assertEquals(nfd, assertInstanceOf(RouteBackfillResolutionResult.Resolved.class, result)
                .artifact().manifest().entries().get(0).productIdentification());

        Fixture rejected = fixture(true);
        rejected.repository.products.put(rejected.productLookup(), List.of());
        RouteBackfillResolutionResult.Rejected value = assertInstanceOf(
                RouteBackfillResolutionResult.Rejected.class, resolvedResult(rejected));
        assertEquals(rejected.inventory.contentSha256(), value.sourceInventorySha256());
        assertTrue(value.issues().stream().noneMatch(issue -> issue.toString().contains("payload")));
    }

    @Test
    void rejectsInvalidInputAndInventoryIntegrityWithStablePrefixes() {
        RouteBackfillManifestResolver resolver = new RouteBackfillManifestResolver(new FakeRepository());
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null));
        RouteInventoryPage page = new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-a", List.of(), null);
        RouteInventoryArtifact artifact = new RouteInventoryArtifact(page);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(new RouteInventoryArtifact(
                page, artifact.canonicalBytes(), "0".repeat(64))));
    }

    private static RouteBackfillResolutionResult.Resolved resolved(Fixture fixture) {
        return assertInstanceOf(RouteBackfillResolutionResult.Resolved.class, resolvedResult(fixture));
    }

    private static RouteBackfillResolutionResult resolvedResult(Fixture fixture) {
        RouteInventoryPage sourcePage = fixture.inventory.page();
        fixture.inventory = new RouteInventoryArtifact(new RouteInventoryPage(
                sourcePage.schemaVersion(), sourcePage.canonicalizationVersion(),
                sourcePage.workloadId(),
                List.of(new RouteInventoryEntry(fixture.key,
                        sourcePage.entries().get(0).rowCount())),
                null));
        return new RouteBackfillManifestResolver(fixture.repository).resolve(fixture.inventory);
    }

    private static void assertCode(Fixture fixture, String expected) {
        RouteBackfillResolutionResult.Rejected result = assertInstanceOf(
                RouteBackfillResolutionResult.Rejected.class, resolvedResult(fixture));
        assertEquals(List.of(expected), result.issues().stream().map(RouteBackfillIssue::code).toList());
    }

    private static Fixture fixture(boolean v11) {
        Fixture fixture = new Fixture();
        fixture.key = new RouteBackfillKey("1", "site-a", 1, "device-a");
        fixture.payload = payload(v11, "product-a", "1", "workload-a", "site-a", 1,
                "device-a");
        fixture.release = release(fixture.payload, v11, 101L);
        fixture.repository.put(fixture);
        fixture.inventory = new RouteInventoryArtifact(new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-a", List.of(new RouteInventoryEntry(fixture.key, 2)), null));
        return fixture;
    }

    private static ReleaseFact release(ObjectNode payload, boolean v11, long id) {
        String canonical = JCS.canonicalize(payload);
        String hash = hash(canonical);
        return new ReleaseFact(id, 1, 2, "site-a", "workload-a", 3, 1,
                v11 ? "1.1" : "1.0", "jcs-rfc8785-v1", canonical, canonical, hash,
                canonical.getBytes(StandardCharsets.UTF_8).length, "PUBLISHED", 20);
    }

    private static ReleaseFact copyRelease(ReleaseFact source, String ignored, String hash) {
        return new ReleaseFact(source.releaseId(), source.tenantId(), source.siteId(), source.siteCode(),
                source.workloadId(), source.nodeId(), source.configVersion(), source.schemaVersion(),
                source.canonicalizationVersion(), source.payloadCanonical(), source.payloadProjection(),
                hash, source.canonicalLengthBytes(), source.status(), source.productId());
    }

    private static ObjectNode payload(boolean v11, String product, String tenant, String workload,
                                      String site, long config, String device) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", v11 ? "1.1" : "1.0");
        if (v11) root.put("productIdentification", product);
        root.put("tenantId", tenant);
        root.put("workloadId", workload);
        root.put("siteCode", site);
        root.put("configVersion", config);
        ArrayNode buses = root.putArray("serialBuses");
        ObjectNode bus = buses.addObject();
        bus.putArray("devices").addObject().put("deviceIdentification", device);
        return root;
    }

    private static String hash(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) {
                result.append(Character.forDigit((b >>> 4) & 15, 16));
                result.append(Character.forDigit(b & 15, 16));
            }
            return result.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class Fixture {
        private final FakeRepository repository = new FakeRepository();
        private RouteBackfillKey key;
        private ObjectNode payload;
        private ReleaseFact release;
        private RouteInventoryArtifact inventory;

        private String lookup() {
            return repository.releaseLookup(1, "workload-a", key.siteCode(), key.configVersion());
        }

        private String projectionLookup() {
            return repository.projectionLookup(1, "workload-a");
        }

        private String productLookup() {
            return repository.productLookup(1, 20);
        }

        private void replacePayload(ObjectNode next) {
            payload = next;
            release = release(next, "1.1".equals(next.path("schemaVersion").asText()), 101L);
            repository.put(this);
        }
    }

    private static final class FakeRepository implements RouteBackfillFactRepository {
        private final Map<String, List<ReleaseFact>> releases = new HashMap<>();
        private final Map<String, List<ProjectionFact>> projections = new HashMap<>();
        private final Map<String, List<ProductFact>> products = new HashMap<>();
        private int reads;

        private void put(Fixture fixture) {
            releases.put(fixture.lookup(), List.of(fixture.release));
            projections.put(projectionLookup(1, "workload-a"), List.of(
                    new ProjectionFact(1, "workload-a", 2, "site-a", 3, 20,
                            1, 999, "STOPPED")));
            products.put(productLookup(1, 20), List.of(new ProductFact(1, 20, "product-a")));
        }

        private String releaseLookup(long tenant, String workload, String site, long version) {
            return tenant + "|" + workload + "|" + site + "|" + version;
        }

        private String projectionLookup(long tenant, String workload) {
            return tenant + "|" + workload;
        }

        private String productLookup(long tenant, long product) {
            return tenant + "|" + product;
        }

        @Override
        public List<ReleaseFact> findReleaseFacts(long tenantId, String workloadId,
                                                  String siteCode, long configVersion) {
            reads++;
            return releases.getOrDefault(releaseLookup(tenantId, workloadId, siteCode, configVersion),
                    List.of());
        }

        @Override
        public List<ProjectionFact> findProjectionFacts(long tenantId, String workloadId) {
            reads++;
            return projections.getOrDefault(projectionLookup(tenantId, workloadId), List.of());
        }

        @Override
        public List<ProductFact> findProductFacts(long tenantId, long productId) {
            reads++;
            return products.getOrDefault(productLookup(tenantId, productId), List.of());
        }
    }
}
