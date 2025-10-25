import http from 'k6/http';
import {check, sleep} from 'k6';
import {config, getRandomBalance, getWalletId} from '../config-concurrency.js';

export let options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        http_req_duration: ['p(95)<2000'], // 95% of requests must complete below 2s
        http_req_failed: ['rate<0.01'],    // Error rate must be below 1%
    },
};

export default function () {
    console.info(`🌱 Seeding ${config.WALLET_POOL_SIZE} wallets for concurrency conflict tests...`);
    let createdWallets = 0;
    let failedWallets = 0;

    for (let i = 1; i <= config.WALLET_POOL_SIZE; i++) {
        const walletId = getWalletId(i);
        const initialBalance = getRandomBalance();
        const payload = JSON.stringify({
            owner: `concurrency-success-user-${String(i).padStart(3, '0')}`,
            initialBalance: initialBalance
        });

        const response = http.put(`${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}`, payload, {
            headers: {'Content-Type': 'application/json'},
        });

        check(response, {
            'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
        });

        if (response.status === 201 || response.status === 200) {
            createdWallets++;
        } else {
            failedWallets++;
            console.error(`Failed to create wallet ${walletId}: ${response.status} - ${response.body}`);
        }

        sleep(0.1); // Small sleep to avoid overwhelming the server during setup
    }

    console.info(`🎯 Concurrency suite seeding completed: ${createdWallets} wallets created, ${failedWallets} failed`);
    check(failedWallets === 0, {
        'All wallets created successfully': () => failedWallets === 0,
    });

    // Verify API is healthy after seeding
    const healthResponse = http.get(`${config.BASE_URL}${config.ENDPOINTS.HEALTH}`);
    check(healthResponse, {
        'API is healthy after seeding': (r) => r.status === 200 && JSON.parse(r.body).status === 'UP',
    });
}
