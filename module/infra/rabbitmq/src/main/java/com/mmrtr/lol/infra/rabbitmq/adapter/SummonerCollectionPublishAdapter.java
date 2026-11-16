package com.mmrtr.lol.infra.rabbitmq.adapter;

import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerCollectionPublishAdapter implements SummonerCollectionPublishPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishForRenewal(List<String> puuids, String platformName) {
        for (String puuid : puuids) {
            SummonerMessage message = new SummonerMessage(platformName, puuid, 0L);
            rabbitTemplate.convertAndSend(
                    RabbitMqBinding.SUMMONER.getExchange(),
                    RabbitMqBinding.SUMMONER.getRoutingKey(),
                    message
            );
        }
        log.debug("[MQ 발행] {}명 발행 완료, platform={}", puuids.size(), platformName);
    }
}
