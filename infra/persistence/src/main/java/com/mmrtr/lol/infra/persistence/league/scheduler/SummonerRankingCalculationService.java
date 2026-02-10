package com.mmrtr.lol.infra.persistence.league.scheduler;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.domain.league.repository.SummonerRankingRepositoryPort;
import com.mmrtr.lol.domain.league.repository.TierCutoffRepositoryPort;
import com.mmrtr.lol.domain.league.service.usecase.SaveTierCutoffUseCase;
import com.mmrtr.lol.infra.persistence.league.repository.LeagueSummonerJpaRepository;
import com.mmrtr.lol.infra.persistence.league.repository.MostChampionProjection;
import com.mmrtr.lol.infra.persistence.league.repository.SummonerRankingProjection;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerRankingCalculationService {

    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;
    private final MatchSummonerJpaRepository matchSummonerJpaRepository;
    private final SummonerRankingRepositoryPort summonerRankingRepositoryPort;
    private final TierCutoffRepositoryPort tierCutoffRepositoryPort;
    private final SaveTierCutoffUseCase saveTierCutoffUseCase;

    private static final int PAGE_SIZE = 5000;

    public void processQueueRanking(String queue) {
        try {
            // 0. 잔존 백업 데이터 방어적 정리
            summonerRankingRepositoryPort.clearBackup(queue);
            tierCutoffRepositoryPort.clearBackup(queue);

            // 1. 현재 랭킹을 백업 테이블로 복사
            summonerRankingRepositoryPort.backupCurrentRanks(queue);

            // 1-1. 현재 티어 커트라인을 백업 테이블로 복사
            tierCutoffRepositoryPort.backupCurrentCutoffs(queue);

            // 2. 기존 데이터 삭제
            summonerRankingRepositoryPort.deleteByQueue(queue);

            // 3. 지역 목록 (Platform enum 기반)
            List<String> regions = Arrays.stream(Platform.values())
                    .map(Platform::getPlatformId)
                    .toList();

            // 티어 커트라인 계산을 위한 데이터 수집 (region별)
            Map<String, Integer> minChallengerLPByRegion = new HashMap<>();
            Map<String, Integer> minGrandmasterLPByRegion = new HashMap<>();
            Map<String, Integer> challengerCountByRegion = new HashMap<>();
            Map<String, Integer> grandmasterCountByRegion = new HashMap<>();

            long totalProcessed = 0;

            // 4. 지역별로 순위 계산
            for (String region : regions) {
                long regionCount = leagueSummonerJpaRepository.countRankingByQueueAndRegion(queue, region);
                if (regionCount == 0) {
                    continue;
                }

                int totalPages = (int) Math.ceil((double) regionCount / PAGE_SIZE);
                int currentRank = 1;

                for (int page = 0; page < totalPages; page++) {
                    Page<SummonerRankingProjection> projectionPage =
                            leagueSummonerJpaRepository.findRankingByQueueAndRegionPaged(
                                    queue, region, PageRequest.of(page, PAGE_SIZE));

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
                                .region(region)
                                .currentRank(currentRank++)
                                .rankChange(0)
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

                        String tier = projection.getTier();
                        int lp = projection.getLeaguePoints();
                        if ("CHALLENGER".equals(tier)) {
                            minChallengerLPByRegion.merge(region, lp, Math::min);
                            challengerCountByRegion.merge(region, 1, Integer::sum);
                        } else if ("GRANDMASTER".equals(tier)) {
                            minGrandmasterLPByRegion.merge(region, lp, Math::min);
                            grandmasterCountByRegion.merge(region, 1, Integer::sum);
                        }
                    }

                    summonerRankingRepositoryPort.bulkSaveAll(rankings);
                    rankings.clear();
                    log.debug("큐 {} 지역 {} 페이지 {}/{} 처리 완료", queue, region, page + 1, totalPages);
                }

                totalProcessed += regionCount;
                log.debug("큐 {} 지역 {} 랭킹 처리 완료: {} 명", queue, region, regionCount);
            }

            // 5. rankChange 일괄 UPDATE (DB 레벨)
            summonerRankingRepositoryPort.updateRankChangesFromBackup(queue);

            // 6. 백업 테이블 정리
            summonerRankingRepositoryPort.clearBackup(queue);

            log.info("큐 {} 랭킹 처리 완료: {} 명 ({} 개 지역)", queue, totalProcessed, regions.size());

            // 7. 티어 커트라인 저장 (region별)
            List<TierCutoff> cutoffs = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (Map.Entry<String, Integer> entry : minChallengerLPByRegion.entrySet()) {
                cutoffs.add(TierCutoff.builder()
                        .queue(queue)
                        .tier("CHALLENGER")
                        .region(entry.getKey())
                        .minLeaguePoints(entry.getValue())
                        .userCount(challengerCountByRegion.getOrDefault(entry.getKey(), 0))
                        .updatedAt(now)
                        .build());
            }

            for (Map.Entry<String, Integer> entry : minGrandmasterLPByRegion.entrySet()) {
                cutoffs.add(TierCutoff.builder()
                        .queue(queue)
                        .tier("GRANDMASTER")
                        .region(entry.getKey())
                        .minLeaguePoints(entry.getValue())
                        .userCount(grandmasterCountByRegion.getOrDefault(entry.getKey(), 0))
                        .updatedAt(now)
                        .build());
            }

            if (!cutoffs.isEmpty()) {
                saveTierCutoffUseCase.execute(cutoffs);

                // 8. lpChange 계산 및 업데이트
                tierCutoffRepositoryPort.updateLpChangesFromBackup(queue);
            }

            // 9. 티어 커트라인 백업 정리
            tierCutoffRepositoryPort.clearBackup(queue);

        } catch (Exception e) {
            log.error("큐 {} 랭킹 처리 중 오류 발생", queue, e);
            safeCleanupBackups(queue);
        }
    }

    private void safeCleanupBackups(String queue) {
        try {
            summonerRankingRepositoryPort.clearBackup(queue);
        } catch (Exception ex) {
            log.error("큐 {} 랭킹 백업 정리 실패", queue, ex);
        }
        try {
            tierCutoffRepositoryPort.clearBackup(queue);
        } catch (Exception ex) {
            log.error("큐 {} 티어 커트라인 백업 정리 실패", queue, ex);
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
