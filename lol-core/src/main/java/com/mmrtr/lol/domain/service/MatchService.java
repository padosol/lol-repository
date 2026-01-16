package com.mmrtr.lol.domain.service;

import com.mmrtr.lol.domain.entity.Challenges;
import com.mmrtr.lol.domain.match.entity.ChallengesEntity;
import com.mmrtr.lol.domain.match.entity.MatchEntity;
import com.mmrtr.lol.domain.match.entity.MatchSummonerEntity;
import com.mmrtr.lol.domain.match.entity.MatchTeamEntity;
import com.mmrtr.lol.domain.match.repository.ChallengesRepository;
import com.mmrtr.lol.domain.match.repository.MatchRepository;
import com.mmrtr.lol.domain.match.repository.MatchSummonerRepository;
import com.mmrtr.lol.domain.match.repository.MatchTeamRepository;
import com.mmrtr.lol.riot.dto.match.MatchDto;
import com.mmrtr.lol.riot.dto.match.ParticipantDto;
import com.mmrtr.lol.riot.dto.match.TeamDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        MatchEntity match = new MatchEntity(matchDto,25);

        // match_summoner, challenges
        List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

        List<MatchSummonerEntity> matchSummoners = new ArrayList<>();
        List<Challenges> challenges = new ArrayList<>();

        for (ParticipantDto participant : participants) {
            matchSummoners.add(MatchSummonerEntity.of(match, participant));
            challenges.add(new Challenges(matchDto, participant, participant.getChallenges()));
        }

        // match_team
        List<TeamDto> teams = matchDto.getInfo().getTeams();
        List<MatchTeamEntity> matchTeams = new ArrayList<>();
        for (TeamDto team : teams) {
            matchTeams.add(MatchTeamEntity.of(match, team));
        }

        matchRepository.save(match);
        matchSummonerRepository.bulkSave(matchSummoners);
        matchTeamRepository.bulkSave(matchTeams);
//        challengesRepository.bulkSave(challenges);
    }

    public void bulkSave(List<MatchDto> matchDtoList) {

        List<MatchEntity> matches = new ArrayList<>();
        List<MatchSummonerEntity> matchSummoners = new ArrayList<>();
        List<ChallengesEntity> challenges = new ArrayList<>();
        List<MatchTeamEntity> matchTeams = new ArrayList<>();

        for (MatchDto matchDto : matchDtoList) {

            if(matchDto.isError()) {
                continue;
            }

            // match
            MatchEntity match = new MatchEntity(matchDto, 25);
            matches.add(match);

            // match_summoner, challenges
            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

            for (ParticipantDto participant : participants) {
                matchSummoners.add(MatchSummonerEntity.of(match, participant));
//                challenges.add(ChallengesEntity.of(matchDto, participant, participant.getChallenges()));
            }

            // match_team
            List<TeamDto> teams = matchDto.getInfo().getTeams();
            for (TeamDto team : teams) {
                matchTeams.add(MatchTeamEntity.of(match, team));
            }

        }

        matchRepository.bulkSave(matches);
        matchSummonerRepository.bulkSave(matchSummoners);
        matchTeamRepository.bulkSave(matchTeams);
//        challengesRepository.bulkSave(challenges);
    }

}
