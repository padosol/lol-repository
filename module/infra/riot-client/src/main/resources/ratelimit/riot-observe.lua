-- Riot 이 응답 헤더로 알려 준 한도·집계 수를 버킷마다 해시 하나에 덮어쓴다. 비교용이고 허가에는 쓰지 않는다.
-- RedisRiotRateLimiter.observe 가 부른다.
-- KEYS: 버킷 해시 (riot:rl:observed:{bucket})
-- ARGV: [1] nowMillis(빈 문자열이면 Redis TIME), [2] 해시 TTL ms, [3 ..] KEYS 마다 limit, count (헤더 원문, 없으면 빈 문자열)
-- 반환: 쓴 키 수
local now
if ARGV[1] ~= '' then
  now = tonumber(ARGV[1])
else
  local t = redis.call('TIME')
  now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
end
local ttl = tonumber(ARGV[2])
for i = 1, #KEYS do
  redis.call('HSET', KEYS[i], 'limit', ARGV[1 + 2 * i], 'count', ARGV[2 + 2 * i], 'at', string.format('%d', now))
  redis.call('PEXPIRE', KEYS[i], ttl)
end
return #KEYS
