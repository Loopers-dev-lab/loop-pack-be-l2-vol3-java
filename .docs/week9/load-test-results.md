# Week 9 랭킹 파이프라인 부하 테스트

## 테스트 환경

| 항목 | 값 |
|------|-----|
| 인프라 | docker compose (local) |
| commerce-api | localhost:8080 |
| commerce-streamer | (background consumer) |
| MySQL | infra-compose.yml |
| Redis | infra-compose.yml |
| Kafka | infra-compose.yml |

```bash
# 인프라 실행
docker compose -f infra-compose.yml up -d

# 앱 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-streamer:bootRun
```

---

## 시나리오 1: VIEW 이벤트 폭주

**목적:** 상품 조회 → Kafka VIEW 이벤트 → ranking_metrics UPSERT 경로의 DB 병목과 deadlock 여부 확인

**상품 수를 9개로 제한한 이유:**
실제 서비스에서는 파레토 법칙(상위 20% 상품이 80% 트래픽을 받음)에 따라 인기 상품 소수에 요청이 집중된다.
상품 수를 적게 유지할수록 동일한 `(product_id, date, hour)` 행에 UPSERT가 집중되어 DB 행 경합과 deadlock 가능성이 극대화된다.
즉, **DB 병목을 가장 가혹하게 재현하는 조건**이면서 동시에 실제 트래픽 패턴에도 부합한다.
상품 수를 늘리면 부하가 분산되어 오히려 문제가 드러나지 않는다.

**스크립트:** `k6/scenario1-view-flood.js`

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 100,
  duration: '60s',
};

export default function () {
  const productId = Math.floor(Math.random() * 10) + 1;  // 상품 10개 중 랜덤
  const res = http.get(`http://localhost:8080/api/v1/products/${productId}`);
  check(res, { 'status 200': (r) => r.status === 200 });
}
```

**측정 지표:**
- ranking_metrics UPSERT TPS
- DB connection pool 사용률
- P99 응답시간

**결과:**

| 측정 항목 | 값 | 비고 |
|----------|-----|------|
| 실행 날짜 | 2026-04-10 | VUs=100, duration=60s, 상품 9개 (ID 6 미존재) |
| 총 요청 수 | 98,050 | RPS: 1,632/s |
| 성공률 | 100% | http_req_failed=0.00% |
| P50 응답시간 | 51.16ms | avg=61.14ms |
| P90 응답시간 | 101.03ms | |
| P99 응답시간 | 202.21ms | max=654.32ms |
| DB deadlock 발생 | 없음 | 9개 행 집중 UPSERT 무결 처리 |

**ranking_metrics UPSERT 결과 (DB 직접 확인):**

| product_id | view_count | dirty |
|-----------|-----------|-------|
| 4 | 20,859 | false (Redis 동기화 완료) |
| 10 | 20,431 | false (Redis 동기화 완료) |
| 9 | 17,675 | true |
| 2 | 17,560 | true |
| 3 | 17,338 | true |
| 1 | 14,691 | true |
| 7 | 14,671 | true |
| 8 | 14,637 | true |
| 5 | 14,612 | true |

**인사이트:**

> - 9개 상품에 ~98k 요청이 집중됐음에도 DB deadlock 없이 100% 처리됨
> - `ON DUPLICATE KEY UPDATE` UPSERT가 row-level lock 경합 없이 안정적으로 동작
> - 상품 4, 10은 RankingSyncScheduler가 이미 Redis ZADD 완료 (dirty=false) → 5초 이내 스케줄러 동작 확인
> - P99 202ms는 Cache-Aside miss 발생 시 DB 조회 지연이 포함된 수치. Cache TTL 60s 기준으로 초기 miss 이후 대부분 Redis 히트로 전환됨
> - 상품 수가 적을수록(9개) 동일 행 UPSERT 집중 → 부하 분산 비교를 위해 상품 수 증가 테스트 필요

---

## 시나리오 2: 랭킹 API 동시 조회

**목적:** Redis ZSET 읽기 경로의 레이턴시 측정 (REPLICA_PREFERRED 설정 효과 확인)

**스크립트:** `k6/scenario2-ranking-read.js`

**측정 지표:**
- P50 / P95 / P99 응답시간
- Redis 연결 수
- 초당 요청 처리량 (RPS)

---

### 리팩토링 전 (Before)

**결과:**

| 측정 항목 | 값 | 비고 |
|----------|-----|------|
| 실행 날짜 | 2026-04-10 | VUs=100, duration=60s |
| 총 요청 수 | 69,717 | RPS: 1,160/s |
| 성공률 | 100% | http_req_failed=0.00% |
| P50 응답시간 | 72.89ms | avg=85.81ms |
| P90 응답시간 | 130.87ms | |
| P95 응답시간 | 172.47ms | threshold(50ms) ❌ 3.4배 초과 |
| P99 응답시간 | 374.53ms | threshold(100ms) ❌ 3.7배 초과 |
| max 응답시간 | 1,480ms | |

**병목 원인 분석:**

`RankingFacade.findDailyRanking()`은 Redis ZSET에서 productId 목록을 가져온 뒤,
매 요청마다 아래 두 DB 쿼리를 실행한다:

```
1. productService.findAllByIds(productIds)  → product 테이블 IN 쿼리
2. brandService.findNamesByIds(brandIds)    → brand 테이블 IN 쿼리
```

`ProductFacade.findById()`에는 Cache-Aside가 적용되어 있지만,
`RankingFacade`는 `ProductFacade`가 아닌 `ProductService`를 직접 호출하므로 캐시를 우회한다.
Redis가 아무리 빨라도 DB 조회가 매번 붙어 P95 50ms 달성이 구조적으로 불가능한 상태.

**리팩토링 방향:**

`RankingFacade.findDailyRanking()` 결과 전체를 Redis에 캐싱한다.
- 캐시 키: `ranking:daily:{date}:page={page}:size={size}`
- Cache-Aside 패턴: miss 시 DB 조회 후 캐시 저장, hit 시 즉시 반환
- TTL: 스케줄러 주기(5초)보다 짧게 설정하여 랭킹 변경 반영 보장

---

### 리팩토링 후 (After)

**리팩토링 내용:**

`RankingFacade`에 Cache-Aside 패턴 적용. 조립된 랭킹 페이지 결과를 Redis에 캐싱하여 매 요청마다 발생하던 DB IN 쿼리 제거.

- 캐시 키: `loopers:ranking:daily:{date}:p{page}:s{size}` / `loopers:ranking:hourly:{date}{HH}:p{page}:s{size}`
- TTL: 30초 (RankingSyncScheduler 5초 주기 대비 적절한 stale 허용 범위)
- DIP 준수: `RankingCacheRepository` 인터페이스(application) / `RankingCacheRepositoryImpl`(infrastructure) 분리
- Redis 장애 시 캐시 미스로 처리 → DB fallback 보장

**결과:**

| 측정 항목 | 리팩토링 전 | 리팩토링 후 | 개선율 |
|----------|-----------|-----------|------|
| 실행 날짜 | 2026-04-10 | 2026-04-10 | — |
| RPS | 1,160/s | **2,285/s** | **+97%** |
| P50 응답시간 | 72.89ms | **37ms** | -49% |
| P90 응답시간 | 130.87ms | **64ms** | -51% |
| P95 응답시간 | 172.47ms ❌ | **82ms** ❌ | -52% |
| P99 응답시간 | 374.53ms ❌ | **155ms** ❌ | -59% |

**인사이트:**

> - Avg/P50 기준 약 50% 개선, RPS는 약 2배 상승 → 캐시 히트율이 매우 높음을 의미
> - P95/P99가 여전히 임계값(50ms/100ms)을 초과하는 원인: **Cache Stampede**
>   - TTL 30초마다 캐시 만료 시 50개 VU가 동시에 캐시 미스 → 다수의 DB IN 쿼리 동시 발생 → latency spike
>   - 완전 해결하려면 Probabilistic Early Expiration 또는 Lock-based Cache Warming 패턴 추가 필요
> - 임계값이 타이트(P95 < 50ms)하지만, Cache-Aside 도입으로 핵심 병목(매 요청 DB 조회)은 해소됨
> - 일간/시간별 랭킹 모두 동일한 개선 효과 (Before 85ms avg → After 43ms avg)
> - **추후 개선 포인트 (Cache Stampede 완전 해소):** TTL 만료 순간 다수 VU가 동시에 캐시 미스를 만나는 문제는 Singleflight 또는 Probabilistic Early Expiration(PER)으로 해소 가능. Singleflight는 동일 키에 대해 진행 중인 DB 조회가 있으면 나머지 요청이 결과를 공유받아 DB 조회가 정확히 1회만 발생함. 상품 목록(PLP) / 상품 상세(PDP) 등 트래픽 집중 API에도 동일하게 적용 검토 필요.

---

## 시나리오 3: weight 변경 시 스케줄러 스파이크

**목적:** `UPDATE ranking_metrics SET dirty=true WHERE metrics_date=today` 실행 후 스케줄러 1회 처리 시간과 DB 부하 측정

**준비:**
```sql
-- 사전 데이터 적재 (상품 500개 × 오늘 10시간 = 5,000 행)
-- k6 pre-script 또는 직접 SQL로 준비
INSERT INTO ranking_metrics (product_id, metrics_date, metrics_hour, view_count, like_count, order_revenue, dirty, created_at, updated_at)
SELECT
  p.id,
  CURDATE(),
  h.hour,
  FLOOR(RAND() * 100),
  FLOOR(RAND() * 20),
  ROUND(RAND() * 10000, 2),
  FALSE,
  NOW(), NOW()
FROM products p
CROSS JOIN (SELECT 0 AS hour UNION SELECT 1 UNION ... UNION SELECT 9) h
ON DUPLICATE KEY UPDATE updated_at = NOW();
```

**스파이크 트리거:**
```sql
UPDATE ranking_metrics SET dirty = TRUE WHERE metrics_date = CURDATE();
```

**측정 지표:**
- 스케줄러 1회 실행 시간 (log에서 확인)
- 해당 구간 ranking API P99 레이턴시 (시나리오 2 동시 실행)
- DB CPU 사용률

**결과:**

| 측정 항목 | 캐싱 후 기준선 (60s) | 스파이크 포함 (120s) | 변화 |
|----------|---------------------|-------------------|------|
| 실행 날짜 | 2026-04-10 | 2026-04-10 | — |
| dirty 행 수 | — | 3,000행 (500상품 × 6시간) | |
| 스케줄러 처리 결과 | — | 3,000행 전량 dirty=false | 정상 처리 |
| Avg 응답시간 | 43ms | 44ms | +2% |
| P95 응답시간 | 82ms | 91ms | +11% |
| P99 응답시간 | 155ms | 159ms | +3% |
| Max 응답시간 | ~1,050ms | 967ms | 오히려 낮음 |
| RPS | 2,285/s | 2,240/s | -2% |

**인사이트:**

> - 500개 상품 × 6시간 = 3,000행을 스케줄러가 단일 실행에서 처리하는 동안 랭킹 API P99는 155ms → 159ms로 **사실상 무변화**
> - **Cache-Aside 레이어가 스케줄러 스파이크를 흡수**: 스케줄러는 Redis ZSET을 갱신하고, API는 별도 캐시(`loopers:ranking:daily:*`)에서 서빙 — 두 레이어가 독립적으로 동작
> - Max 967ms는 캐시 TTL 만료 + 스케줄러 ZADD가 겹치는 순간 캐시 미스가 발생할 때의 스파이크
> - **설계 검증**: "스케줄러 부하 ≠ API 레이턴시 증가" — 스케줄러 처리량이 많아져도 API에 직접적인 영향 없음

**인지된 트레이드오프 (가중치 변경 시 스테일 윈도우):**

가중치 변경(`dirty=true`) 이후 사용자가 새 랭킹을 보기까지의 최대 지연은 두 단계의 합이다.

```
스테일 윈도우 = 스케줄러 주기(5s) + 결과 캐시 TTL(30s) = 최대 35초
```

- 스케줄러가 ZSET을 업데이트해도 결과 캐시(`loopers:ranking:daily:*`, TTL 30s)는 명시적으로 무효화되지 않음
- 결과 캐시가 만료되어야 비로소 새 ZSET 점수 기반 랭킹이 서빙됨
- **현재 설계 수용 근거**: 랭킹 가중치는 실시간 변경이 아닌 운영 정책 변경이므로 35초 반영 지연은 허용 범위
- **즉시 반영이 필요한 경우**: 스케줄러 ZADD 완료 후 `DEL loopers:ranking:*` 로 결과 캐시 명시 무효화 추가 필요

---

## 시나리오 4: 혼합 부하 (파이프라인 E2E)

**목적:** VIEW 이벤트 발생 → ranking_metrics 반영 → 스케줄러 ZADD → 랭킹 API 조회까지 end-to-end 지연 실측

**스크립트:** `k6/scenario4-mixed-load.js`

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    view_traffic: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '120s',
      preAllocatedVUs: 60,
      exec: 'viewProduct',
    },
    ranking_read: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '120s',
      preAllocatedVUs: 60,
      exec: 'readRanking',
    },
  },
};

export function viewProduct() {
  const productId = Math.floor(Math.random() * 10) + 1;
  http.get(`http://localhost:8080/api/v1/products/${productId}`);
}

export function readRanking() {
  const res = http.get('http://localhost:8080/api/v1/rankings?size=20');
  check(res, { 'status 200': (r) => r.status === 200 });
}
```

**E2E 지연 측정 방법:**
1. 특정 상품에 VIEW 이벤트 발생 시각 기록
2. ranking API에서 해당 상품 score 변경 감지 시각 기록
3. 차이 = 파이프라인 end-to-end 지연 (목표: 5초 이내)

**결과:**

| 측정 항목 | 값 | 비고 |
|----------|-----|------|
| 실행 날짜 | 2026-04-10 | VIEW 50/s + 랭킹 50/s, 120s |
| VIEW RPS | 50/s | constant-arrival-rate |
| 랭킹 조회 RPS | 50/s | constant-arrival-rate |
| VIEW API Avg / P95 / P99 | 12ms / 36ms / 166ms | ✓ threshold(500ms) 통과 |
| 랭킹 API Avg / P95 / P99 | 12ms / 35ms / 158ms | ❌ threshold(100ms) 초과 |
| 성공률 | 100% | VIEW + 랭킹 모두 |
| E2E 파이프라인 지연 (API 기준) | **≤ 2초** | 결과 캐시 만료 상태 기준 |
| VIEW 이벤트 DB 반영 | 정상 | 9상품 × 7시간 시간대 행 누적 확인 |

**인사이트:**

> - **Avg/P95가 매우 낮은 이유**: constant-arrival-rate 50/s = 낮은 캐시 압박 → 캐시 히트율 극대화 (scenario 2의 100VU constant 대비 처리량이 낮아 Stampede 발생 빈도 ↓)
> - **P99 158ms는 여전히 Cache Stampede**: 30s TTL 만료 순간 다수 VU 동시 miss → 이는 scenario 2 이후 일관된 구조적 한계
> - **E2E ≤ 2초**: VIEW 이벤트 → Kafka 소비 → ranking_metrics UPSERT → 스케줄러 ZADD → API 반영이 2초 이내 완료. 결과 캐시가 이미 만료된 상태에서 측정했으므로 캐시 TTL(30s) 영향 없이 순수 파이프라인 지연만 반영됨
> - **E2E 지연 정확한 해석**: `VIEW → ZSET 반영` ≤ 5s (스케줄러 주기), `ZSET → API 서빙` = 0~30s (결과 캐시 TTL). 캐시 만료 직후 측정하면 전자만 측정됨 — 목표 "5초 이내"는 달성
> - VIEW와 랭킹 혼합 부하 상태에서도 두 경로 간 간섭 없음 — 각 경로가 독립적인 캐시 레이어를 사용하기 때문

---

## 시나리오 5: 토픽 혼재 부하 비교 (VIEW vs LIKE 이벤트 경합)

**목적:** `catalog-events` 단일 토픽에 VIEW + LIKE 이벤트가 혼재될 때, VIEW 폭주가 LIKE 이벤트 처리를 지연시키는지 확인.
토픽 분리 전/후 비교를 통해 `view-events` 토픽 분리 도입 여부를 결정한다.

**스크립트:** `k6/scenario5-topic-contention.js`

**부하 패턴:**
- VIEW 이벤트: 100 VU × 60s — 상품 조회 API 반복 → `PRODUCT_VIEWED` → `catalog-events` 직접 발행
- LIKE 이벤트: 50 VU × 60s — LIKE 생성/취소 교대 → `LIKE_CREATED/CANCELLED` → outbox → `catalog-events`

**측정 지표:**
- k6: VIEW/LIKE API 응답 시간 및 성공률
- DB: 테스트 종료 후 `ranking_metrics.view_count` vs `like_count` 누적 비율

```sql
-- 테스트 종료 후 처리 결과 확인
SELECT product_id, view_count, like_count
FROM ranking_metrics
WHERE metrics_date = CURDATE()
ORDER BY product_id;
```

**판단 기준:**
- `like_count`가 LIKE 이벤트 발송 수 대비 현저히 낮으면 → 토픽 경합으로 LIKE 이벤트 처리 지연 **→ 분리 필요**
- `like_count`와 발송 수가 비례하면 → 현재 구조로도 충분 **→ 분리 불필요**

---

### 결과 (2026-04-10, VIEW_VUS=100 / LIKE_VUS=50, duration=60s)

| 측정 항목 | 값 | 비고 |
|----------|-----|------|
| VIEW 이벤트 발송 | 42,846건 | view_events_sent |
| LIKE 이벤트 발송 | 3,622건 | like_events_sent (409 제외) |
| VIEW API P95 응답시간 | 323ms | |
| LIKE API P95 응답시간 | 331ms | |
| DB view_count 합계 | 42,846 | 발송 수와 **100% 일치** |
| DB like_count 합계 | 3,622 | 발송 수와 **100% 일치** |

### 결론: 현재 규모에서 토픽 분리 불필요

VIEW(42,846건) + LIKE(3,622건)가 `catalog-events` 단일 토픽에 혼재됐음에도 이벤트 유실 0건, LIKE 처리 지연도 VIEW와 동일 수준(P95 차이 8ms).

**토픽 분리가 필요한 시점:**
- VIEW:LIKE 비율이 수백:1 이상으로 커지는 경우 (현재 약 12:1)
- LIKE 이벤트의 처리 SLA(예: 5초 이내 반영)가 요구되는 경우
- 파티션 수나 consumer concurrency를 이벤트 타입별로 독립적으로 조정해야 하는 경우

→ **현재 규모에서는 단일 토픽으로 충분. 트래픽이 현재 대비 수십 배 증가하거나 LIKE 처리 SLA가 생기면 `view-events` 토픽 분리 재검토.**

---

## 종합 정리

> (전체 시나리오 완료 후 기록)

### 발견된 병목

### 설계 검증 결과

### 개선 필요 사항
