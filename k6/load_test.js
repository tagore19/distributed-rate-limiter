import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 Load Test Script
// Simulates 500 concurrent users generating 15,000 requests/minute
export const options = {
  scenarios: {
    high_throughput: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 500 }, // Ramp up to 500 concurrent users
        { duration: '30s', target: 500 }, // Stay at 500 users
        { duration: '15s', target: 0 },   // Ramp down
      ],
    },
  },
  thresholds: {
    // Validate requirement: maintaining <40 ms average response time under load
    http_req_duration: ['p(95)<40', 'avg<40'],
  },
};

const BASE_URL = 'http://localhost:8080/api/v1';

export default function () {
  const userId = `user_${__VU % 50}`; // Simulate 50 active users
  const params = {
    headers: {
      'X-User-Id': userId,
      'Content-Type': 'application/json',
    },
  };

  // Alternate between token bucket and sliding window algorithms
  const algo = __ITER % 2 === 0 ? 'token-bucket' : 'sliding-window';
  const res = http.get(`${BASE_URL}/resource?algo=${algo}`, params);

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'response time < 40ms': (r) => r.timings.duration < 40,
  });

  sleep(0.1); // Small pacing between user requests
}
