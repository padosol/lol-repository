package com.mmrtr.lol.infra.riot.config;

import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.infra.riot.interceptor.ConcurrencyInterceptor;
import com.mmrtr.lol.infra.riot.interceptor.RateLimitInterceptor;
import com.mmrtr.lol.infra.riot.interceptor.RetryInterceptor;
import com.mmrtr.lol.infra.riot.ratelimit.HostRateLimitResolver;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LogLevel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RiotAPIProperties.class)
public class RiotApiConfig {

    private static final int CONCURRENCY_LIMIT = 20;

    private final RiotAPIProperties riotAPIProperties;
    private final RedissonClient redissonClient;
    private final HostRateLimitResolver hostRateLimitResolver;
    private final MeterRegistry meterRegistry;

    @Bean
    public RestClient riotRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(riotAPIProperties.getTimeout()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(riotAPIProperties.getReadTimeout()));

        RetryInterceptor retryInterceptor = new RetryInterceptor(meterRegistry);
        RateLimitInterceptor rateLimitInterceptor = new RateLimitInterceptor(
                redissonClient, hostRateLimitResolver);
        ConcurrencyInterceptor concurrencyInterceptor = new ConcurrencyInterceptor(CONCURRENCY_LIMIT);

        // Interceptor 실행 순서 (등록 순서 = 호출 순서, 첫번째가 가장 바깥):
        // 1. retryInterceptor (가장 바깥 - 429/5xx/IOException 재시도)
        // 2. rateLimitInterceptor (로컬 Redisson limiter)
        // 3. logRequest (로깅)
        // 4. concurrencyInterceptor (동시 요청 수 제한 - 가장 안쪽)
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("X-Riot-Token", riotAPIProperties.getApiKey())
                .defaultHeader("User-Agent", "MMRTR")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .defaultHeader("Accept-Charset", "application/x-www-form-urlencoded; charset=UTF-8")
                .requestInterceptor(retryInterceptor)
                .requestInterceptor(rateLimitInterceptor)
                .requestInterceptor(logRequest())
                .requestInterceptor(concurrencyInterceptor)
                .defaultStatusHandler(status -> status.value() == 404, this::handleNotFound)
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, this::handleClientError)
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, this::handleServerError)
                .build();
    }

    private void handleNotFound(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw new RiotClientNotFoundException(
                response.getStatusCode(), response.getStatusText(), LogLevel.WARN);
    }

    private void handleClientError(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw new RiotClientException(
                response.getStatusCode(), response.getStatusText(), LogLevel.WARN);
    }

    private void handleServerError(HttpRequest request, ClientHttpResponse response) throws IOException {
        log.warn("5xx error headers: {}", response.getHeaders());
        throw new RiotServerException(response.getStatusCode(), response.getStatusText());
    }

    private ClientHttpRequestInterceptor logRequest() {
        return (request, body, execution) -> {
            log.debug("Request: {} {}", request.getMethod(), request.getURI());
            log.debug("Headers: {}", request.getHeaders());
            return execution.execute(request, body);
        };
    }
}
