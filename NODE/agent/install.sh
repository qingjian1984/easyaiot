#!/bin/bash
# EasyAIoT Node Agent 安装脚本
set -euo pipefail

INSTALL_DIR="${INSTALL_DIR:-/opt/easyaiot/node-agent}"
PYTHON="${PYTHON:-python3}"

echo "==> 安装目录: $INSTALL_DIR"
sudo mkdir -p "$INSTALL_DIR"
resolved_install_dir="$(readlink -f "$INSTALL_DIR")"
resolved_pwd="$(readlink -f "$(pwd)")"
if [ "$resolved_pwd" != "$resolved_install_dir" ]; then
  sudo cp run_agent.py agent_server.py media_manager.py workload_manager.py requirements.txt agent.env.example "$INSTALL_DIR/"
fi
cd "$resolved_install_dir"
sudo $PYTHON -m pip install -r requirements.txt -q

if [ ! -f "$INSTALL_DIR/agent.env" ]; then
  sudo cp agent.env.example agent.env
  echo "请编辑 $INSTALL_DIR/agent.env 填入 NODE_ID 和 AGENT_TOKEN"
fi

cat <<'UNIT' | sudo tee /etc/systemd/system/easyaiot-node-agent.service > /dev/null
[Unit]
Description=EasyAIoT Node Agent
After=network.target

[Service]
Type=simple
EnvironmentFile=/opt/easyaiot/node-agent/agent.env
ExecStart=/usr/bin/python3 /opt/easyaiot/node-agent/run_agent.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT

sudo systemctl daemon-reload
echo "==> 安装完成。执行以下命令启动:"
echo "    sudo systemctl enable --now easyaiot-node-agent"
