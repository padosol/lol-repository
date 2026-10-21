package com.mmrtr.lol.domain.match.readmodel;

import java.time.LocalDate;
import java.util.List;

/** 날짜별 게임 수와 기간 안의 하루 최소·최대 게임 수. 필드가 lol-server {@code DailyGameCountResponse} 와 같다. */
public record DailyGameCountReadModel(List<DailyCount> dailyCounts, long minCount, long maxCount) {

    /** @param gameDate 게임 생성 일시({@code match.game_create_datetime})의 날짜 */
    public record DailyCount(LocalDate gameDate, Long gameCount) {
    }

    /** 게임이 없으면 최소·최대 모두 0. */
    public static DailyGameCountReadModel of(List<DailyCount> dailyCounts) {
        long minCount = dailyCounts.stream().mapToLong(DailyCount::gameCount).min().orElse(0L);
        long maxCount = dailyCounts.stream().mapToLong(DailyCount::gameCount).max().orElse(0L);
        return new DailyGameCountReadModel(dailyCounts, minCount, maxCount);
    }
}
