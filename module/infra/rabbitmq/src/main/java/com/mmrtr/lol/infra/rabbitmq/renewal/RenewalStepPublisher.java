package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import java.time.Duration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 갱신 단계(①~④) 메시지를 큐에 넣는다. */
@Component
public class RenewalStepPublisher {

    static final Duration REQUEUE_DELAY = Duration.ofSeconds(1);

    private final RabbitTemplate rabbitTemplate;
    private final Sleeper sleeper;

    @Autowired
    public RenewalStepPublisher(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, RenewalStepPublisher::sleep);
    }

    RenewalStepPublisher(RabbitTemplate rabbitTemplate, Sleeper sleeper) {
        this.rabbitTemplate = rabbitTemplate;
        this.sleeper = sleeper;
    }

    public void publish(RabbitMqBinding binding, RenewalStepMessage message) {
        rabbitTemplate.convertAndSend(binding.getExchange(), binding.getRoutingKey(), message);
    }

    /** 예약 실패처럼 같은 거절이 바로 다시 도는 것을 막아야 할 때, 1초 기다린 뒤 넣는다. */
    public void requeueLater(RabbitMqBinding binding, RenewalStepMessage message) {
        sleeper.sleep(REQUEUE_DELAY);
        publish(binding, message);
    }

    /** 20개를 꽉 채운 매치 단계가 21번째부터의 매치 검색을 백그라운드 검색 큐에 넘긴다. */
    public void publishMatchFind(SummonerRenewalMessage message) {
        rabbitTemplate.convertAndSend(RabbitMqBinding.RENEWAL_MATCH_FIND.getExchange(),
                RabbitMqBinding.RENEWAL_MATCH_FIND.getRoutingKey(), message);
    }

    public void republishSummoner(SummonerMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMqBinding.SUMMONER.getExchange(), RabbitMqBinding.SUMMONER.getRoutingKey(), message);
    }

    public void republishSummonerLater(SummonerMessage message) {
        sleeper.sleep(REQUEUE_DELAY);
        republishSummoner(message);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration);
    }
}
