package com.mmrtr.lol.domain.match.application;

import com.mmrtr.lol.domain.match.application.port.MatchStatsQueryPort;
import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel;
import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel.DailyCount;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel.RankChampion;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel.RankChampionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchStatsQueryServiceTest {

    private static final String PUUID = "puuid-1";

    private final MatchStatsQueryPort matchStatsQueryPort = mock(MatchStatsQueryPort.class);
    private final MatchStatsQueryService service = new MatchStatsQueryService(matchStatsQueryPort);

    @DisplayName("일별 게임 수는 3개월 전 0시부터, 큐를 안 주면 솔로·자유 랭크 — 최소·최대를 함께 준다")
    @Test
    void getDailyGameCounts_noQueue_countsRankedQueuesSinceThreeMonthsAgo() {
        // given
        LocalDateTime from = LocalDate.now().minusMonths(3).atStartOfDay();
        given(matchStatsQueryPort.findDailyGameCounts(PUUID, 15, List.of(420, 440), from)).willReturn(List.of(
                new DailyCount(LocalDate.of(2026, 9, 1), 3L),
                new DailyCount(LocalDate.of(2026, 9, 2), 1L),
                new DailyCount(LocalDate.of(2026, 9, 5), 5L)));

        // when
        DailyGameCountReadModel result = service.getDailyGameCounts(PUUID, 15, null);

        // then
        assertThat(result.dailyCounts()).hasSize(3);
        assertThat(result.minCount()).isEqualTo(1);
        assertThat(result.maxCount()).isEqualTo(5);
    }

    @DisplayName("큐를 주면 그 큐만, 게임이 없으면 최소·최대 0")
    @Test
    void getDailyGameCounts_queue_countsThatQueueOnly() {
        // given
        given(matchStatsQueryPort.findDailyGameCounts(eq(PUUID), anyInt(), any(), any())).willReturn(List.of());

        // when
        DailyGameCountReadModel result = service.getDailyGameCounts(PUUID, 15, 450);

        // then
        verify(matchStatsQueryPort).findDailyGameCounts(eq(PUUID), eq(15), eq(List.of(450)), any());
        assertThat(result).isEqualTo(new DailyGameCountReadModel(List.of(), 0, 0));
    }

    @DisplayName("랭크 챔피언은 솔로(420)·자유(440)로 나누고 순서를 지킨다, 다른 큐는 버린다")
    @Test
    void getRankChampions_records_splitsByQueueKeepingOrder() {
        // given
        RankChampion ahri = champion(103, 3, 2);
        RankChampion zed = champion(238, 2, 1);
        RankChampion lux = champion(99, 1, 1);
        given(matchStatsQueryPort.findRankChampions(PUUID, 15)).willReturn(List.of(
                new RankChampionRecord(420, ahri), new RankChampionRecord(440, lux),
                new RankChampionRecord(420, zed), new RankChampionRecord(450, lux)));

        // when
        RankChampionsReadModel result = service.getRankChampions(PUUID, 15);

        // then
        assertThat(result.solo()).containsExactly(ahri, zed);
        assertThat(result.flex()).containsExactly(lux);
    }

    @DisplayName("승률은 승리 × 100 / 판 수 (반올림 없음), 판 수 0 이면 0")
    @Test
    void rankChampionOf_winsAndPlayCount_computesWinRate() {
        // when & then
        assertThat(champion(1, 3, 2).winRate()).isEqualTo(200.0 / 3);
        assertThat(champion(1, 0, 0).winRate()).isZero();
    }

    private static RankChampion champion(int championId, long playCount, long win) {
        return RankChampion.of(1.0, 2.0, 3.0, championId, "c" + championId, win, playCount - win, 700.0, 3.0, 60.0,
                20.0, 400.0, playCount);
    }
}
