import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DATE = __ENV.DATE || '20260416';

const quarterlyDuration = new Trend('quarterly_duration', true);
const weeklyDuration = new Trend('weekly_duration', true);
const monthlyDuration = new Trend('monthly_duration', true);
const errorRate = new Rate('error_rate');

export const options = {
    scenarios: {
        quarterly_baseline: {
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
            exec: 'quarterlyRanking',
            tags: { scenario: 'quarterly_baseline' },
        },
        weekly_comparison: {
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
            exec: 'weeklyRanking',
            startTime: '35s',
            tags: { scenario: 'weekly_comparison' },
        },
        monthly_comparison: {
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
            exec: 'monthlyRanking',
            startTime: '70s',
            tags: { scenario: 'monthly_comparison' },
        },
        quarterly_high_load: {
            executor: 'constant-vus',
            vus: 200,
            duration: '30s',
            exec: 'quarterlyRanking',
            startTime: '105s',
            tags: { scenario: 'quarterly_high_load' },
        },
    },
    thresholds: {
        'quarterly_duration{scenario:quarterly_baseline}': ['p(95)<100'],
        'weekly_duration{scenario:weekly_comparison}': ['p(95)<100'],
        'monthly_duration{scenario:monthly_comparison}': ['p(95)<100'],
        'quarterly_duration{scenario:quarterly_high_load}': ['p(95)<200'],
        'error_rate': ['rate<0.05'],
    },
};

function measure(period, metric) {
    const page = Math.floor(Math.random() * 5);
    const res = http.get(`${BASE_URL}/api/v1/rankings?period=${period}&date=${DATE}&page=${page}&size=20`);
    metric.add(res.timings.duration);
    check(res, { [`${period} 200`]: (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
    sleep(0.05);
}

export function quarterlyRanking() { measure('quarterly', quarterlyDuration); }
export function weeklyRanking() { measure('weekly', weeklyDuration); }
export function monthlyRanking() { measure('monthly', monthlyDuration); }
