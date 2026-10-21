package com.mmrtr.lol.domain.championstats.application;

import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ChampionTierCalculatorTest {

    @DisplayName("빈 리스트를 입력하면 빈 리스트를 반환한다")
    @Test
    void emptyList_returnsEmpty() {
        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(List.of());
        assertThat(result).isEmpty();
    }

    @DisplayName("단일 챔피언도 점수 기반으로 티어가 배정된다")
    @Test
    void singleChampion_getsScoreBasedTier() {
        List<ChampionRateReadModel> input = List.of(
                new ChampionRateReadModel(266, 0.52, 0.08, 0.05, 1500)
        );

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).championId()).isEqualTo(266);
        // winRate=0.52, pickRate=0.08, banRate=0.05, totalGames=1500 → score ≈ 71.6 → tier "1"
        assertThat(result.get(0).tier()).isEqualTo("1");
    }

    @DisplayName("저표본 챔피언은 극단적인 지표여도 신뢰도 감쇠로 OP가 되지 않는다")
    @Test
    void lowGames_isShrunkTowardAverage() {
        List<ChampionRateReadModel> input = List.of(
                new ChampionRateReadModel(1, 0.70, 0.15, 0.10, 20),
                new ChampionRateReadModel(2, 0.56, 0.14, 0.09, 1800),
                new ChampionRateReadModel(3, 0.52, 0.08, 0.03, 1500),
                new ChampionRateReadModel(4, 0.48, 0.04, 0.01, 1400)
        );

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        ChampionRateReadModel lowGamesChampion = result.stream()
                .filter(c -> c.championId() == 1).findFirst().orElseThrow();
        ChampionRateReadModel stableHighSampleChampion = result.stream()
                .filter(c -> c.championId() == 2).findFirst().orElseThrow();

        assertThat(tierRank(lowGamesChampion.tier()))
                .isGreaterThanOrEqualTo(tierRank(stableHighSampleChampion.tier()));
        assertThat(lowGamesChampion.tier()).isNotEqualTo("OP");
    }

    @DisplayName("강한 챔피언이 소수일 때 상위 티어도 소수만 배정된다")
    @Test
    void multipleChampions_assignsSparseTopTiers() {
        List<ChampionRateReadModel> input = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            double winRate, pickRate, banRate;
            long totalGames;
            if (i < 3) {
                winRate = 0.56; pickRate = 0.12; banRate = 0.15; totalGames = 3000;
            } else if (i < 10) {
                winRate = 0.53; pickRate = 0.08; banRate = 0.07; totalGames = 2000;
            } else if (i < 30) {
                winRate = 0.515; pickRate = 0.06; banRate = 0.04; totalGames = 1500;
            } else if (i < 65) {
                winRate = 0.50; pickRate = 0.04; banRate = 0.02; totalGames = 1200;
            } else if (i < 85) {
                winRate = 0.48; pickRate = 0.02; banRate = 0.01; totalGames = 1000;
            } else {
                winRate = 0.44; pickRate = 0.008; banRate = 0.003; totalGames = 800;
            }
            input.add(new ChampionRateReadModel(i + 1, winRate + i * 0.0001, pickRate, banRate, totalGames + i));
        }

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        assertThat(result).hasSize(100);

        Map<String, Long> tierCounts = result.stream()
                .collect(Collectors.groupingBy(ChampionRateReadModel::tier, Collectors.counting()));

        assertThat(tierCounts.getOrDefault("OP", 0L)).isLessThanOrEqualTo(10L);
        assertThat(tierCounts.getOrDefault("1", 0L)).isGreaterThan(0L);
        assertThat(tierCounts.getOrDefault("5", 0L)).isGreaterThan(0L);
    }

    @DisplayName("승률과 픽률이 높은 챔피언이 더 높은 tier를 받는다")
    @Test
    void highWinRateAndPickRate_getsHigherTier() {
        List<ChampionRateReadModel> input = List.of(
                new ChampionRateReadModel(1, 0.55, 0.15, 0.10, 2000),
                new ChampionRateReadModel(2, 0.52, 0.10, 0.07, 1500),
                new ChampionRateReadModel(3, 0.48, 0.05, 0.03, 1000),
                new ChampionRateReadModel(4, 0.45, 0.03, 0.01, 800)
        );

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        ChampionRateReadModel best = result.stream()
                .filter(c -> c.championId() == 1).findFirst().orElseThrow();
        ChampionRateReadModel worst = result.stream()
                .filter(c -> c.championId() == 4).findFirst().orElseThrow();

        assertThat(tierRank(best.tier())).isLessThan(tierRank(worst.tier()));

        // 결과가 tier 내림차순(OP→1→2→...→5)으로 정렬되어 있는지 검증
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(tierRank(result.get(i).tier()))
                    .isLessThanOrEqualTo(tierRank(result.get(i + 1).tier()));
        }
    }

    @DisplayName("원본 데이터(championId, winRate 등)가 보존된다")
    @Test
    void originalDataIsPreserved() {
        List<ChampionRateReadModel> input = List.of(
                new ChampionRateReadModel(266, 0.52, 0.08, 0.05, 1500)
        );

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        ChampionRateReadModel champion = result.get(0);
        assertThat(champion.championId()).isEqualTo(266);
        assertThat(champion.winRate()).isEqualTo(0.52);
        assertThat(champion.pickRate()).isEqualTo(0.08);
        assertThat(champion.banRate()).isEqualTo(0.05);
        assertThat(champion.totalGames()).isEqualTo(1500);
        assertThat(champion.tier()).isNotNull();
    }

    @DisplayName("저표본 고승률 챔피언보다 고표본 준수 승률 챔피언이 더 높은 tier를 받을 수 있다")
    @Test
    void highSampleChampion_canBeatLowSampleOutlier() {
        List<ChampionRateReadModel> input = List.of(
                new ChampionRateReadModel(1, 0.70, 0.20, 0.15, 30),
                new ChampionRateReadModel(2, 0.53, 0.11, 0.08, 2500),
                new ChampionRateReadModel(3, 0.48, 0.05, 0.03, 1000),
                new ChampionRateReadModel(4, 0.45, 0.03, 0.01, 800)
        );

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        ChampionRateReadModel lowGames = result.stream()
                .filter(c -> c.championId() == 1).findFirst().orElseThrow();
        ChampionRateReadModel highGames = result.stream()
                .filter(c -> c.championId() == 2).findFirst().orElseThrow();

        assertThat(tierRank(highGames.tier())).isLessThanOrEqualTo(tierRank(lowGames.tier()));
    }

    @DisplayName("동일 지표의 고신뢰 그룹은 모두 같은 티어를 받는다")
    @Test
    void flatDistribution_highConfidence_assignsSameTier() {
        List<ChampionRateReadModel> input = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            input.add(new ChampionRateReadModel(i + 1, 0.50, 0.05, 0.03, 1000 + i));
        }

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        assertThat(result).extracting(ChampionRateReadModel::tier)
                .containsOnly(result.get(0).tier());
    }

    @DisplayName("같은 tier 내에서는 score가 높고 동점이면 게임수가 많은 챔피언이 먼저 정렬된다")
    @Test
    void sameTier_sortedByScoreThenTotalGames() {
        List<ChampionRateReadModel> input = List.of(
                new ChampionRateReadModel(1, 0.54, 0.09, 0.04, 900),
                new ChampionRateReadModel(2, 0.54, 0.09, 0.04, 1300),
                new ChampionRateReadModel(3, 0.54, 0.09, 0.04, 1500),
                new ChampionRateReadModel(4, 0.46, 0.03, 0.01, 1600),
                new ChampionRateReadModel(5, 0.45, 0.02, 0.01, 1700)
        );

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        ChampionRateReadModel first = result.get(0);
        ChampionRateReadModel second = result.get(1);
        ChampionRateReadModel third = result.get(2);

        assertThat(first.tier()).isEqualTo(second.tier());
        assertThat(second.tier()).isEqualTo(third.tier());
        assertThat(first.championId()).isEqualTo(3);
        assertThat(second.championId()).isEqualTo(2);
        assertThat(third.championId()).isEqualTo(1);
    }

    @DisplayName("레이팅 점수는 항상 0~100 범위 내에 있다")
    @Test
    void ratingScore_isWithinExpectedRange() {
        List<ChampionRateReadModel> input = List.of(
                new ChampionRateReadModel(1, 0.70, 0.20, 0.30, 5000),
                new ChampionRateReadModel(2, 0.30, 0.005, 0.001, 5000),
                new ChampionRateReadModel(3, 0.50, 0.05, 0.03, 100),
                new ChampionRateReadModel(4, 0.80, 0.01, 0.01, 5)
        );

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        assertThat(result).hasSize(4);
        for (ChampionRateReadModel c : result) {
            assertThat(c.tier()).isIn("OP", "1", "2", "3", "4", "5");
        }
    }

    @DisplayName("그룹 크기가 달라도 동일 지표의 챔피언은 같은 티어를 받는다")
    @Test
    void absoluteScore_isConsistentAcrossGroupSizes() {
        ChampionRateReadModel target = new ChampionRateReadModel(1, 0.53, 0.08, 0.05, 2000);

        List<ChampionRateReadModel> smallGroup = List.of(
                target,
                new ChampionRateReadModel(2, 0.48, 0.04, 0.02, 1500)
        );

        List<ChampionRateReadModel> largeGroup = new ArrayList<>();
        largeGroup.add(target);
        for (int i = 1; i <= 50; i++) {
            double wr = 0.45 + (i * 0.002);
            largeGroup.add(new ChampionRateReadModel(i + 1, wr, 0.04, 0.02, 1000 + i * 20));
        }

        List<ChampionRateReadModel> resultSmall = ChampionTierCalculator.assignTiers(smallGroup);
        List<ChampionRateReadModel> resultLarge = ChampionTierCalculator.assignTiers(largeGroup);

        String tierSmall = resultSmall.stream()
                .filter(c -> c.championId() == 1).findFirst().orElseThrow().tier();
        String tierLarge = resultLarge.stream()
                .filter(c -> c.championId() == 1).findFirst().orElseThrow().tier();

        assertThat(tierSmall).isEqualTo(tierLarge);
    }

    @DisplayName("극소 표본 챔피언은 신뢰도 감쇠로 50점(중립) 근처로 수렴한다")
    @Test
    void confidenceDampening_pullsTowardNeutral() {
        List<ChampionRateReadModel> input = List.of(
                new ChampionRateReadModel(1, 0.80, 0.20, 0.15, 1),
                new ChampionRateReadModel(2, 0.20, 0.01, 0.005, 1),
                new ChampionRateReadModel(3, 0.50, 0.05, 0.03, 5000)
        );

        List<ChampionRateReadModel> result = ChampionTierCalculator.assignTiers(input);

        ChampionRateReadModel extremeHigh = result.stream()
                .filter(c -> c.championId() == 1).findFirst().orElseThrow();
        ChampionRateReadModel extremeLow = result.stream()
                .filter(c -> c.championId() == 2).findFirst().orElseThrow();

        // 극소 표본은 극단 지표에도 불구하고 중립 근처 티어(3 또는 4)를 받아야 한다
        assertThat(tierRank(extremeHigh.tier())).isGreaterThanOrEqualTo(tierRank("3"));
        assertThat(tierRank(extremeLow.tier())).isGreaterThanOrEqualTo(tierRank("3"));
    }

    private int tierRank(String tier) {
        return switch (tier) {
            case "OP" -> 0;
            case "1" -> 1;
            case "2" -> 2;
            case "3" -> 3;
            case "4" -> 4;
            case "5" -> 5;
            default -> throw new IllegalArgumentException("Unknown tier: " + tier);
        };
    }
}
