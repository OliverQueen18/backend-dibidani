package ml.dibidani.service;

import ml.dibidani.domain.ProductImageJob;
import ml.dibidani.domain.ProductImageJobStatus;
import ml.dibidani.domain.ProductImageStatus;
import ml.dibidani.repository.ProductImageAssetRepository;
import ml.dibidani.repository.ProductImageJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;

@Service
public class ProductImageJobService {
  private static final Logger log = LoggerFactory.getLogger(ProductImageJobService.class);
  private final ProductImageJobRepository jobs;
  private final ProductImageAssetRepository assets;
  private final ProductImageProcessingService processing;
  private final ObjectMapper json;

  public ProductImageJobService(ProductImageJobRepository jobs, ProductImageAssetRepository assets,
                                ProductImageProcessingService processing, ObjectMapper json) {
    this.jobs = jobs;
    this.assets = assets;
    this.processing = processing;
    this.json = json;
  }

  @Async("photoStudioExecutor")
  public void run(Long jobId) {
    ProductImageJob job = jobs.findWithAsset(jobId).orElse(null);
    if (job == null) return;
    job.setStatus(ProductImageJobStatus.PROCESSING);
    job.setProgress(10);
    job.setMessage("Traitement en cours…");
    job.setStartedAt(Instant.now());
    jobs.save(job);
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> options = json.readValue(job.getOptionsJson() == null ? "{}" : job.getOptionsJson(), Map.class);
      var result = processing.process(job.getAsset().getOriginalPath(), options);
      var asset = job.getAsset();
      asset.setMasterUrl(result.masterUrl());
      asset.setCatalogUrl(result.catalogUrl());
      asset.setThumbUrl(result.thumbUrl());
      asset.setJpegUrl(result.jpegUrl());
      asset.setEngine(result.engine());
      asset.setProcessingVersion(result.version());
      asset.setStatus(ProductImageStatus.PROCESSED);
      assets.save(asset);
      job.setStatus(ProductImageJobStatus.COMPLETED);
      job.setProgress(100);
      job.setMessage(result.message());
      job.setFinishedAt(Instant.now());
      jobs.save(job);
    } catch (Exception e) {
      log.warn("Job studio {} en échec : {}", jobId, e.getMessage());
      String msg = e.getMessage() == null ? "Le traitement a échoué" : e.getMessage();
      job.setStatus(ProductImageJobStatus.FAILED);
      job.setProgress(100);
      job.setMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
      job.setFinishedAt(Instant.now());
      jobs.save(job);
      var a = job.getAsset();
      a.setStatus(ProductImageStatus.FAILED);
      assets.save(a);
    }
  }
}
