package com.mmrtr.lol.infra.riot.config;

import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * lab 프로파일에서 Riot 요청의 목적지만 가짜 서버로 바꾸는 팩토리. 호출 기록({@code RiotCallRecorder})은
 * {@code RiotApiService} 가 넘긴 원래 Riot 호스트를 보므로 한도 키·메트릭 라벨이 바뀌지 않는다.
 *
 * <p>{@code https://{routing}.api.riotgames.com/path?q} 를 {@code {base}/{routing}/path?q} 로 옮긴다.
 * 이미 인코딩된 경로와 쿼리는 raw 그대로 붙여 이중 인코딩이 생기지 않는다.
 */
public class RiotMockRedirectRequestFactory implements ClientHttpRequestFactory {

    private static final String RIOT_HOST_SUFFIX = ".api.riotgames.com";

    private final ClientHttpRequestFactory delegate;
    private final String base;

    public RiotMockRedirectRequestFactory(ClientHttpRequestFactory delegate, URI baseUrl) {
        this.delegate = delegate;
        String text = baseUrl.toString();
        this.base = text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
        String host = uri.getHost();
        if (host == null || !host.endsWith(RIOT_HOST_SUFFIX)) {
            return delegate.createRequest(uri, httpMethod);
        }
        String routing = host.substring(0, host.length() - RIOT_HOST_SUFFIX.length());
        String rawQuery = uri.getRawQuery();
        String target = base + "/" + routing + uri.getRawPath() + (rawQuery != null ? "?" + rawQuery : "");
        return delegate.createRequest(URI.create(target), httpMethod);
    }
}
