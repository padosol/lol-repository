package lol.mmrtr.lolrepository.rabbitmq.service;

import com.rabbitmq.client.Channel;
import lol.mmrtr.lolrepository.domain.league.entity.League;
import lol.mmrtr.lolrepository.domain.league.entity.LeagueSummonerDetail;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueRepository;
import lol.mmrtr.lolrepository.domain.league.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.domain.league.entity.id.LeagueSummonerId;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueSummonerDetailJpaRepository;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueSummonerRepository;
import lol.mmrtr.lolrepository.domain.league.service.LeagueService;
import lol.mmrtr.lolrepository.domain.match.entity.Challenges;
import lol.mmrtr.lolrepository.domain.match.entity.Match;
import lol.mmrtr.lolrepository.domain.match.entity.MatchSummoner;
import lol.mmrtr.lolrepository.domain.match.entity.MatchTeam;
import lol.mmrtr.lolrepository.domain.match.repository.ChallengesRepository;
import lol.mmrtr.lolrepository.domain.match.repository.MatchRepository;
import lol.mmrtr.lolrepository.domain.match.repository.MatchSummonerRepository;
import lol.mmrtr.lolrepository.domain.match.repository.MatchTeamRepository;
import lol.mmrtr.lolrepository.domain.match.service.MatchService;
import lol.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import lol.mmrtr.lolrepository.domain.summoner.repository.SummonerRepository;
import lol.mmrtr.lolrepository.rabbitmq.dto.SummonerMessage;
import lol.mmrtr.lolrepository.redis.model.SummonerRenewalSession;
import lol.mmrtr.lolrepository.redis.repository.SummonerRedisRepository;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.account.AccountDto;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lol.mmrtr.lolrepository.riot.dto.match.ChallengesDto;
import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.riot.dto.match.ParticipantDto;
import lol.mmrtr.lolrepository.riot.dto.match.TeamDto;
import lol.mmrtr.lolrepository.riot.dto.match_timeline.TimelineDto;
import lol.mmrtr.lolrepository.riot.dto.summoner.SummonerDTO;
import lol.mmrtr.lolrepository.riot.type.Platform;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqService {

    private final SummonerRepository summonerRepository;
    private final SummonerRedisRepository summonerRedisRepository;
    private final RabbitTemplate rabbitTemplate;

    private final MatchService matchService;
    private final LeagueService leagueService;


    @RabbitListener(queues = "mmrtr.matchId", containerFactory = "batchRabbitListenerContainerFactory")
    public void receiveMessage(List<String> matchIds) {
        log.info("MatchId: {}", matchIds);
        String s = matchIds.get(0);
        String platform = s.substring(0, 2);

        List<MatchDto> matchDtos = RiotAPI.match(Platform.valueOf(platform)).byMatchIds(matchIds);
        List<TimelineDto> timelineDtos = RiotAPI.timeLine(Platform.valueOf(platform)).byMatchIds(matchIds);

        matchService.addAllMatch(matchDtos, timelineDtos);

    }

    @Transactional
    @RabbitListener(queues = "mmrtr.summoner", containerFactory = "simpleRabbitListenerContainerFactory")
    public void receiveSummonerMessage(
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Payload SummonerMessage summonerMessage
    ) throws IOException {

        try {
            Platform platform = Platform.valueOfName(summonerMessage.getPlatform());
            String puuid = summonerMessage.getPuuid();
            LocalDateTime now = LocalDateTime.now();

            log.info("유저 전적 갱신 시도 {} {}", platform, puuid);

            long revisionDate = summonerMessage.getRevisionDate();

            SummonerDTO summonerDTO = RiotAPI.summoner(platform).byPuuid(puuid);
            long newRevisionDate = summonerDTO.getRevisionDate();

            SummonerRenewalSession renewalSession = summonerRedisRepository
                    .findById(puuid).orElse(null);

            if (renewalSession == null) {
                channel.basicAck(tag, false);
                return;
            }

            if (revisionDate == newRevisionDate) {
                // 갱신 완료

                summonerRedisRepository.delete(renewalSession);
                log.info("유저 갱신 완료 {}", puuid);
            } else {
                // account, summoner 갱신
                AccountDto accountDto = RiotAPI.account(platform).byPuuid(puuid);
                Summoner summoner = new Summoner(accountDto, summonerDTO, platform);

                summonerRepository.save(summoner);

                renewalSession.summonerUpdate();
                summonerRedisRepository.save(renewalSession);

                // league 갱신
                if (!renewalSession.isLeagueUpdate()) {
                    Set<LeagueEntryDTO> leagueEntryDTOS = RiotAPI.league(platform).byPuuid(puuid);
                    leagueService.addAllLeague(puuid, leagueEntryDTOS);

                    renewalSession.leagueUpdate();
                    summonerRedisRepository.save(renewalSession);
                }

                // match 갱신
                if (!renewalSession.isMatchUpdate()) {
                    List<String> matchAll = RiotAPI.matchList(platform).byPuuid(puuid).getAll();

                    // 20 게임에 대해서만 즉시 추가
                    List<String> firstInsertMatchIds = matchAll.subList(0, 20);
                    List<Match> matches = matchService.findAllMatch(firstInsertMatchIds);
                    Set<String> findMatchIds = matches.stream().map(Match::getMatchId).collect(Collectors.toSet());

                    Set<String> insertMatchIds = firstInsertMatchIds.stream().filter(matchId -> !findMatchIds.contains(matchId)).collect(Collectors.toSet());
                    List<MatchDto> matchDtos = RiotAPI.match(platform).byMatchIds(insertMatchIds);
                    List<TimelineDto> timelineDtos = RiotAPI.timeLine(platform).byMatchIds(insertMatchIds);

                    matchService.addAllMatch(matchDtos, timelineDtos);

                    // 나머지 게임은 백그라운드로 처리함.
                    List<String> backgroundProgressMatchIds = matchAll.subList(20, matchAll.size());

                    renewalSession.matchUpdate();
                    summonerRedisRepository.save(renewalSession);

                    for (String backgroundProgressMatchId : backgroundProgressMatchIds) {
                        sendMessageByMatchId(backgroundProgressMatchId);
                    }
                }

                summonerRedisRepository.delete(renewalSession);
                log.info("새로운 유저 정보 갱신 시작 {}", puuid);
            }
        } catch(Exception e) {
            channel.basicReject(tag, false);
        }
    }

    public void sendMessageByMatchId(String matchId) {
        rabbitTemplate.convertAndSend("mmrtr.matchId.exchange", "mmrtr.routingkey.matchId", matchId);
    }
}
