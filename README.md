# Distributed Rate Limiter Service

A production-ready distributed rate limiting service implementing **Token Bucket** and **Sliding Window Log** algorithms with Redis-backed atomic Lua scripts, Docker containerization, k6 load testing, and in-memory fallback.

---

## Problem Statement

Rate limiting is essential for:
- **API Protection**: Prevent abuse and ensure fair resource usage.
- **Cost Control**: Limit expensive operations (database queries, external API calls).
- **Stability**: Protect backend systems from traffic spikes.
- **Compliance**: Enforce usage quotas per user or API key.

This service provides a distributed, scalable rate limiting solution that maintains availability even during Redis outages.

---

## High-Level Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP Request (e.g. GET /api/v1/resource)
       ▼
┌─────────────────────┐
│  RateLimitFilter    │  ← Servlet Filter (Spring Order 1)
│  - Extract User ID  │
│  - Check Rate Limit │
│  - Record Metrics   │
└──────┬──────────────┘
       │
       ├──► Redis (Primary Engine)
       │    ├── token_bucket.lua (Atomic Token Bucket)
       │    └── sliding_window.lua (Atomic Sliding Window ZSET)
       │
       └──► In-Memory (Fallback Engine)
            ├── TokenBucket per User (ConcurrentHashMap)
            └── SlidingWindow per User (ConcurrentLinkedDeque)
```

---

## Core Algorithms

### 1. Token Bucket Algorithm
- **Overview**: Token Bucket allows bursts up to capacity while maintaining a steady refill rate.
- **Redis Implementation**: [token_bucket.lua](file:///e:/distributed-rate-limiter/src/main/resources/scripts/token_bucket.lua) executes atomically in Redis using Hashes (`tokens` and `lastRefillTime`).
- **In-Memory Fallback**: [TokenBucket.java](file:///e:/distributed-rate-limiter/src/main/java/com/example/ratelimiterservice/ratelimiter/TokenBucket.java).

### 2. Sliding Window Log Algorithm
- **Overview**: Sliding Window Log tracks precise request timestamps within a sliding window (e.g., 60 seconds).
- **Redis Implementation**: [sliding_window.lua](file:///e:/distributed-rate-limiter/src/main/resources/scripts/sliding_window.lua) uses Redis Sorted Sets (`ZSET`) with `ZREMRANGEBYSCORE` and `ZCARD`.
- **In-Memory Fallback**: [SlidingWindowRateLimiter.java](file:///e:/distributed-rate-limiter/src/main/java/com/example/ratelimiterservice/ratelimiter/SlidingWindowRateLimiter.java) using `ConcurrentLinkedDeque`.

---

## Failure Handling Strategy (Graceful Degradation)

When Redis is unavailable, the service automatically falls back to local in-memory token buckets/sliding window deques:

```java
try {
    return redisTemplate.execute(script, ...);
} catch (Exception e) {
    // Fallback to in-memory TokenBucket / SlidingWindow
    return fallbackLimiter.allowRequest(...);
}
```

- ✅ **High Availability**: Application remains 100% available during Redis downtime.
- ✅ **Automatic Recovery**: Reconnects to Redis seamlessly when connection recovers.

---

## Quick Start (Docker Compose)

### 1. Build & Run Containers
```bash
docker compose up --build -d
```
This starts both **Redis 7** (port `6379`) and **Spring Boot Rate Limiter** (port `8080`).

### 2. Check Running Containers
```bash
docker ps
```

---

## Sample API Usage

### Token Bucket Endpoint
```bash
curl -H "X-User-Id: user123" http://localhost:8080/api/v1/resource?algo=token-bucket
```

### Sliding Window Endpoint
```bash
curl -H "X-User-Id: user123" http://localhost:8080/api/v1/resource?algo=sliding-window
```

### Metrics & Database Protection Endpoint
```bash
curl http://localhost:8080/metrics
```

**Sample Metrics Response**:
```json
{
  "totalIncomingRequests": 26471,
  "dbQueriesExecuted": 101,
  "requestsThrottledAtEdge": 26370,
  "backendDbLookupsPreventedPercent": "99.62%"
}
```

---

## Load Testing & Benchmarking (k6)

Run the included k6 stress test simulating 50 Virtual Users over 35 seconds:

```powershell
Get-Content load-tests/test.js | docker run --rm -i --network="host" grafana/k6 run -
```

### Measured Performance Results:
- **Throughput**: `26,471 requests` at **`756 requests/second` (~45,000 requests/min)**.
- **Average Latency**: **`1.74 ms`** (95th percentile = `4.26 ms`).
- **Edge Throttling**: **`99.62%` backend DB query lookups saved**.

---

## Testing Strategy

### Running Unit Tests
```bash
mvn test
```

Includes test coverage for:
- `TokenBucketTest`: Unit tests for refill math and burst capacity.
- `RedisRateLimiterTest`: Mocked Redis execution and fallback triggers.
- `SlidingWindowTest`: Timestamp window eviction and capacity limits.
- `ConcurrencyTest`: Multi-threaded (100 parallel threads) race-condition safety.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
Copyright (c) 2026 Tagore Reddy Pasham (tagore19).
