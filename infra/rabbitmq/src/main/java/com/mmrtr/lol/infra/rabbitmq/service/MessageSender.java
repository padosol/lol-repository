package com.mmrtr.lol.infra.rabbitmq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageSender {

    private final RabbitTemplate rabbitTemplate;
    public void sendMessageByMatchId(String matchId, String region) {
        rabbitTemplate.convertAndSend(
                "mmrtr.matchId.exchange",
                "mmrtr.routingkey.matchId",
                matchId,
                message -> {
                    message.getMessageProperties().setHeader("region", region);
                    return message;
                }
        );
    }
}
