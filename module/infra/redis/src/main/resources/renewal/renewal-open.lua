-- KEYS[1]=record hash, KEYS[2]=targets set, KEYS[3]=active zset
-- ARGV[1]=renewalId, ARGV[2]=platform, ARGV[3]=puuid, ARGV[4]=deadline ms, ARGV[5]=ttl seconds
-- 반환: 접수 시각(epoch ms, Redis TIME)
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local deadline = now + tonumber(ARGV[4])
local nowStr = string.format('%.0f', now)
local deadlineStr = string.format('%.0f', deadline)

redis.call('DEL', KEYS[1], KEYS[2])
redis.call('HSET', KEYS[1],
    'renewalId', ARGV[1],
    'status', 'PROGRESS',
    'steps', '0',
    'targetsReady', '0',
    'acceptedAt', nowStr,
    'deadlineAt', deadlineStr,
    'platform', ARGV[2])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
redis.call('ZADD', KEYS[3], deadline, ARGV[3])
return now
