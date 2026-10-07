package com.mmrtr.lol.domain.gamedata.readmodel;

import com.mmrtr.lol.domain.gamedata.readmodel.patchnote.PatchNoteContent;

/**
 * 패치노트 상세.
 *
 * @param createdAt 작성일 (yyyy-MM-dd)
 */
public record PatchNoteReadModel(
        String versionId,
        String title,
        PatchNoteContent content,
        String patchUrl,
        String createdAt
) {
}
