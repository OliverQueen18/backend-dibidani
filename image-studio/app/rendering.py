"""Non-generative segmentation, composition and export; source bytes stay untouched."""
import io
from pathlib import Path
from PIL import Image, ImageColor, ImageEnhance, ImageOps
from app.models import manager
from app.options import Options
from app.pipeline import VERSION, load_image, segment_foreground, watermark


def render(data, raw, watermark_path=None):
    options = Options.model_validate(raw)
    work = load_image(data).convert("RGBA")
    if options.removeBackground:
        if options.engine == "rembg":
            work = manager.segment(work, options.model, options.refineEdges)
        else:
            work = segment_foreground(work, "local")
    if options.rotation:
        work = work.rotate(-options.rotation, expand=True)
    if options.recenter:
        box = work.getchannel("A").getbbox()
        if box is None:
            raise ValueError("Aucun sujet détecté. Essayez une autre photo.")
        work = work.crop(box)
    alpha = work.getchannel("A")
    rgb = work.convert("RGB")
    rgb = ImageEnhance.Brightness(rgb).enhance(options.brightness * (1.03 if options.enhance else 1))
    rgb = ImageEnhance.Contrast(rgb).enhance(options.contrast * (1.08 if options.enhance else 1))
    work = rgb.convert("RGBA")
    work.putalpha(alpha)
    # Clamp zoom and translation so automatic framing never clips the product.
    fill = min(0.98, options.fillRatio * options.zoom)
    target = (max(1, int(options.width * fill)), max(1, int(options.height * fill)))
    work = ImageOps.contain(work, target, Image.Resampling.LANCZOS)
    x = round((options.width - work.width) / 2 + options.offsetX * options.width)
    y = round((options.height - work.height) / 2 + options.offsetY * options.height)
    x = max(0, min(options.width - work.width, x))
    y = max(0, min(options.height - work.height, y))
    layer = Image.new("RGBA", (options.width, options.height))
    if options.shadow:
        from PIL import ImageFilter
        shadow = Image.new("RGBA", work.size, (0, 0, 0, 0))
        shadow.putalpha(work.getchannel("A").point(lambda p: int(p * 0.18)))
        layer.alpha_composite(shadow, (x, min(options.height - work.height, y + 8)))
        layer = layer.filter(ImageFilter.GaussianBlur(8))
    layer.alpha_composite(work, (x, y))
    background = options.background
    if not options.whiteBackground and "background" not in raw:
        background = "transparent"
    if background != "transparent":
        color = {"white": "#FFFFFF", "gray": "#F2F2F2", "custom": options.backgroundColor}[background]
        base = Image.new("RGBA", layer.size, ImageColor.getrgb(color) + (255,))
        base.alpha_composite(layer)
        layer = base
    if options.watermark and watermark_path:
        layer = watermark(layer, watermark_path, options.watermarkSize, options.watermarkOpacity,
                          options.watermarkMargin, options.watermarkPosition).convert("RGBA")
    return layer, options


def encode(image, fmt, quality):
    output = io.BytesIO()
    if fmt == "jpeg":
        base = Image.new("RGB", image.size, "white")
        base.paste(image, mask=image.getchannel("A") if image.mode == "RGBA" else None)
        base.save(output, "JPEG", quality=quality, optimize=True)
    elif fmt == "webp":
        image.save(output, "WEBP", quality=quality, method=4)
    else:
        image.save(output, "PNG", optimize=True)
    return output.getvalue()


def process(data, output_dir, stem, raw, watermark_path):
    image, opts = render(data, raw, watermark_path)
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    files, sizes, written = {}, {}, []
    try:
        for name, max_size in [("master", max(image.size)), ("catalog", 600), ("thumb", 300)]:
            resized = ImageOps.contain(image, (max_size, max_size), Image.Resampling.LANCZOS)
            sizes[name] = {}
            for fmt in dict.fromkeys([opts.format, "webp", "jpeg"]):
                ext = "jpg" if fmt == "jpeg" else fmt
                path = output_dir / f"{stem}-{name}.{ext}"
                content = encode(resized, fmt, opts.quality)
                path.write_bytes(content)
                written.append(path)
                files[name + fmt.title()] = path.name
                sizes[name][fmt] = len(content)
        files["master"] = files["master" + opts.format.title()]
        return {"version": VERSION, "engine": opts.engine, "model": opts.model if opts.removeBackground else None,
                "width": image.width, "height": image.height, "format": opts.format,
                "bytes": sizes["master"][opts.format], "sizes": sizes, "files": files, "options": opts.model_dump()}
    except Exception:
        for path in written:
            path.unlink(missing_ok=True)
        raise
