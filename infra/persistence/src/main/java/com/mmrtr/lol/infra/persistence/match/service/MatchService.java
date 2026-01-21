package com.mmrtr.lol.infra.persistence.match.service;

import com.mmrtr.lol.infra.persistence.match.entity.ChallengesEntity;
import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.entity.MatchSummonerEntity;
import com.mmrtr.lol.infra.persistence.match.entity.MatchTeamEntity;
import com.mmrtr.lol.infra.persistence.match.repository.ChallengesRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchTeamRepositoryImpl;
import com.mmrtr.lol.infra.riot.dto.match.ChallengesDto;
import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import com.mmrtr.lol.infra.riot.dto.match.ParticipantDto;
import com.mmrtr.lol.infra.riot.dto.match.TeamDto;
import com.mmrtr.lol.infra.riot.dto.match_timeline.TimelineDto;
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

    private final MatchRepositoryImpl matchRepository;
    private final MatchSummonerRepositoryImpl matchSummonerRepository;
    private final ChallengesRepositoryImpl challengesRepository;
    private final MatchTeamRepositoryImpl matchTeamRepository;

    @Transactional
    public void addAllMatch(List<MatchDto> matchDtos, List<TimelineDto> timelineDtos) {

        for (MatchDto matchDto : matchDtos) {
            MatchEntity match = matchRepository.save(new MatchEntity(matchDto, 26));

            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

            List<MatchSummonerEntity> matchSummoners = new ArrayList<>();
            List<ChallengesEntity> challenges = new ArrayList<>();
            for (ParticipantDto participant : participants) {
                MatchSummonerEntity matchSummoner = MatchSummonerEntity.of(match, participant);
                ChallengesDto challengesDto = participant.getChallenges();

                if (challengesDto == null) {
                    continue;
                }

                matchSummoners.add(matchSummoner);
                challenges.add(ChallengesEntity.of(
                        matchSummoner, challengesDto
                ));
            }

            matchSummonerRepository.bulkSave(matchSummoners);
            challengesRepository.bulkSave(challenges);

            List<TeamDto> teams = matchDto.getInfo().getTeams();
            List<MatchTeamEntity> matchTeams = new ArrayList<>();
            for (TeamDto team : teams) {
                MatchTeamEntity matchTeam = MatchTeamEntity.of(match, team);
                matchTeams.add(matchTeam);
            }

            matchTeamRepository.bulkSave(matchTeams);
        }

        for (TimelineDto timelineDto : timelineDtos) {

        }

    }

    public List<MatchEntity> findAllMatch(List<String> matchIds) {
        return  matchRepository.findAllByIds(matchIds);
    }
}
