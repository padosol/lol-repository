package com.mmrtr.lol.controller.admin.response;

import java.time.LocalDateTime;

public record RankingCalculateResponse(
        boolean success,
        String message,
        LocalDateTime executedAt
) {
    public static RankingCalculateResponse of() {
        return new RankingCalculateResponse(
                true,
                "랭킹 계산이 완료되었습니다.",
                LocalDateTime.now()
        );
    }
}
