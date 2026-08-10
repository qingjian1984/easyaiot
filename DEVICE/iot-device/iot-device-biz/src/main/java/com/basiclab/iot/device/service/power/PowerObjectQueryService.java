package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotReqDTO;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.POWER_CAPABILITY_UNAVAILABLE;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.POWER_OBJECT_NOT_FOUND;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.POWER_OBJECT_SCOPE_AMBIGUOUS;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.POWER_OBJECT_SNAPSHOT_REQUEST_INVALID;

/** TD-004 §14.1 collector 发布前的 tenant-safe 电力对象快照服务。 */
@Service
public class PowerObjectQueryService {

    public static final String CAPABILITY_CODE = "power.device.model";
    static final int MAX_BATCH_SIZE = 500;

    private final PowerObjectSnapshotMapper mapper;
    private final CapabilityService capabilityService;

    public PowerObjectQueryService(PowerObjectSnapshotMapper mapper,
                                   CapabilityService capabilityService) {
        this.mapper = mapper;
        this.capabilityService = capabilityService;
    }

    @Transactional(readOnly = true)
    public List<PowerCollectorObjectSnapshotRespDTO> queryCollectorSnapshots(
            PowerCollectorObjectSnapshotReqDTO request) {
        requireCapability();
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<String> requested = validateRequest(request);
        List<PowerObjectSnapshotRow> rows = mapper.selectByDeviceIdentifications(tenantId, requested);

        Map<String, PowerObjectSnapshotRow> byIdentification = new HashMap<>();
        for (PowerObjectSnapshotRow row : rows) {
            if (row.tenantId() != tenantId) {
                throw exception(POWER_OBJECT_SCOPE_AMBIGUOUS, row.deviceIdentification());
            }
            PowerObjectSnapshotRow previous = byIdentification.putIfAbsent(
                    row.deviceIdentification(), row);
            if (previous != null) {
                throw exception(POWER_OBJECT_SCOPE_AMBIGUOUS, row.deviceIdentification());
            }
        }

        List<PowerCollectorObjectSnapshotRespDTO> result = new ArrayList<>(requested.size());
        for (String deviceIdentification : requested) {
            PowerObjectSnapshotRow row = byIdentification.get(deviceIdentification);
            if (row == null) {
                throw exception(POWER_OBJECT_NOT_FOUND, deviceIdentification);
            }
            result.add(toResponse(row));
        }
        return result;
    }

    private List<String> validateRequest(PowerCollectorObjectSnapshotReqDTO request) {
        if (request == null || request.getDeviceIdentifications() == null
                || request.getDeviceIdentifications().isEmpty()
                || request.getDeviceIdentifications().size() > MAX_BATCH_SIZE) {
            throw exception(POWER_OBJECT_SNAPSHOT_REQUEST_INVALID, "deviceIdentifications");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : request.getDeviceIdentifications()) {
            if (value == null || value.isEmpty() || value.isBlank()
                    || value.length() > 256 || !unique.add(value)) {
                throw exception(POWER_OBJECT_SNAPSHOT_REQUEST_INVALID, "deviceIdentification");
            }
        }
        return new ArrayList<>(unique);
    }

    private PowerCollectorObjectSnapshotRespDTO toResponse(PowerObjectSnapshotRow row) {
        String status = status(row);
        boolean ready = "READY".equals(status);
        return PowerCollectorObjectSnapshotRespDTO.builder()
                .tenantId(Long.toString(row.tenantId()))
                .deviceIdentification(row.deviceIdentification())
                .siteCode(row.siteCode())
                .spaceCode(row.spaceCode())
                .circuitCode(row.circuitCode())
                .deviceAssetVersion(decimal(row.assetVersion()))
                .assignmentVersion(decimal(row.assignmentVersion()))
                .objectRevision(ready ? objectRevision(row) : null)
                .status(status)
                .active(ready)
                .build();
    }

    private String status(PowerObjectSnapshotRow row) {
        if (row.assetId() == null || row.assignmentId() == null || row.siteId() == null
                || row.siteCode() == null || row.siteCode().isBlank()) {
            return "NOT_BOUND";
        }
        if (!"ENABLE".equals(row.deviceStatus())
                || !"ACTIVE".equals(row.assetStatus())
                || !"ACTIVE".equals(row.siteStatus())
                || (row.spaceId() != null && !"ACTIVE".equals(row.spaceStatus()))
                || (row.circuitId() != null && !"ACTIVE".equals(row.circuitStatus()))) {
            return "INACTIVE";
        }
        if (row.assetVersion() == null || row.assignmentVersion() == null
                || row.siteVersion() == null
                || (row.spaceId() != null && row.spaceVersion() == null)
                || (row.circuitId() != null && row.circuitVersion() == null)) {
            return "INACTIVE";
        }
        return "READY";
    }

    static String objectRevision(PowerObjectSnapshotRow row) {
        String canonical = "power-object-revision-v1\n"
                + field("tenantId", row.tenantId())
                + field("deviceId", row.deviceId())
                + field("deviceIdentification", row.deviceIdentification())
                + field("deviceStatus", row.deviceStatus())
                + field("assetId", row.assetId())
                + field("assetStatus", row.assetStatus())
                + field("assetVersion", row.assetVersion())
                + field("assignmentId", row.assignmentId())
                + field("assignmentVersion", row.assignmentVersion())
                + field("siteId", row.siteId())
                + field("siteCode", row.siteCode())
                + field("siteStatus", row.siteStatus())
                + field("siteVersion", row.siteVersion())
                + field("spaceId", row.spaceId())
                + field("spaceCode", row.spaceCode())
                + field("spaceStatus", row.spaceStatus())
                + field("spaceVersion", row.spaceVersion())
                + field("circuitId", row.circuitId())
                + field("circuitCode", row.circuitCode())
                + field("circuitStatus", row.circuitStatus())
                + field("circuitVersion", row.circuitVersion());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String field(String name, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return name + "=" + text.length() + ":" + text + "\n";
    }

    private static String decimal(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private void requireCapability() {
        if (!capabilityService.isEnabled(CAPABILITY_CODE)) {
            throw exception(POWER_CAPABILITY_UNAVAILABLE, CAPABILITY_CODE);
        }
    }
}
