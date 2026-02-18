package com.mmrtr.lol.infra.persistence.match.service;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.infra.persistence.league.entity.LeagueSummonerEntity;
import com.mmrtr.lol.infra.persistence.league.repository.LeagueSummonerJpaRepository;
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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepositoryImpl matchRepository;
    private final MatchSummonerRepositoryImpl matchSummonerRepository;
    private final ChallengesRepositoryImpl challengesRepository;
    private final MatchTeamRepositoryImpl matchTeamRepository;
    private final TimeLineService timeLineService;
    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;

    @Transactional
    public void addAllMatch(List<MatchDto> matchDtos, List<TimelineDto> timelineDtos) {

        List<MatchEntity> matchEntities = matchDtos.stream()
                .map(dto -> new MatchEntity(dto, 26))
                .toList();

        // queueId별로 참가자 puuid를 수집하고 league 데이터를 배치 조회
        Map<String, Map<String, LeagueSummonerEntity>> leagueByQueueAndPuuid = buildLeagueMap(matchDtos);

        List<MatchSummonerEntity> allMatchSummoners = new ArrayList<>();
        List<ChallengesEntity> allChallenges = new ArrayList<>();
        List<MatchTeamEntity> allMatchTeams = new ArrayList<>();

        for (int i = 0; i < matchDtos.size(); i++) {
            MatchDto matchDto = matchDtos.get(i);
            MatchEntity match = matchEntities.get(i);

            Queue queue = Queue.fromQueueId(matchDto.getInfo().getQueueId());
            Map<String, LeagueSummonerEntity> leagueMap = (queue != null)
                    ? leagueByQueueAndPuuid.getOrDefault(queue.name(), Collections.emptyMap())
                    : Collections.emptyMap();

            List<ParticipantDto> participants = matchDto.getInfo().getParticipants();
            List<Integer> absolutePointsList = new ArrayList<>();

            for (ParticipantDto participant : participants) {
                LeagueSummonerEntity league = leagueMap.get(participant.getPuuid());

                String tier = null;
                String tierRank = null;
                Integer absolutePoints = null;

                if (league != null) {
                    tier = league.getTier();
                    tierRank = league.getRank();
                    absolutePoints = league.getAbsolutePoints();
                    absolutePointsList.add(absolutePoints);
                }

                MatchSummonerEntity matchSummoner = MatchSummonerEntity.of(match, participant, tier, tierRank, absolutePoints);
                ChallengesDto challengesDto = participant.getChallenges();

                if (challengesDto == null) {
                    continue;
                }

                allMatchSummoners.add(matchSummoner);
                allChallenges.add(ChallengesEntity.of(matchSummoner, challengesDto));
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
            }
        }

        matchRepository.bulkSave(matchEntities);
        matchSummonerRepository.bulkSave(allMatchSummoners);
        challengesRepository.bulkSave(allChallenges);
        matchTeamRepository.bulkSave(allMatchTeams);

        if (timelineDtos != null && !timelineDtos.isEmpty()) {
            timeLineService.saveAll(matchEntities, timelineDtos);
        }
    }

    private Map<String, Map<String, LeagueSummonerEntity>> buildLeagueMap(List<MatchDto> matchDtos) {
        // queueName -> Set<puuid> 수집
        Map<String, Set<String>> puuidsByQueue = new HashMap<>();

        for (MatchDto matchDto : matchDtos) {
            Queue queue = Queue.fromQueueId(matchDto.getInfo().getQueueId());
            if (queue == null) {
                continue;
            }

            Set<String> puuids = puuidsByQueue.computeIfAbsent(queue.name(), k -> new HashSet<>());
            for (ParticipantDto participant : matchDto.getInfo().getParticipants()) {
                puuids.add(participant.getPuuid());
            }
        }

        // queueName -> (puuid -> LeagueSummonerEntity) 맵 구성
        Map<String, Map<String, LeagueSummonerEntity>> result = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : puuidsByQueue.entrySet()) {
            String queueName = entry.getKey();
            Set<String> puuids = entry.getValue();

            List<LeagueSummonerEntity> leagueEntities =
                    leagueSummonerJpaRepository.findAllByPuuidInAndQueue(puuids, queueName);

            Map<String, LeagueSummonerEntity> byPuuid = leagueEntities.stream()
                    .collect(Collectors.toMap(LeagueSummonerEntity::getPuuid, Function.identity()));

            result.put(queueName, byPuuid);
        }

        return result;
    }

    public List<MatchEntity> findAllMatch(List<String> matchIds) {
        return matchRepository.findAllByIds(matchIds);
    }
}
