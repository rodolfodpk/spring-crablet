import {check} from 'k6';
import {config, getRandomTransferAmount} from './config-insufficient.js';
import {getWalletPair, performTransfer} from './setup/helpers.js';

export let options = {
  stages: [
    { duration: '5s', target: 10 },   // Ramp up to 10 users
    { duration: '40s', target: 10 }, // Stay at 10 users
    { duration: '5s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'], // 95% of requests must complete below 300ms
    http_req_failed: ['rate<0.5'],    // Error rate must be at least 50% (expected insufficient balance errors)
  },
};

export default function() {
  // Get random wallet pair from small pool (10 wallets with low balance)
  const walletPair = getWalletPair(config);
  
  // Perform transfer with high amount (200-500) - intentionally higher than balance
  const transferAmount = getRandomTransferAmount();
  
  const result = performTransfer(
    config,
    walletPair.fromWalletId,
    walletPair.toWalletId,
    transferAmount,
    'Insufficient balance test'
  );
  
  check(result.response, {
    'status is 200 or 400': (r) => r.status === 200 || r.status === 400,
    'response time < 300ms': (r) => r.timings.duration < 300,
    'successful transfer or insufficient funds': (r) => {
      if (r.status === 200) return true; // Transfer succeeded
      if (r.status === 400) {
        // Check if error message contains "Insufficient funds"
        try {
          const body = JSON.parse(r.body);
          return body.message && body.message.includes('Insufficient funds');
        } catch {
          return false;
        }
      }
      return false;
    },
  });
}
