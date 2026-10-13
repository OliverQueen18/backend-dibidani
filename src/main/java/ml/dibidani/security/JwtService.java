package ml.dibidani.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ml.dibidani.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

@Service
public class JwtService {
  private static final Logger log = LoggerFactory.getLogger(JwtService.class);
  public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
  public static final Duration REFRESH_TTL = Duration.ofDays(14);

  private final SecretKey key;
  private final boolean productionSecret;

  public JwtService(@Value("${app.jwt.secret}") String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT_SECRET doit faire au moins 32 octets. Définissez-le via une variable d'environnement.");
    }
    this.productionSecret = !looksLikeDefault(secret);
    if (!productionSecret) log.warn("JWT_SECRET utilise une valeur de développement : à remplacer avant production.");
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String accessToken(AppUser user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getEmail())
        .claim("role", user.getRole().name())
        .claim("uid", user.getId())
        .claim("ver", user.getTokenVersion())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ACCESS_TTL)))
        .signWith(key)
        .compact();
  }

  public Parsed parse(String token) {
    Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    Number ver = c.get("ver", Number.class);
    Number uid = c.get("uid", Number.class);
    return new Parsed(c.getSubject(), c.get("role", String.class), uid == null ? null : uid.longValue(), ver == null ? 0 : ver.intValue());
  }

  public boolean valid(String token) {
    try {
      parse(token);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /** Empreinte SHA-256 d'un refresh token : le jeton en clair n'est jamais persisté. */
  public static String hash(String raw) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 indisponible", e);
    }
  }

  public boolean productionSecret() { return productionSecret; }

  private static boolean looksLikeDefault(String secret) {
    String s = secret.toLowerCase();
    return s.contains("change") || s.contains("dibidani-super-secret") || s.contains("dev-only");
  }

  public record Parsed(String email, String role, Long userId, int version) {}
}
