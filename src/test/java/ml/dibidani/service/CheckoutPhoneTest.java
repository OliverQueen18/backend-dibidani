package ml.dibidani.service;

import jakarta.validation.Validation;
import ml.dibidani.api.dto.CheckoutDtos.CustomerStep;
import ml.dibidani.api.dto.OrderDtos.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutPhoneTest {
  @Test void requiresRealPhoneDigitsForBothCheckoutEndpoints() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (String phone : new String[]{"", "   ", "+223 ", "........", "abcdefgh", "1234567890123456"}) {
        assertFalse(validator.validateValue(CustomerStep.class, "phone", phone).isEmpty(), phone);
        assertFalse(validator.validateValue(CreateOrderRequest.class, "customerPhone", phone).isEmpty(), phone);
      }
      for (String phone : new String[]{"76123456", "+223 76 12 34 56", "+33 6 12 34 56 78"}) {
        assertTrue(validator.validateValue(CustomerStep.class, "phone", phone).isEmpty(), phone);
        assertTrue(validator.validateValue(CreateOrderRequest.class, "customerPhone", phone).isEmpty(), phone);
      }
      assertFalse(validator.validateValue(CreateOrderRequest.class, "customerPhone", null).isEmpty());
    }
  }
}
