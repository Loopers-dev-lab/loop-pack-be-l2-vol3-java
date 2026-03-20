import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 파레토 분포: 상위 1% 상품(ID 1~100)에 80% 트래픽
const HOT_PRODUCT_IDS = Array.from({ length: 100 }, (_, i) => i + 1);
const COLD_PRODUCT_IDS = Array.from({ length: 9900 }, (_, i) => i + 101);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_ID = '1';

// 커스텀 메트릭
const v1Duration = new Trend('v1_detail_duration', true);
const v2Duration = new Trend('v2_detail_duration', true);
const v3Duration = new Trend('v3_detail_duration', true);
const v1Requests = new Counter('v1_detail_requests');
const v2Requests = new Counter('v2_detail_requests');
const v3Requests = new Counter('v3_detail_requests');

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
    scenarios: {
        v1_detail: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testV1',
            tags: { version: 'v1' },
            startTime: '0s',
        },
        v2_warmup: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 50,
            exec: 'warmupV2',
            tags: { version: 'v2-warmup' },
            startTime: '35s',
        },
        v2_detail: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testV2',
            tags: { version: 'v2' },
            startTime: '40s',
        },
        v3_warmup: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 50,
            exec: 'warmupV3',
            tags: { version: 'v3-warmup' },
            startTime: '75s',
        },
        v3_detail: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testV3',
            tags: { version: 'v3' },
            startTime: '80s',
        },
    },
    thresholds: {
        'v1_detail_duration': ['p(50)<50', 'p(95)<200'],
        'v2_detail_duration': ['p(50)<20', 'p(95)<100'],
        'v3_detail_duration': ['p(50)<10', 'p(95)<50'],
    },
};

function getProductId() {
    // 80% 확률로 핫 상품, 20% 확률로 콜드 상품
    if (Math.random() < 0.8) {
        return HOT_PRODUCT_IDS[Math.floor(Math.random() * HOT_PRODUCT_IDS.length)];
    }
    return COLD_PRODUCT_IDS[Math.floor(Math.random() * COLD_PRODUCT_IDS.length)];
}

const params = {
    headers: { 'X-User-Id': USER_ID },
};

export function warmupV2() {
    const productId = getProductId();
    http.get(`${BASE_URL}/api/experiment/products/v2/${productId}`, params);
}

export function warmupV3() {
    const productId = getProductId();
    http.get(`${BASE_URL}/api/experiment/products/v3/${productId}`, params);
}

export function testV1() {
    const productId = getProductId();
    const res = http.get(`${BASE_URL}/api/experiment/products/v1/${productId}`, params);
    check(res, { 'v1 status 200': (r) => r.status === 200 });
    v1Duration.add(res.timings.duration);
    v1Requests.add(1);
}

export function testV2() {
    const productId = getProductId();
    const res = http.get(`${BASE_URL}/api/experiment/products/v2/${productId}`, params);
    check(res, { 'v2 status 200': (r) => r.status === 200 });
    v2Duration.add(res.timings.duration);
    v2Requests.add(1);
}

export function testV3() {
    const productId = getProductId();
    const res = http.get(`${BASE_URL}/api/experiment/products/v3/${productId}`, params);
    check(res, { 'v3 status 200': (r) => r.status === 200 });
    v3Duration.add(res.timings.duration);
    v3Requests.add(1);
}

export function handleSummary(data) {
    const extract = (metricName) => {
        const m = data.metrics[metricName];
        if (!m) return { avg: '-', p50: '-', p95: '-', p99: '-', max: '-' };
        return {
            avg: m.values['avg']?.toFixed(2) || '-',
            p50: m.values['p(50)']?.toFixed(2) || '-',
            p95: m.values['p(95)']?.toFixed(2) || '-',
            p99: m.values['p(99)']?.toFixed(2) || '-',
            max: m.values['max']?.toFixed(2) || '-',
        };
    };

    const v1 = extract('v1_detail_duration');
    const v2 = extract('v2_detail_duration');
    const v3 = extract('v3_detail_duration');

    const summary = `
========================================
  상품 상세 조회 성능 비교 결과
========================================

| 지표      | v1 (DB)    | v2 (Redis) | v3 (L1+L2) |
|-----------|------------|------------|------------|
| avg (ms)  | ${v1.avg.padStart(8)} | ${v2.avg.padStart(8)} | ${v3.avg.padStart(8)} |
| p50 (ms)  | ${v1.p50.padStart(8)} | ${v2.p50.padStart(8)} | ${v3.p50.padStart(8)} |
| p95 (ms)  | ${v1.p95.padStart(8)} | ${v2.p95.padStart(8)} | ${v3.p95.padStart(8)} |
| p99 (ms)  | ${v1.p99.padStart(8)} | ${v2.p99.padStart(8)} | ${v3.p99.padStart(8)} |
| max (ms)  | ${v1.max.padStart(8)} | ${v2.max.padStart(8)} | ${v3.max.padStart(8)} |

========================================
`;
    console.log(summary);
    return {};
}
