package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MatchRepositoryPort {

    void saveAll(List<MatchDto> matches, List<TimelineDto> timelines,
                 Map<String, Map<String, LeagueSummoner>> leagueMap);

    List<String> findExistingMatchIds(List<String> matchIds);

    /** 타임라인(participant_frame)이 이미 저장된 matchId. */
    Set<String> findMatchIdsWithTimeline(List<String> matchIds);
}
