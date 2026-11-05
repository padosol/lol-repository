-- Riot 호출 한 건의 허가를 원자적으로 판단한다. RedisRiotRateLimiter#acquire 가 부른다.
-- 규칙(버킷 × 창)마다 ZSET 하나다. member = eventId, score = 허가 시각(ms, riot-complete.lua 뒤엔 응답 시각).
-- 1단계로 모든 규칙을 검사만 하고, 전부 통과했을 때만 2단계로 모든 규칙에 같은 eventId 를 기록한다.
-- KEYS: [1] app 정지 키, [2] method 정지 키, [3] 유저 활동 키, [4..] 규칙 ZSET
-- ARGV: [1] eventId, [2] nowMillis(빈 문자열이면 Redis TIME), [3] 우선순위 level(1 USER, 2 DEFERRED, 3 BACKGROUND),
--       [4] 유저 활동 중 낮춤 규칙에 곱할 비율, [5] 유저 활동 표시 ms,
--       [6..] 규칙마다 3개씩 (상한, 창 ms, 낮춤 여부 0/1). KEYS[4..] 와 같은 순서다.
-- 반환: 0 = 허가(기록함), 양수 = 그만큼 ms 뒤 다시 물어야 한다(기록 안 함)
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
local level = tonumber(ARGV[3])
local ratio = tonumber(ARGV[4])
local userActiveMs = tonumber(ARGV[5])
local FIRST_RULE = 4

local function fmt(n)
  return string.format('%d', n)
end

local function ruleArg(i, offset)
  return ARGV[6 + (i - FIRST_RULE) * 3 + offset]
end

-- USER 요청은 허가 여부와 관계없이 유저 활동 표시를 켠다. 값은 활동이 끝나는 시각(ms)이고 만료는 정리용이다.
if level == 1 and userActiveMs > 0 then
  redis.call('SET', KEYS[3], fmt(now + userActiveMs), 'PX', fmt(userActiveMs))
end

-- 정지 키 값은 정지가 끝나는 시각(ms)이다 (riot-pause.lua). 정지 중이면 기록하지 않고 남은 시간을 돌려준다.
local function pauseLeft(key)
  local untilMs = redis.call('GET', key)
  if untilMs then
    return math.max(0, tonumber(untilMs) - now)
  end
  return 0
end
local pause = math.max(pauseLeft(KEYS[1]), pauseLeft(KEYS[2]))
if pause > 0 then
  return pause
end

-- 유저 활동 중이면 DEFERRED·BACKGROUND 는 낮춤 규칙(app·method)의 상한을 ratio 배로 낮춘다
local factor = 1
if level > 1 and tonumber(redis.call('GET', KEYS[3]) or '0') > now then
  factor = ratio
end

-- 1단계: 창을 빠진 항목(score <= now - 창)을 지우고 센다. 넘으면 인덱스 n - 상한 항목이 창을 빠질 때까지 기다린다.
-- 상한이 개수보다 작을 수 있어(한도가 줄었거나 낮춘 상한) 가장 오래된 항목이 아니라 이 항목을 본다.
local maxWait = 0
for i = FIRST_RULE, #KEYS do
  local key = KEYS[i]
  local cap = tonumber(ruleArg(i, 0))
  local window = tonumber(ruleArg(i, 1))
  redis.call('ZREMRANGEBYSCORE', key, '-inf', fmt(now - window))
  local n = redis.call('ZCARD', key)
  local eff = cap
  if ruleArg(i, 2) == '1' then
    eff = math.max(1, math.floor(cap * factor))
  end
  if n >= eff then
    local entry = redis.call('ZRANGE', key, fmt(n - eff), fmt(n - eff), 'WITHSCORES')
    local s = tonumber(entry[2])
    maxWait = math.max(maxWait, math.max(s + window - now, 1))
  end
end
if maxWait > 0 then
  return maxWait
end

-- 2단계: 모든 규칙에 같은 eventId 를 기록하고 키 만료를 창 길이로 늘린다
for i = FIRST_RULE, #KEYS do
  redis.call('ZADD', KEYS[i], fmt(now), eventId)
  redis.call('PEXPIRE', KEYS[i], ruleArg(i, 1))
end
return 0
