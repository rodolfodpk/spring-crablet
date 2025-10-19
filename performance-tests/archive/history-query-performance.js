import http from 'k6/http';
import {check} from 'k6';

export let options = {
    stages: [
        {duration: '5s', target: 15},  // Ramp up to 15 users
        {duration: '40s', target: 15}, // Stay at 15 users
        {duration: '5s', target: 0},   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'], // 95% of requests must complete below 1s
        http_req_failed: ['rate<0.1'],     // Error rate must be below 10%
    },
};

export default function () {
    const walletId = `wallet-${__VU}`;

    // Test different page sizes and pages
    const page = Math.floor(Math.random() * 5);
    const size = [10, 20, 50][Math.floor(Math.random() * 3)];

    const response = http.get(
        `http://localhost:8080/api/wallets/${walletId}/events?page=${page}&size=${size}`
    );

    check(response, {
        'status is 200': (r) => r.status === 200,
        'response time < 1000ms': (r) => r.timings.duration < 1000,
        'has pagination metadata': (r) => {
            const body = JSON.parse(r.body);
            return body.hasOwnProperty('totalEvents') &&
                body.hasOwnProperty('page') &&
                body.hasOwnProperty('size');
        },
        'page size is correct': (r) => {
            const body = JSON.parse(r.body);
            return body.events.length <= body.size;
        },
    });
}
