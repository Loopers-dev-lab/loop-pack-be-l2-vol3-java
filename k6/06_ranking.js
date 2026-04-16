/**
 * 랭킹 시스템 부하 테스트
 *
 * 시나리오:
 *   A. 랭킹 Top-N 읽기          — GET /api/v1/rankings (Redis ZREVRANGE)
 *   B. 상품 상세 + rank 읽기    — GET /api/v1/products/{id} (ZREVRANK 오버헤드 측정)
 *   C. 이벤트 쓰기 파이프라인   — GET(조회) + POST(좋아요) → Kafka → ZSET 경로
 *   D. Hot Product 경합         — 상위 3개 상품에 70% 트래픽 집중
 *
 * 사전 조건:
 *   - 서버 실행 중 (commerce-api, commerce-streamer, Redis, Kafka)
 *   - user1~user100 계정 등록 완료 (bash scripts/seed-users.sh)
 *   - 상품 데이터 존재 (productId 1~20)
 *
 * 실행: k6 run k6/06_ranking.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// ── 공통 헬퍼 ──────────────────────────────────────────────────────────────

function todayDate() {
    const now = new Date();
    return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
}

function yesterdayDate() {
    const d = new Date();
    d.setDate(d.getDate() - 1);
    return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
}

function userHeaders(vuId) {
    const idx = (vuId % 100) + 1;
    return {
        'X-Loopers-LoginId': `user${idx}`,
        'X-Loopers-LoginPw': 'Test1234!',
    };
}

// Hot Product: 상위 3개에 70% 집중, 나머지 30%는 4~20에 분산
function pickProductId(vuId, iter) {
    const r = Math.random();
    if (r < 0.70) {
        return (((vuId + iter) % 3) + 1); // 1, 2, 3
    }
    return (Math.floor(Math.random() * 17) + 4); // 4~20
}

// ── 커스텀 메트릭 ──────────────────────────────────────────────────────────

const rankingReadFailRate    = new Rate('ranking_read_fail');
const productDetailFailRate  = new Rate('product_detail_fail');
const writeEventFailRate     = new Rate('write_event_fail');
const hotProductFailRate     = new Rate('hot_product_fail');
const weeklyRankingFailRate  = new Rate('weekly_ranking_fail');
const monthlyRankingFailRate = new Rate('monthly_ranking_fail');
const zrevrankLatency        = new Trend('zrevrank_extra_latency_ms');

// ── 시나리오 옵션 ──────────────────────────────────────────────────────────

export const options = {
    scenarios: {
        // A. 랭킹 Top-N 읽기 — 읽기 전용, Redis ZREVRANGE가 대부분 처리
        ranking_read: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 100 },
                { duration: '30s', target: 300 },
                { duration: '15s', target: 0 },
            ],
            exec: 'scenarioRankingRead',
            tags: { scenario: 'A-ranking-read' },
        },

        // B. 상품 상세 + rank — ZREVRANK 추가 오버헤드 측정
        product_detail_with_rank: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 50 },
                { duration: '30s', target: 200 },
                { duration: '15s', target: 0 },
            ],
            exec: 'scenarioProductDetail',
            tags: { scenario: 'B-product-detail' },
            startTime: '65s', // A 종료 후 시작
        },

        // C. 이벤트 쓰기 파이프라인 — 조회 + 좋아요 → Kafka → ZSET
        write_pipeline: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 100 },
                { duration: '40s', target: 500 },
                { duration: '10s', target: 0 },
            ],
            exec: 'scenarioWritePipeline',
            tags: { scenario: 'C-write-pipeline' },
            startTime: '130s', // B 종료 후 시작
        },

        // D. Hot Product 경합 — 상위 3개 상품에 70% 트래픽 집중
        hot_product: {
            executor: 'constant-vus',
            vus: 200,
            duration: '30s',
            exec: 'scenarioHotProduct',
            tags: { scenario: 'D-hot-product' },
            startTime: '200s', // C 종료 후 시작
        },

        // E. 주간 랭킹 조회 — MV 테이블 기반 읽기 성능 측정
        weekly_ranking_read: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },
                { duration: '20s', target: 150 },
                { duration: '10s', target: 0 },
            ],
            exec: 'scenarioWeeklyRankingRead',
            tags: { scenario: 'E-weekly-ranking' },
            startTime: '240s', // D 종료 후 시작
        },

        // F. 월간 랭킹 조회 — MV 테이블 기반 읽기 성능 측정
        monthly_ranking_read: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },
                { duration: '20s', target: 150 },
                { duration: '10s', target: 0 },
            ],
            exec: 'scenarioMonthlyRankingRead',
            tags: { scenario: 'F-monthly-ranking' },
            startTime: '285s', // E 종료 후 시작
        },
    },

    thresholds: {
        // A: Redis 읽기이므로 넉넉하게
        'http_req_duration{scenario:A-ranking-read}':  ['p(95)<150', 'p(99)<300'],
        // B: ZREVRANK 1회 추가, 상품 DB 조회 포함
        'http_req_duration{scenario:B-product-detail}': ['p(95)<200', 'p(99)<500'],
        // C: Kafka 비동기 경로이므로 쓰기 자체(API 응답)는 빠를 수 있음
        'http_req_duration{scenario:C-write-pipeline}': ['p(95)<500', 'p(99)<1000'],
        // D: Hot Key 경합 — 동일 상품 ZINCRBY 병목 구간 측정
        'http_req_duration{scenario:D-hot-product}':   ['p(95)<300', 'p(99)<600'],
        // E: MV 테이블 조회 — DB 읽기이므로 Redis보다 여유있게
        'http_req_duration{scenario:E-weekly-ranking}':  ['p(95)<300', 'p(99)<600'],
        // F: MV 테이블 조회 — 월간 집계는 데이터량이 많을 수 있으므로 허용치 넉넉하게
        'http_req_duration{scenario:F-monthly-ranking}': ['p(95)<300', 'p(99)<600'],

        // 전체 에러율
        'http_req_failed': ['rate<0.05'],
        'ranking_read_fail':    ['rate<0.01'],
        'product_detail_fail':  ['rate<0.01'],
        'write_event_fail':     ['rate<0.05'],
        'hot_product_fail':     ['rate<0.05'],
        'weekly_ranking_fail':  ['rate<0.01'],
        'monthly_ranking_fail': ['rate<0.01'],
    },
};

// ── 시나리오 A: 랭킹 Top-N 읽기 ───────────────────────────────────────────

export function scenarioRankingRead() {
    const today     = todayDate();
    const yesterday = yesterdayDate();
    const date      = Math.random() < 0.8 ? today : yesterday; // 80% 오늘, 20% 어제

    const res = http.get(`${BASE_URL}/api/v1/rankings?date=${date}&size=20&page=1`);

    const ok = check(res, {
        '[A] status 200':           (r) => r.status === 200,
        '[A] data.content 배열':    (r) => {
            try { return Array.isArray(JSON.parse(r.body).data.content); } catch { return false; }
        },
        '[A] rank 필드 존재':       (r) => {
            try {
                const content = JSON.parse(r.body).data.content;
                return content.length === 0 || content[0].rank !== undefined;
            } catch { return false; }
        },
        '[A] name·brandName Aggregation': (r) => {
            try {
                const content = JSON.parse(r.body).data.content;
                return content.length === 0 || (content[0].name !== undefined && content[0].brandName !== undefined);
            } catch { return false; }
        },
    });

    rankingReadFailRate.add(!ok);
    sleep(0.3);
}

// ── 시나리오 B: 상품 상세 + rank ───────────────────────────────────────────

export function scenarioProductDetail() {
    const productId = ((__VU % 20) + 1); // 1~20 상품 균등 분산

    const beforeMs = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/products/${productId}`);
    const afterMs = Date.now();

    const ok = check(res, {
        '[B] status 200':               (r) => r.status === 200,
        '[B] rank 필드 항상 존재':      (r) => {
            try { return 'rank' in (JSON.parse(r.body).data || {}); } catch { return false; }
        },
        '[B] rank는 숫자 또는 null':    (r) => {
            try {
                const rank = JSON.parse(r.body).data.rank;
                return rank === null || typeof rank === 'number';
            } catch { return false; }
        },
    });

    productDetailFailRate.add(!ok);

    // ZREVRANK 오버헤드를 응답시간으로 간접 측정
    zrevrankLatency.add(afterMs - beforeMs);

    sleep(0.2);
}

// ── 시나리오 C: 이벤트 쓰기 파이프라인 ───────────────────────────────────

export function scenarioWritePipeline() {
    const productId = ((__VU % 20) + 1);
    const headers   = userHeaders(__VU);

    // 조회 이벤트: GET /api/v1/products/{id} → PRODUCT_VIEWED → Kafka → ZSET
    const viewRes = http.get(`${BASE_URL}/api/v1/products/${productId}`);

    const viewOk = check(viewRes, {
        '[C-조회] status 200': (r) => r.status === 200,
    });

    // 좋아요 이벤트: POST /api/v1/likes → PRODUCT_LIKED → Kafka → ZSET
    // 50% 확률로 좋아요 (매번 좋아요하면 conflict 발생)
    let likeOk = true;
    if (Math.random() < 0.5) {
        const likeRes = http.post(
            `${BASE_URL}/api/v1/likes`,
            JSON.stringify({ productId: productId }),
            { headers: { ...headers, 'Content-Type': 'application/json' } }
        );
        // 200(성공), 409(이미 좋아요) 모두 정상
        likeOk = likeRes.status === 200 || likeRes.status === 409;
        check(likeRes, {
            '[C-좋아요] status 200 or 409': (r) => r.status === 200 || r.status === 409,
        });
    }

    writeEventFailRate.add(!viewOk || !likeOk);
    sleep(0.1);
}

// ── 시나리오 D: Hot Product 경합 ──────────────────────────────────────────

export function scenarioHotProduct() {
    const productId = pickProductId(__VU, __ITER);

    // 읽기 (ZREVRANK) + 쓰기 (조회 이벤트) 혼합
    const res = http.get(`${BASE_URL}/api/v1/products/${productId}`);

    const ok = check(res, {
        '[D] status 200':          (r) => r.status === 200,
        '[D] rank 필드 존재':      (r) => {
            try { return 'rank' in (JSON.parse(r.body).data || {}); } catch { return false; }
        },
    });

    hotProductFailRate.add(!ok);

    // Hot 상품(1~3)이면 랭킹 API도 함께 조회 (실제 홈 화면 패턴)
    if (productId <= 3) {
        const rankRes = http.get(`${BASE_URL}/api/v1/rankings?date=${todayDate()}&size=10&page=1`);
        check(rankRes, { '[D] 랭킹 조회 200': (r) => r.status === 200 });
    }

    sleep(0.1);
}

// ── 시나리오 E: 주간 랭킹 조회 ────────────────────────────────────────────

export function scenarioWeeklyRankingRead() {
    const res = http.get(`${BASE_URL}/api/v1/rankings?date=${todayDate()}&type=weekly&size=20&page=1`);

    const ok = check(res, {
        '[E] status 200':           (r) => r.status === 200,
        '[E] data.content 배열':    (r) => {
            try { return Array.isArray(JSON.parse(r.body).data.content); } catch { return false; }
        },
    });

    weeklyRankingFailRate.add(!ok);
    sleep(0.3);
}

// ── 시나리오 F: 월간 랭킹 조회 ────────────────────────────────────────────

export function scenarioMonthlyRankingRead() {
    const res = http.get(`${BASE_URL}/api/v1/rankings?date=${todayDate()}&type=monthly&size=20&page=1`);

    const ok = check(res, {
        '[F] status 200':           (r) => r.status === 200,
        '[F] data.content 배열':    (r) => {
            try { return Array.isArray(JSON.parse(r.body).data.content); } catch { return false; }
        },
    });

    monthlyRankingFailRate.add(!ok);
    sleep(0.3);
}
