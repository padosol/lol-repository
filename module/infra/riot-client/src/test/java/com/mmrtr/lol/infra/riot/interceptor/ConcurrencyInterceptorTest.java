package com.mmrtr.lol.infra.riot.interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrencyInterceptorTest {

    private static final HttpRequest REQUEST =
            new TestHttpRequest(URI.create("http://example.com/x"), HttpMethod.GET);

    @Test
    @DisplayName("정상 흐름: execution 결과 그대로 반환")
    void normal_flow_returns_execution_response() throws IOException {
        ConcurrencyInterceptor interceptor = new ConcurrencyInterceptor(1);
        ClientHttpResponse stub = new TestClientHttpResponse(HttpStatus.OK);
        ClientHttpRequestExecution execution = (req, body) -> stub;

        ClientHttpResponse response = interceptor.intercept(REQUEST, new byte[0], execution);

        assertThat(response).isSameAs(stub);
    }

    @Test
    @DisplayName("execution 예외 발생해도 permit 해제 — 후속 호출이 막히지 않음")
    void permit_released_on_exception() throws IOException {
        ConcurrencyInterceptor interceptor = new ConcurrencyInterceptor(1);
        ClientHttpRequestExecution failing = (req, body) -> {
            throw new IOException("boom");
        };
        ClientHttpResponse okStub = new TestClientHttpResponse(HttpStatus.OK);

        assertThatThrownBy(() -> interceptor.intercept(REQUEST, new byte[0], failing))
                .isInstanceOf(IOException.class);

        ClientHttpResponse response = interceptor.intercept(REQUEST, new byte[0], (req, body) -> okStub);
        assertThat(response).isSameAs(okStub);
    }

    @Test
    @DisplayName("permit 만큼만 동시 실행: 2 permit, 3 요청 → 3번째는 대기")
    void permits_bound_concurrent_requests() throws Exception {
        ConcurrencyInterceptor interceptor = new ConcurrencyInterceptor(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger peak = new AtomicInteger(0);
        CountDownLatch entered = new CountDownLatch(2);

        ClientHttpRequestExecution holding = (req, body) -> {
            int current = inFlight.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } finally {
                inFlight.decrementAndGet();
            }
            return new TestClientHttpResponse(HttpStatus.OK);
        };

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> interceptor.intercept(REQUEST, new byte[0], holding));
            }

            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(50);
            assertThat(inFlight.get()).isEqualTo(2);

            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(peak.get()).isEqualTo(2);
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
        public Map<String, Object> getAttributes() {
            return Map.of();
        }
    }

    private static final class TestClientHttpResponse implements ClientHttpResponse {
        private final HttpStatus status;
        private final HttpHeaders headers = new HttpHeaders();

        TestClientHttpResponse(HttpStatus status) {
            this.status = status;
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
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
