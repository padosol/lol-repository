package com.mmrtr.lol.support.page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 조회 결과. lol-server {@code PageResult} 와 필드가 같다 — 공개 API 의 {@code PageResponse} 가 그대로 옮겨 담는다.
 *
 * @param page 1부터 시작하는 페이지 번호
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isFirst,
        boolean isLast
) {
    public <R> PageResult<R> map(Function<T, R> converter) {
        List<R> converted = content.stream().map(converter).toList();
        return new PageResult<>(converted, page, size, totalElements, totalPages, isFirst, isLast);
    }
}
