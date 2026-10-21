package com.mmrtr.lol.domain.spectator.application.port;

import com.mmrtr.lol.domain.spectator.domain.SpectatorTarget;

import java.util.Optional;

/** 저장된 소환사의 플랫폼·갱신 일시. summoner 테이블을 읽는다 (spectator → summoner 모듈 의존 없음). */
public interface SpectatorTargetPort {

    Optional<SpectatorTarget> findTarget(String puuid);
}
