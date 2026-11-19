package com.mmrtr.lol.domain.summoner.application.port;

public interface SummonerRenewalPublishPort {

    /** 사용자가 요청한 갱신. USER 우선순위로 처리된다. */
    void publishForRenewal(String puuid, String platformName, String renewalId);
}
