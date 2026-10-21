package com.mmrtr.lol.controller.support.response;

import com.mmrtr.lol.support.page.PageResult;

import java.util.List;

/** 공개 API 페이지 응답. lol-server {@code PageResponse<T>} 와 필드·직렬화 형태가 같아야 한다. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isFirst,
        boolean isLast
) {
    public static <T> PageResponse<T> of(PageResult<T> pageResult) {
        return new PageResponse<>(
                pageResult.content(),
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements(),
                pageResult.totalPages(),
                pageResult.isFirst(),
                pageResult.isLast());
    }
}
