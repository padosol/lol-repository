package com.mmrtr.lol.domain.summoner.application.usecase;

import com.mmrtr.lol.domain.summoner.domain.LeagueSummoner;

import java.util.List;
import java.util.Set;

public interface LeagueSummonerQueryUseCase {

    List<LeagueSummoner> findAllByPuuidsAndQueue(Set<String> puuids, String queue);
}
