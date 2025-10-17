import http from 'k6/http';
import {check} from 'k6';

export let options = {
  stages: [
    { duration: '5s', target: 10 },   // Ramp up to 10 users
    { duration: '40s', target: 10 }, // Stay at 10 users
    { duration: '5s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'], // 95% of requests must complete below 300ms
    http_req_failed: ['rate<0.05'],    // Error rate must be below 5%
  },
};

export default function() {
  // Use a simple wallet ID that we know exists from seeding
  const walletId = `success-wallet-${String(Math.floor(Math.random() * 1000) + 1).padStart(3, '0')}`;
  
  // Perform withdrawal with random amount between 10-100
  const withdrawalAmount = Math.floor(Math.random() * 91) + 10; // 10-100 range
  const withdrawalId = `withdrawal-${__VU}-${__ITER}-${Date.now()}`;
  
  const payload = JSON.stringify({
    withdrawalId: withdrawalId,
    amount: withdrawalAmount,
    description: 'Simple withdrawal test'
  });
  
  const response = http.post(
    `http://localhost:8080/api/wallets/${walletId}/withdraw`,
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );
  
  check(response, {
    'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
    'response time < 300ms': (r) => r.timings.duration < 300,
    'withdrawal successful': (r) => r.status === 201 || r.status === 200,
  });
}
