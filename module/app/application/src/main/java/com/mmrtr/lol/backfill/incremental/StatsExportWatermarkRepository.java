package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * stats_export_watermark 접근. 모든 갱신은 기대 범위가 아직 유효할 때만 적용되는 낙관적 조건부 UPDATE 다.
 * observed_at 은 observed_until 을 쓸 때만 갱신한다 (범위 패치 고정은 관측 시각을 바꾸지 않는다).
 */
@Repository
public class StatsExportWatermarkRepository {

    private static final String SELECT = "SELECT fact_type, exported_until, observed_until, range_patches,"
            + " observed_at, export_epoch FROM stats_export_watermark";

    private final JdbcTemplate jdbc;

    public StatsExportWatermarkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Watermark findOrCreate(BackfillFactType factType, long seedId) {
        jdbc.update("""
                INSERT INTO stats_export_watermark (fact_type, exported_until, observed_until, observed_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (fact_type) DO NOTHING
                """, factType.code(), seedId, seedId);
        return jdbc.queryForObject(SELECT + " WHERE fact_type = ?", (rs, i) -> map(rs), factType.code());
    }

    public boolean saveRangePatches(BackfillFactType factType, long expectedStart, long expectedObserved, String patches) {
        return jdbc.update("""
                UPDATE stats_export_watermark
                SET range_patches = ?, updated_at = now()
                WHERE fact_type = ? AND exported_until = ? AND observed_until = ? AND range_patches IS NULL
                """, patches, factType.code(), expectedStart, expectedObserved) == 1;
    }

    public boolean advance(BackfillFactType factType, long expectedStart, long expectedObserved,
                           long newObserved, @Nullable String runId) {
        return jdbc.update("""
                UPDATE stats_export_watermark
                SET exported_until = ?, observed_until = ?, observed_at = now(), range_patches = NULL,
                    last_run_id = COALESCE(CAST(? AS VARCHAR), last_run_id),
                    last_success_at = CASE WHEN CAST(? AS VARCHAR) IS NULL THEN last_success_at ELSE now() END,
                    updated_at = now()
                WHERE fact_type = ? AND exported_until = ? AND observed_until = ?
                """, expectedObserved, newObserved, runId, runId, factType.code(), expectedStart, expectedObserved) == 1;
    }

    public List<Watermark> findAll() {
        return jdbc.query(SELECT + " ORDER BY fact_type", (rs, i) -> map(rs));
    }

    private static Watermark map(ResultSet rs) throws SQLException {
        return new Watermark(BackfillFactType.fromCode(rs.getString("fact_type")),
                rs.getLong("exported_until"), rs.getLong("observed_until"), rs.getString("range_patches"),
                rs.getObject("observed_at", OffsetDateTime.class).toInstant(), rs.getInt("export_epoch"));
    }
}
