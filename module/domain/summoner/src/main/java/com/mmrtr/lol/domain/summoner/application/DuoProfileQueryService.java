package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.summoner.application.port.DuoProfileQueryPort;
import com.mmrtr.lol.domain.summoner.application.usecase.DuoProfileQueryUseCase;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RecentGameSummary;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RiotId;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.TierInfo;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * lol-server duo {@code RiotAccountResolver#lookupAllStats} 의 이전 — summoner·league·match UseCase 4회 호출을 한 번에.
 * 조회 조건은 lol-server 와 같고, 모스트 챔피언은 시즌을 거르지 않는다 (lol-server 는 season=null 로 호출해 예외가 난다).
 */
@Service
@RequiredArgsConstructor
public class DuoProfileQueryService implements DuoProfileQueryUseCase {

    static final Queue QUEUE = Queue.RANKED_SOLO_5x5;
    static final int MOST_CHAMPION_LIMIT = 3;
    static final int RECENT_GAME_SIZE = 20;

    private final DuoProfileQueryPort duoProfileQueryPort;

    @Override
    public DuoProfileReadModel getDuoProfile(String puuid) {
        // 리그·매치 조회는 DB 만 보므로, 색인 안 된 소환사는 실제 티어와 무관하게 언랭으로 보인다 → 언랭과 구분한다
        RiotId riotId = duoProfileQueryPort.findRiotId(puuid)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_PUUID));
        return new DuoProfileReadModel(
                riotId.gameName(),
                riotId.tagLine(),
                duoProfileQueryPort.findTier(puuid, QUEUE).orElse(TierInfo.UNRANKED),
                duoProfileQueryPort.findMostChampions(puuid, QUEUE, MOST_CHAMPION_LIMIT),
                RecentGameSummary.from(duoProfileQueryPort.findRecentGames(puuid, QUEUE, RECENT_GAME_SIZE))
        );
    }
}
