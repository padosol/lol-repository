-- KEYS[1]=record hash
-- ARGV[1]=renewalId, ARGV[2]=field, ARGV[3]=value
-- renewalId 가 같을 때만 필드 하나를 쓴다 (TTL 은 건드리지 않는다)
if redis.call('HGET', KEYS[1], 'renewalId') ~= ARGV[1] then
    return 0
end
redis.call('HSET', KEYS[1], ARGV[2], ARGV[3])
return 1
