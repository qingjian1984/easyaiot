package com.basiclab.iot.device.service.power;

/** JDBC 层读取的完整候选关系；只在 iot-device biz 内部使用。 */
final class PowerObjectSnapshotRow {

    private final long tenantId;
    private final long deviceId;
    private final String deviceIdentification;
    private final String deviceStatus;
    private final Long assetId;
    private final String assetStatus;
    private final Long assetVersion;
    private final Long assignmentId;
    private final Long assignmentVersion;
    private final Long siteId;
    private final String siteCode;
    private final String siteStatus;
    private final Long siteVersion;
    private final Long spaceId;
    private final String spaceCode;
    private final String spaceStatus;
    private final Long spaceVersion;
    private final Long circuitId;
    private final String circuitCode;
    private final String circuitStatus;
    private final Long circuitVersion;

    PowerObjectSnapshotRow(long tenantId, long deviceId, String deviceIdentification,
                           String deviceStatus, Long assetId, String assetStatus,
                           Long assetVersion, Long assignmentId, Long assignmentVersion,
                           Long siteId, String siteCode, String siteStatus, Long siteVersion,
                           Long spaceId, String spaceCode, String spaceStatus, Long spaceVersion,
                           Long circuitId, String circuitCode, String circuitStatus,
                           Long circuitVersion) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.deviceIdentification = deviceIdentification;
        this.deviceStatus = deviceStatus;
        this.assetId = assetId;
        this.assetStatus = assetStatus;
        this.assetVersion = assetVersion;
        this.assignmentId = assignmentId;
        this.assignmentVersion = assignmentVersion;
        this.siteId = siteId;
        this.siteCode = siteCode;
        this.siteStatus = siteStatus;
        this.siteVersion = siteVersion;
        this.spaceId = spaceId;
        this.spaceCode = spaceCode;
        this.spaceStatus = spaceStatus;
        this.spaceVersion = spaceVersion;
        this.circuitId = circuitId;
        this.circuitCode = circuitCode;
        this.circuitStatus = circuitStatus;
        this.circuitVersion = circuitVersion;
    }

    long tenantId() { return tenantId; }
    long deviceId() { return deviceId; }
    String deviceIdentification() { return deviceIdentification; }
    String deviceStatus() { return deviceStatus; }
    Long assetId() { return assetId; }
    String assetStatus() { return assetStatus; }
    Long assetVersion() { return assetVersion; }
    Long assignmentId() { return assignmentId; }
    Long assignmentVersion() { return assignmentVersion; }
    Long siteId() { return siteId; }
    String siteCode() { return siteCode; }
    String siteStatus() { return siteStatus; }
    Long siteVersion() { return siteVersion; }
    Long spaceId() { return spaceId; }
    String spaceCode() { return spaceCode; }
    String spaceStatus() { return spaceStatus; }
    Long spaceVersion() { return spaceVersion; }
    Long circuitId() { return circuitId; }
    String circuitCode() { return circuitCode; }
    String circuitStatus() { return circuitStatus; }
    Long circuitVersion() { return circuitVersion; }
}
