package com.mmrtr.lol.infra.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 앱이 만드는 발행 템플릿·리스너 팩토리가 큐 대기 시간과 trace(observation)를 실제 브로커를 거쳐 잇는지 본다. */
@Testcontainers
class RabbitTracingWiringTest {

    private static final String QUEUE = "test.renewal.step";

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    static CachingConnectionFactory connectionFactory;

    @BeforeAll
    static void connect() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        new RabbitAdmin(connectionFactory).declareQueue(new Queue(QUEUE, false));
    }

    @AfterAll
    static void close() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("앱 템플릿으로 보낸 메시지를 앱 리스너가 받으면 큐에서 기다린 시간을 받은 큐 이름으로 남긴다")
    void publishAndConsume_recordsQueueWaitByQueue() throws Exception {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RabbitQueueWait queueWait = new RabbitQueueWait(meterRegistry);
        RabbitMqConfig config = new RabbitMqConfig();
        RabbitTemplate template = config.rabbitTemplate(connectionFactory, new RabbitProperties(), queueWait);
        CountDownLatch received = new CountDownLatch(1);
        SimpleMessageListenerContainer container = listen(config, queueWait, received);

        // when
        try {
            template.convertAndSend("", QUEUE, "renewal");
            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            container.stop();
        }

        // then
        Timer timer = meterRegistry.find(RabbitQueueWait.METRIC).tag("queue", QUEUE).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("spring.rabbitmq.template.observation-enabled 를 켜면 발행이 observation 으로 잡혀 trace 를 넘길 수 있다")
    void templateObservationEnabled_publishIsObserved() {
        // given
        List<String> observed = new CopyOnWriteArrayList<>();
        RabbitTemplate template = templateWithObservation(true, observed);

        // when
        template.convertAndSend("", QUEUE, "renewal");

        // then
        assertThat(observed).contains("spring.rabbit.template");
    }

    @Test
    @DisplayName("spring.rabbitmq.template.observation-enabled 가 꺼져 있으면(기본) 발행을 observation 으로 잡지 않는다")
    void templateObservationDisabled_publishIsNotObserved() {
        // given
        List<String> observed = new CopyOnWriteArrayList<>();
        RabbitTemplate template = templateWithObservation(false, observed);

        // when
        template.convertAndSend("", QUEUE, "renewal");

        // then
        assertThat(observed).doesNotContain("spring.rabbit.template");
    }

    private static SimpleMessageListenerContainer listen(RabbitMqConfig config, RabbitQueueWait queueWait,
                                                         CountDownLatch received) {
        SimpleRabbitListenerContainerFactory factory = config.simpleRabbitListenerContainerFactory(
                new SimpleRabbitListenerContainerFactoryConfigurer(new RabbitProperties()),
                connectionFactory,
                new StaticListableBeanFactory().getBeanProvider(TaskExecutor.class),
                queueWait);
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("wiring");
        endpoint.setQueueNames(QUEUE);
        endpoint.setMessageListener(message -> received.countDown());
        SimpleMessageListenerContainer container = factory.createListenerContainer(endpoint);
        container.afterPropertiesSet();
        container.start();
        return container;
    }

    private static RabbitTemplate templateWithObservation(boolean enabled, List<String> observed) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStart(Observation.Context context) {
                observed.add(context.getName());
            }
        });
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(ObservationRegistry.class, () -> registry);
        context.refresh();

        RabbitProperties properties = new RabbitProperties();
        properties.getTemplate().setObservationEnabled(enabled);
        RabbitTemplate template = new RabbitMqConfig().rabbitTemplate(
                connectionFactory, properties, new RabbitQueueWait(new SimpleMeterRegistry()));
        template.setApplicationContext(context);
        return template;
    }
}
