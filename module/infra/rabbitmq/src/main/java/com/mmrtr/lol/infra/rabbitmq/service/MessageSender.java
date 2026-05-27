package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageSender {

    private final RabbitTemplate rabbitTemplate;
    public void sendMessageByMatchId(String matchId, String region) {
        rabbitTemplate.convertAndSend(
                RabbitMqBinding.MATCH_ID.getExchange(),
                RabbitMqBinding.MATCH_ID.getRoutingKey(),
                matchId,
                message -> {
                    message.getMessageProperties().setHeader("region", region);
                    return message;
                }
        );
    }
}
