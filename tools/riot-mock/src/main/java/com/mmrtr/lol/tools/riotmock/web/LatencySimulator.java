package com.mmrtr.lol.tools.riotmock.web;

import com.mmrtr.lol.tools.riotmock.config.RiotMockProperties;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** 엔드포인트별 평균±지터 지연. 설정이 없거나 꺼져 있으면 지연하지 않는다. */
@Component
public class LatencySimulator {

    private final RiotMockProperties.Latency latency;

    public LatencySimulator(RiotMockProperties props) {
        this.latency = props.latency();
    }

    public void delay(String endpointKey) throws InterruptedException {
        long millis = delayMillis(endpointKey);
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }

    long delayMillis(String endpointKey) {
        if (!latency.enabled()) {
            return 0;
        }
        RiotMockProperties.Endpoint endpoint = latency.endpoints().get(endpointKey);
        if (endpoint == null) {
            return 0;
        }
        long jitter = endpoint.jitterMs() <= 0 ? 0 : ThreadLocalRandom.current().nextLong(-endpoint.jitterMs(), endpoint.jitterMs() + 1);
        return Math.max(0, endpoint.meanMs() + jitter);
    }
}
