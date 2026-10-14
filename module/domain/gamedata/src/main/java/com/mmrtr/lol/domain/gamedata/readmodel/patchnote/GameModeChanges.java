package com.mmrtr.lol.domain.gamedata.readmodel.patchnote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameModeChanges(
        List<ChangeEntry> champions,
        List<ChangeEntry> items,
        List<ChangeEntry> systems
) {
}
