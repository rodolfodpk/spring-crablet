package com.outbox.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for Swagger UI documentation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI outboxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Outbox Service API")
                        .description("A microservice for reliable event publishing using the outbox pattern. " +
                                "Manages event publishing with transactional guarantees, retry mechanisms, and dead letter handling. " +
                                "Provides endpoints for outbox management, publisher configuration, and publishing control.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Outbox Service Team")
                                .email("support@outboxservice.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}

