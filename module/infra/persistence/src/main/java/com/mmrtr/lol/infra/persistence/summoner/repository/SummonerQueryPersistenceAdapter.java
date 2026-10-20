package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.domain.summoner.application.port.SummonerQueryPort;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel.LeagueRecord;
import com.mmrtr.lol.infra.persistence.league.entity.LeagueSummonerEntity;
import com.mmrtr.lol.infra.persistence.league.repository.LeagueSummonerHistoryJpaRepository;
import com.mmrtr.lol.infra.persistence.league.repository.LeagueSummonerJpaRepository;
import com.mmrtr.lol.infra.persistence.summoner.entity.SummonerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 공개 소환사 조회 어댑터. 조회 조건은 lol-server summoner 모듈의 쿼리와 같다 (차이는 각 메서드에 적는다). */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SummonerQueryPersistenceAdapter implements SummonerQueryPort {

    private static final char LIKE_ESCAPE = '!';

    private final SummonerJpaRepository summonerJpaRepository;
    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;
    private final LeagueSummonerHistoryJpaRepository leagueSummonerHistoryJpaRepository;

    @Override
    public Optional<Summoner> findSummoner(String puuid) {
        return summonerJpaRepository.findById(puuid).map(SummonerEntity::toDomain);
    }

    /** lol-server 는 {@code lower(replace(game_name))}·{@code lower(tag_line)} 비교라 인덱스를 못 탄다. 같은 값인 search_name 으로 찾는다. */
    @Override
    public List<Summoner> findSummonersBySearchName(String platformId, String searchName, int limit) {
        return summonerJpaRepository.findByPlatformIdAndSearchName(platformId, searchName, firstPage(limit))
                .stream()
                .map(SummonerEntity::toDomain)
                .toList();
    }

    @Override
    public List<Summoner> findSummonersBySearchNamePrefix(String platformId, String prefix, int limit) {
        return summonerJpaRepository.findByPlatformIdAndSearchNameStartingWith(platformId, prefix, firstPage(limit))
                .stream()
                .map(SummonerEntity::toDomain)
                .toList();
    }

    @Override
    public List<Summoner> findRankedSummonersBySearchNamePrefix(String platformId, String prefix, int limit) {
        List<SummonerEntity> summoners = summonerJpaRepository.findRankedBySearchNameLike(
                platformId, escapeLike(prefix) + "%", PageRequest.of(0, limit));
        if (summoners.isEmpty()) {
            return List.of();
        }
        Map<String, Set<LeagueInfo>> leaguesByPuuid = leagueSummonerJpaRepository
                .findAllByPuuidIn(summoners.stream().map(SummonerEntity::getPuuid).toList())
                .stream()
                .collect(Collectors.groupingBy(LeagueSummonerEntity::getPuuid,
                        Collectors.mapping(this::toLeagueInfo, Collectors.toSet())));
        return summoners.stream()
                .map(summoner -> summoner.toDomain(leaguesByPuuid.getOrDefault(summoner.getPuuid(), Set.of())))
                .toList();
    }

    @Override
    public List<LeagueRecord> findLeagues(String puuid) {
        return leagueSummonerJpaRepository.findAllByPuuidOrderByIdAsc(puuid).stream()
                .map(league -> new LeagueRecord(league.getQueue(), league.getLeaguePoints(), league.getWins(),
                        league.getLosses(), league.getTier(), league.getRank()))
                .toList();
    }

    /**
     * lol-server 는 현재 league_summoner 행의 id 로 이력을 찾는다. league_summoner 행은 지우지 않으므로 puuid 로 찾아도 같고,
     * puuid 쪽만 인덱스가 있다.
     */
    @Override
    public List<LeagueRecord> findLeagueHistories(String puuid) {
        return leagueSummonerHistoryJpaRepository.findAllByPuuidOrderByCreatedAtDescIdDesc(puuid).stream()
                .map(history -> new LeagueRecord(history.getQueue(), history.getLeaguePoints(), history.getWins(),
                        history.getLosses(), history.getTier(), history.getRank()))
                .toList();
    }

    private PageRequest firstPage(int limit) {
        return PageRequest.of(0, limit, Sort.by("puuid"));
    }

    private LeagueInfo toLeagueInfo(LeagueSummonerEntity league) {
        return LeagueInfo.builder()
                .leagueId(league.getLeagueId())
                .queueType(league.getQueue())
                .tier(league.getTier())
                .rank(league.getRank())
                .leaguePoints(league.getLeaguePoints())
                .wins(league.getWins())
                .losses(league.getLosses())
                .hotStreak(league.isHotStreak())
                .veteran(league.isVeteran())
                .freshBlood(league.isFreshBlood())
                .inactive(league.isInactive())
                .build();
    }

    private String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
