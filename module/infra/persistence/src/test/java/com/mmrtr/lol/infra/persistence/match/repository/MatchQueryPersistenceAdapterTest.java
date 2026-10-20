package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.ParticipantRecord;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.TeamObjectives;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel;
import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import com.mmrtr.lol.support.page.SliceResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 마이그레이션(Flyway) 스키마에서 매치 조회 — 컬럼 매핑·현재 Riot ID 우선·팀 집계·프레임 골드·이벤트 필터와 순서. */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, MatchQueryPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchQueryPersistenceAdapterTest {

    private static final String MATCH_ID = "KR_1";
    private static final String OTHER_MATCH_ID = "KR_2";

    @Autowired
    private MatchQueryPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // given
        for (String table : List.of("timeline_event_frame", "participant_frame", "match_team", "match_participant",
                "match", "summoner")) {
            entityManager.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        Map<String, Object> match = new HashMap<>(Map.of("match_id", MATCH_ID, "data_version", "2",
                "game_creation", 1000L, "game_duration", 1800L, "game_end_timestamp", 3000L,
                "game_start_timestamp", 1100L, "game_mode", "CLASSIC", "game_type", "MATCHED_GAME",
                "game_version", "15.1.1.123", "map_id", 11));
        match.putAll(Map.of("queue_id", 420, "platform_id", "KR", "average_tier", 4450));
        insert("match", match);
        insert("match", Map.of("match_id", OTHER_MATCH_ID));

        // 2번을 먼저 넣는다 — 응답은 참가자 번호순. 2번은 게임 뒤 이름을 바꿨다
        insert("summoner", Map.of("puuid", "p2", "game_name", "NewName", "tag_line", "KR1"));
        insert("match_participant", Map.of("puuid", "p2", "match_id", MATCH_ID, "participant_id", 2,
                "team_id", 200, "riot_id_game_name", "OldName", "riot_id_tagline", "KR9", "tier", "GOLD",
                "tier_rank", "IV", "absolute_points", 4450));
        Map<String, Object> p1 = new HashMap<>(Map.of("puuid", "p1", "match_id", MATCH_ID, "participant_id", 1,
                "team_id", 100, "riot_id_game_name", "Faker", "riot_id_tagline", "KR1", "win", true));
        p1.putAll(Map.of("champion_name", "Ahri", "item0", 3157, "primary_style_id", 8100, "stat_perk_flex", 5008));
        insert("match_participant", p1);
        insert("match_participant", Map.of("puuid", "p1", "match_id", OTHER_MATCH_ID, "participant_id", 1));
        insert("match_team", Map.of("match_id", MATCH_ID, "team_id", 100, "win", true, "champion_kills", 25,
                "baron_kills", 1, "dragon_kills", 2, "tower_kills", 9, "inhibitor_kills", 1));

        // 프레임은 시각 역순으로 넣는다
        insert("participant_frame", Map.of("match_id", MATCH_ID, "participant_id", 1, "timestamp", 60000,
                "total_gold", 800));
        insert("participant_frame", Map.of("match_id", MATCH_ID, "participant_id", 1, "timestamp", 0,
                "total_gold", 500));

        event(MATCH_ID, 120000, 0, "{\"type\": \"ITEM_PURCHASED\", \"participantId\": 2, \"itemId\": 2003}");
        event(MATCH_ID, 60000, 1, "{\"type\": \"ITEM_PURCHASED\", \"participantId\": 1, \"itemId\": 1055}");
        event(MATCH_ID, 60000, 0, "{\"type\": \"SKILL_LEVEL_UP\", \"participantId\": 1, \"skillSlot\": 1}");
        // 응답에 쓰지 않는 이벤트·참가자 없는 이벤트·다른 매치는 읽지 않는다
        event(MATCH_ID, 0, 0, "{\"type\": \"ITEM_SOLD\", \"participantId\": 1, \"itemId\": 1055}");
        event(MATCH_ID, 0, 1, "{\"type\": \"ITEM_PURCHASED\", \"itemId\": 1055}");
        event(OTHER_MATCH_ID, 0, 0, "{\"type\": \"ITEM_PURCHASED\", \"participantId\": 1, \"itemId\": 1055}");
    }

    @DisplayName("게임 정보: 컬럼을 그대로 옮기고 평균 티어 점수를 티어·등급으로, 없는 매치는 빈 값")
    @Test
    void findGameInfo_storedMatch_mapsColumnsAndAverageTier() {
        // when & then
        assertThat(adapter.findGameInfo(MATCH_ID)).contains(new GameInfo("2", 1000L, 1800L, 3000L, "CLASSIC",
                1100L, "MATCHED_GAME", "15.1.1.123", 11, "KR", 420, null, MATCH_ID, "GOLD", "IV"));
        assertThat(adapter.findGameInfo(OTHER_MATCH_ID)).get()
                .extracting(GameInfo::averageTier, GameInfo::averageRank)
                .containsExactly(null, null);
        assertThat(adapter.findGameInfo("ghost")).isEmpty();
    }

    @DisplayName("참가자: 참가자 번호순, 저장된 소환사의 현재 Riot ID 우선, 팀 집계·시각순 프레임 골드")
    @Test
    void findParticipants_match_readsParticipantWithTeamAndGold() {
        // when
        List<ParticipantRecord> records = adapter.findParticipants(List.of(MATCH_ID));

        // then
        assertThat(records).extracting(record -> record.participant().participantId()).containsExactly(1, 2);

        ParticipantRecord first = records.get(0);
        ParticipantReadModel p1 = first.participant();
        assertThat(first.matchId()).isEqualTo(MATCH_ID);
        assertThat(p1.riotIdGameName()).isEqualTo("Faker");
        assertThat(p1.championName()).isEqualTo("Ahri");
        assertThat(p1.win()).isTrue();
        assertThat(p1.absolutePoints()).isNull();
        assertThat(p1.item().item0()).isEqualTo(3157);
        assertThat(p1.style().primaryStyleId()).isEqualTo(8100);
        assertThat(p1.statValue().flex()).isEqualTo(5008);
        assertThat(p1.itemSeq()).isNull();
        assertThat(first.team()).isEqualTo(new TeamObjectives(25, 1, 2, 9, 1));
        assertThat(first.goldTimeline()).containsExactly(500, 800);
        assertThat(first.timestamps()).containsExactly(0, 60000);

        ParticipantRecord second = records.get(1);
        assertThat(second.participant().riotIdGameName()).isEqualTo("NewName");
        assertThat(second.participant().riotIdTagline()).isEqualTo("KR1");
        assertThat(second.participant().tierRank()).isEqualTo("IV");
        assertThat(second.participant().absolutePoints()).isEqualTo(4450);
        assertThat(second.team()).isEqualTo(new TeamObjectives(0, 0, 0, 0, 0));
        assertThat(second.goldTimeline()).isNull();
        assertThat(second.timestamps()).isNull();

        assertThat(adapter.findParticipants(List.of(MATCH_ID, OTHER_MATCH_ID))).hasSize(3);
        assertThat(adapter.findParticipants(List.of())).isEmpty();
    }

    @DisplayName("이벤트: 그 매치의 아이템 구매·스킬 레벨업만 프레임 시각·이벤트 순으로")
    @Test
    void findTimelineEvents_match_readsUsedEventsInOrder() {
        // when & then
        assertThat(adapter.findTimelineEvents(List.of(MATCH_ID))).containsExactly(
                new Event(MATCH_ID, 1, "SKILL_LEVEL_UP", null, 1, 60000),
                new Event(MATCH_ID, 1, "ITEM_PURCHASED", 1055, null, 60000),
                new Event(MATCH_ID, 2, "ITEM_PURCHASED", 2003, null, 120000));
        assertThat(adapter.findTimelineEvents(List.of())).isEmpty();
    }

    @DisplayName("소환사 전적: 그 소환사의 CLASSIC·CHERRY(대소문자 무시)만, 시즌·큐 필터, 게임 종료 최신순, 한 건 더 읽어 hasNext")
    @Test
    void findSummonerGameInfos_filtersAndPages() {
        // given — KR_1 은 setUp 의 다른 소환사 매치
        summonerGame("S_1", "CLASSIC", 420, 15, 1000);
        summonerGame("S_2", "classic", 440, 15, 4000);
        summonerGame("S_3", "CHERRY", 1700, 15, 3000);
        summonerGame("S_4", "ARAM", 450, 15, 5000);
        summonerGame("S_5", "CLASSIC", 420, 14, 2000);

        // when
        SliceResult<GameInfo> all = adapter.findSummonerGameInfos("me", null, null, 0, 20);
        SliceResult<GameInfo> firstPage = adapter.findSummonerGameInfos("me", null, null, 0, 2);
        SliceResult<GameInfo> lastPage = adapter.findSummonerGameInfos("me", null, null, 1, 2);

        // then
        assertThat(all.content()).extracting(GameInfo::matchId).containsExactly("S_2", "S_3", "S_5", "S_1");
        assertThat(all.hasNext()).isFalse();
        assertThat(firstPage.content()).extracting(GameInfo::matchId).containsExactly("S_2", "S_3");
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.content()).extracting(GameInfo::matchId).containsExactly("S_5", "S_1");
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(adapter.findSummonerGameInfos("me", 15, null, 0, 20).content())
                .extracting(GameInfo::matchId).containsExactly("S_2", "S_3", "S_1");
        assertThat(adapter.findSummonerGameInfos("me", 15, 420, 0, 20).content())
                .extracting(GameInfo::matchId).containsExactly("S_1");
        assertThat(adapter.findSummonerGameInfos("ghost", null, null, 0, 20).content()).isEmpty();
    }

    @DisplayName("소환사 매치 ID: 게임 모드와 무관하게 매치 ID 내림차순, 큐 필터, 한 건 더 읽어 hasNext")
    @Test
    void findSummonerMatchIds_ordersByMatchIdAndPages() {
        // given
        summonerGame("S_1", "CLASSIC", 420, 15, 3000);
        summonerGame("S_3", "ARAM", 450, 15, 1000);
        summonerGame("S_2", "CLASSIC", 440, 15, 2000);

        // when
        SliceResult<String> firstPage = adapter.findSummonerMatchIds("me", null, 0, 2);

        // then
        assertThat(firstPage.content()).containsExactly("S_3", "S_2");
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(adapter.findSummonerMatchIds("me", null, 1, 2)).isEqualTo(new SliceResult<>(List.of("S_1"), false));
        assertThat(adapter.findSummonerMatchIds("me", 420, 0, 20).content()).containsExactly("S_1");
    }

    private void summonerGame(String matchId, String gameMode, int queueId, int season, long gameEnd) {
        insert("match", Map.of("match_id", matchId, "game_mode", gameMode, "queue_id", queueId, "season", season,
                "game_end_timestamp", gameEnd));
        insert("match_participant", Map.of("puuid", "me", "match_id", matchId, "participant_id", 1));
    }

    private void event(String matchId, long timestamp, int eventIndex, String data) {
        entityManager.createNativeQuery("INSERT INTO timeline_event_frame (match_id, timestamp, event_index, data) "
                        + "VALUES (?1, ?2, ?3, CAST(?4 AS jsonb))")
                .setParameter(1, matchId)
                .setParameter(2, timestamp)
                .setParameter(3, eventIndex)
                .setParameter(4, data)
                .executeUpdate();
    }

    /** 기본값 없는 NOT NULL 컬럼은 타입별 0 값으로 채우고, 테스트에 필요한 컬럼만 지정한다 (match_participant 는 100개가 넘는다). */
    @SuppressWarnings("unchecked")
    private void insert(String table, Map<String, Object> values) {
        List<Object[]> requiredColumns = entityManager.createNativeQuery("""
                        SELECT column_name, data_type FROM information_schema.columns
                        WHERE table_schema = current_schema() AND table_name = :table
                          AND is_nullable = 'NO' AND column_default IS NULL AND is_identity = 'NO'
                        """)
                .setParameter("table", table)
                .getResultList();
        Map<String, Object> row = new LinkedHashMap<>();
        for (Object[] column : requiredColumns) {
            row.put((String) column[0], zeroOf((String) column[1]));
        }
        row.putAll(values);

        List<String> placeholders = new ArrayList<>();
        for (int i = 1; i <= row.size(); i++) {
            placeholders.add("?" + i);
        }
        Query query = entityManager.createNativeQuery("INSERT INTO " + table + " (" + String.join(", ", row.keySet())
                + ") VALUES (" + String.join(", ", placeholders) + ")");
        int position = 1;
        for (Object value : row.values()) {
            query.setParameter(position++, value);
        }
        query.executeUpdate();
    }

    private Object zeroOf(String dataType) {
        return switch (dataType) {
            case "boolean" -> false;
            case "smallint", "integer" -> 0;
            case "bigint" -> 0L;
            case "real", "double precision", "numeric" -> 0.0;
            default -> "";
        };
    }
}
