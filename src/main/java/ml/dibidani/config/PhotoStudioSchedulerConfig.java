package ml.dibidani.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PhotoStudioSchedulerConfig {
  @Bean("taskScheduler")
  public ThreadPoolTaskScheduler applicationScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("application-scheduler-");
    return scheduler;
  }
  @Bean("photoStudioScheduler")
  public ThreadPoolTaskScheduler photoStudioScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("photo-studio-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(130);
    return scheduler;
  }
}
