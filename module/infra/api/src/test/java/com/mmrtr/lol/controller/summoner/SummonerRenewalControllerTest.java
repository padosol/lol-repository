package com.mmrtr.lol.controller.summoner;

import com.mmrtr.lol.controller.support.RestDocsSupport;
import com.mmrtr.lol.domain.summoner.application.usecase.SummonerRenewalUseCase;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerRenewalReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
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
 * lol-server {@code SummonerControllerTest} 갱신 부분의 응답 필드 정의를 그대로 옮기고,
 * {@code SummonerRouteMappingTest} 의 상태 코드 규칙(접수 202, 거절 200)을 검증한다.
 */
class SummonerRenewalControllerTest extends RestDocsSupport {

    private static final String PUUID = "test-puuid";

    private final SummonerRenewalUseCase summonerRenewalUseCase = mock(SummonerRenewalUseCase.class);

    @Override
    protected Object initController() {
        return new SummonerRenewalController(summonerRenewalUseCase);
    }

    @DisplayName("POST /api/v1/summoners/{puuid}/renewal — 접수되면 202 + SUCCESS")
    @Test
    void requestRenewal_accepted_returns202() throws Exception {
        // given
        given(summonerRenewalUseCase.requestRenewal(PUUID)).willReturn(new SummonerRenewalReadModel(PUUID, "SUCCESS"));

        // when & then
        mockMvc.perform(post("/api/v1/summoners/{puuid}/renewal", PUUID))
                .andExpect(status().isAccepted())
                .andDo(document("summoner-renewal",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("소환사 고유 PUUID")),
                        responseFields(renewalFields("갱신 요청한 소환사 PUUID",
                                "갱신 요청 상태 (SUCCESS: 갱신 진행/시작, FAILED: 쿨다운)"))));
    }

    @DisplayName("쿨다운으로 거절되면 200 + FAILED — 클라이언트는 폴링하지 않는다")
    @Test
    void requestRenewal_rejected_returns200() throws Exception {
        // given
        given(summonerRenewalUseCase.requestRenewal(PUUID)).willReturn(new SummonerRenewalReadModel(PUUID, "FAILED"));

        // when & then
        mockMvc.perform(post("/api/v1/summoners/{puuid}/renewal", PUUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @DisplayName("GET /api/v1/summoners/{puuid}/renewal — 갱신 완료(SUCCESS)")
    @Test
    void getRenewalStatus_completed_returnsSuccess() throws Exception {
        // given
        given(summonerRenewalUseCase.getRenewalStatus(PUUID)).willReturn(new SummonerRenewalReadModel(PUUID, "SUCCESS"));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/renewal", PUUID))
                .andExpect(status().isOk())
                .andDo(document("summoner-renewal-status-completed",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("소환사 고유 PUUID")),
                        responseFields(renewalFields("갱신 유저 PUUID", "갱신 상태"))));
    }

    @DisplayName("GET /api/v1/summoners/{puuid}/renewal — 갱신 중(PROGRESS)")
    @Test
    void getRenewalStatus_inProgress_returnsProgress() throws Exception {
        // given
        given(summonerRenewalUseCase.getRenewalStatus(PUUID)).willReturn(new SummonerRenewalReadModel(PUUID, "PROGRESS"));

        // when & then
        mockMvc.perform(get("/api/v1/summoners/{puuid}/renewal", PUUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROGRESS"))
                .andDo(document("summoner-renewal-status-in-progress",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("puuid").description("소환사 고유 PUUID")),
                        responseFields(renewalFields("갱신 유저 PUUID", "갱신 상태"))));
    }

    private List<FieldDescriptor> renewalFields(String puuidDescription, String statusDescription) {
        return List.of(
                fieldWithPath("result").type(JsonFieldType.STRING).description("API 성공 여부"),
                fieldWithPath("data.puuid").type(JsonFieldType.STRING).description(puuidDescription),
                fieldWithPath("data.status").type(JsonFieldType.STRING).description(statusDescription),
                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 정보 (정상 응답 시 null)"));
    }
}
