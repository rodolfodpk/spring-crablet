import http from 'k6/http';
import {check} from 'k6';

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

export default function () {
    // Get two different wallets from the seeded pool
    const wallet1Index = Math.floor(Math.random() * 1000) + 1;
    let wallet2Index = Math.floor(Math.random() * 1000) + 1;

    // Ensure we have different wallets
    while (wallet2Index === wallet1Index) {
        wallet2Index = Math.floor(Math.random() * 1000) + 1;
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

    check(response, {
        'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
        'response time < 300ms': (r) => r.timings.duration < 300,
        'transfer successful': (r) => r.status === 201 || r.status === 200,
    });
}