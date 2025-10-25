import {check} from 'k6';
import {config} from './config-success.js';
import {getRandomWallet, performDeposit} from './setup/helpers.js';

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
    // Get random wallet from the pool
    const walletId = getRandomWallet(config);

    // Perform deposit with random amount between 10-100
    const depositAmount = Math.floor(Math.random() * 91) + 10; // 10-100 range

    const result = performDeposit(config, walletId, depositAmount, 'Deposit test');

    check(result.response, {
        'status is 200': (r) => r.status === 200,
        'response time < 300ms': (r) => r.timings.duration < 300,
        'deposit successful': (r) => {
            // Deposit endpoint returns 200 OK with empty body on success
            return r.status === 200;
        },
    });
}
