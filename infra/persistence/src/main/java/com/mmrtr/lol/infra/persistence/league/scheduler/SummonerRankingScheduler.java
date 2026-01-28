package com.mmrtr.lol.infra.persistence.league.scheduler;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.repository.SummonerRankingRepositoryPort;
import com.mmrtr.lol.domain.league.service.usecase.CalculateSummonerRankingUseCase;
import com.mmrtr.lol.domain.league.service.usecase.TriggerSummonerRankingCalculationUseCase;
import com.mmrtr.lol.infra.persistence.league.repository.LeagueSummonerJpaRepository;
import com.mmrtr.lol.infra.persistence.league.repository.MostChampionProjection;
import com.mmrtr.lol.infra.persistence.league.repository.SummonerRankingProjection;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerRankingScheduler implements TriggerSummonerRankingCalculationUseCase {

    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;
    private final MatchSummonerJpaRepository matchSummonerJpaRepository;
    private final SummonerRankingRepositoryPort summonerRankingRepositoryPort;
    private final CalculateSummonerRankingUseCase calculateSummonerRankingUseCase;

    private static final List<String> QUEUE_TYPES = List.of(
            "RANKED_SOLO_5x5",
            "RANKED_FLEX_SR"
    );

    @Override
    @Scheduled(fixedRate = 7200000)
    public void execute() {
        log.info("소환사 랭킹 스케줄링 시작");
        LocalDateTime snapshotAt = LocalDateTime.now();

        for (String queue : QUEUE_TYPES) {
            List<SummonerRanking> rankings = processQueueRanking(queue, snapshotAt);
            if (!rankings.isEmpty()) {
                calculateSummonerRankingUseCase.execute(rankings);
            }
        }

        log.info("소환사 랭킹 스케줄링 완료");
    }

    private List<SummonerRanking> processQueueRanking(String queue, LocalDateTime snapshotAt) {
        List<SummonerRankingProjection> projections = leagueSummonerJpaRepository.findRankingByQueue(queue);

        if (projections.isEmpty()) {
            log.warn("큐 {} 에 대한 마스터 이상 랭킹 데이터가 없습니다.", queue);
            return List.of();
        }

        Map<String, Integer> previousRanks = summonerRankingRepositoryPort.findPreviousRanksByQueue(queue);

        List<String> puuids = projections.stream()
                .map(SummonerRankingProjection::getPuuid)
                .toList();

        Map<String, List<String>> mostChampionsMap = getMostChampionsMap(puuids);

        List<SummonerRanking> rankings = new ArrayList<>();
        int currentRank = 1;

        for (SummonerRankingProjection projection : projections) {
            String puuid = projection.getPuuid();
            int previousRank = previousRanks.getOrDefault(puuid, 0);
            int rankChange = previousRank > 0 ? previousRank - currentRank : 0;

            List<String> mostChampions = mostChampionsMap.getOrDefault(puuid, List.of());

            int wins = projection.getWins();
            int losses = projection.getLosses();
            int totalGames = wins + losses;
            BigDecimal winRate = totalGames > 0
                    ? BigDecimal.valueOf(wins)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalGames), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            SummonerRanking ranking = SummonerRanking.builder()
                    .puuid(puuid)
                    .queue(queue)
                    .currentRank(currentRank)
                    .previousRank(previousRank)
                    .rankChange(rankChange)
                    .gameName(projection.getGameName())
                    .tagLine(projection.getTagLine())
                    .mostChampion1(mostChampions.size() > 0 ? mostChampions.get(0) : null)
                    .mostChampion2(mostChampions.size() > 1 ? mostChampions.get(1) : null)
                    .mostChampion3(mostChampions.size() > 2 ? mostChampions.get(2) : null)
                    .wins(wins)
                    .losses(losses)
                    .winRate(winRate)
                    .tier(projection.getTier())
                    .rank(projection.getRank())
                    .leaguePoints(projection.getLeaguePoints())
                    .snapshotAt(snapshotAt)
                    .build();

            rankings.add(ranking);
            currentRank++;
        }

        log.info("큐 {} 랭킹 처리 완료: {} 명", queue, rankings.size());
        return rankings;
    }

    private Map<String, List<String>> getMostChampionsMap(List<String> puuids) {
        if (puuids.isEmpty()) {
            return Map.of();
        }

        List<MostChampionProjection> mostChampions = matchSummonerJpaRepository.findMostChampionsByPuuids(puuids);

        Map<String, List<String>> result = new HashMap<>();
        for (MostChampionProjection projection : mostChampions) {
            String puuid = projection.getPuuid();
            result.computeIfAbsent(puuid, k -> new ArrayList<>());

            List<String> champions = result.get(puuid);
            if (champions.size() < 3) {
                champions.add(projection.getChampionName());
            }
        }

        return result;
    }
}
