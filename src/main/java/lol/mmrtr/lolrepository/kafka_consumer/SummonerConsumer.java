package lol.mmrtr.lolrepository.kafka_consumer;

import lol.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import lol.mmrtr.lolrepository.message.SummonerMessage;
import lol.mmrtr.lolrepository.repository.SummonerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SummonerConsumer {

    private final SummonerRepository summonerRepository;

    public SummonerConsumer(SummonerRepository summonerRepository) {
        this.summonerRepository = summonerRepository;
    }

//    @KafkaListener(topics = "summoner", groupId = "group_1")
    public void listener(
        @Headers MessageHeaders messageHeaders,
        @Payload SummonerMessage message
    ) {
        log.info("summoner message 도착");
        summonerRepository.save(new Summoner(message));
    }

}
