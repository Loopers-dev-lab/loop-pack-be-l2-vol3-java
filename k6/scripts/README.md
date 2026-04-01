# k6 Scripts

세션별 부하 테스트 스크립트 모음. 각 세션의 주제에 맞는 시나리오를 검증한다.

## 디렉토리 구조

```
scripts/
├── session5/    # 읽기 성능 최적화 (인덱스 · 캐시)
├── session6/    # 결제 Resilience (PG 장애 대응)
├── session7/    # 이벤트 기반 아키텍처 + 선착순 쿠폰
└── session8/    # 대기열 시스템 (Redis Sorted Set 폴링)
```

---

## session5/ — 읽기 성능 최적화

> **주제**: 인덱스, 비정규화(`like_count`), L1(Caffeine)+L2(Redis) 캐시  
> **시드**: `k6/seed.sh` (1유저, 5브랜드, 100상품, 재고 1000)

| 스크립트 | 유형 | VU | 시간 | 설명 |
|----------|------|----|------|------|
| `product-steady.js` | Steady State | 50 | 5분 | 기본 부하. 80% 상품 상세(Zipf) + 20% 목록 조회. p95<100ms, p99<200ms 기준 |
| `product-spike.js` | Spike | 10→200→0 | 55초 | Hot Key 집중. 90% 단일 상품(id=1) + 10% 랜덤. 캐시 Stampede 검증 |
| `product-soak.js` | Soak | 100 | 30분 | 장시간 안정성. 메모리 누수, GC, 커넥션 풀 고갈 감지 |
| `product-mixed.js` | Mixed R/W | 50 | 5분 | 읽기 35VU + 목록 10VU + 관리자 쓰기 3VU + 주문 2VU. 캐시 eviction 영향 측정 |
| `product-list-cache.js` | List Cache | 30 | 3분 | 목록 캐시 전략 검증. 60% 캐시 히트(page=0) + 25% 키워드 + 15% 딥 페이징 |

### 검증 포인트

- L2-only vs L1+L2 비교 (p50 응답시간, p95 꼬리 지연)
- 캐시 미스 시 DB fallback 정상 동작
- 관리자 상품 수정 시 캐시 무효화 후 hit rate 회복

---

## session6/ — 결제 Resilience

> **주제**: PG 연동, Timeout/Retry/CircuitBreaker, 분산락, 보상 트랜잭션  
> **시드**: `k6/create-k6-users.sh` (50유저) + `k6/seed.sh` (상품/브랜드)  
> **전제**: PG 시뮬레이터 실행 중, 요청 접수 성공률 60%, 콜백 비동기

| 스크립트 | 유형 | VU | 시간 | 설명 |
|----------|------|----|------|------|
| `payment-baseline.js` | Baseline | 50 | 1분 | PG 40% 실패 환경 기준선. 주문 생성 → 결제 요청 흐름의 성공률/응답시간 측정 |
| `payment-spike.js` | Spike | 10→100→10 | 70초 | 결제 트래픽 급증(블랙프라이데이). 스레드 풀 내성, Bulkhead 효과 검증 |
| `payment-mixed.js` | Mixed | 50 | 1분 | 결제 30VU + 상품 조회 20VU 병행. 결제 부하가 조회 성능에 미치는 영향 분리 |
| `payment-concurrent-pay.js` | Concurrency | 10 | 30초 | 동일 주문에 10VU 동시 결제. 분산락으로 1건만 성공, 9건 409 거부 검증 |
| `payment-cb-lifecycle.js` | CB Lifecycle | 10 | 105초 | CB 상태 전이 3단계: 정상(CLOSED) → PG 다운(OPEN) → 복구(HALF_OPEN→CLOSED). **수동 PG 중단/재시작 필요** |
| `payment-polling-verify.js` | Polling | 30 | 30초 | REQUESTED 상태 결제를 Polling 스케줄러가 최종 처리하는지 검증. 테스트 후 2분 대기 필요 |
| `payment-orphan-recovery.js` | Orphan Recovery | 25 | 25초 | PG 다운 중 생성된 고아 결제(TK=null) 복구. **수동 PG 중단/재시작 필요** |
| `payment-order-expiry.js` | Order Expiry | 10 | 30초 | 미결제 주문 만료 배치 검증. 주문 생성 후 expires_at을 과거로 수동 변경 필요 |
| `payment-compensation.js` | Compensation | 10 | 30초 | 재고 commit 실패 시 보상 트랜잭션 기록 검증. 저재고(2개) 상품 사용 |
| `payment-optimistic-stock.js` | CAS Stock | 40 | 30초 | 저재고 상품에 20VU 동시 주문. CAS 재고 차감 정확성 + Polling 복구 검증 |

### 수동 개입이 필요한 스크립트

| 스크립트 | 필요 작업 | 시점 |
|----------|----------|------|
| `payment-cb-lifecycle.js` | PG 시뮬레이터 중단 → 재시작 | Phase B 시작(30초) / Phase C 시작(75초) |
| `payment-orphan-recovery.js` | PG 시뮬레이터 중단 → 재시작 | 10초 / 25초 |
| `payment-order-expiry.js` | DB에서 `expires_at`을 과거 시간으로 UPDATE | 주문 생성 후 |

---

## session7/ — 이벤트 기반 아키텍처 + 선착순 쿠폰

> **주제**: Transactional Outbox, Kafka, ApplicationEvent 분리, Rush 쿠폰  
> **시드**: `k6/seed-session7.sh` (1000유저, 100상품, 쿠폰 100장, Redis 초기화)  
> **초기화**: `k6/reset-rush-coupon.sh` (쿠폰 스크립트 재실행 시)

| 스크립트 | 유형 | VU | 시간 | 설명 |
|----------|------|----|------|------|
| `event-like-throughput.js` | Throughput | 100 | 3분 | ApplicationEvent 분리 효과 측정. 70% 좋아요 + 30% 취소. p95<50ms 기준 |
| `event-order-isolation.js` | Isolation | 30 | 2분 | AFTER_COMMIT 리스너 실패해도 주문 성공률 100% 보장 검증 |
| `outbox-relay-steady.js` | Outbox Relay | 20 | 5분 | Outbox PENDING→PUBLISHED 안정성. 30% 주문 취소 포함. PENDING 적체 <10건 기준 |
| `kafka-consumer-lag.js` | Consumer Lag | 50 | 5분 | 이벤트 3종(조회 40%, 좋아요 30%, 주문 30%) 동시 발생 시 Consumer 처리 지연 모니터링 |
| `rush-coupon-spike.js` | Spike (핵심) | 500 | 30초 | **500명 동시 요청 → 정확히 100장 발급**, 초과 0건 검증. p95<100ms |
| `rush-coupon-redis-gate.js` | Redis Gate | 1000 | 15초 | Redis DECR 게이트키퍼가 90% 요청을 Kafka 진입 전 차단하는지 검증 |
| `rush-coupon-polling.js` | Polling | 200 | 1분 | 비동기 쿠폰 발급 결과 polling. 요청 → requestId → 결과 조회(최대 10회, 0.5초 간격) |
| `full-mixed-load.js` | E2E Mixed | 10→100→10 | 10분 | 전체 시스템 운영 시뮬레이션. 조회 60% + 좋아요 15% + 주문 15% + 쿠폰 10% |

### 실행 순서 (권장)

```bash
# 1. 시드 데이터 생성
bash k6/seed-session7.sh

# 2. 이벤트 격리 검증 (Step 1)
k6 run k6/scripts/session7/event-like-throughput.js
k6 run k6/scripts/session7/event-order-isolation.js

# 3. Outbox + Kafka 파이프라인 (Step 2)
k6 run k6/scripts/session7/outbox-relay-steady.js
k6 run k6/scripts/session7/kafka-consumer-lag.js

# 4. 선착순 쿠폰 (Step 3) — 실행 전 reset 필수
bash k6/reset-rush-coupon.sh
RUSH_COUPON_ID=1 k6 run k6/scripts/session7/rush-coupon-spike.js

bash k6/reset-rush-coupon.sh
RUSH_COUPON_ID=1 k6 run k6/scripts/session7/rush-coupon-redis-gate.js

# 5. 전체 운영 시뮬레이션
bash k6/reset-rush-coupon.sh
RUSH_COUPON_ID=1 k6 run k6/scripts/session7/full-mixed-load.js
```

---

## session8/ — 대기열 시스템

> **주제**: Redis Sorted Set 기반 대기열, 입장 토큰, Polling 응답 성능  
> **시드**: `session8/seed-session8.sh` (10000유저 생성)  
> **초기화**: `session8/reset-queue.sh` (Redis 대기열/토큰 키 삭제)

| 스크립트 | 유형 | VU | 시간 | 설명 |
|----------|------|----|------|------|
| `queue-polling-load.js` | Load | 1000 | ~3분 | 대기열 진입(처음 30초) → Polling 2분 + 상품 조회 50VU 혼합. p95<50ms, 에러율<0.1% 기준 |
| `queue-polling-scale.js` | Scale | 500~SCALE | ~3분 | Polling VU를 ramping으로 점진 증가. 대기열 API의 수평 확장성 측정. p95<100ms 기준 |

### 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `BASE_URL` | `http://localhost:8080` | API 서버 주소 |
| `USERS` | `1000` | `queue-polling-load.js` 진입/Polling 동시 사용자 수 |
| `SCALE` | `500` | `queue-polling-scale.js` ramping 목표 VU |

### 실행 순서

```bash
# 1. 시드 데이터 생성 (10000유저)
bash k6/scripts/session8/seed-session8.sh

# 2. 대기열 초기화
bash k6/scripts/session8/reset-queue.sh

# 3. 부하 테스트 실행
k6 run k6/scripts/session8/queue-polling-load.js

# 4. 스케일 테스트 (SCALE 조정 가능)
bash k6/scripts/session8/reset-queue.sh
SCALE=1000 k6 run k6/scripts/session8/queue-polling-scale.js
```

### 검증 포인트

- Polling p95 < 50ms (캐시 없는 Redis 직접 조회)
- 대기열 진입 멱등성 (동일 유저 중복 진입 처리)
- Polling 부하가 상품 조회 API 응답시간에 미치는 영향
- SCALE 증가 시 Redis 메모리/CPU 사용량 (Grafana)
