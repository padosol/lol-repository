package lol.mmrtr.lolrepository.rabbitmq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lol.mmrtr.lolrepository.domain.league.entity.League;
import lol.mmrtr.lolrepository.domain.league.repository.LeagueRepository;
import lol.mmrtr.lolrepository.domain.league_summoner.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.entity.Summoner;
import lol.mmrtr.lolrepository.rabbitmq.dto.SummonerMessage;
import lol.mmrtr.lolrepository.redis.model.SummonerRenewalSession;
import lol.mmrtr.lolrepository.repository.LeagueSummonerRepository;
import lol.mmrtr.lolrepository.repository.SummonerRepository;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.account.AccountDto;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lol.mmrtr.lolrepository.riot.dto.summoner.SummonerDTO;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SummonerRepository summonerRepository;
    private final AsyncRabbitService asyncRabbitService;
    private final LeagueRepository leagueRepository;
    private final LeagueSummonerRepository leagueSummonerRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "mmrtr.match")
    public void receiveMessage(String matchId) {
        asyncRabbitService.processSummonerRefreshAsync(matchId);
    }

    @Transactional
    @RabbitListener(queues = "mmrtr.summoner")
    public void receiveSummonerMessage(SummonerMessage summonerMessage) throws JsonProcessingException {

        // 유저 정보 호출해서 갱신가능한지 체크
        Platform platform = Platform.valueOfName(summonerMessage.getPlatform());
        String puuid = summonerMessage.getPuuid();
        long revisionDate = summonerMessage.getRevisionDate();

        SummonerDTO summonerDTO = RiotAPI.summoner(platform).byPuuid(puuid);
        long newRevisionDate = summonerDTO.getRevisionDate();

        HashOperations<String, Object, Object> summonerHash = redisTemplate.opsForHash();
        String hashString = (String) summonerHash.get("renewal", puuid);
        if (!StringUtils.hasText(hashString)) {
            return;
        }
        SummonerRenewalSession renewalSession = objectMapper.readValue(
                hashString,
                SummonerRenewalSession.class
        );



        log.info("renewalSession: {}", renewalSession);

        if (revisionDate == newRevisionDate) {
            // 갱신 완료
            log.info("유저 갱신 완료 {}", puuid);
        } else {

            // account, summoner 갱신
            if (!renewalSession.isAccountUpdate()) {
                AccountDto accountDto = RiotAPI.account(platform).byPuuid(puuid);
                Summoner summoner = new Summoner(accountDto, summonerDTO, platform);

                summonerRepository.save(summoner);

                renewalSession.summonerUpdate();
                summonerHash.put("renewal", puuid, renewalSession);
            }

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
                        leagueRepository.save(newLeague);
                    }
                    LeagueSummoner leagueSummoner = new LeagueSummoner(
                        puuid, leagueEntryDTO
                    );
                    leagueSummonerRepository.save(leagueSummoner);
                }

                renewalSession.leagueUpdate();
                summonerHash.put("renewal", puuid, renewalSession);
            }

            // match 갱신
            if (!renewalSession.isMatchUpdate()) {
                List<String> matchAll = RiotAPI.matchList(platform).byPuuid(puuid).getAll();

                // 20 게임에 대해서만 즉시 추가
                // 나머지 게임은 백그라운드로 처리함.


                renewalSession.matchUpdate();
                summonerHash.put("renewal", puuid, renewalSession);
            }

            log.info("새로운 유저 정보 갱신 시작 {}", puuid);
        }
    }
}
