-- Riot 호출 한 건의 한도 확인과 증가를 원자적으로 한다. RedisRiotRateLimiter#acquire 가 부른다.
-- ARGV: routing, method, level, appDefault, safetyRatio, lookaheadSeconds, reserveRatio, nowMillis(빈 문자열이면 Redis TIME)
-- 반환: 기다릴 ms(0 이상), -1 = USER 예약 범위 초과, -2 = 낮은 우선순위 양보
local P = 'riot:rl:'
local routing = ARGV[1]
local method = ARGV[2]
local level = tonumber(ARGV[3])
local safety = tonumber(ARGV[5])
local lookahead = tonumber(ARGV[6])
local reserve = tonumber(ARGV[7])

local now
if ARGV[8] ~= '' then
  now = tonumber(ARGV[8])
else
  local t = redis.call('TIME')
  now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
end
local sec = math.floor(now / 1000)

local function fmt(n)
  return string.format('%d', n)
end

local appBucket = routing .. '|app'
local methodBucket = routing .. '|m|' .. method

-- 창마다 (칸 길이 len, 칸 허용치 a). RateLimitWindow#bucketSeconds·allowance 와 같은 식이다.
local windows = {}
local function addWindows(bucket, header)
  for l, w in string.gmatch(header, '(%d+):(%d+)') do
    local limit = tonumber(l)
    local ws = tonumber(w)
    if limit > 0 and ws > 0 then
      local a = math.floor(math.min(limit * safety / ws, limit / (ws + 1)))
      local len = 1
      if a < 1 then
        len = ws
        a = math.max(1, math.floor(limit * safety / 2))
      end
      windows[#windows + 1] = { prefix = P .. 'c:' .. bucket .. ':' .. fmt(ws) .. ':', len = len, a = a }
    end
  end
end

addWindows(appBucket, redis.call('HGET', P .. 'limits', appBucket) or ARGV[4])
local methodHeader = redis.call('HGET', P .. 'limits', methodBucket)
if methodHeader then
  addWindows(methodBucket, methodHeader)
end

-- 정지 키 값은 정지가 끝나는 시각(ms)이다 (riot-pause.lua)
local function pauseLeft(bucket)
  local untilMs = redis.call('GET', P .. 'pause:' .. bucket)
  if untilMs then
    return math.max(0, tonumber(untilMs) - now)
  end
  return 0
end
local pauseMs = math.max(pauseLeft(appBucket), pauseLeft(methodBucket))

local function fits(s, capOf)
  for _, win in ipairs(windows) do
    local count = tonumber(redis.call('GET', win.prefix .. fmt(math.floor(s / win.len))) or '0')
    if count >= capOf(win) then
      return false
    end
  end
  return true
end

local function take(s)
  for _, win in ipairs(windows) do
    local idx = math.floor(s / win.len)
    local key = win.prefix .. fmt(idx)
    redis.call('INCR', key)
    redis.call('PEXPIRE', key, fmt((idx + 1) * win.len * 1000 - now + 2000))
  end
end

local busyKey = P .. 'busy:' .. routing

if level == 1 then
  local start = sec
  if pauseMs > 0 then
    start = math.floor((now + pauseMs) / 1000)
  end
  local fullAllowance = function(win) return win.a end
  for s = start, sec + lookahead do
    if fits(s, fullAllowance) then
      take(s)
      local wait = math.max(0, s * 1000 - now, pauseMs)
      if wait > 0 then
        redis.call('SET', busyKey, '1', 'PX', '1000')
        local horizonKey = P .. 'horizon:' .. routing
        local horizon = math.max(tonumber(redis.call('GET', horizonKey) or '0'), s)
        redis.call('SET', horizonKey, fmt(horizon), 'PX', fmt((lookahead + 2) * 1000))
      end
      return wait
    end
  end
  redis.call('SET', busyKey, '1', 'PX', '1000')
  return -1
end

if pauseMs > 0 or redis.call('EXISTS', busyKey) == 1 then
  return -2
end
local wantKey = P .. 'want:' .. routing
if level == 3 and redis.call('EXISTS', wantKey) == 1 then
  return -2
end
if level == 2 then
  redis.call('SET', wantKey, '1', 'PX', '1000')
end
local reducedAllowance = function(win) return win.a - math.floor(win.a * reserve) end
if fits(sec, reducedAllowance) then
  take(sec)
  return 0
end
return -2
