import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 캐시 히트/미스 응답 시간을 별도 측정
const cachedDuration   = new Trend('cached_page_duration',   true);
const uncachedDuration = new Trend('uncached_page_duration', true);
const cachedErrors     = new Counter('cached_page_errors');
const uncachedErrors   = new Counter('uncached_page_errors');

export const options = {
    stages: [
        { duration: '10s', target: 30 },  // 30명까지 점진 증가
        { duration: '30s', target: 30 },  // 30명 유지
        { duration: '10s', target: 0  },  // 종료
    ],
    thresholds: {
        cached_page_duration:   ['p(95)<100'],  // 캐시 히트는 95%가 100ms 이내 목표
        uncached_page_duration: ['p(95)<2000'], // 캐시 미스(딥 페이징)는 2000ms 허용
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    // ── 캐시 적용 대상 (page=0~2) ──────────────────────────
    const r1 = http.get(`${BASE_URL}/api/v1/products?page=0&sort=latest`);
    cachedDuration.add(r1.timings.duration);
    if (!check(r1, { 'cached page=0 status 200': (r) => r.status === 200 })) {
        cachedErrors.add(1);
    }

    const r2 = http.get(`${BASE_URL}/api/v1/products?page=1&sort=latest`);
    cachedDuration.add(r2.timings.duration);
    if (!check(r2, { 'cached page=1 status 200': (r) => r.status === 200 })) {
        cachedErrors.add(1);
    }

    // ── 캐시 미적용 대상 (page=10, 딥 페이징) ──────────────
    const r3 = http.get(`${BASE_URL}/api/v1/products?page=10&sort=latest`);
    uncachedDuration.add(r3.timings.duration);
    if (!check(r3, { 'uncached page=10 status 200': (r) => r.status === 200 })) {
        uncachedErrors.add(1);
    }

    sleep(0.5);
}
