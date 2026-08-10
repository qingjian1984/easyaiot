package com.basiclab.iot.device.service.power;

import java.util.Collection;
import java.util.List;

/** 当前认证租户内的电力对象快照只读 Mapper。 */
interface PowerObjectSnapshotMapper {

    List<PowerObjectSnapshotRow> selectByDeviceIdentifications(
            long tenantId, Collection<String> deviceIdentifications);
}
