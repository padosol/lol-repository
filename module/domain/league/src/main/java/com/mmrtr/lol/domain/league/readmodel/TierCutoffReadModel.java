package com.mmrtr.lol.domain.league.readmodel;

import com.mmrtr.lol.domain.league.domain.TierCutoff;

import java.time.format.DateTimeFormatter;

/**
 * 티어 컷오프. lol-server {@code TierCutoffReadModel} 과 필드가 같아야 한다 ({@code RankingControllerTest} 가 검증).
 *
 * @param updatedAt {@code yyyy-MM-dd HH:mm:ss} 문자열 (lol-server {@code TierCutoffMapper} 와 같은 형식)
 */
public record TierCutoffReadModel(
        Long id,
        String queue,
        String tier,
        String platformId,
        int minLeaguePoints,
        int lpChange,
        int userCount,
        String updatedAt
) {
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static TierCutoffReadModel from(TierCutoff cutoff) {
        return new TierCutoffReadModel(
                cutoff.getId(),
                cutoff.getQueue(),
                cutoff.getTier(),
                cutoff.getPlatformId(),
                cutoff.getMinLeaguePoints(),
                cutoff.getLpChange(),
                cutoff.getUserCount(),
                cutoff.getUpdatedAt() != null ? cutoff.getUpdatedAt().format(UPDATED_AT_FORMAT) : null);
    }
}
