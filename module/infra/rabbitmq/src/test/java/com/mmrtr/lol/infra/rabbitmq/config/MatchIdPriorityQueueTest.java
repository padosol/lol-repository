package com.mmrtr.lol.infra.rabbitmq.config;

import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MatchIdPriorityQueueTest {

    /** 옛 비우선순위 매치 적재 큐. 배포 전 브로커에 남아 있거나, 롤링 배포 중 옛 파드가 다시 만든다. */
    private static final String LEGACY_QUEUE = "mmrtr.matchId";
    private static final String LEGACY_EXCHANGE = "mmrtr.matchId.exchange";

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    static CachingConnectionFactory connectionFactory;
    static RabbitTemplate rabbitTemplate;
    static RabbitAdmin admin;

    @BeforeAll
    static void declare() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        admin = new RabbitAdmin(connectionFactory);

        // 옛 앱이 선언하던 그대로 (x-max-priority 없음)
        admin.declareExchange(new DirectExchange(LEGACY_EXCHANGE));
        admin.declareQueue(QueueBuilder.durable(LEGACY_QUEUE)
                .withArgument("x-dead-letter-exchange", RabbitMqBinding.MATCH_ID_DLX.getExchange())
                .withArgument("x-dead-letter-routing-key", RabbitMqBinding.MATCH_ID_DLX.getRoutingKey())
                .build());

        // 앱의 RabbitAdmin 처럼 exchange → queue → binding 순서로 앱의 선언을 모두 한다
        List<Declarable> declarables = appDeclarables();
        declarables.stream().filter(Exchange.class::isInstance).forEach(d -> admin.declareExchange((Exchange) d));
        declarables.stream().filter(Queue.class::isInstance).forEach(d -> admin.declareQueue((Queue) d));
        declarables.stream().filter(Binding.class::isInstance).forEach(d -> admin.declareBinding((Binding) d));
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

    @Test
    @DisplayName("옛 비우선순위 mmrtr.matchId 가 남아 있어도 선언이 멈추지 않아 갱신 단계 큐와 바인딩까지 생긴다")
    void legacyQueueLeftOver_declarationContinuesToRenewalQueues() {
        // when
        rabbitTemplate.convertAndSend(RabbitMqBinding.Exchange.RENEWAL,
                RabbitMqBinding.RENEWAL_MATCH.getRoutingKey(), "ping");

        // then
        assertThat(admin.getQueueProperties(RabbitMqBinding.Queue.MATCH_ID)).isNotNull();
        assertThat(admin.getQueueProperties(RabbitMqBinding.Queue.RENEWAL_ACCOUNT)).isNotNull();
        assertThat(admin.getQueueProperties(RabbitMqBinding.Queue.RENEWAL_LEAGUE)).isNotNull();
        assertThat(rabbitTemplate.receiveAndConvert(RabbitMqBinding.Queue.RENEWAL_MATCH, 5000)).isEqualTo("ping");
        assertThat(admin.getQueueProperties(LEGACY_QUEUE)).isNotNull();
    }

    @Test
    @DisplayName("앱은 옛 mmrtr.matchId 큐·exchange 를 선언하지 않고, 새 큐는 우선순위 3 이며 기존 DLX 로 보낸다")
    void appDeclarables_newPriorityQueueOnly() {
        // when
        List<Declarable> declarables = appDeclarables();

        // then
        List<Queue> queues = declarables.stream().filter(Queue.class::isInstance).map(Queue.class::cast).toList();
        List<String> exchanges = declarables.stream().filter(Exchange.class::isInstance)
                .map(d -> ((Exchange) d).getName()).toList();
        assertThat(queues).extracting(Queue::getName).doesNotContain(LEGACY_QUEUE);
        assertThat(exchanges).doesNotContain(LEGACY_EXCHANGE).contains("mmrtr.matchId.priority.exchange");
        Queue priorityQueue = queues.stream()
                .filter(q -> q.getName().equals("mmrtr.matchId.priority")).findFirst().orElseThrow();
        assertThat(priorityQueue.getArguments())
                .containsEntry("x-max-priority", 3)
                .containsEntry("x-dead-letter-exchange", "matchId.dlx.exchange")
                .containsEntry("x-dead-letter-routing-key", "deadLetter");
    }

    private String receive() {
        return (String) rabbitTemplate.receiveAndConvert(RabbitMqBinding.Queue.MATCH_ID, 5000);
    }

    /** RabbitMqConfig 의 인자 없는 @Bean 중 Queue·Exchange·Binding 을 모두 만든다 (앱의 RabbitAdmin 이 선언하는 것). */
    private static List<Declarable> appDeclarables() {
        RabbitMqConfig config = new RabbitMqConfig();
        List<Declarable> result = new ArrayList<>();
        for (Method method : RabbitMqConfig.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class) && method.getParameterCount() == 0
                    && Declarable.class.isAssignableFrom(method.getReturnType())) {
                try {
                    result.add((Declarable) method.invoke(config));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return result;
    }
}
