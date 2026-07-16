from types import SimpleNamespace
import unittest

from app.blueprints.train_task import _resolve_publish_image_url


class TrainTaskPublishImageTest(unittest.TestCase):
    def test_new_model_does_not_generate_remote_preview(self):
        self.assertIsNone(_resolve_publish_image_url(None))

    def test_existing_custom_preview_is_preserved(self):
        model = SimpleNamespace(image_url='/api/v1/buckets/models/objects/download?prefix=images/custom.png')

        self.assertEqual(_resolve_publish_image_url(model), model.image_url)

    def test_legacy_generated_preview_is_removed(self):
        model = SimpleNamespace(
            image_url=(
                '/api/v1/buckets/models/objects/download?'
                'prefix=images/default_model_6ae04b0e0c6e4daba014722c9ca07b49.png'
            )
        )

        self.assertIsNone(_resolve_publish_image_url(model))


if __name__ == '__main__':
    unittest.main()
