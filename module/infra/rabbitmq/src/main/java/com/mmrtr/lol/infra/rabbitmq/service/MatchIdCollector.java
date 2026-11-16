package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchIdCollector {

    private static final int DEFAULT_COUNT = 100;
    private static final int DEFAULT_MAX_PAGES = 10;

    private final MatchApiPort matchApiPort;

    public List<String> collectAll(
            String puuid, String platformName, long startTime, int startOffset, Executor executor) {
        return collectAll(puuid, platformName, startTime, startOffset, DEFAULT_COUNT, DEFAULT_MAX_PAGES, executor);
    }

    public List<String> collectAll(
            String puuid, String platformName, long startTime,
            int startOffset, int count, int maxPages, Executor executor) {

        List<String> allMatchIds = new ArrayList<>();
        int offset = startOffset;

        for (int i = 0; i < maxPages; i++) {
            List<String> fetched = matchApiPort.fetchMatchIdsByPuuid(
                    puuid, platformName, startTime, offset, count, executor
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
        return allMatchIds;
    }
}
