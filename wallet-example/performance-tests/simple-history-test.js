import http from 'k6/http';
import {check} from 'k6';

export let options = {
    stages: [
        {duration: '5s', target: 15},   // Ramp up to 15 users
        {duration: '40s', target: 15}, // Stay at 15 users
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'], // 95% of requests must complete below 1000ms
        http_req_failed: ['rate<0.10'],    // Error rate must be below 10%
    },
};

export default function () {
    // Use a simple wallet ID that we know exists from seeding
    const walletId = `success-wallet-${String(Math.floor(Math.random() * 1000) + 1).padStart(3, '0')}`;

    // Test different page sizes and page numbers
    const pageSizes = [10, 50, 100];
    const pageSize = pageSizes[Math.floor(Math.random() * pageSizes.length)];
    const page = Math.floor(Math.random() * 10) + 1; // Pages 1-10

    const response = http.get(
        `http://localhost:8080/api/wallets/${walletId}/events?page=${page}&size=${pageSize}`
    );

    check(response, {
        'status is 200': (r) => r.status === 200,
        'response time < 1000ms': (r) => r.timings.duration < 1000,
        'history query successful': (r) => r.status === 200,
        'response has content-type': (r) => r.headers['Content-Type'].includes('application/json'),
    });
}
