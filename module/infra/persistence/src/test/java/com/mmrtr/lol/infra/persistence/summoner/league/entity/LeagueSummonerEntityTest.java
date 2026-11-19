package com.mmrtr.lol.infra.persistence.summoner.league.entity;

import com.mmrtr.lol.domain.summoner.domain.LeagueSummoner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueSummonerEntityTest {

    @DisplayName("IRON~CHALLENGER 랭크 큐는 티어·디비전·LP 를 더해 절대 점수를 계산한다")
    @Test
    void fromDomain_rankedTierQueue_calculatesAbsolutePoints() {
        // when
        LeagueSummonerEntity entity = LeagueSummonerEntity.fromDomain(league("RANKED_SOLO_5x5", "GOLD", "II", 50));

        // then
        assertThat(entity.getAbsolutePoints()).isEqualTo(4250);
    }

    @DisplayName("티어 체계가 다른 큐(Arena CHERRY 등)는 절대 점수를 0 으로 두고 Riot 값을 그대로 둔다")
    @Test
    void fromDomain_otherTierSystemQueue_keepsRiotValuesWithZeroPoints() {
        // when
        LeagueSummonerEntity entity = LeagueSummonerEntity.fromDomain(league("CHERRY", "WOOD", null, 0));

        // then
        assertThat(entity.getAbsolutePoints()).isZero();
        assertThat(entity.getTier()).isEqualTo("WOOD");
        assertThat(entity.getRank()).isNull();
    }

    private LeagueSummoner league(String queue, String tier, String rank, int leaguePoints) {
        return LeagueSummoner.builder()
                .puuid("p1").queue(queue).tier(tier).rank(rank).leaguePoints(leaguePoints)
                .build();
    }
}
