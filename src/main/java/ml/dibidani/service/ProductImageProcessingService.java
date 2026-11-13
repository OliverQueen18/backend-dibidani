package ml.dibidani.service;

import ml.dibidani.config.PhotoStudioProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductImageProcessingService {
  private final PhotoStudioProperties props;
  private final ProductImageStorageService files;
  private final RestClient http;
  public ProductImageProcessingService(PhotoStudioProperties props, ProductImageStorageService files, LocalPhotoFallback fallback) {
    this.props=props; this.files=files;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(4));
    factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
    http=RestClient.builder().baseUrl(props.getServiceUrl()).requestFactory(factory).build();
  }
  public record Result(String masterUrl, String catalogUrl, String thumbUrl, String jpegUrl,
      String engine, String version, String message, String model, String format, Integer width, Integer height, Long bytes) {}

  public boolean processorUp() {
    try { http.get().uri("/health").retrieve().toBodilessEntity(); return true; }
    catch (RuntimeException e) { return false; }
  }
  public Map<?, ?> models() {
    return http.get().uri("/models").header("X-Studio-Token", props.getToken()).retrieve().body(Map.class);
  }
  public Result process(String originalRelative, Map<String, Object> options) {
    String stem=UUID.randomUUID().toString();
    String folder="studio/" + LocalDate.now().toString().substring(0,7);
    Path dest;
    try { dest=files.publicDir(); } catch (Exception e) { throw new IllegalStateException("Stockage studio indisponible"); }
    Map<?, ?> body=http.post().uri("/process").contentType(MediaType.APPLICATION_JSON)
        .header("X-Studio-Token", props.getToken())
        .body(Map.of("inputPath", files.resolveOriginal(originalRelative).toAbsolutePath().toString(),
            "outputDir", dest.toAbsolutePath().toString(), "stem", stem, "options", options))
        .retrieve().body(Map.class);
    if (body == null || !(body.get("files") instanceof Map<?, ?> names)) throw new IllegalStateException("Réponse studio invalide");
    return new Result(url(folder,names.get("master")),url(folder,names.get("catalogWebp")),
        url(folder,names.get("thumbWebp")),url(folder,names.get("masterJpeg")),
        text(body.get("engine")),text(body.get("version")),"Photo prête. Vérifiez le résultat avant de l'enregistrer.",
        text(body.get("model")),text(body.get("format")),number(body.get("width")).intValue(),
        number(body.get("height")).intValue(),number(body.get("bytes")).longValue());
  }
  private String url(String folder, Object name) {
    String n=text(name);
    if (n == null || !n.matches("[a-zA-Z0-9_-]+\\.(webp|jpg|png)")) throw new IllegalStateException("Fichier studio invalide");
    return files.publicUrl(folder+"/"+n);
  }
  private static String text(Object v) { return v == null ? null : v.toString(); }
  private static Number number(Object v) {
    if (!(v instanceof Number n)) throw new IllegalStateException("Métadonnées studio invalides");
    return n;
  }
}
