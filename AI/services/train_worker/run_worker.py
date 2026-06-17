"""
模型训练集群 Worker：在 GPU 计算节点执行 YOLO 训练，读写 CephFS 共享目录。
"""
import importlib.util
import json
import logging
import os
import sys

_ai_root = os.getenv('AI_ROOT', '/opt/easyaiot/AI')
_lib_root = os.getenv('NODE_REMOTE_LIB_ROOT', '/opt/easyaiot/lib')
for _p in (_lib_root, _ai_root):
    if _p and _p not in sys.path:
        sys.path.insert(0, _p)

logging.basicConfig(level=logging.INFO, format='%(asctime)s [train_worker] %(levelname)s %(message)s')
logger = logging.getLogger(__name__)

WORKLOAD_TYPE_MODEL_TRAIN = 'model_train'


def _load_env():
    try:
        spec = importlib.util.spec_from_file_location(
            '_ai_env', os.path.join(_ai_root, 'app', 'utils', 'ai_env.py'),
        )
        if spec and spec.loader:
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            mod.load_ai_env(override=False)
    except Exception as e:
        logger.warning('加载 AI 环境变量失败: %s', e)
    try:
        from cluster_storage import apply_cluster_env_defaults, ensure_cluster_dirs, is_cluster_mode
        apply_cluster_env_defaults()
        if is_cluster_mode():
            ensure_cluster_dirs()
    except ImportError:
        pass


def _env_bool(key: str, default: bool = False) -> bool:
    raw = os.getenv(key)
    if raw is None:
        return default
    return raw.strip().lower() in ('1', 'true', 'yes', 'on')


def _parse_gpu_ids() -> list | None:
    raw = (os.getenv('GPU_IDS') or os.getenv('TRAIN_GPU_IDS') or '').strip()
    if not raw:
        return None
    try:
        return [int(x.strip()) for x in raw.split(',') if x.strip() != '']
    except ValueError:
        return None


def _release_binding(task_id: int) -> None:
    try:
        from app.utils import node_client
        node_client.release_binding(WORKLOAD_TYPE_MODEL_TRAIN, str(task_id))
    except Exception as e:
        logger.warning('释放训练节点绑定失败 task_id=%s: %s', task_id, e)


def main():
    _load_env()
    task_id = int(os.getenv('TRAIN_TASK_ID', '0'))
    record_id = int(os.getenv('TRAIN_RECORD_ID', str(task_id)))
    if not task_id:
        logger.error('未配置 TRAIN_TASK_ID')
        sys.exit(1)

    from app.blueprints.train import train_model, train_status

    epochs = int(os.getenv('TRAIN_EPOCHS', '20'))
    img_size = int(os.getenv('TRAIN_IMG_SIZE', '640'))
    batch_size = int(os.getenv('TRAIN_BATCH_SIZE', '16'))
    model_arch = os.getenv('TRAIN_MODEL_ARCH', 'yolov8n.pt')
    dataset_zip_path = os.getenv('TRAIN_DATASET_PATH', '')
    dataset_source = (os.getenv('TRAIN_DATASET_SOURCE') or 'local').strip().lower()
    use_gpu = _env_bool('TRAIN_USE_GPU', True)
    resume_mode = _env_bool('TRAIN_RESUME', False)
    gpu_ids = _parse_gpu_ids()

    train_status[task_id] = {
        'status': 'preparing',
        'message': '远程 Worker 已启动，准备训练…',
        'progress': 0,
        'log': '',
        'stop_requested': False,
    }

    logger.info(
        '开始远程训练 task_id=%s epochs=%s model=%s dataset=%s resume=%s',
        task_id, epochs, model_arch, dataset_zip_path, resume_mode,
    )
    try:
        train_model(
            task_id,
            epochs=epochs,
            model_arch=model_arch,
            img_size=img_size,
            batch_size=batch_size,
            use_gpu=use_gpu,
            dataset_zip_path=dataset_zip_path or None,
            record_id=record_id,
            gpu_ids=gpu_ids,
            dataset_source=dataset_source,
            resume_mode=resume_mode,
        )
    except Exception as e:
        logger.exception('远程训练异常 task_id=%s: %s', task_id, e)
        sys.exit(1)
    finally:
        _release_binding(task_id)


if __name__ == '__main__':
    main()
