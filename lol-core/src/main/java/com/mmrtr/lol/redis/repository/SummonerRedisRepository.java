package com.mmrtr.lol.redis.repository;


import com.mmrtr.lol.redis.model.SummonerRenewalSession;
import org.springframework.data.repository.CrudRepository;

public interface SummonerRedisRepository extends CrudRepository<SummonerRenewalSession, String> {
}
