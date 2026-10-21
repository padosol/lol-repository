package com.mmrtr.lol.infra.bigquery.config;

import com.google.cloud.bigquery.BigQuery;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsMetricsPort;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsQueryPort;
import com.mmrtr.lol.infra.bigquery.adapter.ChampionStatsBigQueryAdapter;
import com.mmrtr.lol.infra.bigquery.adapter.ChampionStatsMetricsAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class BigQueryConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BigQueryConfig.class, ChampionStatsBigQueryAdapter.class, ChampionStatsMetricsAdapter.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @DisplayName("bigquery.* 를 바인딩해 조회·메트릭 어댑터를 등록한다 — 키 위치가 비어도(ADC 없음) 기동한다")
    @Test
    void context_withoutCredentials_starts() {
        contextRunner
                .withPropertyValues(
                        "bigquery.project-id=test-project",
                        "bigquery.dataset=lol_analytics",
                        "bigquery.credentials-location=")
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(ChampionStatsQueryPort.class)
                            .hasSingleBean(ChampionStatsMetricsPort.class);
                    assertThat(context.getBean(BigQuery.class).getOptions().getProjectId()).isEqualTo("test-project");
                });
    }

    @DisplayName("single-flight fallback 은 endpoint 태그를 단 카운터로 센다")
    @Test
    void metricsAdapter_countsFallbackByEndpoint() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChampionStatsMetricsAdapter adapter = new ChampionStatsMetricsAdapter(registry);

        // when
        adapter.recordSingleFlightFallback(ChampionStatsMetricsPort.ENDPOINT_DETAIL);
        adapter.recordSingleFlightFallback(ChampionStatsMetricsPort.ENDPOINT_DETAIL);

        // then
        assertThat(registry.get("lol.bq.single_flight.fallback").tag("endpoint", "detail").counter().count())
                .isEqualTo(2.0);
    }
}
