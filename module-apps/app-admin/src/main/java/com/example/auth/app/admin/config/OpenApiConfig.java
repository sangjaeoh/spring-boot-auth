package com.example.auth.app.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 계약 메타데이터를 조립한다(app-api와 동일 — 계약 버전은 presentation/v{n} 패키지 버저닝).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("auth-admin").version("v1"));
    }
}
