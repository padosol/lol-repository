package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import com.mmrtr.lol.infra.persistence.summoner.entity.SummonerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, SummonerRepositoryImpl.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SummonerRepositoryImplTest {

    private static final LocalDateTime REVISION = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime LAST_CALL = LocalDateTime.of(2026, 1, 2, 0, 0);

    @Autowired
    private SummonerRepositoryImpl repository;

    @Autowired
    private SummonerJpaRepository jpa;

    @BeforeEach
    void setUp() {
        // given
        jpa.deleteAll();
        jpa.saveAndFlush(SummonerEntity.fromDomain(Summoner.builder()
                .puuid("p1")
                .gameIdentity(new GameIdentity("Old Name", "KR1"))
                .platformId("KR")
                .statusInfo(new StatusInfo(1, 30L))
                .revisionInfo(new RevisionInfo(REVISION, LAST_CALL))
                .leagueInfos(Set.of())
                .build()));
    }

    @Test
    @DisplayName("상태 갱신은 아이콘과 레벨만 바꾸고 revisionDate·lastRiotCallDate 는 그대로 둔다")
    void updateStatusInfo_existing_updatesOnlyIconAndLevel() {
        // when
        boolean updated = repository.updateStatusInfo("p1", 7, 300L);

        // then
        assertThat(updated).isTrue();
        SummonerEntity e = jpa.findById("p1").orElseThrow();
        assertThat(e.getProfileIconId()).isEqualTo(7);
        assertThat(e.getSummonerLevel()).isEqualTo(300L);
        assertThat(e.getRevisionDate()).isEqualTo(REVISION);
        assertThat(e.getLastRiotCallDate()).isEqualTo(LAST_CALL);
        assertThat(e.getGameName()).isEqualTo("Old Name");
    }

    @Test
    @DisplayName("없는 소환사의 상태 갱신은 false 다")
    void updateStatusInfo_missing_false() {
        // when / then
        assertThat(repository.updateStatusInfo("nope", 1, 1L)).isFalse();
    }

    @Test
    @DisplayName("계정 갱신은 이름·태그·searchName 만 fromDomain 규칙으로 바꾼다")
    void updateGameIdentity_updatesNameTagAndSearchName() {
        // when
        repository.updateGameIdentity("p1", "Hide On Bush", "KR9");

        // then
        SummonerEntity e = jpa.findById("p1").orElseThrow();
        assertThat(e.getGameName()).isEqualTo("Hide On Bush");
        assertThat(e.getTagLine()).isEqualTo("KR9");
        assertThat(e.getSearchName()).isEqualTo("hideonbush#kr9");
        assertThat(e.getProfileIconId()).isEqualTo(1);
        assertThat(e.getRevisionDate()).isEqualTo(REVISION);
        assertThat(e.getLastRiotCallDate()).isEqualTo(LAST_CALL);
    }

    @Test
    @DisplayName("revisionDate 갱신은 revisionDate 만 바꾸고 lastRiotCallDate 는 그대로 둔다")
    void updateRevisionDate_updatesOnlyRevisionDate() {
        // given
        LocalDateTime next = LocalDateTime.of(2026, 5, 5, 5, 5);

        // when
        repository.updateRevisionDate("p1", next);

        // then
        SummonerEntity e = jpa.findById("p1").orElseThrow();
        assertThat(e.getRevisionDate()).isEqualTo(next);
        assertThat(e.getLastRiotCallDate()).isEqualTo(LAST_CALL);
    }
}
