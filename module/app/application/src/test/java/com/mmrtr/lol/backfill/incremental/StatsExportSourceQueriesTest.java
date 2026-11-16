package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.RiotTestRows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(StatsExportSourceQueries.class)
class StatsExportSourceQueriesTest {

    private static final BackfillFactType MATCH = BackfillFactType.MATCH;

    @Autowired
    private StatsExportSourceQueries queries;

    @Autowired
    private JdbcTemplate jdbc;

    private RiotTestRows rows;

    @BeforeEach
    void setUp() {
        rows = new RiotTestRows(jdbc);
    }

    @Test
    @DisplayName("빈 테이블의 nextId 는 1, 아니면 MAX+1")
    void nextId_returnsMaxPlusOne() {
        // given
        long empty = queries.nextId(MATCH);
        insertMatch("M-1", "16.9", 420);
        long last = insertMatch("M-2", "16.9", 420);

        // when
        long result = queries.nextId(MATCH);

        // then
        assertThat(empty).isEqualTo(1);
        assertThat(result).isEqualTo(last + 1);
    }

    @Test
    @DisplayName("범위 최대 패치는 숫자 순이고 NULL·다른 큐·범위 밖은 무시한다")
    void maxPatchInRange_ordersNumericallyAndIgnoresNull() {
        // given
        long from = insertMatch("M-1", "16.9", 420);
        insertMatch("M-2", "16.21", 420);
        insertMatch("M-3", null, 420);
        insertMatch("M-4", "16.30", 440);
        long outside = insertMatch("M-5", "16.40", 420);

        // when
        var result = queries.maxPatchInRange(MATCH, from, outside, List.of(420));

        // then
        assertThat(result).contains(PatchVersion.parse("16.21"));
    }

    @Test
    @DisplayName("범위에 해당 행이 없으면 빈 값")
    void maxPatchInRange_noRows_empty() {
        // when
        var result = queries.maxPatchInRange(MATCH, 1, 10, List.of(420));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("participant 범위는 match 를 조인해 패치를 얻는다")
    void maxPatchInRange_participantJoinsMatch() {
        // given
        insertMatch("M-1", "16.9", 420);
        insertMatch("M-2", "16.21", 420);
        insertMatch("M-3", "16.30", 440);
        long p1 = rows.insert("match_participant", Map.of("match_id", "M-1", "participant_id", 1, "team_id", 100));
        rows.insert("match_participant", Map.of("match_id", "M-2", "participant_id", 1, "team_id", 100));
        long p3 = rows.insert("match_participant", Map.of("match_id", "M-3", "participant_id", 1, "team_id", 100));

        // when
        var result = queries.maxPatchInRange(BackfillFactType.MATCH_PARTICIPANT_BUILD, p1, p3 + 1, List.of(420));
        var endExclusive = queries.maxPatchInRange(BackfillFactType.MATCH_PARTICIPANT_BUILD, p1, p3, List.of(420, 440));

        // then
        assertThat(result).contains(PatchVersion.parse("16.21"));
        assertThat(endExclusive).contains(PatchVersion.parse("16.21"));
    }

    @Test
    @DisplayName("최신 시즌의 패치만 숫자 내림차순으로 limit 개")
    void latestSeasonPatches_latestSeasonOnlyNumericDesc() {
        // given
        insertSeason(15, 15L, "15.24");
        insertSeason(16, 16L, "16.9", "16.21", "16.20");

        // when
        var result = queries.latestSeasonPatches(2);

        // then
        assertThat(result).containsExactly(PatchVersion.parse("16.21"), PatchVersion.parse("16.20"));
    }

    @Test
    @DisplayName("패치 행이 아직 없는 다음 시즌은 건너뛰고, 패치가 있는 최신 시즌을 쓴다")
    void latestSeasonPatches_newestSeasonWithoutPatches_usesLatestSeasonWithPatches() {
        // given
        insertSeason(16, 16L, "16.20", "16.21");
        insertSeason(17, 17L);

        // when
        var result = queries.latestSeasonPatches(3);

        // then
        assertThat(result).containsExactly(PatchVersion.parse("16.21"), PatchVersion.parse("16.20"));
    }

    @Test
    @DisplayName("앞자리 0 패치는 정규화된다")
    void latestSeasonPatches_leadingZero_normalized() {
        // given
        insertSeason(16, 16L, "16.01");

        // when
        var result = queries.latestSeasonPatches(5);

        // then
        assertThat(result).containsExactly(new PatchVersion(16, 1));
    }

    @Test
    @DisplayName("시즌이 없으면 빈 목록")
    void latestSeasonPatches_noSeason_empty() {
        // when
        var result = queries.latestSeasonPatches(3);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("16시즌 기준 데이터는 갖춰져 있고 17시즌은 아니다")
    void referenceData_seededSeasonComplete() {
        // when
        ReferenceDataStatus season16 = queries.referenceData(16);
        ReferenceDataStatus season17 = queries.referenceData(17);

        // then
        assertThat(season16.complete()).isTrue();
        assertThat(season17.complete()).isFalse();
        assertThat(season17).isEqualTo(new ReferenceDataStatus(0, 0, 0));
    }

    private long insertMatch(String matchId, String patch, int queueId) {
        Map<String, Object> values = new HashMap<>();
        values.put("match_id", matchId);
        values.put("season", 16);
        values.put("queue_id", queueId);
        values.put("patch_version", patch);
        return rows.insert("match", values);
    }

    private void insertSeason(int seasonValue, long seasonId, String... patches) {
        rows.insert("season", Map.of("season_id", seasonId, "season_value", seasonValue, "season_name", "S" + seasonValue));
        long versionId = seasonId * 100;
        for (String patch : patches) {
            rows.insert("patch_version", Map.of("version_id", versionId++, "version_value", patch, "season_id", seasonId));
        }
    }
}
