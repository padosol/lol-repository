package com.mmrtr.lol.infra.riot.interceptor;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    private RetryInterceptor retryInterceptor;
    private JdkClientHttpRequestFactory factory;
    private RestClient restClient;
    private String host;

    @BeforeEach
    void setUp() {
        wm.resetAll();
        meterRegistry = new SimpleMeterRegistry();
        sleeps = new ArrayList<>();
        RetryInterceptor.Sleeper sleeper = (long ms) -> sleeps.add(ms);
        retryInterceptor = new RetryInterceptor(meterRegistry, sleeper);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(500));

        restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(wm.baseUrl())
                .requestInterceptor(retryInterceptor)
                .build();

        host = URI.create(wm.baseUrl()).getHost();
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
    @DisplayName("시나리오 4: 5xx → 5xx → 200 (재시도 2회) + responses counter 누적 확인")
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
        // riot.api.responses counter 는 매 시도마다 누적
        assertThat(meterRegistry.counter("riot.api.responses", "status", "500", "host", host).count())
                .isEqualTo(2.0);
        assertThat(meterRegistry.counter("riot.api.responses", "status", "200", "host", host).count())
                .isEqualTo(1.0);
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

    @Test
    @DisplayName("시나리오 8: 로컬 limiter (RiotRateLimitException) → retry → 성공 (unit-level)")
    void scenario8_local_limiter_then_200() throws IOException {
        // Spring InterceptingRequestExecution 은 single-pass iterator 라 RestClient chain 안의
        // fake interceptor 가 retry 두번째 시도에 다시 호출되지 않는다 (production 의 RateLimitInterceptor
        // 도 동일한 한계 — 기존 spring-retry 시절부터 존재). 따라서 시나리오 8 은 RetryInterceptor 의
        // RiotRateLimitException catch + retry 동작을 unit-level (ClientHttpRequestExecution lambda) 로 검증.
        HttpRequest request = new TestHttpRequest(URI.create("http://example.com/x"), HttpMethod.GET);
        ClientHttpResponse okResponse = new TestClientHttpResponse(HttpStatus.OK, "ok");

        AtomicInteger calls = new AtomicInteger(0);
        ClientHttpRequestExecution execution = (req, body) -> {
            if (calls.incrementAndGet() == 1) {
                throw new RiotRateLimitException(
                        Duration.ZERO, HttpStatus.TOO_MANY_REQUESTS, "Local limiter exhausted", LogLevel.WARN);
            }
            return okResponse;
        };

        ClientHttpResponse response = retryInterceptor.intercept(request, new byte[0], execution);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(sleeps).hasSize(1);
        // RiotRateLimitException(Duration.ZERO) → exp backoff (INITIAL=500ms ± 25%)
        assertThat(sleeps.get(0)).isBetween(375L, 625L);
        assertThat(meterRegistry.counter("riot.api.retry.attempts", "outcome", "success").count())
                .isEqualTo(1.0);
    }

    private static final class TestHttpRequest implements HttpRequest {
        private final URI uri;
        private final HttpMethod method;
        private final HttpHeaders headers = new HttpHeaders();

        TestHttpRequest(URI uri, HttpMethod method) {
            this.uri = uri;
            this.method = method;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public java.util.Map<String, Object> getAttributes() {
            return java.util.Map.of();
        }
    }

    private static final class TestClientHttpResponse implements ClientHttpResponse {
        private final HttpStatus status;
        private final HttpHeaders headers = new HttpHeaders();
        private final byte[] body;

        TestClientHttpResponse(HttpStatus status, String body) {
            this.status = status;
            this.body = body.getBytes();
        }

        @Override
        public HttpStatus getStatusCode() {
            return status;
        }

        @Override
        public String getStatusText() {
            return status.getReasonPhrase();
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
