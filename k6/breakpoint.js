/**
 * 주문 시스템 한계점 탐색 (Breakpoint Test)
 *
 * 목적: 현재 코드(비관적 락 + @Transactional)에서 몇 명까지 P99 < 2000ms 를 유지할 수 있는지 찾기
 *
 * 실행 방법:
 *   k6 run -e PRODUCT_ID=1 k6/breakpoint.js
 *
 * Grafana 연동 실행:
 *   k6 run -e PRODUCT_ID=1 --out influxdb=http://localhost:8086/k6 k6/breakpoint.js
 *
 * 주의사항:
 *   - PRODUCT_ID는 재고 10,000짜리 한계점 테스트 전용 상품 ID를 사용할 것
 *   - seed-black-friday.http 먼저 실행 후 이 스크립트 실행
 */

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const PRODUCT_ID  = __ENV.PRODUCT_ID  || '1';
const PASSWORD    = 'Bptest123!';
const USER_PREFIX = 'bp_user_';
const TOTAL_USERS = 300;

// 커스텀 메트릭
const orderDuration  = new Trend('order_duration', true);
const orderSuccess   = new Counter('order_success');
const stockExhausted = new Counter('stock_exhausted');
const sysError       = new Counter('sys_error');
const abnormalRate   = new Rate('abnormal_error_rate');

export const options = {
    // 단계적으로 VU 증가 → 어느 구간에서 P99가 꺾이는지 관찰
    stages: [
        { duration: '5s',  target: 10  },  // 1단계 진입
        { duration: '30s', target: 10  },  // 1단계 유지 (베이스라인)
        { duration: '5s',  target: 30  },  // 2단계 진입
        { duration: '30s', target: 30  },  // 2단계 유지
        { duration: '5s',  target: 50  },  // 3단계 진입
        { duration: '30s', target: 50  },  // 3단계 유지
        { duration: '5s',  target: 100 },  // 4단계 진입 (한계점 예상)
        { duration: '30s', target: 100 },  // 4단계 유지
        { duration: '5s',  target: 150 },  // 5단계 진입
        { duration: '30s', target: 150 },  // 5단계 유지
        { duration: '5s',  target: 200 },  // 6단계 진입 (블랙프라이데이 수준)
        { duration: '30s', target: 200 },  // 6단계 유지
        { duration: '5s',  target: 300 },  // 7단계 진입 (과부하)
        { duration: '30s', target: 300 },  // 7단계 유지
        { duration: '5s',  target: 0   },  // 종료
    ],
    thresholds: {
        // 이 임계값을 어느 구간에서 위반하는지가 핵심 관찰 포인트
        'order_duration': ['p(99)<2000'],
        'abnormal_error_rate': ['rate<0.05'],
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    console.log(`[setup] 테스트 유저 ${TOTAL_USERS}명 등록 시작 (이미 존재하면 무시)`);

    const headers = { 'Content-Type': 'application/json' };

    for (let i = 1; i <= TOTAL_USERS; i++) {
        const loginId = `${USER_PREFIX}${String(i).padStart(3, '0')}`;
        http.post(
            `${BASE_URL}/api/v1/users`,
            JSON.stringify({
                loginId,
                password: PASSWORD,
                name: `부하테스트${i}`,
                birthDate: '19900101',
                email: `${loginId}@loadtest.com`,
            }),
            { headers }
        );
    }

    console.log('[setup] 유저 등록 완료. 테스트 시작.');
    return { productId: parseInt(PRODUCT_ID) };
}

export default function (data) {
    const vuIndex = ((__VU - 1) % TOTAL_USERS) + 1;
    const loginId = `${USER_PREFIX}${String(vuIndex).padStart(3, '0')}`;

    const headers = {
        'Content-Type': 'application/json',
        'X-Loopers-LoginId': loginId,
        'X-Loopers-LoginPw': PASSWORD,
    };

    const res = http.post(
        `${BASE_URL}/api/v1/orders`,
        JSON.stringify({
            items: [{ productId: data.productId, quantity: 1 }],
        }),
        { headers, timeout: '10s' }
    );

    orderDuration.add(res.timings.duration);

    if (res.status === 201) {
        orderSuccess.add(1);
        abnormalRate.add(false);
        check(res, { '주문 성공 (201)': (r) => r.status === 201 });
    } else if (res.status === 400) {
        const isStockError = res.body && res.body.includes('재고가 부족합니다');
        if (isStockError) {
            stockExhausted.add(1);
            abnormalRate.add(false);
        } else {
            sysError.add(1);
            abnormalRate.add(true);
            check(res, { '비정상 400': () => false });
        }
    } else {
        // 500, 타임아웃, 서킷브레이커 등 비정상 에러
        sysError.add(1);
        abnormalRate.add(true);
        check(res, { [`비정상 에러 (${res.status})`]: () => false });
    }
    // sleep 없음 → 최대 처리량 생성, 한계점 빠르게 탐색
}

export function teardown() {
    console.log('');
    console.log('=== 한계점 탐색 결과 ===');
    console.log('Grafana에서 order_duration p(99) 추이를 확인하세요.');
    console.log('P99가 2000ms를 초과하는 구간의 VU 수 = 현재 시스템 한계점');
    console.log('');
    console.log('단계별 VU 기준:');
    console.log('  1단계:  10 VU  (베이스라인)');
    console.log('  2단계:  30 VU');
    console.log('  3단계:  50 VU');
    console.log('  4단계: 100 VU  ← 한계점 예상 구간');
    console.log('  5단계: 150 VU');
    console.log('  6단계: 200 VU  (블랙프라이데이 수준)');
    console.log('  7단계: 300 VU  (과부하)');
}
