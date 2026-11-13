package com.mmrtr.lol.infra.riot.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.config.RiotApiConfig;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Riot 응답을 gzip 으로 받는다. timeline(약 700KB)이 약 60KB 로 줄어 asia 받기 대역폭을 덜 쓴다. 실제 {@link RiotApiConfig} 의
 * RestClient 로 WireMock 을 불러, gzip 을 요청하고 받은 본문을 풀어 읽으며 받은 바이트는 압축된 크기로 세는지 본다.
 */
class RiotGzipTest {

    private static final String HOST = "asia.api.riotgames.com";
    private static final String MATCH_PATH = "/asia/lol/match/v5/matches/KR_1";
    private static final String MATCH_JSON =
            "{\"metadata\":{\"matchId\":\"KR_1\"},\"pad\":\"" + "x".repeat(20_000) + "\"}";

    /** 응답 압축은 테스트가 정한다 (WireMock 이 알아서 압축하지 않게 끈다). */
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().gzipDisabled(true))
            .build();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private ExecutorService executor;
    private RiotApiService service;

    @BeforeEach
    void setUp() {
        wm.resetAll();
        RiotAPIProperties properties = new RiotAPIProperties();
        properties.setApiKey("test-key");
        properties.getMock().setBaseUrl(wm.baseUrl());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "lab");
        RestClient client = new RiotApiConfig(properties, environment).riotRestClient();
        RiotCallRecorder recorder =
                new RiotCallRecorder(new RiotCallRecorderTest.FakeLimiter(), properties, registry);
        service = new RiotApiService(client, recorder);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Riot 에 gzip 으로 달라고 요청한다")
    void requestsGzip() {
        // given
        wm.stubFor(get(urlEqualTo(MATCH_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(MATCH_JSON)));

        // when
        service.getMatchById("KR_1", Platform.KR, permit(), executor).join();

        // then
        wm.verify(getRequestedFor(urlEqualTo(MATCH_PATH)).withHeader("Accept-Encoding", containing("gzip")));
    }

    @Test
    @DisplayName("gzip 으로 온 본문을 풀어 읽고, 받은 바이트는 압축된 크기로 센다")
    void gzipResponse_isDecompressed_andCountedCompressed() throws IOException {
        // given
        byte[] compressed = gzip(MATCH_JSON);
        wm.stubFor(get(urlEqualTo(MATCH_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("Content-Encoding", "gzip")
                .withBody(compressed)));

        // when
        MatchDto match = service.getMatchById("KR_1", Platform.KR, permit(), executor).join();

        // then
        DistributionSummary size = registry.find("riot.api.response.size")
                .tags("host", HOST, "method", "match-v5.match").summary();
        assertThat(match.getMetadata().getMatchId()).isEqualTo("KR_1");
        assertThat(size.count()).isEqualTo(1);
        assertThat(size.totalAmount()).isEqualTo(compressed.length);
        assertThat(compressed.length).isLessThan(MATCH_JSON.length() / 10);
        assertThat(registry.find("riot.api.call.download").tags("host", HOST, "method", "match-v5.match")
                .timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("압축하지 않고 온 응답은 그대로 읽는다")
    void plainResponse_isReadAsIs() {
        // given
        wm.stubFor(get(urlEqualTo(MATCH_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(MATCH_JSON)));

        // when
        MatchDto match = service.getMatchById("KR_1", Platform.KR, permit(), executor).join();

        // then
        DistributionSummary size = registry.find("riot.api.response.size")
                .tags("host", HOST, "method", "match-v5.match").summary();
        assertThat(match.getMetadata().getMatchId()).isEqualTo("KR_1");
        assertThat(size.totalAmount()).isEqualTo(MATCH_JSON.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("gzip 이라고 왔는데 본문이 비어 있으면 빈 본문으로 본다 (압축 풀기에서 터지지 않는다)")
    void emptyGzipBody_isEmpty() throws IOException {
        // given
        MockClientHttpResponse empty = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        empty.getHeaders().add("Content-Encoding", "gzip");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("https://" + HOST));

        // when
        ClientHttpResponse response = new RiotGzipInterceptor().intercept(request, new byte[0], (r, b) -> empty);

        // then
        assertThat(response.getBody().read()).isEqualTo(-1);
        assertThat(response.getHeaders().containsKey("Content-Encoding")).isFalse();
    }

    private static RiotPermit permit() {
        return new RiotPermit("t-match", "KR", RiotMethod.MATCH, RiotCallPriority.USER);
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
