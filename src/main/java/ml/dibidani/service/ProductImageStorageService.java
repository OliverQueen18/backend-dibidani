package ml.dibidani.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class ProductImageStorageService {
  private static final Set<String> OK = Set.of("jpg", "jpeg", "png", "webp", "gif", "heic", "heif");
  private final StorageService storage;

  public ProductImageStorageService(StorageService storage) { this.storage = storage; }

  public record Stored(String relativePath, Path absolute, String mime, int width, int height, int bytes, String sha256) {}

  public Stored storeOriginal(MultipartFile file, long maxBytes) throws IOException {
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("Aucun fichier reçu");
    if (file.getSize() > maxBytes) throw new IllegalArgumentException("Image trop volumineuse (12 Mo maximum)");
    Kind kind = sniff(file);
    String folder = "private/originals/" + LocalDate.now().toString().substring(0, 7);
    Path dir = safeDir(folder);
    Files.createDirectories(dir);
    String name = java.util.UUID.randomUUID() + "." + kind.ext;
    Path dest = dir.resolve(name);
    byte[] raw = file.getBytes();
    Files.write(dest, raw);
    int w = 0, h = 0;
    try (var in = Files.newInputStream(dest)) {
      BufferedImage img = ImageIO.read(in);
      if (img != null) { w = img.getWidth(); h = img.getHeight(); }
    } catch (IOException ignored) {}
    return new Stored(folder + "/" + name, dest, kind.mime, w, h, raw.length, sha256(raw));
  }

  public Path publicDir() throws IOException {
    String folder = "studio/" + LocalDate.now().toString().substring(0, 7);
    Path dir = safeDir(folder);
    Files.createDirectories(dir);
    return dir;
  }

  public String publicUrl(String folderFile) {
    return storage.publicPrefix() + "/" + folderFile.replace('\\', '/');
  }

  public Path resolveOriginal(String relative) {
    return safeFile(relative);
  }

  public void deleteQuietly(String relative) {
    if (relative == null || relative.isBlank()) return;
    try { Files.deleteIfExists(safeFile(relative)); } catch (IOException ignored) {}
  }

  public Path safeFile(String relative) {
    Path root = storage.root();
    Path p = root.resolve(relative.replace('\\', '/')).normalize();
    if (!p.startsWith(root)) throw new SecurityException("Chemin refusé");
    return p;
  }

  private Path safeDir(String folder) {
    return safeFile(folder);
  }

  public static Kind sniff(MultipartFile file) throws IOException {
    byte[] h = new byte[16];
    try (InputStream in = file.getInputStream()) {
      if (in.readNBytes(h, 0, h.length) < 4) throw new IllegalArgumentException("Fichier image corrompu");
    }
    String declared = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8;
    boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
    boolean gif = h[0] == 'G' && h[1] == 'I' && h[2] == 'F';
    boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    boolean heic = h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
    if (jpeg) return new Kind("jpg", "image/jpeg");
    if (png) return new Kind("png", "image/png");
    if (gif) return new Kind("gif", "image/gif");
    if (webp) return new Kind("webp", "image/webp");
    if (heic && (declared.endsWith(".heic") || declared.endsWith(".heif") || looksHeic(h))) return new Kind("heic", "image/heic");
    throw new IllegalArgumentException("Format accepté : JPEG, PNG, WebP, GIF ou HEIC");
  }

  private static boolean looksHeic(byte[] h) {
    String brand = new String(h, 8, Math.min(4, h.length - 8));
    return "heic".equalsIgnoreCase(brand) || "heif".equalsIgnoreCase(brand) || "mif1".equalsIgnoreCase(brand);
  }

  private static String sha256(byte[] raw) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    } catch (Exception e) {
      return null;
    }
  }

  public record Kind(String ext, String mime) {
    public Kind {
      if (!OK.contains(ext)) throw new IllegalArgumentException("Extension refusée");
    }
  }
}
