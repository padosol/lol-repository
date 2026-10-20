package com.mmrtr.lol.domain.summoner.readmodel;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;

import java.util.Comparator;
import java.util.List;

/** 소환사 자동완성 한 건. 필드·순서가 lol-server {@code SummonerAutoReadModel} 과 같다. */
public record SummonerAutocompleteReadModel(
        String gameName,
        String tagLine,
        int profileIconId,
        long summonerLevel,
        String tier,
        String rank,
        int leaguePoints
) {

    /** 솔로 랭크 → 자유 랭크 순. lol-server 는 조인 결과의 첫 행이라 어느 큐인지 정해지지 않는다. */
    private static final List<String> QUEUE_PREFERENCE =
            List.of(Queue.RANKED_SOLO_5x5.name(), Queue.RANKED_FLEX_SR.name());

    private static final Comparator<LeagueInfo> BY_QUEUE_PREFERENCE = Comparator
            .comparingInt((LeagueInfo league) -> preferenceOf(league.getQueueType()))
            .thenComparing(LeagueInfo::getQueueType, Comparator.nullsLast(Comparator.naturalOrder()));

    /** 리그 정보가 없으면 tier·rank 는 null, LP 는 0 (lol-server 와 같다). */
    public static SummonerAutocompleteReadModel from(Summoner summoner) {
        LeagueInfo league = summoner.getLeagueInfos().stream()
                .min(BY_QUEUE_PREFERENCE)
                .orElse(null);
        return new SummonerAutocompleteReadModel(
                summoner.getGameIdentity().gameName(),
                summoner.getGameIdentity().tagLine(),
                summoner.getStatusInfo().profileIconId(),
                summoner.getStatusInfo().summonerLevel(),
                league != null ? league.getTier() : null,
                league != null ? league.getRank() : null,
                league != null ? league.getLeaguePoints() : 0
        );
    }

    private static int preferenceOf(String queueType) {
        int index = QUEUE_PREFERENCE.indexOf(queueType);
        return index < 0 ? QUEUE_PREFERENCE.size() : index;
    }
}
