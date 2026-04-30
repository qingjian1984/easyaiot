package com.basiclab.iot.video.dahua.sdk;

import com.basiclab.iot.video.sdk.dto.SdkAuthRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过大华 NetSDK 登录设备并读取设备注册信息（序列号、通道资源等）。
 */
@Service
public class DahuaSdkDeviceInfoService {

    private static final Object INIT_LOCK = new Object();
    private static volatile boolean sdkInitialized;

    private void ensureSdkInit() {
        if (sdkInitialized) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (sdkInitialized) {
                return;
            }
            boolean ok = DahuaNetSdkLite.INSTANCE.CLIENT_Init(
                    (lLoginID, pchDVRIP, nDVRPort, dwUser) -> { /*断线回调*/ },
                    null);
            if (!ok) {
                throw new IllegalStateException("大华 CLIENT_Init 失败");
            }
            DahuaNetSdkLite.NET_PARAM netParam = new DahuaNetSdkLite.NET_PARAM();
            netParam.nConnectTime = 10000;
            netParam.nGetConnInfoTime = 3000;
            netParam.nGetDevInfoTime = 3000;
            DahuaNetSdkLite.INSTANCE.CLIENT_SetNetworkParam(netParam);
            sdkInitialized = true;
        }
    }

    public Map<String, Object> queryDeviceInfo(SdkAuthRequest req) {
        ensureSdkInit();

        DahuaNetSdkLite.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY in = new DahuaNetSdkLite.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY();
        copyToFixedBytes(req.getIp(), in.szIP);
        in.nPort = req.getPort();
        copyToFixedBytes(req.getUsername(), in.szUserName);
        copyToFixedBytes(req.getPassword(), in.szPassword);
        in.emSpecCap = 0;
        in.emTLSCap = 0;
        in.pCapParam = null;
        in.write();

        DahuaNetSdkLite.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY out = new DahuaNetSdkLite.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY();
        out.stuDeviceInfo = new DahuaNetSdkLite.NET_DEVICEINFO_Ex();
        out.write();

        DahuaNetSdkLite.LLong handle = DahuaNetSdkLite.INSTANCE.CLIENT_LoginWithHighLevelSecurity(in, out);
        if (handle.longValue() == 0) {
            int err = DahuaNetSdkLite.INSTANCE.CLIENT_GetLastError();
            throw new IllegalStateException("大华设备登录失败, errCode=" + err);
        }
        try {
            out.read();
            DahuaNetSdkLite.NET_DEVICEINFO_Ex dev = out.stuDeviceInfo;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("vendor", "dahua");
            map.put("ip", req.getIp());
            map.put("port", req.getPort());
            map.put("serialNumber", bytesToStringGbk(dev.sSerialNumber));
            map.put("byChanNum", dev.byChanNum);
            map.put("byAlarmInPortNum", dev.byAlarmInPortNum);
            map.put("byAlarmOutPortNum", dev.byAlarmOutPortNum);
            map.put("byDiskNum", dev.byDiskNum);
            map.put("byDVRType", dev.byDVRType);
            return map;
        } finally {
            DahuaNetSdkLite.INSTANCE.CLIENT_Logout(handle);
        }
    }

    private static void copyToFixedBytes(String src, byte[] dest) {
        byte[] raw = src.getBytes(Charset.forName("GBK"));
        System.arraycopy(raw, 0, dest, 0, Math.min(raw.length, dest.length - 1));
    }

    private static String bytesToStringGbk(byte[] bytes) {
        int n = 0;
        while (n < bytes.length && bytes[n] != 0) {
            n++;
        }
        return new String(bytes, 0, n, Charset.forName("GBK")).trim();
    }
}
