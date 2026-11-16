package com.mmrtr.lol.infra.rabbitmq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqConfig;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitQueueWait;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.StringUtils;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 매치 리스너만 소비자를 30개로 늘린다. 공용 팩토리는 소비자 최대 20 이라, 리스너의 concurrency 를 "30" 처럼 시작 수만 주면
 * 팩토리가 최대 20 을 덮어써 컨테이너가 뜨지 않는다. 앱과 같이 공용 팩토리로 만든 컨테이너가 실제 브로커에서 30개로 뜨는지 본다.
 */
@Testcontainers
class RenewalMatchListenerConcurrencyTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    @Test
    @DisplayName("매치 리스너는 공용 팩토리(소비자 최대 20)를 쓰면서도 소비자 30개로 뜬다")
    void matchListener_startsThirtyConsumersOnSharedFactory() throws Exception {
        // given
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        RabbitListener listener = RenewalMatchListener.class
                .getMethod("receive", RenewalStepMessage.class)
                .getAnnotation(RabbitListener.class);
        new RabbitAdmin(connectionFactory).declareQueue(new Queue(listener.queues()[0], false));
        SimpleRabbitListenerContainerFactory factory = new RabbitMqConfig().simpleRabbitListenerContainerFactory(
                new SimpleRabbitListenerContainerFactoryConfigurer(new RabbitProperties()),
                connectionFactory,
                new StaticListableBeanFactory().getBeanProvider(TaskExecutor.class),
                new RabbitQueueWait(new SimpleMeterRegistry()));
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("renewal-match");
        endpoint.setQueueNames(listener.queues());
        endpoint.setConcurrency(StringUtils.hasText(listener.concurrency()) ? listener.concurrency() : null);
        endpoint.setMessageListener(message -> { });

        // when
        SimpleMessageListenerContainer container = factory.createListenerContainer(endpoint);
        container.afterPropertiesSet();
        container.start();

        // then
        try {
            await().atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> assertThat(container.getActiveConsumerCount()).isEqualTo(30));
        } finally {
            container.stop();
            connectionFactory.destroy();
        }
    }
}
