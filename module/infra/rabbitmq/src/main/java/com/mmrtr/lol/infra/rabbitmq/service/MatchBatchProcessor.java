package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // prefetch=1 × concurrent=20 = 최대 20 in-flight. flush 지연 대비 25배 안전치(500).
    private static final int QUEUE_CAPACITY = 500;
    private static final int DRAIN_LIMIT = 1000;

    private final SaveMatchDataUseCase saveMatchDataUseCase;
    private final BlockingQueue<Item> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    /** 적재 항목. match 가 null 이면 타임라인만 저장하는 항목이다 (매치는 이미 DB 에 있음). */
    private record Item(MatchDto match, TimelineDto timeline) {
    }

    /**
     * 비차단 enqueue. 큐가 가득 차면 false 를 반환해 호출자가 RabbitMQ requeue 로 backpressure 를 걸도록 한다.
     */
    public boolean add(MatchDto match, TimelineDto timeline) {
        return queue.offer(new Item(match, timeline));
    }

    @Scheduled(fixedRate = 1000)
    public void flush() {
        List<Item> items = new ArrayList<>();
        int count = queue.drainTo(items, DRAIN_LIMIT);
        if (count > 0) {
            log.debug("배치 저장 데이터 갯수: {}", count);

            List<MatchDto> matchDtos = new ArrayList<>();
            List<TimelineDto> timelineDtos = new ArrayList<>();
            for (Item item : items) {
                if (item.match() != null) {
                    matchDtos.add(item.match());
                }
                if (item.timeline() != null) {
                    timelineDtos.add(item.timeline());
                }
            }

            saveMatchDataUseCase.execute(matchDtos, timelineDtos);
        }
    }
}
