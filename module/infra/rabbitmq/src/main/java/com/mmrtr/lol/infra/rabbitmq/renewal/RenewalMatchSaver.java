package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 갱신 매치 단계가 받은 매치·타임라인을 리스너 밖에서 DB 에 저장한다. 매치 단계는 요청자 캐시에 쓰면 끝나므로
 * 저장을 기다리지 않는다. 실행기는 {@code matchSaveExecutor}(10 스레드, 큐 0, 다 차면 부른 스레드가 실행)라
 * 동시 저장 수가 묶이고, 넘쳐도 버리지 않는다. 실패는 로그와 {@value #METRIC}{result=failure} 로만 남기고
 * 다시 하지 않는다 (스펙 8장).
 */
@Slf4j
@Component
class RenewalMatchSaver {

    static final String METRIC = "lol.renewal.match_save";

    private final SaveMatchDataUseCase saveMatchDataUseCase;
    private final Executor executor;
    private final MeterRegistry meterRegistry;

    RenewalMatchSaver(SaveMatchDataUseCase saveMatchDataUseCase,
                      @Qualifier("matchSaveExecutor") Executor executor, MeterRegistry meterRegistry) {
        this.saveMatchDataUseCase = saveMatchDataUseCase;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
    }

    /** 받은 매치·타임라인을 executor 에서 저장한다. 둘 다 비면 아무것도 하지 않는다. 어떤 경우에도 던지지 않는다. */
    void save(List<MatchDto> matches, List<TimelineDto> timelines) {
        if (matches.isEmpty() && timelines.isEmpty()) {
            return;
        }
        try {
            executor.execute(() -> run(matches, timelines));
        } catch (RuntimeException e) {
            log.error("갱신 매치 저장을 넘기지 못함. 그 매치는 누락된다. matches={}, first={}",
                    matches.size(), firstMatchId(matches), e);
            timer("failure").record(Duration.ZERO);
        }
    }

    private void run(List<MatchDto> matches, List<TimelineDto> timelines) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            saveMatchDataUseCase.execute(matches, timelines);
            sample.stop(timer("success"));
        } catch (RuntimeException e) {
            sample.stop(timer("failure"));
            log.error("갱신 매치 비동기 저장 실패. 그 매치는 누락된다. matches={}, timelines={}, first={}",
                    matches.size(), timelines.size(), firstMatchId(matches), e);
        }
    }

    private Timer timer(String result) {
        return meterRegistry.timer(METRIC, "result", result);
    }

    private static String firstMatchId(List<MatchDto> matches) {
        if (matches.isEmpty() || matches.get(0).getMetadata() == null) {
            return null;
        }
        return matches.get(0).getMetadata().getMatchId();
    }
}
