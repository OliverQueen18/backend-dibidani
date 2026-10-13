package ml.dibidani.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
  @Bean(name = "photoStudioExecutor")
  Executor photoStudioExecutor() {
    var e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(1);
    e.setMaxPoolSize(2);
    e.setQueueCapacity(16);
    e.setThreadNamePrefix("photo-studio-");
    e.initialize();
    return e;
  }
}
