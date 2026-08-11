# TD-005 iot-device Config Tree 运行时修复部署尝试（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 授权：`USER-APPROVAL-20260811-TD005-IOT-DEVICE-CONFIGTREE-RUNTIME-REPAIR-DEPLOY`
> 结论：`DEPLOY_FAILED / ROLLBACK_PASS / DIRECT_FILE_PROVIDER_REQUIRED`

## 1. 部署前门禁

- 暂存 JAR SHA-256 精确匹配
  `54bedaec85bed61f7afe012dcbc5eda933c4e6958c8ade6ca3a6c86e4143b009`；
- 仓库外 Secret 只读形状检查全部 PASS：绝对路径、仓库外普通文件、严格 UTF-8、无 BOM/换行、至少 32
  字节、无宽泛读取 ACL；未输出或记录 Secret 路径、内容、摘要或字节样本；
- 当前非敏感运行开关保持 full、release=true、events=true、template=true、binding=false，明文环境长度 0；
- 部署前双库 READ ONLY 前检 PASS 并 ROLLBACK：权限 3/0、tenant 123 十四类 Canary 事实残留 0；
- 旧镜像 `4fa86930…705b` 保留专用回退标签，并补旧镜像专用 Config Tree 键覆盖层。

## 2. 执行与失败

- 仅使用批准 JAR 构建 `iot-module-device-biz`，候选镜像
  `sha256:3e0ff7d44df65891c53ae9e37cc719703c838b8703f81eb8213f2fcc9683bbdb`；
- 仅以 `--no-deps --force-recreate` 重建 iot-device；
- 新容器未在 150 秒有界窗口达到 healthy，共享日志再次显示
  `POWER_MODEL_IDEMPOTENCY_SECRET_INVALID: write API requires at least 32 UTF-8 bytes`；
- 因健康门禁失败，未执行运行类检查、activation preflight、无 Token identity 路由探针或任何业务 API；
- 真实仓库多文档 `application.yaml` + 临时 Config Tree 动态合同另行补测 3/3 PASS，说明简化 YAML 并非唯一
  缺口；实际容器中的 Config Tree/属性源差异仍未关闭，不能继续复用 Spring 属性间接注入。

## 3. 回退与最终状态

- 自动恢复旧镜像 `4fa86930…705b`，使用旧键覆盖层仅重建 iot-device；
- 当前 iot-device 容器 `805b39aa4917…` running/healthy、restartCount=0；
- 其他容器 ID、镜像与 StartedAt 全部未变化；
- 回退后双库 READ ONLY 验收再次 PASS 并 ROLLBACK：权限 3/0、Canary 残留 0；
- 未修改数据库、角色、Topic、Nacos、capability 或 Secret，未打开浏览器、登录、获取 Token 或写 Canary；
- 新候选与此前两个候选共三个 4.18 GB 无标签镜像保留，未越权删除。

## 4. 下一门禁

下一步不得再次直接部署同一候选。应改为应用内 fail-closed 直接文件 provider：只接收非敏感文件路径，启动时
严格读取挂载文件并执行 UTF-8/BOM/换行/长度校验，写服务与 ActivationGuard 统一注入 provider；环境明文
继续为空。完成 provider 单元合同、真实临时文件合同及候选构建后，再申请新的仅 iot-device 部署窗口。
