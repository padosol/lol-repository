package com.mmrtr.lol.domain.summoner.application.port;

/**
 * 갱신 진행 표시와 클릭 쿨다운. 진행 표시는 갱신 컨슈머가 끝날 때 지운다 (못 지우면 TTL 로 사라진다).
 * lol-server 와 같은 키를 써서, 이전 과도기에 어느 쪽이 요청을 받아도 상태가 이어진다.
 */
public interface SummonerRenewalStatePort {

    boolean isRenewing(String puuid);

    /** 쿨다운을 건다. 이미 쿨다운 중이면 false (확인과 설정이 한 번에 일어난다). */
    boolean tryStartClickCooldown(String puuid);

    void markRenewing(String puuid);
}
