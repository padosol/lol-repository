package com.mmrtr.lol.infra.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.core.task.TaskExecutor;

/** 추가 매치 찾기 큐(renewal.match.find.queue) 리스너 컨테이너는 lol.renewal.background.enabled 를 따라 자동으로 시작한다. */
class FindQueueContainerFactoryTest {

    @Test
    @DisplayName("추가 갱신을 끄면 추가 매치 찾기 리스너가 자동으로 시작하지 않는다 (메시지는 큐에 남는다)")
    void backgroundDisabled_findQueueContainerDoesNotAutoStart() {
        assertThat(container(false).isAutoStartup()).isFalse();
    }

    @Test
    @DisplayName("추가 갱신을 켜면(기본) 추가 매치 찾기 리스너가 자동으로 시작한다")
    void backgroundEnabled_findQueueContainerAutoStarts() {
        assertThat(container(true).isAutoStartup()).isTrue();
    }

    private static SimpleMessageListenerContainer container(boolean backgroundEnabled) {
        SimpleRabbitListenerContainerFactory factory = new RabbitMqConfig().findQueueSimpleRabbitListenerContainerFactory(
                new SimpleRabbitListenerContainerFactoryConfigurer(new RabbitProperties()),
                mock(ConnectionFactory.class),
                new StaticListableBeanFactory().getBeanProvider(TaskExecutor.class),
                backgroundEnabled,
                new RabbitQueueWait(new SimpleMeterRegistry()));
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("find");
        endpoint.setQueueNames(RabbitMqBinding.Queue.RENEWAL_MATCH_FIND);
        endpoint.setMessageListener(message -> { });
        return factory.createListenerContainer(endpoint);
    }
}
