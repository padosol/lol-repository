package com.mmrtr.lolrepository.domain.service;

import com.mmrtr.lolrepository.domain.entity.Challenges;
import com.mmrtr.lolrepository.domain.match.entity.Match;
import com.mmrtr.lolrepository.domain.match.entity.MatchSummoner;
import com.mmrtr.lolrepository.domain.match.entity.MatchTeam;
import com.mmrtr.lolrepository.domain.match.repository.ChallengesRepository;
import com.mmrtr.lolrepository.domain.match.repository.MatchRepository;
import com.mmrtr.lolrepository.domain.match.repository.MatchSummonerRepository;
import com.mmrtr.lolrepository.domain.match.repository.MatchTeamRepository;
import com.mmrtr.lolrepository.riot.dto.match.MatchDto;
import com.mmrtr.lolrepository.riot.dto.match.ParticipantDto;
import com.mmrtr.lolrepository.riot.dto.match.TeamDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
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
            matchSummoners.add(MatchSummoner.of(match, participant));
            challenges.add(new Challenges(matchDto, participant, participant.getChallenges()));
        }

        // match_team
        List<TeamDto> teams = matchDto.getInfo().getTeams();
        List<MatchTeam> matchTeams = new ArrayList<>();
        for (TeamDto team : teams) {
            matchTeams.add(MatchTeam.of(match, team));
        }

        matchRepository.save(match);
        matchSummonerRepository.bulkSave(matchSummoners);
        matchTeamRepository.bulkSave(matchTeams);
//        challengesRepository.bulkSave(challenges);
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
            Match match = new Match(matchDto);
            matches.add(match);

            // match_summoner, challenges
            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

            for (ParticipantDto participant : participants) {
                matchSummoners.add(MatchSummoner.of(match, participant));
                challenges.add(new Challenges(matchDto, participant, participant.getChallenges()));
            }

            // match_team
            List<TeamDto> teams = matchDto.getInfo().getTeams();
            for (TeamDto team : teams) {
                matchTeams.add(MatchTeam.of(match, team));
            }

        }

        matchRepository.bulkSave(matches);
        matchSummonerRepository.bulkSave(matchSummoners);
        matchTeamRepository.bulkSave(matchTeams);
//        challengesRepository.bulkSave(challenges);
    }

}
