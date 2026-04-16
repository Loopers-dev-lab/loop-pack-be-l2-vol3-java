/**
 * 랭킹 API 검증 테스트
 *
 * 목표:
 *   1. 랭킹 목록 조회 API 정상 동작 확인 (상품 정보 Aggregation 포함)
 *   2. 이전 날짜 랭킹 조회 정상 동작 확인
 *   3. 상품 상세 조회 시 랭킹 순위 반환 확인
 *
 * 사전 조건:
 *   - 서버 실행 중
 *   - Redis에 랭킹 데이터가 적재되어 있어야 함 (상품 조회/주문 이벤트 발생 후)
 *
 * 실행: k6 run k6/06_ranking.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// 오늘 날짜 계산 (yyyyMMdd)
function todayDate() {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    return `${y}${m}${d}`;
}

// 어제 날짜 계산
function yesterdayDate() {
    const now = new Date();
    now.setDate(now.getDate() - 1);
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    return `${y}${m}${d}`;
}

const rankingSuccess = new Counter('ranking_success');
const rankingFail = new Counter('ranking_fail');
const productDetailSuccess = new Counter('product_detail_success');
const rankFieldPresent = new Rate('rank_field_present');

export const options = {
    scenarios: {
        ranking_check: {
            executor: 'shared-iterations',
            vus: 5,
            iterations: 20,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<500'],
    },
};

const HEADERS = {
    'X-Loopers-LoginId': 'user1',
    'X-Loopers-LoginPw': 'Test1234!',
};

export default function () {
    const today = todayDate();
    const yesterday = yesterdayDate();

    // 1. 오늘 랭킹 목록 조회
    {
        const res = http.get(`${BASE_URL}/api/v1/rankings?date=${today}&size=20&page=1`, { headers: HEADERS });
        const ok = check(res, {
            '[오늘 랭킹] 200 응답': (r) => r.status === 200,
            '[오늘 랭킹] data 필드 존재': (r) => JSON.parse(r.body).data !== undefined,
            '[오늘 랭킹] content 배열 존재': (r) => {
                const body = JSON.parse(r.body);
                return Array.isArray(body.data.content);
            },
            '[오늘 랭킹] 상품 정보 Aggregation 확인 (name, brandName 존재)': (r) => {
                const body = JSON.parse(r.body);
                const content = body.data.content;
                if (content.length === 0) return true; // 데이터 없으면 pass
                return content[0].name !== undefined && content[0].brandName !== undefined;
            },
            '[오늘 랭킹] rank 필드 존재': (r) => {
                const body = JSON.parse(r.body);
                const content = body.data.content;
                if (content.length === 0) return true;
                return content[0].rank !== undefined;
            },
        });
        ok ? rankingSuccess.add(1) : rankingFail.add(1);
    }

    sleep(0.5);

    // 2. 이전 날짜 랭킹 조회 (어제)
    {
        const res = http.get(`${BASE_URL}/api/v1/rankings?date=${yesterday}&size=20&page=1`, { headers: HEADERS });
        check(res, {
            '[어제 랭킹] 200 응답': (r) => r.status === 200,
            '[어제 랭킹] data 필드 존재': (r) => JSON.parse(r.body).data !== undefined,
            '[어제 랭킹] content 배열 존재': (r) => Array.isArray(JSON.parse(r.body).data.content),
        });
    }

    sleep(0.5);

    // 3. 상품 상세 조회 시 rank 필드 확인 (productId=1)
    {
        const res = http.get(`${BASE_URL}/api/v1/products/1`, { headers: HEADERS });
        const ok = check(res, {
            '[상품 상세] 200 응답': (r) => r.status === 200,
            '[상품 상세] rank 필드 존재 (null이어도 됨)': (r) => {
                const body = JSON.parse(r.body);
                return 'rank' in (body.data || {});
            },
        });
        productDetailSuccess.add(ok ? 1 : 0);
        rankFieldPresent.add(ok ? 1 : 0);
    }

    sleep(0.5);
}
