# TD-005 tenant 123 隔离模板 Canary 资产

> 状态：Review Candidate / 未执行
> 适用：`full` 档位、tenant 123
> 禁止：产品绑定、设备写入、binding API、3903～3906 权限

运行准备状态（2026-08-11）：HMAC Secret 注入、tenant 123 / role 112 的 3900～3902 最小增量授权和
template API 启用已分别通过独立窗口完成；binding API 保持关闭，尚未批准或执行任何 Canary 请求。
公共网关 `/api/v1/power/**` 原样转发路由已通过独立首次部署窗口上线且网关 healthy；候选 user 132 已通过
认证-only harness 验收，当前仅有一组短时浏览器会话元数据。独立 Canary 写入批准未关闭前，不得进入
Canary 写请求。

认证-only harness 已在独立 Chrome CDP 窗口完成 tenant 123 / user 132 单次认证，未读取或导出 Token，
未调用业务 API；该认证不构成 Canary 写入批准。

该目录冻结未来独立 Canary 窗口唯一允许的三个请求体：模板 identity、1.0.0 草稿和发布原因。模板
`canary-meter-123` 只有一个只读电压测点，无事件、无服务、无产品或设备引用。

执行顺序固定为：

1. `POST /api/v1/power/model-templates` 创建 identity；
2. `POST /api/v1/power/model-templates/canary-meter-123/drafts` 创建草稿并保存响应 `draftId` 与强 ETag；
3. `POST /api/v1/power/model-templates/canary-meter-123/drafts/{draftId}:validate`，必须 `valid=true`；
4. 取得独立“单次 Canary 写入”批准后，使用原 ETag 调用字面量冒号路由
   `POST /api/v1/power/model-templates/canary-meter-123/drafts/{draftId}:publish`。

每个写请求使用窗口内新生成且互不相同的 `Idempotency-Key`；发布另带窗口内新生成的 `X-Request-Id`。
这些运行标识不得预写入资产。tenant、actor、draftId、ETag、traceId 和服务端 ID 均不得由资产伪造。

`manifest.json` 冻结三个请求文件及生产 Schema 的逐字节 SHA-256。tenant 123 资产重定向后暂标
`gitCommit=UNCOMMITTED`；必须先形成实际资产基准提交并回填提交号，才能申请运行窗口。提交号只关闭资产
可追溯门禁，不替代任何运行批准。

形成或验证本目录不授权调用 API，也不授权角色、Secret 注入、容器重建、API 开启或数据库写入。
