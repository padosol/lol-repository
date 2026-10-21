package com.mmrtr.lol.domain.championstats.domain;

import com.mmrtr.lol.common.type.Tier;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 통계 집계 대상 티어. 단일 티어(EMERALD) 또는 그 티어 이상 범위(MASTER+). lol-server {@code TierFilter} 이전.
 */
public final class TierFilter {

    private final List<Tier> tiers;
    private final String displayString;

    private TierFilter(List<Tier> tiers, String displayString) {
        this.tiers = tiers;
        this.displayString = displayString;
    }

    /** 잘못된 값은 {@link IllegalArgumentException}. */
    public static TierFilter of(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("티어 필터는 비어있을 수 없습니다.");
        }

        if (input.endsWith("+")) {
            Tier baseTier = parseTier(input.substring(0, input.length() - 1));
            List<Tier> tiers = Arrays.stream(Tier.values())
                    .filter(t -> t.getScore() >= baseTier.getScore())
                    .toList();
            return new TierFilter(tiers, input);
        }

        return new TierFilter(List.of(parseTier(input)), input);
    }

    private static Tier parseTier(String name) {
        try {
            return Tier.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 티어입니다: " + name);
        }
    }

    public List<Tier> getTiers() {
        return tiers;
    }

    /** 응답의 {@code tier} 와 캐시 키에 쓰는 입력 그대로의 표기 (예: {@code MASTER+}). */
    public String toDisplayString() {
        return displayString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(tiers, ((TierFilter) o).tiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tiers);
    }

    @Override
    public String toString() {
        return displayString;
    }
}
