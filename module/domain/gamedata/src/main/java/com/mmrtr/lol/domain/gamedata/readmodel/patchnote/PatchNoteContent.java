package com.mmrtr.lol.domain.gamedata.readmodel.patchnote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code patch_note.content}(jsonb) 구조. lol-patch-crawler 가 적재한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchNoteContent(
        String version,
        GameModeChanges rift,
        GameModeChanges arena,
        AramChanges aram
) {
}
