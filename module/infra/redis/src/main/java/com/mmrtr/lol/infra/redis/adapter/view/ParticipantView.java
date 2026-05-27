package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ParticipantView {
    private int profileIcon;
    private String riotIdGameName;
    private String riotIdTagline;
    private String puuid;
    private int summonerLevel;
    private String summonerId;
    private String tier;
    private String tierRank;
    private Integer absolutePoints;

    private String individualPosition;
    private int kills;
    private int deaths;
    private int assists;
    private int champExperience;
    private int champLevel;
    private int championId;
    private String championName;
    private int consumablesPurchased;
    private int goldEarned;
    private ItemView item;
    private int summoner1Id;
    private int summoner2Id;
    private int itemsPurchased;
    private int participantId;
    private StatView statValue;
    private StyleView style;
    private int visionScore;
    private int totalMinionsKilled;
    private int neutralMinionsKilled;
    private int totalDamageDealtToChampions;
    private int totalDamageTaken;
    private int visionWardsBoughtInGame;
    private int wardsKilled;
    private int wardsPlaced;

    private int doubleKills;
    private int tripleKills;
    private int quadraKills;
    private int pentaKills;

    private double kda;
    private double teamDamagePercentage;
    private double goldPerMinute;
    private double killParticipation;

    private int teamId;
    private String teamPosition;
    private boolean win;

    private int timePlayed;
    private int timeCCingOthers;
    private String lane;
    private String role;

    private int placement;
    private int playerAugment1;
    private int playerAugment2;
    private int playerAugment3;
    private int playerAugment4;

    private List<ItemSeqView> itemSeq;
    private List<SkillSeqView> skillSeq;
}
