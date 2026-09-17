package com.example.ratelimiterservice.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlidingWindowTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private SlidingWindowRateLimiter slidingWindowRateLimiter;
    private static final long CAPACITY = 5;
    private static final long WINDOW_MS = 1000;
    private static final String TEST_KEY = "rate-limit:sliding:user123";

    @BeforeEach
    void setUp() throws Exception {
        slidingWindowRateLimiter = new SlidingWindowRateLimiter(redisTemplate);
        slidingWindowRateLimiter.init();
    }

    @Test
    void testAllowWhenUnderCapacity() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
            .thenReturn(1L);

        boolean allowed = slidingWindowRateLimiter.allowRequest(TEST_KEY, CAPACITY, WINDOW_MS);
        assertTrue(allowed, "Should allow request when under capacity");
    }

    @Test
    void testDenyWhenExceedingCapacity() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
            .thenReturn(0L);

        boolean allowed = slidingWindowRateLimiter.allowRequest(TEST_KEY, CAPACITY, WINDOW_MS);
        assertFalse(allowed, "Should deny request when exceeding capacity");
    }

    @Test
    void testInMemoryFallbackWhenRedisFails() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Redis connection refused"));

        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(slidingWindowRateLimiter.allowRequest(TEST_KEY, CAPACITY, WINDOW_MS),
                "Fallback should allow request " + (i + 1));
        }

        assertFalse(slidingWindowRateLimiter.allowRequest(TEST_KEY, CAPACITY, WINDOW_MS),
            "Fallback should deny request when capacity reached");
    }
}
