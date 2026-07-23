# EasyAIoT FUXA 组态演示工程

## 内容

工程文件 `easyaiot_scada_demo.fuxap` 含 **4 套高质量中文组态画面**（基于 FUXA 官方演示工艺图增强）：

| 画面名 | 说明 |
|--------|------|
| 水厂工艺总貌 | 水处理工艺：罐体 / 管线 / 阀门 |
| 产线运行看板 | 产线 KPI、电机与阀组状态 |
| 厂区管网组态 | 厂区管网拓扑（高细节工艺图） |
| 配电室电力监视 | 配电室负荷与开关监视 |

封面图位于 `WEB/public/resource/visualize-demo/scada-*-cover.svg`。

## 导入 / 恢复被改坏的演示数据

```bash
# 1) FUXA 画面（需 fuxa-server 已启动）
# 生产恢复请直连容器内网（绕过公网 nginx 只读策略），例如：
#   FUXA_URL=http://10.0.0.87:1881 bash .scripts/fuxa/seed_fuxa_demo.sh
bash .scripts/fuxa/seed_fuxa_demo.sh

# 2) 平台侧大屏×4 + 组态×4 元数据
bash .scripts/go-view/seed_visualize_demo.sh
```

默认 FUXA 账号：`admin` / `123456`（请在生产环境立即修改）。

平台项目 `editor_ref` 与画面名一致；预览打开对应运行态画面。

## 演示数据只读保护（防界面改删工艺图）

演示工艺图曾因用户跳转到 FUXA `:1881` 编辑器被改删。仓库内已加多层保护：

| 层 | 行为 |
|----|------|
| 平台前端 | 演示项目（ID 9311–9314 / 上表画面名）隐藏「打开编辑器」，只保留预览 |
| 平台后端 | `iot.fuxa.demo-read-only=true`：演示打开强制 `preview`；生产默认 `force-preview=true` |
| SSO 桥接页 | `easyaiot-sso.html` 对演示画面拒绝 `mode=edit` / `target=/editor` |
| 公网 nginx `:1881` | `deny /editor`；`/api/project` 仅允许 GET（禁止覆盖工程） |

运维若需修改工艺图：

1. **直连容器内网**（例如 `http://10.0.0.87:1881/editor`），不要走公网反代
2. 改完后如需固化到仓库：从 FUXA 导出工程覆盖 `easyaiot_scada_demo.fuxap`
3. 被改坏时重新执行上方 seed 即可整包恢复

相关配置：

- `DEVICE/.../application-prod.yaml` → `iot.fuxa.demo-read-only` / `force-preview`
- `WEB/conf/nginx.prod-server.conf` → `:1881` server 只读规则
- 环境变量：`IOT_FUXA_DEMO_READ_ONLY`、`IOT_FUXA_FORCE_PREVIEW`

大屏 content 重新生成（纯 Python，秒级；勿用浏览器截图 gen）：

```bash
python3 .scripts/go-view/gen_visualize_dashboard_demo.py
```

## EasyAIoT 免登跳转（SSO）

已开启 FUXA `secureEnabled` 时，平台通过代登录 + 同源桥接页免登进入：

1. 前端调用 `GET /visualize/project/fuxa-open?id=&mode=edit|preview`
2. 后端向 FUXA `/api/signin` 获取 token（演示/强制预览时降级为 preview）
3. 浏览器打开 `http://<fuxa>/easyaiot-sso.html?token=...&mode=...&view=...`
4. 桥接页写入 `sessionStorage.currentUser` 后跳转 `/home?view=...`（演示不再进 `/editor`）

相关文件：

- 桥接页：`.scripts/fuxa/easyaiot-sso.html`（compose 挂载到 FUXA `client/dist`）
- 配置：`iot.fuxa.*`（`DEVICE/iot-visualize/.../application.yaml`）
- FUXA 鉴权：`fuxa_data/appdata/settings.js` 中 `secureEnabled` / `secretCode`
