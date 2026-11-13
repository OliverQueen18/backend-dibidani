"""Pipeline de studio photo : détourage, fond blanc, cadrage, amélioration, signature."""
from __future__ import annotations

import io
import logging
import os
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter, ImageOps

log = logging.getLogger("dibidani.studio")

VERSION = "2.0.0"
MAX_EDGE = 4000
MAX_PIXELS = int(os.getenv("MAX_IMAGE_PIXELS", "20000000"))
MAX_BYTES = int(os.getenv("MAX_IMAGE_MB", "10")) * 1024 * 1024
FILL_RATIO = 0.82


def _register_heif() -> None:
    try:
        import pillow_heif
        pillow_heif.register_heif_opener()
    except Exception:
        pass


_register_heif()


def load_image(data: bytes) -> Image.Image:
    if len(data) > MAX_BYTES:
        raise ValueError("Image trop volumineuse")
    if not data or len(data) < 24:
        raise ValueError("Fichier image vide ou corrompu")
    try:
        img = Image.open(io.BytesIO(data))
        if img.format not in {"JPEG", "PNG", "WEBP", "GIF", "HEIF"}:
            raise ValueError("Format non autorisé")
        if img.width * img.height > MAX_PIXELS:
            raise ValueError("Trop de pixels dans cette image")
        if getattr(img, "n_frames", 1) != 1:
            raise ValueError("Les images animées ne sont pas acceptées")
        img.verify()
        img = Image.open(io.BytesIO(data))
        img.load()
    except Exception as e:
        raise ValueError("Format d'image illisible") from e
    img = ImageOps.exif_transpose(img)
    img.getexif().clear()
    img.info.clear()
    if img.mode not in ("RGB", "RGBA"):
        img = img.convert("RGBA") if "A" in img.getbands() else img.convert("RGB")
    w, h = img.size
    if w * h > MAX_PIXELS or max(w, h) > MAX_EDGE:
        scale = min(MAX_EDGE / max(w, h), (MAX_PIXELS / (w * h)) ** 0.5)
        img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.Resampling.LANCZOS)
    return img


def _to_bgr(img: Image.Image) -> np.ndarray:
    rgb = np.array(img.convert("RGB"))
    return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)


def _corner_background_mask(bgr: np.ndarray) -> np.ndarray:
    h, w = bgr.shape[:2]
    samples = [
        bgr[2:12, 2:12], bgr[2:12, w - 12:w - 2],
        bgr[h - 12:h - 2, 2:12], bgr[h - 12:h - 2, w - 12:w - 2],
    ]
    mean = np.mean([s.reshape(-1, 3).mean(axis=0) for s in samples], axis=0)
    diff = np.linalg.norm(bgr.astype(np.float32) - mean, axis=2)
    bg = (diff < 38).astype(np.uint8) * 255
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))
    bg = cv2.morphologyEx(bg, cv2.MORPH_CLOSE, kernel, iterations=2)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(bg, 8)
    keep = np.zeros_like(bg)
    for i in range(1, n):
        x, y, bw, bh, _ = stats[i]
        if x == 0 or y == 0 or x + bw >= w - 1 or y + bh >= h - 1:
            keep[labels == i] = 255
    return keep


def _grabcut_mask(bgr: np.ndarray) -> np.ndarray:
    h, w = bgr.shape[:2]
    mask = np.zeros((h, w), np.uint8)
    bgd = np.zeros((1, 65), np.float64)
    fgd = np.zeros((1, 65), np.float64)
    rect = (max(1, w // 12), max(1, h // 12), max(2, w - w // 6), max(2, h - h // 6))
    try:
        cv2.grabCut(bgr, mask, rect, bgd, fgd, 4, cv2.GC_INIT_WITH_RECT)
    except cv2.error:
        return np.full((h, w), 255, np.uint8)
    fg = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    return fg


def segment_foreground(img: Image.Image, engine: str = "local") -> Image.Image:
    """Retourne RGBA. Si le détourage est trop agressif, l'image entière est conservée."""
    if engine == "rembg":
        cut = _rembg(img)
        if cut is not None:
            return cut
    bgr = _to_bgr(img)
    h, w = bgr.shape[:2]
    grab = _grabcut_mask(bgr)
    corners = _corner_background_mask(bgr)
    alpha = cv2.bitwise_and(grab, cv2.bitwise_not(corners))
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    alpha = cv2.morphologyEx(alpha, cv2.MORPH_CLOSE, kernel, iterations=2)
    alpha = cv2.GaussianBlur(alpha, (5, 5), 0)
    coverage = float(np.count_nonzero(alpha > 40)) / float(w * h)
    if coverage < 0.06 or coverage > 0.98:
        rgba = img.convert("RGBA")
        return rgba
    rgb = np.array(img.convert("RGB"))
    out = np.dstack([rgb, alpha])
    return Image.fromarray(out, "RGBA")


def _rembg(img: Image.Image) -> Image.Image | None:
    from app.models import manager
    return manager.segment(img)


def bounding_box(rgba: Image.Image, threshold: int = 12) -> tuple[int, int, int, int]:
    alpha = np.array(rgba.getchannel("A"))
    ys, xs = np.where(alpha > threshold)
    if len(xs) == 0:
        return 0, 0, rgba.width, rgba.height
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def square_canvas(rgba: Image.Image, fill_ratio: float = FILL_RATIO, offset_x: float = 0, offset_y: float = 0, zoom: float = 1.0) -> Image.Image:
    x0, y0, x1, y1 = bounding_box(rgba)
    crop = rgba.crop((x0, y0, x1, y1))
    bw, bh = crop.size
    fill_ratio = min(0.92, max(0.55, fill_ratio))
    zoom = min(2.2, max(0.6, zoom))
    side = max(int(max(bw, bh) / fill_ratio / zoom), 1)
    canvas = Image.new("RGBA", (side, side), (255, 255, 255, 255))
    dx = int((side - bw) / 2 + offset_x * side)
    dy = int((side - bh) / 2 + offset_y * side)
    canvas.alpha_composite(crop, (dx, dy))
    white = Image.new("RGBA", canvas.size, (255, 255, 255, 255))
    return Image.alpha_composite(white, canvas)


def apply_shadow(img: Image.Image, enabled: bool) -> Image.Image:
    if not enabled:
        return img
    rgba = img.convert("RGBA")
    alpha = np.array(rgba.getchannel("A"))
    ys, xs = np.where(alpha > 20)
    if len(xs) == 0:
        return img
    shadow = Image.new("RGBA", rgba.size, (255, 255, 255, 0))
    blob = Image.new("L", rgba.size, 0)
    draw = np.zeros(rgba.size[::-1], np.uint8)
    cx, cy = int(xs.mean()), int(ys.max())
    cv2.ellipse(draw, (cx, min(rgba.height - 8, cy + 6)), (max(20, (xs.max() - xs.min()) // 3), 10), 0, 0, 360, 90, -1)
    blob = Image.fromarray(cv2.GaussianBlur(draw, (21, 21), 0), "L")
    sh = Image.merge("RGBA", (Image.new("L", rgba.size, 20),) * 3 + (blob,))
    base = Image.new("RGBA", rgba.size, (255, 255, 255, 255))
    base.alpha_composite(sh)
    base.alpha_composite(rgba)
    return base


def enhance(img: Image.Image) -> Image.Image:
    rgb = img.convert("RGB")
    rgb = ImageOps.autocontrast(rgb, cutoff=1)
    rgb = ImageEnhance.Color(rgb).enhance(1.04)
    rgb = ImageEnhance.Contrast(rgb).enhance(1.08)
    rgb = ImageEnhance.Brightness(rgb).enhance(1.03)
    rgb = rgb.filter(ImageFilter.UnsharpMask(radius=1.2, percent=85, threshold=3))
    return rgb


def watermark(img: Image.Image, mark_path: str | None, size_ratio: float, opacity: float, margin_ratio: float, position: str) -> Image.Image:
    if not mark_path or not os.path.isfile(mark_path):
        return img
    mark = Image.open(mark_path).convert("RGBA")
    w = img.width
    target = max(16, int(w * size_ratio))
    ratio = target / max(mark.width, 1)
    mark = mark.resize((max(1, int(mark.width * ratio)), max(1, int(mark.height * ratio))), Image.Resampling.LANCZOS)
    if opacity < 1:
        a = mark.split()[-1].point(lambda p: int(p * opacity))
        mark.putalpha(a)
    margin = int(w * margin_ratio)
    x, y = margin, margin
    if "RIGHT" in position.upper():
        x = img.width - mark.width - margin
    if "BOTTOM" in position.upper():
        y = img.height - mark.height - margin
    if "CENTER" in position.upper() and "BOTTOM" not in position.upper() and "TOP" not in position.upper():
        x = (img.width - mark.width) // 2
        y = (img.height - mark.height) // 2
    base = img.convert("RGBA")
    base.alpha_composite(mark, (max(0, x), max(0, y)))
    return base


def resize_square(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.Resampling.LANCZOS)


def save_webp_jpeg(img: Image.Image, dest_stem: Path) -> dict[str, int]:
    dest_stem.parent.mkdir(parents=True, exist_ok=True)
    webp = dest_stem.with_suffix(".webp")
    jpeg = dest_stem.with_suffix(".jpg")
    rgb = img.convert("RGB")
    rgb.save(webp, "WEBP", quality=82, method=4)
    rgb.save(jpeg, "JPEG", quality=85, optimize=True, progressive=True)
    return {"webp": webp.stat().st_size, "jpeg": jpeg.stat().st_size}


def process_file(input_path: Path, output_dir: Path, stem: str, options: dict[str, Any], watermark_path: str | None, uploads_root: Path) -> dict[str, Any]:
    src = input_path.resolve()
    root = uploads_root.resolve()
    if root not in src.parents and src != root:
        raise ValueError("Chemin d'image refusé")
    if not src.is_file():
        raise FileNotFoundError("Image source introuvable")
    return process(src.read_bytes(), output_dir, stem, options, watermark_path)


def process(data, output_dir, stem, options, watermark_path):
    from app.rendering import process as render_process
    return render_process(data, output_dir, stem, options, watermark_path)
