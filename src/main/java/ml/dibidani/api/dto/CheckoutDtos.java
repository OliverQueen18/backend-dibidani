package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.api.dto.AccountDtos.AddressResponse;
import ml.dibidani.api.dto.CartDtos.CartView;
import ml.dibidani.api.dto.DeliveryDtos.DeliveryOption;
import ml.dibidani.api.dto.DeliveryDtos.ZoneResponse;
import ml.dibidani.api.dto.PaymentDtos.PaymentOption;
import ml.dibidani.domain.DeliveryMethod;
import ml.dibidani.domain.PaymentMethod;
import ml.dibidani.domain.PaymentProvider;
import java.util.List;

public final class CheckoutDtos {
  private CheckoutDtos() {}

  static final String PHONE = "^\\+?[0-9 ().-]{8,25}$";

  public record CustomerStep(
      @NotBlank(message = "Votre nom est obligatoire") @Size(max = 255) String fullName,
      @NotBlank(message = "Votre téléphone est obligatoire") @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String phone,
      @Email(message = "E-mail invalide") @Size(max = 255) String email) {}

  /** Soit une adresse enregistrée (addressId), soit une nouvelle adresse avec son quartier. */
  public record AddressStep(
      Long addressId,
      @Size(max = 500) String addressLine,
      Long deliveryZoneId,
      @Size(max = 500) String landmark,
      Boolean saveAddress,
      @Size(max = 100) String label) {}

  public record DeliveryStep(@NotNull(message = "Choisissez un mode de livraison") DeliveryMethod method) {}

  public record PaymentStep(
      @NotNull(message = "Choisissez un mode de paiement") PaymentMethod method,
      PaymentProvider provider,
      @Pattern(regexp = "^$|" + PHONE, message = "Numéro de paiement invalide") String phone) {}

  public record ConfirmRequest(@AssertTrue(message = "Vous devez accepter les conditions de vente") boolean acceptTerms) {}

  public record SessionView(int step, String fullName, String phone, String email,
                            Long addressId, String addressLine, ZoneResponse zone, String landmark,
                            DeliveryMethod deliveryMethod, PaymentMethod paymentMethod, PaymentProvider paymentProvider, String paymentPhone,
                            boolean customerComplete, boolean addressComplete, boolean deliveryComplete, boolean paymentComplete) {}

  public record CheckoutView(SessionView session, CartView cart, List<DeliveryOption> deliveryOptions, List<PaymentOption> paymentOptions,
                             List<AddressResponse> addresses, boolean readyToConfirm, List<String> blockers) {}
}
