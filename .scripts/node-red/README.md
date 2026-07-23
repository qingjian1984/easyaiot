# EasyAIoT Node-RED 演示规则链

## 内容

工程文件 `easyaiot_flows_demo.json` 含 **4 条高质量中文演示规则链**（仅用 Node-RED 核心节点）：

| 页签（规则链） | 固定 ID | 说明 |
|----------------|---------|------|
| 设备遥测采集链路 | `easyaiot_demo_telemetry` | MQTT 遥测接入 → 解析 → 温度阈值 → 调试 |
| 告警分级推送链路 | `easyaiot_demo_alert` | 告警事件分级（紧急/重要/一般）→ 模板推送 |
| 工控协议桥接链路 | `easyaiot_demo_bridge` | 模拟点位 → 物模型映射 → MQTT 上行 |
| 视觉质检联动链路 | `easyaiot_demo_vision` | HTTP 质检回调 → AI 判定 → NG 剔除下发 |

容器页面标题 / 顶栏：**EasyAIoT**（`settings.js` → `editorTheme`）。

## 导入 / 恢复被改坏的演示数据

```bash
# 需 nodered-server 已启动
# 生产恢复请直连容器内网（绕过公网只读策略），例如：
#   NODERED_URL=http://10.0.0.87:1880 bash .scripts/node-red/seed_nodered_demo.sh
bash .scripts/node-red/seed_nodered_demo.sh

# CentOS 7 一键启动并导入演示：
#   cd .scripts/docker && sudo ./start_nodered_centos7.sh --seed
# 仅恢复被改坏的演示（容器已运行）：
#   cd .scripts/docker && sudo ./start_nodered_centos7.sh --seed-only

# 若首次同步 settings.js，需重启使标题与只读中间件生效
docker restart nodered-server
```

## 演示数据只读保护（防界面改删规则链）

演示链路曾可能因用户进入 Node-RED 编辑器被改删。仓库内已加多层保护：

| 层 | 行为 |
|----|------|
| 平台前端 | 演示项（上表 4 个 ID/名称）隐藏编辑/删除，仅保留查看 |
| Node-RED `settings.js` | `httpAdminMiddleware`：禁止 PUT/DELETE 演示页签；`POST /flows` 部署时强制回填锁定副本 |
| 公网 nginx | `/dev-api/nodeRed/flow/easyaiot_demo_*` 仅允许 GET（禁止覆盖/删除） |

运维若需修改演示链路：

1. **直连容器内网**（例如 `http://10.0.0.87:1880`），不要走公网反代
2. 改完后固化到仓库：导出/覆盖 `easyaiot_flows_demo.json`
3. 被改坏时重新执行上方 seed 即可整包恢复

相关文件：

- 工程：`.scripts/node-red/easyaiot_flows_demo.json`
- 配置：`.scripts/node-red/settings.js`（compose 挂载到 Node-RED `/data/settings.js`）
- 编辑器只读脚本：`.scripts/node-red/public/easyaiot-demo-guard.js`
- 种子脚本：`.scripts/node-red/seed_nodered_demo.sh`
- 前端识别：`WEB/src/utils/noderedDemo.ts`

## EasyAIoT 打开方式

平台「规则链」列表点击封面/查看 → iframe 打开 `/dev-api/nodeRed/#flow/<id>`，浏览器页签标题为规则链名称；Node-RED 编辑器自身标题为 **EasyAIoT**。
