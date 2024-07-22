package lol.mmrtr.lolrepository.kafka_consumer;

import lol.mmrtr.lolrepository.entity.League;
import lol.mmrtr.lolrepository.message.LeagueMessage;
import lol.mmrtr.lolrepository.repository.LeagueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LeagueConsumer {

    private final LeagueRepository leagueRepository;

    public LeagueConsumer(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @KafkaListener(topics = "league", groupId = "group_1")
    public void listener(
        @Payload LeagueMessage message
    ) {
        log.info("league message 도착");
        leagueRepository.save(new League(message));
    }
}
