"""MinIO download proxy response header tests."""
import unittest
from types import SimpleNamespace
from unittest.mock import MagicMock, patch
from urllib.parse import unquote

from flask import Flask
from werkzeug.test import Client
from werkzeug.wrappers import Response

from app.blueprints.minio_proxy import minio_proxy_bp


class TestMinioProxy(unittest.TestCase):
    def setUp(self):
        self.app = Flask(__name__)
        self.app.register_blueprint(minio_proxy_bp)
        self.client = Client(self.app, Response)

    def assert_unicode_filename(self, response, disposition, filename):
        header = response.headers['Content-Disposition']
        header.encode('latin-1', 'strict')
        self.assertTrue(header.startswith(f'{disposition}; '), header)
        self.assertIn('filename="download.jpg"', header)

        marker = "filename*=UTF-8''"
        self.assertIn(marker, header)
        encoded_filename = header.split(marker, 1)[1]
        self.assertEqual(unquote(encoded_filename), filename)

    def test_downloads_minio_object_with_chinese_filename(self):
        content = b'jpeg-data'
        object_key = 'nested/path/中文封面.jpg'
        minio_response = MagicMock()
        minio_response.read.return_value = content
        minio_client = MagicMock()
        minio_client.bucket_exists.return_value = True
        minio_client.stat_object.return_value = SimpleNamespace(content_type='image/jpeg')
        minio_client.get_object.return_value = minio_response

        with patch(
            'app.utils.service_urls.minio_storage_enabled', return_value=True
        ), patch(
            'app.blueprints.minio_proxy.ModelService.get_minio_client',
            return_value=minio_client,
        ):
            response = self.client.get(
                '/api/v1/buckets/snap-space/objects/download',
                query_string={'prefix': object_key},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, content)
        self.assertEqual(response.content_type, 'image/jpeg')
        self.assert_unicode_filename(response, 'attachment', '中文封面.jpg')
        minio_client.get_object.assert_called_once_with('snap-space', object_key)
        minio_response.close.assert_called_once_with()
        minio_response.release_conn.assert_called_once_with()

    def test_downloads_local_object_with_chinese_filename(self):
        content = b'local-image'
        with patch(
            'app.utils.service_urls.minio_storage_enabled', return_value=False
        ), patch(
            'app.services.local_storage_service.read_local_object',
            return_value=(content, 'image/jpeg', None),
        ):
            response = self.client.get(
                '/api/v1/buckets/snap-space/objects/download',
                query_string={'prefix': '中文封面.jpg'},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, content)
        self.assertEqual(response.content_type, 'image/jpeg')
        self.assert_unicode_filename(response, 'inline', '中文封面.jpg')

    def test_preserves_ascii_filename(self):
        with patch(
            'app.utils.service_urls.minio_storage_enabled', return_value=False
        ), patch(
            'app.services.local_storage_service.read_local_object',
            return_value=(b'image', 'image/jpeg', None),
        ):
            response = self.client.get(
                '/api/v1/buckets/snap-space/objects/download',
                query_string={'prefix': 'cover.jpg'},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.headers['Content-Disposition'],
            'inline; filename="cover.jpg"',
        )


if __name__ == '__main__':
    unittest.main()
