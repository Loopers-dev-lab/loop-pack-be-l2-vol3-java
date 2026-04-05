-- ZINCRBY + EXPIRE 원자적 실행
-- KEYS[1]: ranking ZSET key (e.g. rank:all:20260405)
-- ARGV[1]: member (productDbId as string)
-- ARGV[2]: score increment (double)
-- ARGV[3]: ttl seconds (only applied on first creation)
-- returns: new score after increment

local key = KEYS[1]
local member = ARGV[1]
local increment = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

local newScore = redis.call('ZINCRBY', key, increment, member)

if redis.call('TTL', key) == -1 then
    redis.call('EXPIRE', key, ttl)
end

return newScore
