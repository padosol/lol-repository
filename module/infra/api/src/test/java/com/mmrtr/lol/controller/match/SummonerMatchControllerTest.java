package com.mmrtr.lol.controller.match;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.match.application.usecase.MatchQueryUseCase;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.page.SliceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

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
 * lol-server {@code MatchControllerTest} 의 {@code match-summoner-get-list}·{@code match-get-ids} 응답 필드 정의를
 * 그대로 옮겨 응답이 lol-server 와 같은지 검증한다. page 설명만 실제 동작(0부터)에 맞췄다.
 * 오버레이·필터 규칙은 {@code MatchQueryServiceTest} 가 검증한다.
 */
class SummonerMatchControllerTest extends RestDocsSupport {

    private final MatchQueryUseCase matchQueryUseCase = mock(MatchQueryUseCase.class);

    @Override
    protected Object initController() {
        return new SummonerMatchController(matchQueryUseCase);
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
