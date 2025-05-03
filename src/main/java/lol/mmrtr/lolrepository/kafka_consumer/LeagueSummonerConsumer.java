package lol.mmrtr.lolrepository.kafka_consumer;

import lol.mmrtr.lolrepository.domain.league_summoner.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.message.LeagueSummonerMessage;
import lol.mmrtr.lolrepository.repository.LeagueSummonerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LeagueSummonerConsumer {

    private final LeagueSummonerRepository leagueSummonerRepository;

    public LeagueSummonerConsumer(LeagueSummonerRepository leagueSummonerRepository) {
        this.leagueSummonerRepository = leagueSummonerRepository;
    }

//    @KafkaListener(topics = "league_summoner", groupId = "group_1")
    public void listener(
            @Payload LeagueSummonerMessage message
    ) {
        log.info("league_summoner message 도착");
        leagueSummonerRepository.save(new LeagueSummoner(message));
    }
}
