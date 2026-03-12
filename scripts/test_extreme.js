/**
 * 극단 부하 테스트 - 캐시 적용/미적용 비교용
 * 실행 전: ulimit -n 10000
 *
 * [캐시 미적용] page=10 테스트:
 *   docker exec redis-master redis-cli FLUSHALL && \
 *   K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT=scripts/report_extreme_uncached.html \
 *   k6 run -e TARGET_PAGE=10 scripts/test_extreme.js
 *
 * [캐시 적용] page=0 테스트:
 *   docker exec redis-master redis-cli FLUSHALL && \
 *   K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT=scripts/report_extreme_cached.html \
 *   k6 run -e TARGET_PAGE=0 scripts/test_extreme.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const duration = new Trend('response_duration', true);
const PAGE = __ENV.TARGET_PAGE || '0';

export const options = {
    stages: [
        { duration: '10s', target: 100 }, // 10초 안에 100명
        { duration: '20s', target: 100 }, // 100명 유지
        { duration: '10s', target: 300 }, // 10초 안에 300명
        { duration: '20s', target: 300 }, // 300명 유지
        { duration: '10s', target: 500 }, // 10초 안에 500명 (극단)
        { duration: '20s', target: 500 }, // 500명 유지
        { duration: '10s', target: 0   }, // 종료
    ],
    thresholds: {
        response_duration: ['p(95)<5000'], // 5초 기준 (초과 여부 관찰용)
        http_req_failed:   ['rate<0.10'],  // 에러율 10% 미만
    },
};

export default function () {
    const res = http.get(`http://localhost:8080/api/v1/products?page=${PAGE}&sort=latest`);
    duration.add(res.timings.duration);
    check(res, { 'status 200': (r) => r.status === 200 });
    sleep(0.3);
}
