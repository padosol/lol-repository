package com.mmrtr.lol.infra.rabbitmq.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RabbitMqBinding {
    SUMMONER("mmrtr.exchange", "mmrtr.key", Queue.SUMMONER),
    SUMMONER_DLX("summoner.dlx.exchange", "deadLetter", Queue.SUMMONER_DLX),
    /** ⑥ 매치 적재 우선순위 큐. 옛 비우선순위 {@code mmrtr.matchId}·{@code mmrtr.matchId.exchange} 를 대신하는 새 이름이다. */
    MATCH_ID("mmrtr.matchId.priority.exchange", "mmrtr.routingkey.matchId", Queue.MATCH_ID),
    MATCH_ID_DLX("matchId.dlx.exchange", "deadLetter", Queue.MATCH_ID_DLX),
    RENEWAL_MATCH_FIND("renewal.topic.exchange", "renewal.match.find", Queue.RENEWAL_MATCH_FIND),
    RENEWAL_ACCOUNT(Exchange.RENEWAL, "renewal.account", Queue.RENEWAL_ACCOUNT),
    RENEWAL_LEAGUE(Exchange.RENEWAL, "renewal.league", Queue.RENEWAL_LEAGUE),
    RENEWAL_MATCH(Exchange.RENEWAL, "renewal.match", Queue.RENEWAL_MATCH),
    RENEWAL_ACCOUNT_DLX(Exchange.RENEWAL_DLX, "renewal.account", Queue.RENEWAL_ACCOUNT_DLX),
    RENEWAL_LEAGUE_DLX(Exchange.RENEWAL_DLX, "renewal.league", Queue.RENEWAL_LEAGUE_DLX),
    RENEWAL_MATCH_DLX(Exchange.RENEWAL_DLX, "renewal.match", Queue.RENEWAL_MATCH_DLX);

    private final String exchange;
    private final String routingKey;
    private final String queue;

    public static final class Exchange {
        public static final String RENEWAL = "mmrtr.renewal.exchange";
        public static final String RENEWAL_DLX = "mmrtr.renewal.dlx.exchange";

        private Exchange() {}
    }

    public static final class Queue {
        public static final String SUMMONER = "mmrtr.summoner";
        public static final String SUMMONER_DLX = "mmrtr.summoner.dlx";
        public static final String MATCH_ID = "mmrtr.matchId.priority";
        public static final String MATCH_ID_DLX = "mmrtr.matchId.dlx";
        public static final String RENEWAL_MATCH_FIND = "renewal.match.find.queue";
        public static final String RENEWAL_ACCOUNT = "mmrtr.renewal.account";
        public static final String RENEWAL_LEAGUE = "mmrtr.renewal.league";
        public static final String RENEWAL_MATCH = "mmrtr.renewal.match";
        public static final String RENEWAL_ACCOUNT_DLX = "mmrtr.renewal.account.dlx";
        public static final String RENEWAL_LEAGUE_DLX = "mmrtr.renewal.league.dlx";
        public static final String RENEWAL_MATCH_DLX = "mmrtr.renewal.match.dlx";

        private Queue() {}
    }
}
