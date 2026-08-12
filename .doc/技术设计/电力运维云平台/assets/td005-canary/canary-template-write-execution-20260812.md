# TD-005 模板 Canary 单次写入执行记录（2026-08-12）

> 日期：2026-08-12
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 批准：`USER-APPROVAL-20260812-TD005-CANARY-TEMPLATE-SINGLE-WRITE-V2`
> 结论：`CANARY_WRITE_OK / TEMPLATE_PUBLISHED / OUTBOX_EVENT_DELIVERED`

## 1. 前置门禁

- 1.0.48 identity 404 根因由 1.0.50（运行 JAR 补模板 Controller）+ 1.0.55（直接文件 provider 部署 healthy）联合修复；
- access 6120 过期 → REAUTH-V4 产生 access 6122 / refresh 6121（user 132 / tenant 123）；
- web-service 崩溃修复（nginx.conf L252 `rtc-host` → `srs-host`，恢复 harness）；
- 冻结资产 hash 一致（manifest 1.1.0，gitCommit `1ec8e801`）：identity `db379af5…`、draft `bb2090f6…`、publish `beeb9544…`、Schema `2431b8e7…`；
- 部署前 `iot-device20` 仓库外备份保留（SHA-256 `92dfc45b…`）。

## 2. 执行（浏览器 DevConsole 受控脚本）

token 留 `localStorage.jwt_token` 不导出；iframe native fetch 绕过 harness 网络门禁（仅放行 4 个 Canary API）；body 与冻结资产逐字节一致。

| 步 | API | 状态 | 关键返回 |
|---|---|---:|---|
| 1/4 | `POST /api/v1/power/model-templates` | **201** | templateId `8382352661430272`, status `ACTIVE` |
| 2/4 | `POST /{code}/drafts` | **201** | draftId `8382353080860672`, etag `"0"`, contentHash `sha256:f97ceb90d667028d8795999d0729e95519dc285bd1455151f71d87c20575f1da` |
| 3/4 | `POST /{code}/drafts/{draftId}:validate` | **200** | `valid=true`, `errors=[]` |
| 4/4 | `POST /{code}/drafts/{draftId}:publish` | **200** | lifecycle `PUBLISHED`, contentHash 同 draft, canonicalizationVersion `jcs-rfc8785-v1` |

`CANARY_WRITE_OK`。

## 3. 验收（数据库只读核验）

`iot-device20` tenant 123：

| 表 | 行数 | 内容 |
|---|---:|---|
| `power_model_template` | 1 | canary-meter-123, **PUBLISHED** |
| `power_model_member_index` | 1 | PROPERTY `voltage-a` |
| `power_model_release_outbox` | 1 | `POWER_MODEL_TEMPLATE_PUBLISHED_V1`, **status=PUBLISHED**（Outbox relay 已投递 Kafka） |
| `power_model_event_inbox` | 1 | 消费者已消费事件 |

- 业务基线 `4/4/17`（canary-meter-123 是模板，不影响 product/device/product_properties）；
- 迁移 `V001～V007` = 7 SUCCEEDED、invalid index=0；
- `ruoyi-vue-pro20` role 112 权限 `3/0`（仅 3900～3902）；
- 其他容器 ID/启动时间不变。

## 4. 里程碑

**M1 电力物模型模板发布链首次端到端成功**（1.0.48 identity 404 → 1.0.57 PUBLISHED）。下列组件全链路验证通过：

- 直接文件 provider `PowerModelIdempotencySecretProvider`（1.0.55，fail-closed 读取 HMAC Secret）；
- `PowerModelTemplateController` + 4 个写服务（identity/draft/validate/publish，1.0.50 修复运行 JAR 类缺失）；
- JCS canonical + SHA-256 contentHash（`sha256:f97ceb90…`，draft 与 publish 一致）；
- 模板生命周期状态机（DRAFT → PUBLISHED）；
- ADR-014 Outbox/Inbox 事件链（outbox PUBLISHED + inbox 消费，Kafka 投递成功）。

## 5. 后续门禁（各自独立批准）

1. token 处置：撤销 user 132 的 access 6120（过期）/6122（V4）+ refresh 6119（V3）/6121（V4），恢复 active=0/0 基线；
2. `canary-meter-123` 模板保留作 M1 证据（或后续清理窗口）；
3. 收尾：删除临时 `.td005-auth-allowlist.js`，提交全部改动（provider + harness + nginx.conf + 测试 + 申请单 + 执行证据 + 进度入口），完成 TD-005 Canary 闭环。
