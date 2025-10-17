import http from 'k6/http';
import {check} from 'k6';

export let options = {
  stages: [
    { duration: '10s', target: 5 },   // Ramp up to 5 users
    { duration: '10s', target: 5 },   // Stay at 5 users
    { duration: '5s', target: 50 },   // Spike to 50 users
    { duration: '10s', target: 50 },  // Stay at 50 users (spike)
    { duration: '5s', target: 5 },    // Ramp down to 5 users
    { duration: '10s', target: 5 },   // Stay at 5 users
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95% of requests must complete below 1000ms
    http_req_failed: ['rate<0.20'],    // Error rate must be below 20% (higher tolerance for spike)
  },
};

export default function() {
  // Use a simple wallet ID that we know exists from seeding
  const walletId = `success-wallet-${String(Math.floor(Math.random() * 1000) + 1).padStart(3, '0')}`;
  
  // Perform deposit with random amount between 10-100
  const depositAmount = Math.floor(Math.random() * 91) + 10; // 10-100 range
  const depositId = `spike-deposit-${__VU}-${__ITER}-${Date.now()}`;
  
  const payload = JSON.stringify({
    depositId: depositId,
    amount: depositAmount,
    description: 'Spike test deposit'
  });
  
  const response = http.post(
    `http://localhost:8080/api/wallets/${walletId}/deposit`,
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );
  
  check(response, {
    'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
    'response time < 1000ms': (r) => r.timings.duration < 1000,
    'spike deposit successful': (r) => r.status === 201 || r.status === 200,
  });
}
