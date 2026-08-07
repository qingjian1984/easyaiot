# TD-005 完整 12 表画像生产重跑 Runbook

> 版本：1.0.0　日期：2026-08-07　关联：ADR-013 1.4.2、画像报告 §2 生产库重跑契约、评审报告 §8.4
>
> 目的：将冻结于 `iot-device20`（本地目标集成实例）的 12 表画像证据，按契约在**生产存量库**重跑，
> 关闭 ADR-013 转 Accepted 的 `productionRerunRequired=true` 项。
>
> 安全红线：全程**只读**（`transaction_read_only=on` + 只读角色 + 语句超时）；
> 本 Runbook 不含任何写操作；任何写需求 = 停止并回到评审。

---

## 1. 前置条件（全部满足才可执行）

| # | 条件 | 确认人 |
|---|---|---|
| 1 | 生产变更/只读窗口已批准（审批单号记入证据包） | 运维负责人 |
| 2 | 只读画像角色已创建（独立于 `migration_executor`，仅 CONNECT + SELECT） | DBA |
| 3 | 画像脚本与解析管线版本核对：`target-schema-profile.sql` **v1.2.0**、结果 Schema **1.1.0**（哈希比对，见 §3.1） | 执行人 |
| 4 | 对比基线就位：`target-schema-profile-result.json`（2026-08-05 评审接受基线）+ 2026-08-06 新鲜度重跑结果 | 执行人 |
| 5 | 回退联系人到位（异常时 10 分钟内可终止会话） | 运维 |

## 2. 冻结契约（来自画像报告 §2，逐字约束）

1. 保存**未经裁剪**的原始 psql 输出；
2. 结构化摘要通过结果 Schema 1.1.0 校验；
3. 与评审接受后的 remediation baseline 比较——**含阻断项的本次结果不得当作新基线**；
4. **阻断项**：表/列缺失、列签名变化、tenant 可空、重复/标识异常/孤儿 > 0、与 Accepted ADR-012 冲突、约束基线回退；
5. **告警项**：PostgreSQL 主版本变化、运行表空样本、无迁移说明的行数变化；
6. 证据包必含：环境标识、执行时间、脚本版本、原始输出、结构化 JSON、差异结论。

## 3. 执行步骤

### 3.1 资产版本核对（本地，约 2 分钟）

```bash
cd ".doc/规格/电力运维云平台/assets/model-templates/verification"
sha256sum target-schema-profile.sql target-schema-profile-result.schema.json target-schema-profile-result.json
# 预期与仓库 cfdqiot 当前版本一致；脚本首行须为 "TD-005 target PostgreSQL read-only profile v1.2.0"
```

### 3.2 生产侧只读执行（约 5 分钟）

```bash
# 以只读角色连接生产库；强制只读事务与语句超时
PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=300000" \
  psql "host=<生产主机> dbname=<生产库> user=<只读角色>" \
  -X -v ON_ERROR_STOP=1 \
  -f target-schema-profile.sql \
  > target-schema-profile-raw-YYYYMMDD.txt 2>&1

# 自检：输出须含 PROFILE_VERSION 1.2.0 行；transaction_read_only=on
grep -E "PROFILE_VERSION|transaction_read_only" target-schema-profile-raw-YYYYMMDD.txt
```

### 3.3 解析、校验、对比（本地，约 5 分钟）

以 `rerun_profile_2026_08_06.py` 为模板复制一份当日脚本（改 RAW/OUT 文件名，解析规则不变）：

```bash
cp rerun_profile_2026_08_06.py rerun_profile_YYYYMMDD.py
# 编辑 RAW = 当日原始输出；OUT = 当日结果 JSON
python rerun_profile_YYYYMMDD.py
# 预期：jsonschema 校验 PASS；与基线逐项 diff 输出
```

### 3.4 判定

- 所有阻断项为 0 且与基线一致（或差异均有迁移说明）→ **PASS**，将结果登记 ADR-013 证据表；
- 出现阻断项 → **BLOCK**：停止 ADR-013 转 Accepted 流程，差异项回评审报告开新发现；
- 仅告警项 → **WARN**：PASS 但须附告警处置说明。

### 3.5 证据包归档

```
profile-rerun-prod-YYYYMMDD/
├── env.txt            # 环境标识/审批单号/执行人/执行时间/PG 版本
├── target-schema-profile-raw-YYYYMMDD.txt   # 未裁剪原始输出
├── target-schema-profile-result-YYYYMMDD.json
├── diff-vs-baseline.md  # 逐项差异与迁移说明引用
└── verdict.md           # PASS / WARN / BLOCK 结论与签字
```

归档至 `.doc/规格/电力运维云平台/assets/model-templates/verification/`（原始输出含生产行数等敏感计数时，按团队保密约定选择归档位置并在证据表中登记指针）。

## 4. 异常处置

| 异常 | 处置 |
|---|---|
| psql 连接失败/超时 | 记录错误，不重试超过 3 次；改约窗口 |
| 语句超时（300s）触发 | 记录触发的查询段；**不得**调大超时硬闯，回 DBA 评估执行计划 |
| 输出缺 PROFILE_VERSION 或版本不符 | 停止，核对脚本哈希，禁止手改输出 |
| 发现生产存在画像外的新表/新约束 | 如实记入 diff（不得忽略），回评审报告评估是否约束基线回退 |

## 5. 完成后动作

1. 证据包指针与结论回填 ADR-013 证据表、评审报告 §8 新小节；
2. 本项关闭后，ADR-013/014 转 Accepted 仅余「standard 最低规格压测」一项（另见《TD-005-standard最低规格压测方案》）；
3. 若 PASS：生产库画像成为后续迁移 apply 的新前置基线。
