package com.mmrtr.lol.infra.persistence.league.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("소환사 랭킹 스케줄러")
class SummonerRankingSchedulerTest {

    @Mock
    private SummonerRankingCalculationService summonerRankingCalculationService;

    @InjectMocks
    private SummonerRankingScheduler summonerRankingScheduler;

    @DisplayName("00시~02시 사이에 실행되면 티어 커트라인까지 갱신한다")
    @ParameterizedTest(name = "실행 시각 {0}")
    @ValueSource(strings = {"00:00", "00:00:01", "01:00", "01:59:59.999999999"})
    void execute_커트라인갱신시간대_커트라인갱신포함(String executedAt) {
        // given
        LocalTime time = LocalTime.parse(executedAt);

        // when
        summonerRankingScheduler.execute(time);

        // then
        verify(summonerRankingCalculationService).processQueueRanking("RANKED_SOLO_5x5", true);
    }

    @DisplayName("00시~02시 밖에서 실행되면 랭킹만 갱신하고 티어 커트라인은 건너뛴다")
    @ParameterizedTest(name = "실행 시각 {0}")
    @ValueSource(strings = {"02:00", "02:00:01", "12:00", "23:59:59"})
    void execute_커트라인갱신시간대아님_커트라인갱신제외(String executedAt) {
        // given
        LocalTime time = LocalTime.parse(executedAt);

        // when
        summonerRankingScheduler.execute(time);

        // then
        verify(summonerRankingCalculationService).processQueueRanking("RANKED_SOLO_5x5", false);
    }

    @DisplayName("티어 커트라인 갱신 시간대는 00:00(포함) ~ 02:00(제외)이다")
    @ParameterizedTest(name = "{0} → 갱신 시간대 여부 판정")
    @ValueSource(strings = {"00:00", "01:59:59.999999999", "02:00", "23:59:59.999999999"})
    void isTierCutoffWindow_경계값_00시부터02시직전까지만참(String time) {
        // given
        LocalTime executedAt = LocalTime.parse(time);
        boolean expected = executedAt.isBefore(LocalTime.of(2, 0));

        // when
        boolean actual = SummonerRankingScheduler.isTierCutoffWindow(executedAt);

        // then
        assertThat(actual).isEqualTo(expected);
    }
}
