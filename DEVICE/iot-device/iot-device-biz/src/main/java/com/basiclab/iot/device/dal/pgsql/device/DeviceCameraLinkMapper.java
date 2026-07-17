package com.basiclab.iot.device.dal.pgsql.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.basiclab.iot.device.domain.device.vo.DeviceCameraLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceCameraLinkMapper extends BaseMapper<DeviceCameraLink> {

    List<DeviceCameraLink> selectByIotDeviceId(@Param("iotDeviceId") Long iotDeviceId);

    List<String> selectAllBoundCameraIds();

    DeviceCameraLink selectByCameraDeviceId(@Param("cameraDeviceId") String cameraDeviceId);

    int deleteByIotDeviceIds(@Param("iotDeviceIds") Long[] iotDeviceIds);
}
