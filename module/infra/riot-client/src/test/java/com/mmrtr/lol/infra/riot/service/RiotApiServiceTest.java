package com.mmrtr.lol.infra.riot.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RiotApiServiceTest {

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
}
