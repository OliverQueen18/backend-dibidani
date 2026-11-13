import io
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import numpy as np
from PIL import Image

from app.pipeline import bounding_box, load_image, process, square_canvas, watermark


class PipelineTests(unittest.TestCase):
    def _product(self, bg=(40, 40, 50), fg=(200, 30, 30), size=400, box=(80, 60, 320, 340)) -> bytes:
        im = Image.new("RGB", (size, size), bg)
        px = im.load()
        x0, y0, x1, y1 = box
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = fg
        buf = io.BytesIO()
        im.save(buf, format="JPEG", quality=90)
        return buf.getvalue()

    def test_corrupted_file(self):
        with self.assertRaises(ValueError):
            load_image(b"not-an-image")

    def test_white_background_and_center(self):
        data = self._product()
        with TemporaryDirectory() as tmp:
            out = process(data, Path(tmp), "p", {"engine": "local", "removeBackground": True, "whiteBackground": True, "watermark": False, "enhance": True}, None)
            master = Image.open(Path(tmp) / out["files"]["masterWebp"])
            self.assertEqual(master.size, (1200, 1200))
            corners = [master.getpixel((4, 4)), master.getpixel((1195, 4)), master.getpixel((4, 1195))]
            for c in corners:
                self.assertGreater(sum(c[:3]) / 3, 240)

    def test_no_distortion_long_product(self):
        data = self._product(size=500, box=(200, 20, 300, 480), fg=(10, 10, 10), bg=(240, 240, 240))
        img = load_image(data).convert("RGBA")
        boxed = square_canvas(img, fill_ratio=0.82)
        self.assertEqual(boxed.width, boxed.height)

    def test_watermark_bottom_right(self):
        canvas = Image.new("RGB", (400, 400), (255, 255, 255))
        mark = Image.new("RGBA", (40, 40), (8, 122, 71, 255))
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "mark.png"
            mark.save(path)
            out = watermark(canvas, str(path), 0.1, 1.0, 0.05, "BOTTOM_RIGHT")
            self.assertNotEqual(out.getpixel((379, 379))[:3], (255, 255, 255))
            self.assertEqual(out.getpixel((10, 10))[:3], (255, 255, 255))

    def test_variants_exist(self):
        data = self._product()
        with TemporaryDirectory() as tmp:
            out = process(data, Path(tmp), "v", {"engine": "local", "watermark": False}, None)
            for name in out["files"].values():
                self.assertTrue((Path(tmp) / name).is_file())

    def test_bounding_box_ignores_empty_alpha(self):
        im = Image.new("RGBA", (50, 50), (255, 255, 255, 0))
        self.assertEqual(bounding_box(im), (0, 0, 50, 50))


if __name__ == "__main__":
    unittest.main()
