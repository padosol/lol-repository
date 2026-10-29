package com.mmrtr.lol.backfill.incremental;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsExportPropertiesTest {

    private static final String[] VALID = {
            "lol.stats-export.patch-count=1",
            "lol.stats-export.queue-ids=420",
            "lol.stats-export.chunk.size=50000",
            "lol.stats-export.chunk.parallelism=2",
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("유효한 설정은 바인딩되고 min-observe-age 는 생략하면 15분이다")
    void bind_valid_usesDefaultMinObserveAge() {
        runner.withPropertyValues(VALID).run(ctx -> {
            // then
            assertThat(ctx).hasNotFailed();
            StatsExportProperties props = ctx.getBean(StatsExportProperties.class);
            assertThat(props.minObserveAge()).isEqualTo(Duration.ofMinutes(15));
            assertThat(props.queueIds()).isEqualTo(List.of(420));
        });
    }

    @Test
    @DisplayName("min-observe-age 를 지정하면 그 값을 쓴다")
    void bind_minObserveAge_overridesDefault() {
        runner.withPropertyValues(VALID).withPropertyValues("lol.stats-export.min-observe-age=30m").run(ctx ->
                assertThat(ctx.getBean(StatsExportProperties.class).minObserveAge()).isEqualTo(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("큐 목록이 없으면 기동이 실패한다")
    void bind_missingQueueIds_fails() {
        runner.withPropertyValues("lol.stats-export.patch-count=1", "lol.stats-export.chunk.size=50000",
                "lol.stats-export.chunk.parallelism=2").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("patch-count 0, chunk.size 0, parallelism 0·5 는 기동이 실패한다")
    void bind_outOfRangeNumbers_fail() {
        runner.withPropertyValues(VALID).withPropertyValues("lol.stats-export.patch-count=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues(VALID).withPropertyValues("lol.stats-export.chunk.size=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues(VALID).withPropertyValues("lol.stats-export.chunk.parallelism=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues(VALID).withPropertyValues("lol.stats-export.chunk.parallelism=5")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("chunk 설정이 없으면 기동이 실패한다")
    void bind_missingChunk_fails() {
        runner.withPropertyValues("lol.stats-export.patch-count=1", "lol.stats-export.queue-ids=420")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StatsExportProperties.class)
    static class PropertiesConfig {
    }
}
