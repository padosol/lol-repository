package com.mmrtr.lol.common.type;

import lombok.Getter;

import java.util.Set;

@Getter
public enum TierGroup {

    ALL(Set.of("CHALLENGER", "GRANDMASTER", "MASTER", "DIAMOND", "EMERALD", "PLATINUM", "GOLD", "SILVER", "BRONZE", "IRON")),
    EMERALD_PLUS(Set.of("CHALLENGER", "GRANDMASTER", "MASTER", "DIAMOND", "EMERALD")),
    DIAMOND_PLUS(Set.of("CHALLENGER", "GRANDMASTER", "MASTER", "DIAMOND")),
    MASTER_PLUS(Set.of("CHALLENGER", "GRANDMASTER", "MASTER"));

    private final Set<String> includedTiers;

    TierGroup(Set<String> includedTiers) {
        this.includedTiers = includedTiers;
    }
}
