package com.mmrtr.lol.domain.gamedata.readmodel.patchnote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeEntry(
        String targetName,
        String type,
        String direction,
        List<StatChange> changes
) {
}
