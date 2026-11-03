-- KEYS[1]=record hash, KEYS[2]=active zset
-- ARGV[1]=renewalId, ARGV[2]=reason, ARGV[3]=puuid, ARGV[4]=mode ('fail'|'overdue'),
-- ARGV[5]=done ttl, ARGV[6]=deadline-failed ttl, ARGV[7]=deadline reason
-- 반환 (RenewalTransition ordinal): 0 NONE, 2 FAILED
if redis.call('HGET', KEYS[1], 'renewalId') ~= ARGV[1] then
    return 0
end
if redis.call('HGET', KEYS[1], 'status') ~= 'PROGRESS' then
    return 0
end

if ARGV[4] == 'overdue' then
    local t = redis.call('TIME')
    local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
    if now <= tonumber(redis.call('HGET', KEYS[1], 'deadlineAt')) then
        return 0
    end
end

redis.call('HSET', KEYS[1], 'status', 'FAILED', 'reason', ARGV[2])
if ARGV[2] == ARGV[7] then
    -- 늦은 완료를 기다리므로 active 에 남기고 TTL 을 길게 둔다
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[6]))
else
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
    redis.call('ZREM', KEYS[2], ARGV[3])
end
return 2
