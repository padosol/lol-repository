package com.mmrtr.lol.tools.riotmock.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mmrtr.lol.infra.riot.ratelimit.RateLimitWindow;
import com.mmrtr.lol.tools.riotmock.world.LabIds;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class HeaderContractTest extends LabWebTestSupport {

    @Test
    @DisplayName("가짜 서버 헤더를 앱 파서가 그대로 읽는다")
    void headers_앱_파서가_그대로_읽는다() throws Exception {
        // given
        mvc.perform(put("/admin/limits").contentType(MediaType.APPLICATION_JSON).content("{\"app\":\"2:10\"}"));
        String path = "/asia/lol/match/v5/matches/" + LabIds.sharedMatchId(0, 0);
        mvc.perform(get(path).header("X-Riot-Token", "lab-key"));
        mvc.perform(get(path).header("X-Riot-Token", "lab-key"));

        // when
        MockHttpServletResponse rejected = mvc.perform(get(path).header("X-Riot-Token", "lab-key"))
                .andExpect(status().isTooManyRequests()).andReturn().getResponse();

        // then
        assertThat(RateLimitWindow.parse(rejected.getHeader("X-App-Rate-Limit")))
                .containsExactly(new RateLimitWindow(2, 10));
        assertThat(RateLimitWindow.parse(rejected.getHeader("X-App-Rate-Limit-Count")))
                .isEqualTo(List.of(new RateLimitWindow(3, 10)));
    }
}
