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
from app.utils.train_dataset_layout import repair_flat_coco_yolo_layout


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

    def test_normalize_nested_yolo_layout_without_split_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            dataset_dir = os.path.join(tmp, 'crowdperson(DST1336)')
            for split_name in ('train', 'valid', 'test'):
                os.makedirs(os.path.join(dataset_dir, 'images', split_name))
                os.makedirs(os.path.join(dataset_dir, 'labels', split_name))
            with open(os.path.join(dataset_dir, 'data.yaml'), 'w', encoding='utf-8') as file_obj:
                yaml.safe_dump({'names': ['people'], 'nc': 1}, file_obj)

            out = _normalize_dataset_yaml(tmp, output_dir=tmp)

            with open(out, 'r', encoding='utf-8') as file_obj:
                normalized = yaml.safe_load(file_obj)
            self.assertEqual(normalized['train'], os.path.join(dataset_dir, 'images', 'train'))
            self.assertEqual(normalized['val'], os.path.join(dataset_dir, 'images', 'valid'))
            self.assertEqual(normalized['test'], os.path.join(dataset_dir, 'images', 'test'))

    def test_generate_yaml_for_split_first_yolo_layout(self):
        with tempfile.TemporaryDirectory() as tmp:
            for split_name in ('train', 'val'):
                image_dir = os.path.join(tmp, split_name, 'images')
                label_dir = os.path.join(tmp, split_name, 'labels')
                os.makedirs(image_dir)
                os.makedirs(label_dir)
                open(os.path.join(image_dir, 'sample.jpg'), 'wb').close()
                with open(os.path.join(label_dir, 'sample.txt'), 'w', encoding='utf-8') as file_obj:
                    file_obj.write('0 0.5 0.5 0.2 0.2\n')
            with open(os.path.join(tmp, 'classes.txt'), 'w', encoding='utf-8') as file_obj:
                file_obj.write('rider\nhead\nhelmet\n')

            repaired = repair_flat_coco_yolo_layout(tmp)

            self.assertTrue(repaired)
            with open(os.path.join(tmp, 'data.yaml'), 'r', encoding='utf-8') as file_obj:
                generated = yaml.safe_load(file_obj)
            self.assertEqual(generated['train'], 'train/images')
            self.assertEqual(generated['val'], 'val/images')
            self.assertEqual(generated['nc'], 3)
            self.assertEqual(generated['names'], ['rider', 'head', 'helmet'])


if __name__ == '__main__':
    unittest.main()
