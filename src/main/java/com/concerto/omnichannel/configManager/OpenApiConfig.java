package com.concerto.omnichannel.configManager;

import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.Components;
import org.springdoc.core.models.GroupedOpenApi;

import javax.swing.*;

/*@Configuration
public class OpenApiConfig {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Omni-Channel Transaction Processing API")
                        .version(appVersion)
                        .description("API for processing transactions across multiple channels including POS, ATM, UPI, BBPS, and Payment Gateways")
                        .contact(new Contact()
                                .name("API Support")
                                .url("https://example.com/support")
                                .email("support@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(Arrays.asList(
                        new Server().url("http://localhost:8080").description("Development server"),
                        new Server().url("https://api.example.com").description("Production server")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token for authentication")));
    }
}*/

@Configuration
@OpenAPIDefinition(
        info = @io.swagger.v3.oas.annotations.info.Info(
                title = "Omni-Channel Transaction Processing API",
                version = "1.0.0",
                description = "Enterprise-grade transaction processing system supporting multiple channels",
                contact = @io.swagger.v3.oas.annotations.info.Contact(
                        name = "API Support",
                        email = "support@yourcompany.com"
                )
        ),
        servers = {
                @io.swagger.v3.oas.annotations.servers.Server(url = "http://localhost:8080", description = "Local server"),
                @Server(url = "https://api.yourcompany.com", description = "Production server")
        }
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Omni-Channel Transaction Processing API")
                        .version("1.0.0")
                        .description("Enterprise-grade transaction processing system supporting POS, ATM, Mobile, Web, and ISO8583 channels")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@yourcompany.com")
                        )
                        .license(new License()
                                .name("Internal Use")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("ClientCredentials"))
                .components(new Components()
                        .addSecuritySchemes("ClientCredentials",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Client-Id")
                                        .description("Client ID for authentication")
                        )
                        .addSecuritySchemes("ClientSecret",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Client-Secret")
                                        .description("Client Secret for authentication")
                        )
                );
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("transactions")
                .pathsToMatch("/api/v1/transactions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("actuator")
                .pathsToMatch("/actuator/**")
                .build();
    }

    @Bean
    public GroupedOpenApi monitorApi(){
        return GroupedOpenApi.builder()
                .group("monitor")
                .pathsToMatch("/api/v1/monitor/**")
                .build();
    }
}
