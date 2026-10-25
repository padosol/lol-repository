package com.mmrtr.lol.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiPathFilterTest {

    private static final int PUBLIC_PORT = 8080;
    private static final int INTERNAL_PORT = 8081;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final PublicApiPathFilter filter = new PublicApiPathFilter(
            new WebPortProperties(INTERNAL_PORT, List.of("/api/riot", "/api/admin", "/actuator")),
            objectMapper);

    @DisplayName("공개 포트: 소유 prefix 는 통과")
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/summoners/abc",
            "/api/v1/matches/KR_1",
            "/api/v1/rankings",
            "/api/v1/champion-stats/1",
            "/api/v1/game-data/versions/latest"
    })
    void publicPortAllowsOwnedPrefixes(String path) throws Exception {
        assertThat(pass(PUBLIC_PORT, path)).isTrue();
    }

    @DisplayName("공개 포트: 이행용 허용 prefix 는 통과")
    @ParameterizedTest
    @ValueSource(strings = {"/api/riot/kr/summoners/x", "/api/admin/ranking/calculate", "/actuator/health/readiness"})
    void publicPortAllowsTransitionalPrefixes(String path) throws Exception {
        assertThat(pass(PUBLIC_PORT, path)).isTrue();
    }

    @DisplayName("공개 포트: 소유 prefix 밖·/internal 은 404")
    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/backfill/x/runs",
            "/internal",
            "/api/v1/members/me",
            "/api/v1/game-datas",
            "/api/community/posts",
            "/"
    })
    void publicPortRejectsOthers(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(PUBLIC_PORT, path, path), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString())
                .contains("\"result\":\"ERROR\"")
                .contains("\"errorCode\":\"E404\"");
    }

    @DisplayName("공개 포트: 원본 URI 가 정규화 후 /internal 을 가리키면 404")
    @Test
    void publicPortRejectsTraversalToInternal() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(PUBLIC_PORT, "/internal/backfill/x/runs",
                "/api/v1/game-data/%2e%2e/%2e%2e/%2e%2e/internal/backfill/x/runs"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @DisplayName("내부 포트: /internal 만 통과")
    @Test
    void internalPortAllowsOnlyInternal() throws Exception {
        assertThat(pass(INTERNAL_PORT, "/internal/backfill/x/runs")).isTrue();
        assertThat(pass(INTERNAL_PORT, "/api/v1/summoners/abc")).isFalse();
        assertThat(pass(INTERNAL_PORT, "/api/riot/kr/summoners/x")).isFalse();
    }

    @DisplayName("내부 포트 비활성(0 이하)이면 모든 요청을 공개 포트 규칙으로 판정")
    @Test
    void internalPortDisabled() throws Exception {
        PublicApiPathFilter disabled = new PublicApiPathFilter(new WebPortProperties(0, List.of()), objectMapper);
        MockFilterChain chain = new MockFilterChain();

        disabled.doFilter(request(0, "/internal/backfill/x/runs", "/internal/backfill/x/runs"),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
    }

    private boolean pass(int port, String path) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(port, path, path), new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }

    private MockHttpServletRequest request(int port, String servletPath, String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setServletPath(servletPath);
        request.setLocalPort(port);
        return request;
    }
}
