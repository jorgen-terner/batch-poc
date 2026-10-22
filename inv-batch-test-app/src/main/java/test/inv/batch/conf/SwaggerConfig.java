package test.inv.batch.conf;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig
{
   @Bean
   public OpenAPI openApi()
   {
      return new OpenAPI().info(new Info()
            .title("INV API batchhantering")
            .description("REST-API för att hantera batchjobb för INV"));
   }
}
