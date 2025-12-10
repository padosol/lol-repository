package com.mmrtr.lol.rabbitmq.listener;

import com.mmrtr.lol.domain.match.entity.Match;
import com.mmrtr.lol.domain.match.service.MatchService;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.entity.SummonerEntity;
import com.mmrtr.lol.domain.summoner.repository.SummonerRepository;
import com.mmrtr.lol.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.redis.service.RedisLockHandler;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.riot.dto.match.MatchDto;
import com.mmrtr.lol.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.riot.service.RiotApiService;
import com.mmrtr.lol.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerRenewalListener {

    private final RabbitTemplate rabbitTemplate;
    private final RedisLockHandler redisLockHandler;
    private final RiotApiService riotApiService;
    private final MatchService matchService;
    private final SummonerRepository summonerRepository;

    @RabbitListener(queues = "mmrtr.summoner", containerFactory = "simpleRabbitListenerContainerFactory")
    @Transactional
    public void receiveSummonerMessageV2(@Payload SummonerMessage summonerMessage) {
        String puuid = summonerMessage.getPuuid();
        if (!redisLockHandler.acquireLock(puuid, Duration.ofMinutes(3L))) {
            log.info("이미 전적 갱신 진행 중 입니다. {}", puuid);
            return;
        }

        long start = System.currentTimeMillis();
        log.info("Lock 획득, 유저 전적 갱신 시작: {}", puuid);
        Platform platform = Platform.valueOfName(summonerMessage.getPlatform());

        CompletableFuture<SummonerDto> summonerDtoFuture = riotApiService.getSummonerByPuuid(puuid, platform);
        SummonerDto summonerDto = summonerDtoFuture.join();
        assert summonerDto != null;
        if (summonerMessage.getRevisionDate() == summonerDto.getRevisionDate()) {
            log.info("revisionDate is same. No need to update.");
            redisLockHandler.deleteSummonerRenewal(puuid);
            redisLockHandler.releaseLock(puuid);
            return;
        }

        CompletableFuture<AccountDto> accountDtoFuture = riotApiService.getAccountByPuuid(puuid, platform);
        CompletableFuture<Set<LeagueEntryDto>> leagueEntryDtoFuture = riotApiService.getLeagueEntriesByPuuid(puuid, platform);
        CompletableFuture<List<String>> matchIdListFuture = riotApiService.getMatchListByPuuid(puuid, platform, summonerMessage.getRevisionDate(), 0, 20);

        CompletableFuture<List<MatchDto>> matchListFuture = matchIdListFuture.thenCompose(matchIds -> {
            if (matchIds.size() == 20) {
                log.info("matchIds size is 20. send message for search more matchIds");
                rabbitTemplate.convertAndSend(
                        "renewal.topic.exchange",
                        "renewal.match.find",
                        new SummonerRenewalMessage(puuid,  platform.getPlatformId(), summonerMessage.getRevisionDate())
                );
            }

            List<Match> matchList = matchService.findAllMatch(matchIds);
            List<String> existMatchIds = matchList.stream().map(Match::getMatchId).toList();

            List<String> filteredMatchIds = matchIds.stream().filter(matchId -> !existMatchIds.contains(matchId)).toList();

            List<CompletableFuture<MatchDto>> matchAllOfFuture = filteredMatchIds.stream()
                    .map(mathId -> riotApiService.getMatchById(mathId, platform))
                    .toList();

            CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(matchAllOfFuture.toArray(new CompletableFuture[0]));

            return allOfFuture.thenApply(v -> matchAllOfFuture.stream()
                    .map(CompletableFuture::join)
                    .toList());
        });

        Summoner summoner = accountDtoFuture.thenCombine(
                leagueEntryDtoFuture,
                (accountDto, leagueEntryDtos) ->
                    Summoner.of(accountDto, summonerDto, platform.getPlatformId(), leagueEntryDtos)
                ).join();

        List<MatchDto> matchDtos = matchListFuture.join();

        long middle = System.currentTimeMillis();
        log.info("middle {} ms", middle - start);

        summonerRepository.save(SummonerEntity.of(summoner));
        matchService.addAllMatch(matchDtos, new ArrayList<>());

        redisLockHandler.deleteSummonerRenewal(puuid);
        redisLockHandler.releaseLock(puuid);

        long end = System.currentTimeMillis();
        log.info("소요 시간: {}ms", end - start);
        log.info("Lock 해제, 유저 전적 갱신 완료: {}", puuid);
    }


}
