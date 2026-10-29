package com.mmrtr.lol.infra.persistence;

import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 빈 DB 에 이 레포의 마이그레이션 체인(db/migration/riot)만 적용해 riot 스키마 단독으로 동작하는지 검증한다.
 * ddl-auto=validate 로 띄우므로 컨텍스트가 뜨면 모든 엔티티가 riot 테이블과 맞는다는 뜻이다 (운영 설정과 같다).
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class RiotSchemaMigrationTest {

    @Autowired
    private EntityManager entityManager;

    @DisplayName("연결의 현재 스키마와 Flyway 이력은 riot 이다")
    @Test
    void migration_freshDatabase_usesRiotSchemaOnly() {
        // when
        Object currentSchema = single("SELECT current_schema()");
        Object appliedVersion = single("SELECT max(version::int) FROM riot.flyway_schema_history WHERE success");

        // then
        assertThat(currentSchema).isEqualTo("riot");
        assertThat(((Number) appliedVersion).intValue()).isGreaterThanOrEqualTo(1);
    }

    @DisplayName("riot 외 스키마에는 테이블을 만들지 않고, lol-server 소유 테이블(member·community·duo)은 없다")
    @Test
    void migration_freshDatabase_createsNoTablesOutsideRiot() {
        // when
        List<Object> outsideRiot = list("""
                SELECT table_schema || '.' || table_name FROM information_schema.tables
                WHERE table_schema NOT IN ('riot', 'pg_catalog', 'information_schema')
                """);
        List<Object> appTables = list("""
                SELECT table_name FROM information_schema.tables
                WHERE table_name IN ('member', 'member_withdrawal', 'social_account')
                   OR table_name LIKE 'community\\_%' OR table_name LIKE 'duo\\_%'
                """);

        // then
        assertThat(outsideRiot).isEmpty();
        assertThat(appTables).isEmpty();
    }

    @DisplayName("기준 데이터(item_meta)와 Spring Batch 시퀀스가 riot 에 있다")
    @Test
    void migration_freshDatabase_seedsReferenceDataAndBatchSequences() {
        // when
        Object itemMetaRows = single("SELECT count(*) FROM item_meta");
        List<Object> batchSequences = list("""
                SELECT sequence_name FROM information_schema.sequences
                WHERE sequence_schema = 'riot' AND sequence_name LIKE 'batch\\_%'
                """);

        // then
        assertThat(((Number) itemMetaRows).longValue()).isEqualTo(294);
        assertThat(batchSequences).containsExactlyInAnyOrder(
                "batch_job_seq", "batch_job_execution_seq", "batch_step_execution_seq");
    }

    @DisplayName("match.tier_bucket 은 average_tier 에서 파생되고 챌린저 이상은 10000 하나로 묶인다")
    @Test
    void migration_tierBucket_derivesFromAverageTier() {
        // given
        insert("match", Map.of("match_id", "T1", "average_tier", 6543));
        insert("match", Map.of("match_id", "T2", "average_tier", 11022));

        // when
        Object bucketOfT1 = single("SELECT tier_bucket FROM match WHERE match_id = 'T1'");
        Object bucketOfT2 = single("SELECT tier_bucket FROM match WHERE match_id = 'T2'");

        // then
        assertThat(bucketOfT1).isEqualTo(6000);
        assertThat(bucketOfT2).isEqualTo(10000);
    }

    @DisplayName("stats_export_watermark 는 설계 5.1 컬럼과 V5 가드 컬럼(observed_at·export_epoch)을 가진다")
    @Test
    void migration_createsStatsExportWatermark() {
        // when
        List<Object> columns = list("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'riot' AND table_name = 'stats_export_watermark'
                """);
        Object rangePatchesComment = single("""
                SELECT col_description('riot.stats_export_watermark'::regclass, ordinal_position::int)
                FROM information_schema.columns
                WHERE table_schema = 'riot' AND table_name = 'stats_export_watermark' AND column_name = 'range_patches'
                """);

        // then
        assertThat(columns).containsExactlyInAnyOrder("fact_type", "exported_until", "observed_until",
                "range_patches", "last_run_id", "last_success_at", "updated_at", "observed_at", "export_epoch");
        assertThat((String) rangePatchesComment).contains("16.21");
    }

    @DisplayName("16시즌 BOOTS 는 소환사의 협곡 구매 가능 신발 14종이다")
    @Test
    void migration_seedsSeason16Boots() {
        // when
        List<Object> bootIds = list("SELECT item_id FROM item_meta WHERE season = 16 AND type = 'BOOTS' AND active");

        // then
        assertThat(bootIds).containsExactlyInAnyOrder(1001, 3006, 3008, 3009, 3020, 3047, 3111, 3158,
                3168, 3170, 3171, 3173, 3174, 3175);
    }

    private Object single(String sql) {
        return entityManager.createNativeQuery(sql).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(String sql) {
        return entityManager.createNativeQuery(sql).getResultList();
    }

    /** 기본값 없는 NOT NULL 컬럼은 타입별 0 값으로 채우고, 테스트에 필요한 컬럼만 지정한다. */
    @SuppressWarnings("unchecked")
    private void insert(String table, Map<String, Object> values) {
        List<Object[]> requiredColumns = entityManager.createNativeQuery("""
                        SELECT column_name, data_type FROM information_schema.columns
                        WHERE table_schema = current_schema() AND table_name = :table
                          AND is_nullable = 'NO' AND column_default IS NULL AND is_identity = 'NO'
                          AND is_generated = 'NEVER'
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
