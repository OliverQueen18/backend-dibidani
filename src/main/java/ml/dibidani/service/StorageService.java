package ml.dibidani.service;

import jakarta.annotation.PostConstruct;
import ml.dibidani.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Stockage local des images (volume Docker). Remplaçable par S3/MinIO sans changer l'API d'upload. */
@Service
public class StorageService {
  private static final Logger log = LoggerFactory.getLogger(StorageService.class);
  private static final long MAX_BYTES = 20L * 1024 * 1024;
  private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");
  private static final int LIBRARY_LIMIT = 240;

  public record MediaItem(String url, String name, long bytes, Instant modified) {}

  private final Path root;
  private final String publicPrefix;

  public StorageService(@Value("${app.uploads.dir:uploads}") String dir, @Value("${app.uploads.public-prefix:/uploads}") String publicPrefix) {
    this.root = Path.of(dir).toAbsolutePath().normalize();
    this.publicPrefix = publicPrefix;
  }

  @PostConstruct
  void init() throws IOException {
    Files.createDirectories(root);
    if (!Files.isWritable(root)) {
      log.error("Dossier uploads non inscriptible : {}", root);
    }
  }

  public Path root() { return root; }
  public String publicPrefix() { return publicPrefix; }

  public String store(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("Aucun fichier reçu");
    if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Image trop volumineuse (20 Mo maximum)");
    String ext = sniffExtension(file);
    String folder = LocalDate.now().toString().substring(0, 7);
    Path dir = root.resolve(folder);
    Files.createDirectories(dir);
    String name = UUID.randomUUID() + "." + ext;
    try (InputStream in = file.getInputStream()) {
      Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IOException("Impossible d'enregistrer l'image (dossier uploads). " + e.getMessage(), e);
    }
    return publicPrefix + "/" + folder + "/" + name;
  }

  public List<MediaItem> listPublic() throws IOException {
    if (!Files.isDirectory(root)) return List.of();
    try (Stream<Path> walk = Files.walk(root)) {
      return walk
          .filter(Files::isRegularFile)
          .filter(this::isLibraryFile)
          .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
          .limit(LIBRARY_LIMIT)
          .map(this::toItem)
          .toList();
    }
  }

  public void deletePublic(String url) throws IOException {
    Path file = resolvePublic(url);
    if (!Files.isRegularFile(file)) throw new NotFoundException("Image introuvable");
    Files.delete(file);
  }

  Path resolvePublic(String url) {
    if (url == null || url.isBlank()) throw new IllegalArgumentException("Aucune image");
    String path = url.trim();
    int idx = path.indexOf(publicPrefix + "/");
    if (idx >= 0) path = path.substring(idx);
    if (!path.startsWith("/")) path = "/" + path;
    if (!path.startsWith(publicPrefix + "/")) throw new IllegalArgumentException("Image hors bibliothèque");
    String rel = path.substring(publicPrefix.length() + 1).replace('\\', '/');
    if (rel.contains("..") || rel.startsWith("private/") || rel.contains("/private/")) {
      throw new SecurityException("Chemin refusé");
    }
    Path p = root.resolve(rel).normalize();
    if (!p.startsWith(root)) throw new SecurityException("Chemin refusé");
    return p;
  }

  private boolean isLibraryFile(Path p) {
    String rel = root.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    if (rel.startsWith("private/") || rel.contains("/private/")) return false;
    int dot = rel.lastIndexOf('.');
    if (dot < 0) return false;
    return IMAGE_EXT.contains(rel.substring(dot + 1));
  }

  private MediaItem toItem(Path p) {
    String rel = root.relativize(p).toString().replace('\\', '/');
    long bytes = 0;
    Instant modified = Instant.EPOCH;
    try {
      bytes = Files.size(p);
      modified = Files.getLastModifiedTime(p).toInstant();
    } catch (IOException ignored) {}
    return new MediaItem(publicPrefix + "/" + rel, p.getFileName().toString(), bytes, modified);
  }

  static String sniffExtension(MultipartFile file) throws IOException {
    byte[] h = new byte[12];
    try (InputStream in = file.getInputStream()) {
      if (in.readNBytes(h, 0, h.length) < 4) throw new IllegalArgumentException("Fichier image corrompu");
    }
    boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8;
    boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
    boolean gif = h[0] == 'G' && h[1] == 'I' && h[2] == 'F';
    boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    if (jpeg) return "jpg";
    if (png) return "png";
    if (gif) return "gif";
    if (webp) return "webp";
    throw new IllegalArgumentException("Format accepté : JPEG, PNG, WebP ou GIF");
  }
}
