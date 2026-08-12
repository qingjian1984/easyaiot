# ADR-007：collector 打包与 NODE 管理契约

> 状态：Accepted  
> 日期：2026-07-30  
> 决策范围：M1 站点 collector 交付与生命周期  
> 关联：ADR-001、POWER-SPEC-003  
> 产品基线：平台功能计划 1.5.0 / 项目开发宪法 1.6.0

## 决策

`iot-sink collector` 使用同一 iot-sink 构建产物和独立 Spring Profile 打包为 OCI 容器，由 NODE Agent 通过受控 Docker Compose 工作负载适配器管理。

standard/full 必须使用同一镜像、工作负载规格和生命周期 API；capability manifest 只配置是否启用及资源/规模配额。mini 不部署该电力工作负载。

- profile 名固定为 `collector`；只启用 RTU Poller、配置客户端、SQLite outbox、MQTT client、ACK、指标和健康检查。
- 不创建第二套协议工程或复制 `IotModbusRtuPollingProtocol`。
- 容器镜像使用不可变版本/摘要；部署规格包含 `workloadType=iot-sink-collector`、实例 ID、镜像摘要、配置版本、资源限制、串口设备映射、数据卷和 Broker 引用。
- NODE 只接受结构化白名单字段并生成 Compose 配置，不接受控制面下发任意 shell 命令。
- Linux 串口按明确设备路径逐项映射；Windows 非容器场景属于受控例外，可由 NODE 以固定 Java 命令模板管理服务，但必须保持相同规格、状态和日志契约。

## 生命周期

```text
REQUESTED → PULLING → STARTING → HEALTHY
                         └──────→ FAILED
HEALTHY → UPDATING → HEALTHY | ROLLED_BACK
HEALTHY → STOPPING → STOPPED
```

- NODE 回报镜像版本、配置版本、容器 ID/PID、健康状态、最近错误和串口占用。
- 更新使用先停止旧实例、确认串口释放、再启动新实例的单端口策略；M1 不对同一串口做蓝绿并行。
- 新版本在可配置健康窗口内失败时自动恢复上一镜像和上一配置；SQLite 数据卷不得随容器删除。
- 更新控制器必须记录计划停采窗口及各点位理论应采样区间。经审批的计划维护窗口按 SPEC-004 规则排除；启动失败、超出批准窗口或回滚期间形成的缺失样本写入 `stage=COLLECTOR_DOWNTIME` 的缺口事实并计入完整率，不得用“升级中”掩盖。
- NODE 重启后必须从容器标签和本地状态文件恢复工作负载清单，不只依赖内存字典。

## 资源与权限

- 初始压测候选资源为内存 384 MiB、CPU 1 核，不构成生产硬承诺；生产 request/limit 由 TD-001 按档位冻结。压测必须同时覆盖 ADR-006 的持续/峰值吞吐、24 小时断网补传和配置更新，并记录 JVM heap、metaspace、native memory、线程栈、SQLite page/WAL cache、RSS、GC pause 和 OOM margin；任一安全余量不达标即上调配额或收紧准入，不得带风险发布。
- 容器使用非 root 用户；只授予目标串口、outbox 卷、只读配置和网络出站权限。
- 健康检查分别上报 `process`、`config`、`serial`、`center` 四个 facet：进程死亡或必需串口不可访问为 `FAILED`；新配置应用失败但旧配置仍运行、中心离线且本地队列可写为 `DEGRADED`；中心离线不得触发进程重启。状态聚合规则属于工作负载契约并由 Linux/Windows 共用。

### Windows 受控适配

Windows 固定命令适配必须与 Linux 容器使用同一 workload schema、配置版本、消息/ACK、SQLite schema、日志字段和健康 facet。平台差异只允许出现在串口标识（COM 名称与稳定硬件指纹）、路径/权限映射和进程托管适配；SQLite 必须使用专用绝对数据目录，串口枚举结果需记录硬件 ID，不能仅依赖可能漂移的 COM 序号。

## 被否决方案

- **独立 collector 仓库/独立协议实现**：造成双栈漂移。
- **NODE 任意命令启动 jar**：命令注入、不可重复部署且难以审计。
- **systemd 作为唯一方案**：跨平台和升级一致性较弱；仅保留为 Windows/特殊主机受控适配。

## 回滚

NODE 保存最近两个已验证镜像摘要和配置版本。回滚仅切换不可变镜像/配置，保留 SQLite 数据卷；回滚后继续使用原 messageId 补传。
