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

    /**
     * 매치 단계만 소비자를 30개로 둔다 (공용 팩토리는 20). 공용 팩토리가 소비자 최대 20 을 걸므로 "30" 처럼 시작 수만 주면
     * 최대 20 이 덮어써 컨테이너가 뜨지 않는다. 범위("30-30")로 줘야 팩토리 최대를 건너뛴다.
     */
    @RabbitListener(queues = RabbitMqBinding.Queue.RENEWAL_MATCH,
            containerFactory = "simpleRabbitListenerContainerFactory", concurrency = "30-30")
    public void receive(@Payload RenewalStepMessage message) {
        log.info("갱신 매치 단계 요청 {}", message);
        matchStep.run(message);
    }
}
