package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 리스너에서 발생한 예외를 "재시도할 가치가 있는가" 기준으로 분류한다.
 *
 * <p>예외를 삼키면 컨테이너가 정상 처리로 간주해 ACK 하므로, 큐에 선언된 DLX 가 있어도
 * 실패 메시지가 격리되지 않고 사라진다. 이 클래스는 그 판단을 한 곳에 모아
 * 재시도 가치가 있으면 그대로 전파하고(재시도 소진 시 DLQ), 무의미하면 즉시 DLQ 로 보낸다.
 *
 * <p>사용법 — 호출부에서 반드시 {@code throw} 와 함께 쓴다:
 * <pre>{@code
 * try {
 *     service.doSomething();
 * } catch (Exception e) {
 *     throw ListenerFailurePolicy.translate(e, "전적 갱신", puuid);
 * }
 * }</pre>
 */
@Slf4j
public final class ListenerFailurePolicy {

    private ListenerFailurePolicy() {
    }

    /**
     * 예외를 분류해 컨테이너에 전달할 형태로 변환한다. 항상 예외를 반환하므로 호출부는 이를 던져야 한다.
     *
     * @param e         리스너에서 잡은 예외
     * @param operation 로그에 남길 작업 이름 (예: "전적 갱신")
     * @param key       실패 대상 식별자 (예: puuid, matchId)
     * @return 재시도 대상이면 원래 예외, 아니면 {@link AmqpRejectAndDontRequeueException}
     */
    public static RuntimeException translate(Exception e, String operation, String key) {
        Throwable cause = unwrap(e);

        if (isRetryable(cause)) {
            log.warn("[{}] 재시도 가능한 실패 — key={}, cause={}",
                    operation, key, cause.getClass().getSimpleName(), e);
            if (e instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
            return new AmqpException(String.format("%s 실패 (key=%s)", operation, key), e);
        }

        log.error("[{}] 재시도 불가 실패 — DLQ 로 격리합니다. key={}, cause={}",
                operation, key, cause.getClass().getSimpleName(), e);
        return new AmqpRejectAndDontRequeueException(
                String.format("%s 처리 불가 (key=%s)", operation, key), e);
    }

    /**
     * {@code CompletableFuture.join()} / {@code get()} 이 감싼 래퍼를 벗겨 실제 원인을 꺼낸다.
     */
    static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 재시도 판정. 분류 순서가 중요하다 — {@link RiotRateLimitException} 은
     * {@link RiotClientException} 의 하위 타입이므로 먼저 검사해야 4xx 로 오분류되지 않는다.
     */
    static boolean isRetryable(Throwable cause) {
        if (cause instanceof RiotRateLimitException) {
            return true;
        }
        if (cause instanceof RiotClientNotFoundException || cause instanceof RiotClientException) {
            return false;
        }
        if (cause instanceof RiotServerException) {
            return true;
        }
        if (cause instanceof CoreException coreException) {
            return coreException.getErrorType() != ErrorType.NOT_FOUND_USER;
        }
        if (cause instanceof MessageConversionException || cause instanceof IllegalArgumentException) {
            return false;
        }
        return true;
    }
}
