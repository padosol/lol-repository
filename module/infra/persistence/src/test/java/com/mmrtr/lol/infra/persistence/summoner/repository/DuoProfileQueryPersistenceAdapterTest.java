package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.MostChampion;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RecentGame;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.TierInfo;
import com.mmrtr.lol.infra.persistence.config.JpaConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 마이그레이션(Flyway) 스키마에서 duo 프로필 조회 — 큐 필터·challenges 조인·정렬·개수 제한. */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, DuoProfileQueryPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DuoProfileQueryPersistenceAdapterTest {

    private static final String ME = "me";
    private static final String OTHER = "other";

    @Autowired
    private DuoProfileQueryPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // given
        for (String table : List.of("match_participant_challenges", "match_participant", "match", "league_summoner",
                "summoner")) {
            entityManager.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        insert("summoner", Map.of("puuid", ME));
        insert("league_summoner", Map.of("puuid", ME, "queue", "RANKED_SOLO_5x5", "tier", "GOLD", "rank", "II",
                "league_points", 45));
        insert("league_summoner", Map.of("puuid", ME, "queue", "RANKED_FLEX_SR", "tier", "PLATINUM", "rank", "IV",
                "league_points", 10));

        // 솔로 랭크 5판(종료 시각이 삽입 순서와 다르다) + 자유 랭크 1판. M5 는 challenges 행이 없다
        game("M1", 420, 1000, 103, "Ahri", true, true);
        game("M2", 420, 3000, 103, "Ahri", false, true);
        game("M3", 420, 2000, 238, "Zed", true, true);
        game("M4", 440, 4000, 99, "Lux", true, true);
        game("M5", 420, 5000, 157, "Yasuo", false, false);
        game("M6", 420, 2500, 1, "Annie", true, true);
        // 같은 게임의 다른 참가자는 세지 않는다
        insert("match_participant", Map.of("puuid", OTHER, "match_id", "M1", "champion_id", 238,
                "champion_name", "Zed", "win", false));
        insert("match_participant_challenges", Map.of("puuid", OTHER, "match_id", "M1"));
    }

    @DisplayName("색인 여부는 summoner 행 존재로 판단한다")
    @Test
    void existsSummoner_byPuuid_checksSummonerRow() {
        // when & then
        assertThat(adapter.existsSummoner(ME)).isTrue();
        assertThat(adapter.existsSummoner("ghost")).isFalse();
    }

    @DisplayName("티어는 요청한 큐의 league_summoner 행, 없으면 빈 값")
    @Test
    void findTier_byQueue_returnsThatQueueOnly() {
        // when & then
        assertThat(adapter.findTier(ME, Queue.RANKED_SOLO_5x5)).contains(new TierInfo("GOLD", "II", 45));
        assertThat(adapter.findTier(ME, Queue.RANKED_FLEX_TT)).isEmpty();
        assertThat(adapter.findTier("ghost", Queue.RANKED_SOLO_5x5)).isEmpty();
    }

    @DisplayName("모스트 챔피언: 해당 큐·challenges 있는 게임만, 판 수 내림차순 → championId 오름차순, limit 개")
    @Test
    void findMostChampions_soloQueue_countsGamesWithChallengesOrderedByPlayCount() {
        // when
        List<MostChampion> top = adapter.findMostChampions(ME, Queue.RANKED_SOLO_5x5, 3);
        List<MostChampion> first = adapter.findMostChampions(ME, Queue.RANKED_SOLO_5x5, 1);

        // then
        assertThat(top).containsExactly(
                new MostChampion(103, "Ahri", 2, 1, 1),
                new MostChampion(1, "Annie", 1, 1, 0),
                new MostChampion(238, "Zed", 1, 1, 0));
        assertThat(first).extracting(MostChampion::championName).containsExactly("Ahri");
    }

    @DisplayName("최근 게임: 해당 큐만 게임 종료 시각 최신순 size 개 (challenges 유무와 무관)")
    @Test
    void findRecentGames_soloQueue_returnsLatestByGameEnd() {
        // when
        List<RecentGame> recent = adapter.findRecentGames(ME, Queue.RANKED_SOLO_5x5, 3);

        // then
        assertThat(recent).containsExactly(
                new RecentGame(false, 157, "Yasuo"),
                new RecentGame(false, 103, "Ahri"),
                new RecentGame(true, 1, "Annie"));
        assertThat(adapter.findRecentGames(ME, Queue.RANKED_SOLO_5x5, 20)).hasSize(5);
        assertThat(adapter.findRecentGames("ghost", Queue.RANKED_SOLO_5x5, 20)).isEmpty();
    }

    private void game(String matchId, int queueId, long gameEnd, int championId, String championName, boolean win,
                      boolean withChallenges) {
        insert("match", Map.of("match_id", matchId, "queue_id", queueId, "game_end_timestamp", gameEnd));
        insert("match_participant", Map.of("puuid", ME, "match_id", matchId, "champion_id", championId,
                "champion_name", championName, "win", win));
        if (withChallenges) {
            insert("match_participant_challenges", Map.of("puuid", ME, "match_id", matchId));
        }
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
