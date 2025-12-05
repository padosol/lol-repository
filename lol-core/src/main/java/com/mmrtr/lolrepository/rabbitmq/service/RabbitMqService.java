package com.mmrtr.lolrepository.rabbitmq.service;

import com.rabbitmq.client.Channel;
import com.mmrtr.lolrepository.domain.league.service.LeagueService;
import com.mmrtr.lolrepository.domain.match.entity.Match;
import com.mmrtr.lolrepository.domain.match.service.MatchService;
import com.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import com.mmrtr.lolrepository.domain.summoner.repository.SummonerRepository;
import com.mmrtr.lolrepository.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lolrepository.redis.model.SummonerRenewalSession;
import com.mmrtr.lolrepository.redis.repository.SummonerRedisRepository;
import com.mmrtr.lolrepository.redis.service.RedisLockHandler;
import com.mmrtr.lolrepository.riot.core.api.RiotAPI;
import com.mmrtr.lolrepository.riot.dto.account.AccountDto;
import com.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import com.mmrtr.lolrepository.riot.dto.match.MatchDto;
import com.mmrtr.lolrepository.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lolrepository.riot.dto.summoner.SummonerDTO;
import com.mmrtr.lolrepository.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqService {

    private final SummonerRepository summonerRepository;
    private final RabbitTemplate rabbitTemplate;

    private final MatchService matchService;
    private final LeagueService leagueService;

    private final RedisLockHandler redisLockHandler;


    @RabbitListener(queues = "mmrtr.matchId", containerFactory = "batchRabbitListenerContainerFactory")
    public void receiveMessage(List<String> matchIds) {
        log.info("MatchId: {}", matchIds);

        long start = System.currentTimeMillis();
        log.info("start");
        String s = matchIds.get(0);
        String platform = s.substring(0, 2);

        List<MatchDto> matchDtos = RiotAPI.match(Platform.valueOf(platform)).byMatchIds(matchIds);
        List<TimelineDto> timelineDtos = RiotAPI.timeLine(Platform.valueOf(platform)).byMatchIds(matchIds);

        matchService.addAllMatch(matchDtos, timelineDtos);

        long end = System.currentTimeMillis();
        log.info("end 걸린시간: {} ms", end - start);

    }

    @Transactional
    @RabbitListener(queues = "mmrtr.summoner", containerFactory = "simpleRabbitListenerContainerFactory")
    public void receiveSummonerMessage(
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Payload SummonerMessage summonerMessage
    ) throws IOException {
        String puuid = summonerMessage.getPuuid();

        // 가장 먼저 락을 휙득 하자.
        boolean result = redisLockHandler.acquireLock(puuid, Duration.ofMinutes(3L));
        if (!result) {
            log.info("이미 전적 갱신 진행 중 입니다. {}", puuid);
            return;
        }

        try {
            Platform platform = Platform.valueOfName(summonerMessage.getPlatform());
            log.info("Lock 휙득 {}, {}", result, puuid);
            // Lock 을 휙득 했으면 RedisTemplate 로 리그, 유저, 매치에 대한 갱신을 시작함.

            log.info("유저 전적 갱신 시도 {} {}", platform, puuid);

            long revisionDate = summonerMessage.getRevisionDate();

            SummonerDTO summonerDTO = RiotAPI.summoner(platform).byPuuid(puuid);
            long newRevisionDate = summonerDTO.getRevisionDate();

            // 넘겨 받은 갱신 시간과 기존 갱신 시간이 같다면 갱신을 할 필요가 없음.
            if (revisionDate == newRevisionDate) {
                log.info("유저 갱신 완료 {}", puuid);
                redisLockHandler.releaseLock(puuid);
                redisLockHandler.deleteSummonerRenewal(puuid);
                return;
            }
            // account, summoner 갱신
            AccountDto accountDto = RiotAPI.account(platform).byPuuid(puuid);
            Summoner summoner = new Summoner(accountDto, summonerDTO, platform);

            summonerRepository.save(summoner);
            log.info("유저 갱신 완료 {}", puuid);

            // league 갱신
            Set<LeagueEntryDTO> leagueEntryDTOS = RiotAPI.league(platform).byPuuid(puuid);
            leagueService.addAllLeague(puuid, leagueEntryDTOS);

            log.info("리그 정보 갱신 완료 {}", puuid);

            // match 갱신
            List<String> matchAll = RiotAPI.matchList(platform).byPuuid(puuid).getAll();

            // 20 게임에 대해서만 즉시 추가
            List<String> firstInsertMatchIds = matchAll.subList(0, 20);
            List<Match> matches = matchService.findAllMatch(firstInsertMatchIds);
            Set<String> findMatchIds = matches.stream().map(Match::getMatchId).collect(Collectors.toSet());

            Set<String> insertMatchIds = firstInsertMatchIds.stream().filter(matchId -> !findMatchIds.contains(matchId)).collect(Collectors.toSet());
            List<MatchDto> matchDtos = RiotAPI.match(platform).byMatchIds(insertMatchIds);
            List<TimelineDto> timelineDtos = RiotAPI.timeLine(platform).byMatchIds(insertMatchIds);

            matchService.addAllMatch(matchDtos, timelineDtos);

            log.info("매치 정보 갱신 완료 {}", puuid);

            // 나머지 게임은 백그라운드로 처리함.
            List<String> backgroundProgressMatchIds = matchAll.subList(20, matchAll.size());

            for (String backgroundProgressMatchId : backgroundProgressMatchIds) {
                sendMessageByMatchId(backgroundProgressMatchId);
            }

            boolean b = redisLockHandler.releaseLock(puuid);
            redisLockHandler.deleteSummonerRenewal(puuid);

            log.info("Lock 해제 여부: {}, puuid: {}", b, puuid);
            log.info("새로운 유저 정보 갱신 완료 {}", puuid);
        } catch(Exception e) {
            channel.basicReject(tag, false);
        }
    }

    public void sendMessageByMatchId(String matchId) {
        rabbitTemplate.convertAndSend("mmrtr.matchId.exchange", "mmrtr.routingkey.matchId", matchId);
    }
}
