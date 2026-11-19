package com.mmrtr.lol.domain.match.application.usecase;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.league.application.port.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.match.domain.MatchTierSnapshot;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaveMatchDataUseCase {

    private final MatchRepositoryPort matchRepositoryPort;
    private final LeagueSummonerRepositoryPort leagueSummonerRepositoryPort;

    public void execute(List<MatchDto> matches, List<TimelineDto> timelines) {
        Map<String, Map<String, LeagueSummoner>> leagueMap = buildLeagueMap(matches);
        Map<String, MatchTierSnapshot> tierByMatchId = new HashMap<>();
        for (MatchDto matchDto : matches) {
            tierByMatchId.put(matchDto.getMetadata().getMatchId(), snapshotOf(matchDto, leagueMap));
        }
        matchRepositoryPort.saveAll(matches, timelines, tierByMatchId);
    }

    private MatchTierSnapshot snapshotOf(MatchDto matchDto,
                                         Map<String, Map<String, LeagueSummoner>> leagueMap) {
        Queue queue = Queue.fromQueueId(matchDto.getInfo().getQueueId());
        if (queue == null) {
            return MatchTierSnapshot.empty();
        }
        List<String> puuids = matchDto.getInfo().getParticipants().stream()
                .map(ParticipantDto::getPuuid)
                .toList();
        return MatchTierSnapshot.of(puuids, leagueMap.getOrDefault(queue.name(), Map.of()));
    }

    private Map<String, Map<String, LeagueSummoner>> buildLeagueMap(List<MatchDto> matches) {
        // queueName -> Set<puuid> 수집
        Map<String, Set<String>> puuidsByQueue = new HashMap<>();

        for (MatchDto matchDto : matches) {
            Queue queue = Queue.fromQueueId(matchDto.getInfo().getQueueId());
            if (queue == null) {
                continue;
            }

            Set<String> puuids = puuidsByQueue.computeIfAbsent(queue.name(), k -> new HashSet<>());
            for (ParticipantDto participant : matchDto.getInfo().getParticipants()) {
                puuids.add(participant.getPuuid());
            }
        }

        // queueName -> (puuid -> LeagueSummoner) 맵 구성
        Map<String, Map<String, LeagueSummoner>> result = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : puuidsByQueue.entrySet()) {
            String queueName = entry.getKey();
            Set<String> puuids = entry.getValue();

            List<LeagueSummoner> leagueSummoners =
                    leagueSummonerRepositoryPort.findAllByPuuidsAndQueue(puuids, queueName);

            Map<String, LeagueSummoner> byPuuid = leagueSummoners.stream()
                    .collect(Collectors.toMap(LeagueSummoner::getPuuid, Function.identity()));

            result.put(queueName, byPuuid);
        }

        return result;
    }
}
