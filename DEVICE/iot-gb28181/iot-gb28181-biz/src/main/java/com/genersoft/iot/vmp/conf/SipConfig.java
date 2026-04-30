package com.genersoft.iot.vmp.conf;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "sip", ignoreInvalidFields = true)
@Order(0)
@Data
public class SipConfig {

	private String ip;

	private String showIp;

	private List<String> monitorIps;

	private Integer port;

	private String domain;

	private String id;

	private String password;

	/**
	 * 是否启用设备REGISTER密码鉴权，默认启用。
	 * 关闭后将跳过401挑战与密码校验，仅用于兼容不支持鉴权的设备。
	 */
	private boolean registerPasswordAuth = true;

	Integer ptzSpeed = 50;

	Integer registerTimeInterval = 120;

	private boolean alarm = false;

	private long timeout = 1000;
}
