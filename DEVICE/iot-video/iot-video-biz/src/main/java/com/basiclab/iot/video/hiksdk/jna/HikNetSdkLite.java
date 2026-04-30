package com.basiclab.iot.video.hiksdk.jna;

import com.sun.jna.Library;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

/**
 * 海康 HCNetSDK 最小映射：仅保留登录与设备基础信息读取所需结构。
 */
public interface HikNetSdkLite extends Library {

    final class NET_DVR_USER_LOGIN_INFO extends Structure {
        public byte[] sDeviceAddress = new byte[129];
        public byte byUseTransport;
        public short wPort;
        public byte[] sUserName = new byte[64];
        public byte[] sPassword = new byte[64];
        public Object cbLoginResult;
        public PointerByReference pUser;
        public boolean bUseAsynLogin;
        public byte byProxyType;
        public byte byUseUTCTime;
        public byte byLoginMode;
        public byte byHttps;
        public int iProxyID;
        public byte byVerifyMode;
        public byte[] byRes3 = new byte[119];

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "sDeviceAddress", "byUseTransport", "wPort", "sUserName", "sPassword",
                    "cbLoginResult", "pUser", "bUseAsynLogin", "byProxyType",
                    "byUseUTCTime", "byLoginMode", "byHttps", "iProxyID", "byVerifyMode", "byRes3");
        }
    }

    final class NET_DVR_DEVICEINFO_V30 extends Structure {
        public byte[] sSerialNumber = new byte[48];
        public byte byAlarmInPortNum;
        public byte byAlarmOutPortNum;
        public byte byDiskNum;
        public byte byDVRType;
        public byte byChanNum;
        public byte byStartChan;
        public byte byAudioChanNum;
        public byte byIPChanNum;
        public byte byZeroChanNum;
        public byte byMainProto;
        public byte bySubProto;
        public byte bySupport;
        public byte bySupport1;
        public byte bySupport2;
        public short wDevType;
        public byte bySupport3;
        public byte byMultiStreamProto;
        public byte byStartDChan;
        public byte byStartDTalkChan;
        public byte byHighDChanNum;
        public byte bySupport4;
        public byte byLanguageType;
        public byte[] byRes2 = new byte[9];

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "sSerialNumber", "byAlarmInPortNum", "byAlarmOutPortNum", "byDiskNum",
                    "byDVRType", "byChanNum", "byStartChan", "byAudioChanNum", "byIPChanNum",
                    "byZeroChanNum", "byMainProto", "bySubProto", "bySupport", "bySupport1",
                    "bySupport2", "wDevType", "bySupport3", "byMultiStreamProto",
                    "byStartDChan", "byStartDTalkChan", "byHighDChanNum", "bySupport4",
                    "byLanguageType", "byRes2");
        }
    }

    final class NET_DVR_DEVICEINFO_V40 extends Structure {
        public NET_DVR_DEVICEINFO_V30 struDeviceV30;
        public byte bySupportLock;
        public byte byRetryLoginTime;
        public byte byPasswordLevel;
        public byte byProxyType;
        public int dwSurplusLockTime;
        public byte byCharEncodeType;
        public byte bySupportDev5;
        public byte bySupport;
        public byte byLoginMode;
        public int dwOEMCode;
        public int iResidualValidity;
        public byte byResidualValidity;
        public byte bySingleStartDTalkChan;
        public byte bySingleDTalkChanNums;
        public byte byPassWordResetLevel;
        public byte bySupportStreamEncrypt;
        public byte[] byMarketType = new byte[2];
        public byte byRes2;
        public byte[] byRes = new byte[238];

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "struDeviceV30", "bySupportLock", "byRetryLoginTime", "byPasswordLevel",
                    "byProxyType", "dwSurplusLockTime", "byCharEncodeType", "bySupportDev5",
                    "bySupport", "byLoginMode", "dwOEMCode", "iResidualValidity", "byResidualValidity",
                    "bySingleStartDTalkChan", "bySingleDTalkChanNums", "byPassWordResetLevel",
                    "bySupportStreamEncrypt", "byMarketType", "byRes2", "byRes");
        }
    }

    int NET_DVR_Login_V40(NET_DVR_USER_LOGIN_INFO pLoginInfo, NET_DVR_DEVICEINFO_V40 lpDeviceInfo);

    boolean NET_DVR_Logout(int lUserID);

    boolean NET_DVR_Init();

    int NET_DVR_GetLastError();

}
