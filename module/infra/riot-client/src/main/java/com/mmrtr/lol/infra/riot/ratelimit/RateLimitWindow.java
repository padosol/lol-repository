package com.mmrtr.lol.infra.riot.ratelimit;

import java.util.ArrayList;
import java.util.List;

/**
 * Riot 한도 창 하나 (예: 10초에 500회). {@code X-App-Rate-Limit} 과 {@code *-Count} 헤더가 같은 형식이다.
 */
public record RateLimitWindow(int limit, int windowSeconds) {

    /** "500:10,30000:600" 형식을 파싱한다. 잘못된 항목은 건너뛴다. */
    public static List<RateLimitWindow> parse(String header) {
        List<RateLimitWindow> windows = new ArrayList<>();
        if (header == null || header.isBlank()) {
            return windows;
        }
        for (String entry : header.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                int limit = Integer.parseInt(parts[0].trim());
                int window = Integer.parseInt(parts[1].trim());
                if (limit > 0 && window > 0) {
                    windows.add(new RateLimitWindow(limit, window));
                }
            } catch (NumberFormatException ignored) {
                // 숫자가 아닌 항목은 건너뛴다
            }
        }
        return windows;
    }
}
