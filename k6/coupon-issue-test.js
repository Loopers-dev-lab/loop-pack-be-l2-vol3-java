import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 실험 목적: 선착순 100장 쿠폰에 1000명 동시 요청
// 관찰: 발급 건수가 정확히 100장인지, 초과 발급이 없는지

const accepted = new Counter('accepted');
const rejected = new Counter('rejected');
const failed = new Counter('failed');

export const options = {
    vus: 100,          // 동시 유저 100명
    iterations: 1000,  // 총 요청 1000건
};

const BASE_URL = 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1';

export default function () {
    const memberId = __VU * 10 + __ITER; // 유저별 고유 ID

    const res = http.post(
        `${BASE_URL}/api/v1/coupons/${COUPON_ID}/issue/async?memberId=${memberId}`,
        null,
        { tags: { name: 'coupon-issue' } }
    );

    if (res.status === 200) {
        accepted.add(1);
    } else if (res.status === 400 || res.status === 409) {
        rejected.add(1);
    } else {
        failed.add(1);
        console.log(`unexpected status=${res.status} body=${res.body}`);
    }

    check(res, {
        'status is 200 or 400': (r) => r.status === 200 || r.status === 400 || r.status === 409,
    });
}

export function handleSummary(data) {
    console.log('\n=== 실험 결과 ===');
    console.log(`총 요청: ${data.metrics.iterations.values.count}`);
    console.log(`accepted (Kafka 발행): ${data.metrics.accepted?.values?.count ?? 0}`);
    console.log(`rejected: ${data.metrics.rejected?.values?.count ?? 0}`);
    console.log(`failed: ${data.metrics.failed?.values?.count ?? 0}`);
    console.log('streamer 로그에서 [CouponIssue] issued 건수 확인하세요');
    return {};
}
