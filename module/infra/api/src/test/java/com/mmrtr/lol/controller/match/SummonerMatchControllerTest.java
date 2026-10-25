package com.mmrtr.lol.controller.match;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.match.application.usecase.MatchQueryUseCase;
import com.mmrtr.lol.domain.match.application.usecase.MatchStatsQueryUseCase;
import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel;
import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel.DailyCount;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel.RankChampion;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.page.SliceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * lol-server {@code MatchControllerTest} 의 {@code match-summoner-get-list}·{@code match-get-ids}·
 * {@code match-get-rank-champions}·{@code match-daily-count} 응답 필드 정의를 그대로 옮겨 응답이 lol-server 와 같은지 검증한다.
 * page 설명만 실제 동작(0부터)에 맞췄다. 오버레이·필터·집계 규칙은 서비스·어댑터 테스트가 검증한다.
 */
class SummonerMatchControllerTest extends RestDocsSupport {

    private final MatchQueryUseCase matchQueryUseCase = mock(MatchQueryUseCase.class);
    private final MatchStatsQueryUseCase matchStatsQueryUseCase = mock(MatchStatsQueryUseCase.class);

    @Override
    protected Object initController() {
        return new SummonerMatchController(matchQueryUseCase, matchStatsQueryUseCase);
    }

    @DisplayName("GET /api/v1/summoners/{puuid}/matches — lol-server 와 같은 전적 응답")
    @Test
    void getMatches_validRequest_returnsLolServerShape() throws Exception {
        // given
        given(matchQueryUseCase.getSummonerMatches("puuid-1234", 2025, 420, 1))
                .willReturn(new SliceResult<>(List.of(MatchControllerTest.game()), false));
        List<FieldDescriptor> fields = new ArrayList<>(MatchControllerTest.gameFields("data.content[]."));
        fields.add(fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/matches", "puuid-1234")
                        .param("season", "2025")
                        .param("queueId", "420")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andDo(document("match-summoner-get-list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("조회할 소환사의 PUUID")
                        ),
                        queryParameters(
                                parameterWithName("season").description("시즌 (연도)").optional(),
                                parameterWithName("queueId").description("큐 ID (e.g., 420:솔로랭크, 430:일반, 450:칼바람)").optional(),
                                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional()
                        ),
                        responseFields(fields)));
    }

    @DisplayName("GET /api/v1/summoners/{puuid}/match-ids — lol-server 와 같은 매치 ID 응답")
    @Test
    void getMatchIds_validRequest_returnsLolServerShape() throws Exception {
        // given
        given(matchQueryUseCase.getSummonerMatchIds("puuid-1234", 420, 1))
                .willReturn(new SliceResult<>(List.of("KR_123456789", "KR_987654321"), true));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/match-ids", "puuid-1234")
                        .param("queueId", "420")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andDo(document("match-get-ids",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("조회할 유저의 PUUID")
                        ),
                        queryParameters(
                                parameterWithName("queueId").description("큐 ID (e.g., 420:솔로랭크, 430:일반, 450:칼바람)").optional(),
                                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional()
                        ),
                        responseFields(
                                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS, FAIL)"),
                                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)"),
                                fieldWithPath("data.content[]").type(JsonFieldType.ARRAY).description("매치 ID 목록"),
                                fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 항목 존재 여부")
                        )));
    }

    @DisplayName("page 를 생략하면 0페이지 — 전적 목록도 매치 ID 와 같다 (lol-server 전적 목록은 1)")
    @Test
    void getMatches_pageOmitted_requestsFirstPage() throws Exception {
        // given
        given(matchQueryUseCase.getSummonerMatches("puuid-1", null, null, 0)).willReturn(SliceResult.empty());
        given(matchQueryUseCase.getSummonerMatchIds("puuid-1", null, 0)).willReturn(SliceResult.empty());

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/matches", "puuid-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mockMvc.perform(get("/api/v1/summoners/{puuid}/match-ids", "puuid-1"))
                .andExpect(status().isOk());
        verify(matchQueryUseCase).getSummonerMatches("puuid-1", null, null, 0);
        verify(matchQueryUseCase).getSummonerMatchIds("puuid-1", null, 0);
    }

    @DisplayName("GET /api/v1/summoners/{puuid}/champions — lol-server 와 같은 랭크 챔피언 응답 (솔로/자유 분리)")
    @Test
    void getRankChampions_validRequest_returnsLolServerShape() throws Exception {
        // given
        RankChampion solo = new RankChampion(8.5, 5.5, 10.5, 266, "Aatrox", 20L, 10L, 66.7, 850.0, 3.45, 72.5,
                22.5, 420.0, 30L);
        RankChampion flex = new RankChampion(5.0, 6.0, 8.0, 157, "Yasuo", 7L, 5L, 58.3, 720.0, 2.17, 68.0,
                20.0, 395.0, 12L);
        given(matchStatsQueryUseCase.getRankChampions("puuid-1234", 2024))
                .willReturn(new RankChampionsReadModel(List.of(solo), List.of(flex)));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/champions", "puuid-1234").param("season", "2024"))
                .andExpect(status().isOk())
                .andDo(document("match-get-rank-champions",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("조회할 유저의 PUUID")
                        ),
                        queryParameters(
                                parameterWithName("season").description("시즌").optional()
                        ),
                        responseFields(
                                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS, FAIL)"),
                                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)"),
                                fieldWithPath("data.solo[].championId").type(JsonFieldType.NUMBER).description("솔로 랭크 챔피언 ID"),
                                fieldWithPath("data.solo[].championName").type(JsonFieldType.STRING).description("솔로 랭크 챔피언 이름"),
                                fieldWithPath("data.solo[].kills").type(JsonFieldType.NUMBER).description("솔로 랭크 평균 킬"),
                                fieldWithPath("data.solo[].deaths").type(JsonFieldType.NUMBER).description("솔로 랭크 평균 데스"),
                                fieldWithPath("data.solo[].assists").type(JsonFieldType.NUMBER).description("솔로 랭크 평균 어시스트"),
                                fieldWithPath("data.solo[].win").type(JsonFieldType.NUMBER).description("솔로 랭크 승리 횟수"),
                                fieldWithPath("data.solo[].losses").type(JsonFieldType.NUMBER).description("솔로 랭크 패배 횟수"),
                                fieldWithPath("data.solo[].winRate").type(JsonFieldType.NUMBER).description("솔로 랭크 승률 (%)"),
                                fieldWithPath("data.solo[].damagePerMinute").type(JsonFieldType.NUMBER).description("솔로 랭크 분당 피해량"),
                                fieldWithPath("data.solo[].kda").type(JsonFieldType.NUMBER).description("솔로 랭크 KDA"),
                                fieldWithPath("data.solo[].laneMinionsFirst10Minutes").type(JsonFieldType.NUMBER).description("솔로 랭크 10분 CS"),
                                fieldWithPath("data.solo[].damageTakenOnTeamPercentage").type(JsonFieldType.NUMBER).description("솔로 랭크 팀 피해 분담률 (%)"),
                                fieldWithPath("data.solo[].goldPerMinute").type(JsonFieldType.NUMBER).description("솔로 랭크 분당 골드"),
                                fieldWithPath("data.solo[].playCount").type(JsonFieldType.NUMBER).description("솔로 랭크 플레이 횟수"),
                                fieldWithPath("data.flex[].championId").type(JsonFieldType.NUMBER).description("자유 랭크 챔피언 ID"),
                                fieldWithPath("data.flex[].championName").type(JsonFieldType.STRING).description("자유 랭크 챔피언 이름"),
                                fieldWithPath("data.flex[].kills").type(JsonFieldType.NUMBER).description("자유 랭크 평균 킬"),
                                fieldWithPath("data.flex[].deaths").type(JsonFieldType.NUMBER).description("자유 랭크 평균 데스"),
                                fieldWithPath("data.flex[].assists").type(JsonFieldType.NUMBER).description("자유 랭크 평균 어시스트"),
                                fieldWithPath("data.flex[].win").type(JsonFieldType.NUMBER).description("자유 랭크 승리 횟수"),
                                fieldWithPath("data.flex[].losses").type(JsonFieldType.NUMBER).description("자유 랭크 패배 횟수"),
                                fieldWithPath("data.flex[].winRate").type(JsonFieldType.NUMBER).description("자유 랭크 승률 (%)"),
                                fieldWithPath("data.flex[].damagePerMinute").type(JsonFieldType.NUMBER).description("자유 랭크 분당 피해량"),
                                fieldWithPath("data.flex[].kda").type(JsonFieldType.NUMBER).description("자유 랭크 KDA"),
                                fieldWithPath("data.flex[].laneMinionsFirst10Minutes").type(JsonFieldType.NUMBER).description("자유 랭크 10분 CS"),
                                fieldWithPath("data.flex[].damageTakenOnTeamPercentage").type(JsonFieldType.NUMBER).description("자유 랭크 팀 피해 분담률 (%)"),
                                fieldWithPath("data.flex[].goldPerMinute").type(JsonFieldType.NUMBER).description("자유 랭크 분당 골드"),
                                fieldWithPath("data.flex[].playCount").type(JsonFieldType.NUMBER).description("자유 랭크 플레이 횟수")
                        )));
    }

    @DisplayName("GET /api/v1/summoners/{puuid}/matches/daily-count — lol-server 와 같은 날짜별 게임 수 응답")
    @Test
    void getDailyGameCounts_validRequest_returnsLolServerShape() throws Exception {
        // given
        given(matchStatsQueryUseCase.getDailyGameCounts("puuid-1234", 2025, 420)).willReturn(
                new DailyGameCountReadModel(List.of(
                        new DailyCount(LocalDate.of(2025, 1, 15), 3L),
                        new DailyCount(LocalDate.of(2025, 1, 14), 5L),
                        new DailyCount(LocalDate.of(2025, 1, 13), 2L)), 2L, 5L));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/matches/daily-count", "puuid-1234")
                        .param("season", "2025")
                        .param("queueId", "420"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyCounts[0].gameDate").value("2025-01-15"))
                .andDo(document("match-daily-count",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("조회할 소환사의 PUUID")
                        ),
                        queryParameters(
                                parameterWithName("season").description("시즌 (연도)"),
                                parameterWithName("queueId").description("큐 ID (e.g., 420:솔로랭크, 440:자유랭크)").optional()
                        ),
                        responseFields(
                                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS, FAIL)"),
                                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)"),
                                fieldWithPath("data.dailyCounts[].gameDate").type(JsonFieldType.STRING).description("게임 날짜 (yyyy-MM-dd)"),
                                fieldWithPath("data.dailyCounts[].gameCount").type(JsonFieldType.NUMBER).description("해당 날짜의 게임 수"),
                                fieldWithPath("data.minCount").type(JsonFieldType.NUMBER).description("기간 내 일별 최소 게임 수"),
                                fieldWithPath("data.maxCount").type(JsonFieldType.NUMBER).description("기간 내 일별 최대 게임 수")
                        )));
    }

    @DisplayName("daily-count 는 season 이 없으면 400, champions 는 season 없이 전 시즌")
    @Test
    void stats_seasonOmitted_dailyCountRejectsAndChampionsAllSeasons() throws Exception {
        // given
        given(matchStatsQueryUseCase.getRankChampions("puuid-1", null))
                .willReturn(new RankChampionsReadModel(List.of(), List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/matches/daily-count", "puuid-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));
        mockMvc.perform(get("/api/v1/summoners/{puuid}/champions", "puuid-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.solo").isEmpty());
        verify(matchStatsQueryUseCase).getRankChampions("puuid-1", null);
    }

    @DisplayName("음수 page·숫자가 아닌 page 는 400 INVALID_INPUT")
    @Test
    void getMatches_invalidPage_returns400() throws Exception {
        // given
        given(matchQueryUseCase.getSummonerMatches("puuid-1", null, null, -1))
                .willThrow(new CoreException(ErrorType.INVALID_INPUT));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/matches", "puuid-1").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));
        mockMvc.perform(get("/api/v1/summoners/{puuid}/match-ids", "puuid-1").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));
    }
}
