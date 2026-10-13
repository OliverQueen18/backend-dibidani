package ml.dibidani.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

  @Bean
  SecurityFilterChain chain(HttpSecurity http, JwtAuthFilter filter) throws Exception {
    return http
        .csrf(c -> c.disable())
        .cors(c -> {})
        .headers(h -> h
            .contentTypeOptions(o -> {})
            .frameOptions(f -> f.deny())
            .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            .permissionsPolicyHeader(p -> p.policy("camera=(self), microphone=(self), geolocation=(self)")))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e
            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .accessDeniedHandler((req, res, ex) -> {
              res.setStatus(HttpStatus.FORBIDDEN.value());
              res.setContentType("application/json;charset=UTF-8");
              res.getWriter().write("{\"status\":403,\"message\":\"Accès refusé\",\"errors\":null}");
            }))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/error", "/actuator/health", "/api/v1/public/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/oauth", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                "/api/v1/auth/forgot-password", "/api/v1/auth/verify-reset-otp", "/api/v1/auth/reset-password").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth/providers").permitAll()
            .requestMatchers(HttpMethod.GET, "/uploads/**", "/api/v1/categories/**", "/api/v1/products/**", "/api/v1/vendors/**",
                "/api/v1/brands/**", "/api/v1/advertisements/**", "/api/v1/search/**", "/api/v1/vehicles/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/cart/quote", "/api/v1/payments/webhooks/**").permitAll()
            .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "MANAGER", "CATALOG_MANAGER", "ORDER_MANAGER", "STOCK_MANAGER")
            .anyRequest().authenticated())
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.cors-origins}") String origins) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
    c.setAllowCredentials(true);
    c.setMaxAge(Duration.ofHours(1));
    var s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }
}
