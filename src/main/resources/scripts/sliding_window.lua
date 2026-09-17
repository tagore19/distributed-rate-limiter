-- Sliding Window Log Algorithm in Redis
-- KEYS[1]: rate limit key
-- ARGV[1]: capacity (max requests allowed in window)
-- ARGV[2]: windowSizeInMs (window length in milliseconds)
-- ARGV[3]: currentTimestampInMs (current time in milliseconds)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local windowSize = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local clearBefore = now - windowSize

-- Remove requests outside the current sliding window
redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)

-- Count requests remaining in the window
local currentRequests = redis.call('ZCARD', key)

if currentRequests < capacity then
    -- Increment sequence to ensure unique member names even for requests at exact same millisecond
    local seq = redis.call('INCR', key .. ':seq')
    redis.call('ZADD', key, now, now .. ':' .. seq)
    -- Set expiration to clean up inactive keys
    redis.call('PEXPIRE', key, windowSize)
    redis.call('PEXPIRE', key .. ':seq', windowSize)
    return 1
else
    return 0
end
