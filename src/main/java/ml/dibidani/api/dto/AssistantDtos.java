package ml.dibidani.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import java.util.List;

public final class AssistantDtos {
  private AssistantDtos() {}

  public record ChatRequest(
      @NotBlank(message = "Écrivez ou dictez votre demande") @Size(max = 500) String message,
      Integer cartCount,
      @Size(max = 12) List<Long> lastProductIds) {}

  public record ChatAction(String type, Long productId, Integer quantity, String path) {}

  public record ChatReply(
      String reply,
      String intent,
      String query,
      List<ProductSummary> products,
      List<String> suggestions,
      ChatAction action) {}
}
