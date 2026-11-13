package ml.dibidani.service;

import ml.dibidani.api.dto.OrderDtos.OrderItemResponse;
import ml.dibidani.api.dto.StockDtos.MovementResponse;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.domain.OrderItem;
import ml.dibidani.domain.StockMovement;
import ml.dibidani.domain.StockMovementType;
import ml.dibidani.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductDeletionTest {
  @Test void deletesExistingProductAndReportsMissingProduct() {
    var products = mock(ProductRepository.class);
    var service = new ProductService(products, null, null, null, null, null, null, null, null, null);
    when(products.deletePermanently(42L)).thenReturn(1);
    service.deletePermanently(42L);
    verify(products).deletePermanently(42L);
    assertThrows(NotFoundException.class, () -> service.deletePermanently(43L));
  }

  @Test void orderSnapshotRemainsReadableAfterProductDeletion() {
    var item = new OrderItem();
    item.setProductName("Batterie"); item.setProductSku("BAT-1"); item.setQuantity(2);
    var response = OrderItemResponse.of(item);
    assertNull(response.productId()); assertNull(response.productSlug());
    assertEquals("Batterie", response.productName()); assertEquals("BAT-1", response.productSku());
    assertEquals(2, response.quantity());
  }

  @Test void stockHistoryRemainsReadableAfterProductDeletion() {
    var movement = new StockMovement(); movement.setType(StockMovementType.PURCHASE);
    movement.setQuantity(5); movement.setReference("PURCHASE-1");
    var response = MovementResponse.of(movement);
    assertNull(response.productId()); assertEquals(5, response.quantity());
    assertEquals("PURCHASE-1", response.reference());
  }
}
