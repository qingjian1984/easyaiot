package com.basiclab.iot.video;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 视频接入服务：通过海康 HCNetSDK / 大华 NetSDK 获取设备注册信息。
 */
@SpringBootApplication(
        scanBasePackages = {"com.basiclab.iot.video"})
public class IotVideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotVideoApplication.class, args);
    }
}
