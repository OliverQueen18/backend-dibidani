from __future__ import annotations

from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

MASTER = 1200
CATALOG = 600
THUMB = 300


def _num(options: dict[str, Any], key: str, default: float) -> float:
    v = options.get(key, default)
    if isinstance(v, bool) or v is None:
        return default
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def _flag(options: dict[str, Any], key: str, default: bool = False) -> bool:
    v = options.get(key, default)
    if isinstance(v, str):
        return v.strip().lower() in {"1", "true", "yes", "oui"}
    return bool(v)


def _open(path: Path) -> Image.Image:
    img = Image.open(path)
    img.load()
    if img.mode in {"RGBA", "LA"} or (img.mode == "P" and "transparency" in img.info):
        rgba = img.convert("RGBA")
        bg = Image.new("RGB", rgba.size, (255, 255, 255))
        bg.paste(rgba, mask=rgba.getchannel("A"))
        return bg
    return img.convert("RGB")


def _flatten(src: Image.Image) -> Image.Image:
    """Remplace un fond assez uni (coins similaires) par du blanc."""
    rgb = src.convert("RGB")
    w, h = rgb.size
    corners = [
        _sample(rgb, 0, 0, 12),
        _sample(rgb, max(0, w - 12), 0, 12),
        _sample(rgb, 0, max(0, h - 12), 12),
        _sample(rgb, max(0, w - 12), max(0, h - 12), 12),
    ]
    if _max_spread(corners) > 70:
        return src
    bg = tuple(sum(c[i] for c in corners) // 4 for i in range(3))
    thresh = 42 + min(36, sum(bg) // 24)
    px = rgb.load()
    out = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    dest = out.load()
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            if _dist(p, bg) <= thresh:
                dest[x, y] = (255, 255, 255, 0)
            else:
                dest[x, y] = (p[0], p[1], p[2], 255)
    return out


def _sample(img: Image.Image, x: int, y: int, size: int) -> tuple[int, int, int]:
    crop = img.crop((x, y, min(img.width, x + size), min(img.height, y + size)))
    pixels = [crop.getpixel((i % crop.width, i // crop.width)) for i in range(crop.width * crop.height)]
    if not pixels:
        return (255, 255, 255)
    n = len(pixels)
    return (sum(p[0] for p in pixels) // n, sum(p[1] for p in pixels) // n, sum(p[2] for p in pixels) // n)


def _dist(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5


def _max_spread(colors: list[tuple[int, int, int]]) -> float:
    best = 0.0
    for i, a in enumerate(colors):
        for b in colors[i + 1 :]:
            best = max(best, _dist(a, b))
    return best


def _watermark(canvas: Image.Image, options: dict[str, Any], mark_path: Path | None) -> None:
    side = canvas.width
    size_ratio = max(0.11, _num(options, "watermarkSize", 0.11))
    margin_ratio = _num(options, "watermarkMargin", 0.03)
    opacity = max(0.35, min(1.0, _num(options, "watermarkOpacity", 0.82)))
    tw = max(48, int(side * size_ratio))
    margin = int(side * margin_ratio)
    pos = str(options.get("watermarkPosition") or "BOTTOM_RIGHT").upper()
    mark = None
    if mark_path and mark_path.is_file():
        try:
            mark = Image.open(mark_path).convert("RGBA")
        except OSError:
            mark = None
    if mark is not None:
        th = max(16, int(mark.height * (tw / max(1, mark.width))))
        mark = mark.resize((tw, th), Image.Resampling.LANCZOS)
        alpha = mark.split()[-1].point(lambda p: int(p * opacity))
        mark.putalpha(alpha)
        x, y = margin, margin
        if "RIGHT" in pos:
            x = side - tw - margin
        elif "CENTER" in pos:
            x = (side - tw) // 2
        if "BOTTOM" in pos:
            y = side - th - margin
        canvas.paste(mark, (x, y), mark)
        return
    draw = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.load_default(size=max(22, tw // 3))
    except TypeError:
        font = ImageFont.load_default()
    label = "Dibidani"
    box = draw.textbbox((0, 0), label, font=font)
    twt, tht = box[2] - box[0], box[3] - box[1]
    x = side - twt - margin if "RIGHT" in pos else margin
    y = side - tht - margin if "BOTTOM" in pos else margin
    color = (8, 122, 71, int(opacity * 255))
    draw.text((x, y), label, font=font, fill=color)


def _place(src: Image.Image, options: dict[str, Any]) -> Image.Image:
    canvas = Image.new("RGB", (MASTER, MASTER), (255, 255, 255))
    fill = _num(options, "fillRatio", 0.82)
    zoom = _num(options, "zoom", 1.0)
    ox = _num(options, "offsetX", 0.0)
    oy = _num(options, "offsetY", 0.0)
    target = int(MASTER * fill * zoom)
    scale = min(target / max(1, src.width), target / max(1, src.height))
    dw = max(1, int(src.width * scale))
    dh = max(1, int(src.height * scale))
    product = src.resize((dw, dh), Image.Resampling.LANCZOS)
    if _flag(options, "enhance", True):
        product = ImageEnhance.Contrast(product).enhance(1.08)
        product = ImageEnhance.Sharpness(product).enhance(1.12)
        product = ImageEnhance.Color(product).enhance(1.04)
    x = (MASTER - dw) // 2 + int(ox * MASTER)
    y = (MASTER - dh) // 2 + int(oy * MASTER)
    if _flag(options, "shadow", False):
        shadow = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
        blob = Image.new("L", (dw, dh), 90)
        blob = blob.filter(ImageFilter.GaussianBlur(18))
        shadow.paste(blob, (x + 12, y + 18))
        canvas = canvas.convert("RGBA")
        canvas.paste(shadow, (0, 0), shadow)
        canvas = canvas.convert("RGB")
    if product.mode == "RGBA":
        canvas = canvas.convert("RGBA")
        canvas.paste(product, (x, y), product)
        canvas = canvas.convert("RGB")
    else:
        canvas.paste(product, (x, y))
    return canvas


def _save(img: Image.Image, dest: Path, size: int, quality: int) -> None:
    out = img.resize((size, size), Image.Resampling.LANCZOS) if img.size != (size, size) else img
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.suffix.lower() == ".webp":
        out.save(dest, "WEBP", quality=quality, method=4)
    else:
        out.save(dest, "JPEG", quality=quality, optimize=True, subsampling=1)


def process_image(
    input_path: Path,
    output_dir: Path,
    stem: str,
    options: dict[str, Any] | None = None,
    watermark_path: Path | None = None,
    engine: str = "local",
) -> dict[str, str]:
    options = options or {}
    src = _open(input_path)
    if _flag(options, "removeBackground") or _flag(options, "whiteBackground"):
        src = _flatten(src)
    canvas = _place(src, options)
    if _flag(options, "watermark", True):
        _watermark(canvas, options, watermark_path)
    output_dir.mkdir(parents=True, exist_ok=True)
    names = {
        "masterJpeg": f"{stem}-master.jpg",
        "masterWebp": f"{stem}-master.webp",
        "catalogJpeg": f"{stem}-catalog.jpg",
        "catalogWebp": f"{stem}-catalog.webp",
        "thumbJpeg": f"{stem}-thumb.jpg",
        "thumbWebp": f"{stem}-thumb.webp",
    }
    _save(canvas, output_dir / names["masterJpeg"], MASTER, 86)
    _save(canvas, output_dir / names["masterWebp"], MASTER, 82)
    _save(canvas, output_dir / names["catalogJpeg"], CATALOG, 84)
    _save(canvas, output_dir / names["catalogWebp"], CATALOG, 80)
    _save(canvas, output_dir / names["thumbJpeg"], THUMB, 82)
    _save(canvas, output_dir / names["thumbWebp"], THUMB, 78)
    names["engine"] = engine
    return names
