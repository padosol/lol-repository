package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalLeagueStep;
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
public class RenewalLeagueListener {

    private final RenewalLeagueStep leagueStep;

    @RabbitListener(queues = RabbitMqBinding.Queue.RENEWAL_LEAGUE,
            containerFactory = "simpleRabbitListenerContainerFactory", concurrency = "10")
    public void receive(@Payload RenewalStepMessage message) {
        log.info("갱신 리그 단계 요청 {}", message);
        leagueStep.run(message);
    }
}
