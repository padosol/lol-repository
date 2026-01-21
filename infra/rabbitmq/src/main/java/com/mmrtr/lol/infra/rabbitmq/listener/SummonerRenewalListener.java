package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.service.MatchService;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.infra.persistence.summoner.repository.SummonerRepositoryImpl;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.redis.service.RedisLockHandler;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.common.type.Platform;
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
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerRenewalListener {

    private final RabbitTemplate rabbitTemplate;
    private final RedisLockHandler redisLockHandler;
    private final RiotApiService riotApiService;
    private final MatchService matchService;
    private final SummonerRepositoryImpl summonerRepository;
    private final Executor requestExecutor;

    @RabbitListener(queues = "mmrtr.summoner", containerFactory = "simpleRabbitListenerContainerFactory")
    @Transactional
    public void receiveSummonerMessageV2(@Payload SummonerMessage summonerMessage) {
        log.info("전적 갱신 요청 {}", summonerMessage);
        String puuid = summonerMessage.getPuuid();
        if (!redisLockHandler.acquireLock(puuid, Duration.ofMinutes(3L))) {
            log.info("이미 전적 갱신 진행 중 입니다. {}", puuid);
            return;
        }

        long start = System.currentTimeMillis();
        log.info("Lock 획득, 유저 전적 갱신 시작: {}", puuid);
        Platform platform = Platform.valueOfName(summonerMessage.getPlatform());

        CompletableFuture<SummonerDto> summonerDtoFuture = riotApiService.getSummonerByPuuid(puuid, platform, requestExecutor);
        SummonerDto summonerDto = summonerDtoFuture.join();
        assert summonerDto != null;
        if (summonerMessage.getRevisionDate() == summonerDto.getRevisionDate()) {
            log.info("revisionDate is same. No need to update.");
            redisLockHandler.deleteSummonerRenewal(puuid);
            redisLockHandler.releaseLock(puuid);
            return;
        }

        CompletableFuture<AccountDto> accountDtoFuture = riotApiService
                .getAccountByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<Set<LeagueEntryDto>> leagueEntryDtoFuture = riotApiService
                .getLeagueEntriesByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<List<String>> matchIdListFuture = riotApiService.getMatchListByPuuid(
                puuid, platform, summonerMessage.getRevisionDate(), 0, 20, requestExecutor);

        CompletableFuture<List<MatchDto>> matchListFuture = matchIdListFuture.thenCompose(matchIds -> {
            if (matchIds.size() == 20) {
                log.info("matchIds size is 20. send message for search more matchIds");
                rabbitTemplate.convertAndSend(
                        "renewal.topic.exchange",
                        "renewal.match.find",
                        new SummonerRenewalMessage(puuid,  platform.getPlatformId(), summonerMessage.getRevisionDate())
                );
            }

            List<MatchEntity> matchList = matchService.findAllMatch(matchIds);
            List<String> existMatchIds = matchList.stream().map(MatchEntity::getMatchId).toList();

            List<String> filteredMatchIds = matchIds.stream().filter(matchId -> !existMatchIds.contains(matchId)).toList();

            List<CompletableFuture<MatchDto>> matchAllOfFuture = filteredMatchIds.stream()
                    .map(mathId -> riotApiService.getMatchById(mathId, platform, requestExecutor))
                    .toList();

            CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(matchAllOfFuture.toArray(new CompletableFuture[0]));

            return allOfFuture.thenApply(v -> matchAllOfFuture.stream()
                    .map(CompletableFuture::join)
                    .toList());
        });

        Summoner summoner = accountDtoFuture.thenCombine(
                leagueEntryDtoFuture,
                (accountDto, leagueEntryDtos) -> {
                    Set<LeagueInfo> leagueInfos = leagueEntryDtos.stream()
                            .map(dto -> LeagueInfo.builder()
                                    .leagueId(dto.getLeagueId())
                                    .queueType(dto.getQueueType())
                                    .tier(dto.getTier())
                                    .rank(dto.getRank())
                                    .leaguePoints(dto.getLeaguePoints())
                                    .wins(dto.getWins())
                                    .losses(dto.getLosses())
                                    .hotStreak(dto.isHotStreak())
                                    .veteran(dto.isVeteran())
                                    .freshBlood(dto.isFreshBlood())
                                    .inactive(dto.isInactive())
                                    .build())
                            .collect(Collectors.toSet());
                    return Summoner.create(
                            accountDto.getPuuid(),
                            accountDto.getGameName(),
                            accountDto.getTagLine(),
                            platform.getPlatformId(),
                            summonerDto.getProfileIconId(),
                            summonerDto.getSummonerLevel(),
                            summonerDto.getRevisionDate(),
                            leagueInfos
                    );
                }).join();

        List<MatchDto> matchDtos = matchListFuture.join();

        long middle = System.currentTimeMillis();
        log.info("middle {} ms", middle - start);

        summonerRepository.save(summoner);
        matchService.addAllMatch(matchDtos, new ArrayList<>());

        redisLockHandler.deleteSummonerRenewal(puuid);
        redisLockHandler.releaseLock(puuid);

        long end = System.currentTimeMillis();
        log.info("소요 시간: {}ms", end - start);
        log.info("Lock 해제, 유저 전적 갱신 완료: {}", puuid);
    }


}
