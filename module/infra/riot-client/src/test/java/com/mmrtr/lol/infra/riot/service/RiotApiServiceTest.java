package com.mmrtr.lol.infra.riot.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRequestAttributes;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RiotApiServiceTest {

    private final List<Object[]> seen = new ArrayList<>();

    @DisplayName("Riot ID 는 경로 세그먼트로 인코딩한다 — / ? # { } 가 다른 Riot 경로·쿼리가 되지 않는다")
    @Test
    void getAccountByRiotId_reservedCharacters_encodesAsPathSegments() {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RiotApiService service = new RiotApiService(builder.build());
        server.expect(requestTo("https://asia.api.riotgames.com/riot/account/v1/accounts/by-riot-id/"
                        + "..%2Flol%3Fx%23%7By%7D%20%EB%8B%A4/KR1"))
                .andRespond(withSuccess("{\"puuid\":\"p-1\",\"gameName\":\"n\",\"tagLine\":\"KR1\"}",
                        MediaType.APPLICATION_JSON));

        // when
        AccountDto account = service.getAccountByRiotId("../lol?x#{y} 다", "KR1", Platform.KR, Runnable::run)
                .join();

        // then
        server.verify();
        assertThat(account.getPuuid()).isEqualTo("p-1");
    }

    private RiotApiService recordingService() {
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor((request, bytes, execution) -> {
                    seen.add(new Object[] {
                            request.getAttributes().get(RiotRequestAttributes.PRIORITY),
                            request.getAttributes().get(RiotRequestAttributes.METHOD)});
                    return execution.execute(request, bytes);
                });
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        return new RiotApiService(builder.build());
    }

    @DisplayName("스코프 밖 호출은 USER 우선순위와 method 이름을 단다")
    @Test
    void riotCall_defaultsToUserPriority() {
        RiotApiService service = recordingService();

        service.getMatchById("KR_1", Platform.KR, Runnable::run).join();

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0)[0]).isEqualTo(RiotCallPriority.USER);
        assertThat(seen.get(0)[1]).isEqualTo("match-v5.match");
    }

    @DisplayName("다른 스레드에서 실행돼도 호출 스레드의 스코프 우선순위를 단다")
    @Test
    void riotCall_capturesCallerScope_evenOnAnotherThread() {
        RiotApiService service = recordingService();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            RiotCallScope.run(RiotCallPriority.BACKGROUND,
                    () -> service.getTimelineById("KR_1", Platform.KR, executor).join());
        } finally {
            executor.shutdown();
        }

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0)[0]).isEqualTo(RiotCallPriority.BACKGROUND);
        assertThat(seen.get(0)[1]).isEqualTo("match-v5.timeline");
    }

    @DisplayName("apex 리그는 method 이름에 티어 경로를 포함한다")
    @Test
    void apexLeague_methodIncludesTier() {
        RiotApiService service = recordingService();

        service.getApexLeague("challengerleagues", "RANKED_SOLO_5x5", Platform.KR, Runnable::run).join();

        assertThat(seen.get(0)[1]).isEqualTo("league-v4.challengerleagues");
    }
}
