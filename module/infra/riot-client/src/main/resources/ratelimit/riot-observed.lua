-- 지표용: Riot 헤더 기록 해시마다 limit, count, at 을 읽는다. 아무것도 쓰지 않는다. RiotRateLimitMetrics 가 부른다.
-- KEYS: 버킷 해시 (riot:rl:observed:{bucket})
-- ARGV: [1] nowMillis(빈 문자열이면 Redis TIME)
-- 반환: [1] now, 그다음 KEYS 마다 limit, count, at (키나 필드가 없으면 빈 문자열). 모두 문자열이다.
local now
if ARGV[1] ~= '' then
  now = tonumber(ARGV[1])
else
  local t = redis.call('TIME')
  now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
end
local out = { string.format('%d', now) }
for i = 1, #KEYS do
  local v = redis.call('HMGET', KEYS[i], 'limit', 'count', 'at')
  out[#out + 1] = v[1] or ''
  out[#out + 1] = v[2] or ''
  out[#out + 1] = v[3] or ''
end
return out
