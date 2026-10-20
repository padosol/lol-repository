package com.mmrtr.lol.domain.summoner.application.port;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.MostChampion;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RecentGame;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RiotId;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.TierInfo;

import java.util.List;
import java.util.Optional;

/** duo 프로필 조회. 소환사·리그·매치 테이블을 읽는 조회 전용 포트. */
public interface DuoProfileQueryPort {

    /** 소환사 검색으로 색인된 소환사의 Riot ID. 색인된 적 없으면 빈 값. */
    Optional<RiotId> findRiotId(String puuid);

    Optional<TierInfo> findTier(String puuid, Queue queue);

    /** 해당 큐에서 많이 플레이한 챔피언 순 (판 수가 같으면 championId 오름차순). */
    List<MostChampion> findMostChampions(String puuid, Queue queue, int limit);

    /** 해당 큐의 최근 게임, 게임 종료 시각 최신순. */
    List<RecentGame> findRecentGames(String puuid, Queue queue, int size);
}
