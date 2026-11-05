-- 허가된 호출의 기록을 지금 시각(응답·실패 시각)으로 옮긴다. RedisRiotRateLimiter#complete 가 부른다.
-- 응답 시각은 Riot 도착 시각보다 늘 뒤라, 기록이 창을 빠지는 시각이 "도착 + 창" 이후가 된다.
-- 그 eventId 가 없는 규칙(이미 창을 빠졌거나 허가 뒤 새로 배운 창)은 건너뛴다.
-- ZADD GT 는 Redis 6.2 이상이라 쓰지 않고 ZSCORE 로 있는지 확인한다.
-- KEYS: 규칙 ZSET
-- ARGV: [1] eventId, [2] nowMillis(빈 문자열이면 Redis TIME), [3..] 규칙마다 창 ms (KEYS 와 같은 순서)
-- TIME 뒤에 쓰기를 하므로 효과 복제를 켠다 (Redis 5 이상에서는 기본값이라 no-op)
redis.replicate_commands()
local now
if ARGV[2] ~= '' then
  now = tonumber(ARGV[2])
else
  local t = redis.call('TIME')
  now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
end
local eventId = ARGV[1]
for i = 1, #KEYS do
  if redis.call('ZSCORE', KEYS[i], eventId) then
    redis.call('ZADD', KEYS[i], 'XX', string.format('%d', now), eventId)
    redis.call('PEXPIRE', KEYS[i], ARGV[2 + i])
  end
end
return 0
