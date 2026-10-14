package com.mmrtr.lol.controller.support;

import java.util.List;

/**
 * lol-repository 가 소유한 공개 리소스 prefix. Ingress 라우팅 표(공개 API 재설계 문서 5절)의
 * lol-repository 줄과 같아야 한다.
 */
public final class PublicApiPaths {

    public static final List<String> OWNED_PREFIXES = List.of(
            "/api/v1/summoners",
            "/api/v1/matches",
            "/api/v1/rankings",
            "/api/v1/champion-stats",
            "/api/v1/game-data"
    );

    /** 서비스 간 내부 API. 내부 포트에서만 받는다. */
    public static final String INTERNAL_PREFIX = "/internal";

    private PublicApiPaths() {
    }

    public static boolean isOwned(String path) {
        return OWNED_PREFIXES.stream().anyMatch(prefix -> matches(path, prefix));
    }

    public static boolean isInternal(String path) {
        return matches(path, INTERNAL_PREFIX);
    }

    /** 세그먼트 단위 prefix 매칭 (Ingress {@code pathType: Prefix} 와 같은 의미). */
    public static boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
