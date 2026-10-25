package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel.DailyCount;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel.RankChampion;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel.RankChampionRecord;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** 실제 마이그레이션(Flyway) 스키마에서 소환사 매치 통계 — 날짜 묶음·필터, challenges 조인·평균·큐별 정렬. */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, MatchStatsQueryPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchStatsQueryPersistenceAdapterTest {

    private static final String ME = "me";

    @Autowired
    private MatchStatsQueryPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        for (String table : List.of("match_participant_challenges", "match_participant", "match")) {
            entityManager.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
    }

    @DisplayName("일별 게임 수: 그 소환사·시즌·큐, 기준 시각 이후만 게임 생성 날짜로 묶어 날짜순")
    @Test
    void findDailyGameCounts_filtersAndGroupsByDate() {
        // given
        game("D1", 420, 15, LocalDateTime.of(2026, 9, 1, 10, 0), 103, true, false);
        game("D2", 420, 15, LocalDateTime.of(2026, 9, 1, 23, 59), 103, true, false);
        game("D3", 440, 15, LocalDateTime.of(2026, 8, 31, 0, 0), 103, true, false);
        game("D4", 450, 15, LocalDateTime.of(2026, 9, 2, 0, 0), 103, true, false);
        game("D5", 420, 14, LocalDateTime.of(2026, 9, 3, 0, 0), 103, true, false);
        game("D6", 420, 15, LocalDateTime.of(2026, 6, 30, 23, 59), 103, true, false);
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);

        // when & then
        assertThat(adapter.findDailyGameCounts(ME, 15, List.of(420, 440), from)).containsExactly(
                new DailyCount(LocalDate.of(2026, 8, 31), 1L),
                new DailyCount(LocalDate.of(2026, 9, 1), 2L));
        assertThat(adapter.findDailyGameCounts(ME, 15, List.of(450), from))
                .containsExactly(new DailyCount(LocalDate.of(2026, 9, 2), 1L));
        assertThat(adapter.findDailyGameCounts("ghost", 15, List.of(420), from)).isEmpty();
    }

    @DisplayName("랭크 챔피언: challenges 있는 솔로·자유 게임만 큐·챔피언별로 평균·승패, 판 수 내림차순 → 챔피언 ID 순")
    @Test
    void findRankChampions_aggregatesRankedGamesWithChallenges() {
        // given
        game("R1", 420, 15, LocalDateTime.of(2026, 9, 1, 0, 0), 103, true, true);
        game("R2", 420, 15, LocalDateTime.of(2026, 9, 2, 0, 0), 103, false, true);
        game("R3", 420, 15, LocalDateTime.of(2026, 9, 3, 0, 0), 238, true, true);
        game("R4", 420, 15, LocalDateTime.of(2026, 9, 4, 0, 0), 1, true, true);
        game("R5", 440, 15, LocalDateTime.of(2026, 9, 5, 0, 0), 99, true, true);
        game("R6", 420, 15, LocalDateTime.of(2026, 9, 6, 0, 0), 157, true, false);
        game("R7", 450, 15, LocalDateTime.of(2026, 9, 7, 0, 0), 22, true, true);
        game("R8", 420, 14, LocalDateTime.of(2026, 9, 8, 0, 0), 266, true, true);

        // when
        List<RankChampionRecord> season15 = adapter.findRankChampions(ME, 15);

        // then — 정렬은 큐와 무관하게 판 수 내림차순 → 챔피언 ID 순 (서비스가 큐별로 나눈다)
        assertThat(season15).extracting(RankChampionRecord::queueId, row -> row.champion().championId())
                .containsExactly(tuple(420, 103), tuple(420, 1), tuple(440, 99), tuple(420, 238));
        // 아리는 1승 1패 — challenges 값은 승리·패배 게임의 평균
        RankChampion ahri = season15.get(0).champion();
        assertThat(ahri).isEqualTo(new RankChampion(3.0, 2.0, 103.0, 103, "c103", 1L, 1L, 50.0,
                600.0, 3.0, 55.0, 25.0, 400.0, 2L));
        assertThat(adapter.findRankChampions(ME, null)).extracting(row -> row.champion().championId())
                .contains(266);
    }

    /** kills 는 챔피언 ID 와 같게, challenges 는 승리 게임과 패배 게임에 다른 값(분당 피해 500·700 …)을 넣는다. */
    private void game(String matchId, int queueId, int season, LocalDateTime created, int championId, boolean win,
                      boolean withChallenges) {
        insert("match", Map.of("match_id", matchId, "queue_id", queueId, "season", season,
                "game_create_datetime", created));
        insert("match_participant", Map.of("puuid", ME, "match_id", matchId, "participant_id", 1,
                "champion_id", championId, "champion_name", "c" + championId, "win", win, "kills", championId,
                "deaths", 2, "assists", 3));
        if (withChallenges) {
            insert("match_participant_challenges", Map.of("puuid", ME, "match_id", matchId,
                    "damage_per_minute", win ? 500.0 : 700.0, "kda", win ? 2.0 : 4.0,
                    "lane_minions_first10minutes", win ? 50 : 60,
                    "damage_taken_on_team_percentage", win ? 20.0 : 30.0, "gold_per_minute", 400.0));
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
