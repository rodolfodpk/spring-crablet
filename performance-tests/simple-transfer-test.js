import http from 'k6/http';
import {check} from 'k6';

export let options = {
    stages: [
        {duration: '5s', target: 5},   // Ramp up to 5 users
        {duration: '40s', target: 5}, // Stay at 5 users
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<300'], // 95% of requests must complete below 300ms
        http_req_failed: ['rate<0.10'],   // Error rate must be below 10% (DCB concurrency conflicts expected)
    },
};

export default function () {
    // Use a subset of wallets to reduce contention (similar to concurrency test)
    // Use wallets 1-200 for transfers (5 users, ~40 wallets per user)
    const wallet1Index = Math.floor(Math.random() * 200) + 1;
    let wallet2Index = Math.floor(Math.random() * 200) + 1;

    // Ensure we have different wallets
    while (wallet2Index === wallet1Index) {
        wallet2Index = Math.floor(Math.random() * 200) + 1;
    }

    const fromWalletId = `success-wallet-${String(wallet1Index).padStart(3, '0')}`;
    const toWalletId = `success-wallet-${String(wallet2Index).padStart(3, '0')}`;

    // Perform transfer with random amount between 50-200
    const transferAmount = Math.floor(Math.random() * 151) + 50; // 50-200 range
    const transferId = `transfer-${__VU}-${__ITER}-${Date.now()}`;

    const payload = JSON.stringify({
        transferId: transferId,
        fromWalletId: fromWalletId,
        toWalletId: toWalletId,
        amount: transferAmount,
        description: 'Simple transfer test'
    });

    const response = http.post(
        `http://localhost:8080/api/wallets/transfer`,
        payload,
        {headers: {'Content-Type': 'application/json'}}
    );

    // Accept 201 (created), 200 (idempotent), and 409 (conflict) as valid responses
    // DCB pattern causes legitimate concurrency conflicts
    check(response, {
        'status is valid (201, 200, or 409)': (r) => r.status === 201 || r.status === 200 || r.status === 409,
        'response time < 300ms': (r) => r.timings.duration < 300,
        'transfer handled': (r) => r.status === 201 || r.status === 200 || r.status === 409,
    });
}