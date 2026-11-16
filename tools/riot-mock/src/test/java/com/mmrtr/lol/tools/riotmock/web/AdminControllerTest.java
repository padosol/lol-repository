package com.mmrtr.lol.tools.riotmock.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AdminControllerTest extends LabWebTestSupport {

    @Test
    @DisplayName("reset은 epoch를 지정하거나 올린다")
    void reset_epoch를_지정하거나_올린다() throws Exception {
        // given // when // then
        mvc.perform(post("/admin/reset").contentType(MediaType.APPLICATION_JSON).content("{\"epoch\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.epoch").value(0));
        mvc.perform(post("/admin/reset").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.epoch").value(1));
        mvc.perform(get("/admin/epoch")).andExpect(jsonPath("$.epoch").value(1));
        assertThat(world.epoch()).isEqualTo(1);
    }

    @Test
    @DisplayName("본문 없는 reset도 epoch를 올리고 엔진 창을 비운다")
    void reset_본문없음_창을_비운다() throws Exception {
        // given
        engine.acquire("asia", "match-v5.match");

        // when
        mvc.perform(post("/admin/reset")).andExpect(status().isOk()).andExpect(jsonPath("$.epoch").value(1));

        // then
        assertThat(engine.usage()).isEmpty();
    }

    @Test
    @DisplayName("limits 조회는 app과 methods와 defaultMethod를 준다")
    void limits_조회() throws Exception {
        // given // when // then
        mvc.perform(get("/admin/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("500:10"))
                .andExpect(jsonPath("$.defaultMethod").value("1000:10"))
                .andExpect(jsonPath("$.methods['match-v5.match']").value("2000:10"));
    }

    @Test
    @DisplayName("limits는 일부만 바꿀 수 있고 형식이 틀리면 400이며 아무것도 바꾸지 않는다")
    void limits_부분변경과_검증() throws Exception {
        // given // when
        mvc.perform(put("/admin/limits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"methods\":{\"match-v5.match\":\"5:10\"},\"defaultMethod\":\"7:10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("500:10"))
                .andExpect(jsonPath("$.methods['match-v5.match']").value("5:10"))
                .andExpect(jsonPath("$.defaultMethod").value("7:10"));
        mvc.perform(put("/admin/limits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app\":\"9:10\",\"defaultMethod\":\"garbage\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/admin/limits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"methods\":{\"no-such.endpoint\":\"5:10\"}}"))
                .andExpect(status().isBadRequest());

        // then
        assertThat(engine.appLimit()).isEqualTo("500:10");
    }
}
