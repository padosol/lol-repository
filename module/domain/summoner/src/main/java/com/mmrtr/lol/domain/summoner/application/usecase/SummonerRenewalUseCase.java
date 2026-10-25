package com.mmrtr.lol.domain.summoner.application.usecase;

import com.mmrtr.lol.domain.summoner.readmodel.SummonerRenewalReadModel;

/** 소환사 전적 갱신 요청·상태 — lol-server {@code SummonerService} 갱신의 이전 (공개 API 재설계 3.1절). */
public interface SummonerRenewalUseCase {

    /**
     * 저장된 플랫폼으로 갱신 메시지를 발행한다. 이미 진행 중이면 SUCCESS, 쿨다운(클릭 10초·Riot 호출 2분)이면 FAILED.
     *
     * @throws com.mmrtr.lol.support.error.CoreException {@code NOT_FOUND_PUUID}, {@code UNKNOWN_SUMMONER_PLATFORM}
     */
    SummonerRenewalReadModel requestRenewal(String puuid);

    /** 진행 중이면 PROGRESS, 아니면 SUCCESS. 저장소를 보지 않는다 (모르는 puuid 도 SUCCESS — lol-server 와 같다). */
    SummonerRenewalReadModel getRenewalStatus(String puuid);
}
