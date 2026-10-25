package com.mmrtr.lol.support.page;

import java.util.List;

/**
 * 전체 개수 없이 다음 페이지 여부만 아는 조회 결과. lol-server {@code SliceResult} 와 필드가 같다 —
 * 공개 API 의 {@code SliceResponse} 가 그대로 옮겨 담는다.
 */
public record SliceResult<T>(List<T> content, boolean hasNext) {

    public static <T> SliceResult<T> empty() {
        return new SliceResult<>(List.of(), false);
    }
}
