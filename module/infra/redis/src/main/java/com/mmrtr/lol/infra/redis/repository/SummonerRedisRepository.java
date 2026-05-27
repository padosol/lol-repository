package com.mmrtr.lol.infra.redis.repository;

import com.mmrtr.lol.infra.redis.model.SummonerRenewalSession;
import org.springframework.data.repository.CrudRepository;

public interface SummonerRedisRepository extends CrudRepository<SummonerRenewalSession, String> {
}
