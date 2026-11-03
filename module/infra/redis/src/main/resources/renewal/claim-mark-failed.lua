-- KEYS[1] = renewal:match:failed:{matchId}, KEYS[2] = renewal:match:failed-count:{matchId}
-- ARGV[1] = 실패 시각(ms), ARGV[2] = 실패 표시 TTL(초), ARGV[3] = 실패 횟수 TTL(초)
-- 표시·횟수·TTL 을 한 번에 쓴다. 따로 쓰다 INCR 와 EXPIRE 사이에서 죽으면 TTL 없는 횟수가 남아 매치가 영구히 빠진다.
-- 반환: 올린 뒤의 실패 횟수
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
local count = redis.call('INCR', KEYS[2])
redis.call('EXPIRE', KEYS[2], ARGV[3])
return count
