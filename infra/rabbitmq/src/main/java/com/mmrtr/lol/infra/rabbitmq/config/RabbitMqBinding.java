package com.mmrtr.lol.infra.rabbitmq.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RabbitMqBinding {
    SUMMONER("mmrtr.exchange", "mmrtr.key", Queue.SUMMONER),
    SUMMONER_DLX("summoner.dlx.exchange", "deadLetter", Queue.SUMMONER_DLX),
    MATCH_ID("mmrtr.matchId.exchange", "mmrtr.routingkey.matchId", Queue.MATCH_ID),
    RENEWAL_MATCH_FIND("renewal.topic.exchange", "renewal.match.find", Queue.RENEWAL_MATCH_FIND);

    private final String exchange;
    private final String routingKey;
    private final String queue;

    public static final class Queue {
        public static final String SUMMONER = "mmrtr.summoner";
        public static final String SUMMONER_DLX = "mmrtr.summoner.dlx";
        public static final String MATCH_ID = "mmrtr.matchId";
        public static final String RENEWAL_MATCH_FIND = "renewal.match.find.queue";

        private Queue() {}
    }
}
