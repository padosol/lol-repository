package com.mmrtr.lol.domain.gamedata.readmodel.patchnote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StatChange(
        String statName,
        String before,
        String after
) {
}
