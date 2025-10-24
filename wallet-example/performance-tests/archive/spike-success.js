import {check} from 'k6';
import {config} from './config-success.js';
import {getRandomWallet, getWalletPair, initializeWalletPool} from './setup/wallet-pool.js';
import {getWalletBalance, performDeposit, performTransfer} from './setup/helpers.js';

export let options = {
    stages: [
        {duration: '5s', target: 5},   // Normal load
        {duration: '10s', target: 50}, // Sudden spike
        {duration: '10s', target: 5},  // Back to normal
        {duration: '15s', target: 5},  // Sustain normal
        {duration: '10s', target: 0},  // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'], // 95% of requests must complete below 1s
        http_req_failed: ['rate<0.01'],     // Error rate must be below 1% (only network/timeout acceptable)
    },
};

// Setup function to initialize wallet pool
export function setup() {
    initializeWalletPool();
}

export default function () {
    const action = Math.random();

    if (action < 0.4) {
        // 40% - Quick balance check
        const walletId = getRandomWallet();
        const balance = getWalletBalance(config, walletId);

        check(balance !== null, {
            'wallet exists': () => balance !== null,
            'balance retrieved': () => typeof balance === 'number',
        });
    } else if (action < 0.7) {
        // 30% - Deposit money (more realistic than creating wallets during spike)
        const walletId = getRandomWallet();
        const depositAmount = Math.floor(Math.random() * 200) + 50; // 50-250 range

        const result = performDeposit(config, walletId, depositAmount, 'Success suite spike deposit');

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 800ms': (r) => r.timings.duration < 800,
        });
    } else {
        // 30% - Transfer
        const walletPair = getWalletPair(config);
        const transferAmount = Math.floor(Math.random() * 100) + 25; // 25-125 range

        const result = performTransfer(
            config,
            walletPair.fromWalletId,
            walletPair.toWalletId,
            transferAmount,
            'Success suite spike transfer'
        );

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 1000ms': (r) => r.timings.duration < 1000,
        });
    }
}
