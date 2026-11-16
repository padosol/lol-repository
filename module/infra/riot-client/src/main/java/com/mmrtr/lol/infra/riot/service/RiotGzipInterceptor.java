package com.mmrtr.lol.infra.riot.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Riot 응답을 gzip 으로 받는다. 요청에 {@code Accept-Encoding: gzip} 을 붙이고, {@code Content-Encoding: gzip} 으로 온
 * 본문은 풀어서 넘긴다 (JDK HttpClient 와 Spring 6.2 의 JdkClientHttpRequestFactory 는 스스로 풀지 않는다).
 * 실측(2026-11-14)으로 timeline 690KB → 61KB, match 79KB → 12KB 라 asia 받기 대역폭을 크게 던다.
 * <p>
 * {@link RiotCallTimingInterceptor} 보다 바깥(먼저 등록)에 둬야 받기 시간과 받은 바이트가 네트워크로 받은 압축 그대로 잡힌다.
 * 압축을 다 풀면 원본 스트림의 남은 바이트를 끝까지 읽어, 받기 끝 시각이 본문 변환 뒤가 아니라 마지막 바이트를 받은 때가 되게 한다.
 */
public class RiotGzipInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");
        ClientHttpResponse response = execution.execute(request, body);
        return isGzip(response.getHeaders()) ? new GunzippedResponse(response) : response;
    }

    private static boolean isGzip(HttpHeaders headers) {
        List<String> encodings = headers.get(HttpHeaders.CONTENT_ENCODING);
        return encodings != null
                && encodings.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("gzip"));
    }

    /** 본문을 풀어 주는 응답. 풀었으므로 Content-Encoding·Content-Length 를 뺀 헤더를 보인다. */
    private static final class GunzippedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final HttpHeaders headers;
        private InputStream body;

        GunzippedResponse(ClientHttpResponse delegate) {
            this.delegate = delegate;
            HttpHeaders copy = new HttpHeaders();
            copy.addAll(delegate.getHeaders());
            copy.remove(HttpHeaders.CONTENT_ENCODING);
            copy.remove(HttpHeaders.CONTENT_LENGTH);
            this.headers = copy;
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
            return headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            if (body == null) {
                body = gunzip(delegate.getBody());
            }
            return body;
        }

        @Override
        public void close() {
            delegate.close();
        }

        /** 본문이 비었으면(gzip 이라고만 온 빈 본문) 빈 스트림이다. */
        private static InputStream gunzip(InputStream raw) throws IOException {
            PushbackInputStream source = new PushbackInputStream(raw, 1);
            int first = source.read();
            if (first < 0) {
                return InputStream.nullInputStream();
            }
            source.unread(first);
            return new DrainingStream(new GZIPInputStream(source), source);
        }
    }

    /** 압축을 다 풀었으면(-1) 원본의 남은 바이트를 끝까지 읽는다. */
    private static final class DrainingStream extends FilterInputStream {

        private final InputStream source;
        private boolean drained;

        DrainingStream(InputStream gunzipped, InputStream source) {
            super(gunzipped);
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value < 0) {
                drain();
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count < 0) {
                drain();
            }
            return count;
        }

        private void drain() throws IOException {
            if (drained) {
                return;
            }
            drained = true;
            // 남은 바이트(보통 없음)를 버리고 원본 끝(EOF)까지 읽는다
            byte[] skip = new byte[1024];
            int count = 0;
            while (count >= 0) {
                count = source.read(skip);
            }
        }
    }
}
