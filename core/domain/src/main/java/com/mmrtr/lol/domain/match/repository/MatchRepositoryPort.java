package com.mmrtr.lol.domain.match.repository;

import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;

import java.util.List;
import java.util.Map;

public interface MatchRepositoryPort {

    void saveAll(List<MatchDto> matches, List<TimelineDto> timelines,
                 Map<String, Map<String, LeagueSummoner>> leagueMap);

    List<String> findExistingMatchIds(List<String> matchIds);
}
