package com.mmrtr.lol.infra.redis.adapter.view;

import com.mmrtr.lol.domain.match.readmodel.ChallengesDto;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ObjectiveDto;
import com.mmrtr.lol.domain.match.readmodel.ObjectivesDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.PerksDto;
import com.mmrtr.lol.domain.match.readmodel.PerkStatsDto;
import com.mmrtr.lol.domain.match.readmodel.PerkStyleDto;
import com.mmrtr.lol.domain.match.readmodel.PerkStyleSelectionDto;
import com.mmrtr.lol.domain.match.readmodel.TeamDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.EventsTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.FramesTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Riot {@link MatchDto} + {@link TimelineDto} 를 lol-server 의 GameReadModel 형태({@link MatchCacheView})로 변환한다.
 * <p>
 * DB 전용 필드(tier/averageTier 등)와 풀 timeline 전용 필드(goldTimeline/timestamps)는 채우지 않는다.
 * itemSeq 는 {@code ITEM_PURCHASED}, skillSeq 는 {@code SKILL_LEVEL_UP} 이벤트만 추출한다 (DB 리스트 경로와 동일).
 */
@Component
public class MatchCacheViewMapper {

    private static final int BLUE_TEAM_ID = 100;
    private static final int RED_TEAM_ID = 200;
    private static final String ITEM_PURCHASED = "ITEM_PURCHASED";
    private static final String SKILL_LEVEL_UP = "SKILL_LEVEL_UP";

    public MatchCacheView toView(MatchDto match, TimelineDto timeline) {
        InfoDto info = match.getInfo();
        MetadataDto metadata = match.getMetadata();

        ItemSkillSeq seq = extractSeq(timeline);

        return MatchCacheView.builder()
                .gameInfoData(toGameInfo(metadata, info))
                .participantData(toParticipants(info, seq))
                .teamInfoData(toTeam(info))
                .build();
    }

    private GameInfoView toGameInfo(MetadataDto metadata, InfoDto info) {
        return GameInfoView.builder()
                .matchId(metadata == null ? null : metadata.getMatchId())
                .dataVersion(metadata == null ? null : metadata.getDataVersion())
                .gameCreation(info.getGameCreation())
                .gameDuration(info.getGameDuration())
                .gameEndTimestamp(info.getGameEndTimestamp())
                .gameStartTimestamp(info.getGameStartTimestamp())
                .gameMode(info.getGameMode())
                .gameType(info.getGameType())
                .gameVersion(info.getGameVersion())
                .mapId(info.getMapId())
                .platformId(info.getPlatformId())
                .queueId(info.getQueueId())
                .tournamentCode(info.getTournamentCode())
                .build();
    }

    private List<ParticipantView> toParticipants(InfoDto info, ItemSkillSeq seq) {
        List<ParticipantView> result = new ArrayList<>();
        if (info.getParticipants() == null) {
            return result;
        }
        for (ParticipantDto p : info.getParticipants()) {
            result.add(toParticipant(p, seq));
        }
        return result;
    }

    private ParticipantView toParticipant(ParticipantDto p, ItemSkillSeq seq) {
        ChallengesDto c = p.getChallenges();
        return baseBuilder(p)
                .item(toItem(p))
                .style(toStyle(p.getPerks()))
                .statValue(toStat(p.getPerks()))
                .kda(c != null ? c.getKda() : computeKda(p))
                .teamDamagePercentage(c != null ? c.getTeamDamagePercentage() : 0.0)
                .goldPerMinute(c != null ? c.getGoldPerMinute() : 0.0)
                .killParticipation(c != null ? c.getKillParticipation() : 0.0)
                .itemSeq(seq.items.getOrDefault(p.getParticipantId(), List.of()))
                .skillSeq(seq.skills.getOrDefault(p.getParticipantId(), List.of()))
                .build();
    }

    private ParticipantView.ParticipantViewBuilder baseBuilder(ParticipantDto p) {
        return ParticipantView.builder()
                .profileIcon(p.getProfileIcon())
                .riotIdGameName(p.getRiotIdGameName())
                .riotIdTagline(p.getRiotIdTagline())
                .puuid(p.getPuuid())
                .summonerLevel(p.getSummonerLevel())
                .summonerId(p.getSummonerId())
                .individualPosition(p.getIndividualPosition())
                .kills(p.getKills())
                .deaths(p.getDeaths())
                .assists(p.getAssists())
                .champExperience(p.getChampExperience())
                .champLevel(p.getChampLevel())
                .championId(p.getChampionId())
                .championName(p.getChampionName())
                .consumablesPurchased(p.getConsumablesPurchased())
                .goldEarned(p.getGoldEarned())
                .summoner1Id(p.getSummoner1Id())
                .summoner2Id(p.getSummoner2Id())
                .itemsPurchased(p.getItemsPurchased())
                .participantId(p.getParticipantId())
                .visionScore(p.getVisionScore())
                .totalMinionsKilled(p.getTotalMinionsKilled())
                .neutralMinionsKilled(p.getNeutralMinionsKilled())
                .totalDamageDealtToChampions(p.getTotalDamageDealtToChampions())
                .totalDamageTaken(p.getTotalDamageTaken())
                .visionWardsBoughtInGame(p.getVisionWardsBoughtInGame())
                .wardsKilled(p.getWardsKilled())
                .wardsPlaced(p.getWardsPlaced())
                .doubleKills(p.getDoubleKills())
                .tripleKills(p.getTripleKills())
                .quadraKills(p.getQuadraKills())
                .pentaKills(p.getPentaKills())
                .teamId(p.getTeamId())
                .teamPosition(p.getTeamPosition())
                .win(p.isWin())
                .timePlayed(p.getTimePlayed())
                .timeCCingOthers(p.getTimeCCingOthers())
                .lane(p.getLane())
                .role(p.getRole())
                .placement(p.getPlacement())
                .playerAugment1(p.getPlayerAugment1())
                .playerAugment2(p.getPlayerAugment2())
                .playerAugment3(p.getPlayerAugment3())
                .playerAugment4(p.getPlayerAugment4());
    }

    private double computeKda(ParticipantDto p) {
        int deaths = Math.max(1, p.getDeaths());
        return (p.getKills() + p.getAssists()) / (double) deaths;
    }

    private ItemView toItem(ParticipantDto p) {
        return ItemView.builder()
                .item0(p.getItem0())
                .item1(p.getItem1())
                .item2(p.getItem2())
                .item3(p.getItem3())
                .item4(p.getItem4())
                .item5(p.getItem5())
                .item6(p.getItem6())
                .build();
    }

    private StyleView toStyle(PerksDto perks) {
        StyleView.StyleViewBuilder b = StyleView.builder();
        if (perks == null || perks.getStyles() == null || perks.getStyles().isEmpty()) {
            return b.build();
        }
        List<PerkStyleDto> styles = perks.getStyles();
        PerkStyleDto primary = styles.get(0);
        b.primaryStyleId(primary.getStyle())
                .primaryPerk0(perkAt(primary, 0))
                .primaryPerk1(perkAt(primary, 1))
                .primaryPerk2(perkAt(primary, 2))
                .primaryPerk3(perkAt(primary, 3));
        if (styles.size() > 1) {
            PerkStyleDto sub = styles.get(1);
            b.subStyleId(sub.getStyle())
                    .subPerk0(perkAt(sub, 0))
                    .subPerk1(perkAt(sub, 1));
        }
        return b.build();
    }

    private int perkAt(PerkStyleDto style, int idx) {
        List<PerkStyleSelectionDto> selections = style.getSelections();
        if (selections == null || idx >= selections.size() || selections.get(idx) == null) {
            return 0;
        }
        return selections.get(idx).getPerk();
    }

    private StatView toStat(PerksDto perks) {
        if (perks == null || perks.getStatPerks() == null) {
            return StatView.builder().build();
        }
        PerkStatsDto sp = perks.getStatPerks();
        return StatView.builder()
                .defense(sp.getDefense())
                .flex(sp.getFlex())
                .offense(sp.getOffense())
                .build();
    }

    private TeamView toTeam(InfoDto info) {
        if (info.getTeams() == null || info.getTeams().isEmpty()) {
            return TeamView.builder().build();
        }
        return TeamView.builder()
                .blueTeam(toTeamInfo(findTeam(info, BLUE_TEAM_ID)))
                .redTeam(toTeamInfo(findTeam(info, RED_TEAM_ID)))
                .build();
    }

    private TeamDto findTeam(InfoDto info, int teamId) {
        for (TeamDto t : info.getTeams()) {
            if (t != null && t.getTeamId() == teamId) {
                return t;
            }
        }
        return null;
    }

    private TeamInfoView toTeamInfo(TeamDto team) {
        TeamInfoView.TeamInfoViewBuilder b = TeamInfoView.builder();
        if (team == null) {
            return b.build();
        }
        b.teamId(team.getTeamId()).win(team.isWin());
        ObjectivesDto obj = team.getObjectives();
        if (obj != null) {
            b.championKills(kills(obj.getChampion()))
                    .baronKills(kills(obj.getBaron()))
                    .dragonKills(kills(obj.getDragon()))
                    .towerKills(kills(obj.getTower()))
                    .inhibitorKills(kills(obj.getInhibitor()));
        }
        return b.build();
    }

    private int kills(ObjectiveDto objective) {
        return objective == null ? 0 : objective.getKills();
    }

    private ItemSkillSeq extractSeq(TimelineDto timeline) {
        ItemSkillSeq seq = new ItemSkillSeq();
        if (timeline == null || timeline.getInfo() == null || timeline.getInfo().getFrames() == null) {
            return seq;
        }
        for (FramesTimeLineDto frame : timeline.getInfo().getFrames()) {
            if (frame.getEvents() == null) {
                continue;
            }
            for (EventsTimeLineDto e : frame.getEvents()) {
                String type = e.getType();
                if (ITEM_PURCHASED.equals(type)) {
                    seq.items.computeIfAbsent(e.getParticipantId(), k -> new ArrayList<>())
                            .add(ItemSeqView.builder()
                                    .itemId(e.getItemId())
                                    .minute(e.getTimestamp() / 1000 / 60)
                                    .type(type)
                                    .build());
                } else if (SKILL_LEVEL_UP.equals(type)) {
                    seq.skills.computeIfAbsent(e.getParticipantId(), k -> new ArrayList<>())
                            .add(SkillSeqView.builder()
                                    .skillSlot(e.getSkillSlot())
                                    .minute(e.getTimestamp() / 1000 / 60)
                                    .type(type)
                                    .build());
                }
            }
        }
        return seq;
    }

    private static final class ItemSkillSeq {
        private final Map<Integer, List<ItemSeqView>> items = new HashMap<>();
        private final Map<Integer, List<SkillSeqView>> skills = new HashMap<>();
    }
}
