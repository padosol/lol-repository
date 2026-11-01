package com.mmrtr.lol.tools.riotmock.web;

import com.mmrtr.lol.tools.riotmock.config.RiotMockProperties;
import com.mmrtr.lol.tools.riotmock.metrics.MockMetrics;
import com.mmrtr.lol.tools.riotmock.ratelimit.RateLimitEngine;
import com.mmrtr.lol.tools.riotmock.ratelimit.SlidingPeakTracker;
import com.mmrtr.lol.tools.riotmock.ratelimit.Verdict;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Riot 한도 관문. 토큰 확인, 한도 판정, 헤더 부착, 429/404 응답, 지연, 지표를 맡는다.
 * X-Riot-Token 은 있는지만 보고 값은 기록하지도 태그로 쓰지도 않는다.
 */
@Slf4j
@Component
public class RiotGateFilter extends OncePerRequestFilter {

    private static final String UNKNOWN_ROUTING = "unknown";
    private static final Pattern ROUTING_SEGMENT = Pattern.compile("^/([a-z0-9]+)(?:/|$)");
    private static final String UNAUTHORIZED_BODY = "{\"status\":{\"message\":\"Unauthorized\",\"status_code\":401}}";
    private static final String RATE_LIMITED_BODY = "{\"status\":{\"message\":\"Rate limit exceeded\",\"status_code\":429}}";

    private final RiotMockProperties props;
    private final RateLimitEngine engine;
    private final SlidingPeakTracker tracker;
    private final LatencySimulator latency;
    private final MockMetrics metrics;

    public RiotGateFilter(RiotMockProperties props, RateLimitEngine engine, SlidingPeakTracker tracker,
                          LatencySimulator latency, MockMetrics metrics) {
        this.props = props;
        this.engine = engine;
        this.tracker = tracker;
        this.latency = latency;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = rawPath(request);
        return path.startsWith("/admin/") || path.equals("/admin")
                || path.startsWith("/actuator/") || path.equals("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (props.apiKeyRequired() && request.getHeader("X-Riot-Token") == null) {
            write(response, 401, UNAUTHORIZED_BODY);
            return;
        }

        String path = rawPath(request);
        Optional<RiotEndpoint.Match> match = RiotEndpoint.match(path);
        String routing = match.map(RiotEndpoint.Match::routing).orElseGet(() -> firstSegment(path));
        String endpointKey = match.map(m -> m.endpoint().key()).orElse(RiotEndpoint.UNKNOWN_KEY);

        // 구현하지 않은 경로도 Riot 처럼 그 routing 의 application 한도에 센다. method 버킷은 만들지 않는다.
        Verdict verdict = match.isPresent() ? engine.acquire(routing, endpointKey) : engine.acquireAppOnly(routing);
        tracker.record(routing);
        response.setHeader("X-App-Rate-Limit", verdict.appLimit());
        response.setHeader("X-App-Rate-Limit-Count", verdict.appCount());
        if (verdict.methodLimit() != null) {
            response.setHeader("X-Method-Rate-Limit", verdict.methodLimit());
            response.setHeader("X-Method-Rate-Limit-Count", verdict.methodCount());
        }

        if (!verdict.allowed()) {
            response.setHeader("Retry-After", String.valueOf(verdict.retryAfterSeconds()));
            response.setHeader("X-Rate-Limit-Type", verdict.limitType());
            write(response, 429, RATE_LIMITED_BODY);
            metrics.recordRateLimited(routing, verdict.limitType());
            if (match.isEmpty()) {
                metrics.recordUnknown(routing);
            }
            metrics.recordRequest(routing, endpointKey, 429);
            return;
        }
        if (match.isEmpty()) {
            log.warn("알 수 없는 경로: {}", path);
            write(response, 404, RiotApiController.NOT_FOUND_BODY);
            metrics.recordUnknown(routing);
            metrics.recordRequest(routing, endpointKey, 404);
            return;
        }

        try {
            latency.delay(endpointKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            write(response, 503, "{\"status\":{\"message\":\"Interrupted\",\"status_code\":503}}");
            metrics.recordRequest(routing, endpointKey, 503);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            metrics.recordRequest(routing, endpointKey, response.getStatus());
        }
    }

    /** 첫 경로 조각이 routing 형식이면 그 값, 아니면 unknown. 태그 값이 무한히 늘지 않게 형식을 제한한다. */
    private static String firstSegment(String path) {
        Matcher m = ROUTING_SEGMENT.matcher(path);
        return m.find() ? m.group(1) : UNKNOWN_ROUTING;
    }

    private static String rawPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private static void write(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
