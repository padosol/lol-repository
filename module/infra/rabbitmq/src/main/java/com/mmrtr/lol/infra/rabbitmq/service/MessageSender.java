package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageSender {

    /** 매치 적재 메시지의 일시 오류 시도 수(정수). 없으면 0 으로 본다. */
    public static final String ATTEMPT_HEADER = "x-attempt";

    private final RabbitTemplate rabbitTemplate;

    public void sendMessageByMatchId(String matchId, String region, MatchQueueRank rank) {
        sendMessageByMatchId(matchId, region, rank, 0);
    }

    /** 일시 오류로 다시 넣을 때 시도 수를 {@value #ATTEMPT_HEADER} 헤더에 싣는다. */
    public void sendMessageByMatchId(String matchId, String region, MatchQueueRank rank, int attempt) {
        rabbitTemplate.convertAndSend(
                RabbitMqBinding.MATCH_ID.getExchange(),
                RabbitMqBinding.MATCH_ID.getRoutingKey(),
                matchId,
                message -> {
                    message.getMessageProperties().setHeader("region", region);
                    message.getMessageProperties().setHeader(ATTEMPT_HEADER, attempt);
                    message.getMessageProperties().setPriority(rank.amqpPriority());
                    return message;
                }
        );
    }
}
