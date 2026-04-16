package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchBatchProcessor {

    private final SaveMatchDataUseCase saveMatchDataUseCase;
    private final BlockingQueue<Pair<MatchDto, TimelineDto>> queue = new LinkedBlockingQueue<>();

    public void add(Pair<MatchDto, TimelineDto> pair) {
        queue.add(pair);
    }

    @Scheduled(fixedRate = 1000)
    public void flush() {
        List<Pair<MatchDto, TimelineDto>> pairs = new ArrayList<>();
        int count = queue.drainTo(pairs);
        if (count > 0) {
            log.debug("배치 저장 데이터 갯수: {}", count);

            List<MatchDto> matchDtos = new ArrayList<>();
            List<TimelineDto> timelineDtos = new ArrayList<>();
            for (Pair<MatchDto, TimelineDto> pair : pairs) {
                matchDtos.add(pair.getFirst());
                timelineDtos.add(pair.getSecond());
            }

            saveMatchDataUseCase.execute(matchDtos, timelineDtos);
        }
    }
}
