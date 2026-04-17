-- KEYS[1] = "metric:bucket:{epochMillis}"
-- ARGV[1] = ttl (초)
-- ARGV[2], ARGV[3] = pid1, count1
-- ARGV[4], ARGV[5] = pid2, count2
-- ...
-- 반환: 반영된 field 개수

local ttl = tonumber(ARGV[1])
local applied = 0
for i = 2, #ARGV, 2 do
    redis.call('HINCRBY', KEYS[1], ARGV[i], tonumber(ARGV[i+1]))
    applied = applied + 1
end
redis.call('EXPIRE', KEYS[1], ttl)
return applied
