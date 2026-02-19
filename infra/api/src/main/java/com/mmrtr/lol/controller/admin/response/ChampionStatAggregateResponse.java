package com.mmrtr.lol.controller.admin.response;

import java.time.LocalDateTime;

public record ChampionStatAggregateResponse(
        boolean success,
        String message,
        LocalDateTime executedAt
) {
    public static ChampionStatAggregateResponse of() {
        return new ChampionStatAggregateResponse(
                true,
                "챔피언 통계 집계가 완료되었습니다.",
                LocalDateTime.now()
        );
    }
}
