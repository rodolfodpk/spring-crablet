import http from 'k6/http';
import {check} from 'k6';

export let options = {
  stages: [
    { duration: '5s', target: 50 },   // Ramp up to 50 users
    { duration: '40s', target: 50 }, // Stay at 50 users (high concurrency)
    { duration: '5s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
    http_req_failed: ['rate<0.30'],   // Error rate must be below 30% (expecting some 409 conflicts)
  },
};

export default function() {
  // Use a larger set of wallets to reduce contention (50 wallets for 50 users)
  const walletIndex = Math.floor(Math.random() * 50) + 1; // Use wallets 1-50
  const walletId = `success-wallet-${String(walletIndex).padStart(3, '0')}`;
  
  // Get a different wallet for transfer
  let toWalletIndex = Math.floor(Math.random() * 50) + 1;
  while (toWalletIndex === walletIndex) {
    toWalletIndex = Math.floor(Math.random() * 50) + 1;
  }
  
  const toWalletId = `success-wallet-${String(toWalletIndex).padStart(3, '0')}`;
  
  // Perform transfer with random amount between 50-200
  const transferAmount = Math.floor(Math.random() * 151) + 50; // 50-200 range
  const transferId = `concurrency-transfer-${__VU}-${__ITER}-${Date.now()}`;
  
  const payload = JSON.stringify({
    transferId: transferId,
    fromWalletId: walletId,
    toWalletId: toWalletId,
    amount: transferAmount,
    description: 'Concurrency test transfer'
  });
  
  const response = http.post(
    `http://localhost:8080/api/wallets/transfer`,
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );
  
  // Accept 201 (created), 200 (idempotent), and 409 (conflict) as valid responses
  check(response, {
    'status is 201, 200 or 409': (r) => r.status === 201 || r.status === 200 || r.status === 409,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'concurrency test handled': (r) => r.status === 201 || r.status === 200 || r.status === 409,
  });
}
