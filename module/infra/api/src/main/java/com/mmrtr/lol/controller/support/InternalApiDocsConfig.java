package com.mmrtr.lol.controller.support;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

/**
 * 내부 API(/internal/v1/**) OpenAPI 계약. 생산자(lol-repository)가 소유한다 (서비스 분리 설계 5절).
 * 내부 포트의 {@code /internal/v1/api-docs} 에서만 응답하고, 저장소의 {@code docs/api/internal-v1.openapi.yaml} 과 같다
 * ({@code InternalApiContractTest} 가 비교). springdoc 설정은 {@code internal-api-docs.properties}.
 */
@Configuration
@PropertySource("classpath:internal-api-docs.properties")
public class InternalApiDocsConfig {

    @Bean
    public OpenAPI internalApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("lol-repository internal API")
                        .version("v1")
                        .description("lol-server 전용. 내부 포트(lol.web.internal-port)에서만 받고 NetworkPolicy 로 lol-server 파드만 허용한다."))
                .servers(List.of(new Server().url("http://lol-repository:8081").description("클러스터 내부 Service (internal 포트)")));
    }
}
