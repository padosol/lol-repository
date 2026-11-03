package com.mmrtr.lol.domain.summoner.domain;

/** 소환사 갱신 상태. 이름이 응답 값이다. */
public enum RenewalStatus {
    /** 갱신 요청: 시작했거나 이미 진행 중 / 상태 조회: 진행 중인 갱신 없음 (또는 갱신 완료) */
    SUCCESS,
    /** 갱신 요청: 쿨다운이라 시작하지 않음 / 상태 조회: 갱신 실패 또는 기한 초과 */
    FAILED,
    /** 상태 조회: 갱신 진행 중 */
    PROGRESS
}
