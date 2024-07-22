package lol.mmrtr.lolrepository.service;

import lol.mmrtr.lolrepository.dto.match.MatchDto;
import lol.mmrtr.lolrepository.dto.match.ParticipantDto;
import lol.mmrtr.lolrepository.dto.match.TeamDto;
import lol.mmrtr.lolrepository.entity.Challenges;
import lol.mmrtr.lolrepository.entity.Match;
import lol.mmrtr.lolrepository.entity.MatchSummoner;
import lol.mmrtr.lolrepository.entity.MatchTeam;
import lol.mmrtr.lolrepository.repository.ChallengesRepository;
import lol.mmrtr.lolrepository.repository.MatchRepository;
import lol.mmrtr.lolrepository.repository.MatchSummonerRepository;
import lol.mmrtr.lolrepository.repository.MatchTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchSummonerRepository matchSummonerRepository;
    private final MatchTeamRepository matchTeamRepository;
    private final ChallengesRepository challengesRepository;

    public MatchService(MatchRepository matchRepository, MatchSummonerRepository matchSummonerRepository, MatchTeamRepository matchTeamRepository, ChallengesRepository challengesRepository) {
        this.matchRepository = matchRepository;
        this.matchSummonerRepository = matchSummonerRepository;
        this.matchTeamRepository = matchTeamRepository;
        this.challengesRepository = challengesRepository;
    }

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


}
