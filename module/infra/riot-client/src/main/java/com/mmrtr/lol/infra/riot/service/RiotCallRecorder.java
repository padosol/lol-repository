package com.mmrtr.lol.infra.riot.service;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRateLimiter;
import com.mmrtr.lol.support.riot.RiotPermit;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 허가받은 Riot 호출 한 번을 보내고 응답 쪽 일을 한다. 다시 보내지 않는다.
 *
 * <ul>
 *   <li>응답 상태를 {@code riot.api.responses{status,host}} 로 센다.</li>
 *   <li>429 면 {@link RiotRateLimitException} 의 Retry-After·X-Rate-Limit-Type 으로 정지한 뒤 그대로 던진다.</li>
 * </ul>
 * 그 밖의 응답 뒤에는 한도 관리자를 부르지 않는다. 정지의 실패는 호출을 실패시키지 않는다 (경고 로그만 남긴다).
 */
@Slf4j
@Component
public class RiotCallRecorder {

    private static final String UNKNOWN = "unknown";

    private final RiotRateLimiter limiter;
    private final RiotAPIProperties.RateLimit props;
    private final MeterRegistry meterRegistry;

    public RiotCallRecorder(RiotRateLimiter limiter, RiotAPIProperties properties, MeterRegistry meterRegistry) {
        this.limiter = limiter;
        this.props = properties.getRateLimit();
        this.meterRegistry = meterRegistry;
    }

    /**
     * call 을 한 번 부르고 본문을 돌려준다. host 는 {@code https://asia.api.riotgames.com} 처럼 Riot 호스트이고,
     * 첫 {@code .} 앞이 routing 이다 (한도 키·지표 라벨).
     */
    public <T> T exchange(RiotPermit permit, String host, Supplier<ResponseEntity<T>> call) {
        String hostName = hostName(host);
        String routing = routingOf(hostName);
        String method = permit.method().methodName();
        try {
            ResponseEntity<T> response = call.get();
            countResponse(hostName, response.getStatusCode());
            return response.getBody();
        } catch (RiotClientException e) {
            countResponse(hostName, e.getStatus());
            if (e instanceof RiotRateLimitException rateLimited) {
                on429(routing, method, rateLimited);
            }
            throw e;
        } catch (RiotServerException e) {
            countResponse(hostName, e.getStatus());
            throw e;
        }
    }

    private void countResponse(String hostName, HttpStatusCode status) {
        if (status != null) {
            meterRegistry.counter("riot.api.responses",
                    "status", String.valueOf(status.value()), "host", hostName).increment();
        }
    }

    private void on429(String routing, String method, RiotRateLimitException e) {
        String type = e.getRateLimitType();
        meterRegistry.counter("riot.api.rate_limited", "routing", routing, "type", typeTag(type)).increment();
        Duration pause = e.getRetryAfter() != null ? e.getRetryAfter() : props.getDefaultPause();
        // application 이면 routing 전체, 그 밖(method·service·없음)이면 그 method 만 멈춘다
        String pausedMethod = "application".equalsIgnoreCase(type) ? null : method;
        safely("429-pause", routing, method, () -> limiter.pause(routing, pausedMethod, pause));
    }

    private static void safely(String step, String routing, String method, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Riot 응답 후처리 실패 step={}, routing={}, method={}", step, routing, method, e);
        }
    }

    /** 429 의 X-Rate-Limit-Type 을 소문자 그대로, 없으면 none. */
    private static String typeTag(String type) {
        return type == null || type.isBlank() ? "none" : type.trim().toLowerCase(Locale.ROOT);
    }

    /** {@code https://asia.api.riotgames.com} → {@code asia.api.riotgames.com}. */
    private static String hostName(String host) {
        if (host == null || host.isBlank()) {
            return UNKNOWN;
        }
        int scheme = host.indexOf("://");
        String bare = scheme < 0 ? host : host.substring(scheme + 3);
        int slash = bare.indexOf('/');
        return slash < 0 ? bare : bare.substring(0, slash);
    }

    private static String routingOf(String hostName) {
        int dot = hostName.indexOf('.');
        return dot < 0 ? hostName : hostName.substring(0, dot);
    }
}
