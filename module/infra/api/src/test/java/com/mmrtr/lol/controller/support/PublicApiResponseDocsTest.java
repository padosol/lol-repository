package com.mmrtr.lol.controller.support;

import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 API 공통 응답 형식(lol-server {@code ApiResponse<T>}·{@code CoreExceptionAdvice} 와 동일)을 검증·문서화한다.
 */
class PublicApiResponseDocsTest extends RestDocsSupport {

    private static final FieldDescriptor[] ERROR_FIELDS = {
            fieldWithPath("result").type(JsonFieldType.STRING).description("결과 타입 (ERROR)"),
            fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 (에러 시 null)"),
            fieldWithPath("errorMessage").type(JsonFieldType.OBJECT).description("에러 정보"),
            fieldWithPath("errorMessage.errorCode").type(JsonFieldType.STRING).description("에러 코드 (E400, E404, E500 …)"),
            fieldWithPath("errorMessage.message").type(JsonFieldType.STRING).description("에러 메시지"),
            fieldWithPath("errorMessage.timestamp").type(JsonFieldType.STRING).description("발생 시각"),
    };

    @Override
    protected Object initController() {
        return new StubPublicController();
    }

    @DisplayName("성공 응답은 result=SUCCESS, data, errorMessage=null 이다")
    @Test
    void success() throws Exception {
        mockMvc.perform(get("/api/v1/game-data/stub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.value").value("ok"))
                .andExpect(jsonPath("$.errorMessage").isEmpty())
                .andDo(document("common-success-response",
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("result").type(JsonFieldType.STRING).description("결과 타입 (SUCCESS)"),
                                subsectionWithPath("data").type(JsonFieldType.OBJECT).description("응답 데이터"),
                                fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 정보 (성공 시 null)")
                        )));
    }

    @DisplayName("CoreException 은 ErrorType 의 상태·코드·메시지로 응답한다")
    @Test
    void coreException() throws Exception {
        mockMvc.perform(get("/api/v1/game-data/stub/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E404"))
                .andExpect(jsonPath("$.errorMessage.message").value("존재하지 않는 패치노트 입니다."))
                .andDo(document("common-error-response",
                        preprocessResponse(prettyPrint()),
                        responseFields(ERROR_FIELDS)));
    }

    @DisplayName("필수 query 누락은 400 INVALID_INPUT")
    @Test
    void missingRequiredParameter() throws Exception {
        mockMvc.perform(get("/api/v1/game-data/stub/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E400"))
                .andExpect(jsonPath("$.errorMessage.message").value(ErrorType.INVALID_INPUT.getMessage()));
    }

    @DisplayName("처리되지 않은 예외는 500 DEFAULT_ERROR")
    @Test
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/api/v1/game-data/stub/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage.errorCode").value("E500"));
    }

    @PublicApi
    @RestController
    @RequestMapping("/api/v1/game-data/stub")
    static class StubPublicController {

        @GetMapping
        public ApiResponse<Map<String, String>> success() {
            return ApiResponse.success(Map.of("value", "ok"));
        }

        @GetMapping("/error")
        public ApiResponse<Void> error() {
            throw new CoreException(ErrorType.NOT_FOUND_PATCH_NOTE);
        }

        @GetMapping("/required")
        public ApiResponse<String> required(@RequestParam("platform") String platform) {
            return ApiResponse.success(platform);
        }

        @GetMapping("/unexpected")
        public ApiResponse<Void> unexpected() {
            throw new IllegalStateException("boom");
        }
    }
}
