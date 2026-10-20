package com.mmrtr.lol.controller.summoner;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.summoner.application.usecase.SummonerQueryUseCase;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerAutocompleteReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel.LeagueEntry;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
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
 * lol-server {@code SummonerControllerTest}·{@code LeagueControllerTest} 의 응답 필드 정의를 그대로 옮겨
 * 새 경로의 응답이 lol-server 와 같은지 검증한다. responseFields 는 문서화되지 않은 필드가 있거나 빠지면 실패한다.
 * 조회 규칙(정규화·Riot 조회·자동완성 상한)은 {@code SummonerQueryServiceTest} 가 검증한다.
 */
class SummonerQueryControllerTest extends RestDocsSupport {

    private static final SummonerReadModel SUMMONER = new SummonerReadModel(1234, "test-puuid", 500L,
            "hide on bush", "KR1", null, "2026-03-01 12:30:05", "2026-03-02 08:00:00");

    private final SummonerQueryUseCase summonerQueryUseCase = mock(SummonerQueryUseCase.class);

    @Override
    protected Object initController() {
        return new SummonerQueryController(summonerQueryUseCase);
    }

    @DisplayName("GET /api/v1/summoners/by-riot-id/{gameName} — lol-server 와 같은 소환사 응답")
    @Test
    void getSummonerByRiotId_validRequest_returnsLolServerShape() throws Exception {
        // given
        given(summonerQueryUseCase.getSummonerByRiotId("hide on bush-KR1", "kr")).willReturn(SUMMONER);

        // when & then
        mockMvc.perform(get("/api/v1/summoners/by-riot-id/{gameName}", "hide on bush-KR1").param("platform", "kr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastRevisionDateTime").value("2026-03-01 12:30:05"))
                .andDo(document("summoner-detail",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("gameName").description("게임 유저명 (gameName-tagLine)")),
                        queryParameters(
                                parameterWithName("platform").description("플랫폼 ID (예: kr)")),
                        responseFields(summonerFields())));
    }

    @DisplayName("GET /api/v1/summoners/{puuid} — lol-server 와 같은 소환사 응답")
    @Test
    void getSummonerByPuuid_storedSummoner_returnsLolServerShape() throws Exception {
        // given
        given(summonerQueryUseCase.getSummonerByPuuid("test-puuid")).willReturn(SUMMONER);

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}", "test-puuid"))
                .andExpect(status().isOk())
                .andDo(document("summoner-detail-by-puuid",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("소환사 고유 PUUID")),
                        responseFields(summonerFields())));
    }

    @DisplayName("GET /api/v1/summoners/autocomplete — {puuid} 가 아니라 자동완성으로 간다")
    @Test
    void autocomplete_validRequest_returnsLolServerShape() throws Exception {
        // given
        given(summonerQueryUseCase.autocomplete("hide on bush", "kr")).willReturn(List.of(
                new SummonerAutocompleteReadModel("testUser1", "KR1", 123, 100L, "GOLD", "I", 50),
                new SummonerAutocompleteReadModel("testUser2", "KR1", 456, 120L, "SILVER", "II", 25)));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/autocomplete").param("q", "hide on bush").param("platform", "kr"))
                .andExpect(status().isOk())
                .andDo(document("summoner-autocomplete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("q").description("자동완성 검색어"),
                                parameterWithName("platform").description("플랫폼 ID (예: kr)")),
                        responseFields(
                                fieldWithPath("result").type(JsonFieldType.STRING).description("API 성공 여부"),
                                fieldWithPath("data[].gameName").type(JsonFieldType.STRING).description("게임 유저명"),
                                fieldWithPath("data[].tagLine").type(JsonFieldType.STRING).description("태그 라인"),
                                fieldWithPath("data[].profileIconId").type(JsonFieldType.NUMBER).description("프로필 아이콘 ID"),
                                fieldWithPath("data[].summonerLevel").type(JsonFieldType.NUMBER).description("소환사 레벨"),
                                fieldWithPath("data[].tier").type(JsonFieldType.STRING).description("티어"),
                                fieldWithPath("data[].rank").type(JsonFieldType.STRING).description("랭크"),
                                fieldWithPath("data[].leaguePoints").type(JsonFieldType.NUMBER).description("LP"),
                                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 정보 (정상 응답 시 null)"))));
        verify(summonerQueryUseCase).autocomplete("hide on bush", "kr");
    }

    @DisplayName("GET /api/v1/summoners/{puuid}/leagues — lol-server 와 같은 리그 응답")
    @Test
    void getLeagues_storedLeagues_returnsLolServerShape() throws Exception {
        // given
        LeagueEntry solo = new LeagueEntry("RANKED_SOLO_5x5", 1500, 300, 200, "60.00", "CHALLENGER", "I");
        LeagueEntry flex = new LeagueEntry("RANKED_FLEX_SR", 10, 5, 5, "50.00", "PLATINUM", "IV");
        given(summonerQueryUseCase.getLeagues("test-puuid")).willReturn(new SummonerLeagueReadModel(solo, flex,
                List.of(new LeagueEntry("RANKED_SOLO_5x5", 1450, 290, 200, "0.59", "CHALLENGER", "I")),
                List.of(new LeagueEntry("RANKED_FLEX_SR", 5, 4, 5, "0.44", "PLATINUM", "IV"))));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/leagues", "test-puuid"))
                .andExpect(status().isOk())
                .andDo(document("league-get-by-puuid",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("조회할 소환사의 PUUID")),
                        responseFields(leagueFields())));
    }

    @DisplayName("저장된 랭크가 없으면 200 + null·빈 목록 (lol-server 와 같다)")
    @Test
    void getLeagues_noLeagues_returnsEmptyShape() throws Exception {
        // given
        given(summonerQueryUseCase.getLeagues(anyString()))
                .willReturn(new SummonerLeagueReadModel(null, null, List.of(), List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/leagues", "ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.soloLeague").isEmpty())
                .andExpect(jsonPath("$.data.soloLeagueHistory").isArray());
    }

    @DisplayName("저장되지 않은 puuid 는 404 NOT_FOUND_PUUID (lol-server 와 같은 에러 본문)")
    @Test
    void getSummonerByPuuid_unknownPuuid_returns404() throws Exception {
        // given
        given(summonerQueryUseCase.getSummonerByPuuid("ghost")).willThrow(new CoreException(ErrorType.NOT_FOUND_PUUID));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}", "ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E404"))
                .andExpect(jsonPath("$.errorMessage.message").value("존재하지 않는 PUUID 입니다."));
    }

    @DisplayName("platform 누락은 400 INVALID_INPUT, 같은 소환사 조회 중이면 503")
    @Test
    void getSummonerByRiotId_missingPlatformOrLocked_returnsErrorStatus() throws Exception {
        // given
        given(summonerQueryUseCase.getSummonerByRiotId("hide on bush-KR1", "kr"))
                .willThrow(new CoreException(ErrorType.LOCK_ACQUISITION_FAILED));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/by-riot-id/{gameName}", "hide on bush-KR1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"));
        mockMvc.perform(get("/api/v1/summoners/by-riot-id/{gameName}", "hide on bush-KR1").param("platform", "kr"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E503"))
                .andExpect(jsonPath("$.errorMessage.message").value("잠시 후 다시 시도해주세요."));
    }

    private List<FieldDescriptor> summonerFields() {
        return List.of(
                fieldWithPath("result").type(JsonFieldType.STRING).description("API 성공 여부"),
                fieldWithPath("data.profileIconId").type(JsonFieldType.NUMBER).description("프로필 아이콘 ID"),
                fieldWithPath("data.puuid").type(JsonFieldType.STRING).description("소환사 고유 PUUID"),
                fieldWithPath("data.summonerLevel").type(JsonFieldType.NUMBER).description("소환사 레벨"),
                fieldWithPath("data.gameName").type(JsonFieldType.STRING).description("게임 유저명"),
                fieldWithPath("data.tagLine").type(JsonFieldType.STRING).description("태그 라인"),
                fieldWithPath("data.platform").type(JsonFieldType.STRING).description("플랫폼(지역) — 항상 null").optional(),
                // lol-server 문서는 ISO-8601 로 적었지만 실제 형식은 yyyy-MM-dd HH:mm:ss 다
                fieldWithPath("data.lastRevisionDateTime").type(JsonFieldType.STRING)
                        .description("Riot API 마지막 갱신 시간 (yyyy-MM-dd HH:mm:ss)"),
                fieldWithPath("data.lastRevisionClickDateTime").type(JsonFieldType.STRING)
                        .description("유저가 갱신 버튼 누른 시간 (yyyy-MM-dd HH:mm:ss)"),
                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 정보 (정상 응답 시 null)"));
    }

    private List<FieldDescriptor> leagueFields() {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("result").type(JsonFieldType.STRING).description("API 성공 여부"),
                fieldWithPath("data").type(JsonFieldType.OBJECT).description("응답 데이터")));
        fields.addAll(leagueEntryFields("data.soloLeague", JsonFieldType.OBJECT, "솔로 랭크 리그 정보", "RANKED_SOLO_5x5"));
        fields.addAll(leagueEntryFields("data.flexLeague", JsonFieldType.OBJECT, "자유 랭크 리그 정보", "RANKED_FLEX_SR"));
        fields.addAll(leagueEntryFields("data.soloLeagueHistory[]", JsonFieldType.ARRAY, "솔로 랭크 리그 기록",
                "RANKED_SOLO_5x5"));
        fields.addAll(leagueEntryFields("data.flexLeagueHistory[]", JsonFieldType.ARRAY, "자유 랭크 리그 기록",
                "RANKED_FLEX_SR"));
        fields.addAll(List.of(
                fieldWithPath("errorMessage").type(JsonFieldType.OBJECT).description("에러 정보 (정상 응답 시 null)").optional(),
                fieldWithPath("errorMessage.errorCode").type(JsonFieldType.STRING).description("에러 코드").optional(),
                fieldWithPath("errorMessage.message").type(JsonFieldType.STRING).description("에러 메시지").optional(),
                fieldWithPath("errorMessage.timestamp").type(JsonFieldType.STRING).description("에러 발생 시각").optional()));
        return fields;
    }

    private List<FieldDescriptor> leagueEntryFields(String path, JsonFieldType type, String description,
                                                    String leagueType) {
        return List.of(
                fieldWithPath(path).type(type).description(description).optional(),
                fieldWithPath(path + ".leagueType").type(JsonFieldType.STRING)
                        .description("리그 타입 (e.g., " + leagueType + ")"),
                fieldWithPath(path + ".leaguePoints").type(JsonFieldType.NUMBER).description("리그 포인트 (LP)"),
                fieldWithPath(path + ".wins").type(JsonFieldType.NUMBER).description("승리 수"),
                fieldWithPath(path + ".losses").type(JsonFieldType.NUMBER).description("패배 수"),
                fieldWithPath(path + ".oow").type(JsonFieldType.STRING).description("승률"),
                fieldWithPath(path + ".tier").type(JsonFieldType.STRING).description("티어 (e.g., CHALLENGER)"),
                fieldWithPath(path + ".rank").type(JsonFieldType.STRING).description("랭크 (e.g., I)"));
    }
}
