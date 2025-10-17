import http from 'k6/http';
import {check} from 'k6';

export let options = {
  stages: [
    { duration: '10s', target: 20 },  // Ramp up to 20 users
    { duration: '30s', target: 20 },  // Stay at 20 users
    { duration: '10s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
    http_req_failed: ['rate<0.1'],     // Error rate must be below 10%
  },
};

export default function() {
  const walletId = `wallet-${__VU}-${Date.now()}`;
  const payload = JSON.stringify({
    owner: `user-${__VU}`,
    initialBalance: 1000
  });
  
  const response = http.put(`http://localhost:8080/api/wallets/${walletId}`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  
  check(response, {
    'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'has Location header': (r) => r.headers['Location'] && r.headers['Location'].includes(walletId),
  });
}
