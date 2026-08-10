# TD-004 V006 核心归属 Schema 架构与 DBA 专项评审报告

> 日期：2026-08-10
> 评审对象：TD-004 1.0.2 / ADR-013 1.5.3 / V006/U005
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 结论：**通过整改后接受（Schema Candidate Accepted）**
> 注意：本结论只接受迁移候选，不批准目标实例执行，也不表示 TD-004 功能上线

## 1. 总体结论

V006 以 `iot-device` 为唯一控制面，在同一事务建立站点、空间树、回路树、永久设备资产身份和
当前/历史归属五个事实表，符合 TD-004 §7.1～§7.4。standard/full 共用同一 Schema，mini
不初始化业务数据；未复制 `device` 身份，也没有跨库外键或跨服务直查。

初版发现 2 项 HIGH、3 项 MEDIUM、1 项 LOW。两项 HIGH 已通过数据库 trigger 和合同反例关闭；
其余项明确保留为应用实现/目标窗口门禁。整改后无 Schema 级阻断，可进入目标窗口审批。

## 2. 发现与处置

| ID | 级别 | 发现 | 处置 |
|---|---|---|---|
| H-01 | HIGH | assignment 只靠 partial unique，仍可直接 UPDATE 覆写 `device/site/valid_from`，破坏“关闭旧行、插入新行”的历史事实 | 新增 `fn_power_device_assignment_history_guard`：身份、拓扑、起始时间、原因和创建审计不可变；只允许当前行 `valid_to: NULL→非空` 且 `version+1`，已关闭行不可再次修改 |
| H-02 | HIGH | 站点/空间/回路/资产只有编码不可变，仍可能修改 tenant、site 或 device 身份列 | 用 `fn_power_object_identity_guard` 统一保护主键、tenant、所属站点、device 和已分配 assetCode；assetCode 只允许 `NULL→首个规范值`，赋值后不可改/清空 |
| M-01 | MEDIUM | 设备表没有 `(tenant_id,id)` 复合唯一键，单列 FK 无法独立保证租户一致 | 保留 `device(id)` FK，同时由 asset tenant guard 查询 `device.id+tenant_id`；assignment 再以 `(tenant_id,device_id)` 复合 FK 指向资产，跨租户反例已 PASS |
| M-02 | MEDIUM | 数据库只阻止直接自环，不能证明 64 层以内无间接树环 | TD-004 已冻结递归 CTE + 固定锁序 Service 算法；Controller/Service 未实现前 capability 保持关闭，树循环/深度合同仍为 OPEN，不以 V006 宣称完成 |
| M-03 | MEDIUM | IANA 时区、标准/扩展 objectType、站点 ACTIVE 与权限不能仅由 DDL 完整校验 | 按 TD-004 由 Service + `iot-system-api` + CapabilityService 校验；内部对象快照 API 未实现前 collector 发布链保持 fail-closed |
| L-01 | LOW | U005 未接 runner uninstall，手工卸载后 history 仍保留且 runner 不应自动重建 | 保持 ADR-013 既有安全边界：U005 只作独立空表收缩候选；普通回滚关闭 capability/写入并保留 Schema，真正卸载另行审批 |

## 3. 已核对数据库不变量

- 五表均有 `tenant_id BIGINT NOT NULL`、中文表/列 COMMENT、必要状态/版本/时间 CHECK。
- 站点、空间、回路编码范围与唯一作用域符合 SPEC-001；身份列原地修改被 trigger 拒绝。
- 空间/回路父节点和 assignment 的主空间/主回路使用复合 FK，强制同 tenant/site。
- `power_device_asset` 不复制 `deviceIdentification`；`device` 仍为设备身份唯一事实。
- `(tenant_id,device_id) WHERE valid_to IS NULL` 保证最多一个当前归属。
- assignment 合法变更路径为：锁当前行 → 关闭且 version+1 → 插入新当前行；失败事务整体回滚。
- 五表均为空时 U005 可卸载；任一表非空时在首条 DROP 前拒绝并回滚。

## 4. 最终验证

最终 V006 SHA-256：`6fac9b429aae2fff34483fedc800f5d54bab8154b16953a85b2cf96f85229064`。

第二临时评审库 `td004_v006_review2_20260810`：

- runner 首次 `STEP_DONE V006`，同 hash 重放 `STEP_SKIPPED V006`；
- 跨租户资产、重复当前归属、跨站节点、对象身份修改、assignment 历史覆写、错误 version、
  已关闭行二次修改全部拒绝；合法关闭旧行 + 插入新当前行通过；
- READY 关系闭合恰好一条；fixture 全部事务回滚；
- MIG-009 PASS；U005 非空拒绝、清空后卸载 PASS；
- 临时库已删除；目标 `iot-device20` 无五表且无 V006 history。

## 5. 进入目标窗口前仍须满足

1. owner 明确批准 V006 目标窗口及批准单号；
2. runner `dry-run --step V006` hash 必须等于本报告最终 hash；
3. 执行前自动 pg_dump 备份成功，precheck 与既有 V001～V005 history/hash 无漂移；
4. 目标仅执行 V006，不初始化站点/归属数据，不开启 capability；
5. 执行后五表计数均为 0、MIG-009 PASS、既有 product/device/property 计数不变；
6. 任何前置失败均停止，不绕过 runner 或手工建表。

## 6. Accepted 后 OPEN

- 目标窗口执行；
- 全新安装 dump 与 preflight/导入对账；
- Mapper/Service、站点权限、树循环/深度、`PowerObjectQueryApi` 和真实 PG 合同；
- `requestTimeoutMs/maxRetries/dataPriority/pollGroup` 权威来源；
- 第四个 `CollectorConfigReleasePort` 与发布/投影事务。
