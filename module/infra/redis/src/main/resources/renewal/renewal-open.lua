-- 갱신 레코드를 새로 열고(이전 것은 지운다) active 에 올린다.
-- KEYS: [1] 레코드 hash, [2] active zset
-- ARGV: [1] renewalId, [2] platform, [3] puuid, [4] 기한 ms, [5] 레코드 TTL(초)
-- 반환: 접수 시각(epoch ms, Redis TIME)
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local deadline = now + tonumber(ARGV[4])
local nowStr = string.format('%.0f', now)
local deadlineStr = string.format('%.0f', deadline)

redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1],
    'renewalId', ARGV[1],
    'status', 'PROGRESS',
    'steps', '0',
    'acceptedAt', nowStr,
    'deadlineAt', deadlineStr,
    'platform', ARGV[2])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
redis.call('ZADD', KEYS[2], deadline, ARGV[3])
return now
