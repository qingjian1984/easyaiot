# TD-005 iot-device 模板 API 镜像修复部署尝试（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 授权：`USER-APPROVAL-20260811-TD005-IOT-DEVICE-TEMPLATE-API-IMAGE-REPAIR-DEPLOY`
> 结论：`DEPLOY_FAILED / ROLLBACK_PASS / CONFIG_TREE_RUNTIME_GAP`

## 1. 部署输入

- 暂存 JAR SHA-256：`953cc5d958a7ecfb6bcf56d9bfcdd441352dc608a8ab07848df9051e4e0346d6`；
- JAR 内 `PowerModelTemplateController`、Identity/Draft/Publish Service 四类存在；
- 部署前镜像：`sha256:4fa869302238d1b931a4fa7b69a0ceaa417a9500952ba6756ec499e088de705b`；
- 回退标签：`iot-module-device-biz:rollback-td005-template-api-image-repair-predeploy-20260811`；
- 复用既有仓库外 Secret 挂载，未读取 Secret 内容。

## 2. 执行与失败

- 首次脚本调用因 Docker Build 正常进度写 stderr 被 PowerShell `Stop` 误判，在容器重建前停止；`latest` 与
  运行容器均核验仍为旧镜像。修正 native stderr 捕获后在同一批准范围重试；
- 新镜像构建成功且镜像内四类存在，仅以 Compose `--no-deps --force-recreate` 重建 iot-device；
- 新容器未在 150 秒有界窗口内达到 healthy，未执行无 Token 路由探针、双库后验或任何业务 API；
- 共享日志显示新容器多次在 `PowerModelActivationGuard` 启动门禁失败：
  `POWER_MODEL_IDEMPOTENCY_SECRET_INVALID: write API requires at least 32 UTF-8 bytes`；
- 宿主/回退容器只读元数据确认挂载文件存在、可读且为 64 字节，但新代码解析出的
  `easyaiot.power-model.idempotency-hmac-secret` 为空。静态 Config Tree 文件/路径合同未覆盖真实 Spring 属性绑定。

## 3. 回退与收敛

- 脚本把回退标签恢复为 `iot-module-device-biz:latest`，并仅重建 iot-device；
- 当前 iot-device 容器 `70624dc465dc…` 使用旧镜像 `4fa86930…705b`，healthy、restartCount=0；
- postgres-server、iot-gateway、web-service、kafka-server 均 healthy，启动时间未变化；
- 双库 READ ONLY 前检再次 PASS 并 ROLLBACK：权限 3/0、十四类 Canary 业务事实残留 0；
- 未修改数据库、角色、Topic、Nacos、capability 或 Secret，未登录/获取 Token，未调用 API或写 Canary；
- 两个 22:08 构建产生的无标签候选镜像仍保留，未越权删除。

## 4. 下一门禁

下一步先修复并测试 Secret 的真实运行时解析：增加 Config Tree 动态绑定合同，将挂载文件直接映射为最终属性或
使用等价的 fail-closed Secret provider；必须保持明文环境长度 0、仓库外 Secret 不变。完成 Java 合同与隔离
容器启动验证后，再申请新的仅 iot-device 部署窗口；本次部署批准不得复用。

## 5. 源码修复续作（TD-005 1.0.52）

- Compose Config Tree 挂载文件已直接命名为最终属性
  `easyaiot.power-model.idempotency-hmac-secret`，不再依赖中间属性的嵌套占位解析；
- `application.yaml` 保留 `EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET` 空值兼容回退；Config Tree
  作为导入配置直接覆盖该回退；
- 新增真实 Spring Config Data 临时目录启动合同，覆盖挂载存在和挂载缺失两条路径；连同静态挂载合同与
  `PowerModelActivationGuard`，聚焦测试 16/16 PASS；
- 本轮未读取或修改仓库外 Secret，未构建镜像、重建容器、调用 API 或修改数据库；运行态仍保持回退旧镜像；
- Java 17 反应堆 33/33 BUILD SUCCESS；新暂存 JAR 为 279,652,971 字节，SHA-256
  `54bedaec85bed61f7afe012dcbc5eda933c4e6958c8ade6ca3a6c86e4143b009`，模板四类齐全且内置配置不含旧中间键；
- 下一步申请新的仅 iot-device 部署窗口；详见
  [`iot-device-configtree-runtime-repair-preflight-20260811.md`](./iot-device-configtree-runtime-repair-preflight-20260811.md)。
