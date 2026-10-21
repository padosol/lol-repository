package com.mmrtr.lol.domain.match.readmodel;

import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ItemSeq;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ParticipantTimeline;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.SkillSeq;
import lombok.Builder;

import java.util.List;

/**
 * 매치 참가자 한 명. 필드·이름·순서가 lol-server {@code ParticipantResponse} 와 같다.
 *
 * <p>저장된 매치에서 읽으면 {@code kda}·{@code teamDamagePercentage}·{@code goldPerMinute}·{@code killParticipation}
 * 은 0 이다 — lol-server 의 DB 조회도 이 값을 읽지 않는다.
 *
 * @param itemSeq  아이템 구매 순서. 타임라인 이벤트가 없으면 null (lol-server 와 같다)
 * @param skillSeq 스킬 레벨업 순서. 타임라인 이벤트가 없으면 null
 */
@Builder(toBuilder = true)
public record ParticipantReadModel(
        int profileIcon,
        String riotIdGameName,
        String riotIdTagline,
        String puuid,
        int summonerLevel,
        String summonerId,
        String tier,
        String tierRank,
        Integer absolutePoints,
        String individualPosition,
        int kills,
        int deaths,
        int assists,
        int champExperience,
        int champLevel,
        int championId,
        String championName,
        int consumablesPurchased,
        int goldEarned,
        Item item,
        int summoner1Id,
        int summoner2Id,
        int itemsPurchased,
        int participantId,
        StatValue statValue,
        Style style,
        int visionScore,
        int totalMinionsKilled,
        int neutralMinionsKilled,
        int totalDamageDealtToChampions,
        int totalDamageTaken,
        int visionWardsBoughtInGame,
        int wardsKilled,
        int wardsPlaced,
        int doubleKills,
        int tripleKills,
        int quadraKills,
        int pentaKills,
        double kda,
        double teamDamagePercentage,
        double goldPerMinute,
        double killParticipation,
        int teamId,
        String teamPosition,
        boolean win,
        int timePlayed,
        int timeCCingOthers,
        String lane,
        String role,
        int placement,
        int playerAugment1,
        int playerAugment2,
        int playerAugment3,
        int playerAugment4,
        List<ItemSeq> itemSeq,
        List<SkillSeq> skillSeq
) {

    public record Item(int item0, int item1, int item2, int item3, int item4, int item5, int item6) {
    }

    /** 룬 스탯 조각. */
    public record StatValue(int defense, int flex, int offense) {
    }

    /** 주·부 룬. */
    public record Style(int primaryStyleId, int primaryPerk0, int primaryPerk1, int primaryPerk2, int primaryPerk3,
                        int subStyleId, int subPerk0, int subPerk1) {
    }

    /** 타임라인이 없으면(null) 그대로 둔다. */
    public ParticipantReadModel withTimeline(ParticipantTimeline timeline) {
        if (timeline == null) {
            return this;
        }
        return toBuilder().itemSeq(timeline.itemSeq()).skillSeq(timeline.skillSeq()).build();
    }
}
