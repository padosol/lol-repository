package lol.mmrtr.lolrepository.domain.match.entity;


import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.riot.dto.match.ParticipantDto;
import lol.mmrtr.lolrepository.riot.dto.match.PerkStyleDto;
import lol.mmrtr.lolrepository.riot.dto.match.PerkStyleSelectionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MatchSummoner {

    private String summonerId;
    private String matchId;
    private String riotIdGameName;
    private String riotIdTagline;
    private String puuid;
    private int profileIcon;
    private String summonerName;
    private int participantId;
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
    private int kills;
    private int assists;
    private int deaths;
    private int doubleKills;
    private int tripleKills;
    private int quadraKills;
    private int pentaKills;
    private int unrealKills;
    private int championTransform;
    private int goldEarned;
    private int goldSpent;
    private int itemsPurchased;
    private int consumablesPurchased;
    private int neutralMinionsKilled;
    private int totalMinionsKilled;
    private int objectivesStolen;
    private int objectivesStolenAssists;
    private int detectorWardsPlaced;
    private int sightWardsBoughtInGame;
    private int visionScore;
    private int visionWardsBoughtInGame;
    private int wardsKilled;
    private int wardsPlaced;
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
    private boolean gameEndedInEarlySurrender;
    private boolean gameEndedInSurrender;
    private boolean teamEarlySurrendered;
    private String teamPosition;
    private int teamId;
    private boolean win;
    private int timePlayed;
    private String individualPosition;
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
    private int item0;
    private int item1;
    private int item2;
    private int item3;
    private int item4;
    private int item5;
    private int item6;
    private int defense;
    private int flex;
    private int offense;
    private int primaryRuneId;
    private String primaryRuneIds;
    private int secondaryRuneId;
    private String secondaryRuneIds;

    public MatchSummoner(){};


    public MatchSummoner(MatchDto matchDto, ParticipantDto participantDto){

        this.summonerId = participantDto.getSummonerId();
        this.matchId = matchDto.getMetadata().getMatchId();
        this.riotIdGameName = participantDto.getRiotIdGameName();
        this.riotIdTagline = participantDto.getRiotIdTagline();
        this.puuid = participantDto.getPuuid();
        this.profileIcon = participantDto.getProfileIcon();
        this.summonerName = participantDto.getSummonerName();
        this.participantId = participantDto.getParticipantId();
        this.champLevel = participantDto.getChampLevel();
        this.championId = participantDto.getChampionId();
        this.championName = participantDto.getChampionName();
        this.lane = participantDto.getLane();
        this.champExperience = participantDto.getChampExperience();
        this.role = participantDto.getRole();
        this.spell1Casts = participantDto.getSpell1Casts();
        this.spell2Casts = participantDto.getSpell2Casts();
        this.spell3Casts = participantDto.getSpell3Casts();
        this.spell4Casts = participantDto.getSpell4Casts();
        this.summoner1Casts = participantDto.getSummoner1Casts();
        this.summoner1Id = participantDto.getSummoner1Id();
        this.summoner2Casts = participantDto.getSummoner2Casts();
        this.summoner2Id = participantDto.getSummoner2Id();
        this.summonerLevel = participantDto.getSummonerLevel();
        this.bountyLevel = participantDto.getBountyLevel();
        this.kills = participantDto.getKills();
        this.assists = participantDto.getAssists();
        this.deaths = participantDto.getDeaths();
        this.doubleKills = participantDto.getDoubleKills();
        this.tripleKills = participantDto.getTripleKills();
        this.quadraKills = participantDto.getQuadraKills();
        this.pentaKills = participantDto.getPentaKills();
        this.unrealKills = participantDto.getUnrealKills();
        this.championTransform = participantDto.getChampionTransform();
        this.goldEarned = participantDto.getGoldEarned();
        this.goldSpent = participantDto.getGoldSpent();
        this.itemsPurchased = participantDto.getItemsPurchased();
        this.consumablesPurchased = participantDto.getConsumablesPurchased();
        this.neutralMinionsKilled = participantDto.getNeutralMinionsKilled();
        this.totalMinionsKilled = participantDto.getTotalMinionsKilled();
        this.objectivesStolen = participantDto.getObjectivesStolen();
        this.objectivesStolenAssists = participantDto.getObjectivesStolenAssists();
        this.detectorWardsPlaced = participantDto.getDetectorWardsPlaced();
        this.sightWardsBoughtInGame = participantDto.getSightWardsBoughtInGame();
        this.visionScore = participantDto.getVisionScore();
        this.visionWardsBoughtInGame = participantDto.getVisionWardsBoughtInGame();
        this.wardsKilled = participantDto.getWardsKilled();
        this.wardsPlaced = participantDto.getWardsPlaced();
        this.baronKills = participantDto.getBaronKills();
        this.dragonKills = participantDto.getDragonKills();
        this.firstBloodAssist = participantDto.isFirstBloodAssist();
        this.firstBloodKill = participantDto.isFirstBloodKill();
        this.firstTowerAssist = participantDto.isFirstTowerAssist();
        this.firstTowerKill = participantDto.isFirstTowerKill();
        this.inhibitorKills = participantDto.getInhibitorKills();
        this.inhibitorTakedowns = participantDto.getInhibitorTakedowns();
        this.inhibitorsLost = participantDto.getInhibitorsLost();
        this.nexusKills = participantDto.getNexusKills();
        this.nexusTakedowns = participantDto.getNexusTakedowns();
        this.nexusLost = participantDto.getNexusLost();
        this.turretKills = participantDto.getTurretKills();
        this.turretTakedowns = participantDto.getTurretTakedowns();
        this.turretsLost = participantDto.getTurretsLost();
        this.gameEndedInEarlySurrender = participantDto.isGameEndedInEarlySurrender();
        this.gameEndedInSurrender = participantDto.isGameEndedInSurrender();
        this.teamEarlySurrendered = participantDto.isTeamEarlySurrendered();
        this.teamPosition = participantDto.getTeamPosition();
        this.teamId = participantDto.getTeamId();
        this.win = participantDto.isWin();
        this.timePlayed = participantDto.getTimePlayed();
        this.individualPosition = participantDto.getIndividualPosition();
        this.magicDamageDealt = participantDto.getMagicDamageDealt();
        this.magicDamageDealtToChampions = participantDto.getMagicDamageDealtToChampions();
        this.magicDamageTaken = participantDto.getMagicDamageTaken();
        this.physicalDamageDealt = participantDto.getPhysicalDamageDealt();
        this.physicalDamageDealtToChampions = participantDto.getPhysicalDamageDealtToChampions();
        this.physicalDamageTaken = participantDto.getPhysicalDamageTaken();
        this.damageDealtToBuildings = participantDto.getDamageDealtToBuildings();
        this.damageDealtToObjectives = participantDto.getDamageDealtToObjectives();
        this.damageDealtToTurrets = participantDto.getDamageDealtToTurrets();
        this.damageSelfMitigated = participantDto.getDamageSelfMitigated();
        this.totalDamageDealt = participantDto.getTotalDamageDealt();
        this.totalDamageDealtToChampions = participantDto.getTotalDamageDealtToChampions();
        this.totalDamageShieldedOnTeammates = participantDto.getTotalDamageShieldedOnTeammates();
        this.totalDamageTaken = participantDto.getTotalDamageTaken();
        this.trueDamageDealt = participantDto.getTrueDamageDealt();
        this.trueDamageDealtToChampions = participantDto.getTrueDamageDealtToChampions();
        this.trueDamageTaken = participantDto.getTrueDamageTaken();
        this.totalHeal = participantDto.getTotalHeal();
        this.totalHealsOnTeammates = participantDto.getTotalHealsOnTeammates();
        this.totalTimeCCDealt = participantDto.getTotalTimeCCDealt();
        this.totalTimeSpentDead = participantDto.getTotalTimeSpentDead();
        this.totalUnitsHealed = participantDto.getTotalUnitsHealed();
        this.timeCCingOthers = participantDto.getTimeCCingOthers();
        this.killingSprees = participantDto.getKillingSprees();
        this.largestCriticalStrike = participantDto.getLargestCriticalStrike();
        this.largestKillingSpree = participantDto.getLargestKillingSpree();
        this.largestMultiKill = participantDto.getLargestMultiKill();
        this.longestTimeSpentLiving = participantDto.getLongestTimeSpentLiving();
        this.allInPings = participantDto.getAllInPings();
        this.assistMePings = participantDto.getAssistMePings();
        this.commandPings = participantDto.getCommandPings();
        this.eligibleForProgression = participantDto.isEligibleForProgression();
        this.enemyMissingPings = participantDto.getEnemyMissingPings();
        this.enemyVisionPings = participantDto.getEnemyVisionPings();
        this.holdPings = participantDto.getHoldPings();
        this.getBackPings = participantDto.getGetBackPings();
        this.needVisionPings = participantDto.getNeedVisionPings();
        this.onMyWayPings = participantDto.getOnMyWayPings();
        this.playerScore0 = participantDto.getPlayerScore0();
        this.playerScore1 = participantDto.getPlayerScore1();
        this.playerScore2 = participantDto.getPlayerScore2();
        this.playerScore3 = participantDto.getPlayerScore3();
        this.playerScore4 = participantDto.getPlayerScore4();
        this.playerScore5 = participantDto.getPlayerScore5();
        this.playerScore6 = participantDto.getPlayerScore6();
        this.playerScore7 = participantDto.getPlayerScore7();
        this.playerScore8 = participantDto.getPlayerScore8();
        this.playerScore9 = participantDto.getPlayerScore9();
        this.playerScore10 = participantDto.getPlayerScore10();
        this.playerScore11 = participantDto.getPlayerScore11();
        this.placement = participantDto.getPlacement();
        this.playerAugment1 = participantDto.getPlayerAugment1();
        this.playerAugment2 = participantDto.getPlayerAugment2();
        this.playerAugment3 = participantDto.getPlayerAugment3();
        this.playerAugment4 = participantDto.getPlayerAugment4();
        this.playerSubteamId = participantDto.getPlayerSubteamId();
        this.pushPings = participantDto.getPushPings();
        this.riotIdName = participantDto.getRiotIdName();
        this.subteamPlacement = participantDto.getSubteamPlacement();
        this.totalAllyJungleMinionsKilled = participantDto.getTotalAllyJungleMinionsKilled();
        this.totalEnemyJungleMinionsKilled = participantDto.getTotalEnemyJungleMinionsKilled();
        this.visionClearedPings = participantDto.getVisionClearedPings();
        this.item0 = participantDto.getItem0();
        this.item1 = participantDto.getItem1();
        this.item2 = participantDto.getItem2();
        this.item3 = participantDto.getItem3();
        this.item4 = participantDto.getItem4();
        this.item5 = participantDto.getItem5();
        this.item6 = participantDto.getItem6();
        this.defense = participantDto.getPerks().getStatPerks().getDefense();
        this.flex = participantDto.getPerks().getStatPerks().getFlex();
        this.offense = participantDto.getPerks().getStatPerks().getOffense();


        List<PerkStyleDto> styles = participantDto.getPerks().getStyles();

        StringBuffer sb = new StringBuffer();
        for (PerkStyleDto style : styles) {
            sb.setLength(0);

            String description = style.getDescription();

            if(description.equalsIgnoreCase("primaryStyle")) {
                this.primaryRuneId = style.getStyle();
                List<PerkStyleSelectionDto> selections = style.getSelections();
                for (PerkStyleSelectionDto selection : selections) {
                    if(sb.length() != 0) {
                        sb.append(",");
                    }
                    int perk = selection.getPerk();
                    sb.append(perk);
                }

                this.primaryRuneIds = sb.toString();
            }


            if(description.equalsIgnoreCase("subStyle")) {
                this.secondaryRuneId = style.getStyle();
                List<PerkStyleSelectionDto> selections = style.getSelections();
                for (PerkStyleSelectionDto selection : selections) {
                    if(sb.length() != 0) {
                        sb.append(",");
                    }
                    int perk = selection.getPerk();
                    sb.append(perk);
                }

                this.secondaryRuneIds = sb.toString();

            }
        }
    }



}
