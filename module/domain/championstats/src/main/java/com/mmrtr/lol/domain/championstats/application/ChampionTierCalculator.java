package com.mmrtr.lol.domain.championstats.application;

import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ChampionTierCalculator {

    private static final int K = 30;
    private static final double CONFIDENCE_GAMES = 80.0;
    private static final double W_WIN_RATE = 0.55;
    private static final double W_PICK_RATE = 0.30;
    private static final double W_BAN_RATE = 0.15;
    private static final double NEUTRAL_SCORE = 50.0;

    private ChampionTierCalculator() {
    }

    public static List<ChampionRateReadModel> assignTiers(List<ChampionRateReadModel> champions) {
        if (champions.isEmpty()) {
            return champions;
        }

        double[] scores = computeRatingScores(champions);

        int total = champions.size();
        String[] tiers = new String[total];
        for (int i = 0; i < total; i++) {
            tiers[i] = tierFromScore(scores[i]);
        }

        Integer[] indices = new Integer[total];
        for (int i = 0; i < total; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> {
            int tierCmp = tierOrder(tiers[a]) - tierOrder(tiers[b]);
            if (tierCmp != 0) {
                return tierCmp;
            }
            int scoreCmp = Double.compare(scores[b], scores[a]);
            if (scoreCmp != 0) {
                return scoreCmp;
            }
            return Long.compare(champions.get(b).totalGames(), champions.get(a).totalGames());
        });

        List<ChampionRateReadModel> result = new ArrayList<>(total);
        for (int rank = 0; rank < total; rank++) {
            int idx = indices[rank];
            result.add(champions.get(idx).withTier(tiers[idx]));
        }
        return result;
    }

    private static double[] computeRatingScores(List<ChampionRateReadModel> champions) {
        double avgWinRate = computeWeightedAvgWinRate(champions);
        double[] scores = new double[champions.size()];

        for (int i = 0; i < champions.size(); i++) {
            ChampionRateReadModel c = champions.get(i);

            double adjustedWinRate = (c.totalGames() * c.winRate() + K * avgWinRate)
                    / (c.totalGames() + K);

            double winRateScore = 100.0 / (1.0 + Math.exp(-40.0 * (adjustedWinRate - 0.50)));
            double pickRateScore = 100.0 * (1.0 - Math.exp(-30.0 * c.pickRate()));
            double banRateScore = 100.0 * (1.0 - Math.exp(-15.0 * c.banRate()));

            double baseScore = W_WIN_RATE * winRateScore
                    + W_PICK_RATE * pickRateScore
                    + W_BAN_RATE * banRateScore;

            double confidence = confidence(c.totalGames());
            scores[i] = baseScore * confidence + NEUTRAL_SCORE * (1.0 - confidence);
        }
        return scores;
    }

    private static double computeWeightedAvgWinRate(List<ChampionRateReadModel> champions) {
        double sumWeighted = 0;
        long sumGames = 0;
        for (ChampionRateReadModel c : champions) {
            sumWeighted += c.winRate() * c.totalGames();
            sumGames += c.totalGames();
        }
        return sumGames == 0 ? 0.5 : sumWeighted / sumGames;
    }

    private static double confidence(long totalGames) {
        return totalGames / (totalGames + CONFIDENCE_GAMES);
    }

    private static int tierOrder(String tier) {
        return switch (tier) {
            case "OP" -> 0;
            case "1" -> 1;
            case "2" -> 2;
            case "3" -> 3;
            case "4" -> 4;
            case "5" -> 5;
            default -> 6;
        };
    }

    private static String tierFromScore(double score) {
        if (score >= 80.0) {
            return "OP";
        } else if (score >= 65.0) {
            return "1";
        } else if (score >= 53.0) {
            return "2";
        } else if (score >= 47.0) {
            return "3";
        } else if (score >= 35.0) {
            return "4";
        }
        return "5";
    }
}
