import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const pgSuccessRate = new Rate('pg_success_rate');
const pgCircuitOpenRate = new Rate('pg_circuit_open_rate');
const paymentDuration = new Trend('payment_duration', true);

/**
 * 부하 테스트 시나리오
 *
 * 목적: readTimeout / connectTimeout 값 결정을 위한 latency budget 측정
 *
 * 측정 기준:
 *   - pg-simulator 요청 지연: 100ms ~ 500ms
 *   - pg-simulator 40% 확률 실패
 *   - readTimeout = p99 latency * 1.5 ~ 2
 *   - connectTimeout = 일반적으로 2s (내부 네트워크 기준)
 *
 * 단계별 부하:
 *   1단계 (0~30s): VU 5  → 정상 동작 확인, 기본 latency 측정
 *   2단계 (30~60s): VU 20 → 부하 증가, 실패율 증가 확인
 *   3단계 (60~90s): VU 50 → CircuitBreaker OPEN 유도
 *   4단계 (90~120s): VU 5  → HALF_OPEN 복구 확인
 */
export const options = {
    stages: [
        { duration: '30s', target: 5 },
        { duration: '30s', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 5 },
    ],
    thresholds: {
        // p99 latency가 5s 이하여야 함 (readTimeout 기준)
        'payment_duration': ['p(99)<5000'],
        // 전체 HTTP 에러율 90% 이하 (PG 40% 실패 + CircuitBreaker 포함)
        'http_req_failed': ['rate<0.9'],
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    const payload = JSON.stringify({
        orderId: Math.floor(Math.random() * 1000000) + 1,
        memberId: 1,
        amount: 10000,
        cardType: 'SAMSUNG',
        cardNo: '1234-5678-9814-1451',
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/v1/payments`, payload, params);
    const duration = Date.now() - start;

    paymentDuration.add(duration);

    const isSuccess = check(res, {
        '결제 요청 성공 (200)': (r) => r.status === 200,
        '응답 형식 정상': (r) => r.json('meta.result') !== undefined,
    });

    // PG 성공 / CircuitBreaker OPEN 구분
    if (res.status === 200 && res.json('meta.result') === 'SUCCESS') {
        pgSuccessRate.add(1);
        pgCircuitOpenRate.add(0);
    } else if (res.status === 503) {
        // CircuitBreaker OPEN 상태
        pgCircuitOpenRate.add(1);
        pgSuccessRate.add(0);
    } else {
        pgSuccessRate.add(0);
        pgCircuitOpenRate.add(0);
    }

    sleep(0.5);
}
