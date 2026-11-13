package ml.dibidani.service;

import ml.dibidani.config.PhotoStudioProperties;
import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PhotoStudioJobTest {
  @Test @SuppressWarnings({"unchecked","rawtypes"})
  void failedInferencePreservesOriginalAndCanBeRetried() {
    var jobs=mock(ProductImageJobRepository.class); var assets=mock(ProductImageAssetRepository.class);
    var engine=mock(ProductImageProcessingService.class); var tx=mock(TransactionTemplate.class);
    when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback)i.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
    doAnswer(i -> { ((Consumer)i.getArgument(0)).accept(new SimpleTransactionStatus()); return null; }).when(tx).executeWithoutResult(any());
    var asset=new ProductImageAsset(); asset.setId(1L); asset.setOriginalPath("private/originals/immutable.png");
    var job=new ProductImageJob(); job.setId(2L); job.setAsset(asset); job.setOptionsJson("{}"); job.setStatus(ProductImageJobStatus.PENDING);
    when(jobs.claimNext()).thenReturn(Optional.of(job)); when(jobs.findWithAsset(2L)).thenReturn(Optional.of(job));
    when(engine.process(anyString(),anyMap())).thenThrow(new IllegalStateException("timeout"));
    var runner=new ProductImageJobService(jobs,assets,engine,new ObjectMapper(),tx,new PhotoStudioProperties());
    runner.drain();
    assertEquals(ProductImageJobStatus.FAILED,job.getStatus());
    assertEquals("private/originals/immutable.png",asset.getOriginalPath());
    reset(engine);
    when(engine.process(anyString(),anyMap())).thenReturn(new ProductImageProcessingService.Result(
        "/uploads/master.webp","/uploads/catalog.webp","/uploads/thumb.webp","/uploads/master.jpg",
        "rembg","2","Ready","isnet-general-use","webp",800,800,1234L));
    job.setStatus(ProductImageJobStatus.PENDING); runner.drain();
    assertEquals(ProductImageJobStatus.COMPLETED,job.getStatus()); assertEquals(1234L,asset.getOutputBytes());
    assertEquals("private/originals/immutable.png",asset.getOriginalPath());
  }
}
