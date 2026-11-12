package com.mmrtr.lol.infra.riot.ratelimit;

import java.time.Duration;

/**
 * 하나의 rate limit 버킷 (예: app 500/10s, method 2000/10s).
 * key 는 Redis 키. 같은 요청의 버킷들은 hash tag({...})를 공유해 Cluster 에서도 Lua 다중 키가 가능하도록 한다.
 */
record RateBucket(String key, long limit, Duration window) {
}
