package com.mmrtr.lol.domain.summoner.readmodel;

import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel.LeagueEntry;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel.LeagueRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** lol-server {@code LeagueMapper} 의 분류·oow 형식과 같은지. */
class SummonerLeagueReadModelTest {

    @DisplayName("현재 랭크 oow 는 백분율(소수 둘째 자리), 이력 oow 는 비율 — lol-server 와 같은 문자열")
    @Test
    void of_currentAndHistory_formatsOowLikeLolServer() {
        // when
        SummonerLeagueReadModel result = SummonerLeagueReadModel.of(
                List.of(record("RANKED_SOLO_5x5", 2, 1), record("RANKED_FLEX_SR", 1, 0)),
                List.of(record("RANKED_SOLO_5x5", 1, 2), record("RANKED_FLEX_SR", 3, 3)));

        // then
        assertThat(result.soloLeague()).isEqualTo(new LeagueEntry("RANKED_SOLO_5x5", 50, 2, 1, "67.00", "GOLD", "II"));
        assertThat(result.flexLeague().oow()).isEqualTo("100.00");
        assertThat(result.soloLeagueHistory()).extracting(LeagueEntry::oow).containsExactly("0.33");
        assertThat(result.flexLeagueHistory()).extracting(LeagueEntry::oow).containsExactly("0.50");
    }

    @DisplayName("0판이면 현재·이력 모두 \"0\" (lol-server 이력은 0 나누기로 500)")
    @Test
    void of_noGames_returnsZero() {
        // when
        SummonerLeagueReadModel result = SummonerLeagueReadModel.of(
                List.of(record("RANKED_SOLO_5x5", 0, 0)), List.of(record("RANKED_SOLO_5x5", 0, 0)));

        // then
        assertThat(result.soloLeague().oow()).isEqualTo("0");
        assertThat(result.soloLeagueHistory()).extracting(LeagueEntry::oow).containsExactly("0");
    }

    @DisplayName("솔로 랭크가 아닌 큐는 자유 랭크로, 이력은 넘긴 순서를 지킨다. 랭크가 없으면 null·빈 목록")
    @Test
    void of_otherQueuesAndOrder_classifiesAsFlexAndKeepsOrder() {
        // when
        SummonerLeagueReadModel result = SummonerLeagueReadModel.of(
                List.of(record("RANKED_FLEX_TT", 1, 1)),
                List.of(record("RANKED_SOLO_5x5", 3, 1), record("RANKED_SOLO_5x5", 1, 3)));
        SummonerLeagueReadModel empty = SummonerLeagueReadModel.of(List.of(), List.of());

        // then
        assertThat(result.flexLeague().leagueType()).isEqualTo("RANKED_FLEX_TT");
        assertThat(result.soloLeague()).isNull();
        assertThat(result.soloLeagueHistory()).extracting(LeagueEntry::oow).containsExactly("0.75", "0.25");
        assertThat(empty).isEqualTo(new SummonerLeagueReadModel(null, null, List.of(), List.of()));
    }

    private LeagueRecord record(String queue, int wins, int losses) {
        return new LeagueRecord(queue, 50, wins, losses, "GOLD", "II");
    }
}
