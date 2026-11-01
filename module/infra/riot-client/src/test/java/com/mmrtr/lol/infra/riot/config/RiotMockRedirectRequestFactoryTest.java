package com.mmrtr.lol.infra.riot.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class RiotMockRedirectRequestFactoryTest {

    private WireMockServer wireMock;
    private List<String> seenHosts;
    private RestClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse().withStatus(200).withBody("{}")
                        .withHeader("Content-Type", "application/json")));
        seenHosts = new ArrayList<>();
        ClientHttpRequestFactory factory = new RiotMockRedirectRequestFactory(
                new JdkClientHttpRequestFactory(HttpClient.newHttpClient()),
                URI.create("http://localhost:" + wireMock.port()));
        client = RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    seenHosts.add(request.getURI().getHost());
                    return execution.execute(request, body);
                })
                .build();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("createRequest_인코딩된경로와쿼리를_그대로_옮긴다")
    void createRequest_인코딩된경로와쿼리를_그대로_옮긴다() {
        // given
        String url = "https://asia.api.riotgames.com/riot/account/v1/accounts/by-riot-id/{g}/{t}";

        // when
        client.get().uri(url, "홍 길동", "KR1").retrieve().toBodilessEntity();

        // then
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(
                "/asia/riot/account/v1/accounts/by-riot-id/%ED%99%8D%20%EA%B8%B8%EB%8F%99/KR1")));
        assertThat(seenHosts).containsExactly("asia.api.riotgames.com");
    }

    @Test
    @DisplayName("createRequest_쿼리를_보존한다")
    void createRequest_쿼리를_보존한다() {
        // given
        String url = "https://asia.api.riotgames.com/lol/match/v5/matches/by-puuid/abc/ids?startTime=1&start=0&count=20";

        // when
        client.get().uri(URI.create(url)).retrieve().toBodilessEntity();

        // then
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/asia/lol/match/v5/matches/by-puuid/abc/ids"))
                .withQueryParam("startTime", equalTo("1"))
                .withQueryParam("start", equalTo("0"))
                .withQueryParam("count", equalTo("20")));
    }

    @Test
    @DisplayName("createRequest_Riot이_아닌_호스트는_그대로")
    void createRequest_Riot이_아닌_호스트는_그대로() throws Exception {
        // given
        List<URI> received = new ArrayList<>();
        JdkClientHttpRequestFactory real = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        ClientHttpRequestFactory delegate = (uri, method) -> {
            received.add(uri);
            return real.createRequest(uri, method);
        };
        RiotMockRedirectRequestFactory factory = new RiotMockRedirectRequestFactory(
                delegate, URI.create("http://localhost:" + wireMock.port()));
        URI original = URI.create("http://localhost:" + wireMock.port() + "/other/path?x=1");

        // when
        ClientHttpRequest request = factory.createRequest(original, HttpMethod.GET);

        // then
        assertThat(received).containsExactly(original);
        assertThat(request.getURI()).isEqualTo(original);
    }
}
