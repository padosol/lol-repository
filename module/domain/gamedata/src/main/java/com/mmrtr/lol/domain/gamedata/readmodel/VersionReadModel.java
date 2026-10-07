package com.mmrtr.lol.domain.gamedata.readmodel;

import java.time.LocalDateTime;

/**
 * 패치 버전.
 *
 * @param versionValue     패치 버전 (예: 16.16). 공식 패치노트는 26.16 으로 표기하지만
 *                         Riot match API 의 gameVersion 이 16.16.x 라, 전적·통계와 같은 축을 쓰려고 16.x 를 저장한다.
 * @param patchVersionData Data Dragon 정적 데이터 버전 (예: 16.16.1). 정적 리소스 URL 은 이 값으로 만든다.
 *                         아직 채워지지 않은 과거 패치는 null.
 */
public record VersionReadModel(
        Long versionId,
        String versionValue,
        String patchVersionData,
        LocalDateTime createdAt
) {
}
