package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import com.mmrtr.lol.infra.persistence.league.repository.MostChampionProjection;
import com.mmrtr.lol.infra.persistence.match.entity.MatchParticipantEntity;
import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.ArenaValue;
import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.ItemValue;
import com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner.PerkValue;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchSummonerJpaRepositoryTest {

    @Autowired
    private MatchSummonerJpaRepository matchSummonerJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private int matchSequence;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("DELETE FROM match_participant").executeUpdate();
    }

    @DisplayName("모스트 챔피언: 요청한 소환사마다 판 수 상위 3개만 소환사 → 판 수 내림차순으로 반환한다")
    @Test
    void findMostChampionsByPuuids_returnsTopThreePerPuuid() {
        // given
        play("a", "Ahri", 4);
        play("a", "Zed", 3);
        play("a", "Lux", 2);
        play("a", "Yasuo", 1);
        play("b", "Garen", 1);
        play("c", "Teemo", 5);
        flushAndClear();

        // when
        List<MostChampionProjection> result = matchSummonerJpaRepository.findMostChampionsByPuuids(List.of("a", "b"));

        // then
        assertThat(result)
                .extracting(MostChampionProjection::getPuuid, MostChampionProjection::getChampionName,
                        MostChampionProjection::getPlayCount)
                .containsExactly(
                        tuple("a", "Ahri", 4L),
                        tuple("a", "Zed", 3L),
                        tuple("a", "Lux", 2L),
                        tuple("b", "Garen", 1L));
    }

    @DisplayName("모스트 챔피언: 판 수가 같으면 챔피언 이름순으로 3개를 고른다")
    @Test
    void findMostChampionsByPuuids_samePlayCount_ordersByChampionName() {
        // given
        play("a", "Zed", 2);
        play("a", "Yasuo", 2);
        play("a", "Lux", 2);
        play("a", "Ahri", 2);
        flushAndClear();

        // when
        List<MostChampionProjection> result = matchSummonerJpaRepository.findMostChampionsByPuuids(List.of("a"));

        // then
        assertThat(result).extracting(MostChampionProjection::getChampionName)
                .containsExactly("Ahri", "Lux", "Yasuo");
    }

    private void play(String puuid, String championName, int games) {
        for (int i = 0; i < games; i++) {
            entityManager.persist(MatchParticipantEntity.builder()
                    .puuid(puuid)
                    .matchId("KR_" + matchSequence++)
                    .championName(championName)
                    .item(new ItemValue())
                    .perk(new PerkValue())
                    .arena(new ArenaValue())
                    .build());
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
