# TD-005 模板 Canary 单次写入尝试证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 授权：`USER-APPROVAL-20260811-TD005-CANARY-TEMPLATE-SINGLE-WRITE`
> 结论：`STOPPED_AT_IDENTITY_404 / NO_CANARY_FACT / DEPLOYED_JAR_MISSING_TEMPLATE_API`

## 1. 前置门禁与备份

- 冻结提交 `1ec8e801d33436b7d176709c45c115faefe3b41c` 存在；identity、draft、publish 与生产 Schema
  SHA-256 分别为 `db379af5…67c3`、`bb2090f6…2824`、`beeb9544…2413`、`2431b8e7…bae5`，与 manifest 一致；
- access ID 6118 在执行前为 tenant 123 / user 132 / deleted=0 / 未过期，未查询 Token 字段；
- 双库 READ ONLY 前检 PASS 并 ROLLBACK：权限 3/0、十四类业务事实残留 0；
- `iot-device20` 仓库外 custom-format 备份完成：447,615 字节，SHA-256
  `724464e69591555a52671a86e209db31bd69ec6650a0797b9dd90eb06ac99ac6`，TOC 1,346 行；文件位于
  `D:\working\laoluopro\workspace\easyaiot-backups\td005-canary-template-single-write-20260811\iot-device20_20260811_2139_pre_canary.dump`；
- 容器与宿主机 hash 一致；复制完成后仅删除容器内精确临时 dump，仓库外备份保留。

## 2. 执行与停止

- 首次本地执行脚本因 Windows PowerShell 5 无 BOM 中文路径解码失败，在读取冻结资产阶段停止；未导航浏览器、
  未调用 API、未生成或复用幂等键；
- 修正为 ASCII 文件名唯一发现后，独立 Chrome 仅在页面内存中使用既有会话，未输出、导出、持久化或刷新 Token；
- 首个 `POST http://localhost:48080/api/v1/power/model-templates` 返回 HTTP 404、无业务 code；
- 按批准停止条件立即关闭窗口，未调用 draft、validate、publish 或其他 API，未重试 identity。

## 3. 失败后收敛与根因

- 双库 READ ONLY 前检再次 PASS 并 ROLLBACK：权限 3/0、十四类业务事实残留仍为 0；
- `iot-gateway` 和 `iot-device` 均 healthy；网关运行 JAR 确实包含 `/api/v1/power/**` 原样路由；
- `iot-device` 的 template 开关环境值为 true，但运行 JAR 只包含既有
  `PowerModelBindingController` / `PowerObjectQueryController`，不包含 `PowerModelTemplateController` 或模板服务类；
- 因模板 Controller 未进入运行镜像，开关无法注册路由，identity 由运行端返回 404。该事实说明此前“template API
  已激活”仅验证了配置与容器健康，未验证运行 JAR 类存在性和真实路由可达性。

## 4. 后续门禁

当前 Canary 写入仍为 0。下一步必须先形成独立 iot-device 镜像修复部署窗口：构建包含模板 Controller/Service 的
当前源码，仅重建 iot-device，保留仓库外 Secret，template=true、binding=false，并增加运行 JAR 类存在性与
未认证路由非 404 的只读验收。部署完成后需要新的认证窗口和新的单次 Canary 批准；本次批准不得复用。
