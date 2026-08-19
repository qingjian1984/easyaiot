"""
Agent HTTP 服务：接收控制面下发的部署/停止指令。
"""
import logging
import os
from flask import Flask, jsonify, request

from agent_security import (
    AgentAuthError,
    FileAgentSigningKeyProvider,
    PersistentNonceStore,
    verify_agent_request,
)

from workload_manager import WorkloadManager, find_available_port
from media_manager import MediaStackManager
from mqtt_manager import MqttStackManager
from collector_workload import (
    CollectorDeploymentError,
    CollectorDeploymentService,
    DockerComposeCollectorExecutor,
    load_collector_policy,
)
from collector_config_state import (
    CollectorConfigStateError,
    CollectorConfigStateService,
    REQUEST_MAX_BYTES,
    validate_config_envelope,
)

logger = logging.getLogger('easyaiot-node-agent.server')

AGENT_TOKEN = os.environ.get('AGENT_TOKEN', '')
AGENT_LISTEN_HOST = os.environ.get('AGENT_LISTEN_HOST', '0.0.0.0')
AGENT_LISTEN_PORT = int(os.environ.get('AGENT_LISTEN_PORT', '9100'))

app = Flask(__name__)
manager = WorkloadManager()
media_manager = MediaStackManager()
mqtt_manager = MqttStackManager()
collector_executor = DockerComposeCollectorExecutor()
collector_service = CollectorDeploymentService(collector_executor)
collector_config_state_service = None


def _ok(data=None):
    return jsonify({'code': 0, 'msg': 'success', 'data': data or {}})


def _err(msg: str, code=1, status: int = 400):
    return jsonify({'code': code, 'msg': msg, 'data': None}), status


def _check_token():
    token = request.headers.get('X-Agent-Token', '')
    if not AGENT_TOKEN or token != AGENT_TOKEN:
        return False
    return True


_agent_signing_key_provider = FileAgentSigningKeyProvider()
_agent_nonce_store = None


def _requires_hmac(path: str) -> bool:
    """collector 配置及其状态端点禁止退回 token-only。"""
    return path == '/workload/collector/config' or path.startswith('/workload/collector/')


def _get_agent_nonce_store():
    global _agent_nonce_store
    if _agent_nonce_store is None:
        replay_db = os.environ.get(
            'AGENT_REPLAY_DB',
            '/var/lib/easyaiot/node-agent/replay-window.db',
        )
        _agent_nonce_store = PersistentNonceStore(replay_db)
    return _agent_nonce_store


def _get_collector_config_state_service():
    """Resolve the state root only from local installation configuration."""

    global collector_config_state_service
    if collector_config_state_service is None:
        collector_config_state_service = CollectorConfigStateService.from_env()
    return collector_config_state_service


def _config_state_status(code: str) -> int:
    if code == "COLLECTOR_WORKLOAD_NOT_FOUND":
        return 404
    if code in {"CONFIG_VERSION_STALE", "CONFIG_VERSION_CONFLICT"}:
        return 409
    if code == "COLLECTOR_CONFIG_TOO_LARGE":
        return 413
    if code == "COLLECTOR_CONFIG_PERMISSION_INVALID":
        return 503
    if code in {"COLLECTOR_CONFIG_STATE_CORRUPT", "COLLECTOR_CONFIG_WRITE_FAILED"}:
        return 500
    return 400


@app.before_request
def auth_middleware():
    if request.path in ('/health',):
        return None
    if _requires_hmac(request.path):
        try:
            verify_agent_request(
                request.method,
                request.full_path[:-1] if request.full_path.endswith('?') else request.full_path,
                request.get_data(cache=True),
                request.headers,
                _agent_signing_key_provider,
                _get_agent_nonce_store(),
            )
            return None
        except AgentAuthError as exc:
            return _err(exc.code, 'AGENT_AUTH_FAILED', 401)
        except (OSError, RuntimeError):
            # 凭据文件/nonce 数据库不可用时必须拒绝，而不是退回 bearer token。
            return _err('AGENT_SIGNING_KEY_UNAVAILABLE', 'AGENT_AUTH_FAILED', 503)
    if not _check_token():
        return _err('Agent 认证失败', 401, 401)


@app.get('/health')
def health():
    return _ok({'status': 'ok'})


@app.post('/workload/deploy')
def deploy_workload():
    try:
        spec = request.get_json(force=True) or {}
        # 只要识别出 collector 类型即切断通用能力；即使 workloadId 或其它
        # 字段缺失，也不得先走通用必填校验、端口探测或 WorkloadManager。
        if spec.get('workloadType') == 'iot-sink-collector':
            return _err('UNSUPPORTED_GENERIC_DEPLOY', 'UNSUPPORTED_GENERIC_DEPLOY', 400)
        if not spec.get('workloadType') or not spec.get('workloadId'):
            return _err('workloadType 和 workloadId 必填')
        env = spec.get('env') or {}
        runtime = (spec.get('runtime') or env.get('RUNTIME') or 'process').lower()
        # TRANSFORM 镜像默认从 48096 起找空闲口，同节点可多容器
        if not env.get('PORT'):
            start = int(env.get('START_PORT') or (48096 if runtime == 'docker' or spec.get('workloadType') == 'transform_runtime' else 8000))
            port = find_available_port(start)
            if port:
                env['PORT'] = str(port)
                spec['env'] = env
        # docker 模式允许 command 为空（由 Agent 组装 docker run）
        if runtime != 'docker' and not (spec.get('command') or []):
            return _err('command 不能为空')
        data = manager.deploy(spec)
        return _ok(data)
    except Exception as e:
        logger.exception('部署失败')
        return _err(str(e))


@app.post('/workload/collector/deploy')
def deploy_collector_workload():
    """只接受 WorkloadSpec 1.0；认证由 before_request 的 ADR-018 HMAC 完成。"""
    try:
        # auth_middleware 已在读取 JSON 之前执行 HMAC、body hash 和 nonce 检查。
        spec = request.get_json(force=True)
    except Exception:
        return _err(
            'COLLECTOR_WORKLOAD_SCHEMA_INVALID',
            'COLLECTOR_WORKLOAD_SCHEMA_INVALID',
            400,
        )
    if not isinstance(spec, dict):
        return _err(
            'COLLECTOR_WORKLOAD_SCHEMA_INVALID',
            'COLLECTOR_WORKLOAD_SCHEMA_INVALID',
            400,
        )
    try:
        policy = load_collector_policy()
        # 平台值来自 Agent 本地启动环境，不能由远端 WorkloadSpec 影响；未配置时
        # collector_workload 按当前 OS 判定（Windows capability 默认关闭）。
        agent_platform = os.environ.get('COLLECTOR_AGENT_PLATFORM') or None
        data = collector_service.deploy(spec, policy, platform=agent_platform)
        return _ok(data)
    except CollectorDeploymentError as exc:
        return _err(exc.code, exc.code, 400)
    except Exception:
        # 不记录请求原文、secret ref、签名、nonce 或异常堆栈。
        logger.error('collector deployment failed')
        return _err('COLLECTOR_DEPLOY_FAILED', 'COLLECTOR_DEPLOY_FAILED', 500)


@app.put('/workload/collector/config')
def put_collector_config():
    """Accept a verified 1.1 envelope and atomically replace desired only."""

    raw_body = request.get_data(cache=True)
    if len(raw_body) > REQUEST_MAX_BYTES:
        return _err(
            'COLLECTOR_CONFIG_TOO_LARGE',
            'COLLECTOR_CONFIG_TOO_LARGE',
            413,
        )
    try:
        # Complete envelope validation must precede local state-root resolution;
        # malformed requests therefore cannot depend on installation state or
        # create any directory/lock side effect.
        validate_config_envelope(raw_body)
        service = _get_collector_config_state_service()
        result = service.put(raw_body)
        return _ok(result.as_dict())
    except CollectorConfigStateError as exc:
        return _err(exc.code, exc.code, _config_state_status(exc.code))
    except Exception:
        logger.error('collector config state transition failed')
        return _err(
            'COLLECTOR_CONFIG_WRITE_FAILED',
            'COLLECTOR_CONFIG_WRITE_FAILED',
            500,
        )


@app.get('/workload/collector/<workload_id>')
def get_collector_config(workload_id: str):
    """Return redacted desired/active/observed summaries only."""

    try:
        service = _get_collector_config_state_service()
        return _ok(service.get(workload_id))
    except CollectorConfigStateError as exc:
        return _err(exc.code, exc.code, _config_state_status(exc.code))
    except Exception:
        logger.error('collector config state read failed')
        return _err(
            'COLLECTOR_CONFIG_STATE_CORRUPT',
            'COLLECTOR_CONFIG_STATE_CORRUPT',
            500,
        )


@app.post('/workload/stop')
def stop_workload():
    try:
        body = request.get_json(force=True) or {}
        workload_type = body.get('workloadType')
        workload_id = body.get('workloadId')
        if not workload_type or not workload_id:
            return _err('workloadType 和 workloadId 必填')
        removed = manager.stop(workload_type, workload_id)
        return _ok({'stopped': True, 'removed': bool(removed)})
    except Exception as e:
        logger.exception('停止失败')
        return _err(str(e))


@app.get('/workload/list')
def list_workloads():
    return _ok({'workloads': manager.list_workloads(), 'activeTasks': manager.active_count()})


@app.post('/media/deploy')
def deploy_media_stack():
    try:
        spec = request.get_json(force=True) or {}
        if not spec.get('stackType') and not spec.get('mediaType'):
            return _err('stackType 必填')
        data = media_manager.deploy(spec)
        return _ok(data)
    except Exception as e:
        logger.exception('媒体栈部署失败')
        return _err(str(e))


@app.post('/media/stop')
def stop_media_stack():
    try:
        body = request.get_json(force=True) or {}
        stack_type = body.get('stackType') or body.get('mediaType')
        if not stack_type:
            return _err('stackType 必填')
        media_manager.stop(stack_type)
        return _ok({'stopped': True})
    except Exception as e:
        logger.exception('媒体栈停止失败')
        return _err(str(e))


@app.post('/mqtt/deploy')
def deploy_mqtt_stack():
    try:
        spec = request.get_json(force=True) or {}
        data = mqtt_manager.deploy(spec)
        return _ok(data)
    except Exception as e:
        logger.exception('MQTT 网关部署失败')
        return _err(str(e))


@app.post('/mqtt/stop')
def stop_mqtt_stack():
    try:
        body = request.get_json(force=True) or {}
        stack_type = body.get('stackType') or 'emqx'
        mqtt_manager.stop(stack_type)
        return _ok({'stopped': True})
    except Exception as e:
        logger.exception('MQTT 网关停止失败')
        return _err(str(e))


def create_app():
    return app


def run_server():
    logger.info('Agent HTTP 服务启动 %s:%s', AGENT_LISTEN_HOST, AGENT_LISTEN_PORT)
    app.run(host=AGENT_LISTEN_HOST, port=AGENT_LISTEN_PORT, threaded=True, use_reloader=False)
