package com.mmrtr.lol.backfill.sql;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.RiotTestRows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AggregationSqlIntegrationTest {

    private static final int SEASON = 16;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NamedParameterJdbcTemplate named;

    private RiotTestRows rows;
    private int eventIndex;
    private final AggregationSql sql = new AggregationSql();

    @BeforeEach
    void setUp() {
        rows = new RiotTestRows(jdbc);
        eventIndex = 0;
    }

    @DisplayName("match 집계: 패치 필터가 켜지면 대상 패치 매치만, tier_bucket 포함")
    @Test
    void matchAggregation_patchFilter_exportsOnlyTargetPatch() {
        // given
        insertMatch("M-21", "16.21", 6543);
        insertMatch("M-20", "16.20", 6543);

        // when
        List<Map<String, Object>> result = run(BackfillFactType.MATCH, false, List.of("16.21"));

        // then
        assertThat(result).extracting(r -> r.get("patch_version")).containsExactly("16.21");
        assertThat(result.get(0)).containsEntry("patch_version_int", 1621).containsEntry("tier_bucket", 6000);
    }

    @DisplayName("match 집계: allPatches 면 모든 패치")
    @Test
    void matchAggregation_allPatches_exportsEveryPatch() {
        // given
        insertMatch("M-21", "16.21", 6543);
        insertMatch("M-20", "16.20", 6543);

        // when
        List<Map<String, Object>> result = run(BackfillFactType.MATCH, true, List.of(""));

        // then
        assertThat(result).extracting(r -> r.get("patch_version")).containsExactlyInAnyOrder("16.21", "16.20");
    }

    @DisplayName("match_ban 집계: 대상 패치만, champion_id -1 제외")
    @Test
    void matchBanAggregation_patchFilter_exportsOnlyTargetPatchBans() {
        // given
        insertMatch("M-21", "16.21", 6543);
        insertMatch("M-20", "16.20", 6543);
        rows.insert("match_ban", Map.of("match_id", "M-21", "champion_id", 10, "team_id", 100));
        rows.insert("match_ban", Map.of("match_id", "M-21", "champion_id", -1, "team_id", 200));
        rows.insert("match_ban", Map.of("match_id", "M-20", "champion_id", 11, "team_id", 100));

        // when
        List<Map<String, Object>> result = run(BackfillFactType.MATCH_BAN, false, List.of("16.21"));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("champion_id", 10).containsEntry("patch_version", "16.21");
    }

    @DisplayName("participant-build 집계: 시작 아이템에서 와드(TRINKET)를 빼고 신발은 BOOTS 시드로 잡는다")
    @Test
    void participantBuildAggregation_excludesTrinketsAndResolvesBoots() throws SQLException {
        // given
        insertMatch("M-21", "16.21", 6543);
        insertParticipant("M-21", 1, 100, "p1");
        insertParticipant("M-21", 6, 200, "p2");
        for (int participantId : new int[] {1, 6}) {
            insertItem("M-21", participantId, 5000, 1055);
            insertItem("M-21", participantId, 5000, 3340);
            insertItem("M-21", participantId, 600000, 3006);
            insertItem("M-21", participantId, 900000, 2065);
            rows.insert("skill_level_up_event", Map.of("match_id", "M-21", "participant_id", participantId,
                    "skill_slot", 1, "timestamp", 1000L, "event_index", eventIndex++));
        }

        // when
        List<Map<String, Object>> result = run(BackfillFactType.MATCH_PARTICIPANT_BUILD, false, List.of("16.21"));

        // then
        Map<String, Object> row = result.stream().filter(r -> (int) r.get("participant_id") == 1)
                .findFirst().orElseThrow();
        assertThat(toList(row.get("start_item_ids"))).isEqualTo(List.of(1055));
        assertThat(row.get("boot_id")).isEqualTo(3006);
    }

    private void insertMatch(String matchId, String patch, int averageTier) {
        rows.insert("match", Map.of("match_id", matchId, "season", SEASON, "queue_id", 420,
                "patch_version", patch, "average_tier", averageTier));
    }

    private void insertParticipant(String matchId, int participantId, int teamId, String puuid) {
        rows.insert("match_participant", Map.of("match_id", matchId, "participant_id", participantId,
                "team_id", teamId, "individual_position", "TOP", "puuid", puuid, "champion_id", 86));
    }

    private void insertItem(String matchId, int participantId, long timestamp, int itemId) {
        rows.insert("item_event", Map.of("match_id", matchId, "participant_id", participantId,
                "timestamp", timestamp, "item_id", itemId, "type", "ITEM_PURCHASED", "event_index", eventIndex++));
    }

    private List<Map<String, Object>> run(BackfillFactType type, boolean allPatches, List<String> patches) {
        Map<String, Object> params = new HashMap<>();
        params.put("startId", 0L);
        params.put("endId", Long.MAX_VALUE);
        params.put("season", SEASON);
        params.put("queueIds", List.of(420));
        params.put("allPatches", allPatches);
        params.put("patchVersions", patches);
        return named.queryForList(sql.get(type), params);
    }

    private static List<Object> toList(Object sqlArray) throws SQLException {
        return Arrays.asList((Object[]) ((Array) sqlArray).getArray());
    }
}
