package com.mmrtr.lol.controller.internal;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.summoner.application.usecase.DuoProfileQueryUseCase;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.MostChampion;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.PlayedChampion;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RecentGameSummary;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.TierInfo;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * duo 내부 API 응답이 lol-server duo {@code RiotAccountStats}(tierInfo·mostChampions·recentGameSummary) 와
 * 같은 필드 구성인지 검증한다. responseFields 는 문서화되지 않은 필드가 있거나 빠지면 실패한다.
 */
class DuoProfileInternalControllerTest extends RestDocsSupport {

    private final DuoProfileQueryUseCase duoProfileQueryUseCase = mock(DuoProfileQueryUseCase.class);

    @Override
    protected Object initController() {
        return new DuoProfileInternalController(duoProfileQueryUseCase);
    }

    @DisplayName("GET /internal/v1/summoners/{puuid}/duo-profile — RiotAccountStats 형태로 응답한다")
    @Test
    void getDuoProfile_indexedSummoner_returnsRiotAccountStatsShape() throws Exception {
        // given
        given(duoProfileQueryUseCase.getDuoProfile("puuid-1")).willReturn(new DuoProfileReadModel(
                new TierInfo("GOLD", "II", 45),
                List.of(new MostChampion(103, "Ahri", 12, 8, 4)),
                new RecentGameSummary(1, 1, List.of(new PlayedChampion(103, "Ahri"), new PlayedChampion(238, "Zed")))));

        // when & then
        mockMvc.perform(get("/internal/v1/summoners/{puuid}/duo-profile", "puuid-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tierInfo.tier").value("GOLD"))
                .andExpect(jsonPath("$.mostChampions[0].wins").value(8))
                .andExpect(jsonPath("$.recentGameSummary.playedChampions[1].championName").value("Zed"))
                .andDo(document("internal-duo-profile",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("puuid").description("소환사 PUUID")),
                        responseFields(
                                fieldWithPath("tierInfo").type(JsonFieldType.OBJECT).description("솔로 랭크 티어"),
                                fieldWithPath("tierInfo.tier").type(JsonFieldType.STRING).description("티어 (기록 없으면 UNRANKED)"),
                                fieldWithPath("tierInfo.rank").type(JsonFieldType.STRING).description("랭크 (기록 없으면 I)"),
                                fieldWithPath("tierInfo.leaguePoints").type(JsonFieldType.NUMBER).description("LP (기록 없으면 0)"),
                                fieldWithPath("mostChampions").type(JsonFieldType.ARRAY).description("솔로 랭크 판 수 상위 3개"),
                                fieldWithPath("mostChampions[].championId").type(JsonFieldType.NUMBER).description("챔피언 ID"),
                                fieldWithPath("mostChampions[].championName").type(JsonFieldType.STRING).description("챔피언 이름"),
                                fieldWithPath("mostChampions[].playCount").type(JsonFieldType.NUMBER).description("판 수"),
                                fieldWithPath("mostChampions[].wins").type(JsonFieldType.NUMBER).description("승"),
                                fieldWithPath("mostChampions[].losses").type(JsonFieldType.NUMBER).description("패"),
                                fieldWithPath("recentGameSummary").type(JsonFieldType.OBJECT).description("최근 솔로 랭크 20게임"),
                                fieldWithPath("recentGameSummary.wins").type(JsonFieldType.NUMBER).description("승"),
                                fieldWithPath("recentGameSummary.losses").type(JsonFieldType.NUMBER).description("패"),
                                fieldWithPath("recentGameSummary.playedChampions").type(JsonFieldType.ARRAY).description("플레이한 챔피언, 최신 게임부터"),
                                fieldWithPath("recentGameSummary.playedChampions[].championId").type(JsonFieldType.NUMBER).description("챔피언 ID"),
                                fieldWithPath("recentGameSummary.playedChampions[].championName").type(JsonFieldType.STRING).description("챔피언 이름")
                        )));
    }

    @DisplayName("색인 안 된 소환사는 404 + 에러 코드·메시지")
    @Test
    void getDuoProfile_notIndexedSummoner_returns404ErrorMessage() throws Exception {
        // given
        given(duoProfileQueryUseCase.getDuoProfile("ghost")).willThrow(new CoreException(ErrorType.NOT_FOUND_PUUID));

        // when & then
        mockMvc.perform(get("/internal/v1/summoners/{puuid}/duo-profile", "ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("E404"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 PUUID 입니다."));
    }
}
