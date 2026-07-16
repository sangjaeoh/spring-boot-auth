package com.example.auth.app.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 계약 메타데이터를 조립한다. 계약 버전은 패키지 버저닝(presentation/v{n})을 따른다 —
 * JWKS 같은 well-known 엔드포인트만 무버전이다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("auth-api").version("v1"));
    }
}
