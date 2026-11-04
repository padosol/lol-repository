-- KEYS[1] = renewal:match:claim:{matchId}, ARGV[1] = renewalId
-- 값이 같을 때만 지운다 (compare-and-delete).
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
