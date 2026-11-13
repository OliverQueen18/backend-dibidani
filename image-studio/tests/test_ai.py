import io
import os
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from PIL import Image, ImageDraw
from fastapi.testclient import TestClient
from app.models import ModelManager
from app.pipeline import load_image
from app.rendering import process, render, encode
from app import main


def fixture(kind="phone"):
    """Deterministic local scenes; these test geometry, not visual model quality."""
    image = Image.new("RGBA", (240, 180))
    draw = ImageDraw.Draw(image)
    if kind == "shoe": draw.polygon([(40,110),(65,65),(140,95),(200,115),(200,140),(35,140)], fill="#C52E39")
    elif kind == "garment": draw.polygon([(70,30),(95,20),(145,20),(170,30),(210,80),(175,100),(165,160),(75,160),(65,100),(30,80)], fill="#158047")
    elif kind == "appliance":
        draw.rectangle((65,15,175,165), fill="#D0D0D0"); draw.ellipse((80,60,160,140), fill="#173D55")
    elif kind == "transparent": draw.rectangle((75,30,165,160), fill=(40,120,180,100))
    else: draw.rounded_rectangle((80,20,160,160), radius=12, fill="#152736")
    if kind == "parasites": draw.ellipse((5,5,25,25), fill="#A00000")
    buffer = io.BytesIO(); image.save(buffer,"PNG")
    return image, buffer.getvalue()


class AiTests(unittest.TestCase):
    def test_all_product_shapes_transparency_and_original_preservation(self):
        for name in ["shoe", "phone", "garment", "appliance", "transparent", "parasites"]:
            with self.subTest(name=name), TemporaryDirectory() as tmp:
                image, source = fixture(name)
                with patch("app.rendering.manager.segment", return_value=image):
                    result=process(source,Path(tmp),name,{"background":"transparent","width":400,"height":800},None)
                output=Image.open(Path(tmp)/result["files"]["master"])
                self.assertEqual(output.size,(400,800))
                self.assertEqual(output.getpixel((0,0))[3],0)
                self.assertEqual(source,fixture(name)[1])
                self.assertTrue((Path(tmp)/result["files"]["thumbWebp"]).exists())

    def test_extreme_zoom_and_translation_never_clip(self):
        image, source=fixture()
        with patch("app.rendering.manager.segment",return_value=image):
            out,_=render(source,{"background":"transparent","zoom":2.2,"offsetX":0.4,"offsetY":-0.4})
        box=out.getchannel("A").getbbox()
        self.assertLess(abs((box[2]-box[0])/(box[3]-box[1])-81/141),0.02)

    def test_formats_and_custom_background(self):
        _, source=fixture()
        out,_=render(source,{"removeBackground":False,"background":"custom","backgroundColor":"#123456"})
        for fmt in ["png","webp","jpeg"]:
            decoded=Image.open(io.BytesIO(encode(out,fmt,90)))
            self.assertEqual(decoded.size,(1200,1200))
        self.assertEqual(out.getpixel((0,0))[:3],(18,52,86))

    def test_rejects_corrupt_unsupported_and_large_images(self):
        _,source=fixture()
        with self.assertRaises(ValueError): load_image(b"broken file")
        with patch("app.pipeline.MAX_PIXELS",100):
            with self.assertRaises(ValueError): load_image(source)
        with patch("app.pipeline.MAX_BYTES",30):
            with self.assertRaises(ValueError): load_image(source)
        buffer=io.BytesIO(); Image.new("RGB",(10,10)).save(buffer,"BMP")
        with self.assertRaises(ValueError): load_image(buffer.getvalue())

    def test_metadata_removed(self):
        image=Image.new("RGB",(40,20)); exif=Image.Exif(); exif[274]=6; exif[315]="private"
        b=io.BytesIO(); image.save(b,"JPEG",exif=exif)
        out=load_image(b.getvalue())
        self.assertEqual(out.size,(20,40)); self.assertFalse(out.getexif())

    def test_no_implicit_download_or_unknown_model(self):
        manager=ModelManager()
        with self.assertRaises(ValueError): manager.load("remote-url")
        with self.assertRaises(RuntimeError): manager.segment(Image.new("RGB",(20,20)))

    def test_sessions_reused_across_threads(self):
        manager=ModelManager()
        sentinel=object(); manager.sessions[manager.default]=sentinel
        with ThreadPoolExecutor(4) as pool:
            self.assertTrue(all(x is sentinel for x in pool.map(lambda _:manager.load(manager.default),range(12))))

    def test_api_auth_busy_and_path_traversal(self):
        client=TestClient(main.app)
        with patch.object(main,"TOKEN","test-only"):
            self.assertEqual(client.get('/models').status_code,401)
            headers={"X-Studio-Token":"test-only"}
            self.assertEqual(client.get('/models',headers=headers).status_code,200)
            too_large=client.post('/process-image',headers={**headers,"Content-Length":str(main.MAX_BYTES+2*1024*1024)},content=b"x")
            self.assertEqual(too_large.status_code,413)
            invalid=client.post('/process',headers=headers,json={"inputPath":"x","outputDir":"x","stem":"../escape"})
            self.assertEqual(invalid.status_code,422)
            self.assertEqual(invalid.json()["code"],"STUDIO_422")
            self.assertEqual(client.post('/process',headers=headers,json={"inputPath":"/etc/passwd","outputDir":"/tmp","stem":"safe"}).status_code,400)
            _,data=fixture()
            self.assertTrue(main.slots.acquire(blocking=False))
            try:
                result=client.post('/process-image',headers=headers,files={"file":("p.png",data,"image/png")},data={"options":'{"removeBackground":false}'})
                self.assertEqual(result.status_code,429)
            finally: main.slots.release()
            result=client.post('/process-image',headers=headers,files={"file":("p.png",data,"image/png")},data={"options":'{"removeBackground":false}'})
            self.assertEqual(result.status_code,200)


@unittest.skipUnless(os.getenv("RUN_ONNX_TEST") == "1", "Real ONNX test is opt-in after preparing the local model cache")
class RealOnnxTest(unittest.TestCase):
    def test_real_model(self):
        from app.models import manager
        manager.preload()
        _,source=fixture("phone")
        output,_=render(source,{"background":"transparent","width":400,"height":400})
        self.assertEqual(output.mode,"RGBA")
        self.assertIsNotNone(output.getchannel("A").getbbox())
        self.assertLess(output.getchannel("A").getextrema()[0],255)
