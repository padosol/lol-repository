package com.mmrtr.lol.domain.summoner.application.usecase;

import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel;

public interface DuoProfileQueryUseCase {

    /**
     * 솔로 랭크 티어·모스트 챔피언·최근 게임 요약.
     *
     * @throws com.mmrtr.lol.support.error.CoreException {@code NOT_FOUND_PUUID} — 색인된 적 없는 소환사
     */
    DuoProfileReadModel getDuoProfile(String puuid);
}
