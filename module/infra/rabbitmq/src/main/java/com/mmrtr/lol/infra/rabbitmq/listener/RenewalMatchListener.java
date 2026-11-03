package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchStep;
import com.mmrtr.lol.support.aop.TraceLogging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class RenewalMatchListener {

    private final RenewalMatchStep matchStep;

    /** 첫 전달의 매치 저장 실패만 밖으로 나가 메시지가 다시 온다. 재전달 여부로 두 번째 실패는 FAILED 로 끝낸다. */
    @RabbitListener(queues = RabbitMqBinding.Queue.RENEWAL_MATCH,
            containerFactory = "simpleRabbitListenerContainerFactory", concurrency = "20")
    public void receive(@Payload RenewalStepMessage message,
                        @Header(AmqpHeaders.REDELIVERED) boolean redelivered) {
        log.info("갱신 매치 단계 요청 {}, redelivered={}", message, redelivered);
        matchStep.run(message, redelivered);
    }
}
