package ml.dibidani.config;

import ml.dibidani.service.StorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final StorageService storage;

  public WebConfig(StorageService storage) { this.storage = storage; }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**")
        .addResourceLocations(storage.root().toUri().toString())
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
  }
}
