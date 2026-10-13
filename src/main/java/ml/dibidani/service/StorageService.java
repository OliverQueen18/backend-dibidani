package ml.dibidani.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Stockage local des images (volume Docker). Remplaçable par S3/MinIO sans changer l'API d'upload. */
@Service
public class StorageService {
  private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");

  private final Path root;
  private final String publicPrefix;

  public StorageService(@Value("${app.uploads.dir:uploads}") String dir, @Value("${app.uploads.public-prefix:/uploads}") String publicPrefix) {
    this.root = Path.of(dir).toAbsolutePath().normalize();
    this.publicPrefix = publicPrefix;
  }

  public Path root() { return root; }
  public String publicPrefix() { return publicPrefix; }

  public String store(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("Aucun fichier reçu");
    String ext = EXTENSIONS.get(file.getContentType());
    if (ext == null || !hasImageSignature(file)) throw new IllegalArgumentException("Format accepté : JPEG, PNG, WebP ou GIF");
    String folder = LocalDate.now().toString().substring(0, 7);
    Path dir = root.resolve(folder);
    Files.createDirectories(dir);
    String name = UUID.randomUUID() + "." + ext;
    try (InputStream in = file.getInputStream()) {
      Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }
    return publicPrefix + "/" + folder + "/" + name;
  }

  private static boolean hasImageSignature(MultipartFile file) throws IOException {
    byte[] h = new byte[12];
    try (InputStream in = file.getInputStream()) {
      if (in.readNBytes(h, 0, h.length) < 4) return false;
    }
    boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8;
    boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
    boolean gif = h[0] == 'G' && h[1] == 'I' && h[2] == 'F';
    boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    return jpeg || png || gif || webp;
  }
}
