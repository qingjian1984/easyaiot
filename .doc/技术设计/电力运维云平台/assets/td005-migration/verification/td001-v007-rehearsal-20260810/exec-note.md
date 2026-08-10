# TD-001 V007 临时评审库验证记录

- 日期：2026-08-10（Asia/Shanghai）
- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
- 目标：一次性临时库 `td001_v007_review_20260810_1350`
- 明确边界：未对 `postgres-server / iot-device20` 执行 V007；V007 未接入 runner
- V007 SHA-256：`6590d6daa33e6e3382f17b1ef1ced0ed854c5322857062617d2b77c621e38685`
- U006 SHA-256：`a96bf988cdcdc938dba3238a99a5a6dedaf35cee5e1a90db5cab103364889bc5`

## 结果

1. Java 17 reactor 依赖编译：`BUILD SUCCESS`。
2. `PowerModelCollectorEventHandlersTest` 强制 clean 重编译：13/13 PASS；覆盖 V1 Schema 十进制字符串 ID、回滚无模板字段、事件 ID/确认人透传及非法确认人终态拒绝。
3. 临时库按 V003 → V004 → V007 首次执行：PASS。
4. `VALIDATED → PUBLISHED` 受控状态推进并保存 `published_by`：PASS。
5. `(tenant_id, workload_id, source_event_id)` 重复候选：数据库唯一约束拒绝，PASS。
6. `binding_revision` 原地修改：不可变触发器拒绝，PASS。
7. 六个新增列中文 COMMENT 缺失数：0；invalid index：0。
8. 非空表重放 V007：`V007_PRECONDITION_RELEASE_NOT_EMPTY`，失败关闭 PASS。
9. U006 非空卸载：`U006_REFUSED_RELEASE_NOT_EMPTY`；删除临时 fixture 后空表卸载成功，六列残留 0。
10. 临时库已删除，存在性查询返回 0。
11. 专项复核收紧后，在第二个一次性临时库按 V001→V002→V003→V004→V007 重验：
    `(tenant_id, product_id, binding_revision)` 绑定复合 FK、`(tenant_id, source_event_id)`
    Outbox 复合 FK、跨修订/未知事件拒绝、原因码二值约束、U006 三项约束卸载均 PASS；临时库已删除。
12. schema-only 目标镜像临时库应用最终 V007 后，`JdbcCollectorConfigReleasePortPostgresIntegrationTest`
    2/2 PASS（静态门禁 1 + PG 合同 1）：默认关闭、VALIDATED→PUBLISHED、canonical/hash/长度复核、发布单+投影同事务 CAS、
    幂等判定、缺失候选终态失败及事务回滚零残留均通过；invalid index=0，临时库已删除。

## 保持 OPEN

- V007 尚未经过专项架构/DBA 评审，未接入 ADR-013 runner，未获得目标窗口授权。
- `JdbcCollectorConfigReleasePort` 已实现但由 `collector-release-port-enabled=false` 默认门禁保持不装配；
  V007 未落库且配置未显式开启时，事件继续按缺失处理器进入 DLQ。
- 下一步必须取得 V007 独立目标窗口授权；不得因实现和临时库合同通过而直接执行或启用。
