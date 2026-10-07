package com.mmrtr.lol.domain.gamedata.readmodel;

/**
 * 패치노트 목록 항목.
 *
 * @param createdAt 작성일 (yyyy-MM-dd)
 */
public record PatchNoteSummaryReadModel(
        String versionId,
        String title,
        String patchUrl,
        String createdAt
) {
}
