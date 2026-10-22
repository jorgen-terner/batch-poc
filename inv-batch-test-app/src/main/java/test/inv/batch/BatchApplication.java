package test.inv.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

//@EnableBatchProcessing https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide
@SpringBootApplication
@EnableAsync
public class BatchApplication
{
   public static void main(String[] args)
   {
      SpringApplication.run(BatchApplication.class, args);
   }
}
