package com.mmrtr.lol.infra.riot.config;

import java.util.Arrays;

/** 가짜 Riot 서버 주소({@code riot.mock.base-url})는 lab 프로파일에서만 켤 수 있다(.env 의 RIOT_MOCK_BASE_URL 이 다른 프로파일로 새는 것도 막는다). */
public final class RiotMockGuard {

    private RiotMockGuard() {
    }

    public static void assertAllowed(String baseUrl, String[] activeProfiles) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        boolean forbidden = Arrays.stream(activeProfiles).anyMatch(p -> p.equals("prod") || p.equals("dev"));
        boolean lab = Arrays.asList(activeProfiles).contains("lab");
        if (forbidden || !lab) {
            throw new IllegalStateException(
                    "riot.mock.base-url 은 lab 프로파일에서만(prod/dev 제외) 설정할 수 있습니다. 값을 제거하세요.");
        }
    }
}
