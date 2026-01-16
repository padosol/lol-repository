package com.mmrtr.lol.domain.match.entity;

import com.mmrtr.lol.riot.dto.match.ChallengesDto;
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
    private Long id;

    @Column(name = "puuid")
    private String puuid;

    @Column(name = "match_id")
    private String matchId;

    private int assistStreakCount12;
    private int infernalScalePickup;
    private int abilityUses;
    private int acesBefore15Minutes;
    private int alliedJungleMonsterKills;
    private int baronTakedowns;
    private int blastConeOppositeOpponentCount;
    private int bountyGold;
    private int buffsStolen;
    private int completeSupportQuestInTime;
    private double controlWardTimeCoverageInRiverOrEnemyHalf;
    private int controlWardsPlaced;
    private double damagePerMinute;
    private double damageTakenOnTeamPercentage;
    private int dancedWithRiftHerald;
    private int deathsByEnemyChamps;
    private int dodgeSkillShotsSmallWindow;
    private int doubleAces;
    private int dragonTakedowns;
    private int earlyLaningPhaseGoldExpAdvantage;
    private int effectiveHealAndShielding;
    private int elderDragonKillsWithOpposingSoul;
    private int elderDragonMultikills;
    private int enemyChampionImmobilizations;
    private int enemyJungleMonsterKills;
    private int epicMonsterKillsNearEnemyJungler;
    private int epicMonsterKillsWithin30SecondsOfSpawn;
    private int epicMonsterSteals;
    private int epicMonsterStolenWithoutSmite;
    private int firstTurretKilled;
    private double firstTurretKilledTime;
    private int fistBumpParticipation;
    private int flawlessAces;
    private int fullTeamTakedown;
    private double gameLength;
    private int getTakedownsInAllLanesEarlyJungleAsLaner;
    private double goldPerMinute;
    private int hadOpenNexus;
    private int immobilizeAndKillWithAlly;
    private int initialBuffCount;
    private int initialCrabCount;
    private int jungleCsBefore10Minutes;
    private int junglerTakedownsNearDamagedEpicMonster;
    private int kTurretsDestroyedBeforePlatesFall;

    @Comment("K / D / A")
    private double kda;
    private int killAfterHiddenWithAlly;
    private double killParticipation;
    private int killedChampTookFullTeamDamageSurvived;
    private int killingSprees;
    private int killsNearEnemyTurret;
    private int killsOnOtherLanesEarlyJungleAsLaner;
    private int killsOnRecentlyHealedByAramPack;
    private int killsUnderOwnTurret;
    private int killsWithHelpFromEpicMonster;
    private int knockEnemyIntoTeamAndKill;
    private int landSkillShotsEarlyGame;
    private int laneMinionsFirst10Minutes;
    private int laningPhaseGoldExpAdvantage;
    private int legendaryCount;

    @Column(length = 2000)
    private String legendaryItemUsed;

    private int lostAnInhibitor;
    private int maxCsAdvantageOnLaneOpponent;
    private int maxKillDeficit;
    private int maxLevelLeadLaneOpponent;
    private int mejaisFullStackInTime;
    private int moreEnemyJungleThanOpponent;
    private int multiKillOneSpell;
    private int multiTurretRiftHeraldCount;
    private int multikills;
    private int multikillsAfterAggressiveFlash;
    private int outerTurretExecutesBefore10Minutes;
    private int outnumberedKills;
    private int outnumberedNexusKill;
    private int perfectDragonSoulsTaken;
    private int perfectGame;
    private int pickKillWithAlly;
    private int playedChampSelectPosition;
    private int poroExplosions;
    private int quickCleanse;
    private int quickFirstTurret;
    private int quickSoloKills;
    private int riftHeraldTakedowns;
    private int saveAllyFromDeath;
    private int scuttleCrabKills;
    private int skillshotsDodged;
    private int skillshotsHit;
    private int snowballsHit;
    private int soloBaronKills;
    private int soloKills;
    private int stealthWardsPlaced;
    private int survivedSingleDigitHpCount;
    private int survivedThreeImmobilizesInFight;
    private int takedownOnFirstTurret;
    private int takedowns;
    private int takedownsAfterGainingLevelAdvantage;
    private int takedownsBeforeJungleMinionSpawn;
    private int takedownsFirstXMinutes;
    private int takedownsInAlcove;
    private int takedownsInEnemyFountain;
    private int teamBaronKills;

    @Comment("팀 딜량 퍼센트")
    private double teamDamagePercentage;
    private int teamElderDragonKills;
    private int teamRiftHeraldKills;
    private int tookLargeDamageSurvived;
    private int turretPlatesTaken;
    private int turretTakedowns;
    private int turretsTakenWithRiftHerald;
    private int twentyMinionsIn3SecondsCount;
    private int twoWardsOneSweeperCount;
    private int unseenRecalls;
    private double visionScoreAdvantageLaneOpponent;
    private double visionScorePerMinute;
    private int voidMonsterKill;
    private int wardTakedowns;
    private int wardTakedownsBefore20M;
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