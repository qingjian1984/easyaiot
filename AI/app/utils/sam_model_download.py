"""SAM3 模型权重下载与状态查询（下载到本地 SAM_MODEL_PATH，不走 MinIO）"""
import os
import re
import threading
import urllib.error
import urllib.request
from typing import Any, Dict

from app.services.sam_service import SAM_MODEL_PATH

SAM_MODEL_DOWNLOAD_URL = os.getenv('SAM_MODEL_DOWNLOAD_URL', '').strip()
MIN_MODEL_SIZE_BYTES = 100 * 1024 * 1024
ESTIMATED_MODEL_SIZE_BYTES = int(os.getenv('SAM_MODEL_ESTIMATED_BYTES', str(3500 * 1024 * 1024)))
DOWNLOAD_CHUNK_SIZE = 1024 * 1024
DOWNLOAD_USER_AGENT = 'EasyAIoT-AI/1.0'
DOWNLOAD_TIMEOUT_SEC = int(os.getenv('SAM_MODEL_DOWNLOAD_TIMEOUT', '300'))

_lock = threading.Lock()
_state: Dict[str, Any] = {
    'status': 'idle',
    'stage': 'idle',
    'progress': 0,
    'downloaded_bytes': 0,
    'total_bytes': 0,
    'error': None,
}


def _partial_path() -> str:
    return f'{SAM_MODEL_PATH}.downloading'


def _get_partial_bytes() -> int:
    path = _partial_path()
    if not os.path.isfile(path):
        return 0
    try:
        return os.path.getsize(path)
    except OSError:
        return 0


def _parse_content_range_total(content_range: str, content_length: int, existing_size: int) -> int:
    """从 Content-Range 或 Content-Length 解析总字节数。"""
    if content_range:
        match = re.match(r'bytes\s+\d+-\d+/(\d+|\*)', content_range.strip(), re.I)
        if match and match.group(1) != '*':
            return int(match.group(1))
    if content_length > 0:
        return existing_size + content_length
    return ESTIMATED_MODEL_SIZE_BYTES


def _calc_progress(downloaded: int, total: int) -> int:
    if total <= 0:
        return 0
    return min(95, int(downloaded * 95 / total))


def _reset_error_if_idle() -> None:
    if _state['status'] == 'idle':
        _state['error'] = None


def is_sam_model_available() -> bool:
    if not os.path.isfile(SAM_MODEL_PATH):
        return False
    try:
        return os.path.getsize(SAM_MODEL_PATH) >= MIN_MODEL_SIZE_BYTES
    except OSError:
        return False


def _sync_partial_progress_locked() -> None:
    """将磁盘上的 .downloading 进度同步到内存状态（服务重启或下载中断后）。"""
    if is_sam_model_available():
        return
    partial_bytes = _get_partial_bytes()
    if partial_bytes <= 0:
        return
    total = int(_state['total_bytes']) or ESTIMATED_MODEL_SIZE_BYTES
    if _state['status'] != 'downloading':
        _state['downloaded_bytes'] = partial_bytes
        if partial_bytes < total:
            _state['progress'] = _calc_progress(partial_bytes, total)


def _build_status_locked() -> Dict[str, Any]:
    exists = is_sam_model_available()
    size_bytes = os.path.getsize(SAM_MODEL_PATH) if exists else 0
    partial_bytes = 0 if exists else _get_partial_bytes()
    resumable = partial_bytes > 0 and not exists and bool(SAM_MODEL_DOWNLOAD_URL)
    _reset_error_if_idle()
    downloading = _state['status'] == 'downloading'
    stage = _state['stage']
    if exists:
        stage = 'done'
    elif downloading:
        stage = _state['stage'] or 'downloading'
    elif resumable and _state['status'] == 'error':
        stage = 'error'
    elif resumable:
        stage = 'idle'

    total_bytes = int(_state['total_bytes']) or ESTIMATED_MODEL_SIZE_BYTES
    downloaded_bytes = int(_state['downloaded_bytes'])
    if not exists and partial_bytes > downloaded_bytes:
        downloaded_bytes = partial_bytes
    if not exists and partial_bytes > 0 and total_bytes <= 0:
        total_bytes = ESTIMATED_MODEL_SIZE_BYTES

    progress = 0
    if exists:
        progress = 100
    elif downloading or resumable:
        progress = _calc_progress(downloaded_bytes, total_bytes)

    return {
        'exists': exists,
        'filename': os.path.basename(SAM_MODEL_PATH),
        'path': SAM_MODEL_PATH,
        'size_bytes': size_bytes,
        'downloading': downloading,
        'resumable': resumable,
        'stage': stage,
        'progress': progress,
        'downloaded_bytes': downloaded_bytes,
        'total_bytes': total_bytes,
        'error': _state['error'],
    }


def get_sam_model_status() -> Dict[str, Any]:
    with _lock:
        _sync_partial_progress_locked()
        return _build_status_locked()


def _set_progress(stage: str, progress: int, downloaded: int = 0, total: int = 0) -> None:
    with _lock:
        _state['stage'] = stage
        _state['downloaded_bytes'] = downloaded
        if total > 0:
            _state['total_bytes'] = total
        _state['progress'] = max(int(_state['progress']), int(progress))


def _finalize_partial(partial_path: str) -> None:
    size = os.path.getsize(partial_path)
    if size < MIN_MODEL_SIZE_BYTES:
        raise RuntimeError(f'下载文件过小（{size} bytes），可能不完整')
    _set_progress('installing', 96, downloaded=size, total=size)
    os.replace(partial_path, SAM_MODEL_PATH)


def _download_http_with_progress(url: str, dest_path: str) -> None:
    existing_size = _get_partial_bytes() if os.path.abspath(dest_path) == os.path.abspath(_partial_path()) else 0
    if os.path.isfile(dest_path) and existing_size == 0:
        try:
            existing_size = os.path.getsize(dest_path)
        except OSError:
            existing_size = 0

    headers = {'User-Agent': DOWNLOAD_USER_AGENT}
    if existing_size > 0:
        headers['Range'] = f'bytes={existing_size}-'

    req = urllib.request.Request(url, headers=headers)
    try:
        resp = urllib.request.urlopen(req, timeout=DOWNLOAD_TIMEOUT_SEC)
    except urllib.error.HTTPError as exc:
        if exc.code == 416 and existing_size > 0:
            _finalize_partial(dest_path)
            return
        raise

    with resp:
        status_code = getattr(resp, 'status', None) or resp.getcode()
        content_length = int(resp.headers.get('Content-Length', 0) or 0)
        content_range = resp.headers.get('Content-Range', '') or ''
        total = _parse_content_range_total(content_range, content_length, existing_size)

        if status_code == 200 and existing_size > 0:
            # 服务端不支持 Range，从头下载
            existing_size = 0
            total = content_length or ESTIMATED_MODEL_SIZE_BYTES

        downloaded = existing_size
        _set_progress('downloading', max(1, _calc_progress(downloaded, total)), downloaded=downloaded, total=total)

        file_mode = 'ab' if existing_size > 0 and status_code == 206 else 'wb'
        if file_mode == 'wb':
            downloaded = 0

        with open(dest_path, file_mode) as out_file:
            while True:
                chunk = resp.read(DOWNLOAD_CHUNK_SIZE)
                if not chunk:
                    break
                out_file.write(chunk)
                downloaded += len(chunk)
                _set_progress(
                    'downloading',
                    _calc_progress(downloaded, total),
                    downloaded=downloaded,
                    total=total,
                )


def _do_download() -> None:
    partial_path = _partial_path()
    try:
        if not SAM_MODEL_DOWNLOAD_URL:
            raise RuntimeError(
                f'未配置 SAM_MODEL_DOWNLOAD_URL，无法自动下载。'
                f'请设置下载地址，或手动将权重放到 {SAM_MODEL_PATH}'
            )

        resume_bytes = _get_partial_bytes()
        with _lock:
            _state['status'] = 'downloading'
            _state['stage'] = 'downloading'
            _state['error'] = None
            if resume_bytes > 0:
                total = int(_state['total_bytes']) or ESTIMATED_MODEL_SIZE_BYTES
                _state['downloaded_bytes'] = resume_bytes
                _state['total_bytes'] = total
                _state['progress'] = _calc_progress(resume_bytes, total)
            else:
                _state['progress'] = 0
                _state['downloaded_bytes'] = 0
                _state['total_bytes'] = ESTIMATED_MODEL_SIZE_BYTES

        os.makedirs(os.path.dirname(SAM_MODEL_PATH) or '.', exist_ok=True)
        _download_http_with_progress(SAM_MODEL_DOWNLOAD_URL, partial_path)
        _finalize_partial(partial_path)

        with _lock:
            _state['status'] = 'done'
            _state['stage'] = 'done'
            _state['progress'] = 100
            _state['downloaded_bytes'] = os.path.getsize(SAM_MODEL_PATH)
            _state['total_bytes'] = _state['downloaded_bytes']
            _state['error'] = None
    except Exception as exc:
        partial_bytes = _get_partial_bytes()
        with _lock:
            _state['status'] = 'error'
            _state['stage'] = 'error'
            _state['error'] = str(exc)
            if partial_bytes > 0:
                total = int(_state['total_bytes']) or ESTIMATED_MODEL_SIZE_BYTES
                _state['downloaded_bytes'] = partial_bytes
                _state['progress'] = _calc_progress(partial_bytes, total)
    finally:
        try:
            from app.services.sam_service import reset_sam_service
            reset_sam_service()
        except Exception:
            pass


def start_sam_model_download() -> Dict[str, Any]:
    with _lock:
        if is_sam_model_available():
            _state['status'] = 'done'
            _state['stage'] = 'done'
            _state['progress'] = 100
            _state['error'] = None
            return {'started': False, 'message': '模型已存在', **_build_status_locked()}

        if _state['status'] == 'downloading':
            return {'started': False, 'message': '模型正在下载中', **_build_status_locked()}

        resume_bytes = _get_partial_bytes()
        _state['status'] = 'downloading'
        _state['stage'] = 'downloading'
        _state['error'] = None
        if resume_bytes > 0:
            total = int(_state['total_bytes']) or ESTIMATED_MODEL_SIZE_BYTES
            _state['downloaded_bytes'] = resume_bytes
            _state['total_bytes'] = total
            _state['progress'] = _calc_progress(resume_bytes, total)
            message = f'已从 {_format_bytes(resume_bytes)} 处续传'
        else:
            _state['progress'] = 0
            _state['downloaded_bytes'] = 0
            _state['total_bytes'] = ESTIMATED_MODEL_SIZE_BYTES
            message = '已开始下载'
        status = _build_status_locked()

    thread = threading.Thread(target=_do_download, name='sam-model-download', daemon=True)
    thread.start()
    return {'started': True, 'message': message, **status}


def _format_bytes(num: int) -> str:
    if num >= 1024 * 1024 * 1024:
        return f'{num / (1024 * 1024 * 1024):.1f} GB'
    if num >= 1024 * 1024:
        return f'{num / (1024 * 1024):.1f} MB'
    if num >= 1024:
        return f'{num / 1024:.1f} KB'
    return f'{num} B'
