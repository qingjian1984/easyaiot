# EasyAIoT 电力运维云平台 SDD 文档索引

> 文档集版本：1.4.1  
> 日期：2026-08-24  
> 上游依据：[《平台功能计划》1.5.0](../../架构设计/平台功能计划.md)、[《EasyAIoT 项目开发宪法》1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)
> 当前阶段：M1 实施持续推进；PRD-02 1.2.2 已基线化，M2-M3 SDD 评审建议已处置但实现仍受门禁约束

## 1. SDD 文档链

```mermaid
flowchart LR
  VISION["产品愿景与边界"] --> PRD["领域 PRD"]
  PRD --> SPEC["Feature Spec\n可测试需求规范"]
  SPEC --> DESIGN["Technical Design\n接口/数据/事件/安全"]
  DESIGN --> TASKS["Implementation Tasks\n可交付任务"]
  TASKS --> TEST["Acceptance Evidence\n测试与验收证据"]
  TEST --> RELEASE["Release Decision\n发布与回滚"]
```

本目录中的 PRD 只定义用户价值、业务边界、业务规则和验收结果。具体字段、API、数据表、Kafka Topic 和代码任务必须在对应 Feature Spec 与 Technical Design 中冻结后开发。

## 2. PRD 清单与建议顺序

| 顺序 | 文档 | 产品域 | 目标里程碑 | 状态 |
|---:|---|---|---|---|
| 0 | [PRD-00 产品总纲](./PRD-00-产品总纲.md) | 全平台 | 全程 | In Review |
| 1 | [PRD-01 站点设备与数据采集](./PRD-01-站点设备与数据采集.md) | 设备与边缘 | M1 | Approved / Baselined |
| 2 | [PRD-02 监控告警与安全控制](./PRD-02-监控告警与安全控制.md) | 安全运行 | M2-M3 | Approved / Baselined |
| 3 | [PRD-03 可视化SCADA与视频联动](./PRD-03-可视化SCADA与视频联动.md) | 展示与视频 | M2 | In Review |
| 4 | [PRD-04 设备档案与运维闭环](./PRD-04-设备档案与运维闭环.md) | 运维管理 | M4 | In Review |
| 5 | [PRD-05 能源管理与电能质量](./PRD-05-能源管理与电能质量.md) | 能源管理 | M5-M6 | In Review |
| 6 | [PRD-06 报表与经营分析](./PRD-06-报表与经营分析.md) | 报告分析 | M5-M6 | In Review |

## 3. Feature Spec 清单

M1 第一批 Spec 已完成评审并冻结，详见 [M1 SPEC 评审冻结记录](../../规格/电力运维云平台/M1-SPEC评审冻结记录.md)：

1. [SPEC-001 电力对象与测点编码规范](../../规格/电力运维云平台/SPEC-001-电力对象与测点编码规范.md) — Approved / Frozen 1.3.0
2. [SPEC-002 电力设备物模型模板](../../规格/电力运维云平台/SPEC-002-电力设备物模型模板.md) — Approved / Frozen 1.3.0
3. [SPEC-003 RS485-Modbus RTU 采集产品化](../../规格/电力运维云平台/SPEC-003-RS485-Modbus-RTU采集产品化.md) — Approved / Frozen 1.4.0
4. [SPEC-004 遥测质量与断点补传](../../规格/电力运维云平台/SPEC-004-遥测质量与断点补传.md) — Approved / Frozen 1.4.0

后续 Spec 按以下顺序推进：

编写时必须遵循 [后续 Feature Spec 编写约束](../../规格/电力运维云平台/后续Feature-Spec编写约束.md)，其中列出的已批准产品规则不得重新作为开放待决策。

5. `SPEC-005 复合告警规则与状态机`
6. `SPEC-006 值班通知与告警升级`
7. `SPEC-007 安全遥控与操作票`
8. `SPEC-008 事故追忆与视频证据`
9. `SPEC-009 电力一次图、站点聚合与点位绑定`
10. `SPEC-010 设备档案与二维码`
11. `SPEC-011 巡检计划与移动执行`
12. `SPEC-012 缺陷与工单闭环`
13. `SPEC-013 能源组织与计量点`
14. `SPEC-014 自动抄表与冻结值`
15. `SPEC-015 分类分项与同比环比`
16. `SPEC-016 最大需量与电价计算`
17. `SPEC-017 电能质量分析`
18. `SPEC-018 综合月报`

## 4. PRD 评审门禁

每份 PRD 进入 Spec 阶段前必须满足：

- 产品目标、用户和非目标明确。
- 用户故事具备可验证结果。
- 正常流程、异常流程和状态边界明确。
- 权限、租户、安全、隐私和审计要求明确。
- `mini` 必须明确排除全部电力能力；`standard` 与 `full` 的能力、配额和依赖必须明确。
- `full` 必须是 `standard` 的严格能力超集，共享功能不得形成重复 API、服务、页面、表或消息契约。
- 功能开关必须引用统一 capability manifest；安全、权限、审计、数据质量和遥控门禁不得按档位降级。
- 与现有功能的复用、修改、新增范围明确。
- 验收标准可以转写为测试用例。
- 依赖项、风险和待决策问题有责任人。
- 每个下游 Spec 必须覆盖成功流程、权限拒绝、租户隔离、重复提交/幂等、并发冲突、外部依赖超时、升级回滚，以及 mini 关闭和 standard/full 同实现验证。

## 5. 状态定义

```text
Draft → In Review → Approved → Specified → Implementing → Released → Retired
```

- `Draft`：内容正在整理，不作为开发承诺。
- `In Review`：产品、业务、架构、安全和运维共同评审。
- `Approved`：范围和验收口径冻结，可编写 Feature Spec。
- `Specified`：Feature Spec 与技术设计通过，可拆开发任务。
- `Implementing`：代码、测试和文档实施中。
- `Released`：通过验收并发布。
- `Retired`：能力下线或被新版本替代。

## 6. 当前产品基线

- 2026-07-31：PRD-01 1.2.0 完成 M1 产品基线化；文档集版本更新为 1.3.1，M1 进入 TD-001～TD-005 Technical Design。
- 2026-08-24：PRD-02 1.2.1 完成 M2-M3 产品基线化；关闭专项评审 M-1～M-4，进入 SPEC-005～008 编写与评审。
- 2026-08-24：PRD-02 1.2.2 关闭 SDD 评审发现的生命周期歧义与直接关闭冲突；[SDD 评审处置记录](../../开发规范/PRD-02-SDD方案设计评审处置记录.md)已落地，SPEC/TD 和生产实现门禁仍保持 OPEN。

- 电力运维仅支持 `standard/full`；`mini` 不建设、不启用、不验收电力功能。
- `standard` 形成采集、存储、监控、告警、处置和基础运维闭环；`full` 在同一实现上增加多站点、SCADA、事故证据、高级能源、完整运维和经营分析。
- standard 时序后端为 PostgreSQL 分区表，full 为 TDengine；业务侧统一依赖 `TelemetryStore`，不得按档位复制查询逻辑。
- 告警统一采用 ADR-010 的单一事实模型；遥控申请、审批、联锁、执行、回执和审计统一归属 `iot-device`。
- 档位门禁与依赖由 ADR-011 capability manifest 统一表达，前端隐藏不能替代服务端授权。
