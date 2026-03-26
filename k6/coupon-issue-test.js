import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const successCount = new Counter('coupon_issue_success');
const failedCount = new Counter('coupon_issue_failed');
const processingCount = new Counter('coupon_issue_processing');

// 선착순 쿠폰 발급 부하 테스트
// 수량 10개 쿠폰에 100명이 동시 요청 → 10명만 성공해야 함
export const options = {
    scenarios: {
        coupon_issue: {
            executor: 'shared-iterations',
            vus: 50,           // 동시 가상 유저 50명
            iterations: 100,   // 총 100번 요청
            maxDuration: '30s',
        },
    },
};

const BASE_URL = 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1001';

export default function () {
    const userId = exec.scenario.iterationInTest + 1; // 전체 시나리오에서 고유 (1~100)

    // 1. 발급 요청
    const issueRes = http.post(
        `${BASE_URL}/api/v1/coupons/${COUPON_ID}/issue`,
        null,
        { headers: { 'X-User-Id': `${userId}` } }
    );

    check(issueRes, {
        '발급 요청 접수 (202 or 500)': (r) => r.status === 202 || r.status === 500,
    });

    if (issueRes.status === 202) {
        processingCount.add(1);
    }
}

// 테스트 종료 후 결과 확인
export function handleSummary(data) {
    console.log('\n=== 선착순 쿠폰 발급 결과 ===');
    console.log(`총 요청: ${data.metrics.iterations.values.count}`);
    console.log(`PROCESSING 접수: ${data.metrics.coupon_issue_processing ? data.metrics.coupon_issue_processing.values.count : 0}`);
    console.log('\n⏳ Kafka Consumer 처리를 기다린 후 아래 명령으로 결과를 확인하세요:');
    console.log(`   curl -s http://localhost:8080/api/v1/coupons/${COUPON_ID}/issue-result -H "X-User-Id: 1" | python3 -m json.tool`);
    console.log('\n   또는 DB 직접 조회:');
    console.log('   docker exec docker-mysql-1 mysql -uapplication -papplication loopers -e "SELECT status, COUNT(*) FROM coupon_issue_results WHERE coupon_id=' + COUPON_ID + ' GROUP BY status;"');

    return {};
}
