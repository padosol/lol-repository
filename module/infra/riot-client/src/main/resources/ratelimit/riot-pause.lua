-- 버킷 정지. 값은 정지가 끝나는 시각(ms)이라 riot-acquire.lua 가 같은 시계로 남은 시간을 계산한다.
-- 이미 더 늦게 끝나는 정지가 있으면 줄이지 않는다.
-- KEYS[1]: riot:rl:pause:{bucket}, ARGV: durationMs, nowMillis(빈 문자열이면 Redis TIME)
local now
if ARGV[2] ~= '' then
  now = tonumber(ARGV[2])
else
  local t = redis.call('TIME')
  now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
end
local duration = tonumber(ARGV[1])
local untilMs = now + duration
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
if untilMs > current then
  redis.call('SET', KEYS[1], string.format('%d', untilMs), 'PX', string.format('%d', duration))
end
return 0
