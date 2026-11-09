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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 매치 저장. match 행 = 완료 표시: 갱신 완료 판정이 match 행 존재로 "저장 끝"을 판단하므로
 * match 행은 참가자·챌린지·팀·밴·타임라인 저장이 모두 끝난 뒤 마지막에 쓴다.
 */
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
    private final TimelineEventJsonService timelineEventJsonService;
    private final Executor timelineSaveExecutor;

    /**
     * 바깥 트랜잭션을 두지 않는다 — 병렬 저장을 기다리는 동안 커넥션을 쥐지 않기 위해서다.
     * 각 bulkSave 는 자기 커넥션으로 커밋된다.
     */
    @Override
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

        CompletableFuture<?>[] futures = {
            runBulkSave("matchParticipant", allMatchParticipants, matchSummonerRepository::bulkSave),
            runBulkSave("challenges", allChallenges, challengesRepository::bulkSave),
            runBulkSave("matchTeam", allMatchTeams, matchTeamRepository::bulkSave),
            runBulkSave("matchBan", allMatchBans, matchBanRepository::bulkSave),
        };
        CompletableFuture.allOf(futures).join();

        if (timelineDtos != null && !timelineDtos.isEmpty()) {
            t = System.currentTimeMillis();
            CompletableFuture<Void> participantFrameSave = CompletableFuture.runAsync(() -> {
                long inner = System.currentTimeMillis();
                timeLineService.saveAll(timelineDtos);
                log.debug("[saveAll] saveAll timelines: {}ms", System.currentTimeMillis() - inner);
            }, timelineSaveExecutor);

            CompletableFuture<Void> eventJsonSave = CompletableFuture.runAsync(() -> {
                long inner = System.currentTimeMillis();
                timelineEventJsonService.saveAll(timelineDtos);
                log.debug("[saveAll] saveAll timelines (json): {}ms", System.currentTimeMillis() - inner);
            }, timelineSaveExecutor);

            CompletableFuture.allOf(participantFrameSave, eventJsonSave).join();
            log.debug("[saveAll] saveAll timelines (parallel total): {}ms", System.currentTimeMillis() - t);
        }

        // match 행은 마지막에 쓴다 — 행이 있으면 자식 테이블·같은 호출의 타임라인까지 저장이 끝난 것이다.
        // 타임라인만 저장하는 호출(매치는 이미 DB 에 있음)이면 쓸 매치 행이 없다.
        if (!matchEntities.isEmpty()) {
            t = System.currentTimeMillis();
            matchRepository.bulkSave(matchEntities);
            log.debug("[saveAll] bulkSave match: {}ms ({}건)", System.currentTimeMillis() - t, matchEntities.size());
        }

        log.info("[saveAll] 총 소요: {}ms", System.currentTimeMillis() - start);
    }

    private <T> CompletableFuture<Void> runBulkSave(String name, List<T> entities,
                                                      Consumer<List<T>> saveFunction) {
        if (entities.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            long t = System.currentTimeMillis();
            saveFunction.accept(entities);
            log.debug("[saveAll] bulkSave {}: {}ms ({}건)", name,
                    System.currentTimeMillis() - t, entities.size());
        }, timelineSaveExecutor);
    }

    @Override
    public Set<String> findMatchIdsWithTimeline(List<String> matchIds) {
        return timeLineService.findMatchIdsWithTimeline(matchIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findExistingMatchIds(List<String> matchIds) {
        return matchRepository.findAllByIds(matchIds).stream()
                .map(MatchEntity::getMatchId)
                .toList();
    }
}
