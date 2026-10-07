package com.mmrtr.lol.infra.persistence.version;

import com.mmrtr.lol.domain.gamedata.domain.QueueInfo;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.SeasonReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 마이그레이션(Flyway) 스키마에서 게임 데이터 조회 — 정렬·시즌별 버전 묶음·jsonb 파싱·V35 컬럼. */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, GameDataPersistenceAdapter.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameDataPersistenceAdapterTest {

    @Autowired
    private GameDataPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        sql("DELETE FROM patch_note");
        sql("DELETE FROM patch_version");
        sql("DELETE FROM season");
        sql("DELETE FROM queue_data");

        sql("INSERT INTO season (season_id, season_value, season_name) VALUES (1, 2025, '2025 시즌'), (2, 2026, '2026 시즌')");
        sql("INSERT INTO patch_version (version_id, version_value, season_id, patch_version_data, created_at) VALUES "
                + "(10, '25.24', 1, '15.24.1', '2025-12-10 10:00:00'), "
                + "(11, '16.1', 2, NULL, '2026-01-08 10:00:00'), "
                + "(12, '16.2', 2, '16.2.1', '2026-01-22 10:00:00')");
        sql("INSERT INTO patch_note (version_id, title, content, patch_url, created_at) VALUES "
                + "('16.1', '패치 16.1', '{\"version\":\"16.1\",\"rift\":{\"champions\":[{\"targetName\":\"럭스\","
                + "\"type\":\"champion\",\"direction\":\"buff\",\"changes\":[{\"statName\":\"공격력\",\"before\":\"1\","
                + "\"after\":\"2\"}]}]},\"unknownField\":true}', 'https://example.com/16-1', '2026-01-08 09:00:00'), "
                + "('16.2', '패치 16.2', NULL, NULL, '2026-01-22 09:00:00')");
        sql("INSERT INTO queue_data (queue_id, queue_name, is_tab) VALUES (420, '솔로 랭크', true), (900, 'URF', false)");
    }

    @DisplayName("최근 버전은 최신순, patch_version_data(V35)를 읽는다")
    @Test
    void recentVersions() {
        List<VersionReadModel> versions = adapter.findRecentVersions(2);

        assertThat(versions).extracting(VersionReadModel::versionValue).containsExactly("16.2", "16.1");
        assertThat(versions.get(0).patchVersionData()).isEqualTo("16.2.1");
        assertThat(versions.get(1).patchVersionData()).isNull();
        assertThat(adapter.findLatestVersion()).map(VersionReadModel::versionId).contains(12L);
    }

    @DisplayName("시즌은 최신순, 시즌별 패치 버전도 최신순")
    @Test
    void seasons() {
        List<SeasonReadModel> seasons = adapter.findAllSeasons();

        assertThat(seasons).extracting(SeasonReadModel::seasonValue).containsExactly(2026, 2025);
        assertThat(seasons.get(0).patchVersions()).containsExactly("16.2", "16.1");
        assertThat(adapter.findSeasonById(1L)).map(SeasonReadModel::patchVersions).contains(List.of("25.24"));
        assertThat(adapter.findSeasonById(999L)).isEmpty();
    }

    @DisplayName("패치노트: 목록은 작성일 최신순·yyyy-MM-dd, 상세는 jsonb content 파싱(모르는 필드 무시)")
    @Test
    void patchNotes() {
        assertThat(adapter.findAllSummary())
                .extracting(summary -> summary.versionId() + "@" + summary.createdAt())
                .containsExactly("16.2@2026-01-22", "16.1@2026-01-08");

        PatchNoteReadModel detail = adapter.findByVersionId("16.1").orElseThrow();
        assertThat(detail.content().rift().champions().get(0).changes().get(0).after()).isEqualTo("2");
        assertThat(adapter.findByVersionId("16.2").orElseThrow().content()).isNull();
    }

    @DisplayName("큐: 탭 큐만 / 전체")
    @Test
    void queues() {
        assertThat(adapter.findAllTabs()).extracting(QueueInfo::queueId).containsExactly(420L);
        assertThat(adapter.findAll()).hasSize(2);
    }

    private void sql(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }
}
