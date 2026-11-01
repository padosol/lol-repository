package com.mmrtr.lol.infra.riot.config;

import java.util.Arrays;

/** 가짜 Riot 서버 주소({@code riot.mock.base-url})가 prod/dev 에서 켜지는 것을 기동 단계에서 막는다. */
public final class RiotMockGuard {

    private RiotMockGuard() {
    }

    public static void assertAllowed(String baseUrl, String[] activeProfiles) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        boolean forbidden = Arrays.stream(activeProfiles).anyMatch(p -> p.equals("prod") || p.equals("dev"));
        if (forbidden) {
            throw new IllegalStateException("riot.mock.base-url 은 prod/dev 프로파일에서 설정할 수 없습니다. 값을 제거하세요.");
        }
    }
}
