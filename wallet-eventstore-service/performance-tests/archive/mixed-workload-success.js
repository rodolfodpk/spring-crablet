import {check} from 'k6';
import {config} from './config-success.js';
import {
  getWalletBalance,
  getWalletEvents,
  performDeposit,
  performTransfer,
  performWithdrawal
} from './setup/helpers.js';
import {getRandomWallet, getWalletPair, initializeWalletPool} from './setup/wallet-pool.js';

export let options = {
    stages: [
        {duration: '5s', target: 25},  // Ramp up to 25 users
        {duration: '40s', target: 25}, // Stay at 25 users
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<800'], // 95% of requests must complete below 800ms
        http_req_failed: ['rate<0.01'],   // Error rate must be below 1% (only network/timeout acceptable)
    },
};

// Setup function to initialize wallet pool
export function setup() {
    initializeWalletPool();
}

export default function () {
    const scenario = Math.random();

    if (scenario < 0.25) {
        // 25% - Check balance
        const walletId = getRandomWallet();
        const balance = getWalletBalance(config, walletId);

        check(balance !== null, {
            'wallet exists': () => balance !== null,
            'balance retrieved': () => typeof balance === 'number',
        });
    } else if (scenario < 0.5) {
        // 25% - Deposit money
        const walletId = getRandomWallet();
        const depositAmount = Math.floor(Math.random() * 100) + 10; // 10-110 range

        const result = performDeposit(config, walletId, depositAmount, 'Success suite deposit');

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 400ms': (r) => r.timings.duration < 400,
        });
    } else if (scenario < 0.65) {
        // 15% - Withdraw money
        const walletId = getRandomWallet();
        const withdrawalAmount = Math.floor(Math.random() * 50) + 10; // 10-60 range

        const result = performWithdrawal(config, walletId, withdrawalAmount, 'Success suite withdrawal');

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 400ms': (r) => r.timings.duration < 400,
        });
    } else if (scenario < 0.85) {
        // 20% - Transfer money
        const walletPair = getWalletPair();
        const transferAmount = Math.floor(Math.random() * 50) + 10; // 10-60 range

        const result = performTransfer(
            config,
            walletPair.fromWalletId,
            walletPair.toWalletId,
            transferAmount,
            'Success suite transfer'
        );

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 500ms': (r) => r.timings.duration < 500,
        });
    } else {
        // 15% - Check events
        const walletId = getRandomWallet();
        const page = Math.floor(Math.random() * 3); // 0-2
        const size = [10, 20, 50][Math.floor(Math.random() * 3)];

        const result = getWalletEvents(config, walletId, page, size);

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 600ms': (r) => r.timings.duration < 600,
        });
    }
}
