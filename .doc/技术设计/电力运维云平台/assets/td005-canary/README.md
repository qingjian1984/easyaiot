# TD-005 tenant 122 隔离模板 Canary 资产

> 状态：Review Candidate / 未执行
> 适用：`full` 档位、tenant 122
> 禁止：产品绑定、设备写入、binding API、3903～3906 权限

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

`manifest.json` 冻结三个请求文件及生产 Schema 的逐字节 SHA-256。`gitCommit=UNCOMMITTED` 是明确门禁；
只有代码提交后复算并写入真实 commit，资产才可进入实际 Canary 窗口。

形成或验证本目录不授权调用 API，也不授权角色、Secret 注入、容器重建、API 开启或数据库写入。
