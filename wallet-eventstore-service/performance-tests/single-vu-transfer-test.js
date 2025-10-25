import {check} from 'k6';
import {getWalletPair, performTransfer} from './setup/helpers.js';

export let options = {
    stages: [
        {duration: '5s', target: 1},   // Single VU only
        {duration: '40s', target: 1}, // Stay at 1 user
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<300'], // 95% of requests must complete below 300ms
        http_req_failed: ['rate<0.05'],    // Error rate must be below 5%
    },
};

export default function () {
    // Get random wallet pair from the pool
    const walletPair = getWalletPair();

    // Perform transfer with random amount between 50-200
    const transferAmount = Math.floor(Math.random() * 151) + 50; // 50-200 range

    const result = performTransfer(
        walletPair.fromWalletId,
        walletPair.toWalletId,
        transferAmount,
        'Single VU transfer test'
    );

    check(result.response, {
        'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
        'response time < 300ms': (r) => r.timings.duration < 300,
        'transfer successful': (r) => {
            // Transfer endpoint returns 201 CREATED for new transfers, 200 OK for idempotent
            return r.status === 201 || r.status === 200;
        },
    });
}
