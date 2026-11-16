package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@JdbcTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(StatsExportWatermarkRepository.class)
class StatsExportWatermarkRepositoryTest {

    private static final BackfillFactType MATCH = BackfillFactType.MATCH;
    private static final Instant OBSERVED_LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired
    private StatsExportWatermarkRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM stats_export_watermark");
    }

    @Test
    @DisplayName("없으면 seed 로 만든다")
    void findOrCreate_missing_createsSeededRow() {
        // when
        Watermark result = repo.findOrCreate(MATCH, 0);

        // then
        assertThat(result).isEqualTo(new Watermark(MATCH, 0, 0, null, result.observedAt(), 0));
        assertThat(result.observedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("이미 있으면 seed 로 덮어쓰지 않는다")
    void findOrCreate_existing_keepsRow() {
        // given
        seed(MATCH, 10, 20);

        // when
        Watermark result = repo.findOrCreate(MATCH, 0);

        // then
        assertThat(result).isEqualTo(new Watermark(MATCH, 10, 20, null, OBSERVED_LONG_AGO, 0));
    }

    @Test
    @DisplayName("range_patches 는 비어 있고 범위가 맞을 때만 한 번 저장된다")
    void saveRangePatches_onlyOnceForMatchingRange() {
        // given
        seed(MATCH, 0, 100);

        // when
        boolean first = repo.saveRangePatches(MATCH, 0, 100, "16.21");
        boolean second = repo.saveRangePatches(MATCH, 0, 100, "16.22");
        boolean wrongRange = repo.saveRangePatches(MATCH, 5, 100, "16.21");

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(wrongRange).isFalse();
        assertThat(repo.findOrCreate(MATCH, 0).rangePatches()).isEqualTo("16.21");
    }

    @Test
    @DisplayName("범위 패치 고정은 observed_at 을 건드리지 않는다")
    void saveRangePatches_leavesObservedAt() {
        // given
        seed(MATCH, 0, 100);

        // when
        boolean saved = repo.saveRangePatches(MATCH, 0, 100, "16.21");

        // then
        assertThat(saved).isTrue();
        assertThat(repo.findOrCreate(MATCH, 0).observedAt()).isEqualTo(OBSERVED_LONG_AGO);
    }

    @Test
    @DisplayName("전진하면 범위가 밀리고 range_patches 가 지워지며 성공 기록이 남는다")
    void advance_matchingRange_movesAndClearsPatches() {
        // given
        seed(MATCH, 0, 100);
        repo.saveRangePatches(MATCH, 0, 100, "16.21");

        // when
        boolean advanced = repo.advance(MATCH, 0, 100, 250, "inc-match-0-100");

        // then
        assertThat(advanced).isTrue();
        Watermark after = repo.findOrCreate(MATCH, 0);
        assertThat(after.exportedUntil()).isEqualTo(100);
        assertThat(after.observedUntil()).isEqualTo(250);
        assertThat(after.rangePatches()).isNull();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT last_run_id, last_success_at FROM stats_export_watermark WHERE fact_type = 'match'");
        assertThat(row.get("last_run_id")).isEqualTo("inc-match-0-100");
        assertThat(row.get("last_success_at")).isNotNull();
    }

    @Test
    @DisplayName("전진하면 observed_at 이 지금으로 갱신된다")
    void advance_setsObservedAtToNow() {
        // given
        seed(MATCH, 0, 100);

        // when
        repo.advance(MATCH, 0, 100, 250, null);

        // then
        Instant observedAt = repo.findOrCreate(MATCH, 0).observedAt();
        assertThat(observedAt).isAfter(OBSERVED_LONG_AGO);
        assertThat(observedAt).isCloseTo(Instant.now(), within(5, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("runId 가 없으면 성공 기록은 건드리지 않는다")
    void advance_nullRunId_leavesSuccessRecordUntouched() {
        // given
        seed(MATCH, 0, 100);

        // when
        boolean advanced = repo.advance(MATCH, 0, 100, 250, null);

        // then
        assertThat(advanced).isTrue();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT last_run_id, last_success_at FROM stats_export_watermark WHERE fact_type = 'match'");
        assertThat(row.get("last_run_id")).isNull();
        assertThat(row.get("last_success_at")).isNull();
    }

    @Test
    @DisplayName("기대 범위가 다르면 바꾸지 않는다")
    void advance_staleExpectation_returnsFalse() {
        // given
        seed(MATCH, 0, 100);

        // when
        boolean advanced = repo.advance(MATCH, 50, 100, 250, "run");

        // then
        assertThat(advanced).isFalse();
        assertThat(repo.findOrCreate(MATCH, 0)).isEqualTo(new Watermark(MATCH, 0, 100, null, OBSERVED_LONG_AGO, 0));
    }

    @Test
    @DisplayName("export_epoch 를 읽고, 패치 고정·전진은 epoch 를 바꾸지 않는다")
    void exportEpoch_readAndPreservedByUpdates() {
        // given
        seed(MATCH, 0, 100);
        jdbc.update("UPDATE stats_export_watermark SET export_epoch = export_epoch + 1 WHERE fact_type = 'match'");

        // when
        int before = repo.findOrCreate(MATCH, 0).exportEpoch();
        repo.saveRangePatches(MATCH, 0, 100, "16.21");
        repo.advance(MATCH, 0, 100, 250, "run");

        // then
        assertThat(before).isEqualTo(1);
        assertThat(repo.findOrCreate(MATCH, 0).exportEpoch()).isEqualTo(1);
    }

    @Test
    @DisplayName("findAll 은 저장된 모든 fact 의 워터마크를 돌려준다")
    void findAll_returnsAllRows() {
        // given
        seed(MATCH, 0, 100);
        seed(BackfillFactType.MATCH_BAN, 3, 4);

        // when
        var result = repo.findAll();

        // then
        assertThat(result).containsExactlyInAnyOrder(
                new Watermark(MATCH, 0, 100, null, OBSERVED_LONG_AGO, 0),
                new Watermark(BackfillFactType.MATCH_BAN, 3, 4, null, OBSERVED_LONG_AGO, 0));
    }

    private void seed(BackfillFactType type, long exported, long observed) {
        jdbc.update("""
                INSERT INTO stats_export_watermark (fact_type, exported_until, observed_until, observed_at)
                VALUES (?, ?, ?, ?)
                """, type.code(), exported, observed, OffsetDateTime.ofInstant(OBSERVED_LONG_AGO, ZoneOffset.UTC));
    }
}
