package com.mmrtr.lol.infra.riot.exception;

import com.mmrtr.lol.support.riot.RiotCallPriority;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

/**
 * 로컬 한도 예산이 없어 Riot 에 보내지 않았다.
 * RetryInterceptor 가 재시도하지 않도록 RiotRateLimitException 을 상속하지 않는다.
 */
public class RiotBudgetExceededException extends RiotClientException {

    public RiotBudgetExceededException(String routing, RiotCallPriority priority) {
        super(HttpStatus.TOO_MANY_REQUESTS,
                "Riot local rate budget exceeded: routing=" + routing + ", priority=" + priority,
                LogLevel.WARN);
    }
}
