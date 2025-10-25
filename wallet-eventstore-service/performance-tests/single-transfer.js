import {check} from 'k6';
import {performTransfer} from './setup/helpers.js';

export let options = {
    iterations: 1, // Only 1 iteration
    vus: 1, // Single VU
    thresholds: {
        http_req_duration: ['p(95)<300'], // 95% of requests must complete below 300ms
        http_req_failed: ['rate<0.05'],    // Error rate must be below 5%
    },
};

export default function () {
    // Use fixed wallet pair to avoid randomness
    const fromWalletId = 'perf-wallet-001';
    const toWalletId = 'perf-wallet-002';

    // Fixed transfer amount
    const transferAmount = 100;

    const result = performTransfer(
        fromWalletId,
        toWalletId,
        transferAmount,
        'Single transfer test'
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
