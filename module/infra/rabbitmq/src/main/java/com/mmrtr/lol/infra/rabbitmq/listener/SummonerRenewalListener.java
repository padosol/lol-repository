package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRenewalService;
import com.mmrtr.lol.infra.redis.service.RedisLockHandler;
import com.mmrtr.lol.support.aop.TraceLogging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class SummonerRenewalListener {

    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(3L);

    private final RedisLockHandler redisLockHandler;
    private final SummonerRenewalService summonerRenewalService;

    @RabbitListener(queues = RabbitMqBinding.Queue.SUMMONER, containerFactory = "simpleRabbitListenerContainerFactory")
    public void receiveSummonerMessageV2(@Payload SummonerMessage summonerMessage) {
        log.info("전적 갱신 요청 {}", summonerMessage);

        String puuid = summonerMessage.getPuuid();

        Platform platform = Platform.valueOfName(summonerMessage.getPlatformId());
        if (platform == null) {
            // 재요청해도 같은 결과이므로 재시도하지 않고 바로 격리한다.
            throw ListenerFailurePolicy.translate(
                    new IllegalArgumentException("유효하지 않은 platform: " + summonerMessage.getPlatformId()),
                    "전적 갱신", puuid);
        }

        if (!redisLockHandler.acquireLock(puuid, LOCK_TIMEOUT)) {
            // 동일 puuid 가 이미 처리 중 — 중복 요청이므로 ACK 하고 버린다.
            // requeue 하면 락이 풀릴 때까지(최대 3분) 같은 메시지가 재전달을 반복해 busy loop 가 된다.
            log.info("이미 전적 갱신 진행 중 입니다. {}", puuid);
            return;
        }

        try {
            summonerRenewalService.renewSummoner(puuid, platform);
        } catch (Exception e) {
            // 예외를 삼키면 컨테이너가 정상 처리로 보고 ACK 하므로 DLX 가 선언돼 있어도 격리되지 않는다.
            // 재시도 가치에 따라 전파(재시도 소진 시 DLQ) 또는 즉시 DLQ 로 보낸다.
            throw ListenerFailurePolicy.translate(e, "전적 갱신", puuid);
        } finally {
            log.info("전적 갱신 요청 완료 {}", summonerMessage);
            redisLockHandler.deleteSummonerRenewal(puuid);
            redisLockHandler.releaseLock(puuid);
        }
    }
}
