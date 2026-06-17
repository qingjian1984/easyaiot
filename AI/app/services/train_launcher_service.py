"""
模型训练集群调度：将训练任务下发到 GPU 计算节点。
"""
from __future__ import annotations

import json
import logging
import os
import socket
from typing import Optional

from db_models import db, TrainTask
from app.utils.node_remote_python import resolve_ai_bundle_python

logger = logging.getLogger(__name__)

WORKLOAD_TYPE_MODEL_TRAIN = 'model_train'
BUNDLE_MODEL_TRAIN = 'model_train'


def _is_cluster_mode() -> bool:
    try:
        from cluster_storage import is_cluster_mode
        return is_cluster_mode()
    except ImportError:
        return os.getenv('CLUSTER_MODE', '').strip().lower() in ('1', 'true', 'yes', 'on')


def resolve_schedule_policy(explicit: str | None) -> str:
    policy = (explicit or '').strip().lower()
    if policy in ('local', 'auto', 'node'):
        return policy
    if _is_cluster_mode():
        from app.utils.node_client import is_remote_deploy_enabled
        if is_remote_deploy_enabled():
            return 'auto'
    return 'local'


def use_remote_deploy(schedule_policy: str | None) -> bool:
    from app.utils.node_client import is_remote_deploy_enabled
    if not is_remote_deploy_enabled():
        return False
    policy = resolve_schedule_policy(schedule_policy)
    return policy in ('auto', 'node')


def _control_plane_base_url() -> str:
    host = os.getenv('AI_CONTROL_HOST') or os.getenv('POD_IP') or os.getenv('HOST_IP')
    port = os.getenv('FLASK_RUN_PORT', '5000')
    if not host:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(('8.8.8.8', 80))
            host = s.getsockname()[0]
            s.close()
        except Exception:
            host = '127.0.0.1'
    return os.getenv('AI_CONTROL_URL', f'http://{host}:{port}').rstrip('/')


def _build_train_deploy_env(
    train_task: TrainTask,
    *,
    epochs: int,
    model_arch: str,
    img_size: int,
    batch_size: int,
    use_gpu: bool,
    dataset_zip_path: str,
    dataset_source: str,
    resume_mode: bool,
    gpu_ids: list | None,
    log_dir: str,
    server_host: str,
) -> dict:
    env: dict[str, str] = {}
    for key in (
        'DATABASE_URL', 'GATEWAY_URL', 'JWT_TOKEN', 'JAVA_BACKEND_URL',
        'MINIO_ENDPOINT', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY', 'MINIO_SECURE',
        'CLUSTER_MODE', 'MEDIA_HOST_DATA_ROOT', 'CEPH_MOUNT_ROOT',
        'AI_DATASETS_DIR', 'AI_MODELS_DIR', 'AI_TRAIN_DIR',
        'NODE_REMOTE_LIB_ROOT', 'AI_ENV',
    ):
        val = os.getenv(key)
        if val:
            env[key] = val

    env['PYTHONUNBUFFERED'] = '1'
    env['AI_ROOT'] = os.getenv('NODE_REMOTE_AI_ROOT', '/opt/easyaiot/AI')
    env['AI_CONTROL_URL'] = _control_plane_base_url()
    env['LOG_PATH'] = log_dir
    env['POD_IP'] = server_host
    env['HOST_IP'] = server_host
    env['TRAIN_TASK_ID'] = str(train_task.id)
    env['TRAIN_RECORD_ID'] = str(train_task.id)
    env['TRAIN_EPOCHS'] = str(epochs)
    env['TRAIN_IMG_SIZE'] = str(img_size)
    env['TRAIN_BATCH_SIZE'] = str(batch_size)
    env['TRAIN_MODEL_ARCH'] = model_arch or 'yolov8n.pt'
    env['TRAIN_DATASET_PATH'] = dataset_zip_path or ''
    env['TRAIN_DATASET_SOURCE'] = dataset_source or 'local'
    env['TRAIN_USE_GPU'] = 'true' if use_gpu else 'false'
    env['TRAIN_RESUME'] = 'true' if resume_mode else 'false'
    if gpu_ids:
        env['TRAIN_GPU_IDS'] = ','.join(str(x) for x in gpu_ids)
    return env


def dispatch_train_to_node(
    train_task: TrainTask,
    *,
    epochs: int,
    model_arch: str,
    img_size: int,
    batch_size: int,
    use_gpu: bool,
    dataset_zip_path: str,
    dataset_source: str,
    resume_mode: bool,
    gpu_ids: list | None = None,
) -> tuple[bool, str]:
    """将训练任务调度到集群节点并下发 Worker。"""
    from app.utils import node_client

    policy = resolve_schedule_policy(train_task.schedule_policy)
    target_node_id = train_task.target_node_id
    if policy == 'node' and not target_node_id:
        return False, '已选择指定节点但未配置目标节点'

    workload_id = str(train_task.id)
    try:
        allocation = node_client.allocate_node(
            WORKLOAD_TYPE_MODEL_TRAIN,
            workload_id,
            capabilities=['model_train'],
            gpu_count=1,
            prefer_gpu=True,
            target_node_id=int(target_node_id) if policy == 'node' and target_node_id else None,
            sticky=True,
            require_ceph_mount=_is_cluster_mode(),
        )
    except Exception as e:
        logger.warning('训练任务调度失败 task_id=%s: %s', train_task.id, e)
        return False, f'节点调度失败: {e}'

    node_id = allocation['nodeId']
    host = allocation.get('host') or ''
    allocated_gpu_ids = allocation.get('gpuIds')

    ai_root_remote = os.getenv('NODE_REMOTE_AI_ROOT', '/opt/easyaiot/AI')
    work_dir = os.path.join(ai_root_remote, 'services', 'train_worker')
    log_dir = os.path.join(ai_root_remote, 'logs', 'train', str(train_task.id))
    python_exec = resolve_ai_bundle_python(ai_root_remote, BUNDLE_MODEL_TRAIN)
    worker_script = os.path.join(ai_root_remote, 'services', 'train_worker', 'run_worker.py')
    command = [python_exec, worker_script]

    env = _build_train_deploy_env(
        train_task,
        epochs=epochs,
        model_arch=model_arch,
        img_size=img_size,
        batch_size=batch_size,
        use_gpu=use_gpu,
        dataset_zip_path=dataset_zip_path,
        dataset_source=dataset_source,
        resume_mode=resume_mode,
        gpu_ids=gpu_ids,
        log_dir=log_dir,
        server_host=host,
    )

    try:
        result = node_client.deploy_workload(
            node_id=node_id,
            workload_type=WORKLOAD_TYPE_MODEL_TRAIN,
            workload_id=workload_id,
            command=command,
            work_dir=work_dir,
            log_dir=log_dir,
            env=env,
            gpu_ids=allocated_gpu_ids,
        )
    except Exception as e:
        node_client.release_binding(WORKLOAD_TYPE_MODEL_TRAIN, workload_id)
        logger.error('训练 Worker 下发失败 task_id=%s: %s', train_task.id, e)
        return False, f'Worker 下发失败: {e}'

    train_task.node_id = node_id
    train_task.service_server_ip = host
    train_task.service_process_id = result.get('pid')
    train_task.schedule_policy = policy
    db.session.commit()
    logger.info(
        '训练任务已下发集群 task_id=%s node_id=%s host=%s pid=%s',
        train_task.id, node_id, host, result.get('pid'),
    )
    return True, f'已下发到节点 {host}，训练正在启动'


def stop_remote_train(train_task: TrainTask) -> None:
    """停止远程训练 Worker 并释放节点绑定。"""
    from app.utils import node_client

    if not train_task or not train_task.node_id:
        return

    train_task.status = 'stopping'
    db.session.commit()

    try:
        node_client.stop_workload(
            train_task.node_id,
            WORKLOAD_TYPE_MODEL_TRAIN,
            str(train_task.id),
        )
    except Exception as e:
        logger.warning('远程停止训练失败 task_id=%s: %s', train_task.id, e)

    try:
        node_client.release_binding(WORKLOAD_TYPE_MODEL_TRAIN, str(train_task.id))
    except Exception as e:
        logger.warning('释放训练绑定失败 task_id=%s: %s', train_task.id, e)
