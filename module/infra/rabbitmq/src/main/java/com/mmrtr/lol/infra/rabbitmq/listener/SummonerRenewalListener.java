package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalSummonerStep;
import com.mmrtr.lol.support.aop.TraceLogging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class SummonerRenewalListener {

    private final RenewalSummonerStep summonerStep;

    @RabbitListener(queues = RabbitMqBinding.Queue.SUMMONER, containerFactory = "simpleRabbitListenerContainerFactory")
    public void receiveSummonerMessageV2(@Payload SummonerMessage summonerMessage) {
        log.info("전적 갱신 요청 {}", summonerMessage);
        summonerStep.run(summonerMessage);
    }
}
