import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 페이지 깊이 분포: 70% 얕은(1~5), 25% 중간(6~20), 5% 깊은(21~50)
function getPage() {
    const r = Math.random();
    if (r < 0.7) return Math.floor(Math.random() * 5);       // page 0~4
    if (r < 0.95) return 5 + Math.floor(Math.random() * 15); // page 5~19
    return 20 + Math.floor(Math.random() * 30);               // page 20~49
}

// Cursor 시뮬레이션: 깊은 페이지는 더 작은 cursor ID
function getCursor() {
    const r = Math.random();
    if (r < 0.7) return 10000 - Math.floor(Math.random() * 100);  // 최신 근처
    if (r < 0.95) return 10000 - 100 - Math.floor(Math.random() * 300);
    return 10000 - 400 - Math.floor(Math.random() * 600);          // 오래된 데이터
}

// ===== Offset 메트릭 =====
const v1OffsetDuration = new Trend('v1_offset_duration', true);
const v2OffsetDuration = new Trend('v2_offset_duration', true);
const v3OffsetDuration = new Trend('v3_offset_duration', true);

// ===== Cursor 메트릭 =====
const v1CursorDuration = new Trend('v1_cursor_duration', true);
const v2CursorDuration = new Trend('v2_cursor_duration', true);
const v3CursorDuration = new Trend('v3_cursor_duration', true);

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
    scenarios: {
        // ===== Offset =====
        v1_offset: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testOffsetV1',
            tags: { version: 'v1', pagination: 'offset' },
            startTime: '0s',
        },
        v2_offset_warmup: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 50,
            exec: 'warmupOffsetV2',
            tags: { version: 'v2-warmup', pagination: 'offset' },
            startTime: '35s',
        },
        v2_offset: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testOffsetV2',
            tags: { version: 'v2', pagination: 'offset' },
            startTime: '40s',
        },
        v3_offset_warmup: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 50,
            exec: 'warmupOffsetV3',
            tags: { version: 'v3-warmup', pagination: 'offset' },
            startTime: '75s',
        },
        v3_offset: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testOffsetV3',
            tags: { version: 'v3', pagination: 'offset' },
            startTime: '80s',
        },
        // ===== Cursor =====
        v1_cursor: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testCursorV1',
            tags: { version: 'v1', pagination: 'cursor' },
            startTime: '115s',
        },
        v2_cursor_warmup: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 50,
            exec: 'warmupCursorV2',
            tags: { version: 'v2-warmup', pagination: 'cursor' },
            startTime: '150s',
        },
        v2_cursor: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testCursorV2',
            tags: { version: 'v2', pagination: 'cursor' },
            startTime: '155s',
        },
        v3_cursor_warmup: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 50,
            exec: 'warmupCursorV3',
            tags: { version: 'v3-warmup', pagination: 'cursor' },
            startTime: '190s',
        },
        v3_cursor: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'testCursorV3',
            tags: { version: 'v3', pagination: 'cursor' },
            startTime: '195s',
        },
    },
};

// ===== Warm-up =====

export function warmupOffsetV2() {
    const page = getPage();
    http.get(`${BASE_URL}/api/experiment/products/v2/offset?page=${page}&size=20`);
}

export function warmupOffsetV3() {
    const page = getPage();
    http.get(`${BASE_URL}/api/experiment/products/v3/offset?page=${page}&size=20`);
}

export function warmupCursorV2() {
    const cursor = getCursor();
    http.get(`${BASE_URL}/api/experiment/products/v2/cursor?cursor=${cursor}&size=20`);
}

export function warmupCursorV3() {
    const cursor = getCursor();
    http.get(`${BASE_URL}/api/experiment/products/v3/cursor?cursor=${cursor}&size=20`);
}

// ===== Offset =====

export function testOffsetV1() {
    const page = getPage();
    const res = http.get(`${BASE_URL}/api/experiment/products/v1/offset?page=${page}&size=20`);
    check(res, { 'v1 offset 200': (r) => r.status === 200 });
    v1OffsetDuration.add(res.timings.duration);
}

export function testOffsetV2() {
    const page = getPage();
    const res = http.get(`${BASE_URL}/api/experiment/products/v2/offset?page=${page}&size=20`);
    check(res, { 'v2 offset 200': (r) => r.status === 200 });
    v2OffsetDuration.add(res.timings.duration);
}

export function testOffsetV3() {
    const page = getPage();
    const res = http.get(`${BASE_URL}/api/experiment/products/v3/offset?page=${page}&size=20`);
    check(res, { 'v3 offset 200': (r) => r.status === 200 });
    v3OffsetDuration.add(res.timings.duration);
}

// ===== Cursor =====

export function testCursorV1() {
    const cursor = getCursor();
    const res = http.get(`${BASE_URL}/api/experiment/products/v1/cursor?cursor=${cursor}&size=20`);
    check(res, { 'v1 cursor 200': (r) => r.status === 200 });
    v1CursorDuration.add(res.timings.duration);
}

export function testCursorV2() {
    const cursor = getCursor();
    const res = http.get(`${BASE_URL}/api/experiment/products/v2/cursor?cursor=${cursor}&size=20`);
    check(res, { 'v2 cursor 200': (r) => r.status === 200 });
    v2CursorDuration.add(res.timings.duration);
}

export function testCursorV3() {
    const cursor = getCursor();
    const res = http.get(`${BASE_URL}/api/experiment/products/v3/cursor?cursor=${cursor}&size=20`);
    check(res, { 'v3 cursor 200': (r) => r.status === 200 });
    v3CursorDuration.add(res.timings.duration);
}

export function handleSummary(data) {
    const extract = (name) => {
        const m = data.metrics[name];
        if (!m) return { avg: '-', p50: '-', p95: '-', p99: '-', max: '-' };
        return {
            avg: m.values['avg']?.toFixed(2) || '-',
            p50: m.values['p(50)']?.toFixed(2) || '-',
            p95: m.values['p(95)']?.toFixed(2) || '-',
            p99: m.values['p(99)']?.toFixed(2) || '-',
            max: m.values['max']?.toFixed(2) || '-',
        };
    };

    const v1o = extract('v1_offset_duration');
    const v2o = extract('v2_offset_duration');
    const v3o = extract('v3_offset_duration');
    const v1c = extract('v1_cursor_duration');
    const v2c = extract('v2_cursor_duration');
    const v3c = extract('v3_cursor_duration');

    const summary = `
========================================
  상품 목록 조회 성능 비교 결과
========================================

  [Offset Pagination]
| 지표      | v1 (DB)    | v2 (Redis) | v3 (L1+L2) |
|-----------|------------|------------|------------|
| avg (ms)  | ${v1o.avg.padStart(8)} | ${v2o.avg.padStart(8)} | ${v3o.avg.padStart(8)} |
| p50 (ms)  | ${v1o.p50.padStart(8)} | ${v2o.p50.padStart(8)} | ${v3o.p50.padStart(8)} |
| p95 (ms)  | ${v1o.p95.padStart(8)} | ${v2o.p95.padStart(8)} | ${v3o.p95.padStart(8)} |
| p99 (ms)  | ${v1o.p99.padStart(8)} | ${v2o.p99.padStart(8)} | ${v3o.p99.padStart(8)} |
| max (ms)  | ${v1o.max.padStart(8)} | ${v2o.max.padStart(8)} | ${v3o.max.padStart(8)} |

  [Cursor Pagination]
| 지표      | v1 (DB)    | v2 (Redis) | v3 (L1+L2) |
|-----------|------------|------------|------------|
| avg (ms)  | ${v1c.avg.padStart(8)} | ${v2c.avg.padStart(8)} | ${v3c.avg.padStart(8)} |
| p50 (ms)  | ${v1c.p50.padStart(8)} | ${v2c.p50.padStart(8)} | ${v3c.p50.padStart(8)} |
| p95 (ms)  | ${v1c.p95.padStart(8)} | ${v2c.p95.padStart(8)} | ${v3c.p95.padStart(8)} |
| p99 (ms)  | ${v1c.p99.padStart(8)} | ${v2c.p99.padStart(8)} | ${v3c.p99.padStart(8)} |
| max (ms)  | ${v1c.max.padStart(8)} | ${v2c.max.padStart(8)} | ${v3c.max.padStart(8)} |

========================================
`;
    console.log(summary);
    return {};
}
