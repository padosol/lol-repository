package com.mmrtr.lol.domain.champion_stat.service.usecase;

public interface TriggerChampionStatAggregationUseCase {

    void execute(int season, int queueId);
}
