package com.basiclab.iot.video.hiksdk;

import com.basiclab.iot.video.hiksdk.common.OsSelect;
import com.basiclab.iot.video.hiksdk.jna.HikNetSdkLite;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 仅负责加载海康 HCNetSDK 动态库并执行一次初始化。
 * 首次调用海康接口时再加载，避免本机未放置 DLL 时应用无法启动。
 */
@Slf4j
@Component
public class HikNetSdkHolder {

    @Value("${hik.hcnetsdk.windows-dll:}")
    private String windowsDll;

    @Value("${hik.hcnetsdk.linux-so:}")
    private String linuxSo;

    public static volatile HikNetSdkLite hCNetSDK;
    private static volatile boolean inited;

    public void ensureLoadedAndInit() {
        if (inited) {
            return;
        }
        synchronized (HikNetSdkHolder.class) {
            if (inited) {
                return;
            }
            if (hCNetSDK == null) {
                String path = resolvePath();
                try {
                    hCNetSDK = Native.load(path, HikNetSdkLite.class);
                } catch (Exception ex) {
                    log.error("加载 HCNetSDK 失败, path={} err={}", path, ex.getMessage());
                    throw new IllegalStateException("无法加载海康 HCNetSDK 动态库: " + path, ex);
                }
            }
            if (!hCNetSDK.NET_DVR_Init()) {
                int err = hCNetSDK.NET_DVR_GetLastError();
                log.error("NET_DVR_Init 失败, errCode={}", err);
                throw new IllegalStateException("海康 SDK 初始化失败, errCode=" + err);
            }
            inited = true;
            log.info("海康 HCNetSDK 已初始化");
        }
    }

    private String resolvePath() {
        if (OsSelect.isWindows()) {
            if (windowsDll != null && !windowsDll.trim().isEmpty()) {
                return windowsDll.trim();
            }
            return System.getProperty("user.dir") + "\\iot-video-sdk\\hik\\win-lib\\HCNetSDK.dll";
        }
        if (linuxSo != null && !linuxSo.trim().isEmpty()) {
            return linuxSo.trim();
        }
        return System.getProperty("user.dir") + "/iot-video-sdk/hik/linux-lib/libhcnetsdk.so";
    }
}
