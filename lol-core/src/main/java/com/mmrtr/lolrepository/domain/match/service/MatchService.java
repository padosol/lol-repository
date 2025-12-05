package com.mmrtr.lolrepository.domain.match.service;

import com.mmrtr.lolrepository.domain.match.entity.Challenges;
import com.mmrtr.lolrepository.domain.match.entity.Match;
import com.mmrtr.lolrepository.domain.match.entity.MatchSummoner;
import com.mmrtr.lolrepository.domain.match.entity.MatchTeam;
import com.mmrtr.lolrepository.domain.match.repository.ChallengesRepository;
import com.mmrtr.lolrepository.domain.match.repository.MatchRepository;
import com.mmrtr.lolrepository.domain.match.repository.MatchSummonerRepository;
import com.mmrtr.lolrepository.domain.match.repository.MatchTeamRepository;
import com.mmrtr.lolrepository.riot.dto.match.ChallengesDto;
import com.mmrtr.lolrepository.riot.dto.match.MatchDto;
import com.mmrtr.lolrepository.riot.dto.match.ParticipantDto;
import com.mmrtr.lolrepository.riot.dto.match.TeamDto;
import com.mmrtr.lolrepository.riot.dto.match_timeline.TimelineDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchSummonerRepository matchSummonerRepository;
    private final ChallengesRepository challengesRepository;
    private final MatchTeamRepository matchTeamRepository;

    @Transactional
    public void addAllMatch(List<MatchDto> matchDtos, List<TimelineDto> timelineDtos) {

        for (MatchDto matchDto : matchDtos) {
            Match match = matchRepository.save(new Match(matchDto));

            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

            List<MatchSummoner> matchSummoners = new ArrayList<>();
            List<Challenges> challenges = new ArrayList<>();
            for (ParticipantDto participant : participants) {
                MatchSummoner matchSummoner = MatchSummoner.of(match, participant);
                ChallengesDto challengesDto = participant.getChallenges();

                if (challengesDto == null) {
                    continue;
                }

                matchSummoners.add(matchSummoner);
                challenges.add(Challenges.of(
                        matchSummoner, challengesDto
                ));
            }

            matchSummonerRepository.saveAll(matchSummoners);
            challengesRepository.saveAll(challenges);

            List<TeamDto> teams = matchDto.getInfo().getTeams();
            List<MatchTeam> matchTeams = new ArrayList<>();
            for (TeamDto team : teams) {
                MatchTeam matchTeam = MatchTeam.of(match, team);
                matchTeams.add(matchTeam);
            }

            matchTeamRepository.saveAll(matchTeams);
        }

        for (TimelineDto timelineDto : timelineDtos) {

        }

    }

    public List<Match> findAllMatch(List<String> matchIds) {
        return  matchRepository.findAllByIds(matchIds);
    }

}
