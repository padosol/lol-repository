package com.mmrtr.lol.infra.riot.ratelimit;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

final class RedisTestSupport {

    private static final int REDIS_PORT = 6379;

    private RedisTestSupport() {
    }

    static GenericContainer<?> newContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(REDIS_PORT);
    }

    /** 운영 RedissonConfig 와 같은 single-server + 고정 풀 크기. 앱 인스턴스 하나를 흉내 낸다. */
    static RedissonClient connect(GenericContainer<?> redis, int poolSize) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(REDIS_PORT))
                .setConnectionPoolSize(poolSize)
                .setConnectionMinimumIdleSize(poolSize);
        return Redisson.create(config);
    }
}
