package com.mmrtr.lol.infra.persistence.match.entity;


import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.ItemValue;
import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.StatValue;
import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.StyleValue;
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
        name = "match_summoner",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_index_match_id_and_puuid",
                        columnNames = {"puuid", "match_id"}
                )
        }
)
public class MatchSummonerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("매치 소환사 ID")
    @Column(name = "match_sumoner_id")
    private Long id;

    @Comment("소환사 고유 식별자")
    @Column(name = "puuid")
    private String puuid;

    @Comment("매치 ID")
    @Column(name = "match_id")
    private String matchId;

    @Comment("소환사 ID")
    private String summonerId;

    // 유저 정보
    @Comment("라이엇 게임 닉네임")
    private String riotIdGameName;
    @Comment("라이엇 태그라인")
    private String riotIdTagline;

    @Comment("프로필 아이콘")
    private int profileIcon;
    @Comment("참가자 ID")
    private int participantId;

    // 챔피언, 룬, 스펠 정보
    @Comment("챔피언 레벨")
    private int champLevel;
    @Comment("챔피언 ID")
    private int championId;
    @Comment("챔피언 이름")
    private String championName;
    @Comment("라인")
    private String lane;
    @Comment("챔피언 경험치")
    private int champExperience;
    @Comment("역할")
    private String role;
    @Comment("Q 스킬 사용 횟수")
    private int spell1Casts;
    @Comment("W 스킬 사용 횟수")
    private int spell2Casts;
    @Comment("E 스킬 사용 횟수")
    private int spell3Casts;
    @Comment("R 스킬 사용 횟수")
    private int spell4Casts;
    @Comment("소환사 주문 1 사용 횟수")
    private int summoner1Casts;
    @Comment("소환사 주문 1 ID")
    private int summoner1Id;
    @Comment("소환사 주문 2 사용 횟수")
    private int summoner2Casts;
    @Comment("소환사 주문 2 ID")
    private int summoner2Id;
    @Comment("소환사 레벨")
    private int summonerLevel;
    @Comment("현상금 레벨")
    private int bountyLevel;

    // 킬 관련
    @Comment("킬 수")
    private int kills;
    @Comment("어시스트 수")
    private int assists;
    @Comment("데스 수")
    private int deaths;
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

    // 케인 전용
    @Comment("챔피언 변신 (케인 전용)")
    private int championTransform;

    // 골드 관련, 아이템 구매
    @Comment("획득 골드")
    private int goldEarned;
    @Comment("사용 골드")
    private int goldSpent;
    @Comment("아이템 구매 횟수")
    private int itemsPurchased;
    @Comment("소모품 구매 횟수")
    private int consumablesPurchased;

    // 미니언 관련
    @Comment("중립 몬스터 처치 수")
    private int neutralMinionsKilled;
    @Comment("총 미니언 처치 수")
    private int totalMinionsKilled;
    @Comment("오브젝트 스틸 수")
    private int objectivesStolen;
    @Comment("오브젝트 스틸 어시스트 수")
    private int objectivesStolenAssists;

    // 와드 관련
    @Comment("감지 와드 설치 수")
    private int detectorWardsPlaced;
    @Comment("시야 와드 구매 수")
    private int sightWardsBoughtInGame;
    @Comment("시야 점수")
    private int visionScore;
    @Comment("제어 와드 구매 수")
    private int visionWardsBoughtInGame;
    @Comment("와드 제거 수")
    private int wardsKilled;
    @Comment("와드 설치 수")
    private int wardsPlaced;

    // 오브젝트 관련
    @Comment("바론 처치 수")
    private int baronKills;
    @Comment("드래곤 처치 수")
    private int dragonKills;
    @Comment("퍼스트 블러드 어시스트 여부")
    private boolean firstBloodAssist;
    @Comment("퍼스트 블러드 킬 여부")
    private boolean firstBloodKill;
    @Comment("첫 타워 파괴 어시스트 여부")
    private boolean firstTowerAssist;
    @Comment("첫 타워 파괴 여부")
    private boolean firstTowerKill;
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
    @Comment("포탑 파괴 수")
    private int turretKills;
    @Comment("포탑 파괴 관여 수")
    private int turretTakedowns;
    @Comment("잃은 포탑 수")
    private int turretsLost;

    // 게임 정보
    @Comment("조기 항복으로 종료 여부")
    private boolean gameEndedInEarlySurrender;
    @Comment("항복으로 종료 여부")
    private boolean gameEndedInSurrender;
    @Comment("팀 조기 항복 여부")
    private boolean teamEarlySurrendered;
    @Comment("팀 포지션")
    private String teamPosition;
    @Comment("팀 ID")
    private int teamId;
    @Comment("승리 여부")
    private boolean win;
    @Comment("플레이 시간 (초)")
    private int timePlayed;
    @Comment("개인 포지션")
    private String individualPosition;

    // 피해, 받은 피해, 회복, CC
    @Comment("마법 피해량")
    private int magicDamageDealt;
    @Comment("챔피언에게 가한 마법 피해량")
    private int magicDamageDealtToChampions;
    @Comment("받은 마법 피해량")
    private int magicDamageTaken;
    @Comment("물리 피해량")
    private int physicalDamageDealt;
    @Comment("챔피언에게 가한 물리 피해량")
    private int physicalDamageDealtToChampions;
    @Comment("받은 물리 피해량")
    private int physicalDamageTaken;
    @Comment("건물에 가한 피해량")
    private int damageDealtToBuildings;
    @Comment("오브젝트에 가한 피해량")
    private int damageDealtToObjectives;
    @Comment("포탑에 가한 피해량")
    private int damageDealtToTurrets;
    @Comment("자체 피해 감소량")
    private int damageSelfMitigated;
    @Comment("총 피해량")
    private int totalDamageDealt;
    @Comment("챔피언에게 가한 총 피해량")
    private int totalDamageDealtToChampions;
    @Comment("아군에게 적용한 총 보호막량")
    private int totalDamageShieldedOnTeammates;
    @Comment("받은 총 피해량")
    private int totalDamageTaken;
    @Comment("고정 피해량")
    private int trueDamageDealt;
    @Comment("챔피언에게 가한 고정 피해량")
    private int trueDamageDealtToChampions;
    @Comment("받은 고정 피해량")
    private int trueDamageTaken;
    @Comment("총 회복량")
    private int totalHeal;
    @Comment("아군 회복량")
    private int totalHealsOnTeammates;
    @Comment("총 CC 적용 시간")
    private int totalTimeCCDealt;
    @Comment("총 사망 시간")
    private int totalTimeSpentDead;
    @Comment("회복한 유닛 수")
    private int totalUnitsHealed;
    @Comment("적에게 CC 적용 시간")
    private int timeCCingOthers;
    @Comment("연속 킬 횟수")
    private int killingSprees;
    @Comment("최대 치명타 피해량")
    private int largestCriticalStrike;
    @Comment("최대 연속 킬 수")
    private int largestKillingSpree;
    @Comment("최대 멀티킬 수")
    private int largestMultiKill;
    @Comment("최장 생존 시간")
    private int longestTimeSpentLiving;

    // 아레나
    @Comment("올인 핑 횟수")
    private int allInPings;
    @Comment("도움 요청 핑 횟수")
    private int assistMePings;
    @Comment("명령 핑 횟수")
    private int commandPings;
    @Comment("진행 자격 여부")
    private boolean eligibleForProgression;
    @Comment("적 실종 핑 횟수")
    private int enemyMissingPings;
    @Comment("적 시야 핑 횟수")
    private int enemyVisionPings;
    @Comment("대기 핑 횟수")
    private int holdPings;
    @Comment("후퇴 핑 횟수")
    private int getBackPings;
    @Comment("시야 필요 핑 횟수")
    private int needVisionPings;
    @Comment("이동 중 핑 횟수")
    private int onMyWayPings;
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
    @Comment("최종 순위 (아레나)")
    private int placement;
    @Comment("플레이어 증강 1")
    private int playerAugment1;
    @Comment("플레이어 증강 2")
    private int playerAugment2;
    @Comment("플레이어 증강 3")
    private int playerAugment3;
    @Comment("플레이어 증강 4")
    private int playerAugment4;
    @Comment("서브팀 ID (아레나)")
    private int playerSubteamId;
    @Comment("밀어 핑 횟수")
    private int pushPings;
    @Comment("서브팀 순위 (아레나)")
    private int subteamPlacement;
    @Comment("아군 정글 몬스터 처치 수")
    private int totalAllyJungleMinionsKilled;
    @Comment("적군 정글 몬스터 처치 수")
    private int totalEnemyJungleMinionsKilled;
    @Comment("시야 제거 핑 횟수")
    private int visionClearedPings;

    @Comment("매치 저장 시점 소환사 티어")
    private String tier;

    @Comment("매치 저장 시점 소환사 디비전")
    @Column(name = "tier_rank")
    private String tierRank;

    @Comment("매치 저장 시점 절대 포인트")
    private Integer absolutePoints;

    @Embedded
    private ItemValue item;

    @Embedded
    private StatValue statValue;

    @Embedded
    private StyleValue styleValue;

    public static MatchSummonerEntity of(MatchEntity match, ParticipantDto participantDto,
                                          String tier, String tierRank, Integer absolutePoints) {
        return MatchSummonerEntity.builder()
                .puuid(participantDto.getPuuid())
                .matchId(match.getMatchId())
                .summonerId(participantDto.getSummonerId())
                .riotIdGameName(participantDto.getRiotIdGameName())
                .riotIdTagline(participantDto.getRiotIdTagline())
                .profileIcon(participantDto.getProfileIcon())
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
                .subteamPlacement(participantDto.getSubteamPlacement())
                .totalAllyJungleMinionsKilled(participantDto.getTotalAllyJungleMinionsKilled())
                .totalEnemyJungleMinionsKilled(participantDto.getTotalEnemyJungleMinionsKilled())
                .visionClearedPings(participantDto.getVisionClearedPings())
                .tier(tier)
                .tierRank(tierRank)
                .absolutePoints(absolutePoints)
                .build();
    }
}
