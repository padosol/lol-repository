package com.mmrtr.lol.infra.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchRedisService {

    private static final String PENDING_KEY_PREFIX = "match:pending:";
    private static final String PROCESSING_KEY_PREFIX = "match:processing:";
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryMarkPending(String matchId) {
        try {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(PENDING_KEY_PREFIX + matchId, "1", PENDING_TTL);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("Redis pending 체크 실패, 처리 진행 (fail-open): {}", e.getMessage());
            return true;
        }
    }

    public boolean tryMarkProcessing(String matchId) {
        try {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(PROCESSING_KEY_PREFIX + matchId, "1", PROCESSING_TTL);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("Redis processing 체크 실패, 처리 진행 (fail-open): {}", e.getMessage());
            return true;
        }
    }

    /** 재전달될 메시지가 DUPLICATE 로 버려지지 않도록 처리 표시를 푼다. 실패해도 진행한다(fail-open). */
    public void releaseProcessing(String matchId) {
        try {
            stringRedisTemplate.delete(PROCESSING_KEY_PREFIX + matchId);
        } catch (Exception e) {
            log.warn("Redis processing 표시 해제 실패 (fail-open): {}", e.getMessage());
        }
    }
}
