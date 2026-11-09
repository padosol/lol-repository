-- 값이 같을 때만 지운다 (compare-and-delete).
-- KEYS: [1] renewal:match:claim:{matchId}
-- ARGV: [1] renewalId
-- 반환: 1 = 지움, 0 = 값이 달라 그대로 둠
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
