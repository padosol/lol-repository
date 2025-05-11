package lol.mmrtr.lolrepository.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
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
    public Queue matchIdQueue() {
        return new Queue("mmrtr.mathId");
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
//    @Bean
//    public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory() {
//        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
//        factory.setConnectionFactory(connectionFactory());
//        factory.setMessageConverter(jackson2JsonMessageConverter());
//
//        // Prefetch 설정
//        factory.setPrefetchCount(20);
//
//        // 동시 소비자 수 설정
//        factory.setConcurrentConsumers(2);
//        factory.setMaxConcurrentConsumers(5);
//
//        // 배치 크기 설정
//        factory.setBatchSize(50);
//
//        // 처리 간격 설정
//        factory.setConsumerBatchEnabled(true);
//        factory.setReceiveTimeout(1000L);
//
//        return factory;
//    }

}
