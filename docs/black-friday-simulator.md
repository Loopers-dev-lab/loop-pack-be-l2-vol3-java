# 블랙프라이데이 주문 대기열 시뮬레이터

> 작성 목적: 주문 대기열 시스템 학습 전, 문제를 먼저 재현하고 개선 효과를 수치로 검증한다.

---

## 배경 및 목적

블랙프라이데이처럼 짧은 시간에 주문이 폭발적으로 몰리는 상황을 직접 재현해,
현재 시스템이 어디서 무너지는지 수치로 확인한다.

**학습 흐름**

```
[Phase 1] 한계점 탐색    →  현재 코드가 몇 명까지 버티는지 P99 기준으로 측정
[Phase 2] 블프 spike 재현 →  한계를 넘었을 때 실제로 무슨 일이 생기는지 확인
          ↓
    [다음 주] 주문 대기열 시스템 구현
          ↓
[Phase 3] 개선 검증       →  같은 부하로 재실행, 수치 비교
```

---

## 현재 코드 분석 (왜 문제가 생기는가)

### 주문 트랜잭션 흐름

```
POST /api/v1/orders
  └── OrderFacade.createOrder()  @Transactional  ← 트랜잭션 시작
        ├── userService.authenticate()            ← DB 조회
        ├── productService.getProducts()          ← DB 조회
        ├── productService.getBrands()            ← DB 조회
        ├── userCouponService.validateAndUse()    ← DB 조회 + UPDATE
        └── orderService.createOrder()
              └── StockDeductionService.deductAll()  @Transactional
                    └── findByProductIdWithLock()    ← PESSIMISTIC_WRITE 락 획득
                          └── stock.deduct()         ← 재고 차감
                    └── 주문/주문아이템 INSERT
                                                     ← 트랜잭션 커밋 (락 해제)
```

**핵심 코드 위치**
- `StockDeductionService` — 비관적 락 획득 및 재고 차감
- `StockJpaRepository` — `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- `OrderFacade.createOrder()` — 전체 주문 흐름 오케스트레이션 (`@Transactional`)

### 현재 방어 메커니즘

| 메커니즘 | 역할 | 한계 |
|---|---|---|
| `PESSIMISTIC_WRITE` 락 | 재고 정합성 보장 (oversell 방지) | 동시 요청 모두 직렬화 → 대기 시간 급증 |
| productId 정렬 후 락 획득 | 데드락 방지 | — |
| `CoreException(BAD_REQUEST)` | 재고 부족 시 400 반환 | — |

### 문제: 정합성은 지키지만 성능은 못 지킨다

```
트랜잭션 1 ─── [락 획득] ───────────────── [커밋/락 해제]
트랜잭션 2 ─────────────── [락 대기 중...] ─────────────
트랜잭션 3 ─────────────── [락 대기 중...] ─────────────
...N명 동시
```

N이 커질수록:
- DB 커넥션이 모두 락 대기 상태로 점유됨
- HikariCP 커넥션 풀 고갈 → 새 요청은 커넥션조차 못 얻고 에러
- 락 대기 타임아웃 → 재고가 있어도 주문 실패
- **결과**: 재고 50개인데 실제 성공 주문이 50개 미만

---

## 시뮬레이터 구성

스크립트 2개를 순서대로 실행한다.

| 스크립트 | 목적 | 특징 |
|---|---|---|
| `k6/breakpoint.js` | 한계점 탐색 | VU를 단계적으로 증가시켜 P99가 꺾이는 지점 찾기 |
| `k6/black-friday.js` | 블프 재현 | 5초 만에 200명 spike, 무슨 일이 생기는지 확인 |

---

## Phase 1: 한계점 탐색 (`breakpoint.js`)

### 목표
"P99 < 2000ms 를 유지할 수 있는 최대 동시 요청 수"를 찾는다.

### VU 증가 단계

| 단계 | VU 수 | 유지 시간 | 예상 상태 |
|---|---|---|---|
| 1 | 10 | 30s | 정상 (베이스라인) |
| 2 | 30 | 30s | 정상 |
| 3 | 50 | 30s | 주의 |
| 4 | 100 | 30s | **한계점 진입 예상** ⚠️ |
| 5 | 150 | 30s | 한계 초과 예상 |
| 6 | 200 | 30s | 블랙프라이데이 수준 |
| 7 | 300 | 30s | 과부하 확인 |

각 단계 전환 시 5초 ramp (급격한 spike 배제, 부하 자체에만 집중)

### 한계점 판단 기준

다음 중 하나라도 만족하면 그 VU 수를 "한계점"으로 기록:
- P99 > 2000ms (응답시간 SLO 위반)
- 비정상 에러율 > 5% (500, 타임아웃 등 재고 부족이 아닌 에러)
- `hikari.connections.pending` > 0 (Grafana에서 확인)

### 기대 결과 예시

```
 10 VU → P99:   120ms   비정상 에러: 0%   ✅
 30 VU → P99:   340ms   비정상 에러: 0%   ✅
 50 VU → P99:   890ms   비정상 에러: 0%   ✅
100 VU → P99:  2800ms   비정상 에러: 2%   ⚠️ 한계점
150 VU → P99:  7200ms   비정상 에러: 18%  ❌
200 VU → P99: 타임아웃   비정상 에러: 35%  ❌
```
*(실제 수치는 실행 환경에 따라 다름)*

### 상품 설정

재고 고갈로 인한 노이즈 제거를 위해 **재고 10,000개** 상품을 사용한다.
→ 재고 부족이 아닌 순수 부하 압박에 의한 P99 변화만 관찰

---

## Phase 2: 블랙프라이데이 재현 (`black-friday.js`)

### 목표
한계점을 알고 있는 상태에서, 그 한계를 **5초 만에 초과**했을 때 무슨 일이 생기는지 확인한다.

### 시나리오

```
[한정판 상품]  재고: 50개
[동시 요청]    200명 (한계점 초과 수준)
[이상적 결과]  50명 성공, 150명 재고 부족 400
[예상 실제]    50명 미만 성공 + 다수 500/타임아웃
```

### 트래픽 패턴

```
 0s →  5s : 0 → 200 VU   (정각 몰림: 급격한 spike)
 5s → 35s : 200 VU        (지속 압박)
35s → 40s : 200 → 0 VU   (종료)
```

### 결과 분류

| 응답 | 분류 | 의미 |
|---|---|---|
| 201 Created | `order_success` | 정상 성공 (재고 50개 한도 내) |
| 400 + "재고가 부족합니다" | `stock_exhausted` | 예상된 실패 (정상) |
| 400 기타 / 500 / 타임아웃 | `sys_error` | **비정상 에러 — 이게 문제** |

---

## 커스텀 메트릭

두 스크립트 공통으로 아래 메트릭을 수집한다.

| 메트릭 | 종류 | 설명 |
|---|---|---|
| `order_duration` | Trend | 주문 API 응답시간 (P50/P95/P99 분석용) |
| `order_success` | Counter | 성공한 주문 수 |
| `stock_exhausted` | Counter | 재고 부족으로 실패한 수 (예상된 실패) |
| `sys_error` | Counter | 비정상 에러 수 (500, 타임아웃 등) |
| `abnormal_error_rate` | Rate | 비정상 에러 비율 |

---

## 웹 대시보드 구성

### 선택: InfluxDB + Grafana

**이유**: Spring Boot 메트릭(DB 커넥션 풀 상태)과 k6 메트릭(P99, 에러율)을 같은 화면에서 보면 인과관계를 한눈에 확인할 수 있다.

```
k6 ──(--out influxdb)──→ InfluxDB (8086) ──→ Grafana (3000)
                                                     ↑
Spring Boot actuator ──→ Prometheus (9090) ──────────┘
```

### Grafana 대시보드 구성

```
┌──────────────────────────────────────────────────────┐
│              블랙프라이데이 시뮬레이터                   │
├──────────────────────┬───────────────────────────────┤
│   k6 메트릭          │   Spring Boot 메트릭            │
│   (InfluxDB)         │   (Prometheus)                 │
│                      │                                │
│  VU 수 추이          │  hikari.connections.active      │
│  P99 응답시간 추이    │  hikari.connections.pending     │
│  비정상 에러율        │  http_server_requests           │
│  성공/재고부족/에러   │  jvm.threads.live               │
└──────────────────────┴───────────────────────────────┘
        ↑ 두 그래프가 같은 시점에 꺾이는 지점 = 한계점
```

k6 공식 Grafana 대시보드: **ID 2587** (Grafana UI에서 import)

### 수정이 필요한 파일

| 파일 | 수정 내용 |
|---|---|
| `docker/monitoring-compose.yml` | InfluxDB 서비스 추가 (influxdb:1.8, 포트 8086) |
| `docker/grafana/provisioning/datasources/datasource.yml` | InfluxDB 데이터소스 추가 |
| `docker/load-test-compose.yml` | k6 실행 시 `--out influxdb` 옵션 추가 |

---

## Seed 데이터 전략

### 필요한 데이터

```
브랜드 1개 (ACTIVE)
  └── 상품 A: 재고 10,000  → breakpoint.js 전용 (재고 고갈 노이즈 제거)
  └── 상품 B: 재고 50      → black-friday.js 전용 (한정 수량 재현)

유저 300명 (bp_user_001 ~ bp_user_300)
  → k6 setup() 단계에서 자동 등록
  → 이미 존재하면 무시 (멱등성)
```

### 재고 관리

Admin API에 재고 수정 엔드포인트 없음 → 직접 SQL 사용

```sql
-- 재고 리셋 (테스트 재실행 시)
UPDATE stock SET quantity = 50    WHERE product_id = {productId_B};
UPDATE stock SET quantity = 10000 WHERE product_id = {productId_A};
```

### 관련 파일
- `http/commerce-api/seed-black-friday.http` — 브랜드/상품 생성 요청 모음

---

## 실행 순서

```bash
# 1. 인프라 실행
docker-compose -f docker/infra-compose.yml up -d

# 2. 모니터링 실행 (InfluxDB 추가 후)
docker-compose -f docker/monitoring-compose.yml up -d

# 3. API 서버 실행
./gradlew :apps:commerce-api:bootRun

# 4. Seed 데이터 생성 (최초 1회)
# http/commerce-api/seed-black-friday.http 순서대로 실행
# → 이후 응답에서 productId 확인 후 SQL로 재고 등록

# 5. Grafana 접속 후 k6 대시보드 import
open http://localhost:3000  # admin / admin
# Dashboards → Import → ID: 2587 입력

# 6. Phase 1: 한계점 탐색
k6 run -e PRODUCT_ID={productId_A} --out influxdb=http://localhost:8086/k6 k6/breakpoint.js

# 7. Phase 2: 블랙프라이데이 재현 (재고 리셋 후)
# SQL: UPDATE stock SET quantity = 50 WHERE product_id = {productId_B};
k6 run -e PRODUCT_ID={productId_B} --out influxdb=http://localhost:8086/k6 k6/black-friday.js
```

---

## Phase 3: 대기열 구현 후 검증 (다음 주)

대기열 시스템 구현 후 **동일한 스크립트**를 재실행해서 비교한다.

| 지표 | Phase 1/2 (현재) | Phase 3 (대기열 후) 기대값 |
|---|---|---|
| 한계점 VU 수 | ~100 VU | 더 높아짐 |
| 블프 성공 주문 수 | 50개 미만 | 정확히 50개 |
| 비정상 에러율 | 30%+ | ≈ 0% |
| P99 응답시간 | 타임아웃 | 빠른 응답 (대기 번호 반환) |
| DB 커넥션 pending | 고갈 | 낮음 (순차 처리) |

---

## 작업 체크리스트

### 인프라 설정
- [ ] `monitoring-compose.yml`에 InfluxDB 1.8 추가 (포트 8086, DB명: k6)
- [ ] `datasource.yml`에 InfluxDB 데이터소스 추가
- [ ] `load-test-compose.yml`에 `--out influxdb` 옵션 추가
- [ ] Grafana에서 k6 대시보드 import (ID: 2587) 확인

### Seed 데이터
- [ ] 브랜드 생성 후 brandId 기록
- [ ] 한계점 테스트 상품 생성 후 productId_A 기록
- [ ] 블프 상품 생성 후 productId_B 기록
- [ ] SQL로 재고 등록 확인 (A: 10,000 / B: 50)

### breakpoint.js 실행
- [ ] PRODUCT_ID=productId_A 로 실행
- [ ] Grafana에서 P99 추이 확인
- [ ] 한계점 VU 수 기록

### black-friday.js 실행
- [ ] 재고 50개 리셋 확인
- [ ] PRODUCT_ID=productId_B 로 실행
- [ ] 성공 주문 수 vs 50개 비교
- [ ] 비정상 에러율 기록

### Phase 3 (대기열 구현 후)
- [ ] 재고 리셋
- [ ] 두 스크립트 재실행
- [ ] Before/After 수치 비교표 작성

---

## 하지 않을 것

- 쿠폰 적용, 여러 상품 → 병목 포인트 단순화 (재고 락 하나에 집중)
- 결제 플로우 → 주문 생성 단계가 핵심, 결제는 별개 분석
- Spring Boot simulator 모듈 → k6로 충분, 다음 시뮬레이터 주제에서 별도 구성