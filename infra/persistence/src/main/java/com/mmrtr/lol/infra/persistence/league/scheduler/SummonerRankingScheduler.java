package com.mmrtr.lol.infra.persistence.league.scheduler;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.domain.league.repository.SummonerRankingRepositoryPort;
import com.mmrtr.lol.domain.league.service.usecase.SaveTierCutoffUseCase;
import com.mmrtr.lol.domain.league.service.usecase.TriggerSummonerRankingCalculationUseCase;
import com.mmrtr.lol.infra.persistence.league.repository.LeagueSummonerJpaRepository;
import com.mmrtr.lol.infra.persistence.league.repository.MostChampionProjection;
import com.mmrtr.lol.infra.persistence.league.repository.SummonerRankingProjection;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final SaveTierCutoffUseCase saveTierCutoffUseCase;

    private static final int PAGE_SIZE = 5000;

    private static final List<String> QUEUE_TYPES = List.of(
            "RANKED_SOLO_5x5",
            "RANKED_FLEX_SR"
    );

    @Override
    @Scheduled(fixedRate = 7200000)
    public void execute() {
        log.info("소환사 랭킹 스케줄링 시작");

        for (String queue : QUEUE_TYPES) {
            processQueueRanking(queue);
        }

        log.info("소환사 랭킹 스케줄링 완료");
    }

    @Transactional
    protected void processQueueRanking(String queue) {
        // 1. 현재 랭킹을 백업 테이블로 복사
        summonerRankingRepositoryPort.backupCurrentRanks(queue);

        // 2. 기존 데이터 삭제
        summonerRankingRepositoryPort.deleteByQueue(queue);

        // 3. 전체 건수 확인
        long totalCount = leagueSummonerJpaRepository.countRankingByQueue(queue);
        if (totalCount == 0) {
            log.warn("큐 {} 에 대한 마스터 이상 랭킹 데이터가 없습니다.", queue);
            summonerRankingRepositoryPort.clearBackup(queue);
            return;
        }

        // 4. 페이지별 처리 (rankChange = 0으로 저장)
        int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
        int currentRank = 1;

        // 티어 커트라인 계산을 위한 데이터 수집
        Integer minChallengerLP = null;
        Integer minGrandmasterLP = null;

        for (int page = 0; page < totalPages; page++) {
            Page<SummonerRankingProjection> projectionPage =
                    leagueSummonerJpaRepository.findRankingByQueuePaged(
                            queue, PageRequest.of(page, PAGE_SIZE));

            List<String> puuids = projectionPage.getContent().stream()
                    .map(SummonerRankingProjection::getPuuid)
                    .toList();

            Map<String, List<String>> mostChampionsMap = getMostChampionsMap(puuids);

            List<SummonerRanking> rankings = new ArrayList<>();
            for (SummonerRankingProjection projection : projectionPage.getContent()) {
                List<String> mostChampions = mostChampionsMap.getOrDefault(projection.getPuuid(), List.of());

                int wins = projection.getWins();
                int losses = projection.getLosses();
                int totalGames = wins + losses;
                BigDecimal winRate = totalGames > 0
                        ? BigDecimal.valueOf(wins)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalGames), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                SummonerRanking ranking = SummonerRanking.builder()
                        .puuid(projection.getPuuid())
                        .queue(queue)
                        .currentRank(currentRank++)
                        .rankChange(0)  // 임시로 0, 나중에 DB에서 일괄 업데이트
                        .gameName(projection.getGameName())
                        .tagLine(projection.getTagLine())
                        .mostChampion1(!mostChampions.isEmpty() ? mostChampions.get(0) : null)
                        .mostChampion2(mostChampions.size() > 1 ? mostChampions.get(1) : null)
                        .mostChampion3(mostChampions.size() > 2 ? mostChampions.get(2) : null)
                        .wins(wins)
                        .losses(losses)
                        .winRate(winRate)
                        .tier(projection.getTier())
                        .rank(projection.getRank())
                        .leaguePoints(projection.getLeaguePoints())
                        .build();

                rankings.add(ranking);

                // 티어 커트라인 계산
                String tier = projection.getTier();
                int lp = projection.getLeaguePoints();
                if ("CHALLENGER".equals(tier)) {
                    if (minChallengerLP == null || lp < minChallengerLP) {
                        minChallengerLP = lp;
                    }
                } else if ("GRANDMASTER".equals(tier)) {
                    if (minGrandmasterLP == null || lp < minGrandmasterLP) {
                        minGrandmasterLP = lp;
                    }
                }
            }

            summonerRankingRepositoryPort.bulkSaveAll(rankings);
            rankings.clear();
            log.debug("큐 {} 페이지 {}/{} 처리 완료", queue, page + 1, totalPages);
        }

        // 5. rankChange 일괄 UPDATE (DB 레벨)
        summonerRankingRepositoryPort.updateRankChangesFromBackup(queue);

        // 6. 백업 테이블 정리
        summonerRankingRepositoryPort.clearBackup(queue);

        log.info("큐 {} 랭킹 처리 완료: {} 명", queue, totalCount);

        // 7. 티어 커트라인 저장
        List<TierCutoff> cutoffs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        if (minChallengerLP != null) {
            cutoffs.add(TierCutoff.builder()
                    .queue(queue)
                    .tier("CHALLENGER")
                    .minLeaguePoints(minChallengerLP)
                    .updatedAt(now)
                    .build());
        }

        if (minGrandmasterLP != null) {
            cutoffs.add(TierCutoff.builder()
                    .queue(queue)
                    .tier("GRANDMASTER")
                    .minLeaguePoints(minGrandmasterLP)
                    .updatedAt(now)
                    .build());
        }

        if (!cutoffs.isEmpty()) {
            saveTierCutoffUseCase.execute(cutoffs);
        }
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
