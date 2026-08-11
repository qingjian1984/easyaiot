# TD-005 tenant 122 隔离模板 Canary 资产

> 状态：Review Candidate / 未执行
> 适用：`full` 档位、tenant 122
> 禁止：产品绑定、设备写入、binding API、3903～3906 权限

运行准备状态（2026-08-11）：HMAC Secret 注入、tenant 122 / role 111 的 3900～3902 最小增量授权和
template API 启用已分别通过独立窗口完成；binding API 保持关闭，尚未批准或执行任何 Canary 请求。
公共网关 `/api/v1/power/**` 原样转发路由已通过独立首次部署窗口上线且网关 healthy；候选用户 113 当前
无活动访问令牌，本机尚无 WEB 镜像/容器和 8888 登录面。WEB 首次部署、用户正常登录和独立 Canary
写入批准三项未关闭前，不得进入 Canary 写请求。

该目录冻结未来独立 Canary 窗口唯一允许的三个请求体：模板 identity、1.0.0 草稿和发布原因。模板
`canary-meter-122` 只有一个只读电压测点，无事件、无服务、无产品或设备引用。

执行顺序固定为：

1. `POST /api/v1/power/model-templates` 创建 identity；
2. `POST /api/v1/power/model-templates/canary-meter-122/drafts` 创建草稿并保存响应 `draftId` 与强 ETag；
3. `POST /api/v1/power/model-templates/canary-meter-122/drafts/{draftId}:validate`，必须 `valid=true`；
4. 取得独立“单次 Canary 写入”批准后，使用原 ETag 调用字面量冒号路由
   `POST /api/v1/power/model-templates/canary-meter-122/drafts/{draftId}:publish`。

每个写请求使用窗口内新生成且互不相同的 `Idempotency-Key`；发布另带窗口内新生成的 `X-Request-Id`。
这些运行标识不得预写入资产。tenant、actor、draftId、ETag、traceId 和服务端 ID 均不得由资产伪造。

`manifest.json` 冻结三个请求文件及生产 Schema 的逐字节 SHA-256，并指向资产基准提交
`af41b51517bee12e36a50c75b6009e96d76f4dea`。该提交号只关闭资产可追溯门禁，不替代任何运行批准。

形成或验证本目录不授权调用 API，也不授权角色、Secret 注入、容器重建、API 开启或数据库写入。
