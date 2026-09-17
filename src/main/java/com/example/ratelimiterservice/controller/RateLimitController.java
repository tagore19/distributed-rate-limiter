package com.example.ratelimiterservice.controller;

import com.example.ratelimiterservice.ratelimiter.RedisRateLimiter;
import com.example.ratelimiterservice.ratelimiter.SlidingWindowRateLimiter;
import com.example.ratelimiterservice.service.RateLimitMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RateLimitController {

    private final RedisRateLimiter tokenBucketLimiter;
    private final SlidingWindowRateLimiter slidingWindowLimiter;
    private final RateLimitMetrics metrics;

    public RateLimitController(RedisRateLimiter tokenBucketLimiter,
                                SlidingWindowRateLimiter slidingWindowLimiter,
                                RateLimitMetrics metrics) {
        this.tokenBucketLimiter = tokenBucketLimiter;
        this.slidingWindowLimiter = slidingWindowLimiter;
        this.metrics = metrics;
    }

    @GetMapping({"/api/v1/resource", "/api/rate-limit/token-bucket"})
    public ResponseEntity<Map<String, Object>> getResource(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestParam(value = "algo", defaultValue = "token-bucket") String algo) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("data", "Heavy Database Query Result for user " + userId);
        response.put("algorithmUsed", algo);

        return ResponseEntity.ok(response);
    }

    @GetMapping({"/api/v1/metrics", "/metrics"})
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(metrics.getMetricsSummary());
    }
}
