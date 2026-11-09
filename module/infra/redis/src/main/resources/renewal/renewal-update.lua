-- renewalId 가 같을 때만 레코드 필드 여러 개를 쓴다 (TTL 은 건드리지 않는다).
-- KEYS: [1] 레코드 hash
-- ARGV: [1] renewalId, [2..] (필드, 값) 쌍
-- 반환: 1 = 씀, 0 = renewalId 다름
if redis.call('HGET', KEYS[1], 'renewalId') ~= ARGV[1] then
    return 0
end
for i = 2, #ARGV, 2 do
    redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
end
return 1
