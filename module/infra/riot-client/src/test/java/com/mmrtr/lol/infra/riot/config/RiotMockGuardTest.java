package com.mmrtr.lol.infra.riot.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiotMockGuardTest {

    private static final String MOCK = "http://localhost:8099";

    @Test
    @DisplayName("assertAllowed_prod면_실패")
    void assertAllowed_prod면_실패() {
        assertThatThrownBy(() -> RiotMockGuard.assertAllowed(MOCK, new String[] {"prod"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riot.mock.base-url");
    }

    @Test
    @DisplayName("assertAllowed_dev면_실패")
    void assertAllowed_dev면_실패() {
        assertThatThrownBy(() -> RiotMockGuard.assertAllowed(MOCK, new String[] {"local", "dev"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riot.mock.base-url");
    }

    @Test
    @DisplayName("assertAllowed_dev_lab이어도_실패")
    void assertAllowed_dev_lab이어도_실패() {
        assertThatThrownBy(() -> RiotMockGuard.assertAllowed(MOCK, new String[] {"dev", "lab"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riot.mock.base-url");
    }

    @Test
    @DisplayName("assertAllowed_local만이면_실패")
    void assertAllowed_local만이면_실패() {
        assertThatThrownBy(() -> RiotMockGuard.assertAllowed(MOCK, new String[] {"local"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riot.mock.base-url");
    }

    @Test
    @DisplayName("assertAllowed_프로파일이_없으면_실패")
    void assertAllowed_프로파일이_없으면_실패() {
        assertThatThrownBy(() -> RiotMockGuard.assertAllowed(MOCK, new String[] {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riot.mock.base-url");
    }

    @Test
    @DisplayName("assertAllowed_local_lab이면_통과")
    void assertAllowed_local_lab이면_통과() {
        assertThatCode(() -> RiotMockGuard.assertAllowed(MOCK, new String[] {"local", "lab"}))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertAllowed_baseUrl이_없으면_프로파일과_상관없이_통과")
    void assertAllowed_baseUrl이_없으면_프로파일과_상관없이_통과() {
        assertThatCode(() -> RiotMockGuard.assertAllowed(null, new String[] {"prod"})).doesNotThrowAnyException();
        assertThatCode(() -> RiotMockGuard.assertAllowed("  ", new String[] {"dev"})).doesNotThrowAnyException();
    }
}
