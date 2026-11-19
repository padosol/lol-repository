package com.mmrtr.lol.infra.persistence.ranking.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.domain.ranking.application.usecase.TriggerSummonerRankingCalculationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("소환사 랭킹 주기 실행")
class SummonerRankingScheduleTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(TriggerSummonerRankingCalculationUseCase.class, () -> mock(TriggerSummonerRankingCalculationUseCase.class))
            .withConfiguration(UserConfigurations.of(SummonerRankingSchedule.class));

    @DisplayName("설정이 없으면 주기 실행 빈이 뜬다")
    @Test
    void 설정없음_주기실행켜짐() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(SummonerRankingSchedule.class));
    }

    @DisplayName("lol.ranking.scheduler.enabled=false 면 주기 실행 빈이 뜨지 않는다 (수동 실행용 트리거 빈은 남는다)")
    @Test
    void 꺼짐_주기실행빈없음() {
        contextRunner
                .withPropertyValues("lol.ranking.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SummonerRankingSchedule.class);
                    assertThat(context).hasSingleBean(TriggerSummonerRankingCalculationUseCase.class);
                });
    }

    @DisplayName("주기 실행은 랭킹 계산 트리거를 그대로 부른다")
    @Test
    void run은_트리거를_부른다() {
        TriggerSummonerRankingCalculationUseCase trigger = mock(TriggerSummonerRankingCalculationUseCase.class);

        new SummonerRankingSchedule(trigger).run();

        verify(trigger).execute();
    }
}
