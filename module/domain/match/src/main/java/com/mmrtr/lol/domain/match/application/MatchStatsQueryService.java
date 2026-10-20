package com.mmrtr.lol.domain.match.application;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.match.application.port.MatchStatsQueryPort;
import com.mmrtr.lol.domain.match.application.usecase.MatchStatsQueryUseCase;
import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** lol-server {@code MatchService} 의 daily-count·랭크 챔피언 조회 이전. */
@Service
@RequiredArgsConstructor
public class MatchStatsQueryService implements MatchStatsQueryUseCase {

    private static final int DAILY_COUNT_MONTHS = 3;
    private static final List<Integer> RANKED_QUEUE_IDS =
            List.of(Queue.RANKED_SOLO_5x5.getQueueId(), Queue.RANKED_FLEX_SR.getQueueId());

    private final MatchStatsQueryPort matchStatsQueryPort;

    @Override
    public DailyGameCountReadModel getDailyGameCounts(String puuid, int season, Integer queueId) {
        LocalDateTime from = LocalDate.now().minusMonths(DAILY_COUNT_MONTHS).atStartOfDay();
        List<Integer> queueIds = queueId == null ? RANKED_QUEUE_IDS : List.of(queueId);
        return DailyGameCountReadModel.of(matchStatsQueryPort.findDailyGameCounts(puuid, season, queueIds, from));
    }

    @Override
    public RankChampionsReadModel getRankChampions(String puuid, Integer season) {
        return RankChampionsReadModel.of(matchStatsQueryPort.findRankChampions(puuid, season));
    }
}
