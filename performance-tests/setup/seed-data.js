import http from 'k6/http';
import {check} from 'k6';
import {config, getRandomBalance, getWalletId} from '../config.js';

export let options = {
  stages: [
    { duration: '1s', target: 1 }, // Single VU for seeding
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'], // Allow up to 2s for wallet creation
    http_req_failed: ['rate<0.01'],     // Less than 1% failure rate
  },
};

export function setup() {
  console.log(`🌱 Seeding ${config.WALLET_POOL_SIZE} wallets for performance tests...`);
  
  const createdWallets = [];
  const failedWallets = [];
  
  // Create wallets sequentially to avoid overwhelming the system
  for (let i = 1; i <= config.WALLET_POOL_SIZE; i++) {
    const walletId = getWalletId(i);
    const owner = `perf-user-${String(i).padStart(3, '0')}`;
    const initialBalance = getRandomBalance();
    
    const payload = JSON.stringify({
      owner: owner,
      initialBalance: initialBalance
    });
    
    const response = http.put(
      `${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}`,
      payload,
      { headers: { 'Content-Type': 'application/json' } }
    );
    
    const success = response.status === 200 || response.status === 201;
    
    if (success) {
      createdWallets.push({
        walletId: walletId,
        owner: owner,
        initialBalance: initialBalance
      });
    } else {
      failedWallets.push({
        walletId: walletId,
        status: response.status,
        error: response.body
      });
    }
    
    // Small delay to avoid overwhelming the system
    if (i % 10 === 0) {
      console.log(`Created ${i}/${config.WALLET_POOL_SIZE} wallets...`);
    }
  }
  
  console.log(`✅ Successfully created ${createdWallets.length} wallets`);
  if (failedWallets.length > 0) {
    console.log(`❌ Failed to create ${failedWallets.length} wallets:`, failedWallets);
  }
  
  return {
    createdWallets: createdWallets,
    failedWallets: failedWallets,
    totalCreated: createdWallets.length,
    totalFailed: failedWallets.length
  };
}

export default function(data) {
  // This function is required by k6 but we do all work in setup()
  // Just verify the API is still healthy
  const response = http.get(`${config.BASE_URL}${config.ENDPOINTS.HEALTH}`);
  
  check(response, {
    'API is healthy after seeding': (r) => r.status === 200,
  });
}

export function teardown(data) {
  console.log(`🎯 Seeding completed: ${data.totalCreated} wallets created, ${data.totalFailed} failed`);
  
  if (data.totalFailed > 0) {
    console.log('Failed wallets:', data.failedWallets);
  }
}
