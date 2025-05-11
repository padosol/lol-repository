package lol.mmrtr.lolrepository.rabbitmq.service;

import com.rabbitmq.client.Channel;
import lol.mmrtr.lolrepository.domain.league.entity.League;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueRepository;
import lol.mmrtr.lolrepository.domain.league_summoner.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.domain.league_summoner.entity.LeagueSummonerId;
import lol.mmrtr.lolrepository.domain.match.entity.Match;
import lol.mmrtr.lolrepository.domain.match.entity.MatchSummoner;
import lol.mmrtr.lolrepository.domain.match.repository.MatchSummonerRepository;
import lol.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import lol.mmrtr.lolrepository.rabbitmq.dto.SummonerMessage;
import lol.mmrtr.lolrepository.redis.model.SummonerRenewalSession;
import lol.mmrtr.lolrepository.redis.repository.SummonerRedisRepository;
import lol.mmrtr.lolrepository.domain.league_summoner.repository.LeagueSummonerRepository;
import lol.mmrtr.lolrepository.domain.match.repository.MatchRepository;
import lol.mmrtr.lolrepository.domain.summoner.repository.SummonerRepository;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.account.AccountDto;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.riot.dto.match.ParticipantDto;
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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqService {

    private final SummonerRepository summonerRepository;
    private final AsyncRabbitService asyncRabbitService;
    private final LeagueRepository leagueRepository;
    private final LeagueSummonerRepository leagueSummonerRepository;
    private final SummonerRedisRepository summonerRedisRepository;
    private final RabbitTemplate rabbitTemplate;
    private final MatchRepository matchRepository;
    private final MatchSummonerRepository matchSummonerRepository;

    @RabbitListener(queues = "mmrtr.match")
    public void receiveMessage(String matchId) {
        asyncRabbitService.processSummonerRefreshAsync(matchId);
    }

    @Transactional
    @RabbitListener(queues = "mmrtr.summoner")
    public void receiveSummonerMessage(
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Payload SummonerMessage summonerMessage
    ) throws IOException {
        // 유저 정보 호출해서 갱신가능한지 체크
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

            Summoner saveSummoner = summonerRepository.save(summoner);

            renewalSession.summonerUpdate();
            summonerRedisRepository.save(renewalSession);

            // league 갱신
            if (!renewalSession.isLeagueUpdate()) {
                Set<LeagueEntryDTO> leagueEntryDTOS = RiotAPI.league(platform).byPuuid(puuid);
                for (LeagueEntryDTO leagueEntryDTO : leagueEntryDTOS) {
                    String leagueId = leagueEntryDTO.getLeagueId();
                    League league = leagueRepository.findById(leagueId);
                    if (league == null) {
                        League newLeague = new League(
                                leagueEntryDTO.getLeagueId(),
                                leagueEntryDTO.getTier(),
                                leagueEntryDTO.getQueueType()
                        );
                        league = leagueRepository.save(newLeague);
                    }
                    LeagueSummoner leagueSummoner = new LeagueSummoner(
                            new LeagueSummonerId(
                                    league.getLeagueId(),
                                    puuid,
                                    now
                            ),
                            saveSummoner,
                            league,
                            leagueEntryDTO
                    );

                    leagueSummonerRepository.save(leagueSummoner);
                }

                renewalSession.leagueUpdate();
                summonerRedisRepository.save(renewalSession);
            }

            // match 갱신
            if (!renewalSession.isMatchUpdate()) {
                List<String> matchAll = RiotAPI.matchList(platform).byPuuid(puuid).getAll();

                // 20 게임에 대해서만 즉시 추가
                List<String> firstInsertMatchIds = matchAll.subList(0, 20);
                List<Match> allByIds = matchRepository.findAllByIds(firstInsertMatchIds);
                Set<String> findMatchIds = allByIds.stream().map(Match::getMatchId).collect(Collectors.toSet());
                List<MatchDto> matchDtos = RiotAPI.match(platform).byMatchIds(findMatchIds);

                // 중복 등록 방지를 위해 Match 는 Lock 을 거는게 좋아보임
                for (MatchDto matchDto : matchDtos) {
                    Match match = new Match(matchDto);
                    Match savedMatch = matchRepository.save(match);

                    List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

                    List<MatchSummoner> matchSummoners = new ArrayList<>();
                    for (ParticipantDto participant : participants) {
                        MatchSummoner matchSummoner = new MatchSummoner(matchDto, participant);
                        matchSummoners.add(matchSummoner);
                    }

                    matchSummonerRepository.bulkSave(matchSummoners);
                }

                // 나머지 게임은 백그라운드로 처리함.
                List<String> backgroundProgressMatchIds = matchAll.subList(20, matchAll.size());
                for (String backgroundProgressMatchId : backgroundProgressMatchIds) {
                    sendMessageByMatchId(backgroundProgressMatchId);
                }

                renewalSession.matchUpdate();
                summonerRedisRepository.save(renewalSession);
            }

            summonerRedisRepository.delete(renewalSession);
            log.info("새로운 유저 정보 갱신 시작 {}", puuid);
        }
    }

    public void sendMessageByMatchId(String matchId) {
        rabbitTemplate.convertAndSend("mmrtr.matchId.exchange", "mmrtr.routingkey.matchId", matchId);
    }
}
