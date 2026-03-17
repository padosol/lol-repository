package com.mmrtr.lol.infra.persistence.match.service;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.match.readmodel.BanDto;
import com.mmrtr.lol.domain.match.readmodel.ChallengesDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.TeamDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.infra.persistence.match.entity.MatchBanEntity;
import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.entity.MatchParticipantChallengesEntity;
import com.mmrtr.lol.infra.persistence.match.entity.MatchParticipantEntity;
import com.mmrtr.lol.infra.persistence.match.entity.MatchTeamEntity;
import com.mmrtr.lol.infra.persistence.match.repository.ChallengesRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchBanRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchTeamRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService implements MatchRepositoryPort {

    private final MatchRepositoryImpl matchRepository;
    private final MatchSummonerRepositoryImpl matchSummonerRepository;
    private final ChallengesRepositoryImpl challengesRepository;
    private final MatchTeamRepositoryImpl matchTeamRepository;
    private final MatchBanRepositoryImpl matchBanRepository;
    private final TimeLineService timeLineService;

    @Override
    @Transactional
    public void saveAll(List<MatchDto> matchDtos, List<TimelineDto> timelineDtos,
                        Map<String, Map<String, LeagueSummoner>> leagueByQueueAndPuuid) {
        long start = System.currentTimeMillis();

        // 엔티티 매핑
        long t = System.currentTimeMillis();
        List<MatchEntity> matchEntities = matchDtos.stream()
                .map(MatchEntity::new)
                .toList();
        log.debug("[saveAll] 엔티티 매핑: {}ms", System.currentTimeMillis() - t);

        List<MatchParticipantEntity> allMatchParticipants = new ArrayList<>();
        List<MatchParticipantChallengesEntity> allChallenges = new ArrayList<>();
        List<MatchTeamEntity> allMatchTeams = new ArrayList<>();
        List<MatchBanEntity> allMatchBans = new ArrayList<>();

        for (int i = 0; i < matchDtos.size(); i++) {
            MatchDto matchDto = matchDtos.get(i);
            MatchEntity match = matchEntities.get(i);

            Queue queue = Queue.fromQueueId(matchDto.getInfo().getQueueId());
            Map<String, LeagueSummoner> leagueMap = (queue != null)
                    ? leagueByQueueAndPuuid.getOrDefault(queue.name(), Collections.emptyMap())
                    : Collections.emptyMap();

            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();
            List<Integer> absolutePointsList = new ArrayList<>();

            for (ParticipantDto participant : participants) {
                LeagueSummoner league = leagueMap.get(participant.getPuuid());

                String tier = null;
                String tierRank = null;
                Integer absolutePoints = null;

                if (league != null) {
                    tier = league.getTier();
                    tierRank = league.getRank();
                    absolutePoints = league.getAbsolutePoints();
                    absolutePointsList.add(absolutePoints);
                }

                MatchParticipantEntity matchParticipant = MatchParticipantEntity.of(match, participant, tier, tierRank, absolutePoints);
                ChallengesDto challengesDto = participant.getChallenges();

                if (challengesDto == null) {
                    continue;
                }

                allMatchParticipants.add(matchParticipant);
                allChallenges.add(MatchParticipantChallengesEntity.of(matchParticipant, challengesDto));
            }

            // 평균 티어 계산
            if (!absolutePointsList.isEmpty()) {
                int avgPoints = (int) absolutePointsList.stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0);
                match.setAverageTier(avgPoints);
            }

            List<TeamDto> teams = matchDto.getInfo().getTeams();
            for (TeamDto team : teams) {
                allMatchTeams.add(MatchTeamEntity.of(match, team));
                if (team.getBans() != null) {
                    for (BanDto ban : team.getBans()) {
                        allMatchBans.add(MatchBanEntity.of(match.getMatchId(), team.getTeamId(), ban));
                    }
                }
            }
        }

        t = System.currentTimeMillis();
        matchRepository.bulkSave(matchEntities);
        log.debug("[saveAll] bulkSave match: {}ms ({}건)", System.currentTimeMillis() - t, matchEntities.size());

        t = System.currentTimeMillis();
        matchSummonerRepository.bulkSave(allMatchParticipants);
        log.debug("[saveAll] bulkSave matchParticipant: {}ms ({}건)", System.currentTimeMillis() - t, allMatchParticipants.size());

        t = System.currentTimeMillis();
        challengesRepository.bulkSave(allChallenges);
        log.debug("[saveAll] bulkSave challenges: {}ms ({}건)", System.currentTimeMillis() - t, allChallenges.size());

        t = System.currentTimeMillis();
        matchTeamRepository.bulkSave(allMatchTeams);
        log.debug("[saveAll] bulkSave matchTeam: {}ms ({}건)", System.currentTimeMillis() - t, allMatchTeams.size());

        t = System.currentTimeMillis();
        matchBanRepository.bulkSave(allMatchBans);
        log.debug("[saveAll] bulkSave matchBan: {}ms ({}건)", System.currentTimeMillis() - t, allMatchBans.size());

        if (timelineDtos != null && !timelineDtos.isEmpty()) {
            t = System.currentTimeMillis();
            timeLineService.saveAll(matchEntities, timelineDtos);
            log.debug("[saveAll] saveAll timelines: {}ms", System.currentTimeMillis() - t);
        }

        log.debug("[saveAll] 총 소요: {}ms", System.currentTimeMillis() - start);
    }

    @Override
    public List<String> findExistingMatchIds(List<String> matchIds) {
        return matchRepository.findAllByIds(matchIds).stream()
                .map(MatchEntity::getMatchId)
                .toList();
    }
}
