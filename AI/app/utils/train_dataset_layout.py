import json
import os
import posixpath
import shutil
from collections import defaultdict
from typing import Callable, Dict, Iterable, Optional

import yaml


IMAGE_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.bmp', '.webp', '.tif', '.tiff'}
SPLIT_JSON_NAMES = {
    'train': ('train_coco.json', 'train.json'),
    'val': ('valid_coco.json', 'val_coco.json', 'validation_coco.json', 'valid.json', 'val.json'),
    'test': ('test_coco.json', 'test.json'),
}
SPLIT_DIR_NAMES = {
    'train': ('train', 'training'),
    'val': ('val', 'valid', 'validation'),
    'test': ('test', 'testing'),
}
CLASS_NAME_FILES = ('classes.txt', 'obj.names', 'classes.names', 'input_class.txt')


def _iter_files(root_dir: str, extensions: Iterable[str]):
    allowed = {ext.lower() for ext in extensions}
    for current_root, dirs, files in os.walk(root_dir):
        dirs[:] = [name for name in dirs if name.lower() not in ('voc', 'train_results')]
        for file_name in files:
            if os.path.splitext(file_name)[1].lower() in allowed:
                yield os.path.join(current_root, file_name)


def _build_file_index(root_dir: str, extensions: Iterable[str]) -> Dict[str, Optional[str]]:
    index: Dict[str, Optional[str]] = {}
    if not os.path.isdir(root_dir):
        return index
    for file_path in _iter_files(root_dir, extensions):
        relative = os.path.relpath(file_path, root_dir).replace('\\', '/').lower()
        basename = os.path.basename(file_path).lower()
        index.setdefault(relative, file_path)
        if basename not in index:
            index[basename] = file_path
        elif index[basename] != file_path:
            index[basename] = None
    return index


def _normalize_relative_name(raw_name: str) -> str:
    normalized = posixpath.normpath(str(raw_name or '').replace('\\', '/').lstrip('/'))
    if normalized == 'images':
        return ''
    if normalized.startswith('images/'):
        normalized = normalized[len('images/'):]
    if not normalized or normalized == '.' or normalized == '..' or normalized.startswith('../'):
        raise ValueError(f'数据集文件路径无效: {raw_name}')
    return normalized


def _resolve_indexed_file(
    index: Dict[str, Optional[str]],
    relative_name: str,
    *,
    split_name: str,
) -> Optional[str]:
    for candidate in (relative_name, f'{split_name}/{relative_name}'):
        indexed = index.get(candidate.lower())
        if indexed:
            return indexed
    basename = posixpath.basename(relative_name).lower()
    if basename in index and index[basename] is None:
        raise ValueError(f'数据集存在重复文件名，无法按名称唯一匹配: {basename}')
    return index.get(basename)


def _target_relative_name(relative_name: str, split_name: str) -> str:
    parts = relative_name.split('/')
    split_aliases = {
        'train': {'train'},
        'val': {'val', 'valid', 'validation'},
        'test': {'test'},
    }
    if len(parts) > 1 and parts[0].lower() in split_aliases.get(split_name, set()):
        return '/'.join(parts[1:])
    return relative_name


def _category_signature(coco_data: dict) -> list[tuple[int, str]]:
    categories = coco_data.get('categories') or []
    return sorted(
        (
            int(row['id']),
            str(row.get('name') or row['id']),
        )
        for row in categories
        if isinstance(row, dict) and row.get('id') is not None
    )


def _find_split_json(dataset_root: str, candidate_names: Iterable[str]) -> Optional[str]:
    wanted = {name.lower() for name in candidate_names}
    for current_root, dirs, files in os.walk(dataset_root):
        depth = os.path.relpath(current_root, dataset_root).count(os.sep)
        if depth >= 2:
            dirs[:] = []
        dirs[:] = [name for name in dirs if name.lower() != 'voc']
        for file_name in files:
            if file_name.lower() in wanted:
                return os.path.join(current_root, file_name)
    return None


def _load_json(file_path: str) -> dict:
    with open(file_path, 'r', encoding='utf-8') as file_obj:
        data = json.load(file_obj)
    return data if isinstance(data, dict) else {}


def _load_existing_names(dataset_root: str) -> list[str]:
    yaml_path = os.path.join(dataset_root, 'data.yaml')
    if not os.path.isfile(yaml_path):
        return []
    try:
        with open(yaml_path, 'r', encoding='utf-8') as file_obj:
            cfg = yaml.safe_load(file_obj) or {}
        names = cfg.get('names')
        if isinstance(names, dict):
            return [str(value) for _, value in sorted(names.items(), key=lambda item: int(item[0]))]
        if isinstance(names, list):
            return [str(value) for value in names]
    except Exception:
        return []
    return []


def _category_names(coco_data: dict) -> list[str]:
    categories = coco_data.get('categories') or []
    ordered = sorted(
        (row for row in categories if isinstance(row, dict) and row.get('id') is not None),
        key=lambda row: int(row['id']),
    )
    return [str(row.get('name') or row['id']) for row in ordered]


def _write_coco_yolo_label(label_path: str, image_row: dict, annotations: list, categories: list) -> None:
    width = float(image_row.get('width') or 0)
    height = float(image_row.get('height') or 0)
    ordered_categories = sorted(
        (row for row in categories if row.get('id') is not None),
        key=lambda row: int(row['id']),
    )
    category_ids = [int(row['id']) for row in ordered_categories]
    category_to_index = {category_id: index for index, category_id in enumerate(category_ids)}
    lines = []
    if width > 0 and height > 0:
        for annotation in annotations:
            bbox = annotation.get('bbox') or []
            try:
                category_id = int(annotation.get('category_id'))
            except (TypeError, ValueError):
                continue
            if len(bbox) < 4 or category_id not in category_to_index:
                continue
            x, y, box_width, box_height = (float(value) for value in bbox[:4])
            if box_width <= 0 or box_height <= 0:
                continue
            center_x = (x + box_width / 2) / width
            center_y = (y + box_height / 2) / height
            normalized_width = box_width / width
            normalized_height = box_height / height
            lines.append(
                f'{category_to_index[category_id]} {center_x:.6f} {center_y:.6f} '
                f'{normalized_width:.6f} {normalized_height:.6f}'
            )
    os.makedirs(os.path.dirname(label_path), exist_ok=True)
    with open(label_path, 'w', encoding='utf-8') as file_obj:
        file_obj.write('\n'.join(lines))
        if lines:
            file_obj.write('\n')


def _find_child_dir(parent: str, candidate_names: Iterable[str]) -> Optional[str]:
    if not os.path.isdir(parent):
        return None
    children = {
        name.lower(): os.path.join(parent, name)
        for name in os.listdir(parent)
        if os.path.isdir(os.path.join(parent, name))
    }
    for candidate_name in candidate_names:
        if candidate_name.lower() in children:
            return children[candidate_name.lower()]
    return None


def _split_first_layout_root(dataset_root: str) -> Optional[str]:
    candidates = [dataset_root]
    if os.path.isdir(dataset_root):
        candidates.extend(
            os.path.join(dataset_root, name)
            for name in os.listdir(dataset_root)
            if os.path.isdir(os.path.join(dataset_root, name))
        )
    for candidate in candidates:
        train_dir = _find_child_dir(candidate, SPLIT_DIR_NAMES['train'])
        val_dir = _find_child_dir(candidate, SPLIT_DIR_NAMES['val'])
        if train_dir and val_dir:
            return candidate
    return None


def _load_class_names_file(*roots: str) -> list[str]:
    for root_dir in roots:
        for file_name in CLASS_NAME_FILES:
            file_path = os.path.join(root_dir, file_name)
            if not os.path.isfile(file_path):
                continue
            with open(file_path, 'r', encoding='utf-8-sig') as file_obj:
                content = file_obj.read().strip()
            if not content:
                continue
            raw_names = content.split(',') if '\n' not in content and ',' in content else content.splitlines()
            names = [name.strip() for name in raw_names if name.strip()]
            if names:
                return names
    return []


def _count_split_files(split_dir: str) -> tuple[int, int]:
    image_dir = _find_child_dir(split_dir, ('images',))
    label_dir = _find_child_dir(split_dir, ('labels',))
    if not image_dir or not label_dir:
        return 0, 0
    image_count = sum(1 for _ in _iter_files(image_dir, IMAGE_EXTENSIONS))
    label_count = sum(1 for _ in _iter_files(label_dir, {'.txt'}))
    return image_count, label_count


def _repair_split_first_yolo_layout(
    dataset_root: str,
    log_fn: Optional[Callable[[str], None]] = None,
) -> bool:
    layout_root = _split_first_layout_root(dataset_root)
    if not layout_root:
        return False

    split_paths = {}
    split_counts = {}
    for split_name, aliases in SPLIT_DIR_NAMES.items():
        split_dir = _find_child_dir(layout_root, aliases)
        if not split_dir:
            continue
        image_dir = _find_child_dir(split_dir, ('images',))
        image_count, label_count = _count_split_files(split_dir)
        if not image_dir or image_count == 0 or label_count == 0:
            continue
        split_paths[split_name] = os.path.relpath(image_dir, layout_root).replace('\\', '/')
        split_counts[split_name] = image_count

    if 'train' not in split_paths or 'val' not in split_paths:
        return False

    names = _load_class_names_file(layout_root, dataset_root)
    if not names:
        return False

    normalized_cfg = {
        'train': split_paths['train'],
        'val': split_paths['val'],
        'nc': len(names),
        'names': names,
    }
    if 'test' in split_paths:
        normalized_cfg['test'] = split_paths['test']
    with open(os.path.join(layout_root, 'data.yaml'), 'w', encoding='utf-8') as file_obj:
        yaml.safe_dump(normalized_cfg, file_obj, allow_unicode=True, sort_keys=False)

    if log_fn:
        log_fn(
            '已根据 YOLO train/val 目录生成 data.yaml: '
            f'train={split_counts["train"]}, val={split_counts["val"]}, '
            f'test={split_counts.get("test", 0)}'
        )
    return True


def repair_flat_coco_yolo_layout(
    dataset_root: str,
    log_fn: Optional[Callable[[str], None]] = None,
) -> bool:
    image_root = os.path.join(dataset_root, 'images')
    label_root = os.path.join(dataset_root, 'labels')
    if not os.path.isdir(image_root):
        return _repair_split_first_yolo_layout(dataset_root, log_fn)

    split_data = {}
    for split_name, candidate_names in SPLIT_JSON_NAMES.items():
        json_path = _find_split_json(dataset_root, candidate_names)
        if json_path:
            split_data[split_name] = _load_json(json_path)
    if 'train' not in split_data or 'val' not in split_data:
        return _repair_split_first_yolo_layout(dataset_root, log_fn)

    image_index = _build_file_index(image_root, IMAGE_EXTENSIONS)
    label_index = _build_file_index(label_root, {'.txt'})
    if not image_index:
        return False

    moved_counts = {'train': 0, 'val': 0, 'test': 0}
    first_coco = split_data['train']
    categories = first_coco.get('categories') or []
    category_signature = _category_signature(first_coco)
    for split_name, coco_data in split_data.items():
        split_signature = _category_signature(coco_data)
        if split_signature and split_signature != category_signature:
            raise ValueError(f'数据集 {split_name} 类别定义不一致，请统一各 split 的 categories')

    for split_name, coco_data in split_data.items():
        annotations_by_image = defaultdict(list)
        for annotation in coco_data.get('annotations') or []:
            annotations_by_image[annotation.get('image_id')].append(annotation)

        target_image_dir = os.path.join(image_root, split_name)
        target_label_dir = os.path.join(label_root, split_name)
        os.makedirs(target_image_dir, exist_ok=True)
        os.makedirs(target_label_dir, exist_ok=True)

        for image_row in coco_data.get('images') or []:
            raw_name = str(image_row.get('file_name') or '').replace('\\', '/')
            relative_name = _normalize_relative_name(raw_name)
            source_image = _resolve_indexed_file(
                image_index,
                relative_name,
                split_name=split_name,
            )
            if not source_image or not os.path.isfile(source_image):
                continue

            target_relative = _target_relative_name(relative_name, split_name)
            target_image = os.path.join(target_image_dir, *target_relative.split('/'))
            os.makedirs(os.path.dirname(target_image), exist_ok=True)
            if os.path.exists(target_image) and os.path.abspath(source_image) != os.path.abspath(target_image):
                raise ValueError(f'数据集目标图片已存在，拒绝覆盖: {target_image}')
            if os.path.abspath(source_image) != os.path.abspath(target_image):
                shutil.move(source_image, target_image)

            source_label_relative = os.path.splitext(relative_name)[0] + '.txt'
            source_label = _resolve_indexed_file(
                label_index,
                source_label_relative,
                split_name=split_name,
            )
            label_relative = os.path.splitext(target_relative)[0] + '.txt'
            target_label = os.path.join(target_label_dir, *label_relative.split('/'))
            if source_label and os.path.isfile(source_label):
                os.makedirs(os.path.dirname(target_label), exist_ok=True)
                if os.path.exists(target_label) and os.path.abspath(source_label) != os.path.abspath(target_label):
                    raise ValueError(f'数据集目标标签已存在，拒绝覆盖: {target_label}')
                if os.path.abspath(source_label) != os.path.abspath(target_label):
                    shutil.move(source_label, target_label)
            else:
                _write_coco_yolo_label(
                    target_label,
                    image_row,
                    annotations_by_image.get(image_row.get('id'), []),
                    categories,
                )
            moved_counts[split_name] += 1

    if moved_counts['train'] == 0 or moved_counts['val'] == 0:
        return False

    existing_names = _load_existing_names(dataset_root)
    category_names = _category_names(first_coco)
    if existing_names and category_names and existing_names != category_names:
        raise ValueError('data.yaml names 与 COCO categories 不一致，请统一类别定义')
    names = existing_names or category_names or ['object']
    normalized_cfg = {
        'train': 'images/train',
        'val': 'images/val',
        'nc': len(names),
        'names': names,
    }
    if moved_counts['test'] > 0:
        normalized_cfg['test'] = 'images/test'
    with open(os.path.join(dataset_root, 'data.yaml'), 'w', encoding='utf-8') as file_obj:
        yaml.safe_dump(normalized_cfg, file_obj, allow_unicode=True, sort_keys=False)

    if log_fn:
        log_fn(
            '已自动整理混合格式数据集: '
            f'train={moved_counts["train"]}, val={moved_counts["val"]}, '
            f'test={moved_counts["test"]}'
        )
    return True
