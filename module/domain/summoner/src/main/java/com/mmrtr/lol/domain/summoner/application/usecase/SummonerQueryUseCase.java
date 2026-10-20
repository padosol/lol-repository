package com.mmrtr.lol.domain.summoner.application.usecase;

import com.mmrtr.lol.domain.summoner.readmodel.SummonerAutocompleteReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerReadModel;

import java.util.List;

/** 공개 소환사 조회 — lol-server summoner 모듈 조회 API 의 이전 (공개 API 재설계 3.1절). */
public interface SummonerQueryUseCase {

    /**
     * 저장된 소환사. Riot 을 호출하지 않는다.
     *
     * @throws com.mmrtr.lol.support.error.CoreException {@code NOT_FOUND_PUUID}
     */
    SummonerReadModel getSummonerByPuuid(String puuid);

    /**
     * Riot ID 로 소환사를 찾는다. 저장된 소환사가 하나로 특정되지 않으면 Riot 에서 찾아 저장한다.
     *
     * @param riotId   {@code gameName-tagLine} (태그 생략 가능 — 이때는 저장된 소환사만 찾는다)
     * @param platform 플랫폼 이름 (kr, na …)
     * @throws com.mmrtr.lol.support.error.CoreException {@code INVALID_INPUT}(모르는 platform·빈 이름),
     *         {@code NOT_FOUND_USER}(Riot 에 없음), {@code LOCK_ACQUISITION_FAILED}(같은 소환사를 조회 중)
     */
    SummonerReadModel getSummonerByRiotId(String riotId, String platform);

    /** 랭크 기록이 있는 소환사 중 이름(태그 포함)이 검색어로 시작하는 소환사. 검색어가 비면 빈 목록. */
    List<SummonerAutocompleteReadModel> autocomplete(String q, String platform);

    /** 현재 랭크와 이력. 저장된 랭크가 없으면 빈 응답 (lol-server 와 같이 404 가 아니다). */
    SummonerLeagueReadModel getLeagues(String puuid);
}
