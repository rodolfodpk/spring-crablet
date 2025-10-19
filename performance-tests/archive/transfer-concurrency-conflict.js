import {check} from 'k6';
import {config, getRandomTransferAmount} from './config-concurrency.js';
import {getWalletPair, performTransfer} from './setup/helpers.js';

export let options = {
    stages: [
        {duration: '5s', target: 50},   // Ramp up to 50 users
        {duration: '40s', target: 50}, // Stay at 50 users (high contention)
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
        http_req_failed: ['rate<0.3'],    // Error rate must be at least 30% (expected concurrency conflicts)
    },
};

export default function () {
    // Get random wallet pair from tiny pool (only 3 wallets!)
    const walletPair = getWalletPair(config);

    // Perform transfer with small amount (50-200) - balance always sufficient
    const transferAmount = getRandomTransferAmount();

    const result = performTransfer(
        config,
        walletPair.fromWalletId,
        walletPair.toWalletId,
        transferAmount,
        'Concurrency conflict test'
    );

    check(result.response, {
        'status is 200 or 409': (r) => r.status === 200 || r.status === 409,
        'response time < 500ms': (r) => r.timings.duration < 500,
        'successful transfer or concurrency conflict': (r) => {
            if (r.status === 200) return true; // Transfer succeeded
            if (r.status === 409) return true; // Concurrency conflict (expected)
            return false;
        },
    });
}
