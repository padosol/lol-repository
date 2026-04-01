package com.mmrtr.lol.infra.rabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
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
        return new Queue(RabbitMqBinding.MATCH_ID.getQueue());
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

        return connectionFactory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
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

        simpleFactory.setChannelTransacted(true);
        rabbitListenerTaskExecutorProvider.ifAvailable(simpleFactory::setTaskExecutor);

        simpleFactory.setConcurrentConsumers(3);
        simpleFactory.setMaxConcurrentConsumers(3);

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
