package com.mmrtr.lol.tools.riotmock.world;

import com.mmrtr.lol.domain.match.readmodel.BanDto;
import com.mmrtr.lol.domain.match.readmodel.ChallengesDto;
import com.mmrtr.lol.domain.match.readmodel.FeatDto;
import com.mmrtr.lol.domain.match.readmodel.FeatsDto;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ObjectiveDto;
import com.mmrtr.lol.domain.match.readmodel.ObjectivesDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.PerkStatsDto;
import com.mmrtr.lol.domain.match.readmodel.PerkStyleDto;
import com.mmrtr.lol.domain.match.readmodel.PerkStyleSelectionDto;
import com.mmrtr.lol.domain.match.readmodel.PerksDto;
import com.mmrtr.lol.domain.match.readmodel.TeamDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import java.util.ArrayList;
import java.util.List;

/**
 * 매치 ID 와 참가자 puuid 목록만으로 매치·타임라인 DTO 를 만든다. 난수를 쓰지 않고 ID 에서 뽑은 값만 쓴다.
 * 앱의 저장·캐시 경로가 널 검사 없이 읽는 하위 객체(challenges, perks, objectives, participantFrames)는 모두 채운다.
 */
public class MatchFactory {

    static final int QUEUE_SOLO = 420;
    private static final int MAP_SUMMONERS_RIFT = 11;
    private static final long GAME_SECONDS = LabIds.GAME_MILLIS / 1000;

    private final String gameVersion;
    private final TimelineFactory timelines = new TimelineFactory();

    public MatchFactory(String gameVersion) {
        this.gameVersion = gameVersion;
    }

    public MatchDto match(MatchRef ref, List<String> participantPuuids) {
        long gameId = gameIdOf(ref);
        boolean blueWins = (LabIds.mix64(gameId) & 1) == 0;

        MetadataDto metadata = new MetadataDto();
        metadata.setDataVersion("2");
        metadata.setMatchId(ref.matchId());
        metadata.setParticipants(new ArrayList<>(participantPuuids));

        List<ParticipantDto> participants = new ArrayList<>();
        for (int slot = 0; slot < participantPuuids.size(); slot++) {
            participants.add(participant(gameId, slot, participantPuuids.get(slot), blueWins));
        }

        InfoDto info = new InfoDto();
        info.setEndOfGameResult("GameComplete");
        info.setGameCreation(ref.startMillis() - 20_000);
        info.setGameStartTimestamp(ref.startMillis());
        info.setGameEndTimestamp(ref.endMillis());
        info.setGameDuration(GAME_SECONDS);
        info.setGameId(gameId);
        info.setGameMode("CLASSIC");
        info.setGameName("lab-" + gameId);
        info.setGameType("MATCHED_GAME");
        info.setGameVersion(gameVersion);
        info.setMapId(MAP_SUMMONERS_RIFT);
        info.setPlatformId("KR");
        info.setQueueId(QUEUE_SOLO);
        info.setTournamentCode("");
        info.setParticipants(participants);
        info.setTeams(List.of(team(100, gameId, blueWins), team(200, gameId, !blueWins)));

        MatchDto match = new MatchDto();
        match.setMetadata(metadata);
        match.setInfo(info);
        return match;
    }

    public TimelineDto timeline(MatchRef ref, List<String> participantPuuids) {
        return timelines.timeline(ref, participantPuuids, gameIdOf(ref), championsOf(gameIdOf(ref)));
    }

    static long gameIdOf(MatchRef ref) {
        return Long.parseLong(ref.matchId().substring("KR_".length()));
    }

    /** 게임 안에서 겹치지 않는 챔피언 10개(20개 목록에서 3칸씩 건너뛴다). */
    static int[] championsOf(long gameId) {
        int base = (int) ((LabIds.mix64(gameId) >>> 1) % LabCatalog.CHAMPION_IDS.length);
        int[] indexes = new int[10];
        for (int slot = 0; slot < 10; slot++) {
            indexes[slot] = Math.floorMod(base + slot * 3, LabCatalog.CHAMPION_IDS.length);
        }
        return indexes;
    }

    private ParticipantDto participant(long gameId, int slot, String puuid, boolean blueWins) {
        long s = LabIds.mix64(gameId * 16 + slot);
        int champIndex = championsOf(gameId)[slot];
        int teamId = slot < 5 ? 100 : 200;
        boolean win = (teamId == 100) == blueWins;
        int pos = slot % 5;
        boolean lab = puuid.startsWith("lab");
        String number = LabIds.numberPartOf(puuid);

        int kills = pick(s, 0, 12);
        int deaths = pick(s, 8, 9);
        int assists = pick(s, 16, 16);
        int cs = 120 + pick(s, 24, 160);
        int gold = 8_000 + kills * 300 + assists * 150 + cs * 20;
        int damage = 9_000 + pick(s, 32, 25_000);

        ParticipantDto p = new ParticipantDto();
        p.setParticipantId(slot + 1);
        p.setPuuid(puuid);
        p.setSummonerId("sid-" + puuid);
        p.setSummonerName("");
        p.setRiotIdGameName((lab ? "lab" : "npc") + number);
        p.setRiotIdTagline(lab ? "LAB" : "NPC");
        p.setSummonerLevel(30 + pick(s, 40, 400));
        p.setProfileIcon(1 + pick(s, 44, 30));
        p.setTeamId(teamId);
        p.setWin(win);
        p.setChampionId(LabCatalog.CHAMPION_IDS[champIndex]);
        p.setChampionName(LabCatalog.CHAMPION_NAMES[champIndex]);
        p.setChampLevel(13 + pick(s, 48, 6));
        p.setChampExperience(14_000 + pick(s, 52, 6_000));
        p.setTeamPosition(LabCatalog.POSITIONS[pos]);
        p.setIndividualPosition(LabCatalog.POSITIONS[pos]);
        p.setLane(LabCatalog.LANES[pos]);
        p.setRole(LabCatalog.ROLES[pos]);
        p.setKills(kills);
        p.setDeaths(deaths);
        p.setAssists(assists);
        p.setLargestKillingSpree(Math.min(kills, 5));
        p.setLargestMultiKill(Math.min(kills / 3, 3));
        p.setGoldEarned(gold);
        p.setGoldSpent(gold - 400);
        p.setTotalMinionsKilled(cs);
        p.setNeutralMinionsKilled(pos == 1 ? 80 : 6);
        p.setTotalDamageDealt(damage * 2);
        p.setTotalDamageDealtToChampions(damage);
        p.setPhysicalDamageDealtToChampions(damage / 2);
        p.setMagicDamageDealtToChampions(damage / 2 - 500);
        p.setTrueDamageDealtToChampions(500);
        p.setTotalDamageTaken(8_000 + pick(s, 20, 15_000));
        p.setVisionScore(10 + pick(s, 28, 50));
        p.setWardsPlaced(6 + pick(s, 36, 20));
        p.setTimePlayed((int) GAME_SECONDS);
        p.setSummoner1Id(pos == 1 ? LabCatalog.SMITE : LabCatalog.FLASH);
        p.setSummoner2Id(LabCatalog.SUMMONER_SPELL_IDS[(int) ((s >>> 14) & 0xFFFF) % LabCatalog.SUMMONER_SPELL_IDS.length]);
        p.setItem0(item(s, 0));
        p.setItem1(item(s, 1));
        p.setItem2(item(s, 2));
        p.setItem3(item(s, 3));
        p.setItem4(item(s, 4));
        p.setItem5(item(s, 5));
        p.setItem6(LabCatalog.TRINKET_ID);
        p.setPerks(perks(s));
        p.setChallenges(challenges(kills, deaths, assists, damage, gold, cs));
        return p;
    }

    private static int pick(long seed, int shift, int bound) {
        return (int) ((seed >>> shift) & 0xFF) * 31 % bound;
    }

    private static int item(long seed, int index) {
        return LabCatalog.ITEM_IDS[(int) (((seed >>> (index * 5)) & 0xFFFF) + index * 7) % LabCatalog.ITEM_IDS.length];
    }

    private static PerksDto perks(long seed) {
        int primary = (int) ((seed >>> 3) & 0xFFFF) % LabCatalog.STYLE_IDS.length;
        int secondary = (primary + 1 + (int) ((seed >>> 19) & 0xFF) % (LabCatalog.STYLE_IDS.length - 1)) % LabCatalog.STYLE_IDS.length;

        PerkStatsDto stats = new PerkStatsDto();
        stats.setDefense(LabCatalog.STAT_DEFENSE);
        stats.setFlex(LabCatalog.STAT_FLEX);
        stats.setOffense(LabCatalog.STAT_OFFENSE);

        PerksDto perks = new PerksDto();
        perks.setStatPerks(stats);
        perks.setStyles(List.of(
                style("primaryStyle", LabCatalog.STYLE_IDS[primary], LabCatalog.STYLE_PERKS[primary], 4),
                style("subStyle", LabCatalog.STYLE_IDS[secondary], LabCatalog.STYLE_PERKS[secondary], 2)));
        return perks;
    }

    private static PerkStyleDto style(String description, int styleId, int[] perkIds, int count) {
        List<PerkStyleSelectionDto> selections = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PerkStyleSelectionDto selection = new PerkStyleSelectionDto();
            selection.setPerk(perkIds[i]);
            selections.add(selection);
        }
        PerkStyleDto style = new PerkStyleDto();
        style.setDescription(description);
        style.setStyle(styleId);
        style.setSelections(selections);
        return style;
    }

    private static ChallengesDto challenges(int kills, int deaths, int assists, int damage, int gold, int cs) {
        ChallengesDto c = new ChallengesDto();
        c.setKda((kills + assists) / (double) Math.max(1, deaths));
        c.setTakedowns(kills + assists);
        c.setKillParticipation(Math.min(1.0, (kills + assists) / 40.0));
        c.setTeamDamagePercentage(Math.min(1.0, damage / 100_000.0));
        c.setDamagePerMinute(damage / 30.0);
        c.setGoldPerMinute(gold / 30.0);
        c.setVisionScorePerMinute(1.0);
        c.setGameLength(GAME_SECONDS);
        c.setSoloKills(Math.min(kills, 2));
        c.setLaneMinionsFirst10Minutes(cs / 4);
        return c;
    }

    private static TeamDto team(int teamId, long gameId, boolean win) {
        long s = LabIds.mix64(gameId * 3 + teamId);
        int towers = win ? 7 + pick(s, 0, 5) : pick(s, 0, 5);
        ObjectivesDto objectives = new ObjectivesDto();
        objectives.setAtakhan(objective(false, 0));
        objectives.setBaron(objective(win, win ? 1 : 0));
        objectives.setChampion(objective(win, 20 + (win ? 8 : 0)));
        objectives.setDragon(objective(win, win ? 3 : 1));
        objectives.setHorde(objective(win, win ? 4 : 2));
        objectives.setInhibitor(objective(win, win ? 2 : 0));
        objectives.setRiftHerald(objective(win, win ? 1 : 0));
        objectives.setTower(objective(win, towers));

        List<BanDto> bans = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BanDto ban = new BanDto();
            ban.setChampionId(LabCatalog.CHAMPION_IDS[Math.floorMod((int) (s >>> 8) + i * 7 + teamId, LabCatalog.CHAMPION_IDS.length)]);
            ban.setPickTurn(i + 1);
            bans.add(ban);
        }

        FeatsDto feats = new FeatsDto();
        feats.setEpicMonsterKill(feat(win ? 1 : 0));
        feats.setFirstBlood(feat(win ? 1 : 0));
        feats.setFirstTurret(feat(win ? 1 : 0));

        TeamDto team = new TeamDto();
        team.setTeamId(teamId);
        team.setWin(win);
        team.setObjectives(objectives);
        team.setBans(bans);
        team.setFeats(feats);
        return team;
    }

    private static ObjectiveDto objective(boolean first, int kills) {
        ObjectiveDto objective = new ObjectiveDto();
        objective.setFirst(first);
        objective.setKills(kills);
        return objective;
    }

    private static FeatDto feat(int state) {
        FeatDto feat = new FeatDto();
        feat.setFeatState(state);
        return feat;
    }
}
