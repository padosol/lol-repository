package com.mmrtr.lol.infra.persistence.match.entity;

import com.mmrtr.lol.infra.riot.dto.match.ChallengesDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "challenges",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_index_puuid_and_match_id",
                        columnNames = {"puuid", "match_id"}
                )
        }
)
public class ChallengesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("챌린지 ID")
    private Long id;

    @Comment("소환사 고유 식별자")
    private String puuid;

    @Comment("매치 ID")
    private String matchId;

    @Comment("12 어시스트 연속 횟수")
    private int assistStreakCount12;
    @Comment("지옥불 비늘 획득 수")
    private int infernalScalePickup;
    @Comment("스킬 사용 횟수")
    private int abilityUses;
    @Comment("15분 전 에이스 횟수")
    private int acesBefore15Minutes;
    @Comment("아군 정글 몬스터 처치 수")
    private int alliedJungleMonsterKills;
    @Comment("바론 처치 관여 수")
    private int baronTakedowns;
    @Comment("폭발 열매 반대편 사용 횟수")
    private int blastConeOppositeOpponentCount;
    @Comment("현상금 골드")
    private int bountyGold;
    @Comment("버프 스틸 횟수")
    private int buffsStolen;
    @Comment("서포터 퀘스트 시간 내 완료")
    private int completeSupportQuestInTime;
    @Comment("강 또는 적 진영 제어 와드 커버 시간")
    private double controlWardTimeCoverageInRiverOrEnemyHalf;
    @Comment("제어 와드 설치 수")
    private int controlWardsPlaced;
    @Comment("분당 피해량")
    private double damagePerMinute;
    @Comment("팀 내 피해 분담 비율")
    private double damageTakenOnTeamPercentage;
    @Comment("전령과 함께 춤추기")
    private int dancedWithRiftHerald;
    @Comment("적 챔피언에 의한 사망 수")
    private int deathsByEnemyChamps;
    @Comment("짧은 시간 내 스킬샷 회피 수")
    private int dodgeSkillShotsSmallWindow;
    @Comment("더블 에이스 횟수")
    private int doubleAces;
    @Comment("드래곤 처치 관여 수")
    private int dragonTakedowns;
    @Comment("초반 라이닝 골드/경험치 우위")
    private int earlyLaningPhaseGoldExpAdvantage;
    @Comment("유효 힐 및 보호막량")
    private int effectiveHealAndShielding;
    @Comment("상대 영혼 보유 시 장로 드래곤 처치 수")
    private int elderDragonKillsWithOpposingSoul;
    @Comment("장로 드래곤 멀티킬 수")
    private int elderDragonMultikills;
    @Comment("적 챔피언 행동 불능 횟수")
    private int enemyChampionImmobilizations;
    @Comment("적 정글 몬스터 처치 수")
    private int enemyJungleMonsterKills;
    @Comment("적 정글러 근처 에픽 몬스터 처치 수")
    private int epicMonsterKillsNearEnemyJungler;
    @Comment("스폰 후 30초 내 에픽 몬스터 처치 수")
    private int epicMonsterKillsWithin30SecondsOfSpawn;
    @Comment("에픽 몬스터 스틸 수")
    private int epicMonsterSteals;
    @Comment("강타 없이 에픽 몬스터 스틸 수")
    private int epicMonsterStolenWithoutSmite;
    @Comment("첫 포탑 파괴 여부")
    private int firstTurretKilled;
    @Comment("첫 포탑 파괴 시간")
    private double firstTurretKilledTime;
    @Comment("주먹 인사 참여 횟수")
    private int fistBumpParticipation;
    @Comment("완벽한 에이스 횟수")
    private int flawlessAces;
    @Comment("팀 전체 처치 관여 수")
    private int fullTeamTakedown;
    @Comment("게임 길이")
    private double gameLength;
    @Comment("라이너로서 모든 라인 처치 관여 수")
    private int getTakedownsInAllLanesEarlyJungleAsLaner;
    @Comment("분당 골드")
    private double goldPerMinute;
    @Comment("열린 넥서스 경험 여부")
    private int hadOpenNexus;
    @Comment("아군과 함께 행동 불능 후 처치 수")
    private int immobilizeAndKillWithAlly;
    @Comment("초기 버프 획득 수")
    private int initialBuffCount;
    @Comment("초기 바위게 획득 수")
    private int initialCrabCount;
    @Comment("10분 전 정글 CS")
    private int jungleCsBefore10Minutes;
    @Comment("피해 입은 에픽 몬스터 근처 정글러 처치 관여 수")
    private int junglerTakedownsNearDamagedEpicMonster;
    @Comment("방벽 낙하 전 파괴한 포탑 수")
    private int kTurretsDestroyedBeforePlatesFall;

    @Comment("K / D / A")
    private double kda;
    @Comment("아군과 은신 후 처치 수")
    private int killAfterHiddenWithAlly;
    @Comment("킬 관여율")
    private double killParticipation;
    @Comment("팀 전체 피해를 받고 생존한 챔피언 처치 수")
    private int killedChampTookFullTeamDamageSurvived;
    @Comment("연속 킬 횟수")
    private int killingSprees;
    @Comment("적 포탑 근처 처치 수")
    private int killsNearEnemyTurret;
    @Comment("라이너로서 다른 라인 초반 처치 수")
    private int killsOnOtherLanesEarlyJungleAsLaner;
    @Comment("ARAM 힐팩으로 최근 회복된 적 처치 수")
    private int killsOnRecentlyHealedByAramPack;
    @Comment("아군 포탑 아래 처치 수")
    private int killsUnderOwnTurret;
    @Comment("에픽 몬스터 도움 처치 수")
    private int killsWithHelpFromEpicMonster;
    @Comment("적을 팀으로 밀어 처치 수")
    private int knockEnemyIntoTeamAndKill;
    @Comment("초반 스킬샷 적중 수")
    private int landSkillShotsEarlyGame;
    @Comment("10분간 라인 미니언 수")
    private int laneMinionsFirst10Minutes;
    @Comment("라이닝 페이즈 골드/경험치 우위")
    private int laningPhaseGoldExpAdvantage;
    @Comment("전설급 달성 횟수")
    private int legendaryCount;

    @Comment("사용한 전설급 아이템 목록")
    @Column(length = 2000)
    private String legendaryItemUsed;

    @Comment("잃은 억제기 수")
    private int lostAnInhibitor;
    @Comment("라인 상대 대비 최대 CS 우위")
    private int maxCsAdvantageOnLaneOpponent;
    @Comment("최대 킬 적자")
    private int maxKillDeficit;
    @Comment("라인 상대 대비 최대 레벨 우위")
    private int maxLevelLeadLaneOpponent;
    @Comment("메자이 풀스택 달성 시간 내 여부")
    private int mejaisFullStackInTime;
    @Comment("상대보다 적 정글 더 많이 파밍 여부")
    private int moreEnemyJungleThanOpponent;
    @Comment("한 스킬로 멀티킬 수")
    private int multiKillOneSpell;
    @Comment("전령으로 여러 포탑 파괴 횟수")
    private int multiTurretRiftHeraldCount;
    @Comment("멀티킬 수")
    private int multikills;
    @Comment("공격적 점멸 후 멀티킬 수")
    private int multikillsAfterAggressiveFlash;
    @Comment("10분 전 외곽 포탑 자체 파괴 수")
    private int outerTurretExecutesBefore10Minutes;
    @Comment("수적 열세 처치 수")
    private int outnumberedKills;
    @Comment("수적 열세 넥서스 파괴 수")
    private int outnumberedNexusKill;
    @Comment("완벽한 드래곤 영혼 획득 수")
    private int perfectDragonSoulsTaken;
    @Comment("퍼펙트 게임 여부")
    private int perfectGame;
    @Comment("아군과 함께 픽킬 수")
    private int pickKillWithAlly;
    @Comment("챔피언 셀렉트 포지션 플레이 여부")
    private int playedChampSelectPosition;
    @Comment("포로 폭발 횟수")
    private int poroExplosions;
    @Comment("빠른 정화 횟수")
    private int quickCleanse;
    @Comment("빠른 첫 포탑 파괴")
    private int quickFirstTurret;
    @Comment("빠른 솔로킬 수")
    private int quickSoloKills;
    @Comment("전령 처치 관여 수")
    private int riftHeraldTakedowns;
    @Comment("아군 사망 구출 횟수")
    private int saveAllyFromDeath;
    @Comment("바위게 처치 수")
    private int scuttleCrabKills;
    @Comment("스킬샷 회피 수")
    private int skillshotsDodged;
    @Comment("스킬샷 적중 수")
    private int skillshotsHit;
    @Comment("눈덩이 적중 수")
    private int snowballsHit;
    @Comment("솔로 바론 처치 수")
    private int soloBaronKills;
    @Comment("솔로킬 수")
    private int soloKills;
    @Comment("은신 와드 설치 수")
    private int stealthWardsPlaced;
    @Comment("한 자릿수 HP로 생존 횟수")
    private int survivedSingleDigitHpCount;
    @Comment("전투 중 3회 행동 불능 생존 횟수")
    private int survivedThreeImmobilizesInFight;
    @Comment("첫 포탑 처치 관여")
    private int takedownOnFirstTurret;
    @Comment("처치 관여 수")
    private int takedowns;
    @Comment("레벨 우위 후 처치 관여 수")
    private int takedownsAfterGainingLevelAdvantage;
    @Comment("정글 몬스터 스폰 전 처치 관여 수")
    private int takedownsBeforeJungleMinionSpawn;
    @Comment("초반 X분 내 처치 관여 수")
    private int takedownsFirstXMinutes;
    @Comment("골목에서 처치 관여 수")
    private int takedownsInAlcove;
    @Comment("적 샘에서 처치 관여 수")
    private int takedownsInEnemyFountain;
    @Comment("팀 바론 처치 수")
    private int teamBaronKills;

    @Comment("팀 딜량 퍼센트")
    private double teamDamagePercentage;
    @Comment("팀 장로 드래곤 처치 수")
    private int teamElderDragonKills;
    @Comment("팀 전령 처치 수")
    private int teamRiftHeraldKills;
    @Comment("큰 피해를 받고 생존 횟수")
    private int tookLargeDamageSurvived;
    @Comment("포탑 방벽 획득 수")
    private int turretPlatesTaken;
    @Comment("포탑 처치 관여 수")
    private int turretTakedowns;
    @Comment("전령으로 파괴한 포탑 수")
    private int turretsTakenWithRiftHerald;
    @Comment("3초 내 미니언 20마리 처치 횟수")
    private int twentyMinionsIn3SecondsCount;
    @Comment("와드 2개 스위퍼 1개 사용 횟수")
    private int twoWardsOneSweeperCount;
    @Comment("들키지 않은 귀환 횟수")
    private int unseenRecalls;
    @Comment("라인 상대 대비 시야 점수 우위")
    private double visionScoreAdvantageLaneOpponent;
    @Comment("분당 시야 점수")
    private double visionScorePerMinute;
    @Comment("공허 몬스터 처치 수")
    private int voidMonsterKill;
    @Comment("와드 제거 수")
    private int wardTakedowns;
    @Comment("20분 전 와드 제거 수")
    private int wardTakedownsBefore20M;
    @Comment("와드 보호 수")
    private int wardsGuarded;


    public static ChallengesEntity of(MatchSummonerEntity matchSummoner, ChallengesDto challengesDto) {

        StringBuffer sb = new StringBuffer();
        for (Integer integer : challengesDto.getLegendaryItemUsed()) {
            if(!sb.isEmpty()) {
                sb.append(",");
            }

            sb.append(integer);
        }

        return ChallengesEntity.builder()
                .puuid(matchSummoner.getPuuid())
                .matchId(matchSummoner.getMatchId())
                .assistStreakCount12(challengesDto.getAssistStreakCount12())
                .infernalScalePickup(challengesDto.getInfernalScalePickup())
                .abilityUses(challengesDto.getAbilityUses())
                .acesBefore15Minutes(challengesDto.getAcesBefore15Minutes())
                .alliedJungleMonsterKills(challengesDto.getAlliedJungleMonsterKills())
                .baronTakedowns(challengesDto.getBaronTakedowns())
                .blastConeOppositeOpponentCount(challengesDto.getBlastConeOppositeOpponentCount())
                .bountyGold(challengesDto.getBountyGold())
                .buffsStolen(challengesDto.getBuffsStolen())
                .completeSupportQuestInTime(challengesDto.getCompleteSupportQuestInTime())
                .controlWardTimeCoverageInRiverOrEnemyHalf(challengesDto.getControlWardTimeCoverageInRiverOrEnemyHalf())
                .controlWardsPlaced(challengesDto.getControlWardsPlaced())
                .damagePerMinute(challengesDto.getDamagePerMinute())
                .damageTakenOnTeamPercentage(challengesDto.getDamageTakenOnTeamPercentage())
                .dancedWithRiftHerald(challengesDto.getDancedWithRiftHerald())
                .deathsByEnemyChamps(challengesDto.getDeathsByEnemyChamps())
                .dodgeSkillShotsSmallWindow(challengesDto.getDodgeSkillShotsSmallWindow())
                .doubleAces(challengesDto.getDoubleAces())
                .dragonTakedowns(challengesDto.getDragonTakedowns())
                .earlyLaningPhaseGoldExpAdvantage(challengesDto.getEarlyLaningPhaseGoldExpAdvantage())
                .effectiveHealAndShielding(challengesDto.getEffectiveHealAndShielding())
                .elderDragonKillsWithOpposingSoul(challengesDto.getElderDragonKillsWithOpposingSoul())
                .elderDragonMultikills(challengesDto.getElderDragonMultikills())
                .enemyChampionImmobilizations(challengesDto.getEnemyChampionImmobilizations())
                .enemyJungleMonsterKills(challengesDto.getEnemyJungleMonsterKills())
                .epicMonsterKillsNearEnemyJungler(challengesDto.getEpicMonsterKillsNearEnemyJungler())
                .epicMonsterKillsWithin30SecondsOfSpawn(challengesDto.getEpicMonsterKillsWithin30SecondsOfSpawn())
                .epicMonsterSteals(challengesDto.getEpicMonsterSteals())
                .epicMonsterStolenWithoutSmite(challengesDto.getEpicMonsterStolenWithoutSmite())
                .firstTurretKilled(challengesDto.getFirstTurretKilled())
                .firstTurretKilledTime(challengesDto.getFirstTurretKilledTime())
                .fistBumpParticipation(challengesDto.getFistBumpParticipation())
                .flawlessAces(challengesDto.getFlawlessAces())
                .fullTeamTakedown(challengesDto.getFullTeamTakedown())
                .gameLength(challengesDto.getGameLength())
                .getTakedownsInAllLanesEarlyJungleAsLaner(challengesDto.getGetTakedownsInAllLanesEarlyJungleAsLaner())
                .goldPerMinute(challengesDto.getGoldPerMinute())
                .hadOpenNexus(challengesDto.getHadOpenNexus())
                .immobilizeAndKillWithAlly(challengesDto.getImmobilizeAndKillWithAlly())
                .initialBuffCount(challengesDto.getInitialBuffCount())
                .initialCrabCount(challengesDto.getInitialCrabCount())
                .jungleCsBefore10Minutes(challengesDto.getJungleCsBefore10Minutes())
                .junglerTakedownsNearDamagedEpicMonster(challengesDto.getJunglerTakedownsNearDamagedEpicMonster())
                .kTurretsDestroyedBeforePlatesFall(challengesDto.getKTurretsDestroyedBeforePlatesFall())
                .kda(challengesDto.getKda())
                .killAfterHiddenWithAlly(challengesDto.getKillAfterHiddenWithAlly())
                .killParticipation(challengesDto.getKillParticipation())
                .killedChampTookFullTeamDamageSurvived(challengesDto.getKilledChampTookFullTeamDamageSurvived())
                .killingSprees(challengesDto.getKillingSprees())
                .killsNearEnemyTurret(challengesDto.getKillsNearEnemyTurret())
                .killsOnOtherLanesEarlyJungleAsLaner(challengesDto.getKillsOnOtherLanesEarlyJungleAsLaner())
                .killsOnRecentlyHealedByAramPack(challengesDto.getKillsOnRecentlyHealedByAramPack())
                .killsUnderOwnTurret(challengesDto.getKillsUnderOwnTurret())
                .killsWithHelpFromEpicMonster(challengesDto.getKillsWithHelpFromEpicMonster())
                .knockEnemyIntoTeamAndKill(challengesDto.getKnockEnemyIntoTeamAndKill())
                .landSkillShotsEarlyGame(challengesDto.getLandSkillShotsEarlyGame())
                .laneMinionsFirst10Minutes(challengesDto.getLaneMinionsFirst10Minutes())
                .laningPhaseGoldExpAdvantage(challengesDto.getLaningPhaseGoldExpAdvantage())
                .legendaryCount(challengesDto.getLegendaryCount())
                .legendaryItemUsed(sb.toString())
                .lostAnInhibitor(challengesDto.getLostAnInhibitor())
                .maxCsAdvantageOnLaneOpponent(challengesDto.getMaxCsAdvantageOnLaneOpponent())
                .maxKillDeficit(challengesDto.getMaxKillDeficit())
                .maxLevelLeadLaneOpponent(challengesDto.getMaxLevelLeadLaneOpponent())
                .mejaisFullStackInTime(challengesDto.getMejaisFullStackInTime())
                .moreEnemyJungleThanOpponent(challengesDto.getMoreEnemyJungleThanOpponent())
                .multiKillOneSpell(challengesDto.getMultiKillOneSpell())
                .multiTurretRiftHeraldCount(challengesDto.getMultiTurretRiftHeraldCount())
                .multikills(challengesDto.getMultikills())
                .multikillsAfterAggressiveFlash(challengesDto.getMultikillsAfterAggressiveFlash())
                .outerTurretExecutesBefore10Minutes(challengesDto.getOuterTurretExecutesBefore10Minutes())
                .outnumberedKills(challengesDto.getOutnumberedKills())
                .outnumberedNexusKill(challengesDto.getOutnumberedNexusKill())
                .perfectDragonSoulsTaken(challengesDto.getPerfectDragonSoulsTaken())
                .perfectGame(challengesDto.getPerfectGame())
                .pickKillWithAlly(challengesDto.getPickKillWithAlly())
                .playedChampSelectPosition(challengesDto.getPlayedChampSelectPosition())
                .poroExplosions(challengesDto.getPoroExplosions())
                .quickCleanse(challengesDto.getQuickCleanse())
                .quickFirstTurret(challengesDto.getQuickFirstTurret())
                .quickSoloKills(challengesDto.getQuickSoloKills())
                .riftHeraldTakedowns(challengesDto.getRiftHeraldTakedowns())
                .saveAllyFromDeath(challengesDto.getSaveAllyFromDeath())
                .scuttleCrabKills(challengesDto.getScuttleCrabKills())
                .skillshotsDodged(challengesDto.getSkillshotsDodged())
                .skillshotsHit(challengesDto.getSkillshotsHit())
                .snowballsHit(challengesDto.getSnowballsHit())
                .soloBaronKills(challengesDto.getSoloBaronKills())
                .soloKills(challengesDto.getSoloKills())
                .stealthWardsPlaced(challengesDto.getStealthWardsPlaced())
                .survivedSingleDigitHpCount(challengesDto.getSurvivedSingleDigitHpCount())
                .survivedThreeImmobilizesInFight(challengesDto.getSurvivedThreeImmobilizesInFight())
                .takedownOnFirstTurret(challengesDto.getTakedownOnFirstTurret())
                .takedowns(challengesDto.getTakedowns())
                .takedownsAfterGainingLevelAdvantage(challengesDto.getTakedownsAfterGainingLevelAdvantage())
                .takedownsBeforeJungleMinionSpawn(challengesDto.getTakedownsBeforeJungleMinionSpawn())
                .takedownsFirstXMinutes(challengesDto.getTakedownsFirstXMinutes())
                .takedownsInAlcove(challengesDto.getTakedownsInAlcove())
                .takedownsInEnemyFountain(challengesDto.getTakedownsInEnemyFountain())
                .teamBaronKills(challengesDto.getTeamBaronKills())
                .teamDamagePercentage(challengesDto.getTeamDamagePercentage())
                .teamElderDragonKills(challengesDto.getTeamElderDragonKills())
                .teamRiftHeraldKills(challengesDto.getTeamRiftHeraldKills())
                .tookLargeDamageSurvived(challengesDto.getTookLargeDamageSurvived())
                .turretPlatesTaken(challengesDto.getTurretPlatesTaken())
                .turretTakedowns(challengesDto.getTurretTakedowns())
                .turretsTakenWithRiftHerald(challengesDto.getTurretsTakenWithRiftHerald())
                .twentyMinionsIn3SecondsCount(challengesDto.getTwentyMinionsIn3SecondsCount())
                .twoWardsOneSweeperCount(challengesDto.getTwoWardsOneSweeperCount())
                .unseenRecalls(challengesDto.getUnseenRecalls())
                .visionScoreAdvantageLaneOpponent(challengesDto.getVisionScoreAdvantageLaneOpponent())
                .visionScorePerMinute(challengesDto.getVisionScorePerMinute())
                .voidMonsterKill(challengesDto.getVoidMonsterKill())
                .wardTakedowns(challengesDto.getWardTakedowns())
                .wardTakedownsBefore20M(challengesDto.getWardTakedownsBefore20M())
                .wardsGuarded(challengesDto.getWardsGuarded())
                .build();

    }
}
