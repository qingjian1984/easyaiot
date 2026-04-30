package com.basiclab.iot.video.dahua.sdk;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * 大华 NetSDK 最小映射：仅保留设备登录与设备信息读取所需接口/结构体。
 */
public interface DahuaNetSdkLite extends Library {

    DahuaNetSdkLite INSTANCE = Native.load("dhnetsdk", DahuaNetSdkLite.class);

    interface fDisConnect extends Callback {
        void invoke(LLong lLoginID, String pchDVRIP, int nDVRPort, Pointer dwUser);
    }

    final class LLong extends com.sun.jna.IntegerType {
        public LLong() {
            this(0);
        }

        public LLong(long value) {
            super(Native.LONG_SIZE, value, true);
        }
    }

    final class NET_PARAM extends Structure {
        public int nConnectTime;
        public int nGetConnInfoTime;
        public int nGetDevInfoTime;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("nConnectTime", "nGetConnInfoTime", "nGetDevInfoTime");
        }
    }

    final class NET_DEVICEINFO_Ex extends Structure {
        public byte[] sSerialNumber = new byte[48];
        public byte byAlarmInPortNum;
        public byte byAlarmOutPortNum;
        public byte byDiskNum;
        public byte byDVRType;
        public byte byChanNum;
        public byte[] byLimitLoginTime = new byte[2];
        public byte byLeftLogTimes;
        public byte byLockLeftTime;
        public byte[] Reserved = new byte[24];

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "sSerialNumber", "byAlarmInPortNum", "byAlarmOutPortNum", "byDiskNum",
                    "byDVRType", "byChanNum", "byLimitLoginTime", "byLeftLogTimes",
                    "byLockLeftTime", "Reserved");
        }
    }

    final class NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY extends Structure {
        public byte[] szIP = new byte[64];
        public int nPort;
        public byte[] szUserName = new byte[64];
        public byte[] szPassword = new byte[64];
        public int emSpecCap;
        public Pointer pCapParam;
        public int emTLSCap;
        public Pointer pProxyType;
        public Pointer pProxyParam;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "szIP", "nPort", "szUserName", "szPassword",
                    "emSpecCap", "pCapParam", "emTLSCap", "pProxyType", "pProxyParam");
        }
    }

    final class NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY extends Structure {
        public NET_DEVICEINFO_Ex stuDeviceInfo;
        public int nError;
        public int[] dwSurplusLockTime = new int[2];
        public int[] dwRemainNum = new int[2];
        public byte byPwdResetWay;
        public byte byReserved;
        public short wReserved;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "stuDeviceInfo", "nError", "dwSurplusLockTime", "dwRemainNum",
                    "byPwdResetWay", "byReserved", "wReserved");
        }
    }

    boolean CLIENT_Init(fDisConnect cbDisConnect, Pointer dwUser);

    void CLIENT_SetNetworkParam(NET_PARAM pNetParam);

    LLong CLIENT_LoginWithHighLevelSecurity(
            NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY pInParam,
            NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY pOutParam
    );

    int CLIENT_GetLastError();

    boolean CLIENT_Logout(LLong lLoginID);
}
