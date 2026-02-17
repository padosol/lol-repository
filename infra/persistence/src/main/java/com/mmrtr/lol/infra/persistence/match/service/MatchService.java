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
    private final TimeLineService timeLineService;

    @Transactional
    public void addAllMatch(List<MatchDto> matchDtos, List<TimelineDto> timelineDtos) {

        List<MatchEntity> matchEntities = matchDtos.stream()
                .map(dto -> new MatchEntity(dto, 26))
                .toList();
        matchRepository.bulkSave(matchEntities);

        List<MatchSummonerEntity> allMatchSummoners = new ArrayList<>();
        List<ChallengesEntity> allChallenges = new ArrayList<>();
        List<MatchTeamEntity> allMatchTeams = new ArrayList<>();

        for (int i = 0; i < matchDtos.size(); i++) {
            MatchDto matchDto = matchDtos.get(i);
            MatchEntity match = matchEntities.get(i);

            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();

            for (ParticipantDto participant : participants) {
                MatchSummonerEntity matchSummoner = MatchSummonerEntity.of(match, participant);
                ChallengesDto challengesDto = participant.getChallenges();

                if (challengesDto == null) {
                    continue;
                }

                allMatchSummoners.add(matchSummoner);
                allChallenges.add(ChallengesEntity.of(matchSummoner, challengesDto));
            }

            List<TeamDto> teams = matchDto.getInfo().getTeams();
            for (TeamDto team : teams) {
                allMatchTeams.add(MatchTeamEntity.of(match, team));
            }
        }

        matchSummonerRepository.bulkSave(allMatchSummoners);
        challengesRepository.bulkSave(allChallenges);
        matchTeamRepository.bulkSave(allMatchTeams);

        if (timelineDtos != null && !timelineDtos.isEmpty()) {
            timeLineService.saveAll(matchEntities, timelineDtos);
        }
    }

    public List<MatchEntity> findAllMatch(List<String> matchIds) {
        return matchRepository.findAllByIds(matchIds);
    }
}
