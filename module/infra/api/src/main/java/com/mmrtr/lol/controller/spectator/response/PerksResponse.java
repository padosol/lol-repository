package com.mmrtr.lol.controller.spectator.response;

import com.mmrtr.lol.domain.spectator.domain.GamePerks;

import java.util.List;

public record PerksResponse(
        long perkStyle,
        long perkSubStyle,
        List<Long> perkIds
) {
    public static PerksResponse of(GamePerks perks) {
        if (perks == null) {
            return null;
        }
        return new PerksResponse(
                perks.getPerkStyle(),
                perks.getPerkSubStyle(),
                perks.getPerkIds()
        );
    }
}
