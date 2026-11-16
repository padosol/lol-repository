package com.mmrtr.lol.domain.summoner.application.port;

/** 갱신 클릭 쿨다운. 갱신 진행 여부는 {@link RenewalRecordPort} 의 갱신 레코드로 본다. */
public interface SummonerRenewalStatePort {

    /** 쿨다운을 건다. 이미 쿨다운 중이면 false (확인과 설정이 한 번에 일어난다). */
    boolean tryStartClickCooldown(String puuid);
}
