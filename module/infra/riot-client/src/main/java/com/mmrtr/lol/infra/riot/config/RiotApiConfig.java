package com.mmrtr.lol.infra.riot.config;

import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.infra.riot.service.RiotCallTimingInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LogLevel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RiotAPIProperties.class)
public class RiotApiConfig {

    private final RiotAPIProperties riotAPIProperties;
    private final Environment environment;

    /**
     * 한도·재시도 로직이 없는 Riot RestClient. 허가는 호출하는 쪽이 받고, 응답 기록(허가 기록 옮기기·한도 학습·정지)은
     * {@code RiotCallRecorder} 가 한다. 상태 처리기는 404 → 429 → 그 밖 4xx → 5xx 순서로 본다.
     */
    @Bean
    public RestClient riotRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(riotAPIProperties.getTimeout()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(riotAPIProperties.getReadTimeout()));

        ClientHttpRequestFactory effectiveFactory = requestFactory;
        String mockBaseUrl = riotAPIProperties.getMock().getBaseUrl();
        RiotMockGuard.assertAllowed(mockBaseUrl, environment.getActiveProfiles());
        if (mockBaseUrl != null && !mockBaseUrl.isBlank()) {
            effectiveFactory = new RiotMockRedirectRequestFactory(requestFactory, URI.create(mockBaseUrl));
            log.warn("Riot 요청을 가짜 서버({})로 보냅니다", mockBaseUrl);
        }

        return RestClient.builder()
                .requestFactory(effectiveFactory)
                .defaultHeader("X-Riot-Token", riotAPIProperties.getApiKey())
                .defaultHeader("User-Agent", "MMRTR")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .defaultHeader("Accept-Charset", "application/x-www-form-urlencoded; charset=UTF-8")
                .requestInterceptor(logRequest())
                .requestInterceptor(new RiotCallTimingInterceptor())
                .defaultStatusHandler(status -> status.value() == 404, this::handleNotFound)
                .defaultStatusHandler(status -> status.value() == 429, this::handleRateLimited)
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, this::handleClientError)
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, this::handleServerError)
                .build();
    }

    private void handleNotFound(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw new RiotClientNotFoundException(
                response.getStatusCode(), response.getStatusText(), LogLevel.WARN, copyOf(response.getHeaders()));
    }

    /** Retry-After(양의 정수 초, 아니면 null)와 X-Rate-Limit-Type 을 담아 던진다. 다시 보내지 않는다. */
    private void handleRateLimited(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpHeaders headers = copyOf(response.getHeaders());
        throw new RiotRateLimitException(retryAfter(headers.getFirst("Retry-After")),
                headers.getFirst("X-Rate-Limit-Type"), response.getStatusCode(), response.getStatusText(),
                LogLevel.WARN, headers);
    }

    private void handleClientError(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw new RiotClientException(
                response.getStatusCode(), response.getStatusText(), LogLevel.WARN, copyOf(response.getHeaders()));
    }

    private void handleServerError(HttpRequest request, ClientHttpResponse response) throws IOException {
        log.warn("5xx error headers: {}", response.getHeaders());
        throw new RiotServerException(response.getStatusCode(), response.getStatusText(),
                copyOf(response.getHeaders()));
    }

    /** 양의 정수 초면 그 기간, 아니면 null. */
    static Duration retryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds > 0L ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException e) {
            log.debug("Failed to parse Retry-After header: {}", header);
            return null;
        }
    }

    /** 예외에 실어 응답을 닫은 뒤에도 읽을 수 있게 복사한다. */
    private static HttpHeaders copyOf(HttpHeaders headers) {
        HttpHeaders copy = new HttpHeaders();
        copy.putAll(headers);
        return copy;
    }

    private ClientHttpRequestInterceptor logRequest() {
        return (request, body, execution) -> {
            log.debug("Request: {} {}", request.getMethod(), request.getURI());
            log.debug("Headers: {}", request.getHeaders());
            return execution.execute(request, body);
        };
    }
}
