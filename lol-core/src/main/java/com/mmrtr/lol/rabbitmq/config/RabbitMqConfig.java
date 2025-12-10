package com.mmrtr.lol.rabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        return QueueBuilder.durable("mmrtr.summoner")
                .withArgument("x-dead-letter-exchange", "summoner.dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "deadLetter")
                .build();
    }

    @Bean
    public Queue dlxSummonerQueue() {
        return new Queue("mmrtr.summoner.dlx", true);
    }

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange("mmrtr.exchange");
    }

    @Bean
    public DirectExchange summonerDlxExchange() {
        return new DirectExchange("summoner.dlx.exchange");
    }

    @Bean
    public Binding summonerBinding() {
        return BindingBuilder
                .bind(summonerQueue())
                .to(topicExchange())
                .with("mmrtr.key");
    }

    @Bean
    public Binding summonerDlxBinding() {
        return BindingBuilder
                .bind(dlxSummonerQueue())
                .to(summonerDlxExchange())
                .with("deadLetter");
    }

    @Bean
    public Queue matchIdQueue() {
        return new Queue("mmrtr.matchId");
    }

    @Bean
    public DirectExchange matchIdExchange() {
        return new DirectExchange("mmrtr.matchId.exchange");
    }

    @Bean
    public Binding matchIdBinding() {
        return BindingBuilder
                .bind(matchIdQueue())
                .to(matchIdExchange())
                .with("mmrtr.routingkey.matchId");
    }

    @Bean
    public TopicExchange renewalExchange() {
        return new TopicExchange("renewal.topic.exchange", true, false);
    }

    @Bean
    public Queue matchFind() {
        return new Queue("renewal.match.find.queue", true);
    }

    @Bean
    public Binding matchFindBinding() {
        return BindingBuilder.bind(matchFind())
                .to(renewalExchange())
                .with("renewal.match.find");
    }

    /* RabbitMQ 연결 설정 */
    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(rabbitmqHost);
        connectionFactory.setPort(rabbitmqPort);
        connectionFactory.setUsername(rabbitmqUsername);
        connectionFactory.setPassword(rabbitmqPassword);

        return connectionFactory;
    }

    /* 연결 설정으로 연결 후 실제 작업을 위한 RabbitTemplate */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // JSON 형식의 메시지를 직렬화하고 역직렬할 수 있도록 설정
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }

    /* 메시지를 JSON 기반으로 변환하는 메시지 컨버터 */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /* 리스너 펙토리 */
    @Bean
    public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory factory
    ) {
        SimpleRabbitListenerContainerFactory simpleFactory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(simpleFactory, factory);

        simpleFactory.setChannelTransacted(true);

        simpleFactory.setConcurrentConsumers(10);
        simpleFactory.setMaxConcurrentConsumers(10);

        simpleFactory.setPrefetchCount(1);
        simpleFactory.setReceiveTimeout(1000L);

        return simpleFactory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory findQueueSimpleRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory factory
    ) {
        SimpleRabbitListenerContainerFactory simpleFactory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(simpleFactory, factory);

        simpleFactory.setConcurrentConsumers(5);
        simpleFactory.setMaxConcurrentConsumers(5);

        simpleFactory.setPrefetchCount(1);
        simpleFactory.setReceiveTimeout(1000L);

        return simpleFactory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory batchRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory factory
    ) {

        SimpleRabbitListenerContainerFactory simpleFactory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(simpleFactory, factory);

        simpleFactory.setChannelTransacted(true);
        simpleFactory.setConcurrentConsumers(1);
        simpleFactory.setMaxConcurrentConsumers(1);

        simpleFactory.setPrefetchCount(20);
        simpleFactory.setReceiveTimeout(2000L);

        simpleFactory.setBatchListener(true);
        simpleFactory.setBatchSize(20);

        simpleFactory.setConsumerBatchEnabled(true);

        return simpleFactory;
    }

}
