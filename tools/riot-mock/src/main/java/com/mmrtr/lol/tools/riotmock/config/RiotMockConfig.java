package com.mmrtr.lol.tools.riotmock.config;

import com.mmrtr.lol.tools.riotmock.ratelimit.LimitSpec;
import com.mmrtr.lol.tools.riotmock.ratelimit.RateLimitEngine;
import com.mmrtr.lol.tools.riotmock.ratelimit.SlidingPeakTracker;
import com.mmrtr.lol.tools.riotmock.world.LabWorld;
import java.util.function.LongSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RiotMockConfig {

    /** 시작 때 벽시계 시각을 한 번 잡고 nanoTime 경과만 더한다. 시계가 뒤로 가지 않아야 슬라이딩 피크 계산이 맞다. */
    @Bean
    public LongSupplier riotMockClock() {
        long baseMillis = System.currentTimeMillis();
        long baseNanos = System.nanoTime();
        return () -> baseMillis + (System.nanoTime() - baseNanos) / 1_000_000;
    }

    @Bean
    public RateLimitEngine rateLimitEngine(RiotMockProperties props, LongSupplier riotMockClock) {
        return new RateLimitEngine(props.appLimit(), props.methodLimits(), props.defaultMethodLimit(),
                props.countRejected(), riotMockClock);
    }

    @Bean
    public SlidingPeakTracker slidingPeakTracker(RiotMockProperties props, LongSupplier riotMockClock) {
        SlidingPeakTracker tracker = new SlidingPeakTracker(riotMockClock);
        tracker.trackWindows(LimitSpec.parseAll(props.appLimit()).stream().map(LimitSpec::windowSeconds).toList());
        return tracker;
    }

    @Bean
    public LabWorld labWorld(RiotMockProperties props) {
        return new LabWorld(props.world());
    }
}
