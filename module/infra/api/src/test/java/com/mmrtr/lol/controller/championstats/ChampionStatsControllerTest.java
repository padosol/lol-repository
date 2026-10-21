package com.mmrtr.lol.controller.championstats;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.championstats.application.usecase.ChampionStatsQueryUseCase;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionAverageStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionBootBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionMatchupReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionPositionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRuneBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSkillBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSpellStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStartItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
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
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * lol-server {@code ChampionStatsControllerTest} 의 응답 필드 정의를 그대로 옮겨 새 경로의 응답이 lol-server 와 같은지 검증한다.
 * responseFields 는 문서화되지 않은 필드가 있거나 빠지면 실패한다. 입력 검증은 {@code ChampionStatsQueryServiceTest} 가 맡는다.
 */
class ChampionStatsControllerTest extends RestDocsSupport {

    private static final String TIER_DESCRIPTION = "티어 필터. 단일 티어(e.g., EMERALD) 또는 범위 티어(e.g., MASTER+)를 지원합니다.";

    private final ChampionStatsQueryUseCase championStatsQueryUseCase = mock(ChampionStatsQueryUseCase.class);

    @Override
    protected Object initController() {
        return new ChampionStatsController(championStatsQueryUseCase);
    }

    @DisplayName("GET /api/v1/champion-stats/{championId} — lol-server 와 같은 상세 응답")
    @Test
    void getChampionStats_validRequest_returnsLolServerShape() throws Exception {
        // given
        ChampionPositionStatsReadModel positionStats = new ChampionPositionStatsReadModel(
                "TOP", 0.55, 0.075, 0.04, "S", 1500,
                new ChampionAverageStatsReadModel("TOP", 6.2, 5.4, 7.8, 2.59, 380.5, 65.3, 12.1),
                List.of(ChampionMatchupReadModel.top(86, 200, 0.55, 0.4),
                        ChampionMatchupReadModel.bottom(122, 180, 0.45, 0.36)),
                List.of(new ChampionRuneBuildReadModel(8000, 8400, 8010, 9111, 9104, 8299, 8446, 8451, 250, 0.56, 0.5)),
                List.of(new ChampionSpellStatsReadModel(4, 14, 800, 0.52, 0.8)),
                List.of(new ChampionSkillBuildReadModel("Q,E,W,Q,Q,R,Q,E,Q,E,R,E,E,W,W", 400, 0.55, 0.4)),
                List.of(new ChampionStartItemBuildReadModel(List.of(1054, 2003), 600, 0.51, 0.6)),
                List.of(new ChampionBootBuildReadModel(3047, 700, 0.53, 0.7)),
                List.of(new ChampionItemBuildReadModel(List.of(3078, 3053, 3065), 300, 0.5667, 0.3)));
        given(championStatsQueryUseCase.getChampionStats(266, "kr", "14.24", "EMERALD"))
                .willReturn(new ChampionStatsReadModel("EMERALD", List.of(positionStats)));

        // when & then
        mockMvc.perform(get("/api/v1/champion-stats/{championId}", 266)
                        .param("platform", "kr")
                        .param("patch", "14.24")
                        .param("tier", "EMERALD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.positions[0].itemBuilds[0].itemBuild[2]").value(3065))
                .andDo(document("champion-stats-get",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("championId").description("챔피언 ID (e.g., 266)")),
                        queryParameters(
                                parameterWithName("platform").description("플랫폼 ID (예: kr)"),
                                parameterWithName("patch").description("패치 버전 (e.g., 14.24)"),
                                parameterWithName("tier").description(TIER_DESCRIPTION)),
                        responseFields(success(detailFields()))));
    }

    @DisplayName("GET /api/v1/champion-stats — lol-server 와 같은 포지션별 목록 응답")
    @Test
    void getChampionStatsByPosition_validRequest_returnsLolServerShape() throws Exception {
        // given
        given(championStatsQueryUseCase.getChampionStatsByPosition("kr", "16.1", "EMERALD")).willReturn(List.of(
                new PositionChampionStatsReadModel("TOP", List.of(
                        new ChampionRateReadModel(266, 0.5200, 0.0800, 0.0500, 1500, "S+"),
                        new ChampionRateReadModel(122, 0.4800, 0.0600, 0.0300, 1200, "C"))),
                new PositionChampionStatsReadModel("JUNGLE", List.of(
                        new ChampionRateReadModel(64, 0.5100, 0.1000, 0.0700, 2000, "A")))));

        // when & then
        mockMvc.perform(get("/api/v1/champion-stats")
                        .param("platform", "kr")
                        .param("patch", "16.1")
                        .param("tier", "EMERALD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].champions[0].totalGames").doesNotExist())
                .andDo(document("champion-stats-positions",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("platform").description("플랫폼 ID (예: kr)"),
                                parameterWithName("patch").description("패치 버전 (e.g., 16.1)"),
                                parameterWithName("tier").description(TIER_DESCRIPTION)),
                        responseFields(success(
                                fieldWithPath("data[]").type(JsonFieldType.ARRAY).description("포지션별 챔피언 통계 목록"),
                                fieldWithPath("data[].teamPosition").type(JsonFieldType.STRING)
                                        .description("포지션 (TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY)"),
                                fieldWithPath("data[].champions[]").type(JsonFieldType.ARRAY).description("해당 포지션의 챔피언 목록"),
                                fieldWithPath("data[].champions[].championId").type(JsonFieldType.NUMBER).description("챔피언 ID"),
                                fieldWithPath("data[].champions[].winRate").type(JsonFieldType.NUMBER).description("승률"),
                                fieldWithPath("data[].champions[].pickRate").type(JsonFieldType.NUMBER).description("픽률"),
                                fieldWithPath("data[].champions[].banRate").type(JsonFieldType.NUMBER).description("밴률"),
                                fieldWithPath("data[].champions[].tier").type(JsonFieldType.STRING)
                                        .description("티어 등급 (S+, S, A, B, C, D)")))));
    }

    @DisplayName("tier=MASTER+ 의 '+' 가 공백으로 디코딩돼 들어오면 '+' 로 되돌려 넘긴다")
    @Test
    void getChampionStatsByPosition_decodedPlus_restoresRangeTier() throws Exception {
        // given
        given(championStatsQueryUseCase.getChampionStatsByPosition(anyString(), anyString(), anyString()))
                .willReturn(List.of());

        // when
        mockMvc.perform(get("/api/v1/champion-stats")
                        .param("platform", "kr")
                        .param("patch", "16.1")
                        .param("tier", "MASTER "))
                .andExpect(status().isOk());

        // then
        verify(championStatsQueryUseCase).getChampionStatsByPosition("kr", "16.1", "MASTER+");
    }

    @DisplayName("필수 query 누락·숫자가 아닌 championId 는 400 INVALID_INPUT")
    @Test
    void missingParamOrInvalidChampionId_returns400() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/champion-stats").param("platform", "kr").param("tier", "EMERALD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));

        mockMvc.perform(get("/api/v1/champion-stats/{championId}", "ahri")
                        .param("platform", "kr").param("patch", "16.1").param("tier", "EMERALD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));
    }

    @DisplayName("잘못된 tier 는 400 (INVALID_TIER_FILTER)")
    @Test
    void invalidTier_returns400() throws Exception {
        // given
        given(championStatsQueryUseCase.getChampionStatsByPosition("kr", "16.1", "UNRANKED"))
                .willThrow(new CoreException(ErrorType.INVALID_TIER_FILTER));

        // when & then
        mockMvc.perform(get("/api/v1/champion-stats")
                        .param("platform", "kr").param("patch", "16.1").param("tier", "UNRANKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.message").value(ErrorType.INVALID_TIER_FILTER.getMessage()));
    }

    private static List<FieldDescriptor> success(FieldDescriptor... dataFields) {
        return success(Arrays.asList(dataFields));
    }

    private static List<FieldDescriptor> success(List<FieldDescriptor> dataFields) {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과"),
                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)")));
        fields.addAll(dataFields);
        return fields;
    }

    private static List<FieldDescriptor> detailFields() {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("data.tier").type(JsonFieldType.STRING).description("티어 (e.g., EMERALD)"),

                // positions (리스트)
                fieldWithPath("data.positions[]").type(JsonFieldType.ARRAY).description("포지션별 통계 목록"),
                fieldWithPath("data.positions[].teamPosition").type(JsonFieldType.STRING).description("포지션"),
                fieldWithPath("data.positions[].winRate").type(JsonFieldType.NUMBER).description("승률"),
                fieldWithPath("data.positions[].pickRate").type(JsonFieldType.NUMBER).description("픽률 (해당 라인 기준)"),
                fieldWithPath("data.positions[].banRate").type(JsonFieldType.NUMBER).description("밴률 (라인 무관)"),
                // lol-server 문서는 필수로 적었지만 목록 통계에 없는 포지션은 null 이다
                fieldWithPath("data.positions[].tier").type(JsonFieldType.STRING).optional()
                        .description("티어 등급 (S+, S, A, B, C, D — /champion-stats 목록과 동일 룰, 목록에 없으면 null)"),
                fieldWithPath("data.positions[].totalGames").type(JsonFieldType.NUMBER).description("총 게임 수"),

                // averages (챔피언 단위 평균 통계)
                fieldWithPath("data.positions[].averages").type(JsonFieldType.OBJECT).description("챔피언 단위 평균 통계 (BQ 미적재 시 null)").optional(),
                fieldWithPath("data.positions[].averages.teamPosition").type(JsonFieldType.STRING).description("포지션").optional(),
                fieldWithPath("data.positions[].averages.avgKills").type(JsonFieldType.NUMBER).description("평균 킬").optional(),
                fieldWithPath("data.positions[].averages.avgDeaths").type(JsonFieldType.NUMBER).description("평균 데스").optional(),
                fieldWithPath("data.positions[].averages.avgAssists").type(JsonFieldType.NUMBER).description("평균 어시스트").optional(),
                fieldWithPath("data.positions[].averages.kda").type(JsonFieldType.NUMBER).description("KDA = (킬+어시) / max(데스, 1)").optional(),
                fieldWithPath("data.positions[].averages.avgGoldPerMinute").type(JsonFieldType.NUMBER).description("분당 평균 골드 (gold_per_minute)").optional(),
                fieldWithPath("data.positions[].averages.avgLaneCs10m").type(JsonFieldType.NUMBER).description("10분 시점 평균 라인 CS").optional(),
                fieldWithPath("data.positions[].averages.avgJungleCs10m").type(JsonFieldType.NUMBER).description("10분 시점 평균 정글 CS").optional(),

                // matchups (rankType=TOP: 잘 잡는 상대 / BOTTOM: 카운터)
                fieldWithPath("data.positions[].matchups[]").type(JsonFieldType.ARRAY).description("매치업 목록 (rankType=TOP: 잘 잡는 상대, BOTTOM: 카운터)"),
                fieldWithPath("data.positions[].matchups[].rankType").type(JsonFieldType.STRING).description("매치업 분류 (TOP=잘 잡는 상대 / BOTTOM=카운터)"),
                fieldWithPath("data.positions[].matchups[].opponentChampionId").type(JsonFieldType.NUMBER).description("상대 챔피언 ID"),
                fieldWithPath("data.positions[].matchups[].games").type(JsonFieldType.NUMBER).description("게임 수"),
                fieldWithPath("data.positions[].matchups[].winRate").type(JsonFieldType.NUMBER).description("승률"),
                fieldWithPath("data.positions[].matchups[].pickRate").type(JsonFieldType.NUMBER).description("대면 비율"),

                // runeBuilds
                fieldWithPath("data.positions[].runeBuilds[]").type(JsonFieldType.ARRAY).description("룬 빌드 목록"),
                fieldWithPath("data.positions[].runeBuilds[].primaryStyleId").type(JsonFieldType.NUMBER).description("주 룬 스타일 ID"),
                fieldWithPath("data.positions[].runeBuilds[].subStyleId").type(JsonFieldType.NUMBER).description("보조 룬 스타일 ID"),
                fieldWithPath("data.positions[].runeBuilds[].primaryPerk0").type(JsonFieldType.NUMBER).description("주 룬 키스톤"),
                fieldWithPath("data.positions[].runeBuilds[].primaryPerk1").type(JsonFieldType.NUMBER).description("주 룬 슬롯 1"),
                fieldWithPath("data.positions[].runeBuilds[].primaryPerk2").type(JsonFieldType.NUMBER).description("주 룬 슬롯 2"),
                fieldWithPath("data.positions[].runeBuilds[].primaryPerk3").type(JsonFieldType.NUMBER).description("주 룬 슬롯 3"),
                fieldWithPath("data.positions[].runeBuilds[].subPerk0").type(JsonFieldType.NUMBER).description("보조 룬 슬롯 0"),
                fieldWithPath("data.positions[].runeBuilds[].subPerk1").type(JsonFieldType.NUMBER).description("보조 룬 슬롯 1"),

                // spellStats
                fieldWithPath("data.positions[].spellStats[]").type(JsonFieldType.ARRAY).description("소환사 주문 조합 목록"),
                fieldWithPath("data.positions[].spellStats[].summoner1Id").type(JsonFieldType.NUMBER).description("소환사 주문 1 ID"),
                fieldWithPath("data.positions[].spellStats[].summoner2Id").type(JsonFieldType.NUMBER).description("소환사 주문 2 ID"),

                // skillBuilds
                fieldWithPath("data.positions[].skillBuilds[]").type(JsonFieldType.ARRAY).description("스킬 빌드 목록"),
                fieldWithPath("data.positions[].skillBuilds[].skillBuild").type(JsonFieldType.STRING).description("스킬 빌드 순서"),

                // startItemBuilds
                fieldWithPath("data.positions[].startItemBuilds[]").type(JsonFieldType.ARRAY).description("시작 아이템 빌드 목록"),
                fieldWithPath("data.positions[].startItemBuilds[].startItems").type(JsonFieldType.ARRAY).description("시작 아이템 ID 배열 (number[])"),

                // bootBuilds (신발)
                fieldWithPath("data.positions[].bootBuilds[]").type(JsonFieldType.ARRAY).description("신발 빌드 목록"),
                fieldWithPath("data.positions[].bootBuilds[].bootId").type(JsonFieldType.NUMBER).description("신발 아이템 ID"),

                // itemBuilds (3코어)
                fieldWithPath("data.positions[].itemBuilds[]").type(JsonFieldType.ARRAY).description("3코어 아이템 빌드 목록"),
                fieldWithPath("data.positions[].itemBuilds[].itemBuild").type(JsonFieldType.ARRAY).description("아이템 ID 배열 (number[], 빌드 순서)")));

        // 빌드류는 모두 같은 통계·신뢰도 필드를 가진다
        for (String build : List.of("runeBuilds", "spellStats", "skillBuilds", "startItemBuilds", "bootBuilds", "itemBuilds")) {
            fields.addAll(buildStatFields("data.positions[]." + build + "[]."));
        }
        return fields;
    }

    private static List<FieldDescriptor> buildStatFields(String prefix) {
        return List.of(
                fieldWithPath(prefix + "games").type(JsonFieldType.NUMBER).description("게임 수"),
                fieldWithPath(prefix + "winRate").type(JsonFieldType.NUMBER).description("승률"),
                fieldWithPath(prefix + "pickRate").type(JsonFieldType.NUMBER).description("픽률"),
                fieldWithPath(prefix + "sampleSize").type(JsonFieldType.NUMBER).description("이 빌드의 표본 크기 (= games)"),
                fieldWithPath(prefix + "totalSampleSize").type(JsonFieldType.NUMBER).description("챔프-라인 총 표본 크기"),
                fieldWithPath(prefix + "confidenceLowerBound").type(JsonFieldType.NUMBER)
                        .description("Wilson 신뢰구간(95%) 하한 — 추천 신뢰도 정렬 기준"));
    }
}
