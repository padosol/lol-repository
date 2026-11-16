package com.mmrtr.lol.infra.riot.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.config.RiotApiConfig;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

/**
 * 실제 {@link RiotApiConfig} 의 RestClient 로 WireMock 을 불러, 호출 시간을 응답 대기(보냄 → 헤더)와 받기(헤더 → 본문 끝)로
 * 나누고 받은 바이트를 호스트·method 별로 남기는지 본다.
 */
class RiotCallTimingTest {

    private static final String HOST = "asia.api.riotgames.com";
    private static final String MATCH_PATH = "/asia/lol/match/v5/matches/KR_1";

    /** 클라이언트가 gzip 을 요청하므로 WireMock 이 알아서 압축하지 않게 끈다 (압축 응답은 {@link RiotGzipTest} 가 본다). */
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
    @DisplayName("응답이 늦으면 대기 시간에, 본문이 천천히 오면 받기 시간에 잡히고, 받은 바이트를 남긴다")
    void splitsWaitAndDownload_andCountsBytes() {
        // given
        String body = "{\"metadata\":{\"matchId\":\"KR_1\"},\"pad\":\"" + "x".repeat(4000) + "\"}";
        wm.stubFor(get(urlEqualTo(MATCH_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)
                .withFixedDelay(300)
                .withChunkedDribbleDelay(4, 400)));

        // when
        service.getMatchById("KR_1", Platform.KR, permit(RiotMethod.MATCH), executor).join();

        // then
        Timer wait = timer("riot.api.call.wait");
        Timer download = timer("riot.api.call.download");
        Timer total = registry.find("riot.api.call.duration")
                .tags("host", HOST, "method", "match-v5.match", "result", "ok").timer();
        DistributionSummary size = registry.find("riot.api.response.size")
                .tags("host", HOST, "method", "match-v5.match").summary();
        assertThat(wait.count()).isEqualTo(1);
        assertThat(wait.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(280);
        assertThat(download.count()).isEqualTo(1);
        assertThat(download.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(250);
        assertThat(total.count()).isEqualTo(1);
        assertThat(total.totalTime(TimeUnit.MILLISECONDS))
                .isGreaterThanOrEqualTo(wait.totalTime(TimeUnit.MILLISECONDS)
                        + download.totalTime(TimeUnit.MILLISECONDS) - 5);
        assertThat(size.count()).isEqualTo(1);
        assertThat(size.totalAmount()).isEqualTo(body.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("5xx 도 헤더는 받았으므로 대기 시간을 남기고, 호출은 error 로 센다")
    void serverError_recordsWait_andErrorDuration() {
        // given
        wm.stubFor(get(urlEqualTo(MATCH_PATH)).willReturn(aResponse().withStatus(503)));

        // when
        assertThatThrownBy(() -> service.getMatchById("KR_1", Platform.KR, permit(RiotMethod.MATCH), executor).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RiotServerException.class);

        // then
        assertThat(timer("riot.api.call.wait").count()).isEqualTo(1);
        assertThat(registry.find("riot.api.call.duration")
                .tags("host", HOST, "method", "match-v5.match", "result", "error").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("연결이 끊겨 응답을 받지 못하면 대기·받기·크기는 남기지 않고 호출만 error 로 센다")
    void connectionReset_onlyErrorDuration() {
        // given
        wm.stubFor(get(urlEqualTo(MATCH_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // when
        assertThatThrownBy(() -> service.getMatchById("KR_1", Platform.KR, permit(RiotMethod.MATCH), executor).join())
                .isInstanceOf(CompletionException.class);

        // then
        assertThat(registry.find("riot.api.call.wait").timer()).isNull();
        assertThat(registry.find("riot.api.call.download").timer()).isNull();
        assertThat(registry.find("riot.api.response.size").summary()).isNull();
        assertThat(registry.find("riot.api.call.duration")
                .tags("host", HOST, "method", "match-v5.match", "result", "error").timer().count()).isEqualTo(1);
    }

    private Timer timer(String name) {
        Timer timer = registry.find(name).tags("host", HOST, "method", "match-v5.match").timer();
        assertThat(timer).as(name).isNotNull();
        return timer;
    }

    private static RiotPermit permit(RiotMethod method) {
        return new RiotPermit("t-" + method.methodName(), "KR", method, RiotCallPriority.USER);
    }
}
