"""训练数据集 YAML 解析单元测试（COCO / YOLO 兼容）。"""
import os
import tempfile
import unittest

import yaml

from app.blueprints.train import (
    _find_dataset_yaml,
    _normalize_dataset_yaml,
    _parse_class_names,
    _resolve_dataset_root,
)


class TrainDatasetYamlTest(unittest.TestCase):
    def test_parse_class_names_dict(self):
        names = _parse_class_names({0: 'person', 1: 'bicycle', 2: 'car'})
        self.assertEqual(names, ['person', 'bicycle', 'car'])

    def test_parse_class_names_list(self):
        names = _parse_class_names(['cat', 'dog'])
        self.assertEqual(names, ['cat', 'dog'])

    def test_resolve_dataset_root_with_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            dataset_dir = os.path.join(tmp, 'coco128')
            os.makedirs(dataset_dir)
            yaml_path = os.path.join(tmp, 'coco128.yaml')
            cfg = {'path': 'coco128', 'train': 'images/train2017'}
            yaml_dir = os.path.dirname(yaml_path)
            root = _resolve_dataset_root(cfg, yaml_dir)
            self.assertEqual(root, os.path.abspath(dataset_dir))

    def test_find_dataset_yaml_prefers_any_yaml_with_train(self):
        with tempfile.TemporaryDirectory() as tmp:
            with open(os.path.join(tmp, 'coco128.yaml'), 'w', encoding='utf-8') as f:
                yaml.safe_dump({'path': 'coco128', 'train': 'images/train2017', 'names': {0: 'a'}}, f)
            found = _find_dataset_yaml(tmp)
            self.assertTrue(found.endswith('coco128.yaml'))

    def test_normalize_coco128_layout(self):
        with tempfile.TemporaryDirectory() as tmp:
            dataset_dir = os.path.join(tmp, 'coco128')
            train_img_dir = os.path.join(dataset_dir, 'images', 'train2017')
            os.makedirs(train_img_dir)
            open(os.path.join(train_img_dir, '000000000009.jpg'), 'w').close()

            coco_yaml = {
                'path': 'coco128',
                'train': 'images/train2017',
                'val': 'images/train2017',
                'names': {0: 'person', 1: 'bicycle'},
            }
            with open(os.path.join(tmp, 'coco128.yaml'), 'w', encoding='utf-8') as f:
                yaml.safe_dump(coco_yaml, f)

            out = _normalize_dataset_yaml(tmp, output_dir=tmp)
            self.assertTrue(out.endswith('data.yaml'))
            with open(out, 'r', encoding='utf-8') as f:
                normalized = yaml.safe_load(f)
            self.assertEqual(normalized['nc'], 2)
            self.assertEqual(normalized['names'], ['person', 'bicycle'])
            self.assertTrue(os.path.isdir(normalized['train']))
            self.assertTrue(os.path.isdir(normalized['val']))


if __name__ == '__main__':
    unittest.main()
