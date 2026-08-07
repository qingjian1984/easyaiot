# 与 2026-08-05 冻结基线的逐项差异（2026-08-07 重跑）

对比方式：`rerun_profile_2026_08_07.py` 解析当日原始输出 → jsonschema 1.1.0 校验 →
与 `target-schema-profile-result.json`（2026-08-05 基线）递归逐项 diff，仅豁免 `verifiedDate`。

## 结论

```
schema validation: PASS (1.1.0)
baseline diff: IDENTICAL (excluding verifiedDate)
blocking conditions: NONE triggered
```

**零差异**：12 表角色划分、行数、列签名、主键/唯一/外键/检查约束/触发器/索引计数、
重复组、标识符作用域异常、孤儿计数、关系异常、孤儿属性明细，全部与基线逐项一致。

## 阻断项核对（Runbook §3.4）

| 阻断项 | 实测 | 判定 |
|---|---:|---|
| duplicate group count > 0 | 0 | 未触发 |
| identifier scope anomaly > 0 | 0 | 未触发 |
| orphan count > 0 | 0 | 未触发 |
| relationship mismatch > 0 | 0 | 未触发 |
| service_id 形状冲突 | false | 未触发 |
| tenant_id 缺失 | 无 | 未触发 |

## 常驻门禁说明（非本次重跑差异）

管道输出 `gate remains: OPEN_REMEDIATION_REQUIRED`：指唯一约束/外键/触发器基线尚未在目标库建成
（即 TD-005 迁移待执行事项），为基线既有状态，不属于本次重跑新引入的差异，不构成 BLOCK。

## 漂移监视

本次执行未发现画像范围外的新表/新约束（Runbook §4 异常项未触发）。
iot-device20 上 power_* 表为 0 张，与签字包 §3.4 只读项核对一致。
