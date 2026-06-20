"""yolo_validator 单元测试。"""
import unittest
import unittest.mock

from app.utils.yolo_validator import (
    _inspect_checkpoint,
    _is_yolov5_style_checkpoint,
    _load_torch_checkpoint,
)


class TestYoloValidatorHelpers(unittest.TestCase):
    def test_detect_yolov5_checkpoint_blob(self):
        self.assertTrue(_is_yolov5_style_checkpoint("models.yolo.Model"))
        self.assertTrue(_is_yolov5_style_checkpoint("/home/ubuntu/算法模型 best.pt yolov5"))

    def test_inspect_checkpoint_rejects_yolov5(self):
        ckpt = {'model': 'models.yolo.Detect', 'train_args': '/home/ubuntu/算法模型'}
        version, method, reject = _inspect_checkpoint(ckpt)
        self.assertIsNone(version)
        self.assertEqual(reject, 'yolov5')

    def test_load_torch_checkpoint_detects_models_yolo(self):
        with unittest.mock.patch('app.utils.yolo_validator.torch') as mock_torch:
            mock_torch.load.side_effect = ModuleNotFoundError("No module named 'models.yolo'")
            with self.assertRaises(Exception) as ctx:
                _load_torch_checkpoint('/fake/path.pt')
            self.assertIn('YOLOv5', str(ctx.exception))


if __name__ == '__main__':
    unittest.main()
