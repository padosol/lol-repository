package com.mmrtr.lol.infra.riot.ratelimit;

import org.springframework.http.HttpHeaders;

/**
 * Riot 응답의 한도 헤더 네 개 원문 ({@code "500:10,30000:600"} 형식). 없는 헤더는 null 이다. 비교용으로 Redis 에 남길 뿐
 * 허가에는 쓰지 않는다.
 */
public record RiotLimitHeaders(String appLimit, String appCount, String methodLimit, String methodCount) {

    public static RiotLimitHeaders from(HttpHeaders headers) {
        return new RiotLimitHeaders(value(headers, "X-App-Rate-Limit"), value(headers, "X-App-Rate-Limit-Count"),
                value(headers, "X-Method-Rate-Limit"), value(headers, "X-Method-Rate-Limit-Count"));
    }

    public boolean hasApp() {
        return appLimit != null || appCount != null;
    }

    public boolean hasMethod() {
        return methodLimit != null || methodCount != null;
    }

    public boolean isEmpty() {
        return !hasApp() && !hasMethod();
    }

    private static String value(HttpHeaders headers, String name) {
        String value = headers == null ? null : headers.getFirst(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
