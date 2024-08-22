package lol.mmrtr.lolrepository.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.consumer.bootstrap-servers}")
    private String boostrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, boostrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "group_1");

        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        config.put(JsonDeserializer.TYPE_MAPPINGS,
                "summoner:lol.mmrtr.lolrepository.message.SummonerMessage," +
                "league:lol.mmrtr.lolrepository.message.LeagueMessage," +
                "league_summoner:lol.mmrtr.lolrepository.message.LeagueSummonerMessage," +
                "match:lol.mmrtr.lolrepository.dto.match.MatchDto," +
                "match_timeline:lol.mmrtr.lolrepository.dto.match_timeline.TimelineDto"
        );

        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");


        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConsumerFactory<String ,Object> batchConsumerFactory() {
        Map<String ,Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, boostrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "group_1");

        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        config.put(JsonDeserializer.TYPE_MAPPINGS,
                "summoner:lol.mmrtr.lolrepository.message.SummonerMessage," +
                        "league:lol.mmrtr.lolrepository.message.LeagueMessage," +
                        "league_summoner:lol.mmrtr.lolrepository.message.LeagueSummonerMessage," +
                        "match:lol.mmrtr.lolrepository.riot.dto.match.MatchDto," +
                        "match_timeline:lol.mmrtr.lolrepository.riot.dto.match_timeline.TimelineDto"
        );

        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 30);
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 5000);
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 2500);

        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);


        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory());
        factory.getContainerProperties().setIdleBetweenPolls(1000);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchListener(true);

        return factory;
    }

}
