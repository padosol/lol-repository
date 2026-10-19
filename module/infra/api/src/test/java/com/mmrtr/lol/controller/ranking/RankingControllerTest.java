package com.mmrtr.lol.controller.ranking;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.league.application.usecase.RankingQueryUseCase;
import com.mmrtr.lol.domain.league.domain.RankingQueue;
import com.mmrtr.lol.domain.league.readmodel.RankingReadModel;
import com.mmrtr.lol.domain.league.readmodel.TierCutoffReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.page.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * lol-server {@code RankControllerTest}·{@code TierCutoffControllerTest} 의 응답 필드 정의를 그대로 옮겨
 * 새 경로의 응답이 lol-server 와 같은지 검증한다. responseFields 는 문서화되지 않은 필드가 있거나 빠지면 실패한다.
 */
class RankingControllerTest extends RestDocsSupport {

    private final RankingQueryUseCase rankingQueryUseCase = mock(RankingQueryUseCase.class);

    @Override
    protected Object initController() {
        return new RankingController(rankingQueryUseCase);
    }

    @DisplayName("GET /api/v1/rankings")
    @Test
    void rankings() throws Exception {
        RankingReadModel faker = new RankingReadModel("puuid-faker", 1, 0, "hide on bush", "KR1", 100, 50,
                new BigDecimal("66.67"), "CHALLENGER", null, 1234, List.of("Ahri", "Zed"));
        given(rankingQueryUseCase.getRankings("kr", RankingQueue.SOLO, null, 1))
                .willReturn(new PageResult<>(List.of(faker), 1, 50, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/rankings")
                        .param("platform", "kr")
                        .param("queue", "SOLO")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].champions[1]").value("Zed"))
                .andExpect(jsonPath("$.data.isFirst").value(true))
                .andDo(document("rankings",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("platform").description("플랫폼 ID (예: kr)"),
                                parameterWithName("queue").description("게임 타입 (SOLO, FLEX, 기본값 SOLO)").optional(),
                                parameterWithName("page").description("페이지 번호 (1부터 시작, 페이지당 50개)").optional(),
                                parameterWithName("tier").description("조회할 티어 (e.g., CHALLENGER, GOLD)").optional()),
                        responseFields(success(
                                fieldWithPath("data.content[].puuid").type(JsonFieldType.STRING).description("소환사 PUUID"),
                                fieldWithPath("data.content[].currentRank").type(JsonFieldType.NUMBER).description("현재 순위"),
                                fieldWithPath("data.content[].rankChange").type(JsonFieldType.NUMBER)
                                        .description("순위 변동 (양수: 상승, 음수: 하락)"),
                                fieldWithPath("data.content[].gameName").type(JsonFieldType.STRING).description("소환사 게임 이름"),
                                fieldWithPath("data.content[].tagLine").type(JsonFieldType.STRING).description("태그 라인"),
                                fieldWithPath("data.content[].wins").type(JsonFieldType.NUMBER).description("승리 수"),
                                fieldWithPath("data.content[].losses").type(JsonFieldType.NUMBER).description("패배 수"),
                                fieldWithPath("data.content[].winRate").type(JsonFieldType.NUMBER).description("승률 (%)"),
                                fieldWithPath("data.content[].tier").type(JsonFieldType.STRING)
                                        .description("티어 (CHALLENGER, GRANDMASTER, MASTER, DIAMOND 등)"),
                                fieldWithPath("data.content[].rank").type(JsonFieldType.NULL)
                                        .description("랭크 (I, II, III, IV 또는 null)").optional(),
                                fieldWithPath("data.content[].leaguePoints").type(JsonFieldType.NUMBER).description("리그 포인트(LP)"),
                                fieldWithPath("data.content[].champions").type(JsonFieldType.ARRAY).description("주요 챔피언 이름 목록"),
                                fieldWithPath("data.page").type(JsonFieldType.NUMBER).description("현재 페이지 번호 (1부터 시작)"),
                                fieldWithPath("data.size").type(JsonFieldType.NUMBER).description("페이지당 항목 수"),
                                fieldWithPath("data.totalElements").type(JsonFieldType.NUMBER).description("전체 항목 수"),
                                fieldWithPath("data.totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                                fieldWithPath("data.isFirst").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                                fieldWithPath("data.isLast").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부")))));
    }

    @DisplayName("queue·page 를 생략하면 SOLO·1 페이지 (lol-server 와 같은 기본값)")
    @Test
    void rankingsDefaults() throws Exception {
        given(rankingQueryUseCase.getRankings(anyString(), any(), any(), anyInt()))
                .willReturn(new PageResult<>(List.of(), 1, 50, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/rankings").param("platform", "kr"))
                .andExpect(status().isOk());

        verify(rankingQueryUseCase).getRankings("kr", RankingQueue.SOLO, null, 1);
    }

    @DisplayName("platform 누락·모르는 queue 는 400 INVALID_INPUT (lol-server 는 각각 400·500)")
    @Test
    void rankingsInvalidInput() throws Exception {
        mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));

        mockMvc.perform(get("/api/v1/rankings").param("platform", "kr").param("queue", "DUO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));
    }

    @DisplayName("GET /api/v1/rankings/tier-cutoffs?platform=kr")
    @Test
    void tierCutoffs() throws Exception {
        given(rankingQueryUseCase.getTierCutoffs("kr", null, null)).willReturn(List.of(
                cutoff(1L, "RANKED_SOLO_5x5", "CHALLENGER", 1500, 10, 300),
                cutoff(2L, "RANKED_SOLO_5x5", "GRANDMASTER", 800, 5, 700),
                cutoff(3L, "RANKED_FLEX_SR", "CHALLENGER", 1200, -3, 200),
                cutoff(4L, "RANKED_FLEX_SR", "GRANDMASTER", 600, 2, 500)));

        mockMvc.perform(get("/api/v1/rankings/tier-cutoffs").param("platform", "kr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[2].lpChange").value(-3))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-01-15 12:00:00"))
                .andDo(document("ranking-tier-cutoffs",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("platform").description("플랫폼 ID (예: kr)")),
                        responseFields(success(tierCutoffFields("티어 컷오프 목록")))));
    }

    @DisplayName("GET /api/v1/rankings/tier-cutoffs?platform=kr&queue=RANKED_SOLO_5x5")
    @Test
    void tierCutoffsByQueue() throws Exception {
        given(rankingQueryUseCase.getTierCutoffs("kr", "RANKED_SOLO_5x5", null)).willReturn(List.of(
                cutoff(1L, "RANKED_SOLO_5x5", "CHALLENGER", 1500, 10, 300),
                cutoff(2L, "RANKED_SOLO_5x5", "GRANDMASTER", 800, 5, 700)));

        mockMvc.perform(get("/api/v1/rankings/tier-cutoffs")
                        .param("platform", "kr")
                        .param("queue", "RANKED_SOLO_5x5"))
                .andExpect(status().isOk())
                .andDo(document("ranking-tier-cutoffs-by-queue",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("platform").description("플랫폼 ID (예: kr)"),
                                parameterWithName("queue").description("큐 타입 필터 (예: RANKED_SOLO_5x5, RANKED_FLEX_SR)")),
                        responseFields(success(tierCutoffFields("티어 컷오프 목록")))));
    }

    @DisplayName("GET /api/v1/rankings/tier-cutoffs?platform=kr&queue=RANKED_SOLO_5x5&tier=CHALLENGER (옛 단건 조회)")
    @Test
    void tierCutoffByQueueAndTier() throws Exception {
        given(rankingQueryUseCase.getTierCutoffs("kr", "RANKED_SOLO_5x5", "CHALLENGER")).willReturn(List.of(
                cutoff(1L, "RANKED_SOLO_5x5", "CHALLENGER", 1500, 10, 300)));

        mockMvc.perform(get("/api/v1/rankings/tier-cutoffs")
                        .param("platform", "kr")
                        .param("queue", "RANKED_SOLO_5x5")
                        .param("tier", "CHALLENGER"))
                .andExpect(status().isOk())
                .andDo(document("ranking-tier-cutoffs-by-tier",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("platform").description("플랫폼 ID (예: kr)"),
                                parameterWithName("queue").description("큐 타입 (예: RANKED_SOLO_5x5, RANKED_FLEX_SR)"),
                                parameterWithName("tier").description("티어 (CHALLENGER, GRANDMASTER) — queue 와 함께 지정")),
                        responseFields(success(tierCutoffFields("티어 컷오프 목록 (단건)")))));
    }

    @DisplayName("tier 만 주고 queue 를 빼면 400 INVALID_INPUT")
    @Test
    void tierCutoffsTierWithoutQueue() throws Exception {
        given(rankingQueryUseCase.getTierCutoffs(anyString(), isNull(), anyString()))
                .willThrow(new CoreException(ErrorType.INVALID_INPUT, "tier 필터는 queue 와 함께 지정해야 합니다."));

        mockMvc.perform(get("/api/v1/rankings/tier-cutoffs").param("platform", "kr").param("tier", "CHALLENGER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"))
                .andExpect(jsonPath("$.errorMessage.message").value("잘못된 입력값입니다."));
    }

    private TierCutoffReadModel cutoff(Long id, String queue, String tier, int minLp, int lpChange, int userCount) {
        return new TierCutoffReadModel(id, queue, tier, "KR", minLp, lpChange, userCount, "2026-01-15 12:00:00");
    }

    private FieldDescriptor[] tierCutoffFields(String listDescription) {
        return new FieldDescriptor[] {
                fieldWithPath("data[]").type(JsonFieldType.ARRAY).description(listDescription),
                fieldWithPath("data[].id").type(JsonFieldType.NUMBER).description("티어 컷오프 ID"),
                fieldWithPath("data[].queue").type(JsonFieldType.STRING).description("큐 타입 (예: RANKED_SOLO_5x5, RANKED_FLEX_SR)"),
                fieldWithPath("data[].tier").type(JsonFieldType.STRING).description("티어 (CHALLENGER, GRANDMASTER)"),
                fieldWithPath("data[].platformId").type(JsonFieldType.STRING).description("플랫폼 ID"),
                fieldWithPath("data[].minLeaguePoints").type(JsonFieldType.NUMBER).description("해당 티어 진입에 필요한 최소 리그 포인트"),
                fieldWithPath("data[].lpChange").type(JsonFieldType.NUMBER).description("이전 갱신 대비 LP 변동량"),
                fieldWithPath("data[].userCount").type(JsonFieldType.NUMBER).description("해당 티어 유저 수"),
                fieldWithPath("data[].updatedAt").type(JsonFieldType.STRING).description("데이터 갱신 일시")
        };
    }

    private List<FieldDescriptor> success(FieldDescriptor... dataFields) {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS, FAIL)"),
                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)")));
        fields.addAll(List.of(dataFields));
        return fields;
    }
}
