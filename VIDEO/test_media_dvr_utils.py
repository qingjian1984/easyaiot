"""media_dvr_utils 路径解析单元测试。"""
import os
import sys
import tempfile
import unittest

VIDEO_ROOT = os.path.dirname(os.path.abspath(__file__))
if VIDEO_ROOT not in sys.path:
    sys.path.insert(0, VIDEO_ROOT)

from app.services.media_dvr_utils import (  # noqa: E402
    discover_srs_host_data_root,
    resolve_playback_absolute_path,
)


class MediaDvrUtilsTest(unittest.TestCase):
    def setUp(self):
        self._saved = dict(os.environ)

    def tearDown(self):
        os.environ.clear()
        os.environ.update(self._saved)

    def test_discover_uses_explicit_env(self):
        with tempfile.TemporaryDirectory() as tmp:
            os.environ['SRS_HOST_DATA_ROOT'] = tmp
            self.assertEqual(discover_srs_host_data_root(), tmp)

    def test_resolve_maps_container_data_to_host_root(self):
        with tempfile.TemporaryDirectory() as tmp:
            rel = 'playbacks/ai/dev1/2026/07/02/123.flv'
            host_file = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(host_file), exist_ok=True)
            with open(host_file, 'wb') as fh:
                fh.write(b'x' * 9000)
            os.environ['SRS_HOST_DATA_ROOT'] = tmp
            mapped = resolve_playback_absolute_path(f'/data/{rel}')
            self.assertEqual(mapped, host_file)

    def test_resolve_existing_path_unchanged(self):
        with tempfile.NamedTemporaryFile(delete=False) as fh:
            path = fh.name
        try:
            self.assertEqual(resolve_playback_absolute_path(path), path)
        finally:
            os.remove(path)


if __name__ == '__main__':
    unittest.main()
