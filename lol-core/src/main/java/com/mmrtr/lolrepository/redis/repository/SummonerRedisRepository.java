package com.mmrtr.lolrepository.redis.repository;


import com.mmrtr.lolrepository.redis.model.SummonerRenewalSession;
import org.springframework.data.repository.CrudRepository;

public interface SummonerRedisRepository extends CrudRepository<SummonerRenewalSession, String> {
}
