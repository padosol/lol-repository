package com.mmrtr.lol.infra.rabbitmq.config;

import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MatchIdPriorityQueueTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    static CachingConnectionFactory connectionFactory;
    static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void declare() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());

        RabbitMqConfig config = new RabbitMqConfig();
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(config.matchIdExchange());
        admin.declareExchange(config.matchIdDlxExchange());
        admin.declareQueue(config.matchIdQueue());
        admin.declareQueue(config.dlxMatchIdQueue());
        admin.declareBinding(config.matchIdBinding());
        admin.declareBinding(config.matchIdDlxBinding());
    }

    @AfterAll
    static void close() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("처리 순위 1 → 2 → 3 순서로 꺼낸다")
    void matchIdQueue_deliversByRank() {
        // given
        MessageSender sender = new MessageSender(rabbitTemplate);
        sender.sendMessageByMatchId("backfill", "KR", MatchQueueRank.BACKFILL);
        sender.sendMessageByMatchId("retry", "KR", MatchQueueRank.USER_RETRY);
        sender.sendMessageByMatchId("timeline", "KR", MatchQueueRank.TIMELINE_LATER);

        // when
        List<String> received = List.of(receive(), receive(), receive());

        // then
        assertThat(received).containsExactly("timeline", "retry", "backfill");
    }

    private String receive() {
        return (String) rabbitTemplate.receiveAndConvert(RabbitMqBinding.Queue.MATCH_ID, 5000);
    }
}
