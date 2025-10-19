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
        http_req_failed: ['rate>0.95'],    // Error rate must be above 95% (expected for insufficient balance test)
    },
};

export default function () {
    // Use a specific wallet that we'll drain first
    const walletId = `success-wallet-${String(Math.floor(Math.random() * 100) + 900).padStart(3, '0')}`; // Use wallets 900-999

    // First, drain the wallet by making a large withdrawal
    const drainAmount = 80000; // Large amount to drain most of the balance
    const drainId = `drain-${__VU}-${__ITER}-${Date.now()}`;

    const drainPayload = JSON.stringify({
        withdrawalId: drainId,
        amount: drainAmount,
        description: 'Drain wallet for insufficient balance test'
    });

    const drainResponse = http.post(
        `http://localhost:8080/api/wallets/${walletId}/withdraw`,
        drainPayload,
        {headers: {'Content-Type': 'application/json'}}
    );

    // Now attempt a transfer that should fail with insufficient funds
    let toWalletIndex = Math.floor(Math.random() * 1000) + 1;
    const fromWalletIndex = parseInt(walletId.split('-')[2]);

    // Ensure different wallets
    while (toWalletIndex === fromWalletIndex) {
        toWalletIndex = Math.floor(Math.random() * 1000) + 1;
    }

    const toWalletId = `success-wallet-${String(toWalletIndex).padStart(3, '0')}`;
    const transferAmount = 50000; // Large amount that should fail
    const transferId = `insufficient-transfer-${__VU}-${__ITER}-${Date.now()}`;

    const transferPayload = JSON.stringify({
        transferId: transferId,
        fromWalletId: walletId,
        toWalletId: toWalletId,
        amount: transferAmount,
        description: 'Insufficient balance test transfer'
    });

    const transferResponse = http.post(
        `http://localhost:8080/api/wallets/transfer`,
        transferPayload,
        {headers: {'Content-Type': 'application/json'}}
    );

    // Check that we get the expected 400 error for insufficient funds
    check(transferResponse, {
        'insufficient balance status is 400': (r) => r.status === 400,
        'insufficient balance response time < 300ms': (r) => r.timings.duration < 300,
        'insufficient balance error message': (r) => r.status === 400 && r.body.includes('Insufficient'),
    });
}
