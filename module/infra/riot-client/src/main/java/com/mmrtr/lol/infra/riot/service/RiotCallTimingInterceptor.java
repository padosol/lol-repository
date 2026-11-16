package com.mmrtr.lol.infra.riot.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Riot RestClient 인터셉터. 이 스레드에 {@link RiotCallTiming} 이 있으면 응답 헤더를 받은 시각을 적고, 본문을 읽는 대로
 * 바이트를 세며, 본문 끝(EOF 또는 닫기)의 시각을 적는다. 기록이 없으면 아무것도 하지 않는다.
 */
public class RiotCallTimingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        RiotCallTiming timing = RiotCallTiming.current();
        ClientHttpResponse response = execution.execute(request, body);
        if (timing == null) {
            return response;
        }
        timing.headersReceived(System.nanoTime());
        return new TimedResponse(response, timing);
    }

    private static final class TimedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final RiotCallTiming timing;
        private InputStream body;

        TimedResponse(ClientHttpResponse delegate, RiotCallTiming timing) {
            this.delegate = delegate;
            this.timing = timing;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            if (body == null) {
                body = new CountingStream(delegate.getBody(), timing);
            }
            return body;
        }

        @Override
        public void close() {
            timing.bodyEnded(System.nanoTime());
            delegate.close();
        }
    }

    private static final class CountingStream extends FilterInputStream {

        private final RiotCallTiming timing;

        CountingStream(InputStream in, RiotCallTiming timing) {
            super(in);
            this.timing = timing;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value < 0) {
                timing.bodyEnded(System.nanoTime());
            } else {
                timing.bodyRead(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count < 0) {
                timing.bodyEnded(System.nanoTime());
            } else {
                timing.bodyRead(count);
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            timing.bodyEnded(System.nanoTime());
            super.close();
        }
    }
}
