package com.mmrtr.lol.domain.summoner.domain;

/** 소환사 갱신 상태. 이름이 응답 값이라 lol-server {@code RenewalStatus} 와 같아야 한다. */
public enum RenewalStatus {
    /** 갱신 요청: 시작했거나 이미 진행 중 / 상태 조회: 진행 중인 갱신 없음 */
    SUCCESS,
    /** 갱신 요청: 쿨다운이라 시작하지 않음 */
    FAILED,
    /** 상태 조회: 갱신 진행 중 */
    PROGRESS
}
