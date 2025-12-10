package com.mmrtr.lol.domain.match.entity;


import jakarta.persistence.*;
import com.mmrtr.lol.domain.match.entity.id.MatchSummonerId;
import com.mmrtr.lol.domain.match.entity.value.matchsummoner.ItemValue;
import com.mmrtr.lol.domain.match.entity.value.matchsummoner.StatValue;
import com.mmrtr.lol.domain.match.entity.value.matchsummoner.StyleValue;
import com.mmrtr.lol.riot.dto.match.ParticipantDto;
import lombok.*;

@Entity
@Table(name = "match_summoner")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchSummoner {

    @EmbeddedId
    private MatchSummonerId matchSummonerId;

    @MapsId("matchId")                  // EmbeddedId 필드명과 일치해야함
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Match match;

    private String summonerId;

    // match 정보 필요
    // summoner 정보 필요

//    @OneToOne(mappedBy = "matchSummoner")
//    private Challenges challenges;

    // 유저 정보
    private String riotIdGameName;
    private String riotIdTagline;

    private int profileIcon;
    private String summonerName;
    private int participantId;

    // 챔피언, 룬, 스펠 정보
    private int champLevel;
    private int championId;
    private String championName;
    private String lane;
    private int champExperience;
    private String role;
    private int spell1Casts;
    private int spell2Casts;
    private int spell3Casts;
    private int spell4Casts;
    private int summoner1Casts;
    private int summoner1Id;
    private int summoner2Casts;
    private int summoner2Id;
    private int summonerLevel;
    private int bountyLevel;

    // 킬 관련
    private int kills;
    private int assists;
    private int deaths;
    private int doubleKills;
    private int tripleKills;
    private int quadraKills;
    private int pentaKills;
    private int unrealKills;

    // 케인 전용
    private int championTransform;

    // 골드 관련, 아이템 구매
    private int goldEarned;
    private int goldSpent;
    private int itemsPurchased;
    private int consumablesPurchased;

    // 미니언 관련
    private int neutralMinionsKilled;
    private int totalMinionsKilled;
    private int objectivesStolen;
    private int objectivesStolenAssists;

    // 와드 관련
    private int detectorWardsPlaced;
    private int sightWardsBoughtInGame;
    private int visionScore;
    private int visionWardsBoughtInGame;
    private int wardsKilled;
    private int wardsPlaced;

    // 오브젝트 관련
    private int baronKills;
    private int dragonKills;
    private boolean firstBloodAssist;
    private boolean firstBloodKill;
    private boolean firstTowerAssist;
    private boolean firstTowerKill;
    private int inhibitorKills;
    private int inhibitorTakedowns;
    private int inhibitorsLost;
    private int nexusKills;
    private int nexusTakedowns;
    private int nexusLost;
    private int turretKills;
    private int turretTakedowns;
    private int turretsLost;

    // 게임 정보
    private boolean gameEndedInEarlySurrender;
    private boolean gameEndedInSurrender;
    private boolean teamEarlySurrendered;
    private String teamPosition;
    private int teamId;
    private boolean win;
    private int timePlayed;
    private String individualPosition;

    // 피해, 받은 피해, 회복, CC
    private int magicDamageDealt;
    private int magicDamageDealtToChampions;
    private int magicDamageTaken;
    private int physicalDamageDealt;
    private int physicalDamageDealtToChampions;
    private int physicalDamageTaken;
    private int damageDealtToBuildings;
    private int damageDealtToObjectives;
    private int damageDealtToTurrets;
    private int damageSelfMitigated;
    private int totalDamageDealt;
    private int totalDamageDealtToChampions;
    private int totalDamageShieldedOnTeammates;
    private int totalDamageTaken;
    private int trueDamageDealt;
    private int trueDamageDealtToChampions;
    private int trueDamageTaken;
    private int totalHeal;
    private int totalHealsOnTeammates;
    private int totalTimeCCDealt;
    private int totalTimeSpentDead;
    private int totalUnitsHealed;
    private int timeCCingOthers;
    private int killingSprees;
    private int largestCriticalStrike;
    private int largestKillingSpree;
    private int largestMultiKill;
    private int longestTimeSpentLiving;

    // 아레나
    private int allInPings;
    private int assistMePings;
    private int commandPings;
    private boolean eligibleForProgression;
    private int enemyMissingPings;
    private int enemyVisionPings;
    private int holdPings;
    private int getBackPings;
    private int needVisionPings;
    private int onMyWayPings;
    private int playerScore0;
    private int playerScore1;
    private int playerScore2;
    private int playerScore3;
    private int playerScore4;
    private int playerScore5;
    private int playerScore6;
    private int playerScore7;
    private int playerScore8;
    private int playerScore9;
    private int playerScore10;
    private int playerScore11;
    private int placement;
    private int playerAugment1;
    private int playerAugment2;
    private int playerAugment3;
    private int playerAugment4;
    private int playerSubteamId;
    private int pushPings;
    private String riotIdName;
    private int subteamPlacement;
    private int totalAllyJungleMinionsKilled;
    private int totalEnemyJungleMinionsKilled;
    private int visionClearedPings;

    @Embedded
    private ItemValue item;

    @Embedded
    private StatValue statValue;

    @Embedded
    private StyleValue styleValue;

    public static MatchSummoner of(Match match, ParticipantDto participantDto) {
        return MatchSummoner.builder()
                .matchSummonerId(new MatchSummonerId(participantDto.getPuuid(), match.getMatchId()))
                .match(match)
                .summonerId(participantDto.getSummonerId())
                .riotIdGameName(participantDto.getRiotIdGameName())
                .riotIdTagline(participantDto.getRiotIdTagline())
                .profileIcon(participantDto.getProfileIcon())
                .summonerName(participantDto.getSummonerName())
                .participantId(participantDto.getParticipantId())
                .champLevel(participantDto.getChampLevel())
                .championId(participantDto.getChampionId())
                .championName(participantDto.getChampionName())
                .lane(participantDto.getLane())
                .champExperience(participantDto.getChampExperience())
                .role(participantDto.getRole())
                .spell1Casts(participantDto.getSpell1Casts())
                .spell2Casts(participantDto.getSpell2Casts())
                .spell3Casts(participantDto.getSpell3Casts())
                .spell4Casts(participantDto.getSpell4Casts())
                .summoner1Casts(participantDto.getSummoner1Casts())
                .summoner1Id(participantDto.getSummoner1Id())
                .summoner2Casts(participantDto.getSummoner2Casts())
                .summoner2Id(participantDto.getSummoner2Id())
                .summonerLevel(participantDto.getSummonerLevel())
                .bountyLevel(participantDto.getBountyLevel())
                .kills(participantDto.getKills())
                .assists(participantDto.getAssists())
                .deaths(participantDto.getDeaths())
                .doubleKills(participantDto.getDoubleKills())
                .tripleKills(participantDto.getTripleKills())
                .quadraKills(participantDto.getQuadraKills())
                .pentaKills(participantDto.getPentaKills())
                .unrealKills(participantDto.getUnrealKills())
                .championTransform(participantDto.getChampionTransform())
                .goldEarned(participantDto.getGoldEarned())
                .goldSpent(participantDto.getGoldSpent())
                .itemsPurchased(participantDto.getItemsPurchased())
                .consumablesPurchased(participantDto.getConsumablesPurchased())
                .neutralMinionsKilled(participantDto.getNeutralMinionsKilled())
                .totalMinionsKilled(participantDto.getTotalMinionsKilled())
                .objectivesStolen(participantDto.getObjectivesStolen())
                .objectivesStolenAssists(participantDto.getObjectivesStolenAssists())
                .detectorWardsPlaced(participantDto.getDetectorWardsPlaced())
                .sightWardsBoughtInGame(participantDto.getSightWardsBoughtInGame())
                .visionScore(participantDto.getVisionScore())
                .visionWardsBoughtInGame(participantDto.getVisionWardsBoughtInGame())
                .wardsKilled(participantDto.getWardsKilled())
                .wardsPlaced(participantDto.getWardsPlaced())
                .baronKills(participantDto.getBaronKills())
                .dragonKills(participantDto.getDragonKills())
                .firstBloodAssist(participantDto.isFirstBloodAssist())
                .firstBloodKill(participantDto.isFirstBloodKill())
                .firstTowerAssist(participantDto.isFirstTowerAssist())
                .firstTowerKill(participantDto.isFirstTowerKill())
                .inhibitorKills(participantDto.getInhibitorKills())
                .inhibitorTakedowns(participantDto.getInhibitorTakedowns())
                .inhibitorsLost(participantDto.getInhibitorsLost())
                .nexusKills(participantDto.getNexusKills())
                .nexusTakedowns(participantDto.getNexusTakedowns())
                .nexusLost(participantDto.getNexusLost())
                .turretKills(participantDto.getTurretKills())
                .turretTakedowns(participantDto.getTurretTakedowns())
                .turretsLost(participantDto.getTurretsLost())
                .gameEndedInEarlySurrender(participantDto.isGameEndedInEarlySurrender())
                .gameEndedInSurrender(participantDto.isGameEndedInSurrender())
                .teamEarlySurrendered(participantDto.isTeamEarlySurrendered())
                .teamPosition(participantDto.getTeamPosition())
                .teamId(participantDto.getTeamId())
                .win(participantDto.isWin())
                .timePlayed(participantDto.getTimePlayed())
                .individualPosition(participantDto.getIndividualPosition())
                .magicDamageDealt(participantDto.getMagicDamageDealt())
                .magicDamageDealtToChampions(participantDto.getMagicDamageDealtToChampions())
                .magicDamageTaken(participantDto.getMagicDamageTaken())
                .physicalDamageDealt(participantDto.getPhysicalDamageDealt())
                .physicalDamageDealtToChampions(participantDto.getPhysicalDamageDealtToChampions())
                .physicalDamageTaken(participantDto.getPhysicalDamageTaken())
                .damageDealtToBuildings(participantDto.getDamageDealtToBuildings())
                .damageDealtToObjectives(participantDto.getDamageDealtToObjectives())
                .damageDealtToTurrets(participantDto.getDamageDealtToTurrets())
                .damageSelfMitigated(participantDto.getDamageSelfMitigated())
                .totalDamageDealt(participantDto.getTotalDamageDealt())
                .totalDamageDealtToChampions(participantDto.getTotalDamageDealtToChampions())
                .totalDamageShieldedOnTeammates(participantDto.getTotalDamageShieldedOnTeammates())
                .totalDamageTaken(participantDto.getTotalDamageTaken())
                .trueDamageDealt(participantDto.getTrueDamageDealt())
                .trueDamageDealtToChampions(participantDto.getTrueDamageDealtToChampions())
                .trueDamageTaken(participantDto.getTrueDamageTaken())
                .totalHeal(participantDto.getTotalHeal())
                .totalHealsOnTeammates(participantDto.getTotalHealsOnTeammates())
                .totalTimeCCDealt(participantDto.getTotalTimeCCDealt())
                .totalTimeSpentDead(participantDto.getTotalTimeSpentDead())
                .totalUnitsHealed(participantDto.getTotalUnitsHealed())
                .timeCCingOthers(participantDto.getTimeCCingOthers())
                .killingSprees(participantDto.getKillingSprees())
                .largestCriticalStrike(participantDto.getLargestCriticalStrike())
                .largestKillingSpree(participantDto.getLargestKillingSpree())
                .largestMultiKill(participantDto.getLargestMultiKill())
                .longestTimeSpentLiving(participantDto.getLongestTimeSpentLiving())
                .item(new ItemValue(participantDto))
                .statValue(new StatValue(participantDto))
                .styleValue(new StyleValue(participantDto))
                .allInPings(participantDto.getAllInPings())
                .assistMePings(participantDto.getAssistMePings())
                .commandPings(participantDto.getCommandPings())
                .eligibleForProgression(participantDto.isEligibleForProgression())
                .enemyMissingPings(participantDto.getEnemyMissingPings())
                .enemyVisionPings(participantDto.getEnemyVisionPings())
                .holdPings(participantDto.getHoldPings())
                .getBackPings(participantDto.getGetBackPings())
                .needVisionPings(participantDto.getNeedVisionPings())
                .onMyWayPings(participantDto.getOnMyWayPings())
                .playerScore0(participantDto.getPlayerScore0())
                .playerScore1(participantDto.getPlayerScore1())
                .playerScore2(participantDto.getPlayerScore2())
                .playerScore3(participantDto.getPlayerScore3())
                .playerScore4(participantDto.getPlayerScore4())
                .playerScore5(participantDto.getPlayerScore5())
                .playerScore6(participantDto.getPlayerScore6())
                .playerScore7(participantDto.getPlayerScore7())
                .playerScore8(participantDto.getPlayerScore8())
                .playerScore9(participantDto.getPlayerScore9())
                .playerScore10(participantDto.getPlayerScore10())
                .playerScore11(participantDto.getPlayerScore11())
                .placement(participantDto.getPlacement())
                .playerAugment1(participantDto.getPlayerAugment1())
                .playerAugment2(participantDto.getPlayerAugment2())
                .playerAugment3(participantDto.getPlayerAugment3())
                .playerAugment4(participantDto.getPlayerAugment4())
                .playerSubteamId(participantDto.getPlayerSubteamId())
                .pushPings(participantDto.getPushPings())
                .riotIdName(participantDto.getRiotIdName())
                .subteamPlacement(participantDto.getSubteamPlacement())
                .totalAllyJungleMinionsKilled(participantDto.getTotalAllyJungleMinionsKilled())
                .totalEnemyJungleMinionsKilled(participantDto.getTotalEnemyJungleMinionsKilled())
                .visionClearedPings(participantDto.getVisionClearedPings())

                .build();
    }


}
