package ml.dibidani.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {
  @TempDir Path tmp;

  @Test
  void storesJpegWhenBrowserOmitsContentType() throws Exception {
    byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    var file = new MockMultipartFile("file", "photo.bin", "application/octet-stream", jpeg);
    var url = new StorageService(tmp.toString(), "/uploads").store(file);
    assertTrue(url.startsWith("/uploads/"));
    assertTrue(url.endsWith(".jpg"));
  }

  @Test
  void rejectsNonImage() {
    var file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
    var storage = new StorageService(tmp.toString(), "/uploads");
    assertThrows(IllegalArgumentException.class, () -> storage.store(file));
  }

  @Test
  void listsPublicImagesAndRejectsPrivatePath() throws Exception {
    byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    var storage = new StorageService(tmp.toString(), "/uploads");
    String url = storage.store(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg));
    java.nio.file.Files.createDirectories(tmp.resolve("private/originals"));
    java.nio.file.Files.write(tmp.resolve("private/originals/secret.jpg"), jpeg);
    var listed = storage.listPublic();
    assertEquals(1, listed.size());
    assertEquals(url, listed.get(0).url());
    assertThrows(SecurityException.class, () -> storage.deletePublic("/uploads/private/originals/secret.jpg"));
    assertThrows(SecurityException.class, () -> storage.resolvePublic("/uploads/../secret.jpg"));
  }
}
