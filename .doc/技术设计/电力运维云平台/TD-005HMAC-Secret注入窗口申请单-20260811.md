# TD-005 HMAC Secret 注入窗口申请单（2026-08-11）

> 状态：OPEN / 待 owner 独立批准
> 目标：`iot-device` 运行容器
> 双基线：平台功能计划 1.4.0；EasyAIoT 项目开发宪法 1.5.0
> 依据：TD-005 1.0.28

## 1. 申请范围

仅允许把 owner 提供的仓库外 HMAC secret 文件通过
`DEVICE/docker-compose.power-model-secret.yml` 挂载到 `iot-device` 的 Spring Config Tree，并为使配置生效
重建该单一服务容器。secret 文件必须使用绝对路径、无 BOM、无换行且不少于 32 UTF-8 字节。

本窗口必须保持以下事实不变：

- `EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED=false`；
- `EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED=false`；
- 不授权任何角色，不创建模板、产品、绑定或其他 Canary 数据；
- 不修改 PostgreSQL、Kafka Topic、capability manifest、Nacos 配置或其他服务；
- 不在日志、命令回显、文档、Git、容器环境或验收记录中输出 secret 值或摘要。

## 2. 执行前门禁

1. owner 明确批准本申请单范围，并单独提供仓库外文件绝对路径；普通“继续”不构成批准。
2. 文件不位于仓库目录，不通过 `.env`、Nacos 或直接环境变量保存；主机 ACL 仅允许运维主体读取。
3. 先执行 `.scripts/docker/power_model_secret_file_preflight.ps1 -SecretFile <仓库外绝对路径>`；该只读脚本
   必须确认普通文件、非 reparse point、仓库外、严格 UTF-8、无 BOM/换行/NUL、至少 32 字节且无宽泛读取
   ACL。只记录门禁结果，不记录路径、内容或哈希。
4. 当前容器健康，template API 与 binding API 均为 false；阶段 2 的 release/events 事实不在本窗口修改。
5. `docker compose ... config --quiet` 与静态契约测试保持 PASS。

### 2.1 已冻结的窗口前只读基线

2026-08-11 使用精确参数执行阶段 2 全量只读预检：

```powershell
.scripts/docker/power_model_activation_preflight.ps1 `
  -Stage events `
  -ExpectedProfile full `
  -ExpectedPartitions 6 `
  -ExpectedReplicationFactor 1 `
  -ExpectedRetentionMs 2592000000
```

16 项检查全部 PASS：PostgreSQL、Kafka、`iot-device` 均 healthy；运行档位为 `full`；template API=false、
binding API=false、release port=true、events=true；V001～V007 为 7/7 SUCCEEDED 且 V007 hash 匹配；
Outbox/Inbox/release/projection=`0/0/0/0`，invalid index=0，业务基线=`4/4/17`；主 Topic 与 DLQ 均为
6 分区、复制因子 1、`retention.ms=2592000000`，消费组 6 分区在线且 lag=0。

批准执行时必须在容器重建前后使用同一组参数各运行一次。任何一项非 PASS 均停止窗口或触发回退；
本窗口不授权修改这些冻结参数。

## 3. 执行与验收

批准后使用基础 Compose 与 secret 覆盖层重建且仅重建 `iot-device`。验收必须确认：

- 容器恢复 healthy；
- `/run/secrets/easyaiot.power-model.idempotency-hmac-secret-file-content` 存在且字节数不少于 32；
- 容器环境中的兼容 secret 为空，不含明文；
- 激活预检仍判定 template API=false、binding API=false；
- PostgreSQL 业务基线和 Canary 租户 14 类事实未变化；
- 未出现模板写请求、Outbox 新增或 Kafka DLQ 增量。
- 上述 §2.1 的 16 项阶段 2 预检在重建后仍全部 PASS。

验收记录只能写 `configured=true`、来源和字节门限结果，不得写值或摘要。

冻结执行入口为 `.scripts/docker/power_model_secret_injection_window.ps1`。默认不带 `-Execute` 时只运行
文件、Compose 与阶段 2 基线检查并返回 `READY_ONLY`；实际执行必须同时提供 `-Execute` 和精确批准令牌
`USER-APPROVAL-20260811-TD005-HMAC-SECRET-INJECTION`。脚本仅对 `iot-device` 使用
`--no-deps --force-recreate`，等待 healthy 后检查挂载字节门限、明文环境变量为 0，并复跑 §2.1；任一失败
自动用基础 Compose 仅重建 `iot-device` 并复验阶段 2。形成脚本不代表窗口已获批准。

2026-08-11 已用仓库外 48 字节严格 UTF-8、收紧 ACL 的临时假数据运行不带 `-Execute` 的完整路径：
文件门禁全部 PASS、阶段 2 预检 16/16 PASS、结果为 `READY_ONLY execute=false runtimeChanged=false`，且
执行前后 `iot-device` 容器 ID 精确一致。临时文件已清理；没有运行批准令牌或变更路径。

## 4. 回退

若容器不能恢复健康、挂载缺失、环境元数据出现明文或任一不变量漂移，立即使用原基础 Compose 重建
`iot-device`，确认两个 API 仍为 false、服务恢复 healthy，并由 owner 在主机侧安全处置仓库外 secret 文件。
本申请不授权 Codex 删除 owner 提供的仓库外文件。

## 5. 后续独立门禁

本窗口完成也不等于 API 激活批准。Canary 角色授权、隔离模板事实创建、template API 开启、单次 Canary
写入及 binding API 开启仍是相互独立的 OPEN 门禁，必须分别申请和批准。
