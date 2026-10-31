package com.mmrtr.lol.infra.riot.ratelimit;

import java.util.ArrayList;
import java.util.List;

/**
 * Riot 한도 창 하나 (예: 10초에 500회). {@code X-App-Rate-Limit} 과 {@code *-Count} 헤더가 같은 형식이다.
 *
 * <p>W초 고정 창은 1초 칸 W+1 개(또는 W초 칸 2개)에 걸칠 수 있어 허용치에 그만큼 여유를 둔다.
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

    private int oneSecondAllowance(double safetyRatio) {
        return (int) Math.floor(Math.min(
                limit * safetyRatio / windowSeconds, (double) limit / (windowSeconds + 1)));
    }

    /** 허용치를 적용할 칸의 길이(초). 1초 칸 허용치가 1 이상이면 1, 아니면 W초. */
    public int bucketSeconds(double safetyRatio) {
        return oneSecondAllowance(safetyRatio) >= 1 ? 1 : windowSeconds;
    }

    /** {@link #bucketSeconds} 칸 하나에 허용하는 호출 수. */
    public int allowance(double safetyRatio) {
        int oneSecond = oneSecondAllowance(safetyRatio);
        if (oneSecond >= 1) {
            return oneSecond;
        }
        return Math.max(1, (int) Math.floor(limit * safetyRatio / 2));
    }

    /** 모든 창을 만족하는 초당 허용 호출 수. 창이 없으면 제한 없음. */
    public static int callsPerSecond(List<RateLimitWindow> windows, double safetyRatio) {
        int min = Integer.MAX_VALUE;
        for (RateLimitWindow w : windows) {
            int bucket = w.bucketSeconds(safetyRatio);
            int allowance = w.allowance(safetyRatio);
            int perSecond = bucket == 1 ? allowance : Math.max(1, allowance / bucket);
            min = Math.min(min, perSecond);
        }
        return min;
    }

    /** 같은 창끼리 비교해 사용량이 한도의 ratio 이상인 창이 있는지. */
    public static boolean nearLimit(
            List<RateLimitWindow> limits, List<RateLimitWindow> counts, double ratio) {
        for (RateLimitWindow l : limits) {
            for (RateLimitWindow c : counts) {
                if (c.windowSeconds == l.windowSeconds && c.limit >= l.limit * ratio) {
                    return true;
                }
            }
        }
        return false;
    }
}
