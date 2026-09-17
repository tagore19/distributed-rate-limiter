package com.example.ratelimiterservice.filter;

import com.example.ratelimiterservice.ratelimiter.RedisRateLimiter;
import com.example.ratelimiterservice.service.RateLimitMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {
    
    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitMetrics metrics;
    
    @Value("${rate-limit.capacity:100}")
    private long capacity;
    
    @Value("${rate-limit.refill-rate-per-second:10.0}")
    private double refillRatePerSecond;
    
    public RateLimitFilter(RedisRateLimiter redisRateLimiter, RateLimitMetrics metrics) {
        this.redisRateLimiter = redisRateLimiter;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/metrics") || path.equals("/metrics");
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        String userIdentifier = extractUserIdentifier(request);
        String rateLimitKey = "rate-limit:" + userIdentifier;
        
        boolean allowed = redisRateLimiter.allowRequest(rateLimitKey, capacity, refillRatePerSecond);
        
        if (!allowed) {
            metrics.recordThrottledRequest();
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Rate limit exceeded. DB lookup avoided!\"}");
            return;
        }
        
        metrics.recordAllowedRequest();
        filterChain.doFilter(request, response);
    }
    
    private String extractUserIdentifier(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        return getClientIpAddress(request);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
