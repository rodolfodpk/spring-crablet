import http from 'k6/http';
import {check, sleep} from 'k6';
import {config, getRandomBalance, getWalletId} from '../config-success.js';

// Track successfully created wallets
let verifiedWallets = [];

export let options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        http_req_duration: ['p(95)<2000'], // 95% of requests must complete below 2s
        http_req_failed: ['rate<0.01'],    // Error rate must be below 1%
    },
};

export default function () {
    console.info(`🌱 Seeding ${config.WALLET_POOL_SIZE} wallets for success performance tests...`);
    let createdWallets = 0;
    let failedWallets = 0;

    for (let i = 1; i <= config.WALLET_POOL_SIZE; i++) {
        const walletId = getWalletId(i);
        const initialBalance = getRandomBalance();
        const payload = JSON.stringify({
            owner: `success-user-${String(i).padStart(3, '0')}`,
            initialBalance: initialBalance
        });

        const response = http.put(`${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}`, payload, {
            headers: {'Content-Type': 'application/json'},
        });

        check(response, {
            'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
        });

        if (response.status === 201 || response.status === 200) {
            verifiedWallets.push(walletId);
            createdWallets++;
        } else {
            failedWallets++;
            console.error(`Failed to create wallet ${walletId}: ${response.status} - ${response.body}`);
        }

        if (i % 100 === 0) {
            console.info(`Created ${createdWallets}/${config.WALLET_POOL_SIZE} wallets...`);
        }
        sleep(0.01); // Small sleep to avoid overwhelming the server during setup
    }

    console.info(`🎯 Success suite seeding completed: ${createdWallets} wallets created, ${failedWallets} failed`);
    console.info(`📊 Verified wallet pool: ${verifiedWallets.length} wallets available for testing`);

    check(failedWallets === 0, {
        'All wallets created successfully': () => failedWallets === 0,
    });

    check(verifiedWallets.length >= 100, {
        'Minimum wallet pool size': () => verifiedWallets.length >= 100,
    });

    // Verify API is healthy after seeding
    const healthResponse = http.get(`${config.BASE_URL}${config.ENDPOINTS.HEALTH}`);
    check(healthResponse, {
        'API is healthy after seeding': (r) => r.status === 200 && JSON.parse(r.body).status === 'UP',
    });

    // Output verified wallets as JSON for shell redirection
    const walletData = {
        wallets: verifiedWallets,
        count: verifiedWallets.length,
        timestamp: Date.now()
    };

    console.info(`💾 WALLET_DATA:${JSON.stringify(walletData)}`);
}
