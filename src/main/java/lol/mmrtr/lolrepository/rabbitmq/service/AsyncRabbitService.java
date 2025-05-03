package lol.mmrtr.lolrepository.rabbitmq.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncRabbitService {

    @Async("taskExecutor")
    public void processSummonerRefreshAsync(String matchId) {
        log.info("MatchId: {}", matchId);
        try {
            Thread.sleep(2000);
        } catch(Exception e) {

        }
    }

}
