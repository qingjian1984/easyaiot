"""远程计算节点 Python 启动器（AI bundle 离线运行时）。"""
import os


def resolve_ai_bundle_python(ai_root: str | None = None, bundle: str = 'ai_service') -> str:
    explicit = os.getenv('NODE_REMOTE_PYTHON', '').strip()
    if explicit:
        return explicit
    root = ai_root or os.getenv('NODE_REMOTE_AI_ROOT', '/opt/easyaiot/AI')
    bundle_key = (bundle or 'ai_service').strip()
    return os.path.join(root, '.bundles', bundle_key, 'run-python.sh')
