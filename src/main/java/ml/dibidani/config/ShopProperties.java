package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.shop")
public record ShopProperties(
    String name,
    String slogan,
    String phone,
    String whatsapp,
    String email,
    String address,
    String city,
    String openingHours,
    BigDecimal deliveryFee,
    BigDecimal freeDeliveryThreshold,
    String facebookUrl,
    String instagramUrl,
    String tiktokUrl) {
}
