package com.mmrtr.lol.infra.persistence.spectator;

import com.mmrtr.lol.domain.spectator.domain.SpectatorTarget;
import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, SpectatorTargetPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SpectatorTargetPersistenceAdapterTest {

    @Autowired
    private SpectatorTargetPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @DisplayName("저장된 소환사의 platform_id·revision_date, 없으면 빈 값")
    @Test
    void findTarget_storedSummoner_returnsPlatformAndRevisionDate() {
        // given
        entityManager.createNativeQuery("DELETE FROM summoner").executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO summoner (puuid, profile_icon_id, summoner_level, platform_id, revision_date)
                        VALUES ('p-1', 0, 0, 'NA1', TIMESTAMP '2026-03-01 12:30:05')
                        """)
                .executeUpdate();

        // when & then
        assertThat(adapter.findTarget("p-1"))
                .contains(new SpectatorTarget("NA1", LocalDateTime.of(2026, 3, 1, 12, 30, 5)));
        assertThat(adapter.findTarget("ghost")).isEmpty();
    }
}
