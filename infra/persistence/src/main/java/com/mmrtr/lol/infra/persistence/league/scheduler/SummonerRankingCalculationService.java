package com.mmrtr.lol.infra.persistence.league.scheduler;

import com.mmrtr.lol.common.type.Tier;
import com.mmrtr.lol.domain.league.application.port.LeagueApiPort;
import com.mmrtr.lol.domain.league.application.port.LeagueApiPort.LeagueEntry;
import com.mmrtr.lol.domain.league.application.port.SummonerRankingRepositoryPort;
import com.mmrtr.lol.domain.league.application.port.TierCutoffRepositoryPort;
import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.domain.summoner.application.SummonerService;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.infra.persistence.league.repository.MostChampionProjection;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerRankingCalculationService {

    private final LeagueApiPort leagueApiPort;
    private final MatchSummonerJpaRepository matchSummonerJpaRepository;
    private final SummonerRankingRepositoryPort summonerRankingRepositoryPort;
    private final TierCutoffRepositoryPort tierCutoffRepositoryPort;
    private final SummonerRepositoryPort summonerRepositoryPort;
    private final SummonerService summonerService;

    private static final String PLATFORM_KR = "KR";
    private static final long UNKNOWN_SUMMONER_FETCH_DELAY_MS = 500;

    public void processQueueRanking(String queue) {
        try {
            // 잔존 백업 데이터 방어적 정리
            summonerRankingRepositoryPort.clearBackup(queue);
            tierCutoffRepositoryPort.clearBackup(queue);

            // 현재 랭킹/티어 커트라인을 백업 (기존 데이터는 유지 → 유저 조회 정상)
            summonerRankingRepositoryPort.backupCurrentRanks(queue);
            tierCutoffRepositoryPort.backupCurrentCutoffs(queue);

            // Riot API에서 챌린저/그랜드마스터 데이터 조회 (KR만)
            log.info("큐 {} 챌린저/그랜드마스터 데이터 조회 시작 (KR)", queue);

            Map<Tier, List<LeagueEntry>> apexEntries = leagueApiPort.getApexEntries(queue, PLATFORM_KR);
            List<LeagueEntry> challengerEntries = apexEntries.getOrDefault(Tier.CHALLENGER, List.of());
            List<LeagueEntry> grandmasterEntries = apexEntries.getOrDefault(Tier.GRANDMASTER, List.of());

            log.info("챌린저 {} 명, 그랜드마스터 {} 명 조회 완료",
                    challengerEntries.size(), grandmasterEntries.size());

            // 엔트리 병합 및 정렬
            List<RankedEntry> mergedEntries = new ArrayList<>();
            for (LeagueEntry entry : challengerEntries) {
                mergedEntries.add(RankedEntry.of(entry, Tier.CHALLENGER));
            }
            for (LeagueEntry entry : grandmasterEntries) {
                mergedEntries.add(RankedEntry.of(entry, Tier.GRANDMASTER));
            }

            mergedEntries.sort(Comparator
                    .comparingInt((RankedEntry e) -> e.absolutePoints)
                    .reversed()
                    .thenComparing(e -> e.entry.puuid()));

            // 소환사 정보 배치 조회
            List<String> puuids = mergedEntries.stream()
                    .map(e -> e.entry.puuid())
                    .toList();

            Map<String, Summoner> summonerMap = summonerRepositoryPort.findAllByPuuidIn(puuids);

            // 미등록 소환사 조회 (500ms 간격)
            List<String> unknownPuuids = mergedEntries.stream()
                    .map(e -> e.entry.puuid())
                    .filter(puuid -> !summonerMap.containsKey(puuid))
                    .toList();

            if (!unknownPuuids.isEmpty()) {
                log.info("미등록 소환사 {} 명 조회 시작", unknownPuuids.size());
                fetchUnknownSummoners(unknownPuuids, summonerMap);
                log.info("미등록 소환사 조회 완료");
            }

            // 전체 랭킹 데이터를 메모리에 빌드 (DB에 아직 쓰지 않음)
            List<SummonerRanking> allRankings = buildRankings(queue, mergedEntries, summonerMap);

            log.info("큐 {} 랭킹 데이터 준비 완료: {} 명, 교체 시작", queue, allRankings.size());

            // 단일 트랜잭션으로 DELETE + INSERT + rankChange UPDATE (데이터 공백 없음)
            summonerRankingRepositoryPort.replaceAllRankings(queue, allRankings);
            summonerRankingRepositoryPort.clearBackup(queue);

            log.info("큐 {} 랭킹 교체 완료: {} 명 (KR)", queue, allRankings.size());

            // 티어 커트라인 빌드 및 교체
            List<TierCutoff> cutoffs = buildTierCutoffs(queue, mergedEntries);

            if (!cutoffs.isEmpty()) {
                tierCutoffRepositoryPort.replaceAllCutoffs(queue, cutoffs);
            }

            tierCutoffRepositoryPort.clearBackup(queue);

        } catch (Exception e) {
            log.error("큐 {} 랭킹 처리 중 오류 발생", queue, e);
            safeCleanupBackups(queue);
        }
    }

    private List<SummonerRanking> buildRankings(
            String queue,
            List<RankedEntry> mergedEntries,
            Map<String, Summoner> summonerMap) {

        List<SummonerRanking> allRankings = new ArrayList<>();
        int currentRank = 1;

        List<String> allPuuids = mergedEntries.stream()
                .map(e -> e.entry.puuid())
                .toList();

        Map<String, List<String>> mostChampionsMap = getMostChampionsMap(allPuuids);

        for (RankedEntry rankedEntry : mergedEntries) {
            LeagueEntry entry = rankedEntry.entry;
            Tier tier = rankedEntry.tier;
            Summoner summoner = summonerMap.get(entry.puuid());

            String gameName = summoner != null ? summoner.getGameIdentity().gameName() : null;
            String tagLine = summoner != null ? summoner.getGameIdentity().tagLine() : null;

            List<String> mostChampions = mostChampionsMap.getOrDefault(entry.puuid(), List.of());

            int wins = entry.wins();
            int losses = entry.losses();
            int totalGames = wins + losses;
            BigDecimal winRate = totalGames > 0
                    ? BigDecimal.valueOf(wins)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalGames), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            allRankings.add(SummonerRanking.builder()
                    .puuid(entry.puuid())
                    .queue(queue)
                    .platformId(PLATFORM_KR)
                    .currentRank(currentRank++)
                    .rankChange(0)
                    .gameName(gameName)
                    .tagLine(tagLine)
                    .mostChampion1(!mostChampions.isEmpty() ? mostChampions.get(0) : null)
                    .mostChampion2(mostChampions.size() > 1 ? mostChampions.get(1) : null)
                    .mostChampion3(mostChampions.size() > 2 ? mostChampions.get(2) : null)
                    .wins(wins)
                    .losses(losses)
                    .winRate(winRate)
                    .tier(tier.name())
                    .rank(entry.rank())
                    .leaguePoints(entry.leaguePoints())
                    .build());
        }

        return allRankings;
    }

    private List<TierCutoff> buildTierCutoffs(String queue, List<RankedEntry> mergedEntries) {
        int challengerCount = 0;
        int grandmasterCount = 0;
        int minChallengerLP = Integer.MAX_VALUE;
        int minGrandmasterLP = Integer.MAX_VALUE;

        for (RankedEntry rankedEntry : mergedEntries) {
            if (Tier.CHALLENGER == rankedEntry.tier) {
                minChallengerLP = Math.min(minChallengerLP, rankedEntry.entry.leaguePoints());
                challengerCount++;
            } else if (Tier.GRANDMASTER == rankedEntry.tier) {
                minGrandmasterLP = Math.min(minGrandmasterLP, rankedEntry.entry.leaguePoints());
                grandmasterCount++;
            }
        }

        List<TierCutoff> cutoffs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        if (challengerCount > 0) {
            cutoffs.add(TierCutoff.builder()
                    .queue(queue)
                    .tier(Tier.CHALLENGER.name())
                    .platformId(PLATFORM_KR)
                    .minLeaguePoints(minChallengerLP)
                    .userCount(challengerCount)
                    .updatedAt(now)
                    .build());
        }

        if (grandmasterCount > 0) {
            cutoffs.add(TierCutoff.builder()
                    .queue(queue)
                    .tier(Tier.GRANDMASTER.name())
                    .platformId(PLATFORM_KR)
                    .minLeaguePoints(minGrandmasterLP)
                    .userCount(grandmasterCount)
                    .updatedAt(now)
                    .build());
        }

        return cutoffs;
    }

    private void fetchUnknownSummoners(List<String> unknownPuuids, Map<String, Summoner> summonerMap) {
        int fetched = 0;
        for (String puuid : unknownPuuids) {
            try {
                Summoner summoner = summonerService.getSummonerByPuuid(PLATFORM_KR, puuid);
                summonerMap.put(puuid, summoner);
                fetched++;
                if (fetched % 50 == 0) {
                    log.info("미등록 소환사 조회 진행: {}/{}", fetched, unknownPuuids.size());
                }
                Thread.sleep(UNKNOWN_SUMMONER_FETCH_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("미등록 소환사 조회 중 인터럽트 발생, 중단합니다.");
                break;
            } catch (Exception e) {
                log.warn("미등록 소환사 조회 실패 puuid={}: {}", puuid, e.getMessage());
            }
        }
        log.info("미등록 소환사 조회 결과: {}/{} 성공", fetched, unknownPuuids.size());
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
            List<String> champions = result.computeIfAbsent(projection.getPuuid(), k -> new ArrayList<>());
            if (champions.size() < 3) {
                champions.add(projection.getChampionName());
            }
        }

        return result;
    }

    private record RankedEntry(LeagueEntry entry, Tier tier, int absolutePoints) {
        static RankedEntry of(LeagueEntry entry, Tier tier) {
            return new RankedEntry(entry, tier, tier.getScore() + entry.leaguePoints());
        }
    }
}
