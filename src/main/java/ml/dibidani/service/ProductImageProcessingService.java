package ml.dibidani.service;

import ml.dibidani.config.PhotoStudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductImageProcessingService {
  private static final Logger log = LoggerFactory.getLogger(ProductImageProcessingService.class);
  private final PhotoStudioProperties props;
  private final ProductImageStorageService files;
  private final LocalPhotoFallback fallback;
  private final RestClient http;

  public ProductImageProcessingService(PhotoStudioProperties props, ProductImageStorageService files, LocalPhotoFallback fallback) {
    this.props = props;
    this.files = files;
    this.fallback = fallback;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(4));
    factory.setReadTimeout(Duration.ofSeconds(90));
    this.http = RestClient.builder().baseUrl(props.getServiceUrl()).requestFactory(factory).build();
  }

  public record Result(String masterUrl, String catalogUrl, String thumbUrl, String jpegUrl, String engine, String version, String message) {}

  public boolean processorUp() {
    try {
      http.get().uri("/health").retrieve().toBodilessEntity();
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  public Result process(String originalRelative, Map<String, Object> options) {
    String stem = UUID.randomUUID().toString();
    String folder = "studio/" + LocalDate.now().toString().substring(0, 7);
    Path dest;
    try { dest = files.publicDir(); } catch (Exception e) { throw new IllegalStateException("Stockage studio indisponible"); }
    Path original = files.resolveOriginal(originalRelative);
    if (processorUp()) {
      try {
        Map<?, ?> body = http.post().uri("/process")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Studio-Token", props.getToken() == null ? "" : props.getToken())
            .body(Map.of(
                "inputPath", original.toAbsolutePath().toString(),
                "outputDir", dest.toAbsolutePath().toString(),
                "stem", stem,
                "options", options
            ))
            .retrieve()
            .body(Map.class);
        return urls(folder, body, String.valueOf(options.getOrDefault("engine", "local")), "Traité par le studio Dibidani");
      } catch (RestClientException e) {
        log.warn("Moteur Python indisponible, cadrage local : {}", e.getMessage());
      }
    }
    try {
      Map<String, String> filesOut = fallback.process(original, dest, stem, options);
      return new Result(
          files.publicUrl(folder + "/" + filesOut.get("masterJpeg")),
          files.publicUrl(folder + "/" + filesOut.get("catalogJpeg")),
          files.publicUrl(folder + "/" + filesOut.get("thumbJpeg")),
          files.publicUrl(folder + "/" + filesOut.get("masterJpeg")),
          "fallback", "local-1",
          "Détourage indisponible : cadrage blanc appliqué. Ajustez manuellement si besoin."
      );
    } catch (Exception e) {
      throw new IllegalStateException("Impossible de traiter cette photo");
    }
  }

  @SuppressWarnings("unchecked")
  private Result urls(String folder, Map<?, ?> body, String engine, String message) {
    Map<String, Object> filesMap = body != null && body.get("files") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    String masterWebp = str(filesMap.get("masterWebp"));
    String masterJpeg = str(filesMap.get("masterJpeg"));
    String catalog = str(filesMap.get("catalogWebp"));
    if (catalog == null) catalog = str(filesMap.get("catalogJpeg"));
    String thumb = str(filesMap.get("thumbWebp"));
    if (thumb == null) thumb = str(filesMap.get("thumbJpeg"));
    return new Result(
        files.publicUrl(folder + "/" + (masterWebp != null ? masterWebp : masterJpeg)),
        files.publicUrl(folder + "/" + catalog),
        files.publicUrl(folder + "/" + thumb),
        files.publicUrl(folder + "/" + masterJpeg),
        String.valueOf(body != null && body.get("engine") != null ? body.get("engine") : engine),
        String.valueOf(body != null && body.get("version") != null ? body.get("version") : "1"),
        message
    );
  }

  private static String str(Object o) { return o == null ? null : o.toString(); }
}
