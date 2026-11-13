package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchStep;
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
public class RenewalMatchListener {

    private final RenewalMatchStep matchStep;

    @RabbitListener(queues = RabbitMqBinding.Queue.RENEWAL_MATCH,
            containerFactory = "simpleRabbitListenerContainerFactory", concurrency = "20")
    public void receive(@Payload RenewalStepMessage message) {
        log.info("갱신 매치 단계 요청 {}", message);
        matchStep.run(message);
    }
}
