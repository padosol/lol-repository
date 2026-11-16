package com.mmrtr.lol.tools.riotmock.web;

import com.mmrtr.lol.tools.riotmock.config.RiotMockProperties;
import com.mmrtr.lol.tools.riotmock.metrics.MockMetrics;
import com.mmrtr.lol.tools.riotmock.ratelimit.LimitSpec;
import com.mmrtr.lol.tools.riotmock.ratelimit.RateLimitEngine;
import com.mmrtr.lol.tools.riotmock.ratelimit.SlidingPeakTracker;
import com.mmrtr.lol.tools.riotmock.world.LabWorld;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/** 웹 계층 테스트 공통: 고정 시계, 지연 끄기, 테스트마다 상태를 yml 기본값으로 되돌린다. */
@SpringBootTest(properties = "riot-mock.latency.enabled=false")
@AutoConfigureMockMvc
@AutoConfigureObservability
@Import(LabWebTestSupport.FixedClockConfig.class)
abstract class LabWebTestSupport {

    static final AtomicLong NOW = new AtomicLong(1_000_000_000_000L);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected RateLimitEngine engine;
    @Autowired
    protected SlidingPeakTracker tracker;
    @Autowired
    protected LabWorld world;
    @Autowired
    protected MockMetrics metrics;
    @Autowired
    private RiotMockProperties props;

    @BeforeEach
    void resetLabState() {
        NOW.set(1_000_000_000_000L);
        engine.reset();
        engine.setAppLimit(props.appLimit());
        engine.setDefaultMethodLimit(props.defaultMethodLimit());
        props.methodLimits().forEach(engine::setMethodLimit);
        tracker.trackWindows(LimitSpec.parseAll(props.appLimit()).stream().map(LimitSpec::windowSeconds).toList());
        world.setEpoch(0);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        LongSupplier fixedLabClock() {
            return NOW::get;
        }
    }
}
