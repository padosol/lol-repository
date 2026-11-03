-- KEYS[1]=record hash, KEYS[2]=targets set
-- ARGV[1]=renewalId, ARGV[2..]=matchId
-- renewalId 가 같을 때만 대상을 넣고 targetsReady 를 켠다 (빈 목록이어도 켠다)
if redis.call('HGET', KEYS[1], 'renewalId') ~= ARGV[1] then
    return 0
end
for i = 2, #ARGV do
    redis.call('SADD', KEYS[2], ARGV[i])
end
-- 대상 집합이 레코드보다 오래 남지 않게 레코드 남은 TTL 에 맞춘다
local ttl = redis.call('TTL', KEYS[1])
if ttl > 0 and redis.call('EXISTS', KEYS[2]) == 1 then
    redis.call('EXPIRE', KEYS[2], ttl)
end
redis.call('HSET', KEYS[1], 'targetsReady', '1')
return 1
