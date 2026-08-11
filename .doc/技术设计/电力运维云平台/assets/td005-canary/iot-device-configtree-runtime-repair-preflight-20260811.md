# TD-005 iot-device Config Tree 运行时修复前检（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> TD 基线：TD-005 1.0.52
> 结论：`SOURCE_FIX_PASS / CANDIDATE_READY / DEPLOYMENT_OPEN`

## 1. 根因修复

- Compose Secret 的 Config Tree 文件名直接映射最终 Spring 属性
  `easyaiot.power-model.idempotency-hmac-secret`；
- 删除中间属性 `easyaiot.power-model.idempotency-hmac-secret-file-content` 的嵌套占位解析；
- `EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET` 仅保留为空值兼容回退；
- 未读取或修改仓库外 Secret。

## 2. 动态合同

新增真实 Spring Config Data 临时目录启动合同：

1. 最终属性名文件存在时，应用环境取得测试专用 Secret；
2. 文件缺失时，最终属性保持空值，由既有 `PowerModelActivationGuard` fail-closed。

聚焦命令显式解除仓库默认 `maven.test.skip=true`，最终结果：16 tests，0 failures，0 errors，0 skipped。

## 3. 候选制品

- Java 17 反应堆：33/33 `BUILD SUCCESS`；
- 暂存 JAR：`DEVICE/target/jars/iot-device-biz.jar`；
- 字节数：279,652,971；
- SHA-256：`54bedaec85bed61f7afe012dcbc5eda933c4e6958c8ade6ca3a6c86e4143b009`；
- JAR 内模板 Controller、Identity/Draft/Publish Service 四类齐全；
- JAR 内 `application.yaml` 命中最终属性兼容回退，不含旧中间属性键。

## 4. 当前边界与下一门禁

本轮未构建 Docker 镜像、未重建容器、未调用 API、未修改数据库；运行 iot-device 仍保持上一窗口回退后的
healthy 旧镜像。下一步必须获得新的仅 iot-device 部署批准，且以本文件候选 JAR 的精确 SHA-256 为输入；
上一窗口批准不得复用。
