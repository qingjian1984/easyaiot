package com.basiclab.iot.video.hiksdk;

import com.basiclab.iot.video.hiksdk.jna.HikNetSdkLite;
import com.basiclab.iot.video.sdk.dto.SdkAuthRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过海康 HCNetSDK 登录设备并读取注册/设备信息（序列号、通道等）。
 */
@Service
public class HikSdkDeviceInfoService {

    @Resource
    private HikNetSdkHolder hikNetSdkHolder;

    public Map<String, Object> queryDeviceInfo(SdkAuthRequest req) {
        hikNetSdkHolder.ensureLoadedAndInit();
        HikNetSdkLite sdk = HikNetSdkHolder.hCNetSDK;

        HikNetSdkLite.NET_DVR_USER_LOGIN_INFO loginInfo = new HikNetSdkLite.NET_DVR_USER_LOGIN_INFO();
        copyAscii(req.getIp(), loginInfo.sDeviceAddress);
        loginInfo.wPort = req.getPort().shortValue();
        copyAscii(req.getUsername(), loginInfo.sUserName);
        copyAscii(req.getPassword(), loginInfo.sPassword);
        loginInfo.bUseAsynLogin = false;
        loginInfo.write();

        HikNetSdkLite.NET_DVR_DEVICEINFO_V40 deviceInfo = new HikNetSdkLite.NET_DVR_DEVICEINFO_V40();

        int userId = sdk.NET_DVR_Login_V40(loginInfo, deviceInfo);
        if (userId < 0) {
            int err = sdk.NET_DVR_GetLastError();
            throw new IllegalStateException("海康设备登录失败, errCode=" + err);
        }
        try {
            deviceInfo.read();
            HikNetSdkLite.NET_DVR_DEVICEINFO_V30 v30 = deviceInfo.struDeviceV30;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("vendor", "hikvision");
            map.put("ip", req.getIp());
            map.put("port", req.getPort());
            map.put("serialNumber", bytesToTrimmedString(v30.sSerialNumber));
            map.put("byChanNum", Byte.toUnsignedInt(v30.byChanNum));
            map.put("byStartChan", Byte.toUnsignedInt(v30.byStartChan));
            map.put("byIPChanNum", Byte.toUnsignedInt(v30.byIPChanNum));
            map.put("byHighDChanNum", Byte.toUnsignedInt(v30.byHighDChanNum));
            map.put("byStartDChan", Byte.toUnsignedInt(v30.byStartDChan));
            map.put("byDVRType", Byte.toUnsignedInt(v30.byDVRType));
            map.put("wDevType", Short.toUnsignedInt(v30.wDevType));
            map.put("byCharEncodeType", Byte.toUnsignedInt(deviceInfo.byCharEncodeType));
            return map;
        } finally {
            sdk.NET_DVR_Logout(userId);
        }
    }

    private static void copyAscii(String src, byte[] dest) {
        if (src == null) {
            return;
        }
        byte[] raw = src.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, dest, 0, Math.min(raw.length, dest.length - 1));
    }

    private static String bytesToTrimmedString(byte[] bytes) {
        int n = 0;
        while (n < bytes.length && bytes[n] != 0) {
            n++;
        }
        return new String(bytes, 0, n, StandardCharsets.UTF_8).trim();
    }
}
