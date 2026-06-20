"""service_urls 单元测试。"""
import os
import unittest
from unittest.mock import patch

from app.utils.service_urls import (
    is_mini_deploy_profile,
    minio_storage_enabled,
    resolve_alert_hook_url,
    should_use_gateway_for_video_api,
    is_local_filesystem_path,
    build_alert_image_api_url,
    build_snap_image_api_url,
)


class TestServiceUrls(unittest.TestCase):
    def setUp(self):
        self._env = os.environ.copy()

    def tearDown(self):
        os.environ.clear()
        os.environ.update(self._env)

    def test_mini_profile_uses_direct_video_hook(self):
        with patch.dict(os.environ, {
            'EASYAIOT_DEPLOY_PROFILE': 'mini',
            'GATEWAY_URL': 'http://localhost:48099',
            'VIDEO_SERVICE_PORT': '6000',
        }, clear=True):
            self.assertTrue(is_mini_deploy_profile())
            self.assertFalse(should_use_gateway_for_video_api())
            self.assertEqual(
                resolve_alert_hook_url(),
                'http://127.0.0.1:6000/video/alert/hook',
            )

    def test_gateway_port_48099_without_profile_is_mini(self):
        with patch.dict(os.environ, {
            'GATEWAY_URL': 'http://localhost:48099',
            'VIDEO_SERVICE_PORT': '6000',
        }, clear=True):
            self.assertTrue(is_mini_deploy_profile())
            self.assertEqual(
                resolve_alert_hook_url(),
                'http://127.0.0.1:6000/video/alert/hook',
            )

    def test_standard_profile_not_mini_with_stale_gateway_port(self):
        with patch.dict(os.environ, {
            'EASYAIOT_DEPLOY_PROFILE': 'standard',
            'GATEWAY_URL': 'http://localhost:48099',
            'VIDEO_SERVICE_PORT': '6000',
        }, clear=True):
            self.assertFalse(is_mini_deploy_profile())
            self.assertTrue(minio_storage_enabled())
            self.assertTrue(should_use_gateway_for_video_api())
            self.assertEqual(
                resolve_alert_hook_url(),
                'http://localhost:48099/admin-api/video/alert/hook',
            )

    def test_full_gateway_uses_admin_api_prefix(self):
        with patch.dict(os.environ, {
            'GATEWAY_URL': 'http://gateway.example.com:48080',
            'VIDEO_SERVICE_PORT': '6000',
        }, clear=True):
            self.assertFalse(is_mini_deploy_profile())
            self.assertTrue(should_use_gateway_for_video_api())
            self.assertEqual(
                resolve_alert_hook_url(),
                'http://gateway.example.com:48080/admin-api/video/alert/hook',
            )

    def test_explicit_alert_hook_url_wins(self):
        with patch.dict(os.environ, {
            'ALERT_HOOK_URL': 'http://custom:7000/hook',
            'GATEWAY_URL': 'http://localhost:48099',
        }, clear=True):
            self.assertEqual(resolve_alert_hook_url(), 'http://custom:7000/hook')

    def test_mini_profile_disables_minio_storage(self):
        with patch.dict(os.environ, {
            'EASYAIOT_DEPLOY_PROFILE': 'mini',
            'GATEWAY_URL': 'http://localhost:48099',
        }, clear=True):
            self.assertFalse(minio_storage_enabled())

    def test_minio_enabled_override(self):
        with patch.dict(os.environ, {
            'EASYAIOT_DEPLOY_PROFILE': 'mini',
            'MINIO_ENABLED': 'true',
        }, clear=True):
            self.assertTrue(minio_storage_enabled())

    def test_local_filesystem_path_detection(self):
        self.assertTrue(is_local_filesystem_path('/data/snaps/a.jpg'))
        self.assertFalse(is_local_filesystem_path('/api/v1/buckets/x'))
        self.assertFalse(is_local_filesystem_path('/video/alert/image?path=x'))

    def test_build_media_api_urls(self):
        self.assertIn('/video/alert/image?path=', build_alert_image_api_url('/data/a.jpg'))
        self.assertEqual(
            build_snap_image_api_url(3, 'dev1/a.jpg'),
            '/video/snap/space/3/image/dev1/a.jpg',
        )


if __name__ == '__main__':
    unittest.main()
