"""SAM 模型断点下载单元测试"""
import io
import os
import tempfile
import unittest
from unittest import mock

from app.utils import sam_model_download as smd


class TestSamModelDownloadHelpers(unittest.TestCase):
    def test_parse_content_range_total(self):
        total = smd._parse_content_range_total('bytes 100-999/5000', 900, 100)
        self.assertEqual(total, 5000)

    def test_parse_content_range_fallback(self):
        total = smd._parse_content_range_total('', 900, 100)
        self.assertEqual(total, 1000)

    def test_build_status_shows_resumable_partial(self):
        with tempfile.TemporaryDirectory() as tmp:
            model_path = os.path.join(tmp, 'sam3', 'model.pt')
            partial_path = f'{model_path}.downloading'
            os.makedirs(os.path.dirname(model_path), exist_ok=True)
            with open(partial_path, 'wb') as f:
                f.write(b'x' * (50 * 1024 * 1024))

            with mock.patch.object(smd, 'SAM_MODEL_PATH', model_path), mock.patch.object(
                smd, 'SAM_MODEL_DOWNLOAD_URL', 'http://example.com/model.pt'
            ), mock.patch.object(smd, '_state', {
                'status': 'error',
                'stage': 'error',
                'progress': 0,
                'downloaded_bytes': 0,
                'total_bytes': smd.ESTIMATED_MODEL_SIZE_BYTES,
                'error': 'network error',
            }):
                status = smd.get_sam_model_status()

            self.assertFalse(status['exists'])
            self.assertTrue(status['resumable'])
            self.assertEqual(status['downloaded_bytes'], 50 * 1024 * 1024)
            self.assertGreater(status['progress'], 0)

    def test_download_resumes_with_range_header(self):
        with tempfile.TemporaryDirectory() as tmp:
            model_path = os.path.join(tmp, 'sam3', 'model.pt')
            partial_path = f'{model_path}.downloading'
            os.makedirs(os.path.dirname(model_path), exist_ok=True)
            existing = b'a' * (120 * 1024 * 1024)
            with open(partial_path, 'wb') as f:
                f.write(existing)

            captured = {}

            class FakeResponse:
                status = 206
                headers = {
                    'Content-Length': str(len(existing)),
                    'Content-Range': f'bytes {len(existing)}-{len(existing) * 2 - 1}/{len(existing) * 2}',
                }

                def __enter__(self):
                    return self

                def __exit__(self, *args):
                    return False

                def getcode(self):
                    return 206

                def read(self, size=-1):
                    if not hasattr(self, '_sent'):
                        self._sent = True
                        return existing
                    return b''

            def fake_urlopen(req, timeout=0):
                captured['range'] = req.headers.get('Range')
                return FakeResponse()

            with mock.patch.object(smd, 'SAM_MODEL_PATH', model_path), mock.patch(
                'urllib.request.urlopen', side_effect=fake_urlopen
            ):
                smd._download_http_with_progress('http://example.com/model.pt', partial_path)

            self.assertEqual(captured.get('range'), f'bytes={len(existing)}-')
            self.assertEqual(os.path.getsize(partial_path), len(existing) * 2)


if __name__ == '__main__':
    unittest.main()
