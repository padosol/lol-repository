package com.mmrtr.lol.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LabIsolationGuardTest {

    private MockEnvironment labEnv() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url",
                        "jdbc:postgresql://localhost:5432/lol_lab?currentSchema=riot&tcpKeepAlive=true")
                .withProperty("spring.data.redis.database", "1")
                .withProperty("spring.rabbitmq.virtual-host", "lab")
                .withProperty("riot.api-key", "lab-key")
                .withProperty("riot.mock.base-url", "http://localhost:8099");
        env.setActiveProfiles("lab");
        return env;
    }

    @Test
    @DisplayName("생성_모두맞으면_통과")
    void 생성_모두맞으면_통과() {
        // given
        MockEnvironment env = labEnv();

        // when // then
        assertThatCode(() -> new LabIsolationGuard(env)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("생성_datasource가_lol_lab이_아니면_실패하고_값은_메시지에_없다")
    void 생성_datasource가_lol_lab이_아니면_실패하고_값은_메시지에_없다() {
        // given
        MockEnvironment env = labEnv().withProperty("spring.datasource.url",
                "jdbc:postgresql://secrethost:5432/postgres?currentSchema=riot");

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url")
                .hasMessageNotContaining("secrethost");
    }

    @Test
    @DisplayName("생성_datasource가_lol_lab으로_끝나도_통과")
    void 생성_datasource가_lol_lab으로_끝나도_통과() {
        // given
        MockEnvironment env = labEnv().withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/lol_lab");

        // when // then
        assertThatCode(() -> new LabIsolationGuard(env)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("생성_lol_lab_snapshot_같은_접두사_DB는_실패")
    void 생성_lol_lab_snapshot_같은_접두사_DB는_실패() {
        // given
        MockEnvironment env = labEnv().withProperty("spring.datasource.url",
                "jdbc:postgresql://localhost:5432/lol_lab_snapshot?currentSchema=riot");

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .hasMessageContaining("spring.datasource.url");
    }

    @Test
    @DisplayName("생성_redis_database가_1이_아니면_실패")
    void 생성_redis_database가_1이_아니면_실패() {
        // given
        MockEnvironment env = labEnv().withProperty("spring.data.redis.database", "0");

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .hasMessageContaining("spring.data.redis.database");
    }

    @Test
    @DisplayName("생성_속성이_하나도_없으면_실패")
    void 생성_속성이_하나도_없으면_실패() {
        // given
        MockEnvironment env = new MockEnvironment();

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url")
                .hasMessageContaining("riot.mock.base-url");
    }

    @Test
    @DisplayName("생성_vhost가_lab이_아니면_실패")
    void 생성_vhost가_lab이_아니면_실패() {
        // given
        MockEnvironment env = labEnv().withProperty("spring.rabbitmq.virtual-host", "/");

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .hasMessageContaining("spring.rabbitmq.virtual-host");
    }

    @Test
    @DisplayName("생성_api키가_다르면_실패하고_값은_메시지에_없다")
    void 생성_api키가_다르면_실패하고_값은_메시지에_없다() {
        // given
        MockEnvironment env = labEnv().withProperty("riot.api-key", "RGAPI-test");

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .hasMessageContaining("riot.api-key")
                .hasMessageNotContaining("RGAPI");
    }

    @Test
    @DisplayName("생성_mock_base_url이_비어있으면_실패")
    void 생성_mock_base_url이_비어있으면_실패() {
        // given
        MockEnvironment env = labEnv().withProperty("riot.mock.base-url", "");

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .hasMessageContaining("riot.mock.base-url");
    }

    @Test
    @DisplayName("생성_dev와_lab을_같이_켜면_실패")
    void 생성_dev와_lab을_같이_켜면_실패() {
        // given
        MockEnvironment env = labEnv();
        env.setActiveProfiles("dev", "lab");

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riot.mock.base-url")
                .hasMessageNotContaining("localhost:8099");
    }

    @Test
    @DisplayName("생성_prod와_lab을_같이_켜면_실패")
    void 생성_prod와_lab을_같이_켜면_실패() {
        // given
        MockEnvironment env = labEnv();
        env.setActiveProfiles("prod", "lab");

        // when // then
        assertThatThrownBy(() -> new LabIsolationGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riot.mock.base-url");
    }
}
