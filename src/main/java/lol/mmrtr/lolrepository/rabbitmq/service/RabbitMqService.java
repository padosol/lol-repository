package lol.mmrtr.lolrepository.rabbitmq.service;

import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqService {

    @RabbitListener(queues = "mmrtr.match")
    public void receiveMessage(String matchId) {
        log.info("MatchId: {}", matchId);
    }

    @RabbitListener(queues = "mmrtr.summoner")
    public void receiveSummonerMessage(String puuid) {
        // 유저 정보 호출해서 갱신가능한지 체크

        log.info("Summoner Puuid: {}", puuid);
    }
}
