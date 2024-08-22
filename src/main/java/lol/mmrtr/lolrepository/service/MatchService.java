package lol.mmrtr.lolrepository.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lol.mmrtr.lolrepository.bucket.BucketService;
import lol.mmrtr.lolrepository.entity.Challenges;
import lol.mmrtr.lolrepository.entity.Match;
import lol.mmrtr.lolrepository.entity.MatchSummoner;
import lol.mmrtr.lolrepository.entity.MatchTeam;
import lol.mmrtr.lolrepository.redis.model.MatchSession;
import lol.mmrtr.lolrepository.repository.ChallengesRepository;
import lol.mmrtr.lolrepository.repository.MatchRepository;
import lol.mmrtr.lolrepository.repository.MatchSummonerRepository;
import lol.mmrtr.lolrepository.repository.MatchTeamRepository;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lol.mmrtr.lolrepository.riot.dto.match.ParticipantDto;
import lol.mmrtr.lolrepository.riot.dto.match.TeamDto;
import lol.mmrtr.lolrepository.riot.dto.match_timeline.TimelineDto;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchSummonerRepository matchSummonerRepository;
    private final MatchTeamRepository matchTeamRepository;
    private final ChallengesRepository challengesRepository;


    @Transactional
    public void save(MatchDto matchDto) {

        // match
        Match match = new Match(matchDto);

        // match_summoner, challenges
        List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

        List<MatchSummoner> matchSummoners = new ArrayList<>();
        List<Challenges> challenges = new ArrayList<>();

        for (ParticipantDto participant : participants) {
            matchSummoners.add(new MatchSummoner(matchDto, participant));
            challenges.add(new Challenges(matchDto, participant, participant.getChallenges()));
        }

        // match_team
        List<TeamDto> teams = matchDto.getInfo().getTeams();
        List<MatchTeam> matchTeams = new ArrayList<>();
        for (TeamDto team : teams) {
            matchTeams.add(new MatchTeam(matchDto, team));
        }

        matchRepository.save(match);
        matchSummonerRepository.bulkSave(matchSummoners);
        matchTeamRepository.bulkSave(matchTeams);
        challengesRepository.bulkSave(challenges);
    }

    public void bulkSave(List<MatchDto> matchDtoList) {

        List<Match> matches = new ArrayList<>();
        List<MatchSummoner> matchSummoners = new ArrayList<>();
        List<Challenges> challenges = new ArrayList<>();
        List<MatchTeam> matchTeams = new ArrayList<>();

        for (MatchDto matchDto : matchDtoList) {

            if(matchDto.isError()) {
                continue;
            }

            // match
            matches.add(new Match(matchDto));

            // match_summoner, challenges
            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

            for (ParticipantDto participant : participants) {
                matchSummoners.add(new MatchSummoner(matchDto, participant));
                challenges.add(new Challenges(matchDto, participant, participant.getChallenges()));
            }

            // match_team
            List<TeamDto> teams = matchDto.getInfo().getTeams();
            for (TeamDto team : teams) {
                matchTeams.add(new MatchTeam(matchDto, team));
            }

        }

        matchRepository.bulkSave(matches);
        matchSummonerRepository.bulkSave(matchSummoners);
        matchTeamRepository.bulkSave(matchTeams);
        challengesRepository.bulkSave(challenges);
    }

}
