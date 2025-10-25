import {check} from 'k6';
import {getWalletPairForVU, performTransfer} from './setup/helpers.js';

export let options = {
    stages: [
        {duration: '5s', target: 10},   // Ramp up to 10 users
        {duration: '40s', target: 10}, // Stay at 10 users
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<300'], // 95% of requests must complete below 300ms
        http_req_failed: ['rate<0.05'],    // Error rate must be below 5%
    },
};

export default function () {
    // Get wallet pair partitioned by VU to avoid concurrency conflicts
    const walletPair = getWalletPairForVU(__VU, options.stages[1].target);

    // Perform transfer with random amount between 50-200
    const transferAmount = Math.floor(Math.random() * 151) + 50; // 50-200 range

    const result = performTransfer(
        walletPair.fromWalletId,
        walletPair.toWalletId,
        transferAmount,
        'Transfer stress test'
    );

    check(result.response, {
        'status is 200': (r) => r.status === 200,
        'response time < 300ms': (r) => r.timings.duration < 300,
        'transfer successful': (r) => {
            // Transfer endpoint returns 200 OK with empty body on success
            return r.status === 200;
        },
    });
}
