package com.mmrtr.lol.controller.support.response;

import com.mmrtr.lol.support.page.SliceResult;

import java.util.List;

/** 공개 API 슬라이스 응답. lol-server {@code SliceResponse<T>} 와 필드·직렬화 형태가 같아야 한다. */
public record SliceResponse<T>(List<T> content, boolean hasNext) {

    public static <T> SliceResponse<T> of(SliceResult<T> sliceResult) {
        return new SliceResponse<>(sliceResult.content(), sliceResult.hasNext());
    }
}
