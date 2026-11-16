package com.mmrtr.lol.infra.riot.ratelimit;

import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * ⑤ Lua 한 번에: 모든 버킷을 한 스크립트 안에서 검사하고, 전부 여유가 있을 때만 함께 INCR.
 * Redis 는 스크립트를 단일 스레드로 끝까지 실행하므로 검사~증가 사이에 다른 요청이 끼어들 수 없다.
 */
final class LuaAtomicLimiter implements MultiBucketRateLimiter {

    /**
     * KEYS[i] = 버킷 카운터 키, ARGV[2i-1] = 한도, ARGV[2i] = 윈도우(ms).
     * 반환: 0 = 허용, i = 거절한 버킷 번호(1부터). 거절이면 어떤 버킷도 건드리지 않는다.
     */
    static final String SCRIPT = """
            for i = 1, #KEYS do
                local used = tonumber(redis.call('GET', KEYS[i]) or '0')
                if used >= tonumber(ARGV[i * 2 - 1]) then
                    return i
                end
            end
            for i = 1, #KEYS do
                if redis.call('INCR', KEYS[i]) == 1 then
                    redis.call('PEXPIRE', KEYS[i], ARGV[i * 2])
                end
            end
            return 0
            """;

    private final RedissonClient redisson;
    private final RScript script;
    private final String sha;

    LuaAtomicLimiter(RedissonClient redisson) {
        this.redisson = redisson;
        this.script = redisson.getScript(StringCodec.INSTANCE);
        this.sha = script.scriptLoad(SCRIPT);
    }

    @Override
    public boolean tryAcquire(List<RateBucket> buckets) {
        List<Object> keys = new ArrayList<>(buckets.size());
        Object[] args = new Object[buckets.size() * 2];
        for (int i = 0; i < buckets.size(); i++) {
            RateBucket bucket = buckets.get(i);
            keys.add(bucket.key());
            args[i * 2] = String.valueOf(bucket.limit());
            args[i * 2 + 1] = String.valueOf(bucket.window().toMillis());
        }
        Long rejectedBucket = script.evalSha(RScript.Mode.READ_WRITE, sha, RScript.ReturnType.INTEGER, keys, args);
        return rejectedBucket == 0L;
    }

    @Override
    public long consumed(RateBucket bucket) {
        return RedisCounters.counter(redisson, bucket).get();
    }
}
