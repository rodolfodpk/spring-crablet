import {check} from 'k6';
import {config, getRandomTransferAmount} from './config-success.js';
import {getWalletPair, initializeWalletPool} from './setup/wallet-pool.js';
import {performTransfer} from './setup/helpers.js';

export let options = {
    stages: [
        {duration: '5s', target: 10},   // Ramp up to 10 users
        {duration: '40s', target: 10}, // Stay at 10 users
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<300'], // 95% of requests must complete below 300ms
        http_req_failed: ['rate<0.01'],   // Error rate must be below 1% (only network/timeout acceptable)
    },
};

// Setup function to initialize wallet pool
export function setup() {
    initializeWalletPool();
}

export default function () {
    // Get random wallet pair from large pool (1000 wallets)
    const walletPair = getWalletPair();

    // Perform transfer with small amount (50-200)
    const transferAmount = getRandomTransferAmount();

    const result = performTransfer(
        config,
        walletPair.fromWalletId,
        walletPair.toWalletId,
        transferAmount,
        'Success suite transfer test'
    );

    check(result.response, {
        'status is 200': (r) => r.status === 200,
        'response time < 300ms': (r) => r.timings.duration < 300,
        'transfer successful': (r) => r.status === 200,
    });
}
