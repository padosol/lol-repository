package com.mmrtr.lol.controller.spectator;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.spectator.application.usecase.ActiveGameQueryUseCase;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel.Banned;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel.Participant;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel.Perks;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * lol-server {@code SpectatorControllerTest} 의 응답 필드 정의를 그대로 옮겨 새 경로의 응답이 lol-server 와 같은지 검증한다.
 * 캐시·Riot 조회 규칙은 {@code ActiveGameQueryServiceTest} 가 검증한다.
 */
class ActiveGameControllerTest extends RestDocsSupport {

    private static final String PUUID = "test-puuid-12345";

    private final ActiveGameQueryUseCase activeGameQueryUseCase = mock(ActiveGameQueryUseCase.class);

    @Override
    protected Object initController() {
        return new ActiveGameController(activeGameQueryUseCase);
    }

    @DisplayName("GET /api/v1/summoners/{puuid}/active-game — 게임 중이면 lol-server 와 같은 게임 응답 (isBot 키 포함)")
    @Test
    void getActiveGame_inGame_returnsLolServerShape() throws Exception {
        // given
        Perks perks = new Perks(8100L, 8300L, List.of(8112L, 8126L, 8138L, 8135L, 8233L, 8237L));
        ActiveGameReadModel game = new ActiveGameReadModel(12345L, "MATCHED_GAME", "CLASSIC", 11L, 420L,
                1706400000000L, 600L, "KR", "encryptionKey123",
                List.of(new Participant("Player1#KR1", "puuid1", 1L, 100L, 4L, 14L, 1L, false, perks),
                        new Participant("Player2#KR1", "puuid2", 2L, 200L, 7L, 11L, 2L, false, perks)),
                List.of(new Banned(157L, 100L, 1), new Banned(238L, 200L, 2)));
        given(activeGameQueryUseCase.getActiveGame(PUUID)).willReturn(Optional.of(game));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/active-game", PUUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participants[0].isBot").value(false))
                .andDo(document("spectator-active-game",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("소환사 PUUID")),
                        responseFields(
                                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS, FAIL)"),
                                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)"),
                                fieldWithPath("data.gameId").type(JsonFieldType.NUMBER).description("게임 ID"),
                                fieldWithPath("data.gameType").type(JsonFieldType.STRING).description("게임 타입 (e.g., MATCHED_GAME)"),
                                fieldWithPath("data.gameMode").type(JsonFieldType.STRING).description("게임 모드 (e.g., CLASSIC)"),
                                fieldWithPath("data.mapId").type(JsonFieldType.NUMBER).description("맵 ID"),
                                fieldWithPath("data.gameQueueConfigId").type(JsonFieldType.NUMBER)
                                        .description("게임 큐 설정 ID (e.g., 420: 솔로 랭크, 430: 일반)"),
                                fieldWithPath("data.gameStartTime").type(JsonFieldType.NUMBER).description("게임 시작 시간 (Unix Timestamp)"),
                                fieldWithPath("data.gameLength").type(JsonFieldType.NUMBER).description("게임 진행 시간 (초)"),
                                fieldWithPath("data.platformId").type(JsonFieldType.STRING).description("플랫폼 ID (e.g., KR)"),
                                fieldWithPath("data.encryptionKey").type(JsonFieldType.STRING).description("관전 암호화 키"),
                                fieldWithPath("data.participants[]").type(JsonFieldType.ARRAY).description("참여자 목록"),
                                fieldWithPath("data.participants[].riotId").type(JsonFieldType.STRING).description("Riot ID (gameName#tagLine)"),
                                fieldWithPath("data.participants[].puuid").type(JsonFieldType.STRING).description("소환사 PUUID"),
                                fieldWithPath("data.participants[].championId").type(JsonFieldType.NUMBER).description("챔피언 ID"),
                                fieldWithPath("data.participants[].teamId").type(JsonFieldType.NUMBER)
                                        .description("팀 ID (100: 블루팀, 200: 레드팀)"),
                                fieldWithPath("data.participants[].spell1Id").type(JsonFieldType.NUMBER).description("소환사 주문 1 ID"),
                                fieldWithPath("data.participants[].spell2Id").type(JsonFieldType.NUMBER).description("소환사 주문 2 ID"),
                                fieldWithPath("data.participants[].profileIconId").type(JsonFieldType.NUMBER).description("프로필 아이콘 ID"),
                                fieldWithPath("data.participants[].isBot").type(JsonFieldType.BOOLEAN).description("봇 여부"),
                                fieldWithPath("data.participants[].perks").type(JsonFieldType.OBJECT).description("룬 정보"),
                                fieldWithPath("data.participants[].perks.perkStyle").type(JsonFieldType.NUMBER).description("핵심 룬 스타일 ID"),
                                fieldWithPath("data.participants[].perks.perkSubStyle").type(JsonFieldType.NUMBER).description("보조 룬 스타일 ID"),
                                fieldWithPath("data.participants[].perks.perkIds[]").type(JsonFieldType.ARRAY).description("선택한 모든 룬 ID 목록"),
                                fieldWithPath("data.bannedChampions[]").type(JsonFieldType.ARRAY).description("밴 된 챔피언 목록"),
                                fieldWithPath("data.bannedChampions[].championId").type(JsonFieldType.NUMBER).description("밴 된 챔피언 ID"),
                                fieldWithPath("data.bannedChampions[].teamId").type(JsonFieldType.NUMBER).description("밴 한 팀 ID"),
                                fieldWithPath("data.bannedChampions[].pickTurn").type(JsonFieldType.NUMBER).description("밴 순서"))));
    }

    @DisplayName("게임 중이 아니면 200 + data null (lol-server 와 같다)")
    @Test
    void getActiveGame_notInGame_returnsNullData() throws Exception {
        // given
        given(activeGameQueryUseCase.getActiveGame(PUUID)).willReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/active-game", PUUID))
                .andExpect(status().isOk())
                .andDo(document("spectator-active-game-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("소환사 PUUID")),
                        responseFields(
                                fieldWithPath("result").type(JsonFieldType.STRING).description("API 응답 결과 (SUCCESS, FAIL)"),
                                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지 (정상 응답 시 null)"),
                                fieldWithPath("data").type(JsonFieldType.NULL).description("게임 중이 아닌 경우 null"))));
    }

    @DisplayName("플랫폼을 모르는 소환사는 404 UNKNOWN_SUMMONER_PLATFORM (lol-server 와 같은 메시지)")
    @Test
    void getActiveGame_unknownPlatform_returns404() throws Exception {
        // given
        given(activeGameQueryUseCase.getActiveGame(PUUID))
                .willThrow(new CoreException(ErrorType.UNKNOWN_SUMMONER_PLATFORM));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/active-game", PUUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E404"))
                .andExpect(jsonPath("$.errorMessage.message").value("플랫폼 정보가 없는 소환사입니다."));
    }
}
