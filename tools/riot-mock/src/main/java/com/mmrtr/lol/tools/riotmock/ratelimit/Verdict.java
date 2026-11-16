package com.mmrtr.lol.tools.riotmock.ratelimit;

/**
 * 한도 판정 결과. limitType 은 거절일 때 "application" 또는 "method", 허용이면 null.
 * count 값은 증가 뒤의 수를 "{count}:{W}" 로 이은 헤더 값이다.
 */
public record Verdict(
        boolean allowed,
        String limitType,
        long retryAfterSeconds,
        String appLimit,
        String appCount,
        String methodLimit,
        String methodCount) {
}
