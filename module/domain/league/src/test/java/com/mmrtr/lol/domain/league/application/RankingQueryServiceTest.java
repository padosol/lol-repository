package com.mmrtr.lol.domain.league.application;

import com.mmrtr.lol.domain.league.application.port.RankingQueryPort;
import com.mmrtr.lol.domain.league.domain.RankingQueue;
import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.domain.league.readmodel.RankingReadModel;
import com.mmrtr.lol.domain.league.readmodel.TierCutoffReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.page.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RankingQueryServiceTest {

    private final RankingQueryPort rankingQueryPort = mock(RankingQueryPort.class);
    private final RankingQueryService service = new RankingQueryService(rankingQueryPort);

    @DisplayName("리더보드: platform 이름은 저장 값으로, queue 는 큐 이름으로, tier 는 대문자로 바꿔 50개씩 조회")
    @Test
    void rankingsQueryConditions() {
        given(rankingQueryPort.findRankings("NA1", "RANKED_FLEX_SR", "GOLD", 2, 50))
                .willReturn(new PageResult<>(List.of(), 2, 50, 0, 0, false, true));

        PageResult<RankingReadModel> result = service.getRankings("na", RankingQueue.FLEX, "gold", 2);

        assertThat(result.page()).isEqualTo(2);
        verify(rankingQueryPort).findRankings("NA1", "RANKED_FLEX_SR", "GOLD", 2, 50);
    }

    @DisplayName("리더보드: 빈 tier 는 필터 없음, 모스트 챔피언은 비어 있지 않은 것만 순서대로")
    @Test
    void rankingsMapping() {
        SummonerRanking ranking = SummonerRanking.builder()
                .puuid("puuid-1").currentRank(1).tier("CHALLENGER")
                .mostChampion1("Ahri").mostChampion2("").mostChampion3("Zed")
                .build();
        given(rankingQueryPort.findRankings("KR", "RANKED_SOLO_5x5", null, 1, 50))
                .willReturn(new PageResult<>(List.of(ranking), 1, 50, 1, 1, true, true));

        PageResult<RankingReadModel> result = service.getRankings("kr", RankingQueue.SOLO, " ", 1);

        assertThat(result.content()).singleElement()
                .extracting(RankingReadModel::champions)
                .isEqualTo(List.of("Ahri", "Zed"));
    }

    @DisplayName("리더보드: page 1 미만·모르는 platform 은 500 이 아니라 INVALID_INPUT")
    @Test
    void rankingsInvalidInput() {
        assertThatThrownBy(() -> service.getRankings("kr", RankingQueue.SOLO, null, 0))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.INVALID_INPUT);
        assertThatThrownBy(() -> service.getRankings("xx", RankingQueue.SOLO, null, 1))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.INVALID_INPUT);
        verify(rankingQueryPort, never()).findRankings(any(), any(), any(), anyInt(), anyInt());
    }

    @DisplayName("티어 컷오프: queue 는 대소문자 무시로 큐 이름 표기(RANKED_SOLO_5x5)에 맞추고 tier 는 대문자로")
    @Test
    void tierCutoffsQueryConditions() {
        service.getTierCutoffs("kr", "ranked_solo_5X5", null);
        service.getTierCutoffs("kr", "RANKED_SOLO_5X5", "challenger");
        service.getTierCutoffs("kr", null, null);

        verify(rankingQueryPort).findTierCutoffs("KR", "RANKED_SOLO_5x5", null);
        verify(rankingQueryPort).findTierCutoffs("KR", "RANKED_SOLO_5x5", "CHALLENGER");
        verify(rankingQueryPort).findTierCutoffs("KR", null, null);
    }

    @DisplayName("티어 컷오프: updatedAt 은 lol-server 와 같은 yyyy-MM-dd HH:mm:ss 문자열")
    @Test
    void tierCutoffsMapping() {
        TierCutoff cutoff = TierCutoff.builder()
                .id(1L).queue("RANKED_SOLO_5x5").tier("CHALLENGER").platformId("KR")
                .minLeaguePoints(1500).lpChange(-3).userCount(300)
                .updatedAt(LocalDateTime.of(2026, 1, 15, 12, 0, 5))
                .build();
        given(rankingQueryPort.findTierCutoffs("KR", null, null)).willReturn(List.of(cutoff));

        assertThat(service.getTierCutoffs("kr", null, null)).singleElement()
                .extracting(TierCutoffReadModel::updatedAt)
                .isEqualTo("2026-01-15 12:00:05");
    }

    @DisplayName("티어 컷오프: tier 만 주고 queue 를 빼면 INVALID_INPUT, 모르는 platform 도 INVALID_INPUT")
    @Test
    void tierCutoffsInvalidInput() {
        assertThatThrownBy(() -> service.getTierCutoffs("kr", null, "CHALLENGER"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.INVALID_INPUT);
        assertThatThrownBy(() -> service.getTierCutoffs("xx", null, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.INVALID_INPUT);
    }
}
