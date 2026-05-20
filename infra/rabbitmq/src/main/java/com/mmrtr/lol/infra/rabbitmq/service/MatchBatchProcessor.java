package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchCacheEvictPort;
import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchBatchProcessor {

    private final SaveMatchDataUseCase saveMatchDataUseCase;
    private final MatchCacheEvictPort matchCacheEvictPort;
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

            // 저장 성공 후 best-effort 캐시 무효화. 실패해도 저장 트랜잭션엔 영향 없음.
            try {
                matchCacheEvictPort.evictByPuuids(extractPuuids(matchDtos));
            } catch (Exception e) {
                log.warn("cache evict 중 예외 발생, 저장은 정상 완료: {}", e.getMessage());
            }
        }
    }

    private Set<String> extractPuuids(List<MatchDto> matchDtos) {
        Set<String> puuids = new HashSet<>();
        for (MatchDto matchDto : matchDtos) {
            if (matchDto == null || matchDto.getInfo() == null) {
                continue;
            }
            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();
            if (participants == null) {
                continue;
            }
            for (ParticipantDto participant : participants) {
                String puuid = participant.getPuuid();
                if (puuid != null && !puuid.isBlank()) {
                    puuids.add(puuid);
                }
            }
        }
        return puuids;
    }
}
