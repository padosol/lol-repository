package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.domain.match.application.port.MatchQueryPort;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.ParticipantRecord;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.TeamObjectives;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel.Item;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel.StatValue;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel.Style;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 공개 매치 조회 어댑터. 쿼리는 lol-server {@code MatchRepositoryCustomImpl}·{@code TimelineRepositoryCustomImpl} 과 같다. */
@Component
@RequiredArgsConstructor
public class MatchQueryPersistenceAdapter implements MatchQueryPort {

    private static final String GAME_INFO_SQL = """
            SELECT match_id, data_version, game_creation, game_duration, game_end_timestamp, game_start_timestamp,
                   game_mode, game_type, game_version, map_id, queue_id, platform_id, tournament_code, average_tier
            FROM match
            WHERE match_id = :matchId
            """;

    /** 팀 집계와 참가자별 프레임 골드를 한 번에 읽는다. 이름은 저장된 소환사의 현재 Riot ID 가 우선한다. */
    private static final String PARTICIPANTS_SQL = """
            SELECT mp.puuid, mp.match_id, mp.summoner_id,
                   COALESCE(s.game_name, mp.riot_id_game_name) AS riot_id_game_name,
                   COALESCE(s.tag_line, mp.riot_id_tagline) AS riot_id_tagline,
                   mp.profile_icon, mp.participant_id, mp.tier, mp.tier_rank,
                   mp.absolute_points, mp.summoner_level,
                   mp.champ_level, mp.champion_id, mp.champion_name, mp.champ_experience,
                   mp.summoner1id, mp.summoner2id,
                   mp.kills, mp.assists, mp.deaths,
                   mp.double_kills, mp.triple_kills, mp.quadra_kills, mp.penta_kills,
                   mp.gold_earned, mp.consumables_purchased, mp.items_purchased,
                   mp.neutral_minions_killed, mp.total_minions_killed,
                   mp.vision_score, mp.vision_wards_bought_in_game, mp.wards_killed, mp.wards_placed,
                   mp.total_damage_dealt_to_champions, mp.total_damage_taken,
                   mp.team_id, mp.team_position, mp.win, mp.time_played, mp.timeccing_others,
                   mp.individual_position, mp.lane, mp.role, mp.placement,
                   mp.player_augment1, mp.player_augment2, mp.player_augment3, mp.player_augment4,
                   mp.item0, mp.item1, mp.item2, mp.item3, mp.item4, mp.item5, mp.item6,
                   mp.stat_perk_defense, mp.stat_perk_flex, mp.stat_perk_offense,
                   mp.primary_style_id, mp.primary_perk0, mp.primary_perk1, mp.primary_perk2, mp.primary_perk3,
                   mp.sub_style_id, mp.sub_perk0, mp.sub_perk1,
                   mt.champion_kills AS team_champion_kills,
                   mt.baron_kills AS team_baron_kills,
                   mt.dragon_kills AS team_dragon_kills,
                   mt.tower_kills AS team_tower_kills,
                   mt.inhibitor_kills AS team_inhibitor_kills,
                   pf_agg.gold_timeline,
                   pf_agg.timestamps
            FROM match_participant mp
            LEFT JOIN summoner s ON s.puuid = mp.puuid
            LEFT JOIN match_team mt ON mt.match_id = mp.match_id AND mt.team_id = mp.team_id
            LEFT JOIN (
                SELECT match_id, participant_id,
                       array_agg(total_gold ORDER BY timestamp) AS gold_timeline,
                       array_agg(timestamp ORDER BY timestamp) AS timestamps
                FROM participant_frame
                WHERE match_id IN (:matchIds)
                GROUP BY match_id, participant_id
            ) pf_agg ON mp.match_id = pf_agg.match_id AND mp.participant_id = pf_agg.participant_id
            WHERE mp.match_id IN (:matchIds)
            ORDER BY mp.match_id, mp.participant_id
            """;

    /** 응답에 쓰는 이벤트(아이템 구매·스킬 레벨업)만 읽는다. timestamp 는 프레임 시각이다. */
    private static final String TIMELINE_EVENTS_SQL = """
            SELECT match_id,
                   (data->>'participantId')::int AS participant_id,
                   data->>'type'                 AS type,
                   (data->>'itemId')::int        AS item_id,
                   (data->>'skillSlot')::int     AS skill_slot,
                   timestamp
            FROM timeline_event_frame
            WHERE match_id IN (:matchIds)
              AND data->>'participantId' IS NOT NULL
              AND data->>'type' IN ('ITEM_PURCHASED', 'SKILL_LEVEL_UP')
            ORDER BY timestamp, event_index
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Optional<GameInfo> findGameInfo(String matchId) {
        return jdbcTemplate.query(GAME_INFO_SQL, new MapSqlParameterSource("matchId", matchId),
                        (rs, rowNum) -> toGameInfo(rs))
                .stream()
                .findFirst();
    }

    @Override
    public List<ParticipantRecord> findParticipants(List<String> matchIds) {
        if (matchIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(PARTICIPANTS_SQL, new MapSqlParameterSource("matchIds", matchIds),
                (rs, rowNum) -> toParticipantRecord(rs));
    }

    @Override
    public List<Event> findTimelineEvents(List<String> matchIds) {
        if (matchIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(TIMELINE_EVENTS_SQL, new MapSqlParameterSource("matchIds", matchIds),
                (rs, rowNum) -> new Event(
                        rs.getString("match_id"),
                        rs.getInt("participant_id"),
                        rs.getString("type"),
                        rs.getObject("item_id", Integer.class),
                        rs.getObject("skill_slot", Integer.class),
                        rs.getLong("timestamp")));
    }

    private GameInfo toGameInfo(ResultSet rs) throws SQLException {
        Integer averageTier = rs.getObject("average_tier", Integer.class);
        return new GameInfo(
                rs.getString("data_version"),
                rs.getLong("game_creation"),
                rs.getLong("game_duration"),
                rs.getLong("game_end_timestamp"),
                rs.getString("game_mode"),
                rs.getLong("game_start_timestamp"),
                rs.getString("game_type"),
                rs.getString("game_version"),
                rs.getInt("map_id"),
                rs.getString("platform_id"),
                rs.getInt("queue_id"),
                rs.getString("tournament_code"),
                rs.getString("match_id"),
                GameInfo.averageTierOf(averageTier),
                GameInfo.averageRankOf(averageTier));
    }

    /** 숫자 컬럼의 NULL 은 0 이다 (lol-server 와 같다). absolute_points 만 null 을 그대로 둔다. */
    private ParticipantRecord toParticipantRecord(ResultSet rs) throws SQLException {
        ParticipantReadModel participant = ParticipantReadModel.builder()
                .puuid(rs.getString("puuid"))
                .summonerId(rs.getString("summoner_id"))
                .riotIdGameName(rs.getString("riot_id_game_name"))
                .riotIdTagline(rs.getString("riot_id_tagline"))
                .profileIcon(rs.getInt("profile_icon"))
                .participantId(rs.getInt("participant_id"))
                .tier(rs.getString("tier"))
                .tierRank(rs.getString("tier_rank"))
                .absolutePoints(rs.getObject("absolute_points", Integer.class))
                .summonerLevel(rs.getInt("summoner_level"))
                .champLevel(rs.getInt("champ_level"))
                .championId(rs.getInt("champion_id"))
                .championName(rs.getString("champion_name"))
                .champExperience(rs.getInt("champ_experience"))
                .summoner1Id(rs.getInt("summoner1id"))
                .summoner2Id(rs.getInt("summoner2id"))
                .kills(rs.getInt("kills"))
                .assists(rs.getInt("assists"))
                .deaths(rs.getInt("deaths"))
                .doubleKills(rs.getInt("double_kills"))
                .tripleKills(rs.getInt("triple_kills"))
                .quadraKills(rs.getInt("quadra_kills"))
                .pentaKills(rs.getInt("penta_kills"))
                .goldEarned(rs.getInt("gold_earned"))
                .consumablesPurchased(rs.getInt("consumables_purchased"))
                .itemsPurchased(rs.getInt("items_purchased"))
                .neutralMinionsKilled(rs.getInt("neutral_minions_killed"))
                .totalMinionsKilled(rs.getInt("total_minions_killed"))
                .visionScore(rs.getInt("vision_score"))
                .visionWardsBoughtInGame(rs.getInt("vision_wards_bought_in_game"))
                .wardsKilled(rs.getInt("wards_killed"))
                .wardsPlaced(rs.getInt("wards_placed"))
                .totalDamageDealtToChampions(rs.getInt("total_damage_dealt_to_champions"))
                .totalDamageTaken(rs.getInt("total_damage_taken"))
                .teamId(rs.getInt("team_id"))
                .teamPosition(rs.getString("team_position"))
                .win(rs.getBoolean("win"))
                .timePlayed(rs.getInt("time_played"))
                .timeCCingOthers(rs.getInt("timeccing_others"))
                .individualPosition(rs.getString("individual_position"))
                .lane(rs.getString("lane"))
                .role(rs.getString("role"))
                .placement(rs.getInt("placement"))
                .playerAugment1(rs.getInt("player_augment1"))
                .playerAugment2(rs.getInt("player_augment2"))
                .playerAugment3(rs.getInt("player_augment3"))
                .playerAugment4(rs.getInt("player_augment4"))
                .item(new Item(rs.getInt("item0"), rs.getInt("item1"), rs.getInt("item2"), rs.getInt("item3"),
                        rs.getInt("item4"), rs.getInt("item5"), rs.getInt("item6")))
                .statValue(new StatValue(rs.getInt("stat_perk_defense"), rs.getInt("stat_perk_flex"),
                        rs.getInt("stat_perk_offense")))
                .style(new Style(rs.getInt("primary_style_id"), rs.getInt("primary_perk0"),
                        rs.getInt("primary_perk1"), rs.getInt("primary_perk2"), rs.getInt("primary_perk3"),
                        rs.getInt("sub_style_id"), rs.getInt("sub_perk0"), rs.getInt("sub_perk1")))
                .build();

        TeamObjectives team = new TeamObjectives(rs.getInt("team_champion_kills"), rs.getInt("team_baron_kills"),
                rs.getInt("team_dragon_kills"), rs.getInt("team_tower_kills"), rs.getInt("team_inhibitor_kills"));
        return new ParticipantRecord(rs.getString("match_id"), participant, team,
                toIntegers(rs.getArray("gold_timeline")), toIntegers(rs.getArray("timestamps")));
    }

    private List<Integer> toIntegers(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        return Arrays.asList((Integer[]) array.getArray());
    }
}
