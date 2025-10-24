import {check} from 'k6';
import {
  getRandomWallet,
  getWalletBalance,
  getWalletHistory,
  getWalletPair,
  performDeposit,
  performTransfer
} from './setup/helpers.js';

export let options = {
    stages: [
        {duration: '5s', target: 25},  // Ramp up to 25 users
        {duration: '40s', target: 25}, // Stay at 25 users
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<800'], // 95% of requests must complete below 800ms
        http_req_failed: ['rate<0.15'],   // Error rate must be below 15%
    },
};

export default function () {
    const scenario = Math.random();

    if (scenario < 0.3) {
        // 30% - Check balance
        const walletId = getRandomWallet();
        const balance = getWalletBalance(walletId);

        check(balance !== null, {
            'wallet exists': () => balance !== null,
            'balance retrieved': () => typeof balance === 'number',
        });
    } else if (scenario < 0.6) {
        // 30% - Deposit money
        const walletId = getRandomWallet();
        const depositAmount = Math.floor(Math.random() * 100) + 10; // 10-110 range

        const result = performDeposit(walletId, depositAmount, 'Mixed workload deposit');

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 400ms': (r) => r.timings.duration < 400,
        });
    } else if (scenario < 0.8) {
        // 20% - Transfer money
        const walletPair = getWalletPair();
        const transferAmount = Math.floor(Math.random() * 50) + 10; // 10-60 range

        const result = performTransfer(
            walletPair.fromWalletId,
            walletPair.toWalletId,
            transferAmount,
            'Mixed workload transfer'
        );

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 500ms': (r) => r.timings.duration < 500,
        });
    } else {
        // 20% - Check history
        const walletId = getRandomWallet();
        const page = Math.floor(Math.random() * 3); // 0-2
        const size = [10, 20, 50][Math.floor(Math.random() * 3)];

        const result = getWalletHistory(walletId, page, size);

        check(result.response, {
            'status is 200': (r) => r.status === 200,
            'response time < 600ms': (r) => r.timings.duration < 600,
        });
    }
}
