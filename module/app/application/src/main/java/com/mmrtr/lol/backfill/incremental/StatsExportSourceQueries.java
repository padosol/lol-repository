package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 증분 적재가 쓰는 읽기 전용 원천 조회. 테이블/컬럼 이름은 {@link BackfillFactType} 상수에서만 오며 사용자 입력이 아니다.
 */
@Repository
public class StatsExportSourceQueries {

    private final JdbcTemplate jdbc;

    public StatsExportSourceQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long nextId(BackfillFactType factType) {
        Long next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(" + factType.sourceIdColumn() + "), 0) + 1 FROM " + factType.sourceTable(), Long.class);
        return next == null ? 1L : next;
    }

    /** [start, end] (양끝 포함) id 범위 행이 속한 매치 중 지정 큐의 최대 패치. */
    public Optional<PatchVersion> maxPatchInRange(BackfillFactType factType, long start, long end, List<Integer> queueIds) {
        if (queueIds.isEmpty()) {
            return Optional.empty();
        }
        String placeholders = String.join(", ", queueIds.stream().map(q -> "?").toList());
        boolean direct = factType == BackfillFactType.MATCH;
        String from = direct ? "match m" : factType.sourceTable() + " x JOIN match m ON m.match_id = x.match_id";
        String idRef = (direct ? "m." : "x.") + factType.sourceIdColumn();
        String sql = "SELECT m.patch_version FROM " + from
                + " WHERE " + idRef + " BETWEEN ? AND ? AND m.queue_id IN (" + placeholders + ")"
                + " AND m.patch_version IS NOT NULL"
                + " ORDER BY split_part(m.patch_version, '.', 1)::int DESC, split_part(m.patch_version, '.', 2)::int DESC"
                + " LIMIT 1";
        List<Object> args = new ArrayList<>();
        args.add(start);
        args.add(end);
        args.addAll(queueIds);
        return jdbc.queryForList(sql, String.class, args.toArray()).stream().findFirst().map(PatchVersion::parse);
    }

    public List<PatchVersion> latestSeasonPatches(int limit) {
        return jdbc.queryForList("""
                SELECT pv.version_value
                FROM patch_version pv
                JOIN season s ON s.season_id = pv.season_id
                WHERE s.season_value = (SELECT MAX(season_value) FROM season)
                ORDER BY split_part(pv.version_value, '.', 1)::int DESC, split_part(pv.version_value, '.', 2)::int DESC
                LIMIT ?
                """, String.class, limit).stream().map(PatchVersion::parse).toList();
    }

    public ReferenceDataStatus referenceData(int season) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE type = 'LEGENDARY') AS legendary,
                       COUNT(*) FILTER (WHERE type = 'BOOTS') AS boots,
                       COUNT(*) FILTER (WHERE type IN ('TRINKET', 'STARTING_EXCLUSION')) AS starting_exclusion
                FROM item_meta
                WHERE season = ? AND active
                """, (rs, i) -> new ReferenceDataStatus(rs.getInt("legendary"), rs.getInt("boots"),
                rs.getInt("starting_exclusion")), season);
    }
}
