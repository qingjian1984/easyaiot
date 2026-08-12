# ADR-011：Capability Manifest 规范

> 状态：Accepted  
> 日期：2026-07-30  
> 决策范围：standard/full 单一实现与能力治理  
> 影响章节：《平台功能计划》1.2～1.4、《EasyAIoT 项目开发宪法》2.3
> 产品基线：平台功能计划 1.5.0 / 项目开发宪法 1.6.0

## 决策

建立一份版本化 capability manifest 作为部署、后端授权、前端展示和 CI 校验的共同来源。仓库规范文件位于：

```text
.scripts/docker/capabilities/
├── capability.schema.json
├── electric-standard.json
└── electric-full.json
```

最小结构：

```json
{
  "schemaVersion": "1.0",
  "profile": "standard",
  "product": "power-operations",
  "capabilities": {
    "power.device.monitor": {
      "enabled": true,
      "quota": { "maxSites": 1 },
      "requires": ["iot-device", "iot-sink", "postgresql", "emqx"]
    },
    "power.scada.editor": {
      "enabled": false,
      "reason": "FULL_ONLY"
    }
  }
}
```

## 生成与消费

- JSON 文件是产品基线；安装器校验 schema 与依赖后，将生效结果写入部署配置并保存内容哈希。
- `iot-system` 提供只读 `/system/capabilities` API，返回当前生效能力、配额、原因、manifest 版本和哈希。
- 后端通过统一 `CapabilityService`/授权注解实施能力门禁；前端只用 API 结果控制菜单和交互，不能把隐藏按钮当授权。
- full manifest 必须是 standard 的严格超集；共有能力编码和语义完全一致，只允许提高配额或增加依赖。
- capability 编码创建后不可改语义；废弃经过兼容期，重命名需新编码和映射。

### 编码框架

首批能力按稳定业务域冻结命名框架，具体配额仍由 manifest 管理：

| 域 | 基础/共有能力示例 | full 扩展能力示例 |
|---|---|---|
| device | `power.device.model`、`power.device.collector`、`power.telemetry.history` | `power.telemetry.scale` |
| alarm | `power.alarm.core` | `power.alarm.advanced` |
| control | `power.control.safe` | `power.control.advanced` |
| video | `power.video.basic` | `power.video.evidence` |
| scada | `power.scada.view` | `power.scada.editor` |
| maintenance/mobile | `power.maintenance.core`、`power.mobile.core` | `power.maintenance.advanced`、`power.mobile.advanced` |
| energy/report | `power.energy.metering`、`power.report.preset` | `power.energy.advanced`、`power.report.advanced` |

Spec/TD 可以在域内新增更细编码，但不得用版本名、菜单名或租户名作为业务能力语义；新增编码必须同时更新 schema、两档 manifest、依赖、门禁和测试。`power.alarm.core` 等安全/基础能力在存在依赖或活动状态时不得直接关闭。

## CI 门禁

- 两份 manifest 必须通过 JSON Schema。
- CI 计算并验证 `enabled(standard) ⊂ enabled(full)`。
- 每个启用能力的 `requires` 必须存在于目标 Compose/安装清单；每个 full-only 菜单、路由、API 和消费者必须声明 capability 编码。
- 扫描禁止散落的档位业务判断；基础设施启动脚本可以读取 `profile`，业务代码必须查询 `CapabilityService`。CI 使用版本化规则清单扫描 Java/Vue/Python/脚本中的 profile 比较、环境变量和路由/API 声明；生成的 capability 常量与注解/路由元数据做结构化校验，文本扫描只作补充。允许项必须进入带责任人和到期日的白名单。
- manifest、权限点和开放 API 生成一致性报告；不一致阻断合并。

## 变更与灰度

- 修改能力启用状态、依赖或降低配额属于发布变更，必须评审并生成差异清单。
- 灰度只能在租户/站点白名单上收窄已部署能力，不得绕过许可证、权限或安全门禁。
- 灰度配置保存在 Nacos/部署配置中心，包含变更单、审批人、开始/结束时间和回滚值；禁止以前端本地存储控制。到达结束时间自动原子恢复上一生效值并验证健康，失败必须告警；人工转正式必须在到期前重新审批，不能用延长灰度代替发布。
- 涉及数据模型或后台任务的能力采用 `ENABLED → DRAINING → DISABLED_READ_ONLY` 停用流程：先阻止新任务/新配置，排空或受控取消后台作业，保留既有数据只读和审计，不因关闭 capability 删除业务事实。基础/安全能力在存在依赖或活动状态时拒绝停用。
- 关闭 `power.alarm.core` 不得中断 ACTIVE/IGNORED 告警的规则评估、到期恢复、升级和审计；必须先解除遥测/规则依赖并把活动告警处置到允许状态。关闭能源等可选能力时，抄表任务停止新增，运行中任务排空或受控取消，冻结值与聚合数据保持只读。

## 回滚

保留上一份已验证 manifest 和哈希。新 manifest 依赖检查、启动或健康验证失败时整体回退，不允许前后端分别回退造成能力漂移。
