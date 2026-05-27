package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncMatchSaver {

    private final SaveMatchDataUseCase saveMatchDataUseCase;
    private final Executor matchSaveExecutor;

    public void saveAsync(List<MatchDto> matches, List<TimelineDto> timelines) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                saveMatchDataUseCase.execute(matches, timelines);
            } catch (Exception e) {
                log.error("매치 비동기 DB 적재 실패. count={} cause={}",
                        matches.size(), e.getMessage(), e);
            }
        }, matchSaveExecutor);
    }
}
