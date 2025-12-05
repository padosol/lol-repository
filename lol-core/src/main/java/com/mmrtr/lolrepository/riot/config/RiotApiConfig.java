package com.mmrtr.lolrepository.riot.config;

import com.mmrtr.lolrepository.bucket.BucketService;
import com.mmrtr.lolrepository.riot.core.api.RiotAPI;
import com.mmrtr.lolrepository.riot.core.calling.DefaultRiotExecute;
import com.mmrtr.lolrepository.riot.core.calling.RiotExecuteProxy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;


@EnableAsync
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RiotAPIProperties.class)
public class RiotApiConfig {

    private final RiotAPIProperties properties;

    @Bean
    RiotAPI riotAPI(BucketService bucketService, RedisTemplate<String, Object> redisTemplate) {
        return RiotAPI.builder()
                .apiKey(properties.getKey())
                .redisTemplate(redisTemplate)
                .execute(new RiotExecuteProxy(new DefaultRiotExecute(properties.getKey()), bucketService))
                .bucket(bucketService)
                .build();
    }

}
