package com.mmrtr.lol.infra.persistence.league.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("소환사 랭킹 주기 실행")
class SummonerRankingScheduleTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(SummonerRankingScheduler.class, () -> mock(SummonerRankingScheduler.class))
            .withConfiguration(UserConfigurations.of(SummonerRankingSchedule.class));

    @DisplayName("설정이 없으면 주기 실행 빈이 뜬다")
    @Test
    void 설정없음_주기실행켜짐() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(SummonerRankingSchedule.class));
    }

    @DisplayName("lol.ranking.scheduler.enabled=false 면 주기 실행 빈이 뜨지 않는다 (수동 실행용 스케줄러 빈은 남는다)")
    @Test
    void 꺼짐_주기실행빈없음() {
        contextRunner
                .withPropertyValues("lol.ranking.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SummonerRankingSchedule.class);
                    assertThat(context).hasSingleBean(SummonerRankingScheduler.class);
                });
    }

    @DisplayName("주기 실행은 랭킹 계산을 그대로 부른다")
    @Test
    void run_랭킹계산호출() {
        SummonerRankingScheduler scheduler = mock(SummonerRankingScheduler.class);

        new SummonerRankingSchedule(scheduler).run();

        verify(scheduler).execute();
    }
}
