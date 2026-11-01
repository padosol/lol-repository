package com.mmrtr.lol.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 가드가 실제 컨텍스트에서 BFPP 로 인스턴스화되는지(생성자 주입 문제) 확인한다. */
class LabIsolationConfigTest {

    private static final String LAB_URL =
            "jdbc:postgresql://localhost:5432/lol_lab?currentSchema=riot&tcpKeepAlive=true";

    private ApplicationContextRunner labRunner(String datasourceUrl) {
        return new ApplicationContextRunner()
                .withUserConfiguration(LabIsolationConfig.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("lab"))
                .withPropertyValues(
                        "spring.datasource.url=" + datasourceUrl,
                        "spring.data.redis.database=1",
                        "spring.rabbitmq.virtual-host=lab",
                        "riot.api-key=lab-key",
                        "riot.mock.base-url=http://localhost:8099");
    }

    @Test
    @DisplayName("컨텍스트_lab값이_맞으면_기동된다")
    void 컨텍스트_lab값이_맞으면_기동된다() {
        // given
        ApplicationContextRunner runner = labRunner(LAB_URL);

        // when // then
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("labIsolationGuard");
        });
    }

    @Test
    @DisplayName("컨텍스트_datasource가_틀리면_기동실패하고_값은_메시지에_없다")
    void 컨텍스트_datasource가_틀리면_기동실패하고_값은_메시지에_없다() {
        // given
        ApplicationContextRunner runner = labRunner("jdbc:postgresql://secrethost:5432/postgres");

        // when // then
        runner.run(context -> {
            assertThat(context).hasFailed();
            Throwable cause = context.getStartupFailure();
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(cause).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("spring.datasource.url")
                    .hasMessageNotContaining("secrethost");
        });
    }

    @Test
    @DisplayName("컨텍스트_lab프로파일이_아니면_가드_빈이_없다")
    void 컨텍스트_lab프로파일이_아니면_가드_빈이_없다() {
        // given
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(LabIsolationConfig.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"));

        // when // then
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("labIsolationGuard");
        });
    }
}
