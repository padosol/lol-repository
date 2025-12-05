package com.mmrtr.lolrepository.riot.core.calling;

import lombok.extern.slf4j.Slf4j;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class DefaultRiotExecute implements RiotExecute{

    private WebClient webClient;


    public DefaultRiotExecute(String apiKey) {

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("User-Agent", "MMRTR");
        headers.add("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        headers.add("Accept-Charset", "application/x-www-form-urlencoded; charset=UTF-8");
        headers.add("X-Riot-Token", apiKey);

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize(2 * 1024 * 2024))   // 2MB
                .build();

        this.webClient = WebClient.builder()
                .defaultHeaders(
                        httpHeaders -> httpHeaders.addAll(headers)
                )
                .exchangeStrategies(exchangeStrategies)
                .build();
    }

    @Override
    public <T> CompletableFuture<T> execute(Class<T> clazz, URI uri) {

        return webClient.get()
                .uri(uri)
                .exchangeToMono(clientResponse -> {

                    log.debug("[URI 호출]: {}", uri.toString());
                    Map<String, String> headerMap = clientResponse.headers().asHttpHeaders().toSingleValueMap();

                    for(String key : headerMap.keySet()) {
                        String header = headerMap.get(key);

                        log.debug("{}: {}", key, header);
                    }

                    int statusCode = clientResponse.statusCode().value();

                    log.debug("Status Code: [{}]", statusCode);

                    return clientResponse.bodyToMono(clazz);
                })
                .timeout(Duration.ofSeconds(3))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .toFuture();
    }

    public WebClient getWebClient() {
        return this.webClient;
    }

}
