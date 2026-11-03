-- KEYS[1]=record hash, KEYS[2]=active zset
-- ARGV[1]=renewalId, ARGV[2]=step bits, ARGV[3]=puuid, ARGV[4]=done ttl, ARGV[5]=deadline-failed ttl
-- 반환 (RenewalTransition ordinal): 0 NONE, 1 SUCCEEDED, 3 FAILED_LATE_COMPLETED, 4 LATE_COMPLETED
if redis.call('HGET', KEYS[1], 'renewalId') ~= ARGV[1] then
    return 0
end

local steps = bit.bor(tonumber(redis.call('HGET', KEYS[1], 'steps') or '0'), tonumber(ARGV[2]))
redis.call('HSET', KEYS[1], 'steps', tostring(steps))
if steps ~= 15 then
    return 0
end

local status = redis.call('HGET', KEYS[1], 'status')
if status == 'PROGRESS' then
    local t = redis.call('TIME')
    local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
    local deadline = tonumber(redis.call('HGET', KEYS[1], 'deadlineAt'))
    if now <= deadline then
        redis.call('HSET', KEYS[1], 'status', 'SUCCESS')
        redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
        redis.call('ZREM', KEYS[2], ARGV[3])
        return 1
    end
    redis.call('HSET', KEYS[1], 'status', 'FAILED', 'reason', 'deadline', 'lateDone', '1')
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
    redis.call('ZREM', KEYS[2], ARGV[3])
    return 3
end

if status == 'FAILED' and redis.call('HGET', KEYS[1], 'reason') == 'deadline'
        and redis.call('HGET', KEYS[1], 'lateDone') ~= '1' then
    redis.call('HSET', KEYS[1], 'lateDone', '1')
    redis.call('ZREM', KEYS[2], ARGV[3])
    return 4
end
return 0
