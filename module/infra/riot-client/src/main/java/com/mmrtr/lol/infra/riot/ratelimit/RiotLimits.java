package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotMethod;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 설정({@code riot.rate-limit.app-limits}·{@code method-limits})에서 읽은 Riot 한도. 같은 값을 routing 마다 따로 센다.
 *
 * @param app     app 한도의 창들 (하나 이상)
 * @param methods method 별 한도의 창들. 설정에 없는 method 는 들어 있지 않다
 */
public record RiotLimits(List<RateLimitWindow> app, Map<RiotMethod, List<RateLimitWindow>> methods) {

    private static final Map<String, RiotMethod> BY_NAME = Arrays.stream(RiotMethod.values())
            .collect(Collectors.toUnmodifiableMap(RiotMethod::methodName, Function.identity()));

    /** 설정을 검증해 읽는다. 비었거나 형식이 틀리거나 모르는 method 이름이 있으면 IllegalArgumentException. */
    public static RiotLimits from(RiotAPIProperties.RateLimit config) {
        List<RateLimitWindow> app = windows("riot.rate-limit.app-limits", config.getAppLimits());
        Map<RiotMethod, List<RateLimitWindow>> methods = new EnumMap<>(RiotMethod.class);
        config.getMethodLimits().forEach((name, value) -> {
            RiotMethod method = BY_NAME.get(name);
            if (method == null) {
                throw new IllegalArgumentException("riot.rate-limit.method-limits 에 모르는 method 가 있습니다: " + name);
            }
            methods.put(method, windows("riot.rate-limit.method-limits[" + name + "]", value));
        });
        return new RiotLimits(app, Collections.unmodifiableMap(methods));
    }

    /** method 한도의 창들. 설정에 없으면 빈 목록이다. */
    public List<RateLimitWindow> windowsOf(RiotMethod method) {
        return methods.getOrDefault(method, List.of());
    }

    /** 한도가 설정되지 않은 method ({@link RiotMethod} 순서). */
    public List<RiotMethod> unconfigured() {
        return Arrays.stream(RiotMethod.values()).filter(m -> !methods.containsKey(m)).toList();
    }

    /** 모든 항목이 "한도:창초" 여야 한다. {@link RateLimitWindow#parse} 는 틀린 항목을 건너뛰므로 개수로 잡는다. */
    private static List<RateLimitWindow> windows(String property, String value) {
        List<RateLimitWindow> parsed = RateLimitWindow.parse(value);
        if (parsed.isEmpty() || parsed.size() != value.split(",").length) {
            throw new IllegalArgumentException(property + " 형식이 잘못되었습니다: " + value);
        }
        return List.copyOf(parsed);
    }
}
