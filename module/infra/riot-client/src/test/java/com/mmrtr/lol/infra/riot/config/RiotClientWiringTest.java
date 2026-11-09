package com.mmrtr.lol.infra.riot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mmrtr.lol.infra.riot.ratelimit.RedisRiotRateLimiter;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.infra.riot.service.RiotCallRecorder;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestClient;

/** 앱과 같이 riot-client 패키지를 컴포넌트 스캔했을 때 빈이 하나씩만 잡혀 기동되는지 확인한다. */
class RiotClientWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RiotClientScan.class, Externals.class)
            .withPropertyValues("riot.api-key=test-key");

    @Test
    @DisplayName("riot-client 빈이 모호함 없이 연결되고 RiotAPIProperties 는 하나만 등록된다")
    void contextStarts_withSingleProperties() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RiotAPIProperties.class);
            assertThat(context).hasSingleBean(RedisRiotRateLimiter.class);
            assertThat(context).hasSingleBean(RiotApiService.class);
            assertThat(context).hasSingleBean(RiotCallRecorder.class);
            assertThat(context).hasSingleBean(RiotPermitPort.class);
            assertThat(context).hasBean("riotRestClient");
            assertThat(context.getBean("riotRestClient")).isInstanceOf(RestClient.class);
        });
    }

    @Test
    @DisplayName("mock주소와_prod프로파일이면_기동실패")
    void mock주소와_prod프로파일이면_기동실패() {
        // given
        ApplicationContextRunner prodRunner = runner
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("riot.mock.base-url=http://localhost:1");

        // when // then
        prodRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure())).hasMessageContaining("riot.mock.base-url");
        });
    }

    @Test
    @DisplayName("mock주소와_lab프로파일이면_기동된다")
    void mock주소와_lab프로파일이면_기동된다() {
        // given
        ApplicationContextRunner labRunner = runner
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local", "lab"))
                .withPropertyValues("riot.mock.base-url=http://localhost:1");

        // when // then
        labRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("mock주소와_lab없는_local프로파일이면_기동실패")
    void mock주소와_lab없는_local프로파일이면_기동실패() {
        // given
        ApplicationContextRunner localRunner = runner
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("riot.mock.base-url=http://localhost:1");

        // when // then
        localRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure())).hasMessageContaining("riot.mock.base-url");
        });
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** @Configuration 이 없어 스캔에 다시 잡히지 않는다. */
    @ComponentScan("com.mmrtr.lol.infra.riot")
    static class RiotClientScan {
    }

    static class Externals {

        @Bean
        RedissonClient redissonClient() {
            return mock(RedissonClient.class);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
