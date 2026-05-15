package com.mmrtr.lol.infra.riot.interceptor;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryInterceptorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private SimpleMeterRegistry meterRegistry;
    private List<Long> sleeps;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        wm.resetAll();
        meterRegistry = new SimpleMeterRegistry();
        sleeps = new ArrayList<>();
        RetryInterceptor.Sleeper sleeper = (long ms) -> sleeps.add(ms);
        RetryInterceptor retryInterceptor = new RetryInterceptor(meterRegistry, sleeper);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(500));

        restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(wm.baseUrl())
                .requestInterceptor(retryInterceptor)
                .build();
    }

    @Test
    @DisplayName("시나리오 1: 200 즉시 → retry 0회")
    void scenario1_200_no_retry() {
        wm.stubFor(get(urlEqualTo("/ok"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String body = restClient.get().uri("/ok").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        wm.verify(1, getRequestedFor(urlEqualTo("/ok")));
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("시나리오 2: 429 Retry-After:1 → 1s sleep → 200")
    void scenario2_429_retry_after_then_200() {
        wm.stubFor(get(urlEqualTo("/x")).inScenario("a")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1"))
                .willSetStateTo("ok"));
        wm.stubFor(get(urlEqualTo("/x")).inScenario("a")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String body = restClient.get().uri("/x").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        wm.verify(2, getRequestedFor(urlEqualTo("/x")));
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isBetween(750L, 1250L);
        assertThat(meterRegistry.counter("riot.api.retry.attempts", "outcome", "success").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("시나리오 3: 429 헤더 없음 → exponential backoff → 200")
    void scenario3_429_no_header_exp_backoff() {
        wm.stubFor(get(urlEqualTo("/x")).inScenario("b")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("ok"));
        wm.stubFor(get(urlEqualTo("/x")).inScenario("b")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String body = restClient.get().uri("/x").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        wm.verify(2, getRequestedFor(urlEqualTo("/x")));
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isBetween(375L, 625L);
    }

    @Test
    @DisplayName("시나리오 4: 5xx → 5xx → 200 (재시도 2회)")
    void scenario4_5xx_5xx_200() {
        wm.stubFor(get(urlEqualTo("/x")).inScenario("c")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second"));
        wm.stubFor(get(urlEqualTo("/x")).inScenario("c")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("third"));
        wm.stubFor(get(urlEqualTo("/x")).inScenario("c")
                .whenScenarioStateIs("third")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String body = restClient.get().uri("/x").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        wm.verify(3, getRequestedFor(urlEqualTo("/x")));
        assertThat(sleeps).hasSize(2);
        assertThat(sleeps.get(0)).isBetween(375L, 625L);
        assertThat(sleeps.get(1)).isBetween(750L, 1250L);
    }

    @Test
    @DisplayName("시나리오 5: 영구 4xx (400) → 즉시 throw, retry 0회")
    void scenario5_400_no_retry() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> restClient.get().uri("/x").retrieve().body(String.class))
                .isInstanceOf(RestClientResponseException.class);
        wm.verify(1, getRequestedFor(urlEqualTo("/x")));
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("시나리오 6: Retry-After:60 → 15s 캡 적용 확인")
    void scenario6_retry_after_capped_to_15s() {
        wm.stubFor(get(urlEqualTo("/x")).inScenario("d")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "60"))
                .willSetStateTo("ok"));
        wm.stubFor(get(urlEqualTo("/x")).inScenario("d")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String body = restClient.get().uri("/x").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isBetween(11_250L, 18_750L);
        assertThat(sleeps.get(0)).isLessThan(60_000L);
    }

    @Test
    @DisplayName("시나리오 7: IOException (connection reset) → retry → 성공")
    void scenario7_io_exception_then_200() {
        wm.stubFor(get(urlEqualTo("/x")).inScenario("e")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("ok"));
        wm.stubFor(get(urlEqualTo("/x")).inScenario("e")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String body = restClient.get().uri("/x").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        wm.verify(2, getRequestedFor(urlEqualTo("/x")));
        assertThat(sleeps).hasSize(1);
        assertThat(meterRegistry.counter("riot.api.retry.attempts", "outcome", "success").count())
                .isEqualTo(1.0);
    }
}
