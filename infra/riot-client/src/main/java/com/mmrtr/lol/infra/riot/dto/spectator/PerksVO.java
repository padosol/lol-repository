package com.mmrtr.lol.infra.riot.dto.spectator;

import java.util.List;

/**
 * Riot API 룬 정보 VO
 */
public record PerksVO(
    long perkStyle,
    long perkSubStyle,
    List<Long> perkIds
) {}
