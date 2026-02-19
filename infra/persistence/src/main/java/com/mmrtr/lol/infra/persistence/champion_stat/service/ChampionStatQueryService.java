package com.mmrtr.lol.infra.persistence.champion_stat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.champion_stat.domain.*;
import com.mmrtr.lol.domain.champion_stat.repository.ChampionStatRepositoryPort;
import com.mmrtr.lol.domain.champion_stat.service.usecase.ChampionStatQueryUseCase;
import com.mmrtr.lol.infra.redis.service.ChampionStatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionStatQueryService implements ChampionStatQueryUseCase {

    private final ChampionStatRepositoryPort championStatRepositoryPort;
    private final ChampionStatCacheService championStatCacheService;
    private final ObjectMapper objectMapper;

    @Override
    public List<ChampionStatSummary> getSummaries(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch) {
        String cacheKey = championStatCacheService.buildCacheKey("summary", championId, position, season, tierGroup, platformId, queueId, patch);
        return getWithCache(cacheKey,
                new TypeReference<>() {},
                () -> championStatRepositoryPort.findSummaries(championId, position, season, tierGroup, platformId, queueId, patch));
    }

    @Override
    public List<ChampionRuneStat> getRuneStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch) {
        String cacheKey = championStatCacheService.buildCacheKey("rune", championId, position, season, tierGroup, platformId, queueId, patch);
        return getWithCache(cacheKey,
                new TypeReference<>() {},
                () -> championStatRepositoryPort.findRuneStats(championId, position, season, tierGroup, platformId, queueId, patch));
    }

    @Override
    public List<ChampionSpellStat> getSpellStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch) {
        String cacheKey = championStatCacheService.buildCacheKey("spell", championId, position, season, tierGroup, platformId, queueId, patch);
        return getWithCache(cacheKey,
                new TypeReference<>() {},
                () -> championStatRepositoryPort.findSpellStats(championId, position, season, tierGroup, platformId, queueId, patch));
    }

    @Override
    public List<ChampionSkillStat> getSkillStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch) {
        String cacheKey = championStatCacheService.buildCacheKey("skill", championId, position, season, tierGroup, platformId, queueId, patch);
        return getWithCache(cacheKey,
                new TypeReference<>() {},
                () -> championStatRepositoryPort.findSkillStats(championId, position, season, tierGroup, platformId, queueId, patch));
    }

    @Override
    public List<ChampionItemStat> getItemStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch) {
        String cacheKey = championStatCacheService.buildCacheKey("item", championId, position, season, tierGroup, platformId, queueId, patch);
        return getWithCache(cacheKey,
                new TypeReference<>() {},
                () -> championStatRepositoryPort.findItemStats(championId, position, season, tierGroup, platformId, queueId, patch));
    }

    @Override
    public List<ChampionMatchupStat> getMatchupStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch) {
        String cacheKey = championStatCacheService.buildCacheKey("matchup", championId, position, season, tierGroup, platformId, queueId, patch);
        return getWithCache(cacheKey,
                new TypeReference<>() {},
                () -> championStatRepositoryPort.findMatchupStats(championId, position, season, tierGroup, platformId, queueId, patch));
    }

    private <T> List<T> getWithCache(String cacheKey, TypeReference<List<T>> typeRef, Supplier<List<T>> dbFallback) {
        Optional<String> cached = championStatCacheService.get(cacheKey);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), typeRef);
            } catch (JsonProcessingException e) {
                log.warn("캐시 역직렬화 실패, DB 조회로 대체 - key: {}", cacheKey, e);
            }
        }

        List<T> result = dbFallback.get();

        try {
            championStatCacheService.put(cacheKey, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            log.warn("캐시 직렬화 실패 - key: {}", cacheKey, e);
        }

        return result;
    }
}
