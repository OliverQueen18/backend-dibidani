package ml.dibidani.service;

import ml.dibidani.api.dto.DeliveryDtos.AssignRequest;
import ml.dibidani.common.ConflictException;
import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryAssignmentTest {
  private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
  private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
  private final AppUserRepository users = mock(AppUserRepository.class);
  private final DeliveryService service = new DeliveryService(mock(DeliveryZoneRepository.class), deliveries, orders, users, mock(ShopService.class));
  private CustomerOrder order;
  private Delivery delivery;
  private AppUser courier;

  @BeforeEach void setup() {
    order = new CustomerOrder(); order.setId(1L); order.setStatus(OrderStatus.READY_FOR_DELIVERY); order.setDeliveryMethod(DeliveryMethod.STANDARD);
    delivery = new Delivery(); delivery.setId(2L); delivery.setOrder(order); delivery.setMethod(DeliveryMethod.STANDARD);
    courier = new AppUser(); courier.setId(3L); courier.setRole(Role.COURIER); courier.setFullName("Amadou"); courier.setPhone("+223 70000000");
    when(orders.findById(1L)).thenReturn(Optional.of(order));
    when(deliveries.findByOrderId(1L)).thenReturn(Optional.of(delivery));
    when(users.findById(3L)).thenReturn(Optional.of(courier));
  }

  @Test void assignsActiveCourierAndCopiesContactInformation() {
    var result = service.assign(1L, new AssignRequest(3L, "Ignored", "Ignored", " Appeler avant arrivée "));
    assertEquals(3L, result.courierId()); assertEquals("Amadou", result.courierName());
    assertEquals("+223 70000000", result.courierPhone()); assertEquals("Appeler avant arrivée", result.notes());
    assertEquals(DeliveryStatus.ASSIGNED, result.status()); assertNotNull(result.assignedAt());
  }

  @Test void rejectsInactiveCourierOrOtherRole() {
    courier.setActive(false);
    assertThrows(IllegalArgumentException.class, () -> service.assign(1L, new AssignRequest(3L, null, null, null)));
    courier.setActive(true); courier.setRole(Role.ADMIN);
    assertThrows(IllegalArgumentException.class, () -> service.assign(1L, new AssignRequest(3L, null, null, null)));
    assertNull(delivery.getCourier());
  }

  @Test void rejectsPickupAndClosedOrders() {
    order.setDeliveryMethod(DeliveryMethod.PICKUP);
    assertThrows(ConflictException.class, () -> service.assign(1L, new AssignRequest(3L, null, null, null)));
    order.setDeliveryMethod(DeliveryMethod.STANDARD);
    for (var status : new OrderStatus[]{OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.REFUNDED}) {
      order.setStatus(status);
      assertThrows(ConflictException.class, () -> service.assign(1L, new AssignRequest(3L, null, null, null)));
    }
  }

  @Test void reassignmentPreservesDeliveryProgressAndUpdatesAssignmentTime() {
    order.setStatus(OrderStatus.OUT_FOR_DELIVERY); delivery.setStatus(DeliveryStatus.PICKED_UP);
    delivery.setAssignedAt(Instant.EPOCH); delivery.setPickedUpAt(Instant.EPOCH);
    var result = service.assign(1L, new AssignRequest(3L, null, null, "Nouvelle tournée"));
    assertEquals(DeliveryStatus.PICKED_UP, result.status()); assertEquals(Instant.EPOCH, result.pickedUpAt());
    assertTrue(result.assignedAt().isAfter(Instant.EPOCH));
  }

  @Test void emptyAssignmentCannotEraseExistingCourier() {
    delivery.setCourier(courier); delivery.setCourierName(courier.getFullName());
    assertThrows(IllegalArgumentException.class, () -> service.assign(1L, new AssignRequest(null, " ", null, null)));
    assertEquals(courier, delivery.getCourier());
  }
}
