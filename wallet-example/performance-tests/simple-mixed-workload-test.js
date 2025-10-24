import http from 'k6/http';
import {check} from 'k6';

export let options = {
    stages: [
        {duration: '5s', target: 25},   // Ramp up to 25 users
        {duration: '40s', target: 25}, // Stay at 25 users
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<800'], // 95% of requests must complete below 800ms
        http_req_failed: ['rate<0.15'],   // Error rate must be below 15%
    },
};

export default function () {
    // Use a simple wallet ID that we know exists from seeding
    const walletId = `success-wallet-${String(Math.floor(Math.random() * 1000) + 1).padStart(3, '0')}`;

    // Random operation selection based on realistic user behavior
    const operation = Math.random();

    if (operation < 0.25) {
        // 25% - Balance check
        const response = http.get(`http://localhost:8080/api/wallets/${walletId}`);
        check(response, {
            'balance check status is 200': (r) => r.status === 200,
            'balance check response time < 800ms': (r) => r.timings.duration < 800,
        });

    } else if (operation < 0.50) {
        // 25% - Deposit
        const depositAmount = Math.floor(Math.random() * 91) + 10; // 10-100 range
        const depositId = `deposit-${__VU}-${__ITER}-${Date.now()}`;

        const payload = JSON.stringify({
            depositId: depositId,
            amount: depositAmount,
            description: 'Mixed workload deposit'
        });

        const response = http.post(
            `http://localhost:8080/api/wallets/${walletId}/deposit`,
            payload,
            {headers: {'Content-Type': 'application/json'}}
        );

        check(response, {
            'deposit status is 201 or 200': (r) => r.status === 201 || r.status === 200,
            'deposit response time < 800ms': (r) => r.timings.duration < 800,
        });

    } else if (operation < 0.65) {
        // 15% - Withdrawal
        const withdrawalAmount = Math.floor(Math.random() * 91) + 10; // 10-100 range
        const withdrawalId = `withdrawal-${__VU}-${__ITER}-${Date.now()}`;

        const payload = JSON.stringify({
            withdrawalId: withdrawalId,
            amount: withdrawalAmount,
            description: 'Mixed workload withdrawal'
        });

        const response = http.post(
            `http://localhost:8080/api/wallets/${walletId}/withdraw`,
            payload,
            {headers: {'Content-Type': 'application/json'}}
        );

        check(response, {
            'withdrawal status is 201 or 200': (r) => r.status === 201 || r.status === 200,
            'withdrawal response time < 800ms': (r) => r.timings.duration < 800,
        });

    } else if (operation < 0.85) {
        // 20% - Transfer
        let toWalletIndex = Math.floor(Math.random() * 1000) + 1;
        const fromWalletIndex = parseInt(walletId.split('-')[2]);

        // Ensure different wallets
        while (toWalletIndex === fromWalletIndex) {
            toWalletIndex = Math.floor(Math.random() * 1000) + 1;
        }

        const toWalletId = `success-wallet-${String(toWalletIndex).padStart(3, '0')}`;
        const transferAmount = Math.floor(Math.random() * 151) + 50; // 50-200 range
        const transferId = `transfer-${__VU}-${__ITER}-${Date.now()}`;

        const payload = JSON.stringify({
            transferId: transferId,
            fromWalletId: walletId,
            toWalletId: toWalletId,
            amount: transferAmount,
            description: 'Mixed workload transfer'
        });

        const response = http.post(
            `http://localhost:8080/api/wallets/transfer`,
            payload,
            {headers: {'Content-Type': 'application/json'}}
        );

        check(response, {
            'transfer status is 201 or 200': (r) => r.status === 201 || r.status === 200,
            'transfer response time < 800ms': (r) => r.timings.duration < 800,
        });

    } else {
        // 15% - History query
        const pageSize = [10, 50, 100][Math.floor(Math.random() * 3)];
        const page = Math.floor(Math.random() * 5) + 1; // Pages 1-5

        const response = http.get(
            `http://localhost:8080/api/wallets/${walletId}/events?page=${page}&size=${pageSize}`
        );

        check(response, {
            'history status is 200': (r) => r.status === 200,
            'history response time < 800ms': (r) => r.timings.duration < 800,
        });
    }
}
