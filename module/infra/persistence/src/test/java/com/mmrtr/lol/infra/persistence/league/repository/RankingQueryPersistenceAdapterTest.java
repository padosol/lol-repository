package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import com.mmrtr.lol.support.page.PageResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 마이그레이션(Flyway) 스키마에서 리더보드·티어 컷오프 조회 — 정렬·필터·페이지 메타데이터. */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, RankingQueryPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RankingQueryPersistenceAdapterTest {

    @Autowired
    private RankingQueryPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // given
        sql("DELETE FROM summoner_ranking");
        sql("DELETE FROM tier_cutoff");

        // KR 솔로 3명(삽입 순서와 순위가 다르다) + KR 자유 1명 + 다른 플랫폼 1명
        sql("INSERT INTO summoner_ranking (puuid, queue, platform_id, current_rank, rank_change, game_name, tag_line, "
                + "most_champion1, wins, losses, win_rate, tier, rank, league_points) VALUES "
                + "('p3', 'RANKED_SOLO_5x5', 'KR', 3, 0, 'third', 'KR1', 'Zed', 10, 10, 50.00, 'GRANDMASTER', 'I', 900), "
                + "('p1', 'RANKED_SOLO_5x5', 'KR', 1, 2, 'first', 'KR1', 'Ahri', 30, 10, 75.00, 'CHALLENGER', 'I', 1500), "
                + "('p2', 'RANKED_SOLO_5x5', 'KR', 2, -1, 'second', 'KR1', NULL, 20, 10, 66.67, 'CHALLENGER', 'I', 1300), "
                + "('f1', 'RANKED_FLEX_SR', 'KR', 1, 0, 'flex', 'KR1', NULL, 5, 5, 50.00, 'CHALLENGER', 'I', 800), "
                + "('n1', 'RANKED_SOLO_5x5', 'NA1', 1, 0, 'na', 'NA1', NULL, 5, 5, 50.00, 'CHALLENGER', 'I', 1000)");
        sql("INSERT INTO tier_cutoff (queue, tier, platform_id, min_league_points, lp_change, user_count, updated_at) VALUES "
                + "('RANKED_SOLO_5x5', 'CHALLENGER', 'KR', 1500, 10, 300, '2026-01-15 12:00:00'), "
                + "('RANKED_SOLO_5x5', 'GRANDMASTER', 'KR', 800, 5, 700, '2026-01-15 12:00:00'), "
                + "('RANKED_FLEX_SR', 'CHALLENGER', 'KR', 1200, -3, 200, '2026-01-15 12:00:00'), "
                + "('RANKED_SOLO_5x5', 'CHALLENGER', 'NA1', 1000, 0, 300, '2026-01-15 12:00:00')");
    }

    @DisplayName("리더보드: 플랫폼·큐로 거르고 순위 오름차순, 페이지 메타데이터는 1부터")
    @Test
    void findRankings_noTier_returnsPlatformQueuePagesByRank() {
        // when
        PageResult<SummonerRanking> first = adapter.findRankings("KR", "RANKED_SOLO_5x5", null, 1, 2);
        PageResult<SummonerRanking> second = adapter.findRankings("KR", "RANKED_SOLO_5x5", null, 2, 2);

        // then
        assertThat(first.content()).extracting(SummonerRanking::getPuuid).containsExactly("p1", "p2");
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.isFirst()).isTrue();
        assertThat(first.isLast()).isFalse();
        assertThat(second.content()).extracting(SummonerRanking::getPuuid).containsExactly("p3");
        assertThat(second.isLast()).isTrue();
    }

    @DisplayName("리더보드: tier 필터")
    @Test
    void findRankings_withTier_returnsOnlyThatTier() {
        // when
        PageResult<SummonerRanking> grandmasters = adapter.findRankings("KR", "RANKED_SOLO_5x5", "GRANDMASTER", 1, 50);

        // then
        assertThat(grandmasters.content()).extracting(SummonerRanking::getPuuid).containsExactly("p3");
        assertThat(grandmasters.content().get(0).getMostChampion1()).isEqualTo("Zed");
    }

    @DisplayName("티어 컷오프: 플랫폼 전체 / 큐 / 큐+티어, id 오름차순")
    @Test
    void findTierCutoffs_byFilters_returnsMatchingCutoffsInIdOrder() {
        // when & then
        assertThat(adapter.findTierCutoffs("KR", null, null))
                .extracting(cutoff -> cutoff.getQueue() + ":" + cutoff.getTier())
                .containsExactly("RANKED_SOLO_5x5:CHALLENGER", "RANKED_SOLO_5x5:GRANDMASTER", "RANKED_FLEX_SR:CHALLENGER");
        assertThat(adapter.findTierCutoffs("KR", "RANKED_SOLO_5x5", null)).hasSize(2);
        assertThat(adapter.findTierCutoffs("KR", "RANKED_SOLO_5x5", "CHALLENGER")).singleElement()
                .satisfies(cutoff -> {
                    assertThat(cutoff.getMinLeaguePoints()).isEqualTo(1500);
                    assertThat(cutoff.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 12, 0));
                });
        assertThat(adapter.findTierCutoffs("KR", "RANKED_FLEX_SR", "GRANDMASTER")).isEmpty();
    }

    private void sql(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }
}
