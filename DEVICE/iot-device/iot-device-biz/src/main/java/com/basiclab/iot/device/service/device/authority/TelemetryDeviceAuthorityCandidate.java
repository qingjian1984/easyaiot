package com.basiclab.iot.device.service.device.authority;

/** Internal mapper projection; never exposed from the authority endpoint. */
public class TelemetryDeviceAuthorityCandidate {

    private Long tenantId;
    private String productIdentification;
    private String deviceIdentification;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getProductIdentification() {
        return productIdentification;
    }

    public void setProductIdentification(String productIdentification) {
        this.productIdentification = productIdentification;
    }

    public String getDeviceIdentification() {
        return deviceIdentification;
    }

    public void setDeviceIdentification(String deviceIdentification) {
        this.deviceIdentification = deviceIdentification;
    }
}
