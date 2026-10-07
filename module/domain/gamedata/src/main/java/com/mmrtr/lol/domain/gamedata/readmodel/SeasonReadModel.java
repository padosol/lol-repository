package com.mmrtr.lol.domain.gamedata.readmodel;

import java.util.List;

/**
 * 시즌.
 *
 * @param patchVersions 시즌에 속한 패치 버전 값 (최신순)
 */
public record SeasonReadModel(
        Integer seasonValue,
        String seasonName,
        List<String> patchVersions
) {
}
