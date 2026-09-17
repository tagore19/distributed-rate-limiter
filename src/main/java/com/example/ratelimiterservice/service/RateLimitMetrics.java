package com.example.ratelimiterservice.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimitMetrics {

    private final AtomicLong totalIncomingRequests = new AtomicLong(0);
    private final AtomicLong dbQueriesExecuted = new AtomicLong(0);
    private final AtomicLong requestsThrottledAtEdge = new AtomicLong(0);

    public void recordAllowedRequest() {
        totalIncomingRequests.incrementAndGet();
        dbQueriesExecuted.incrementAndGet();
    }

    public void recordThrottledRequest() {
        totalIncomingRequests.incrementAndGet();
        requestsThrottledAtEdge.incrementAndGet();
    }

    public Map<String, Object> getMetricsSummary() {
        long total = totalIncomingRequests.get();
        long dbQueries = dbQueriesExecuted.get();
        long throttled = requestsThrottledAtEdge.get();
        double dbSavedPercent = total > 0 ? ((double) throttled / total) * 100.0 : 0.0;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalIncomingRequests", total);
        metrics.put("dbQueriesExecuted", dbQueries);
        metrics.put("requestsThrottledAtEdge", throttled);
        metrics.put("backendDbLookupsPreventedPercent", String.format("%.2f%%", dbSavedPercent));

        return metrics;
    }
}
