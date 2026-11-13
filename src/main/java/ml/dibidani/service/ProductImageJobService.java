package ml.dibidani.service;

import ml.dibidani.domain.ProductImageJobStatus;
import ml.dibidani.domain.ProductImageStatus;
import ml.dibidani.repository.ProductImageAssetRepository;
import ml.dibidani.repository.ProductImageJobRepository;
import ml.dibidani.config.PhotoStudioProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;

@Service
public class ProductImageJobService {
  private final ProductImageJobRepository jobs;
  private final ProductImageAssetRepository assets;
  private final ProductImageProcessingService processing;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  private final PhotoStudioProperties props;
  public ProductImageJobService(ProductImageJobRepository jobs, ProductImageAssetRepository assets,
      ProductImageProcessingService processing, ObjectMapper json, TransactionTemplate tx, PhotoStudioProperties props) {
    this.jobs=jobs; this.assets=assets; this.processing=processing; this.json=json; this.tx=tx; this.props=props;
  }
  private record Work(Long id, String original, String options) {}

  @Scheduled(fixedDelay = 1000, scheduler = "photoStudioScheduler")
  public void drain() {
    Work work = tx.execute(status -> {
      jobs.expireInterrupted(Instant.now().minusSeconds(props.getTimeoutSeconds() * 2L + 60));
      var job = jobs.claimNext().orElse(null);
      if (job == null) return null;
      job.setStatus(ProductImageJobStatus.PROCESSING); job.setProgress(10);
      job.setMessage("L'intelligence artificielle prépare votre photo…"); job.setStartedAt(Instant.now());
      jobs.save(job);
      return new Work(job.getId(), job.getAsset().getOriginalPath(), job.getOptionsJson());
    });
    if (work == null) return;
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> options = json.readValue(work.options(), Map.class);
      var result = processing.process(work.original(), options);
      tx.executeWithoutResult(status -> {
        var job = jobs.findWithAsset(work.id()).orElse(null);
        if (job == null || job.getStatus() != ProductImageJobStatus.PROCESSING) return;
        var asset = job.getAsset();
        asset.setMasterUrl(result.masterUrl()); asset.setCatalogUrl(result.catalogUrl());
        asset.setThumbUrl(result.thumbUrl()); asset.setJpegUrl(result.jpegUrl());
        asset.setEngine(result.engine()); asset.setProcessingVersion(result.version());
        asset.setModel(result.model()); asset.setOutputFormat(result.format());
        asset.setOutputWidth(result.width()); asset.setOutputHeight(result.height()); asset.setOutputBytes(result.bytes());
        asset.setStatus(ProductImageStatus.PROCESSED); assets.save(asset);
        job.setStatus(ProductImageJobStatus.COMPLETED); job.setProgress(100);
        job.setMessage(result.message()); job.setFinishedAt(Instant.now()); jobs.save(job);
      });
    } catch (Exception error) {
      tx.executeWithoutResult(status -> {
        var job = jobs.findWithAsset(work.id()).orElse(null);
        if (job == null) return;
        job.setStatus(ProductImageJobStatus.FAILED); job.setProgress(100);
        job.setMessage("Traitement impossible ou délai dépassé. L'original est conservé ; vous pouvez réessayer.");
        job.setFinishedAt(Instant.now()); jobs.save(job);
        job.getAsset().setStatus(ProductImageStatus.FAILED); assets.save(job.getAsset());
      });
    }
  }
}
