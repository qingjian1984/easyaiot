"""告警录像查询与时间解析单元测试。"""
import unittest
from datetime import datetime, timezone

from app.utils import service_urls as svc_urls


class TestAlertTimeHelpers(unittest.TestCase):
    def test_parse_alert_time_str_shanghai(self):
        aware, err = svc_urls.parse_alert_time_str('2026-06-20 12:00:00')
        self.assertIsNone(err)
        self.assertEqual(aware.tzinfo, svc_urls.SHANGHAI_TZ)
        self.assertEqual(aware.hour, 12)

    def test_parse_alert_time_str_invalid(self):
        aware, err = svc_urls.parse_alert_time_str('bad-time')
        self.assertIsNone(aware)
        self.assertIn('格式错误', err)

    def test_normalize_to_shanghai_naive(self):
        utc = datetime(2026, 6, 20, 4, 0, 0, tzinfo=timezone.utc)
        naive = svc_urls.normalize_to_shanghai_naive(utc)
        self.assertIsNone(naive.tzinfo)
        self.assertEqual(naive.hour, 12)

    def test_legacy_playback_tz_matches_alert(self):
        from types import SimpleNamespace

        alert_time = datetime(2026, 6, 21, 0, 38, 40, tzinfo=svc_urls.SHANGHAI_TZ)
        playback = SimpleNamespace(
            event_time=datetime(2026, 6, 20, 16, 38, 40, tzinfo=svc_urls.SHANGHAI_TZ),
            duration=30,
        )
        score = svc_urls.score_playback_for_alert(playback, alert_time, 300)
        self.assertIsNotNone(score)
        self.assertEqual(score, 0)

    def test_epoch_to_shanghai_datetime(self):
        # 2026-06-20 16:38:40 UTC = 2026-06-21 00:38:40 +08:00
        ts = datetime(2026, 6, 20, 16, 38, 40, tzinfo=timezone.utc).timestamp()
        sh = svc_urls.epoch_to_shanghai_datetime(ts)
        self.assertEqual(sh.hour, 0)
        self.assertEqual(sh.day, 21)


if __name__ == '__main__':
    unittest.main()
