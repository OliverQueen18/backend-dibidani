package ml.dibidani.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProductImageStorageServiceTest {
  @TempDir Path tmp;

  @Test
  void rejectsPathTraversal() {
    var storage = new StorageService(tmp.toString(), "/uploads");
    var studio = new ProductImageStorageService(storage);
    assertThrows(SecurityException.class, () -> studio.safeFile("../secret.txt"));
    assertThrows(SecurityException.class, () -> studio.safeFile("..\\windows"));
    Path ok = studio.safeFile("private/originals/x.jpg");
    assertTrue(ok.startsWith(tmp.toAbsolutePath().normalize()));
  }
}
