package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchIdCollector {

    private static final int DEFAULT_COUNT = 100;
    private static final int DEFAULT_MAX_PAGES = 10;

    private final MatchApiPort matchApiPort;
    private final RiotPermitPort permitPort;

    /**
     * @param ids            모은 매치 id
     * @param deniedAtOffset 허가가 거절돼 멈췄다면 거절된 페이지의 offset. 끝까지 갔으면 비어 있다.
     */
    public record Collected(List<String> ids, OptionalInt deniedAtOffset) {
    }

    public Collected collectAll(String puuid, String platformName, long startTime, int startOffset,
                                RiotCallPriority priority, Executor executor) {
        return collectAll(puuid, platformName, startTime, startOffset, DEFAULT_COUNT, DEFAULT_MAX_PAGES, priority,
                executor);
    }

    /** 페이지마다 priority 로 허가 1건을 받는다. 거절되면 그 페이지에서 멈추고 거절된 offset 을 돌려준다. */
    public Collected collectAll(
            String puuid, String platformName, long startTime,
            int startOffset, int count, int maxPages, RiotCallPriority priority, Executor executor) {

        List<String> allMatchIds = new ArrayList<>();
        int offset = startOffset;

        for (int i = 0; i < maxPages; i++) {
            Optional<RiotPermits> granted = permitPort
                    .tryAcquire(platformName, priority, Map.of(RiotMethod.MATCH_IDS_BY_PUUID, 1));
            if (granted.isEmpty()) {
                log.debug("matchId 수집 허가 거절: puuid={}, offset={}, count={}", puuid, offset, allMatchIds.size());
                return new Collected(allMatchIds, OptionalInt.of(offset));
            }
            RiotPermits permits = granted.get();
            List<String> fetched = matchApiPort.fetchMatchIdsByPuuid(
                    puuid, platformName, startTime, offset, count, permits, executor
            ).join();

            if (fetched == null || fetched.isEmpty()) {
                break;
            }

            allMatchIds.addAll(fetched);

            if (fetched.size() < count) {
                break;
            }
            offset += count;
        }

        log.debug("matchId 수집 완료: puuid={}, count={}", puuid, allMatchIds.size());
        return new Collected(allMatchIds, OptionalInt.empty());
    }
}
