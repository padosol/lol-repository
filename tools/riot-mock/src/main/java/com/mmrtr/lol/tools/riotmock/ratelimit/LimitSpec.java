package com.mmrtr.lol.tools.riotmock.ratelimit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Riot 한도 한 쌍. 헤더 표기는 {@code limit:windowSeconds}. */
public record LimitSpec(int limit, int windowSeconds) {

    public static List<LimitSpec> parseAll(String header) {
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("한도 문자열이 비어 있습니다");
        }
        List<LimitSpec> specs = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split(":");
            if (pair.length != 2) {
                throw new IllegalArgumentException("한도 형식은 limit:window 여야 합니다: " + part);
            }
            try {
                specs.add(new LimitSpec(Integer.parseInt(pair[0].trim()), Integer.parseInt(pair[1].trim())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("한도 숫자가 올바르지 않습니다: " + part, e);
            }
        }
        return List.copyOf(specs);
    }

    public static String format(List<LimitSpec> specs) {
        return specs.stream()
                .map(s -> s.limit() + ":" + s.windowSeconds())
                .collect(Collectors.joining(","));
    }
}
