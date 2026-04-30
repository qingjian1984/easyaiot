package com.basiclab.iot.video.sdk;

import com.basiclab.iot.video.dahua.sdk.DahuaSdkDeviceInfoService;
import com.basiclab.iot.video.hiksdk.HikSdkDeviceInfoService;
import com.basiclab.iot.video.sdk.dto.SdkAuthRequest;
import com.basiclab.iot.video.sdk.dto.AjaxResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 通过海康/大华官方 SDK 获取摄像头注册信息（登录后读取设备信息结构体）。
 */
@RestController
@RequestMapping("/sdk/register")
@Validated
public class SdkDeviceRegisterController {

    @Resource
    private DahuaSdkDeviceInfoService dahuaSdkDeviceInfoService;

    @Resource
    private HikSdkDeviceInfoService hikSdkDeviceInfoService;

    /**
     * 大华 NetSDK：登录并返回设备序列号、通道等信息。
     */
    @PostMapping("/dahua/device-info")
    public AjaxResult dahuaDeviceInfo(@RequestBody @Validated SdkAuthRequest request) {
        try {
            Map<String, Object> data = dahuaSdkDeviceInfoService.queryDeviceInfo(request);
            return AjaxResult.success(data);
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 海康 HCNetSDK：登录并返回设备序列号、通道等信息。
     */
    @PostMapping("/hik/device-info")
    public AjaxResult hikDeviceInfo(@RequestBody @Validated SdkAuthRequest request) {
        try {
            Map<String, Object> data = hikSdkDeviceInfoService.queryDeviceInfo(request);
            return AjaxResult.success(data);
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }
}
