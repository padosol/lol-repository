package com.mmrtr.lol.tools.riotmock.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mmrtr.lol.tools.riotmock.world.LabIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RiotGateFilterTest extends LabWebTestSupport {

    private static final String MATCH_PATH = "/asia/lol/match/v5/matches/" + LabIds.sharedMatchId(0, 0);

    @Test
    @DisplayName("토큰이 없으면 401이고 한도를 세지 않는다")
    void doFilter_토큰없음_401이고_세지않는다() throws Exception {
        // given // when
        mvc.perform(get(MATCH_PATH)).andExpect(status().isUnauthorized());

        // then
        assertThat(engine.usage()).isEmpty();
    }

    @Test
    @DisplayName("한도 이내면 200과 네 헤더를 준다")
    void doFilter_한도이내_200과_네헤더() throws Exception {
        // given // when // then
        mvc.perform(get(MATCH_PATH).header("X-Riot-Token", "lab-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-App-Rate-Limit", "500:10"))
                .andExpect(header().string("X-App-Rate-Limit-Count", "1:10"))
                .andExpect(header().string("X-Method-Rate-Limit", "2000:10"))
                .andExpect(header().string("X-Method-Rate-Limit-Count", "1:10"));
    }

    @Test
    @DisplayName("application 한도를 넘으면 429와 Retry-After와 Type을 준다")
    void doFilter_application초과_429와_RetryAfter와_Type() throws Exception {
        // given
        mvc.perform(put("/admin/limits").contentType(MediaType.APPLICATION_JSON).content("{\"app\":\"2:10\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(MATCH_PATH).header("X-Riot-Token", "lab-key")).andExpect(status().isOk());
        mvc.perform(get(MATCH_PATH).header("X-Riot-Token", "lab-key")).andExpect(status().isOk());

        // when // then
        mvc.perform(get(MATCH_PATH).header("X-Riot-Token", "lab-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(header().string("X-Rate-Limit-Type", "application"))
                .andExpect(header().string("X-App-Rate-Limit", "2:10"))
                .andExpect(jsonPath("$.status.status_code").value(429))
                .andExpect(jsonPath("$.status.message").value("Rate limit exceeded"));
    }

    @Test
    @DisplayName("모르는 경로는 404이고 unknown 지표가 오른다")
    void doFilter_모르는경로_404와_unknown지표() throws Exception {
        // given // when
        mvc.perform(get("/asia/lol/nope/v1/x").header("X-Riot-Token", "lab-key"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-App-Rate-Limit-Count", "1:10"));
        metrics.refresh();

        // then
        assertThat(scrape()).contains("riot_mock_unknown_total{routing=\"unknown\"} 1.0");
    }

    @Test
    @DisplayName("prometheus에 심판 지표가 나온다")
    void prometheus_요청1건뒤_심판지표가_있다() throws Exception {
        // given
        mvc.perform(get(MATCH_PATH).header("X-Riot-Token", "lab-key")).andExpect(status().isOk());

        // when
        metrics.refresh();

        // then
        String scrape = scrape();
        assertThat(scrape).contains("riot_mock_requests_total{endpoint=\"match-v5.match\",routing=\"asia\",status=\"200\"}");
        assertThat(scrape).contains("riot_mock_sliding_peak_ratio{routing=\"asia\",window=\"10\"}");
        assertThat(scrape).contains("riot_mock_window_count{bucket=\"asia|app\",window=\"10\"} 1.0");
        assertThat(scrape).contains("riot_mock_window_limit{bucket=\"asia|app\",window=\"10\"} 500.0");
    }

    @Test
    @DisplayName("창이 지난 버킷은 지표에서 0으로 보인다")
    void prometheus_창만료_카운트0() throws Exception {
        // given
        mvc.perform(get(MATCH_PATH).header("X-Riot-Token", "lab-key")).andExpect(status().isOk());
        NOW.addAndGet(11_000);

        // when
        metrics.refresh();

        // then
        assertThat(scrape()).contains("riot_mock_window_count{bucket=\"asia|app\",window=\"10\"} 0.0");
    }

    private String scrape() throws Exception {
        return mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
