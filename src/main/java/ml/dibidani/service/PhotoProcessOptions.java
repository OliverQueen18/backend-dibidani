package ml.dibidani.service;

import ml.dibidani.api.dto.PhotoStudioDtos.ProcessRequest;
import java.util.Map;
import java.util.Set;

final class PhotoProcessOptions {
  private PhotoProcessOptions() {}
  static void apply(Map<String, Object> out, ProcessRequest r) {
    out.put("engine", "rembg");
    out.put("recenter", r.recenter() == null || r.recenter());
    if (r.model() != null) {
      choice(out, "model", r.model(), Set.of("isnet-general-use", "u2net", "silueta", "birefnet-general", "birefnet-general-lite"));
    }
    choice(out, "background", r.background() != null ? r.background() : Boolean.FALSE.equals(r.whiteBackground()) ? "transparent" : "white", Set.of("white", "transparent", "gray", "custom"));
    choice(out, "format", r.format() == null ? "webp" : r.format(), Set.of("webp", "png", "jpeg"));
    if (r.backgroundColor() != null) {
      if (!r.backgroundColor().matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Couleur invalide");
      out.put("backgroundColor", r.backgroundColor());
    }
    number(out, "width", r.width(), 100, 2400);
    number(out, "height", r.height(), 100, 2400);
    number(out, "quality", r.quality(), 40, 100);
    number(out, "brightness", r.brightness(), 0.5, 1.5);
    number(out, "contrast", r.contrast(), 0.5, 1.5);
    if (r.rotation() != null) {
      if (!Set.of(0, 90, 180, 270).contains(r.rotation())) throw new IllegalArgumentException("Rotation invalide");
      out.put("rotation", r.rotation());
    }
    if (r.refineEdges() != null) out.put("refineEdges", r.refineEdges());
  }
  private static void number(Map<String, Object> out, String name, Number v, double min, double max) {
    if (v == null) return;
    if (!Double.isFinite(v.doubleValue()) || v.doubleValue() < min || v.doubleValue() > max) throw new IllegalArgumentException("Paramètre invalide : " + name);
    out.put(name, v);
  }
  private static void choice(Map<String, Object> out, String name, String v, Set<String> allowed) {
    if (!allowed.contains(v)) throw new IllegalArgumentException("Paramètre invalide : " + name);
    out.put(name, v);
  }
}
