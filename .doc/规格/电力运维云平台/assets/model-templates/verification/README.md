# TD-005 Schema 与 JCS 自动验证证据

> 证据日期：2026-08-04  
> 对应设计：TD-005 1.0.4  
> 状态：PASS（资产级验证；生产 Java/TypeScript 集成仍需实现阶段合同测试）

## 1. 验证范围

- Draft 2020-12 Schema 自身合法性；
- 2 个合法模板和 11 个非法变体；
- `jcs-rfc8785-v1` canonical UTF-8 字节；
- canonical 字节的 SHA-256；
- Python RFC8785 与 Node/ECMAScript 两个独立实现一致性；
- release asset manifest 的文件大小和 SHA-256。
- 画像结果 JSON Schema 1.0.0、七表列计数及 tenant 签名一致性；
- 18 个 JSON/Markdown/脚本/SQL 资产严格 UTF-8 解码且无 BOM。

TD-005 目标库画像的孤儿属性处置还提供三份数据库脚本：

- `orphan-properties-precheck.sql`：只读预检；
- `orphan-properties-remediation.sql`：精确删除候选，动态锁定和扫描全部直接产品/模板标识列，以完整行快照失败关闭；默认回滚，只有 `COMMIT_REMEDIATION=true` 才提交；
- `orphan-properties-rollback.sql`：完整快照恢复，同时保护父产品状态；默认回滚，只有 `COMMIT_ROLLBACK=true` 才提交。

修复脚本已在 `postgres-server / iot-device20` 完成默认回滚演练；未执行持久化删除。回滚脚本须在修复已提交且确需事件恢复时演练，当前存在 4 条原记录时会按设计拒绝执行。

## 2. 可复现命令

```bash
cd .doc/规格/电力运维云平台/assets/model-templates/verification
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt
.venv/Scripts/python verify.py
```

Linux/macOS 将最后两行改为：

```bash
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python verify.py
```

运行环境还需提供 Node.js，或通过 `--node /absolute/path/to/node` 指定可执行文件。验证脚本不访问网络、不解析远程 `$ref`，依赖安装完成后可以离线执行。

## 3. 本次结果

```json
{
  "draft": "2020-12",
  "schemaPositive": 2,
  "schemaNegative": 11,
  "jcsImplementations": ["python-rfc8785", "node-ecmascript"],
  "jcsCases": [
    {
      "name": "standard-meter-artifact",
      "sha256": "e34427a38eb4f3821cf79caafa21902cd84cf3a47938445f764a4e6ac002cca1",
      "bytes": 1052
    },
    {
      "name": "unicode-and-numbers",
      "sha256": "948e8a673f920c7acde693ca2a50d1f3ea55d9d7f410f7f780d69be4bf960172",
      "bytes": 134
    }
  ],
  "manifestArtifacts": 4,
  "targetProfileSchema": "1.0.0",
  "utf8NoBomFiles": 15,
  "result": "PASS"
}
```

验证时发现原 `example-standard-meter-1.0.0.json` 的 `eventCode=measurement_abnormal` 违反 code 正则；已修正为 `measurement-abnormal`，并重新计算样例及 manifest 哈希。这个问题证明 fixture 必须由 validator 执行，不能只依靠人工评审宣称“最小合法样例”。

## 4. Fixture 覆盖

| 类型 | 场景 |
|---|---|
| 正例 | STANDARD 最小样例；VENDOR 精确 base |
| 编码 | eventCode 含下划线 |
| 未知字段 | 根对象未知字段；属性内混入 RTU 地址 |
| 条件必填 | FLOAT 缺 precision；ENUM 缺 enumValues；PERIODIC 缺 intervalMs |
| 继承 | VENDOR 缺 base；STANDARD 错带 base |
| 安全 | HIGH_RISK 二次确认不是 true |
| 十进制 | 前导零非法 decimal |
| 基数 | properties 为空 |

`schema-fixtures.json` 使用可审计 JSON Pointer patch 从正式样例生成每个实例，避免复制样例后发生无关字段漂移。

## 5. Golden 说明

`jcs-golden.json` 以 Base64 保存精确 canonical 字节，避免换行符、编辑器编码或 Git 行尾转换污染 golden。两个实现分别为：

- Python：`rfc8785==0.1.4`；
- Node：`jcs_canonicalize.mjs`，按 ECMAScript JSON 序列化和 UTF-16 key 顺序独立实现。

业务样例 golden 覆盖中文和嵌套模板结构；通用 golden 覆盖 Unicode key、emoji、转义、数组和数值序列化。

## 6. 文本编码契约

所有 JSON、Schema、golden manifest、Markdown 和验证脚本固定使用 UTF-8 无 BOM。`verify.py` 对资产目录中的 `.json/.md/.mjs/.py/.sql/.txt` 执行严格 UTF-8 解码和 BOM 拒绝；消费方不得依赖 Windows GBK/活动代码页。终端显示乱码时应先修正终端编码，不得把合法 UTF-8 文件转为本地编码。`.xlsx` 是 OOXML ZIP 二进制，不属于文本编码检查范围。

## 7. 目标数据库画像

已在 `postgres-server / iot-device20` 使用只读事务执行画像脚本 v1.1.0，机器摘要保存为 `target-schema-profile-result.json`，并由 `target-schema-profile-result.schema.json` 1.0.0 校验。画像保存七表完整列签名/tenant 状态，分别检查 product/template 成员作用域重复和标识异常；目标库没有 `product_properties.service_id`、业务唯一约束、外键或触发器，并存在 4 条孤儿属性记录。

正式判定见 [TD-005 目标数据库与现有实现画像报告](../../../../../技术设计/电力运维云平台/TD-005-目标数据库与现有实现画像报告.md)。本结果关闭“本地目标集成实例事实采集”，没有关闭差异修复和生产存量画像。

## 8. 尚未覆盖

- 生产 Java/TypeScript canonical 实现与本 golden 的合同测试；
- 成员 code 跨数组唯一、SemVer 最小增量、CT/PT 变比一致性等 Schema 外语义校验；
- Excel 宏/OLE/外链/ZIP Bomb 等恶意导入 fixture；
- 目标 PostgreSQL 差异修复、生产存量重跑、事务、并发、租户和旧接口回归；
- 行业专家对 10 类模板、71 个属性及 CT/PT 规则的签字。

因此本证据关闭的是 Schema/资产级验证缺口，不代表 TD-005 已开发完成或可转生产发布。
