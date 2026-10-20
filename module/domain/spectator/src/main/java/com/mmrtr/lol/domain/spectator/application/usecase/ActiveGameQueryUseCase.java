package com.mmrtr.lol.domain.spectator.application.usecase;

import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel;

import java.util.Optional;

/** 공개 관전 조회 {@code GET /api/v1/summoners/{puuid}/active-game} — lol-server {@code SpectatorService} 의 이전. */
public interface ActiveGameQueryUseCase {

    /**
     * 저장된 소환사가 진행 중인 게임. 게임 중이 아니거나 Riot 조회가 실패하면 empty (lol-server 와 같이 200 + null).
     *
     * @throws com.mmrtr.lol.support.error.CoreException {@code NOT_FOUND_PUUID}, {@code UNKNOWN_SUMMONER_PLATFORM}
     */
    Optional<ActiveGameReadModel> getActiveGame(String puuid);
}
