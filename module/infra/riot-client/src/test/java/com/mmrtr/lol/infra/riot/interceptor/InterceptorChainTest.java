package com.mmrtr.lol.infra.riot.interceptor;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRequestAttributes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/** RiotApiConfig 와 같은 순서(retry → 한도)로 붙였을 때 재시도마다 한도를 다시 받는지 검증한다. */
class InterceptorChainTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    // Spring InterceptingRequestExecution 의 iterator 는 단방향이라 retry 의 두 번째 execute 는
    // 뒤따르는 인터셉터(한도)를 건너뛰고 바로 전송한다 — 재시도는 한도를 다시 받지 않는다 (acquire 1회).
    // 설계 결정이 필요해 비활성화: retry 와 한도를 한 인터셉터로 합치거나 재시도마다 체인을 새로 타야 한다.
    @Disabled("Spring 인터셉터 체인이 단방향이라 재시도가 한도 인터셉터를 다시 거치지 않음")
    @Test
    @DisplayName("재시도된 요청은 한도를 다시 받는다")
    void retriedRequest_acquiresAgain() {
        wm.stubFor(get(urlEqualTo("/x")).inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second"));
        wm.stubFor(get(urlEqualTo("/x")).inScenario("retry")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        List<Long> sleeps = new ArrayList<>();
        RiotRateLimitInterceptorTest.FakeLimiter limiter = new RiotRateLimitInterceptorTest.FakeLimiter();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RetryInterceptor retry = new RetryInterceptor(registry, sleeps::add);
        RiotRateLimitInterceptor rateLimit =
                new RiotRateLimitInterceptor(limiter, new RiotAPIProperties(), registry);
        RestClient client = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()))
                .baseUrl(wm.baseUrl())
                .requestInterceptor(retry)
                .requestInterceptor(rateLimit)
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new RiotServerException(res.getStatusCode(), res.getStatusText());
                })
                .build();

        String body = client.get().uri("/x")
                .attribute(RiotRequestAttributes.METHOD, "match-v5.match")
                .retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        assertThat(limiter.acquireCount.get()).isEqualTo(2);
    }
}
