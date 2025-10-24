import http from 'k6/http';
import {check} from 'k6';
import {randomUUID} from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        'rate-limit-test': {
            executor: 'shared-iterations',
            vus: 1, // Single user to clearly see rate limiting
            iterations: 60, // Try to exceed 50/min limit
            maxDuration: '30s',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<200'],
        'rate_limit_triggered': ['rate>0.15'], // Expect at least 15% to be rate limited (10 out of 60)
    },
};

export default function () {
    const walletId = 'rate-limit-test-wallet';

    // Try to deposit - should hit rate limit after 50 requests
    const depositPayload = JSON.stringify({
        depositId: randomUUID(),
        amount: 10.00,
        description: 'Rate limit test deposit'
    });

    const res = http.post(
        `${BASE_URL}/api/wallets/${walletId}/deposit`,
        depositPayload,
        {
            headers: {'Content-Type': 'application/json'},
        }
    );

    // Check if request was successful or rate limited
    const success = check(res, {
        'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
        'status is 429 (rate limited)': (r) => r.status === 429,
    });

    // Custom metric to track rate limiting
    if (res.status === 429) {
        check(res, {
            'rate_limit_triggered': (r) => true,
            'has Retry-After header': (r) => r.headers['Retry-After'] !== undefined,
            'has X-RateLimit headers': (r) => {
                return r.headers['X-Ratelimit-Limit'] !== undefined;
            },
            'error response format is correct': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.error && body.error.code === 'RATE_LIMIT_EXCEEDED';
                } catch (e) {
                    return false;
                }
            },
        });
    }
}

export function handleSummary(data) {
    const rateLimited = data.metrics['checks'].values['rate>0.15'];
    const status429Count = Object.values(data.metrics['http_reqs'].values).filter(v => v === 429).length;

    console.log(`\n========================================`);
    console.log(`Rate Limiting Test Summary`);
    console.log(`========================================`);
    console.log(`Total requests: 60`);
    console.log(`Rate limited (429): ${status429Count || 'N/A'}`);
    console.log(`Expected: ~10 rate limited (after 50 successful)`);
    console.log(`Rate limiting working: ${rateLimited ? 'YES ✅' : 'NO ❌'}`);
    console.log(`========================================\n`);

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}

