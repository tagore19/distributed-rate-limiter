package com.example.ratelimiterservice.ratelimiter;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class SlidingWindowRateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;
    private DefaultRedisScript<Long> script;

    private final ConcurrentHashMap<String, Deque<Long>> inMemoryWindows = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() throws IOException {
        ClassPathResource scriptResource = new ClassPathResource("scripts/sliding_window.lua");
        String scriptContent = new String(scriptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        script = new DefaultRedisScript<>();
        script.setScriptText(scriptContent);
        script.setResultType(Long.class);
    }

    public boolean allowRequest(String key, long capacity, long windowSizeInMs) {
        try {
            long currentTimestamp = System.currentTimeMillis();
            Long result = redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(capacity),
                String.valueOf(windowSizeInMs),
                String.valueOf(currentTimestamp)
            );
            return result != null && result == 1;
        } catch (Exception e) {
            return allowRequestInMemory(key, capacity, windowSizeInMs);
        }
    }

    private synchronized boolean allowRequestInMemory(String key, long capacity, long windowSizeInMs) {
        long now = System.currentTimeMillis();
        long clearBefore = now - windowSizeInMs;

        Deque<Long> timestamps = inMemoryWindows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        while (!timestamps.isEmpty() && timestamps.peekFirst() < clearBefore) {
            timestamps.pollFirst();
        }

        if (timestamps.size() < capacity) {
            timestamps.addLast(now);
            return true;
        }
        return false;
    }
}
