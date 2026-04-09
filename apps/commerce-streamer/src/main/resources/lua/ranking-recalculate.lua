-- 재집계 Lua Script: DEL + ZADD + EXPIRE 원자적 실행
-- KEYS[1]: ranking ZSET key
-- ARGV[1]: ttl seconds
-- ARGV[2..N]: member1, score1, member2, score2, ...
-- returns: 추가된 멤버 수

local key = KEYS[1]
local ttl = tonumber(ARGV[1])

redis.call('DEL', key)

local count = 0
for i = 2, #ARGV, 2 do
    local member = ARGV[i]
    local score = tonumber(ARGV[i + 1])
    redis.call('ZADD', key, score, member)
    count = count + 1
end

if count > 0 then
    redis.call('EXPIRE', key, ttl)
end

return count
