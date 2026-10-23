package com.railway.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RateLimiterService {
    private static final int CAPACITY = 10;                 // max burst = 10 per minute
    private static final double REFILL_PER_SEC = 10.0 / 60; // refill rate

    // The whole refill + consume runs atomically inside Redis.
    private static final String LUA = """
        local key = KEYS[1]
        local cap = tonumber(ARGV[1])
        local rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local d = redis.call('HMGET', key, 'tokens', 'ts')
        local tokens = tonumber(d[1])
        local ts = tonumber(d[2])
        if tokens == nil then tokens = cap; ts = now end
        tokens = math.min(cap, tokens + ((now - ts) / 1000) * rate)
        local ok = 0
        if tokens >= 1 then tokens = tokens - 1; ok = 1 end
        redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
        redis.call('PEXPIRE', key, 120000)
        return ok
        """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);

    public RateLimiterService(StringRedisTemplate redis) { this.redis = redis; }

    public boolean allow(String userId) {
        Long r = redis.execute(script, List.of("ratelimit:" + userId),
                String.valueOf(CAPACITY), String.valueOf(REFILL_PER_SEC), String.valueOf(System.currentTimeMillis()));
        return r != null && r == 1L;
    }
}
