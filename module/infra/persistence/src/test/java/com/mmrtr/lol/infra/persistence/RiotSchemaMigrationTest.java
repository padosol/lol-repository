package com.mmrtr.lol.infra.persistence;

import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

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
        assertThat(((Number) itemMetaRows).longValue()).isEqualTo(280);
        assertThat(batchSequences).containsExactlyInAnyOrder(
                "batch_job_seq", "batch_job_execution_seq", "batch_step_execution_seq");
    }

    private Object single(String sql) {
        return entityManager.createNativeQuery(sql).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(String sql) {
        return entityManager.createNativeQuery(sql).getResultList();
    }
}
