package com.mmrtr.lol.domain.summoner.domain.vo;

import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;

import java.util.Locale;

/**
 * 공개 API 경로의 Riot ID {@code gameName-tagLine}. 태그는 생략할 수 있다.
 *
 * @param tagLine 태그를 생략하면 null
 */
public record RiotId(String gameName, String tagLine) {

    private static final char SEPARATOR = '-';

    /**
     * 마지막 {@code -} 로 이름과 태그를 나눈다 — 태그는 영숫자뿐이라 이름에 든 {@code -} 를 태그로 오인하지 않는다.
     * lol-server 는 첫 {@code -} 로 나눈다.
     *
     * @throws CoreException {@code INVALID_INPUT} — 이름이 비어 있을 때
     */
    public static RiotId parse(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.INVALID_INPUT, "Riot ID 가 비어 있습니다.");
        }
        int separator = value.lastIndexOf(SEPARATOR);
        String gameName = separator < 0 ? value : value.substring(0, separator);
        String tagLine = separator < 0 ? null : value.substring(separator + 1);
        if (gameName.isBlank()) {
            throw new CoreException(ErrorType.INVALID_INPUT, "Riot ID 의 이름이 비어 있습니다.");
        }
        return new RiotId(gameName, tagLine == null || tagLine.isBlank() ? null : tagLine);
    }

    public boolean hasTagLine() {
        return tagLine != null;
    }

    /** {@code summoner.search_name} 과 같은 정규화 (공백 제거, 소문자). 태그가 있을 때만 쓴다. */
    public String searchName() {
        return normalize(gameName + "#" + tagLine);
    }

    /** 태그 없이 찾을 때의 {@code search_name} 접두사 ({@code 이름#}). */
    public String searchNamePrefix() {
        return normalize(gameName + "#");
    }

    /** {@code summoner.search_name} 을 저장할 때와 같은 정규화. 자동완성 검색어에도 쓴다. */
    public static String normalize(String value) {
        return value.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
