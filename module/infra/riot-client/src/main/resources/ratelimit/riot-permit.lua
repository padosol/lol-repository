-- Riot 호출 n 건의 허가를 한 routing 안에서 원자적으로 판단한다. 모두 통과할 때만 토큰을 기록한다.
-- KEYS: [1] 유저 활동 키, [2 .. 1+P] 정지 키(app, 각 method), [2+P ..] 규칙 ZSET
-- ARGV: [1] batchId, [2] nowMillis(빈 문자열이면 Redis TIME), [3] 우선순위 level(1 USER), [4] 낮춤 비율, [5] 유저 활동 ms,
--       [6] 정지 키 수 P, [7] method 수 M, [8 .. 7+2M] (methodName, 건수) 쌍,
--       그 뒤 규칙마다 4개: 상한, 창 ms, 낮춤 여부(0/1), 대상 methodName(빈 문자열이면 모든 method = app·백필 규칙)
-- 반환: 1 = 허가(토큰 {batchId}:{methodName}:{k} 를 기록함), 0 = 거절(기록 없음)
-- 규칙(버킷 × 창)마다 ZSET 하나이고 member = 토큰, score = 허가 시각(ms)이다.
-- TIME 뒤에 쓰기를 하므로 효과 복제를 켠다 (Redis 5 이상에서는 기본값이라 no-op)
redis.replicate_commands()
local now
if ARGV[2] ~= '' then
  now = tonumber(ARGV[2])
else
  local t = redis.call('TIME')
  now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
end
local batchId = ARGV[1]
local level = tonumber(ARGV[3])
local ratio = tonumber(ARGV[4])
local userActiveMs = tonumber(ARGV[5])
local pauseCount = tonumber(ARGV[6])
local methodCount = tonumber(ARGV[7])
local firstRule = 2 + pauseCount
local ruleArgBase = 8 + 2 * methodCount

local function fmt(n)
  return string.format('%d', n)
end

-- USER 요청은 허가 여부와 관계없이 유저 활동 표시를 켠다. 값은 활동이 끝나는 시각(ms)이고 만료는 정리용이다.
if level == 1 and userActiveMs > 0 then
  redis.call('SET', KEYS[1], fmt(now + userActiveMs), 'PX', fmt(userActiveMs))
end

-- 정지 키 값은 정지가 끝나는 시각(ms)이다 (riot-pause.lua). 하나라도 정지 중이면 기록하지 않는다.
for i = 2, 1 + pauseCount do
  local untilMs = redis.call('GET', KEYS[i])
  if untilMs and tonumber(untilMs) > now then
    return 0
  end
end

-- method 별 건수
local counts = {}
local total = 0
for j = 1, methodCount do
  local n = tonumber(ARGV[8 + (j - 1) * 2 + 1])
  counts[ARGV[8 + (j - 1) * 2]] = n
  total = total + n
end

-- 유저 활동 중이면 DEFERRED·BACKGROUND 는 낮춤 규칙의 상한을 ratio 배로 낮춘다
local factor = 1
local userUntil = tonumber(redis.call('GET', KEYS[1]) or '0')
if level > 1 and userUntil > now then
  factor = ratio
end

-- 1단계: 창을 빠진 항목(score <= now - 창)을 지우고, 필요한 칸이 상한 안에 들어가는지 검사만 한다.
local ruleCount = #KEYS - firstRule + 1
for r = 0, ruleCount - 1 do
  local key = KEYS[firstRule + r]
  local base = ruleArgBase + r * 4
  local cap = tonumber(ARGV[base])
  local window = tonumber(ARGV[base + 1])
  local target = ARGV[base + 3]
  local need = total
  if target ~= '' then
    need = counts[target] or 0
  end
  redis.call('ZREMRANGEBYSCORE', key, '-inf', fmt(now - window))
  local eff = cap
  if ARGV[base + 2] == '1' then
    eff = math.max(1, math.floor(cap * factor))
  end
  if redis.call('ZCARD', key) + need > eff then
    return 0
  end
end

-- 2단계: 모든 규칙에 토큰을 기록하고 키 만료를 창 길이로 늘린다
for r = 0, ruleCount - 1 do
  local key = KEYS[firstRule + r]
  local base = ruleArgBase + r * 4
  local target = ARGV[base + 3]
  for j = 1, methodCount do
    local name = ARGV[8 + (j - 1) * 2]
    if target == '' or target == name then
      for k = 1, counts[name] do
        redis.call('ZADD', key, fmt(now), batchId .. ':' .. name .. ':' .. k)
      end
    end
  end
  redis.call('PEXPIRE', key, ARGV[base + 1])
end
return 1
