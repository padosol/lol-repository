package com.mmrtr.lol.domain.championstats.application;

public final class WilsonScore {

    // 95% 신뢰구간 (alpha=0.05) 의 정규분포 분위수.
    private static final double Z = 1.959964;

    private WilsonScore() {
    }

    public static double lowerBound95(long successes, long total) {
        if (total <= 0) {
            return 0.0;
        }
        double p = (double) successes / total;
        double z2 = Z * Z;
        double denom = 1.0 + z2 / total;
        double center = p + z2 / (2.0 * total);
        double margin = Z * Math.sqrt((p * (1.0 - p) + z2 / (4.0 * total)) / total);
        double lower = (center - margin) / denom;
        if (lower < 0.0) {
            return 0.0;
        }
        return lower;
    }
}
