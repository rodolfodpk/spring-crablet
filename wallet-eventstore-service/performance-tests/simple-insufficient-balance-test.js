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
    // Use wallets from the insufficient-balance seed (wallets 1-100 with balances 10-50)
    const walletIndex = Math.floor(Math.random() * 100) + 1;
    const walletId = `insufficient-wallet-${String(walletIndex).padStart(3, '0')}`;

    // Get a different wallet for transfer (use wallets from successful seed)
    let toWalletIndex = Math.floor(Math.random() * 200) + 1;
    const toWalletId = `success-wallet-${String(toWalletIndex).padStart(3, '0')}`;

    // Attempt a transfer with amount higher than balance (should fail with 400)
    const transferAmount = 200; // Higher than the max initial balance (50)
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
    // This is the EXPECTED error behavior, not a concurrency conflict
    check(transferResponse, {
        'insufficient balance status is 400': (r) => r.status === 400,
        'insufficient balance response time < 300ms': (r) => r.timings.duration < 300,
        'insufficient balance error message': (r) => r.status === 400 && r.body.includes('Insufficient'),
    });
}
