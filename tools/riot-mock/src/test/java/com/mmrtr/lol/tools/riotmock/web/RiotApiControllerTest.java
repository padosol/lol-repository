package com.mmrtr.lol.tools.riotmock.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mmrtr.lol.tools.riotmock.world.LabIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiotApiControllerTest extends LabWebTestSupport {

    private static final String KEY = "X-Riot-Token";

    @Test
    @DisplayName("ids는 쿼리를 반영한다")
    void ids_쿼리를_반영한다() throws Exception {
        // given // when // then
        mvc.perform(get("/asia/lol/match/v5/matches/by-puuid/" + LabIds.puuidOf(1) + "/ids?start=0&count=5")
                        .header(KEY, "lab-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("숫자가 아닌 쿼리는 400이다")
    void ids_숫자아닌쿼리_400() throws Exception {
        // given // when // then
        mvc.perform(get("/asia/lol/match/v5/matches/by-puuid/" + LabIds.puuidOf(1) + "/ids?count=abc")
                        .header(KEY, "lab-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("엄청 큰 startTime도 오버플로 없이 처리한다")
    void ids_거대한startTime_빈배열() throws Exception {
        // given // when // then
        mvc.perform(get("/asia/lol/match/v5/matches/by-puuid/" + LabIds.puuidOf(1) + "/ids?startTime=9223372036854775807")
                        .header(KEY, "lab-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("riotId가 lab이면 puuid를 준다")
    void riotId_lab이면_puuid() throws Exception {
        // given // when // then
        mvc.perform(get("/asia/riot/account/v1/accounts/by-riot-id/lab1/LAB").header(KEY, "lab-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puuid").value(LabIds.puuidOf(1)));
    }

    @Test
    @DisplayName("인코딩된 한글 공백 riotId는 400도 500도 아닌 404다")
    void riotId_인코딩된_한글공백은_404() throws Exception {
        // given // when // then
        mvc.perform(get("/asia/riot/account/v1/accounts/by-riot-id/%ED%99%8D%20%EA%B8%B8%EB%8F%99/KR1")
                        .header(KEY, "lab-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status.message").value("Data not found"));
    }

    @Test
    @DisplayName("나머지 엔드포인트도 데이터를 준다")
    void 나머지엔드포인트_데이터() throws Exception {
        // given
        String puuid = LabIds.puuidOf(1);
        String matchId = LabIds.sharedMatchId(0, 0);

        // when // then
        mvc.perform(get("/asia/riot/account/v1/accounts/by-puuid/" + puuid).header(KEY, "lab-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gameName").value("lab1"));
        mvc.perform(get("/kr/lol/summoner/v4/summoners/by-puuid/" + puuid).header(KEY, "lab-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.puuid").value(puuid));
        mvc.perform(get("/kr/lol/league/v4/entries/by-puuid/" + puuid).header(KEY, "lab-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].queueType").value("RANKED_SOLO_5x5"));
        mvc.perform(get("/asia/lol/match/v5/matches/" + matchId).header(KEY, "lab-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metadata.matchId").value(matchId));
        mvc.perform(get("/asia/lol/match/v5/matches/" + matchId + "/timeline").header(KEY, "lab-key"))
                .andExpect(status().isOk());
    }
}
