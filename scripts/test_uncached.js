/**
 * 캐시 미적용 페이지 부하 테스트 (page=10 - 항상 DB 직접 조회)
 * 실행: K6_WEB_DASHBOARD=true k6 run scripts/test_uncached.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const duration = new Trend('response_duration', true);

export const options = {
    stages: [
        { duration: '20s', target: 100 }, // 100명까지 증가
        { duration: '30s', target: 100 }, // 100명 유지
        { duration: '20s', target: 200 }, // 200명까지 증가
        { duration: '30s', target: 200 }, // 200명 유지
        { duration: '20s', target: 300 }, // 300명까지 증가
        { duration: '30s', target: 300 }, // 300명 유지
        { duration: '10s', target: 0   }, // 종료
    ],
    thresholds: {
        response_duration: ['p(95)<2000'],
        http_req_failed:   ['rate<0.01'],
    },
};

export default function () {
    const res = http.get('http://localhost:8080/api/v1/products?page=10&sort=latest');
    duration.add(res.timings.duration);
    check(res, { 'status 200': (r) => r.status === 200 });
    sleep(0.5);
}
