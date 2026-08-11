# TD-005 Template API 激活窗口执行记录

> 日期：2026-08-11
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> APPROVAL：`USER-APPROVAL-20260811-TD005-TEMPLATE-API-ACTIVATION`

## 执行边界

仅保留仓库外 Config Tree Secret，以独立覆盖层重建 `iot-device` 并设置 template API=true；binding API
固定为 false。禁止调用 API、写 Canary 数据、修改角色、Topic、数据库、Secret 文件或其他容器。

## 前置证据

- PowerShell 语法与 `git diff --check` PASS；
- Java 17 reactor `BUILD SUCCESS`，`PowerModelSecretMountContractTest` 6/6 PASS；
- `READY_ONLY`：Secret 文件、Compose、阶段 2、角色、tenant 空数据全部 PASS，容器 ID/启动时间未变化。

## 执行与验收

1. 执行器再次完成阶段 2 16/16 与双库只读门禁后，仅重建 `iot-device`；
2. 重建后的首次 Kafka 检查处于重新入组，第二次有界检查恢复在线，lag=0；
3. template-api 阶段 16/16 PASS：template=true、binding=false、release/events=true；
4. Config Tree Secret=64 字节，容器明文 Secret=0；
5. V001～V007 7/7，积压 `0/0/0/0`，invalid index=0，业务基线 `4/4/17`；
6. Topic 参数保持 6 分区、复制因子 1、保留 2,592,000,000 ms；
7. role 111 仅关联 3900～3902，3903～3906 为 0；tenant 122 的 14 类事实为 0；
8. 独立只读复核再次 16/16 PASS；新容器启动时间为 `2026-08-11T04:16:42.465825392Z`；
9. 未调用任何模板 API，未写 Canary 数据，自动回退未触发。

结论：**PASS**。下一门禁为单次隔离模板 Canary 写入批准；本批准不得复用于该操作。
