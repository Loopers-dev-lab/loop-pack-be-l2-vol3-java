local issuedKey = KEYS[1]
local quantityKey = KEYS[2]
local userId = ARGV[1]
local timestamp = tonumber(ARGV[2])

local totalQuantity = tonumber(redis.call('GET', quantityKey))
if not totalQuantity then
    return 'NOT_FOUND'
end

if redis.call('ZSCORE', issuedKey, userId) then
    return 'ALREADY_ISSUED'
end

if redis.call('ZCARD', issuedKey) >= totalQuantity then
    return 'QUANTITY_EXHAUSTED'
end

redis.call('ZADD', issuedKey, timestamp, userId)
return 'SUCCESS'
