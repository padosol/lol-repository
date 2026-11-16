package com.mmrtr.lol.infra.rabbitmq;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.invocation.InvocationOnMock;

/** 테스트용 {@link RiotPermitPort} mock. */
public final class RiotPermitFixtures {

    private RiotPermitFixtures() {
    }

    /** 요청한 건수만큼 허가를 내주는 mock. */
    public static RiotPermitPort grantingPort() {
        RiotPermitPort port = mock(RiotPermitPort.class);
        given(port.tryAcquire(anyString(), any(), anyMap())).willAnswer(RiotPermitFixtures::grant);
        return port;
    }

    /** 모든 요청을 거절하게 바꾼다. */
    public static void denyAll(RiotPermitPort port) {
        given(port.tryAcquire(anyString(), any(), anyMap())).willReturn(Optional.empty());
    }

    private static Optional<RiotPermits> grant(InvocationOnMock invocation) {
        String platform = invocation.getArgument(0);
        RiotCallPriority priority = invocation.getArgument(1);
        Map<RiotMethod, Integer> counts = invocation.getArgument(2);
        List<RiotPermit> permits = new ArrayList<>();
        counts.forEach((method, count) -> {
            for (int k = 1; k <= count; k++) {
                permits.add(new RiotPermit("t-" + method + "-" + k, platform, method, priority));
            }
        });
        return Optional.of(RiotPermits.of(permits));
    }
}
