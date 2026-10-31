package com.mmrtr.lol.infra.riot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mmrtr.lol.infra.riot.ratelimit.RedisRiotRateLimiter;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
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
            assertThat(context).hasBean("riotRestClient");
            assertThat(context.getBean("riotRestClient")).isInstanceOf(RestClient.class);
        });
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
