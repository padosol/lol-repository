package com.mmrtr.lol.infra.persistence.match.entity;

import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.ItemValue;
import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.PerkValue;
import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.ArenaValue;
import com.mmrtr.lol.infra.riot.dto.match.ParticipantDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "match_participant",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_index_match_participant_puuid_match_id",
                        columnNames = {"puuid", "match_id"}
                )
        }
)
public class MatchParticipantEntity {

    // ==================== PK ====================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("매치 참가자 ID")
    @Column(name = "match_participant_id")
    private Long id;

    // ==================== Identity ====================
    @Comment("소환사 고유 식별자")
    @Column(name = "puuid")
    private String puuid;

    @Comment("매치 ID")
    @Column(name = "match_id")
    private String matchId;

    @Comment("소환사 ID")
    private String summonerId;

    @Comment("라이엇 게임 닉네임")
    private String riotIdGameName;

    @Comment("라이엇 태그라인")
    private String riotIdTagline;

    @Comment("소환사 이름")
    private String summonerName;

    @Comment("소환사 레벨")
    private int summonerLevel;

    @Comment("프로필 아이콘")
    private int profileIcon;

    @Comment("참가자 ID")
    private int participantId;

    @Comment("팀 ID")
    private int teamId;

    // ==================== Champion ====================
    @Comment("챔피언 ID")
    private int championId;

    @Comment("챔피언 이름")
    private String championName;

    @Comment("챔피언 레벨")
    private int champLevel;

    @Comment("챔피언 경험치")
    private int champExperience;

    @Comment("챔피언 변신 (케인 전용)")
    private int championTransform;

    // ==================== Position ====================
    @Comment("개인 포지션")
    private String individualPosition;

    @Comment("라인")
    private String lane;

    @Comment("역할")
    private String role;

    @Comment("팀 포지션")
    private String teamPosition;

    // ==================== Arena ====================
    @Embedded
    private ArenaValue arena;

    // ==================== Items ====================
    @Embedded
    private ItemValue item;

    // ==================== Spells ====================
    @Comment("소환사 주문 1 ID")
    private int summoner1Id;

    @Comment("소환사 주문 1 사용 횟수")
    private int summoner1Casts;

    @Comment("소환사 주문 2 ID")
    private int summoner2Id;

    @Comment("소환사 주문 2 사용 횟수")
    private int summoner2Casts;

    @Comment("Q 스킬 사용 횟수")
    private int spell1Casts;

    @Comment("W 스킬 사용 횟수")
    private int spell2Casts;

    @Comment("E 스킬 사용 횟수")
    private int spell3Casts;

    @Comment("R 스킬 사용 횟수")
    private int spell4Casts;

    // ==================== Combat ====================
    @Comment("킬 수")
    private int kills;

    @Comment("데스 수")
    private int deaths;

    @Comment("어시스트 수")
    private int assists;

    @Comment("더블킬 수")
    private int doubleKills;

    @Comment("트리플킬 수")
    private int tripleKills;

    @Comment("쿼드라킬 수")
    private int quadraKills;

    @Comment("펜타킬 수")
    private int pentaKills;

    @Comment("비현실적 킬 수")
    private int unrealKills;

    @Comment("연속 킬 횟수")
    private int killingSprees;

    @Comment("최대 연속 킬 수")
    private int largestKillingSpree;

    @Comment("최대 멀티킬 수")
    private int largestMultiKill;

    @Comment("최장 생존 시간")
    private int longestTimeSpentLiving;

    @Comment("플레이 시간 (초)")
    private int timePlayed;

    @Comment("퍼스트 블러드 킬 여부")
    private boolean firstBloodKill;

    @Comment("퍼스트 블러드 어시스트 여부")
    private boolean firstBloodAssist;

    @Comment("첫 타워 파괴 여부")
    private boolean firstTowerKill;

    @Comment("첫 타워 파괴 어시스트 여부")
    private boolean firstTowerAssist;

    // ==================== Damage Dealt ====================
    @Comment("총 피해량")
    private int totalDamageDealt;

    @Comment("물리 피해량")
    private int physicalDamageDealt;

    @Comment("마법 피해량")
    private int magicDamageDealt;

    @Comment("고정 피해량")
    private int trueDamageDealt;

    @Comment("챔피언에게 가한 총 피해량")
    private int totalDamageDealtToChampions;

    @Comment("챔피언에게 가한 물리 피해량")
    private int physicalDamageDealtToChampions;

    @Comment("챔피언에게 가한 마법 피해량")
    private int magicDamageDealtToChampions;

    @Comment("챔피언에게 가한 고정 피해량")
    private int trueDamageDealtToChampions;

    @Comment("최대 치명타 피해량")
    private int largestCriticalStrike;

    // ==================== Damage Taken ====================
    @Comment("받은 총 피해량")
    private int totalDamageTaken;

    @Comment("받은 물리 피해량")
    private int physicalDamageTaken;

    @Comment("받은 마법 피해량")
    private int magicDamageTaken;

    @Comment("받은 고정 피해량")
    private int trueDamageTaken;

    @Comment("자체 피해 감소량")
    private int damageSelfMitigated;

    // ==================== Damage to Objectives ====================
    @Comment("건물에 가한 피해량")
    private int damageDealtToBuildings;

    @Comment("오브젝트에 가한 피해량")
    private int damageDealtToObjectives;

    @Comment("포탑에 가한 피해량")
    private int damageDealtToTurrets;

    @Comment("에픽 몬스터에 가한 피해량")
    private int damageDealtToEpicMonsters;

    // ==================== Healing ====================
    @Comment("총 회복량")
    private int totalHeal;

    @Comment("아군 회복량")
    private int totalHealsOnTeammates;

    @Comment("회복한 유닛 수")
    private int totalUnitsHealed;

    @Comment("아군에게 적용한 총 보호막량")
    private int totalDamageShieldedOnTeammates;

    // ==================== CS/Gold ====================
    @Comment("총 미니언 처치 수")
    private int totalMinionsKilled;

    @Comment("중립 몬스터 처치 수")
    private int neutralMinionsKilled;

    @Comment("아군 정글 몬스터 처치 수")
    private int totalAllyJungleMinionsKilled;

    @Comment("적군 정글 몬스터 처치 수")
    private int totalEnemyJungleMinionsKilled;

    @Comment("획득 골드")
    private int goldEarned;

    @Comment("사용 골드")
    private int goldSpent;

    // ==================== Vision ====================
    @Comment("시야 점수")
    private int visionScore;

    @Comment("와드 설치 수")
    private int wardsPlaced;

    @Comment("와드 제거 수")
    private int wardsKilled;

    @Comment("제어 와드 구매 수")
    private int visionWardsBoughtInGame;

    @Comment("시야 와드 구매 수")
    private int sightWardsBoughtInGame;

    @Comment("감지 와드 설치 수")
    private int detectorWardsPlaced;

    // ==================== Objectives ====================
    @Comment("바론 처치 수")
    private int baronKills;

    @Comment("드래곤 처치 수")
    private int dragonKills;

    @Comment("포탑 파괴 수")
    private int turretKills;

    @Comment("포탑 파괴 관여 수")
    private int turretTakedowns;

    @Comment("잃은 포탑 수")
    private int turretsLost;

    @Comment("억제기 파괴 수")
    private int inhibitorKills;

    @Comment("억제기 파괴 관여 수")
    private int inhibitorTakedowns;

    @Comment("잃은 억제기 수")
    private int inhibitorsLost;

    @Comment("넥서스 파괴 수")
    private int nexusKills;

    @Comment("넥서스 파괴 관여 수")
    private int nexusTakedowns;

    @Comment("넥서스 잃음 여부")
    private int nexusLost;

    @Comment("오브젝트 스틸 수")
    private int objectivesStolen;

    @Comment("오브젝트 스틸 어시스트 수")
    private int objectivesStolenAssists;

    // ==================== CC ====================
    @Comment("적에게 CC 적용 시간")
    private int timeCCingOthers;

    @Comment("총 CC 적용 시간")
    private int totalTimeCCDealt;

    @Comment("총 사망 시간")
    private int totalTimeSpentDead;

    // ==================== Pings ====================
    @Comment("올인 핑 횟수")
    private int allInPings;

    @Comment("도움 요청 핑 횟수")
    private int assistMePings;

    @Comment("기본 핑 횟수")
    private int basicPings;

    @Comment("명령 핑 횟수")
    private int commandPings;

    @Comment("위험 핑 횟수")
    private int dangerPings;

    @Comment("적 실종 핑 횟수")
    private int enemyMissingPings;

    @Comment("적 시야 핑 횟수")
    private int enemyVisionPings;

    @Comment("후퇴 핑 횟수")
    private int getBackPings;

    @Comment("대기 핑 횟수")
    private int holdPings;

    @Comment("시야 필요 핑 횟수")
    private int needVisionPings;

    @Comment("이동 중 핑 횟수")
    private int onMyWayPings;

    @Comment("밀어 핑 횟수")
    private int pushPings;

    @Comment("퇴각 핑 횟수")
    private int retreatPings;

    @Comment("시야 제거 핑 횟수")
    private int visionClearedPings;

    // ==================== Surrender ====================
    @Comment("조기 항복으로 종료 여부")
    private boolean gameEndedInEarlySurrender;

    @Comment("항복으로 종료 여부")
    private boolean gameEndedInSurrender;

    @Comment("팀 조기 항복 여부")
    private boolean teamEarlySurrendered;

    // ==================== Perks ====================
    @Embedded
    private PerkValue perk;

    // ==================== Misc ====================
    @Comment("진행 자격 여부")
    private boolean eligibleForProgression;

    @Comment("승리 여부")
    private boolean win;

    @Comment("역할 귀속 아이템")
    private int roleBoundItem;

    @Comment("현상금 레벨")
    private int bountyLevel;

    @Comment("플레이어 점수 0")
    private int playerScore0;

    @Comment("플레이어 점수 1")
    private int playerScore1;

    @Comment("플레이어 점수 2")
    private int playerScore2;

    @Comment("플레이어 점수 3")
    private int playerScore3;

    @Comment("플레이어 점수 4")
    private int playerScore4;

    @Comment("플레이어 점수 5")
    private int playerScore5;

    @Comment("플레이어 점수 6")
    private int playerScore6;

    @Comment("플레이어 점수 7")
    private int playerScore7;

    @Comment("플레이어 점수 8")
    private int playerScore8;

    @Comment("플레이어 점수 9")
    private int playerScore9;

    @Comment("플레이어 점수 10")
    private int playerScore10;

    @Comment("플레이어 점수 11")
    private int playerScore11;

    // ==================== Tier (snapshot at save time) ====================
    @Comment("매치 저장 시점 소환사 티어")
    private String tier;

    @Comment("매치 저장 시점 소환사 디비전")
    @Column(name = "tier_rank")
    private String tierRank;

    @Comment("매치 저장 시점 절대 포인트")
    private Integer absolutePoints;

    // ==================== Static Factory Method ====================
    public static MatchParticipantEntity of(MatchEntity match, ParticipantDto participantDto,
                                             String tier, String tierRank, Integer absolutePoints) {
        return MatchParticipantEntity.builder()
                // Identity
                .puuid(participantDto.getPuuid())
                .matchId(match.getMatchId())
                .summonerId(participantDto.getSummonerId())
                .riotIdGameName(participantDto.getRiotIdGameName())
                .riotIdTagline(participantDto.getRiotIdTagline())
                .summonerName(participantDto.getSummonerName())
                .summonerLevel(participantDto.getSummonerLevel())
                .profileIcon(participantDto.getProfileIcon())
                .participantId(participantDto.getParticipantId())
                .teamId(participantDto.getTeamId())
                // Champion
                .championId(participantDto.getChampionId())
                .championName(participantDto.getChampionName())
                .champLevel(participantDto.getChampLevel())
                .champExperience(participantDto.getChampExperience())
                .championTransform(participantDto.getChampionTransform())
                // Position
                .individualPosition(participantDto.getIndividualPosition())
                .lane(participantDto.getLane())
                .role(participantDto.getRole())
                .teamPosition(participantDto.getTeamPosition())
                // Arena
                .arena(new ArenaValue(participantDto))
                // Items
                .item(new ItemValue(participantDto))
                // Spells
                .summoner1Id(participantDto.getSummoner1Id())
                .summoner1Casts(participantDto.getSummoner1Casts())
                .summoner2Id(participantDto.getSummoner2Id())
                .summoner2Casts(participantDto.getSummoner2Casts())
                .spell1Casts(participantDto.getSpell1Casts())
                .spell2Casts(participantDto.getSpell2Casts())
                .spell3Casts(participantDto.getSpell3Casts())
                .spell4Casts(participantDto.getSpell4Casts())
                // Combat
                .kills(participantDto.getKills())
                .deaths(participantDto.getDeaths())
                .assists(participantDto.getAssists())
                .doubleKills(participantDto.getDoubleKills())
                .tripleKills(participantDto.getTripleKills())
                .quadraKills(participantDto.getQuadraKills())
                .pentaKills(participantDto.getPentaKills())
                .unrealKills(participantDto.getUnrealKills())
                .killingSprees(participantDto.getKillingSprees())
                .largestKillingSpree(participantDto.getLargestKillingSpree())
                .largestMultiKill(participantDto.getLargestMultiKill())
                .longestTimeSpentLiving(participantDto.getLongestTimeSpentLiving())
                .timePlayed(participantDto.getTimePlayed())
                .firstBloodKill(participantDto.isFirstBloodKill())
                .firstBloodAssist(participantDto.isFirstBloodAssist())
                .firstTowerKill(participantDto.isFirstTowerKill())
                .firstTowerAssist(participantDto.isFirstTowerAssist())
                // Damage Dealt
                .totalDamageDealt(participantDto.getTotalDamageDealt())
                .physicalDamageDealt(participantDto.getPhysicalDamageDealt())
                .magicDamageDealt(participantDto.getMagicDamageDealt())
                .trueDamageDealt(participantDto.getTrueDamageDealt())
                .totalDamageDealtToChampions(participantDto.getTotalDamageDealtToChampions())
                .physicalDamageDealtToChampions(participantDto.getPhysicalDamageDealtToChampions())
                .magicDamageDealtToChampions(participantDto.getMagicDamageDealtToChampions())
                .trueDamageDealtToChampions(participantDto.getTrueDamageDealtToChampions())
                .largestCriticalStrike(participantDto.getLargestCriticalStrike())
                // Damage Taken
                .totalDamageTaken(participantDto.getTotalDamageTaken())
                .physicalDamageTaken(participantDto.getPhysicalDamageTaken())
                .magicDamageTaken(participantDto.getMagicDamageTaken())
                .trueDamageTaken(participantDto.getTrueDamageTaken())
                .damageSelfMitigated(participantDto.getDamageSelfMitigated())
                // Damage to Objectives
                .damageDealtToBuildings(participantDto.getDamageDealtToBuildings())
                .damageDealtToObjectives(participantDto.getDamageDealtToObjectives())
                .damageDealtToTurrets(participantDto.getDamageDealtToTurrets())
                .damageDealtToEpicMonsters(participantDto.getDamageDealtToEpicMonsters())
                // Healing
                .totalHeal(participantDto.getTotalHeal())
                .totalHealsOnTeammates(participantDto.getTotalHealsOnTeammates())
                .totalUnitsHealed(participantDto.getTotalUnitsHealed())
                .totalDamageShieldedOnTeammates(participantDto.getTotalDamageShieldedOnTeammates())
                // CS/Gold
                .totalMinionsKilled(participantDto.getTotalMinionsKilled())
                .neutralMinionsKilled(participantDto.getNeutralMinionsKilled())
                .totalAllyJungleMinionsKilled(participantDto.getTotalAllyJungleMinionsKilled())
                .totalEnemyJungleMinionsKilled(participantDto.getTotalEnemyJungleMinionsKilled())
                .goldEarned(participantDto.getGoldEarned())
                .goldSpent(participantDto.getGoldSpent())
                // Vision
                .visionScore(participantDto.getVisionScore())
                .wardsPlaced(participantDto.getWardsPlaced())
                .wardsKilled(participantDto.getWardsKilled())
                .visionWardsBoughtInGame(participantDto.getVisionWardsBoughtInGame())
                .sightWardsBoughtInGame(participantDto.getSightWardsBoughtInGame())
                .detectorWardsPlaced(participantDto.getDetectorWardsPlaced())
                // Objectives
                .baronKills(participantDto.getBaronKills())
                .dragonKills(participantDto.getDragonKills())
                .turretKills(participantDto.getTurretKills())
                .turretTakedowns(participantDto.getTurretTakedowns())
                .turretsLost(participantDto.getTurretsLost())
                .inhibitorKills(participantDto.getInhibitorKills())
                .inhibitorTakedowns(participantDto.getInhibitorTakedowns())
                .inhibitorsLost(participantDto.getInhibitorsLost())
                .nexusKills(participantDto.getNexusKills())
                .nexusTakedowns(participantDto.getNexusTakedowns())
                .nexusLost(participantDto.getNexusLost())
                .objectivesStolen(participantDto.getObjectivesStolen())
                .objectivesStolenAssists(participantDto.getObjectivesStolenAssists())
                // CC
                .timeCCingOthers(participantDto.getTimeCCingOthers())
                .totalTimeCCDealt(participantDto.getTotalTimeCCDealt())
                .totalTimeSpentDead(participantDto.getTotalTimeSpentDead())
                // Pings
                .allInPings(participantDto.getAllInPings())
                .assistMePings(participantDto.getAssistMePings())
                .basicPings(participantDto.getBasicPings())
                .commandPings(participantDto.getCommandPings())
                .dangerPings(participantDto.getDangerPings())
                .enemyMissingPings(participantDto.getEnemyMissingPings())
                .enemyVisionPings(participantDto.getEnemyVisionPings())
                .getBackPings(participantDto.getGetBackPings())
                .holdPings(participantDto.getHoldPings())
                .needVisionPings(participantDto.getNeedVisionPings())
                .onMyWayPings(participantDto.getOnMyWayPings())
                .pushPings(participantDto.getPushPings())
                .retreatPings(participantDto.getRetreatPings())
                .visionClearedPings(participantDto.getVisionClearedPings())
                // Surrender
                .gameEndedInEarlySurrender(participantDto.isGameEndedInEarlySurrender())
                .gameEndedInSurrender(participantDto.isGameEndedInSurrender())
                .teamEarlySurrendered(participantDto.isTeamEarlySurrendered())
                // Perks
                .perk(new PerkValue(participantDto))
                // Misc
                .eligibleForProgression(participantDto.isEligibleForProgression())
                .win(participantDto.isWin())
                .roleBoundItem(participantDto.getRoleBoundItem())
                .bountyLevel(participantDto.getBountyLevel())
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
                // Tier
                .tier(tier)
                .tierRank(tierRank)
                .absolutePoints(absolutePoints)
                .build();
    }
}
