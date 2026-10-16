import tempfile
import unittest
from pathlib import Path

from PIL import Image

from pipeline import process_image


class PipelineTest(unittest.TestCase):
    def test_writes_catalog_files(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            src = root / "in.jpg"
            Image.new("RGB", (640, 480), (40, 120, 200)).save(src, "JPEG")
            dest = root / "out"
            files = process_image(src, dest, "t", {"watermark": False, "fillRatio": 0.8})
            self.assertTrue((dest / files["masterJpeg"]).is_file())
            self.assertTrue((dest / files["catalogJpeg"]).is_file())
            self.assertTrue((dest / files["thumbWebp"]).is_file())
            with Image.open(dest / files["masterJpeg"]) as master:
                self.assertEqual(master.size, (1200, 1200))


if __name__ == "__main__":
    unittest.main()
