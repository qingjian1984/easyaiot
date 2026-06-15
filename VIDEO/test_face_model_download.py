"""人脸模型断点下载单元测试"""
import os
import tempfile
import unittest
import zipfile
from unittest import mock

from app.utils import face_model_download as fmd


class TestFaceModelDownloadHelpers(unittest.TestCase):
    def test_build_status_shows_resumable_partial(self):
        with tempfile.TemporaryDirectory() as tmp:
            model_path = os.path.join(tmp, 'face_rec.onnx')
            zip_path = os.path.join(tmp, 'buffalo_l.zip.downloading')
            with open(zip_path, 'wb') as f:
                f.write(b'x' * (50 * 1024 * 1024))

            with mock.patch.object(fmd, 'FACE_MATCH_MODEL_PATH', model_path), mock.patch.object(
                fmd, '_state', {
                    'status': 'error',
                    'stage': 'error',
                    'progress': 0,
                    'downloaded_bytes': 0,
                    'total_bytes': fmd.ESTIMATED_ZIP_SIZE_BYTES,
                    'error': 'network error',
                }
            ):
                status = fmd.get_face_rec_model_status()

            self.assertFalse(status['exists'])
            self.assertTrue(status['resumable'])
            self.assertEqual(status['downloaded_bytes'], 50 * 1024 * 1024)
            self.assertGreater(status['progress'], 0)

    def test_is_zip_complete(self):
        with tempfile.TemporaryDirectory() as tmp:
            zip_path = os.path.join(tmp, 'buffalo_l.zip')
            onnx_path = os.path.join(tmp, 'w600k_r50.onnx')
            with open(onnx_path, 'wb') as f:
                f.write(b'0' * 1024)
            with zipfile.ZipFile(zip_path, 'w') as zf:
                zf.write(onnx_path, arcname='w600k_r50.onnx')
            self.assertTrue(fmd._is_zip_complete(zip_path))

    def test_download_resumes_with_range_header(self):
        with tempfile.TemporaryDirectory() as tmp:
            model_path = os.path.join(tmp, 'face_rec.onnx')
            zip_path = os.path.join(tmp, 'buffalo_l.zip.downloading')
            existing = b'a' * (20 * 1024 * 1024)
            with open(zip_path, 'wb') as f:
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

            with mock.patch.object(fmd, 'FACE_MATCH_MODEL_PATH', model_path), mock.patch(
                'urllib.request.urlopen', side_effect=fake_urlopen
            ):
                fmd._download_with_progress('http://example.com/buffalo_l.zip', zip_path)

            self.assertEqual(captured.get('range'), f'bytes={len(existing)}-')
            self.assertEqual(os.path.getsize(zip_path), len(existing) * 2)


if __name__ == '__main__':
    unittest.main()
