package com.fancode.aggregator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FanCode Live Sports Match Data Aggregator API")
                        .version("1.0.0")
                        .description("Real-time cricket score, commentary, and webhook aggregation service " +
                                "ingesting live data from Provider A (Push Webhook) and Provider B (Polling HTTP) " +
                                "with strict monotonicity, commentary revision rules, and deterministic precedence.")
                        .contact(new Contact()
                                .name("FanCode Backend Engineering Team")
                                .url("https://fancode.com"))
                        .license(new License().name("Proprietary").url("https://fancode.com")));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
