package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.infra.rabbitmq.listener.RiotFailures;
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
     * @param ids  모은 매치 id
     * @param stop 끝까지 가지 못하고 멈췄다면 멈춘 페이지. 끝까지 갔으면 비어 있다.
     */
    public record Collected(List<String> ids, Optional<Stop> stop) {
    }

    /**
     * 멈춘 페이지.
     *
     * @param offset 멈춘 페이지의 offset (여기부터 이어서 찾는다)
     * @param reason 멈춘 까닭
     * @param cause  일시 오류면 그 예외, 허가 거절이면 null
     */
    public record Stop(int offset, StopReason reason, Throwable cause) {
    }

    public enum StopReason {
        /** 허가가 거절됐다. */
        DENIED,
        /** 429·5xx·IO·시간 초과 같은 일시 오류. */
        TRANSIENT
    }

    public Collected collectAll(String puuid, String platformName, long startTime, int startOffset,
                                RiotCallPriority priority, Executor executor) {
        return collectAll(puuid, platformName, startTime, startOffset, DEFAULT_COUNT, DEFAULT_MAX_PAGES, priority,
                executor);
    }

    /**
     * 페이지마다 priority 로 허가 1건을 받는다. 허가가 거절되거나 조회가 일시 오류면 그 페이지에서 멈추고 앞 페이지까지 모은
     * id 와 멈춘 offset 을 돌려준다. 그 밖의 조회 실패(4xx 등)는 던진다.
     */
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
                return new Collected(allMatchIds, Optional.of(new Stop(offset, StopReason.DENIED, null)));
            }
            RiotPermits permits = granted.get();
            List<String> fetched;
            try {
                fetched = matchApiPort.fetchMatchIdsByPuuid(
                        puuid, platformName, startTime, offset, count, permits, executor
                ).join();
            } catch (RuntimeException e) {
                if (!RiotFailures.isTransient(e)) {
                    throw e;
                }
                log.warn("matchId 수집 일시 오류: puuid={}, offset={}, count={}, cause={}",
                        puuid, offset, allMatchIds.size(), e.toString());
                return new Collected(allMatchIds, Optional.of(new Stop(offset, StopReason.TRANSIENT, e)));
            }

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
        return new Collected(allMatchIds, Optional.empty());
    }
}
