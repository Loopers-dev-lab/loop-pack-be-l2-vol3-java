# 9주차 - R9 랭킹 시스템 k6 부하 테스트 시나리오

## TL;DR

R9 랭킹 구현의 **설계 가설을 실부하로 검증**하기 위한 k6 시나리오 모음. JUnit 통합 테스트로는 드러내기 어려운 **처리량 / 경합 / 지연 분포** 를 측정한다. 스크립트는 `k6-scripts/` 폴더에 위치한다.

- **대상 서비스**: `commerce-api` (기본 8080) + `commerce-streamer` (자동 기동, Kafka Consumer)
- **사전 조건**: Docker infra (MySQL, Redis, Kafka) 기동 + 테스트 데이터 seed
- **도구**: [k6](https://k6.io) v0.47+

## 1. 시나리오 목록

### Priority A — 설계 가설 검증 (꼭 권장)

| ID | 제목 | 목적 | 스크립트 |
|---|---|---|---|
| **A-1** | 랭킹 Top-N 읽기 부하 | `ZREVRANGE` + `findVisibleByIds` DB 조회 한계 측정 | [`scenarios/read-top-n.js`](k6-scripts/scenarios/read-top-n.js) |
| **A-2** | 상품 상세 + dailyRank 오버헤드 | `ZREVRANK` 추가 호출이 p99 에 주는 영향 | [`scenarios/product-detail.js`](k6-scripts/scenarios/product-detail.js) |
| **A-3** | 쓰기 파이프라인 스루풋 | Kafka 배치 리스너의 실제 압축 효과 + Consumer lag 한계 | [`scenarios/write-pipeline.js`](k6-scripts/scenarios/write-pipeline.js) |
| **A-4** | Hot Product 경합 | 특정 상품 집중 트래픽 시 Native UPSERT row lock 경합 | [`scenarios/hot-product.js`](k6-scripts/scenarios/hot-product.js) |

### Priority B — 운영 시나리오 검증

| ID | 제목 | 목적 | 스크립트 |
|---|---|---|---|
| **B-1** | 페이지네이션 깊이 | `ZREVRANGE offset` 증가 시 응답 시간 선형 증가 확인 | [`scenarios/pagination-depth.js`](k6-scripts/scenarios/pagination-depth.js) |
| **B-4** | 혼합 워크로드 | 읽기+쓰기 동시 실행 시 경합 및 일관성 확인 | [`scenarios/mixed-workload.js`](k6-scripts/scenarios/mixed-workload.js) |

### 스코프 제외 (문서만)

- **B-2** 자정 전환 구간 — 실시간 시간 조작이 필요해 k6 단독으로는 불편. Testcontainers + Clock 조작으로 JUnit 가 적합.
- **B-3** TTL retention — Redis `TTL` 명령 + 시간 조작이 쉬움. k6 불필요.
- **C-1** 메모리 프로파일 — `redis-cli --bigkeys` 사용 권장.
- **C-2** Redis 장애 복구 — Chaos testing (`docker stop`) 영역.
- **C-3** 가중치 변경 — 배포 롤링 영역.

---

## 2. 각 시나리오 상세

### A-1. 랭킹 Top-N 읽기 부하

**엔드포인트**: `GET /api/v1/rankings?date=YYYYMMDD&page=1&size=20`

**부하 프로필**:
```
ramping-arrival-rate:
  startRate: 50 rps
  timeUnit:  1s
  stages:
    - { target: 100,  duration: 30s }
    - { target: 300,  duration: 1m  }
    - { target: 500,  duration: 2m  }
    - { target: 500,  duration: 2m  }  # hold
    - { target: 0,    duration: 30s }
```

**측정 지표**:
- `http_req_duration{scenario:read}` p50 / p95 / p99
- `http_req_failed` rate
- (외부) MySQL slow log — `findVisibleByIds` 의 QueryDSL 실행 시간
- (외부) Redis `INFO commandstats` — `ZREVRANGE` 평균 호출 시간

**성공 기준**:
- p99 `< 200ms` @ 500rps
- 실패율 `< 0.1%`

**드러낼 인사이트**:
- "`ProductFacade.findVisibleByIds` 캐시 우회가 진짜 무시 가능한가?" — 500rps 지속 시 MySQL conn pool 과 QueryDSL 쿼리 비용이 병목이 되는지 실측.
- 만약 p99 가 튀면 `ProductCacheStore` multi-get 확장을 다시 고려할 근거.

---

### A-2. 상품 상세 + dailyRank 오버헤드

**엔드포인트**: `GET /api/v1/products/{id}`

**부하 프로필**:
```
constant-vus: 50 VUs / 2m
```

**측정 지표**:
- `http_req_duration` with/without dailyRank 비교
- Custom metric: `redis_revrank_calls` (응답 헤더 또는 로그 기반)

**비교 방식**:
1. **Baseline**: 현재 구현 그대로 (`rankingFacade.getDailyRank` 호출)
2. **Counterfactual**: 코드 임시 주석 또는 feature flag 로 `getDailyRank` 를 `null` 반환 stub 으로 전환 후 재측정
3. 두 결과의 p50/p95/p99 차이 = Redis `ZREVRANK` RTT 의 실측 기여도

**성공 기준**:
- 추가 지연 `< 5ms (p95)` — `masterRedisTemplate` 단일 Redis 호출의 일반적 지연

**드러낼 인사이트**:
- 상품 상세는 기존 Redis 캐시(`ProductCacheStore`) 가 주 경로인데, 여기에 ZSET 1회 호출을 얹었을 때 전체 지연 증가를 정량화.
- week9.md §8-10 "상품 상세에 dailyRank 만 추가" 결정의 비용 근거로 사용 가능.

---

### A-3. 쓰기 파이프라인 스루풋 (가장 중요)

**엔드포인트 (트리거)**:
- `GET /api/v1/products/{id}` → `ProductViewedEvent` 발행
- `POST /api/v1/products/{id}/likes` → `ProductLikedEvent(liked=true)` (인증 필요)
- `DELETE /api/v1/products/{id}/likes` → `ProductLikedEvent(liked=false)` (인증 필요)

**부하 프로필**:
```
ramping-arrival-rate:
  startRate: 100 rps
  stages:
    - { target: 500,  duration: 1m }
    - { target: 1000, duration: 2m }
    - { target: 2000, duration: 2m }  # stress
    - { target: 2000, duration: 3m }  # hold
    - { target: 0,    duration: 30s }
```

**요청 혼합**:
- 70% `GET /api/v1/products/{id}` (view)
- 25% `POST .../likes` (like)
- 5%  `DELETE .../likes` (unlike)

**측정 지표**:
- k6 내부: API 응답 시간 (상품 API 자체는 Kafka 발행만 하고 즉시 응답 → 낮을 것)
- **외부 (중요)**: Kafka consumer lag 추이
  ```bash
  watch -n 1 'docker exec kafka kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 \
      --describe --group metrics-group | grep -E "catalog-events|order-events"'
  ```
- **외부**: `product_metrics_hourly` INSERT/UPDATE TPS (`SHOW GLOBAL STATUS LIKE 'Com_insert'` 등)
- **외부**: Redis OPS (`INFO stats` 의 `instantaneous_ops_per_sec`)

**성공 기준**:
- Consumer lag 이 **수렴 또는 감소** (2000rps 부하 끝난 뒤 30초 내 lag 0 복귀)
- 실 응답 p95 `< 50ms` (API 자체는 Kafka 발행만 하므로 빠름)

**드러낼 인사이트**:
- **핵심**: "Kafka 배치 리스너 (poll=3000) 가 실제로 몇 대 1 압축을 달성하는가?"
  - 이벤트 2000 events/s 가 들어오면 poll 당 평균 몇 건이 묶이는지, 그래서 `processBatch` 가 초당 몇 번 호출되는지 streamer 로그에서 확인.
- **병목 지점 발견**: Consumer lag 이 무한 증가하면 병목이 Kafka / MySQL UPSERT / Redis ZADD 중 어디인지 구분 (MySQL Connection pool, Redis throughput, Hibernate flush 등).
- 블로그 소재로 가장 강력 — "단건 리스너였다면 이 스케일에서 이런 일이 일어난다" 를 대비로 제시 가능.

---

### A-4. Hot Product 경합

**목적**: 특정 상품 1개에 조회/좋아요가 몰릴 때, 동일 `(productId, bucketHour)` row 에 대한 Native UPSERT 경합 확인. week9.md §8-6 에서 "`order-events` 파티션 키가 `orderId` 라 동일 상품이 여러 파티션으로 분산될 수 있다" 는 알려진 위험의 실측.

**트래픽 패턴**:
- 70% VU 가 `productId = 1` 에 집중 (view/like)
- 30% VU 가 랜덤 1..1000 상품에 분산

**부하 프로필**:
```
constant-vus: 100 / 3m
```

**측정 지표**:
- 상품 1의 `product_metrics_hourly` 최종 합계 **vs** k6 전송 카운트 (드리프트 확인)
- MySQL `SHOW ENGINE INNODB STATUS` 의 lock wait 로그
- `event_handled` UNIQUE 충돌 count (DB 로그)

**성공 기준**:
- `SUM(view_count) @ 상품1` ≈ 실제 전송 view 수 (±0.5% 오차 허용)
- Deadlock 0건

**드러낼 인사이트**:
- catalog-events 는 파티션 키 = productId 이므로 경합 없음이 예상 — 테스트로 **검증**.
- order-events 는 파티션 키 = orderId → 한 주문에 상품1이 포함되면 다른 주문들의 상품1 과 다른 파티션으로 분산될 수 있음. `Native UPSERT` + `ON DUPLICATE KEY UPDATE` 가 경합을 막아주지만, **throughput 저하** 는 발생. 얼마나 느려지는지 측정.

---

### B-1. 페이지네이션 깊이

**엔드포인트**: `GET /api/v1/rankings?page={1,10,50,100}&size=20`

**부하 프로필**:
- 4개 시나리오 병렬 실행 (`scenarios` 블록에 `exec` 로 분기)
- 각 시나리오 constant-vus 20 / 1m

**측정 지표**:
- `http_req_duration{page:1}`, `{page:10}`, `{page:50}`, `{page:100}` 태그로 구분

**성공 기준**:
- page=100 응답 시간 / page=1 응답 시간 비율 `< 2.0`

**드러낼 인사이트**:
- Redis `ZREVRANGE` 는 `O(log(N) + M)` 이라 깊은 offset 에서도 빠르다고 알려져 있으나, 실제 네트워크 전송 + 직렬화 시간은 offset 과 무관하지 않을 수 있음.
- 실사용자는 1-3페이지만 보므로 깊은 페이지를 굳이 최적화할 필요가 있는지 판단 근거.

---

### B-4. 혼합 워크로드

**시나리오**: 두 exec 동시 실행
- `reader`: 50 VUs, `GET /api/v1/rankings`
- `writer`: 50 VUs, `GET /api/v1/products/{id}` + `POST .../likes`

**측정 지표**:
- `http_req_duration{exec:reader}` vs `{exec:writer}` p99
- Redis CPU 사용률 (쓰기/읽기 경합 여부)

**드러낼 인사이트**:
- `masterRedisTemplate` 단일 사용 구조에서 쓰기/읽기가 같은 Redis 마스터 노드를 공유 — 트래픽이 임계점을 넘으면 읽기 지연이 쓰기 때문에 튀는지 관찰.
- 만약 심각한 열화가 있으면 week9.md §8-7 의 "트래픽 증가 시 읽기/쓰기 템플릿 분리 재검토" 가 언제 필요한지 수치 근거 마련.

---

## 3. 실행 방법

### 사전 준비

```bash
# 1. 인프라 기동
docker-compose -f ./docker/infra-compose.yml up -d

# 2. 애플리케이션 기동
./gradlew :apps:commerce-api:bootRun &
./gradlew :apps:commerce-streamer:bootRun &

# 3. 테스트 데이터 seed (별도 스크립트 또는 수동)
#    - 상품 1000개 생성
#    - 초기 ZSET seed (A-1, A-2 용)
```

### k6 실행

```bash
# 개별 시나리오
k6 run docs/week9/k6-scripts/scenarios/read-top-n.js
k6 run docs/week9/k6-scripts/scenarios/write-pipeline.js

# 환경 변수 오버라이드
BASE_URL=http://localhost:8080 \
  TEST_DATE=20260409 \
  MAX_PRODUCT_ID=1000 \
  k6 run docs/week9/k6-scripts/scenarios/read-top-n.js

# JSON 요약 리포트
k6 run --summary-export=report.json scenarios/read-top-n.js
```

### 외부 지표 관찰 (A-3, A-4 필수)

```bash
# Kafka consumer lag
watch -n 1 'docker exec kafka kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 \
    --describe --group metrics-group'

# Redis OPS
watch -n 1 'docker exec redis-master redis-cli INFO stats | grep ops_per_sec'

# MySQL TPS
mysqladmin -h localhost -u root -p extended-status -i 1 | \
    grep -E "Com_insert|Com_update|Threads_running"
```

---

## 4. 결과 해석 가이드

각 시나리오 실행 후 다음 기록을 남긴다:

```
## [시나리오 ID] 실행 결과
실행 시각: YYYY-MM-DD HH:MM KST
환경: 로컬 Mac M-series / Docker Desktop / Redis single node

| 지표 | 값 |
|---|---|
| p50 | .. ms |
| p95 | .. ms |
| p99 | .. ms |
| http_req_failed | .. % |
| 총 요청 | .. |
| iterations | .. |
| Consumer lag 최고 | .. (A-3 만) |
| Consumer lag 수렴 여부 | YES/NO |

## 관찰
- ...
- ...

## 개선 제안
- ...
```

실행 결과는 별도 `k6-results/` 폴더에 날짜별로 축적하는 것을 권장.

---

## 5. 공통 유틸리티

`k6-scripts/common/` 아래에 공유 헬퍼를 둔다:

- [`config.js`](k6-scripts/common/config.js) — BASE_URL, thresholds, 기본값
- [`auth.js`](k6-scripts/common/auth.js) — 로그인 헤더 생성
- [`seed.js`](k6-scripts/common/seed.js) — 날짜 포맷, 랜덤 productId 선택

---

## 6. 제약과 주의

- **commerce-streamer 는 리스너 컨테이너** — k6 가 직접 호출하지 않음. 쓰기 파이프라인 테스트는 "commerce-api 에 이벤트 발사 → streamer 가 Kafka 로 흡수" 간접 경로.
- **인증** — 좋아요/주문 API 는 `X-Loopers-LoginId` 헤더 필수. 사전에 회원 seed 필요.
- **Testcontainer 환경에서는 k6 실행 불가** — 로컬 또는 CI 환경에 실 인프라가 떠 있어야 함.
- **결과는 환경 의존적** — 로컬 Mac vs CI 머신 vs 운영 서버는 수치가 다르므로, "환경과 함께 기록" 원칙 지킬 것.
