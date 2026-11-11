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

    @Value("${spring.rabbitmq.virtual-host:/}")
    private String rabbitmqVirtualHost;

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

    /**
     * ⑥ 매치 적재 우선순위 큐. 이미 있는 큐의 인자는 바꿀 수 없어, 옛 비우선순위 {@code mmrtr.matchId} 를 같은 이름으로
     * 다시 선언하면 PRECONDITION_FAILED 로 거절되고 RabbitAdmin 이 뒤의 선언(갱신 단계 큐 포함)까지 멈춘다.
     * 그래서 새 이름으로 선언하고 옛 큐·exchange 는 선언하지도 쓰지도 않는다. DLX 는 옛 큐와 같은 것을 쓴다.
     */
    @Bean
    public Queue matchIdPriorityQueue() {
        return QueueBuilder.durable(RabbitMqBinding.MATCH_ID.getQueue())
                .withArgument("x-dead-letter-exchange", RabbitMqBinding.MATCH_ID_DLX.getExchange())
                .withArgument("x-dead-letter-routing-key", RabbitMqBinding.MATCH_ID_DLX.getRoutingKey())
                .maxPriority(3)
                .build();
    }

    @Bean
    public DirectExchange matchIdPriorityExchange() {
        return new DirectExchange(RabbitMqBinding.MATCH_ID.getExchange());
    }

    @Bean
    public Binding matchIdPriorityBinding() {
        return BindingBuilder
                .bind(matchIdPriorityQueue())
                .to(matchIdPriorityExchange())
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
    public DirectExchange renewalStepExchange() {
        return new DirectExchange(RabbitMqBinding.Exchange.RENEWAL);
    }

    @Bean
    public DirectExchange renewalStepDlxExchange() {
        return new DirectExchange(RabbitMqBinding.Exchange.RENEWAL_DLX);
    }

    @Bean
    public Queue renewalAccountQueue() {
        return renewalStepQueue(RabbitMqBinding.RENEWAL_ACCOUNT, RabbitMqBinding.RENEWAL_ACCOUNT_DLX);
    }

    @Bean
    public Queue renewalLeagueQueue() {
        return renewalStepQueue(RabbitMqBinding.RENEWAL_LEAGUE, RabbitMqBinding.RENEWAL_LEAGUE_DLX);
    }

    @Bean
    public Queue renewalMatchQueue() {
        return renewalStepQueue(RabbitMqBinding.RENEWAL_MATCH, RabbitMqBinding.RENEWAL_MATCH_DLX);
    }

    @Bean
    public Queue renewalAccountDlxQueue() {
        return new Queue(RabbitMqBinding.RENEWAL_ACCOUNT_DLX.getQueue(), true);
    }

    @Bean
    public Queue renewalLeagueDlxQueue() {
        return new Queue(RabbitMqBinding.RENEWAL_LEAGUE_DLX.getQueue(), true);
    }

    @Bean
    public Queue renewalMatchDlxQueue() {
        return new Queue(RabbitMqBinding.RENEWAL_MATCH_DLX.getQueue(), true);
    }

    @Bean
    public Binding renewalAccountBinding() {
        return stepBinding(renewalAccountQueue(), renewalStepExchange(), RabbitMqBinding.RENEWAL_ACCOUNT);
    }

    @Bean
    public Binding renewalLeagueBinding() {
        return stepBinding(renewalLeagueQueue(), renewalStepExchange(), RabbitMqBinding.RENEWAL_LEAGUE);
    }

    @Bean
    public Binding renewalMatchBinding() {
        return stepBinding(renewalMatchQueue(), renewalStepExchange(), RabbitMqBinding.RENEWAL_MATCH);
    }

    @Bean
    public Binding renewalAccountDlxBinding() {
        return stepBinding(renewalAccountDlxQueue(), renewalStepDlxExchange(), RabbitMqBinding.RENEWAL_ACCOUNT_DLX);
    }

    @Bean
    public Binding renewalLeagueDlxBinding() {
        return stepBinding(renewalLeagueDlxQueue(), renewalStepDlxExchange(), RabbitMqBinding.RENEWAL_LEAGUE_DLX);
    }

    @Bean
    public Binding renewalMatchDlxBinding() {
        return stepBinding(renewalMatchDlxQueue(), renewalStepDlxExchange(), RabbitMqBinding.RENEWAL_MATCH_DLX);
    }

    private Queue renewalStepQueue(RabbitMqBinding binding, RabbitMqBinding dlx) {
        return QueueBuilder.durable(binding.getQueue())
                .withArgument("x-dead-letter-exchange", dlx.getExchange())
                .withArgument("x-dead-letter-routing-key", dlx.getRoutingKey())
                .build();
    }

    private Binding stepBinding(Queue queue, DirectExchange exchange, RabbitMqBinding binding) {
        return BindingBuilder.bind(queue).to(exchange).with(binding.getRoutingKey());
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(rabbitmqHost);
        connectionFactory.setPort(rabbitmqPort);
        connectionFactory.setUsername(rabbitmqUsername);
        connectionFactory.setPassword(rabbitmqPassword);
        connectionFactory.setVirtualHost(rabbitmqVirtualHost);
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

        simpleFactory.setConcurrentConsumers(20);
        simpleFactory.setMaxConcurrentConsumers(20);

        simpleFactory.setPrefetchCount(1);
        simpleFactory.setReceiveTimeout(1000L);

        return simpleFactory;
    }

    /**
     * 추가 매치 찾기 큐(renewal.match.find.queue) 전용. {@code lol.renewal.background.enabled=false} 면 리스너를 자동으로 시작하지
     * 않는다 — 추가 갱신을 잠시 끄는 동안 메시지는 버리지 않고 큐에 남는다.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory findQueueSimpleRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory factory,
            @Qualifier("rabbitListenerTaskExecutor")
            ObjectProvider<TaskExecutor> rabbitListenerTaskExecutorProvider,
            @Value("${lol.renewal.background.enabled:true}") boolean backgroundEnabled
    ) {
        SimpleRabbitListenerContainerFactory simpleFactory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(simpleFactory, factory);

        rabbitListenerTaskExecutorProvider.ifAvailable(simpleFactory::setTaskExecutor);
        simpleFactory.setConcurrentConsumers(1);
        simpleFactory.setMaxConcurrentConsumers(1);

        simpleFactory.setPrefetchCount(1);
        simpleFactory.setReceiveTimeout(1000L);
        simpleFactory.setAutoStartup(backgroundEnabled);

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
