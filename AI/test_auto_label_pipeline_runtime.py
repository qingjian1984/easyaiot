import json
import unittest
from datetime import datetime
from unittest.mock import patch
from zoneinfo import ZoneInfo

from app.blueprints import auto_label
from app.services import auto_label_cluster_service


class FakeResponse:
    def __init__(self, payload, status_code=200):
        self.payload = payload
        self.status_code = status_code

    def json(self):
        return self.payload


class PipelineRuntimeTest(unittest.TestCase):
    def test_fetch_frame_tasks_resolves_selected_gb28181_stream(self):
        frame_task_response = FakeResponse({
            'code': 0,
            'data': {
                'list': [
                    {
                        'id': 4,
                        'taskName': 'GB camera',
                        'taskType': 1,
                        'deviceId': '33080300002000251117',
                        'channelId': '33080351001310314002',
                        'rtmpUrl': '',
                    },
                    {
                        'id': 5,
                        'taskName': 'unselected camera',
                        'taskType': 0,
                        'rtmpUrl': 'rtsp://example.test/unselected',
                    },
                ],
            },
        })
        inference_input_response = FakeResponse({
            'code': 0,
            'data': {
                'resolved_source': 'rtsp://media.test/selected',
            },
        })

        with patch.dict(
            auto_label.os.environ,
            {'AUTO_LABEL_STREAM_RESOLVE_TIMEOUT_SEC': 'invalid'},
        ), patch.object(
            auto_label.requests,
            'get',
            side_effect=[frame_task_response, inference_input_response],
        ) as request_get:
            tasks = auto_label._fetch_frame_tasks(
                'http://gateway.test',
                6,
                frame_task_ids=[4],
                resolve_streams=True,
            )

        self.assertEqual([task['id'] for task in tasks], [4])
        self.assertEqual(tasks[0]['rtmpUrl'], 'rtsp://media.test/selected')
        self.assertEqual(request_get.call_count, 2)
        self.assertIn(
            '/admin-api/video/camera/device/'
            'gb28181_33080300002000251117_33080351001310314002/inference-input',
            request_get.call_args_list[1].args[0],
        )

    def test_pipeline_log_uses_shanghai_time(self):
        class Task:
            id = 0
            pipeline_config = '{}'

        task = Task()
        auto_label._pipeline_log(task, 'timezone-probe')

        logged = datetime.fromisoformat(
            json.loads(task.pipeline_config)['logs'][-1]['time']
        )
        expected = datetime.now(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None)
        self.assertLess(abs((expected - logged).total_seconds()), 5)

    def test_platform_worker_command_runs_inside_ai_container(self):
        command, deploy_env = auto_label_cluster_service._platform_worker_command(
            {
                'AI_ROOT': '/opt/easyaiot/AI',
                'DATASET_ID': '5',
                'JWT_TOKEN': 'secret-token',
            },
            gpu_ids='2',
        )

        self.assertEqual(command[:4], ['docker', 'exec', '-w', '/app/services/auto_label_worker'])
        self.assertEqual(
            command[-3:],
            ['ai-service', '/opt/conda/bin/python', '/app/services/auto_label_worker/run_worker.py'],
        )
        self.assertNotIn('/opt/easyaiot/AI/.bundles/ai_service/run-python.sh', command)
        self.assertNotIn('secret-token', command)
        self.assertEqual(deploy_env['AI_ROOT'], '/app')
        self.assertEqual(deploy_env['CUDA_VISIBLE_DEVICES'], '2')


if __name__ == '__main__':
    unittest.main()
