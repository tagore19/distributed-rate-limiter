import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 50 }, // Ramp up to 50 users over 10 seconds
    { duration: '20s', target: 50 }, // Stay at 50 users for 20 seconds
    { duration: '5s', target: 0 },   // Ramp down
  ],
};

export default function () {
  // Send a request to your Token Bucket endpoint
  const res = http.get('http://localhost:8080/api/v1/resource?algo=token-bucket');
  
  check(res, {
    'is status 200 or 429': (r) => r.status === 200 || r.status === 429,
  });

  sleep(0.05); // Small delay between requests per user
}
