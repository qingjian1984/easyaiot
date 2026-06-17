"""model_resolver 模块单元测试。"""
import json
import os
import sys
import tempfile
import unittest

_REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
_LIB_ROOT = os.path.join(_REPO_ROOT, '.scripts', 'lib')
for _p in (_LIB_ROOT,):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from model_resolver import (  # noqa: E402
    get_model_cluster_dir,
    is_cluster_synced,
    pick_weights_ref,
    resolve_cluster_model_path,
    sync_model_weights_to_cluster,
)


class ModelResolverTest(unittest.TestCase):
    def setUp(self):
        self._saved = dict(os.environ)
        self._tmpdir = tempfile.mkdtemp()
        os.environ['AI_MODELS_DIR'] = os.path.join(self._tmpdir, 'ai', 'models')
        os.environ['CLUSTER_MODE'] = 'true'

    def tearDown(self):
        os.environ.clear()
        os.environ.update(self._saved)

    def test_pick_weights_prefers_onnx(self):
        ref, fmt = pick_weights_ref({
            'model_path': '/a.pt',
            'onnx_model_path': '/b.onnx',
        })
        self.assertEqual(ref, '/b.onnx')
        self.assertEqual(fmt, 'onnx')

    def test_sync_local_file(self):
        src = os.path.join(self._tmpdir, 'weights.onnx')
        with open(src, 'wb') as f:
            f.write(b'onnx-bytes')

        record = {
            'id': 42,
            'name': 'test',
            'version': '1.0.0',
            'model_origin': 'upload',
            'onnx_model_path': src,
        }
        ok, msg, path = sync_model_weights_to_cluster(42, record)
        self.assertTrue(ok, msg)
        self.assertTrue(path and os.path.isfile(path))
        self.assertTrue(is_cluster_synced(42))
        self.assertEqual(resolve_cluster_model_path(42), path)

    def test_cluster_dir_from_env(self):
        self.assertEqual(get_model_cluster_dir(7), os.path.join(self._tmpdir, 'ai', 'models', '7'))


if __name__ == '__main__':
    unittest.main()
