package com.mmrtr.lol.domain.summoner.domain;

/** 갱신 레코드를 바꾼 쓰기 하나가 일으킨 상태 전이. 전이마다 정확히 한 번, 바꾼 호출에만 돌려준다. */
public enum RenewalTransition {
    /** 아무 전이도 없다. */
    NONE,
    /** 기한 안에 모든 단계가 끝나 SUCCESS 가 됐다. */
    SUCCEEDED,
    /** FAILED 가 됐다. */
    FAILED,
    /** 마지막 단계가 기한 뒤에 와서 곧바로 FAILED(deadline) + 늦은 완료가 됐다. */
    FAILED_LATE_COMPLETED,
    /** 이미 기한 FAILED 인 갱신의 모든 단계가 뒤늦게 끝났다. */
    LATE_COMPLETED
}
