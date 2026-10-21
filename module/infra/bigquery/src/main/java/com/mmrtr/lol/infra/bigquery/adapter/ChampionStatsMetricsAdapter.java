package com.mmrtr.lol.infra.bigquery.adapter;

import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** lol-server 와 같은 카운터 이름. 두 서비스는 Prometheus 수집 라벨(job·pod)로 갈린다. */
@Component
@RequiredArgsConstructor
public class ChampionStatsMetricsAdapter implements ChampionStatsMetricsPort {

    static final String COUNTER_NAME = "lol.bq.single_flight.fallback";

    private final MeterRegistry meterRegistry;

    @Override
    public void recordSingleFlightFallback(String endpoint) {
        Counter.builder(COUNTER_NAME)
                .tag("endpoint", endpoint)
                .register(meterRegistry)
                .increment();
    }
}
