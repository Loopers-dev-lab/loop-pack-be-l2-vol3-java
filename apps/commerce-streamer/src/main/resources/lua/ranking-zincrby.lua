-- ZINCRBY + EXPIRE 원자적 실행 (daily + hourly dual-write)
-- KEYS[1]: daily ranking ZSET key (e.g. rank:all:20260405)
-- KEYS[2]: hourly ranking ZSET key (e.g. rank:all:20260405:14), empty string to skip
-- ARGV[1]: member (productDbId as string)
-- ARGV[2]: score increment (double)
-- ARGV[3]: daily ttl seconds (only applied on first creation)
-- ARGV[4]: hourly ttl seconds (only applied on first creation)
-- returns: new score after increment

local dailyKey = KEYS[1]
local hourlyKey = KEYS[2]
local member = ARGV[1]
local increment = tonumber(ARGV[2])
local dailyTtl = tonumber(ARGV[3])
local hourlyTtl = tonumber(ARGV[4])

local newScore = redis.call('ZINCRBY', dailyKey, increment, member)

if redis.call('TTL', dailyKey) == -1 then
    redis.call('EXPIRE', dailyKey, dailyTtl)
end

if hourlyKey ~= '' then
    redis.call('ZINCRBY', hourlyKey, increment, member)
    if redis.call('TTL', hourlyKey) == -1 then
        redis.call('EXPIRE', hourlyKey, hourlyTtl)
    end
end

return newScore
