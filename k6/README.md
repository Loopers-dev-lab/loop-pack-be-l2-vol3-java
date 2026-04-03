# k6 부하 테스트

k6 기반 부하 테스트 스크립트. 세션별로 분류되어 있으며, 각 세션의 과제 주제에 맞는 시나리오를 검증한다.

## 디렉토리 구조

```
k6/
├── lib/
│   └── helpers.js              # 공통 유틸 (Zipf 분포, 인증 헤더, 응답 검증)
├── scripts/
│   ├── session5/               # 읽기 성능 최적화 (인덱스 · 캐시)
│   ├── session6/               # 결제 Resilience (PG 장애 대응)
│   └── session7/               # 이벤트 기반 아키텍처 + 선착순 쿠폰
├── seed.sh                     # Session 5 시드 데이터 (1유저, 5브랜드, 100상품)
├── seed-session7.sh            # Session 7 시드 데이�� (1000유저, 쿠폰, Redis 초기화)
├── create-k6-users.sh          # Session 6 테스트 유저 50명 생성
└── reset-rush-coupon.sh        # 선착순 쿠폰 상태 초기화 (재실행용)
```

## 실행 환경

```bash
# 인프라 기동
docker compose -f docker/infra-compose.yml up -d

# 애플리케이션 기동
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew :apps:commerce-api:bootRun

# k6 실행 (예시)
k6 run k6/scripts/session5/product-steady.js
```

> **WSL2 주의**: WSL2 환경에서는 네이티브 Linux 대비 네트워크 RTT 2~5배, 디스크 I/O 3~10배 느리다.
> 측정값은 절대 수치가 아닌 **상대적 경향**으로 해석해야 한다.

---

## lib/ — 공통 유틸리티

### helpers.js

| 함수 | 설명 |
|------|------|
| `getProductIdZipf(maxId)` | Zipf 분포로 상품 ID 선택 (상위 20% 상품에 80% 트래픽 집중) |
| `authHeaders(loginId, loginPw)` | 고객 인증 헤더 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`) |
| `adminHeaders()` | 관리자 인증 헤더 (`X-Loopers-Ldap: loopers.admin`) |
| `checkResponse(res, name)` | 응답 검증 (status 200 + meta.result === SUCCESS) |

---

## scripts/session5/ — 읽기 성능 최적화

> **주제**: 인덱스, 비정규화(`like_count`), L1(Caffeine)+L2(Redis) 캐시
> **시드**: `seed.sh` (1유저, 5브랜드, 100상품, 재고 1000)

| 스크립트 | 유형 | VU | 시간 | 설명 |
|----------|------|-----|------|------|
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

## scripts/session6/ — 결제 Resilience

> **주제**: PG 연동, Timeout/Retry/CircuitBreaker, 분산락, 보상 트랜잭션
> **시드**: `create-k6-users.sh` (50유저) + `seed.sh` (상품/브랜드)
> **전제**: PG 시뮬레이터 실행 중, 요청 접수 성공률 60%, 콜백 비동기

| 스크립트 | 유형 | VU | 시간 | 설명 |
|----------|------|-----|------|------|
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

### 검증 포인트

- Timeout을 FAILED가 아닌 REQUESTED로 유지 (불확실 상태 보존)
- CB OPEN 시 503 즉시 반환 (fail-fast)
- 분산락으로 동일 주문 이중결제 차단
- Polling이 REQUESTED → SUCCESS/FAILED 최종 처리

---

## scripts/session7/ — 이벤트 기반 아키텍처 + 선착순 쿠폰

> **주제**: Transactional Outbox, Kafka, ApplicationEvent 분리, Rush 쿠폰
> **시드**: `seed-session7.sh` (1000유저, 100상품, 쿠폰 100장, Redis 초기화)
> **초기화**: `reset-rush-coupon.sh` (쿠폰 스크립트 재실행 시)

| 스크립트 | 유형 | VU | 시간 | 설명 |
|----------|------|-----|------|------|
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
k6 run k6/scripts/session7/rush-coupon-spike.js

bash k6/reset-rush-coupon.sh
k6 run k6/scripts/session7/rush-coupon-redis-gate.js

# 5. 전체 운영 시뮬레이션
bash k6/reset-rush-coupon.sh
RUSH_COUPON_ID=1 k6 run k6/scripts/session7/full-mixed-load.js
```

### 검증 포인트

- `issued_count == 100` (정확), 초과 발급 0건
- Outbox PENDING 적체 <10건, PUBLISHED 전환율 >99%
- AFTER_COMMIT 리스너 실패가 주문 트랜잭션에 영향 없음
- Redis DECR이 Kafka 진입량을 쿠폰 수량 수준으로 제한

---

## 시드 데이터 스크립트

| 스크립트 | 대상 세션 | 생성 데이터 |
|----------|----------|-----------|
| `seed.sh` | Session 5 | 1 유저, 5 브랜드, 100 상품 (브랜드당 20개, 재고 1000) |
| `create-k6-users.sh` | Session 6 | 50 테스트 유저 (k6user1~k6user50) |
| `seed-session7.sh` | Session 7 | 1000 유저, 5 브랜드, 100 상품 (재고 10000), 쿠폰 1장 (100개), Redis 잔여수량 초기화 |
| `reset-rush-coupon.sh` | Session 7 | 쿠폰 issued_count 리셋, user_coupons/issue_result 삭제, Redis dedup 키 정리 |
