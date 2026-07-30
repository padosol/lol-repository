package com.mmrtr.lol.infra.rabbitmq.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    @Value("${spring.rabbitmq.host}")
    private String rabbitmqHost;

    @Value("${spring.rabbitmq.port}")
    private int rabbitmqPort;

    @Value("${spring.rabbitmq.username}")
    private String rabbitmqUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitmqPassword;

    @Bean
    public Queue summonerQueue() {
        return QueueBuilder.durable(RabbitMqBinding.SUMMONER.getQueue())
                .withArgument("x-dead-letter-exchange", RabbitMqBinding.SUMMONER_DLX.getExchange())
                .withArgument("x-dead-letter-routing-key", RabbitMqBinding.SUMMONER_DLX.getRoutingKey())
                .build();
    }

    @Bean
    public Queue dlxSummonerQueue() {
        return new Queue(RabbitMqBinding.SUMMONER_DLX.getQueue(), true);
    }

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(RabbitMqBinding.SUMMONER.getExchange());
    }

    @Bean
    public DirectExchange summonerDlxExchange() {
        return new DirectExchange(RabbitMqBinding.SUMMONER_DLX.getExchange());
    }

    @Bean
    public Binding summonerBinding() {
        return BindingBuilder
                .bind(summonerQueue())
                .to(topicExchange())
                .with(RabbitMqBinding.SUMMONER.getRoutingKey());
    }

    @Bean
    public Binding summonerDlxBinding() {
        return BindingBuilder
                .bind(dlxSummonerQueue())
                .to(summonerDlxExchange())
                .with(RabbitMqBinding.SUMMONER_DLX.getRoutingKey());
    }

    @Bean
    public Queue matchIdQueue() {
        return QueueBuilder.durable(RabbitMqBinding.MATCH_ID.getQueue())
                .withArgument("x-dead-letter-exchange", RabbitMqBinding.MATCH_ID_DLX.getExchange())
                .withArgument("x-dead-letter-routing-key", RabbitMqBinding.MATCH_ID_DLX.getRoutingKey())
                .build();
    }

    @Bean
    public DirectExchange matchIdExchange() {
        return new DirectExchange(RabbitMqBinding.MATCH_ID.getExchange());
    }

    @Bean
    public Binding matchIdBinding() {
        return BindingBuilder
                .bind(matchIdQueue())
                .to(matchIdExchange())
                .with(RabbitMqBinding.MATCH_ID.getRoutingKey());
    }

    @Bean
    public Queue dlxMatchIdQueue() {
        return new Queue(RabbitMqBinding.MATCH_ID_DLX.getQueue(), true);
    }

    @Bean
    public DirectExchange matchIdDlxExchange() {
        return new DirectExchange(RabbitMqBinding.MATCH_ID_DLX.getExchange());
    }

    @Bean
    public Binding matchIdDlxBinding() {
        return BindingBuilder
                .bind(dlxMatchIdQueue())
                .to(matchIdDlxExchange())
                .with(RabbitMqBinding.MATCH_ID_DLX.getRoutingKey());
    }

    @Bean
    public TopicExchange renewalExchange() {
        return new TopicExchange(RabbitMqBinding.RENEWAL_MATCH_FIND.getExchange(), true, false);
    }

    @Bean
    public Queue matchFind() {
        return new Queue(RabbitMqBinding.RENEWAL_MATCH_FIND.getQueue(), true);
    }

    @Bean
    public Binding matchFindBinding() {
        return BindingBuilder.bind(matchFind())
                .to(renewalExchange())
                .with(RabbitMqBinding.RENEWAL_MATCH_FIND.getRoutingKey());
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(rabbitmqHost);
        connectionFactory.setPort(rabbitmqPort);
        connectionFactory.setUsername(rabbitmqUsername);
        connectionFactory.setPassword(rabbitmqPassword);
        connectionFactory.setRequestedHeartBeat(60);

        // ConnectionFactory 를 코드로 직접 만들면 spring.rabbitmq.publisher-* 프로퍼티가 적용되지 않는다.
        // 발행 확인을 받으려면 여기서 직접 켜야 한다.
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        return connectionFactory;
    }

    /**
     * {@code @Bean} 으로 선언한 Queue/Exchange/Binding 을 브로커에 실제로 반영하는 주체.
     *
     * <p>{@link ConnectionFactory} 를 코드로 직접 정의하면 자동설정의 {@code amqpAdmin} 이 함께 백오프되어
     * RabbitAdmin 이 컨텍스트에 존재하지 않는다. 그 경우 이 클래스의 토폴로지 선언은 브로커에 적용되지 않고,
     * 큐가 없는 환경에 배포하면 리스너가 큐를 찾지 못한다. 명시적으로 등록해 선언이 동작하도록 한다.
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MeterRegistry meterRegistry) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());

        // 소비 측이 브로커 flow control 에 걸려도 발행은 계속되도록 커넥션을 분리한다.
        rabbitTemplate.setUsePublisherConnection(true);

        // 어느 큐에도 라우팅되지 않은 메시지를 조용히 버리지 않고 returns 콜백으로 돌려받는다.
        rabbitTemplate.setMandatory(true);

        Counter confirmAck = Counter.builder("rabbitmq.publish.confirm")
                .tag("outcome", "ack")
                .description("브로커가 발행을 확인한 횟수")
                .register(meterRegistry);
        Counter confirmNack = Counter.builder("rabbitmq.publish.confirm")
                .tag("outcome", "nack")
                .description("브로커가 발행을 거부한 횟수")
                .register(meterRegistry);
        Counter routingFailure = Counter.builder("rabbitmq.publish.returned")
                .description("어느 큐에도 라우팅되지 못하고 반환된 메시지 수")
                .register(meterRegistry);

        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                confirmAck.increment();
                return;
            }
            confirmNack.increment();
            log.error("[MQ 발행 거부] 브로커가 메시지를 받지 못했습니다. correlationData={}, cause={}",
                    correlationData, cause);
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            routingFailure.increment();
            log.error("[MQ 라우팅 실패] 어느 큐에도 전달되지 않았습니다. exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });

        return rabbitTemplate;
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean(name = "rabbitListenerExecutor", destroyMethod = "close")
    @ConditionalOnProperty(name = "lol.vt.executors.enabled", havingValue = "true", matchIfMissing = true)
    public ExecutorService rabbitListenerExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("rabbit-listener-vt-", 0)
                        .factory()
        );
    }

    @Bean(name = "rabbitListenerTaskExecutor")
    @ConditionalOnProperty(name = "lol.vt.executors.enabled", havingValue = "true", matchIfMissing = true)
    public TaskExecutor rabbitListenerTaskExecutor(ExecutorService rabbitListenerExecutor) {
        return new TaskExecutorAdapter(rabbitListenerExecutor);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory factory,
            @Qualifier("rabbitListenerTaskExecutor")
            ObjectProvider<TaskExecutor> rabbitListenerTaskExecutorProvider
    ) {
        SimpleRabbitListenerContainerFactory simpleFactory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(simpleFactory, factory);

        // 채널 트랜잭션은 ACK 을 DB 트랜잭션과 묶을 때만 의미가 있는데 이 리스너에는 그런 경계가 없다.
        // 반면 tx.select/tx.commit 라운드트립 비용은 그대로 들고, 롤백 재전달이
        // default-requeue-rejected=false 기반 DLQ 격리와 겹쳐 동작이 불명확해진다.
        simpleFactory.setChannelTransacted(false);
        rabbitListenerTaskExecutorProvider.ifAvailable(simpleFactory::setTaskExecutor);

        simpleFactory.setConcurrentConsumers(20);
        simpleFactory.setMaxConcurrentConsumers(20);

        simpleFactory.setPrefetchCount(1);
        simpleFactory.setReceiveTimeout(1000L);

        return simpleFactory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory findQueueSimpleRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory factory,
            @Qualifier("rabbitListenerTaskExecutor")
            ObjectProvider<TaskExecutor> rabbitListenerTaskExecutorProvider
    ) {
        SimpleRabbitListenerContainerFactory simpleFactory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(simpleFactory, factory);

        rabbitListenerTaskExecutorProvider.ifAvailable(simpleFactory::setTaskExecutor);
        simpleFactory.setConcurrentConsumers(1);
        simpleFactory.setMaxConcurrentConsumers(1);

        simpleFactory.setPrefetchCount(1);
        simpleFactory.setReceiveTimeout(1000L);

        return simpleFactory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory batchRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory factory,
            @Qualifier("rabbitListenerTaskExecutor")
            ObjectProvider<TaskExecutor> rabbitListenerTaskExecutorProvider
    ) {

        SimpleRabbitListenerContainerFactory simpleFactory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(simpleFactory, factory);

        rabbitListenerTaskExecutorProvider.ifAvailable(simpleFactory::setTaskExecutor);
        simpleFactory.setConcurrentConsumers(20);
        simpleFactory.setMaxConcurrentConsumers(20);

        simpleFactory.setPrefetchCount(1);

        return simpleFactory;
    }

}
