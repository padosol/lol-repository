package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRenewalService;
import com.mmrtr.lol.infra.redis.service.RedisLockHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerRenewalListener {

    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(3L);

    private final RedisLockHandler redisLockHandler;
    private final SummonerRenewalService summonerRenewalService;

    @RabbitListener(queues = RabbitMqBinding.Queue.SUMMONER, containerFactory = "simpleRabbitListenerContainerFactory")
    public void receiveSummonerMessageV2(@Payload SummonerMessage summonerMessage) {
        log.info("전적 갱신 요청 {}", summonerMessage);
        String puuid = summonerMessage.getPuuid();
        if (!redisLockHandler.acquireLock(puuid, LOCK_TIMEOUT)) {
            log.info("이미 전적 갱신 진행 중 입니다. {}", puuid);
            return;
        }

        try {
            Platform platform = Platform.valueOfName(summonerMessage.getPlatform());
            summonerRenewalService.renewSummoner(puuid, platform);
        } finally {
            redisLockHandler.deleteSummonerRenewal(puuid);
            redisLockHandler.releaseLock(puuid);
        }
    }
}
