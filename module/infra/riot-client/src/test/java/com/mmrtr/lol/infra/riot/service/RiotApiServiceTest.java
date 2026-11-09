package com.mmrtr.lol.infra.riot.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.infra.riot.adapter.SummonerApiAdapter;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.config.RiotApiConfig;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 실제 {@link RiotApiConfig} 의 RestClient(상태 처리기·가짜 서버 리다이렉트)로 WireMock 을 부른다.
 * 리다이렉트는 {@code https://{routing}.api.riotgames.com/path} 를 {@code {wiremock}/{routing}/path} 로 옮긴다.
 */
class RiotApiServiceTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final RiotCallRecorderTest.FakeLimiter limiter = new RiotCallRecorderTest.FakeLimiter();
    private RiotAPIProperties properties;
    private ExecutorService executor;
    private RiotApiService service;

    @BeforeEach
    void setUp() {
        wm.resetAll();
        properties = new RiotAPIProperties();
        properties.setApiKey("test-key");
        properties.getMock().setBaseUrl(wm.baseUrl());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "lab");
        RestClient client = new RiotApiConfig(properties, environment).riotRestClient();
        service = new RiotApiService(client, recorder());
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @DisplayName("Riot ID 는 경로 세그먼트로 인코딩한다 — / ? # { } 가 다른 Riot 경로·쿼리가 되지 않는다")
    @Test
    void getAccountByRiotId_reservedCharacters_encodesAsPathSegments() {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RiotApiService plain = new RiotApiService(builder.build(), recorder());
        server.expect(requestTo("https://asia.api.riotgames.com/riot/account/v1/accounts/by-riot-id/"
                        + "..%2Flol%3Fx%23%7By%7D%20%EB%8B%A4/KR1"))
                .andRespond(withSuccess("{\"puuid\":\"p-1\",\"gameName\":\"n\",\"tagLine\":\"KR1\"}",
                        MediaType.APPLICATION_JSON));

        // when
        AccountDto account = plain.getAccountByRiotId("../lol?x#{y} 다", "KR1", Platform.KR,
                permit(RiotMethod.ACCOUNT_BY_RIOT_ID), Runnable::run).join();

        // then
        server.verify();
        assertThat(account.getPuuid()).isEqualTo("p-1");
    }

    @DisplayName("다른 method 의 허가로 부르면 보내지 않고 IllegalArgumentException")
    @Test
    void rejectsPermitOfOtherMethod() {
        // given
        RiotPermit timeline = permit(RiotMethod.MATCH_TIMELINE);

        // when // then
        assertThatThrownBy(() -> service.getMatchById("KR_1", Platform.KR, timeline, executor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(wm.getAllServeEvents()).isEmpty();
        assertThat(limiter.completed).isEmpty();
    }

    @DisplayName("다른 platform 의 허가로 부르면 보내지 않고 IllegalArgumentException")
    @Test
    void rejectsPermitOfOtherPlatform() {
        // given
        RiotPermit jp = new RiotPermit("t-jp", "JP", RiotMethod.MATCH, RiotCallPriority.USER);

        // when // then
        assertThatThrownBy(() -> service.getMatchById("KR_1", Platform.KR, jp, executor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(wm.getAllServeEvents()).isEmpty();
    }

    @DisplayName("Riot ID 조회는 호출한 쪽이 받은 permit 3개를 모두 쓰고, 그 밖의 호출은 없다")
    @Test
    void riotIdLookupUsesThreeCallerPermits() {
        // given
        wm.stubFor(get(urlEqualTo("/asia/riot/account/v1/accounts/by-riot-id/hide/KR1"))
                .willReturn(okJson("{\"puuid\":\"p-1\",\"gameName\":\"hide\",\"tagLine\":\"KR1\"}")));
        wm.stubFor(get(urlEqualTo("/kr/lol/summoner/v4/summoners/by-puuid/p-1"))
                .willReturn(okJson("{\"puuid\":\"p-1\",\"profileIconId\":1,\"summonerLevel\":30,"
                        + "\"revisionDate\":1700000000000}")));
        wm.stubFor(get(urlEqualTo("/kr/lol/league/v4/entries/by-puuid/p-1")).willReturn(okJson("[]")));
        RiotPermits permits = RiotPermits.of(List.of(
                permit(RiotMethod.ACCOUNT_BY_RIOT_ID),
                permit(RiotMethod.SUMMONER_BY_PUUID),
                permit(RiotMethod.LEAGUE_ENTRIES_BY_PUUID)));

        // when
        Summoner summoner = new SummonerApiAdapter(service)
                .fetchSummonerByRiotId("hide", "KR1", "KR", permits, executor).join();

        // then
        assertThat(summoner.getPuuid()).isEqualTo("p-1");
        assertThat(permits.remaining(RiotMethod.ACCOUNT_BY_RIOT_ID)).isZero();
        assertThat(permits.remaining(RiotMethod.SUMMONER_BY_PUUID)).isZero();
        assertThat(permits.remaining(RiotMethod.LEAGUE_ENTRIES_BY_PUUID)).isZero();
        assertThat(wm.getAllServeEvents()).hasSize(3);
        assertThat(limiter.completed).extracting(RiotPermit::method).containsExactlyInAnyOrder(
                RiotMethod.ACCOUNT_BY_RIOT_ID, RiotMethod.SUMMONER_BY_PUUID, RiotMethod.LEAGUE_ENTRIES_BY_PUUID);
    }

    @DisplayName("429 는 Retry-After·X-Rate-Limit-Type 으로 정지하고 다시 보내지 않는다")
    @Test
    void rateLimited_pausesAndDoesNotResend() {
        // given
        wm.stubFor(get(urlEqualTo("/asia/lol/match/v5/matches/KR_1")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "7")
                .withHeader("X-Rate-Limit-Type", "application")));

        // when
        Throwable thrown = catchCause(() -> service.getMatchById("KR_1", Platform.KR,
                permit(RiotMethod.MATCH), executor).join());

        // then
        assertThat(thrown).isInstanceOfSatisfying(RiotRateLimitException.class, e -> {
            assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(7));
            assertThat(e.getRateLimitType()).isEqualTo("application");
        });
        wm.verify(1, getRequestedFor(urlEqualTo("/asia/lol/match/v5/matches/KR_1")));
        assertThat(limiter.pauses).containsExactly("asia|null|PT7S");
        assertThat(limiter.completed).hasSize(1);
    }

    @DisplayName("429 의 Retry-After 가 양의 정수가 아니면 없는 것으로 보고 method 를 기본 정지 시간만큼 멈춘다")
    @Test
    void rateLimitedWithoutValidRetryAfter_pausesMethodForDefault() {
        // given
        wm.stubFor(get(urlEqualTo("/asia/lol/match/v5/matches/KR_1")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "0")));

        // when
        Throwable thrown = catchCause(() -> service.getMatchById("KR_1", Platform.KR,
                permit(RiotMethod.MATCH), executor).join());

        // then
        assertThat(thrown).isInstanceOfSatisfying(RiotRateLimitException.class, e -> {
            assertThat(e.getRetryAfter()).isNull();
            assertThat(e.getRateLimitType()).isNull();
        });
        assertThat(limiter.pauses).containsExactly("asia|match-v5.match|PT1S");
    }

    @DisplayName("5xx 는 다시 보내지 않고 RiotServerException 이다")
    @Test
    void serverError_notResent() {
        // given
        wm.stubFor(get(urlEqualTo("/asia/lol/match/v5/matches/KR_1/timeline"))
                .willReturn(aResponse().withStatus(503)));

        // when
        Throwable thrown = catchCause(() -> service.getTimelineById("KR_1", Platform.KR,
                permit(RiotMethod.MATCH_TIMELINE), executor).join());

        // then
        assertThat(thrown).isInstanceOf(RiotServerException.class);
        wm.verify(1, getRequestedFor(urlEqualTo("/asia/lol/match/v5/matches/KR_1/timeline")));
        assertThat(limiter.completed).hasSize(1);
    }

    @DisplayName("404 는 RiotClientNotFoundException, 그 밖의 4xx 는 RiotClientException 이고 응답 헤더로 한도를 배운다")
    @Test
    void clientErrors_mappedInOrderAndLearned() {
        // given
        wm.stubFor(get(urlEqualTo("/kr/lol/spectator/v5/active-games/by-summoner/p-1"))
                .willReturn(aResponse().withStatus(404).withHeader("X-App-Rate-Limit", "500:10")));
        wm.stubFor(get(urlEqualTo("/kr/lol/spectator/v5/active-games/by-summoner/p-2"))
                .willReturn(aResponse().withStatus(400)));

        // when
        Throwable notFound = catchCause(() -> service.getActiveGameByPuuid("p-1", Platform.KR,
                permit(RiotMethod.ACTIVE_GAME), executor).join());
        Throwable badRequest = catchCause(() -> service.getActiveGameByPuuid("p-2", Platform.KR,
                permit(RiotMethod.ACTIVE_GAME), executor).join());

        // then
        assertThat(notFound).isInstanceOf(RiotClientNotFoundException.class);
        assertThat(badRequest).isExactlyInstanceOf(RiotClientException.class);
        // 404 응답 헤더로 배우고, 헤더가 없는 400 응답도 (빈 값으로) 배우기를 거친다
        assertThat(limiter.learned).containsExactly(
                "kr|spectator-v5.active-games|500:10|null",
                "kr|spectator-v5.active-games|null|null");
    }

    @DisplayName("apex 리그는 permit 의 method 로 티어 경로를 고르고, apex 가 아닌 method 는 거절한다")
    @Test
    void apexLeague_pathFromMethod() {
        // given
        wm.stubFor(get(urlEqualTo("/kr/lol/league/v4/grandmasterleagues/by-queue/RANKED_SOLO_5x5"))
                .willReturn(okJson("{\"entries\":[]}")));

        // when
        service.getApexLeague(RiotMethod.GRANDMASTER_LEAGUE, "RANKED_SOLO_5x5", Platform.KR,
                permit(RiotMethod.GRANDMASTER_LEAGUE), executor).join();

        // then
        wm.verify(1, getRequestedFor(urlEqualTo("/kr/lol/league/v4/grandmasterleagues/by-queue/RANKED_SOLO_5x5")));
        assertThatThrownBy(() -> service.getApexLeague(RiotMethod.LEAGUE_ENTRIES, "RANKED_SOLO_5x5", Platform.KR,
                permit(RiotMethod.LEAGUE_ENTRIES), executor)).isInstanceOf(IllegalArgumentException.class);
    }

    private RiotCallRecorder recorder() {
        return new RiotCallRecorder(limiter, properties, new SimpleMeterRegistry());
    }

    private static RiotPermit permit(RiotMethod method) {
        return new RiotPermit("t-" + method.methodName(), "KR", method, RiotCallPriority.USER);
    }

    private static Throwable catchCause(Runnable call) {
        try {
            call.run();
        } catch (CompletionException e) {
            return e.getCause();
        }
        throw new AssertionError("예외가 나지 않았다");
    }
}
