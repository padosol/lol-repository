package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.summoner.application.port.DuoProfileQueryPort;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.MostChampion;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RecentGame;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RiotId;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.TierInfo;
import com.mmrtr.lol.infra.persistence.league.repository.LeagueSummonerJpaRepository;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** duo 프로필 조회 어댑터. 조회 조건은 lol-server duo {@code RiotAccountResolver} 가 부르던 쿼리와 같다. */
@Component
@RequiredArgsConstructor
public class DuoProfileQueryPersistenceAdapter implements DuoProfileQueryPort {

    private final SummonerJpaRepository summonerJpaRepository;
    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;
    private final MatchSummonerJpaRepository matchSummonerJpaRepository;

    @Override
    public Optional<RiotId> findRiotId(String puuid) {
        return summonerJpaRepository.findById(puuid)
                .map(summoner -> new RiotId(summoner.getGameName(), summoner.getTagLine()));
    }

    @Override
    public Optional<TierInfo> findTier(String puuid, Queue queue) {
        return leagueSummonerJpaRepository.findAllByPuuidAndQueue(puuid, queue.name())
                .map(league -> new TierInfo(league.getTier(), league.getRank(), league.getLeaguePoints()));
    }

    @Override
    public List<MostChampion> findMostChampions(String puuid, Queue queue, int limit) {
        return matchSummonerJpaRepository.findChampionPlayCounts(puuid, queue.getQueueId(), PageRequest.of(0, limit))
                .stream()
                .map(row -> new MostChampion(row.getChampionId(), row.getChampionName(),
                        row.getPlayCount(), row.getWins(), row.getLosses()))
                .toList();
    }

    @Override
    public List<RecentGame> findRecentGames(String puuid, Queue queue, int size) {
        return matchSummonerJpaRepository.findRecentGames(puuid, queue.getQueueId(), PageRequest.of(0, size))
                .stream()
                .map(row -> new RecentGame(row.getWin(), row.getChampionId(), row.getChampionName()))
                .toList();
    }
}
