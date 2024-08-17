package lol.mmrtr.lolrepository.kafka_consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MatchIdConsumer {

    @KafkaListener(topics = "matchId", groupId = "group_1", containerFactory = "kafkaListenerContainerFactory")
    public void listener0(
            @Payload List<String> matchIds
    )  {
        log.info("[{}] match message 도착", matchIds.size());
    }
}
