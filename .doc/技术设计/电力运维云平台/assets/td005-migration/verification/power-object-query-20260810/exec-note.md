# PowerObjectQueryApi 查询闭环验证记录

> 日期：2026-08-10
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> Schema 前置：V006 `6fac9b429aae2fff34483fedc800f5d54bab8154b16953a85b2cf96f85229064` 已受控落库

## 实现范围

- `iot-device-api`：批量 Feign 契约、请求/响应 DTO；请求模型不含 tenantId。
- `iot-device-biz`：内部 provider、`PowerObjectSnapshotMapper`/JDBC 实现、`PowerObjectQueryService`。
- 稳定状态：`READY / NOT_BOUND / INACTIVE`；稳定错误：请求无效、当前 tenant 不存在、作用域歧义、capability 不可用。
- `objectRevision`：服务端 `power-object-revision-v1` 长度前缀 canonical 字段串的 SHA-256。

## 验证结果

1. Java 17 reactor compile：BUILD SUCCESS。
2. `PowerObjectQueryServiceTest`：4/4 PASS，覆盖三状态、请求顺序、未知/重复/歧义失败关闭、capability/tenant 必需和 revision 版本敏感性。
3. `PowerObjectQueryApiContractTest`：2/2 PASS，冻结内部路径、Feign/provider 共用契约及请求 DTO 无 tenantId。
4. `JdbcPowerObjectQueryPostgresIntegrationTest`：2/2 PASS，覆盖同标识跨 tenant 隔离、READY/NOT_BOUND/INACTIVE、跨租户不可见和 V006 hash 前置。
5. PG fixture 全部位于单连接事务并 rollback；验证后五张 V006 表计数均为 0、测试 tenant 设备为 0，原业务 `product/device/product_properties=4/4/17`。

## 诚实边界

- 本批只实现 collector 发布前的内部只读查询，不实现站点普通管理 API、写侧对象 CRUD、存量导入或全量安装 dump。
- capability、菜单、任务和第四个 `CollectorConfigReleasePort` 均未启用/装配；mini 继续 fail-closed。
- Maven 父层仍存在 source 8 漂移，而仓库和既有源码要求 Java 17；本次验证显式指定 source/target 17，未在本批修改父 POM。
- 下一步先冻结四项采集策略权威来源，再实现发布单与 workload 投影同事务写入。
