# TD-005 iot-device 模板 API 镜像修复前检（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 状态：`READY_FOR_INDEPENDENT_DEPLOY_APPROVAL / RUNTIME_UNCHANGED`

## 1. 旧产物根因

- `DEVICE/target/jars/iot-device-biz.jar` 原 SHA-256 为
  `49469f2c7a19e5797a58d61ce4c38dd7203769808358c8e4bfb07853666d5c04`；
- 旧暂存 JAR 缺少 `PowerModelTemplateController`、`PowerModelTemplateIdentityService`、
  `PowerModelTemplateDraftService`、`PowerModelTemplatePublishService` 四个必需类；
- iot-device Dockerfile 直接复制该暂存 JAR，因此此前只重建容器仍部署旧代码。

## 2. Java 17 构建修正

- 仓库 POM 明确声明 source/target=17，但本机 Maven 用户 settings 默认 profile 把有效值覆盖为 1.8；首次构建
  因源码文本块在 source 8 下不可编译而失败；
- 未修改用户 settings 或仓库 POM；构建命令显式传入 `maven.compiler.source=17`、
  `maven.compiler.target=17`，反应堆 33/33 `BUILD SUCCESS`；
- 新候选 JAR：`DEVICE/iot-device/iot-device-biz/target/iot-device-biz.jar`，279,652,991 字节，SHA-256
  `953cc5d958a7ecfb6bcf56d9bfcdd441352dc608a8ab07848df9051e4e0346d6`；
- 候选已按相同 hash 暂存到 `DEVICE/target/jars/iot-device-biz.jar`，尚未构建 Docker 镜像。

## 3. 类与合同验证

- 候选 JAR 内四个模板 Controller/Service 类全部存在；
- `PowerModelTemplateControllerContractTest` + `PowerModelCanaryAssetContractTest`：11/11 PASS、0 skipped；
- Controller 合同映射 identity、create/replace draft、validate、publish 五条冻结路由；未认证 identity 合同返回
  401 而非 404。

## 4. 未执行与下一门禁

- 未构建或覆盖 Docker 镜像，未重建容器，未调用 API，未修改数据库、Secret、角色、Topic、Nacos 或
  capability，未写 Canary；
- 下一步须 owner 独立批准：保留旧镜像回退标签，仅用 hash 为 `953cc5d9…0346d6` 的暂存 JAR 构建新
  `iot-module-device-biz:latest`，仅重建 iot-device，保留现有仓库外 Secret，维持 template=true、binding=false；
- 部署后验收运行 JAR 四类存在、容器 healthy、其他容器未变化、双库只读前检 PASS，并只允许一次无 Token/
  空请求体 identity 路由探针，必须返回 401/403 且不得为 404。失败时仅回退 iot-device。
