package ml.dibidani.service;

import ml.dibidani.domain.*;
import ml.dibidani.repository.ProductImageAssetRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductPhotoAttachmentTest {
  @Test void attachesOnlyReadyDraftsOwnedByAuthor() {
    var repository = mock(ProductImageAssetRepository.class);
    var service = new ProductService(null, null, null, null, null, null, null, null, null, repository);
    var product = new Product();
    product.getImages().add("/uploads/studio/photo.png");
    var own = asset("seller@example.test", ProductImageStatus.PROCESSED);
    var foreign = asset("other@example.test", ProductImageStatus.CONFIRMED);
    var failed = asset("seller@example.test", ProductImageStatus.FAILED);
    var linked = asset("seller@example.test", ProductImageStatus.CONFIRMED);
    var otherProduct = new Product(); linked.setProduct(otherProduct);
    when(repository.findByProductIsNullAndMasterUrlIn(product.getImages()))
        .thenReturn(List.of(own, foreign, failed, linked));
    service.attachDraftImages(product, "seller@example.test");
    assertSame(product, own.getProduct());
    assertTrue(own.isConfirmed());
    assertEquals(ProductImageStatus.CONFIRMED, own.getStatus());
    assertNull(foreign.getProduct());
    assertNull(failed.getProduct());
    assertSame(otherProduct, linked.getProduct());
  }

  private ProductImageAsset asset(String email, ProductImageStatus status) {
    var owner = new AppUser(); owner.setEmail(email);
    var asset = new ProductImageAsset(); asset.setOwner(owner); asset.setStatus(status);
    return asset;
  }
}
