package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RenewalStepPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final List<Duration> sleeps = new ArrayList<>();
    private final RenewalStepPublisher publisher = new RenewalStepPublisher(rabbitTemplate, sleeps::add);
    private final RenewalStepMessage message = new RenewalStepMessage("r1", "p1", "KR", 100L, 0);

    @Test
    @DisplayName("publish 는 바인딩의 exchange 와 routing key 로 보낸다")
    void publish_sendsToBinding() {
        // when
        publisher.publish(RabbitMqBinding.RENEWAL_LEAGUE, message);

        // then
        verify(rabbitTemplate).convertAndSend("mmrtr.renewal.exchange", "renewal.league", message);
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("requeueLater 는 1초 기다린 뒤 보낸다")
    void requeueLater_waitsOneSecond() {
        // when
        publisher.requeueLater(RabbitMqBinding.RENEWAL_MATCH, message);

        // then
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
        verify(rabbitTemplate).convertAndSend("mmrtr.renewal.exchange", "renewal.match", message);
    }

    @Test
    @DisplayName("requeueMatchFindLater 는 1초 기다린 뒤 매치 검색 큐로 보낸다")
    void requeueMatchFindLater_waitsOneSecondThenPublishes() {
        // given
        SummonerRenewalMessage findMessage = new SummonerRenewalMessage("p1", "KR", 100L, 220);

        // when
        publisher.requeueMatchFindLater(findMessage);

        // then
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
        verify(rabbitTemplate).convertAndSend(
                RabbitMqBinding.RENEWAL_MATCH_FIND.getExchange(), RabbitMqBinding.RENEWAL_MATCH_FIND.getRoutingKey(),
                findMessage);
    }

    @Test
    @DisplayName("publishMatchFind 는 renewal.topic.exchange/renewal.match.find 로 보낸다")
    void publishMatchFind_sendsToMatchFindQueue() {
        // given
        SummonerRenewalMessage findMessage = new SummonerRenewalMessage("p1", "KR", 100L, 20);

        // when
        publisher.publishMatchFind(findMessage);

        // then
        verify(rabbitTemplate).convertAndSend("renewal.topic.exchange", "renewal.match.find", findMessage);
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("republishSummoner 는 mmrtr.exchange/mmrtr.key 로 보낸다")
    void republishSummoner_sendsToSummonerQueue() {
        // given
        SummonerMessage summonerMessage = new SummonerMessage("KR", "p1", 1L, "r1", 1);

        // when
        publisher.republishSummoner(summonerMessage);

        // then
        verify(rabbitTemplate).convertAndSend("mmrtr.exchange", "mmrtr.key", summonerMessage);
    }

    @Test
    @DisplayName("republishSummonerLater 는 1초 기다린 뒤 보낸다")
    void republishSummonerLater_waitsOneSecond() {
        // given
        SummonerMessage summonerMessage = new SummonerMessage("KR", "p1", 1L, "r1", 1);

        // when
        publisher.republishSummonerLater(summonerMessage);

        // then
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
        verify(rabbitTemplate).convertAndSend("mmrtr.exchange", "mmrtr.key", summonerMessage);
    }
}
