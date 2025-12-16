package com.mmrtr.lol.riot.config;

import com.mmrtr.lol.riot.exception.RiotClientException;
import com.mmrtr.lol.riot.exception.RiotServerException;
import com.mmrtr.lol.riot.interceptor.RetryInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LogLevel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RiotAPIProperties.class)
public class RiotApiConfig {

    private final RiotAPIProperties riotAPIProperties;

    @Bean
    public RetryTemplate retryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(2)
                .fixedBackoff(2000)
                .retryOn(RiotClientException.class)
                .build();
    }

    @Bean
    public RestClient riotRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(riotAPIProperties.getTimeout()))
                .build();

        RetryInterceptor retryInterceptor = new  RetryInterceptor(retryTemplate());

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .defaultHeader("X-Riot-Token", riotAPIProperties.getApiKey())
                .defaultHeader("User-Agent", "MMRTR")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .defaultHeader("Accept-Charset", "application/x-www-form-urlencoded; charset=UTF-8")
                .requestInterceptor(retryInterceptor)
                .requestInterceptor(logRequest())
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new RiotClientException(
                            response.getStatusCode(), response.getStatusText(), LogLevel.WARN);
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new RiotServerException(response.getStatusCode(), response.getStatusText());
                })
                .build();
    }

    private ClientHttpRequestInterceptor logRequest() {
        return (request, body, execution) -> {
            log.debug("Request: {} {}", request.getMethod(), request.getURI());
            log.debug("Headers: {}", request.getHeaders());
            return execution.execute(request, body);
        };
    }
}
