package com.mmrtr.lol.domain.gamedata.readmodel.patchnote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AramChanges(
        List<ChangeEntry> champions,
        List<ChangeEntry> augments,
        List<ChangeEntry> items
) {
}
