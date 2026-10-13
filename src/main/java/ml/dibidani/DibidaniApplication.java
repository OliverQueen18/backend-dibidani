package ml.dibidani;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ml.dibidani.config.PhotoStudioProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(PhotoStudioProperties.class)
@EnableScheduling
public class DibidaniApplication {
  public static void main(String[] args) { SpringApplication.run(DibidaniApplication.class, args); }
}
