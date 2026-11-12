-- 지표용: 규칙 ZSET 마다 창 안의 기록 수, 정지 키마다 남은 시간을 읽는다. 아무것도 쓰지 않는다. RiotRateLimitMetrics 가 부른다.
-- KEYS: [1 .. R] 규칙 ZSET, [R+1 ..] 정지 키
-- ARGV: [1] nowMillis(빈 문자열이면 Redis TIME), [2] 규칙 수 R, [3 .. 2+R] 규칙마다 창 ms (KEYS 와 같은 순서)
-- 반환: KEYS 순서대로 정수 목록. 규칙은 키가 없으면 -1, 있으면 score > now - 창 인 기록 수(riot-permit.lua 가 세는 것과 같다).
--       정지는 남은 ms, 키가 없으면 -2 (PTTL 그대로)
local now
if ARGV[1] ~= '' then
  now = tonumber(ARGV[1])
else
  local t = redis.call('TIME')
  now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
end
local ruleCount = tonumber(ARGV[2])
local out = {}
for i = 1, ruleCount do
  if redis.call('EXISTS', KEYS[i]) == 1 then
    local from = '(' .. string.format('%d', now - tonumber(ARGV[2 + i]))
    out[i] = redis.call('ZCOUNT', KEYS[i], from, '+inf')
  else
    out[i] = -1
  end
end
for i = ruleCount + 1, #KEYS do
  out[i] = redis.call('PTTL', KEYS[i])
end
return out
