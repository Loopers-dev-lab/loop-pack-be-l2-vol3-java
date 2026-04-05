# Round 8: Redis 기반 대기열 시스템 — 동작 보고서

## 1. 개요

블랙 프라이데이 트래픽 폭증 시 주문 API 앞단에 대기열을 두어 시스템을 보호한다.
Rate Limiting(거부)이 아니라 대기열(줄 세우기)을 선택한 이유: 유저가 "기다리면 내 차례가 온다"는 확신이 있으면 재시도를 안 한다.

### 핵심 설계 결정

| 결정 | 선택 | 이유 |
|------|------|------|
| 자료구조 | Redis Sorted Set | score=timestamp로 선착순 보장, ZADD NX로 중복 방지, ZRANK O(log N) |
| 토큰 검증 위치 | Interceptor | 횡단 관심사. Controller/Service에 넣지 않고 공통 전처리 |
| 동시성 제어 | Redis 단일 스레드 | ZADD NX 원자적 → 1만 명 동시 요청에도 중복 진입 불가 |
| Thundering Herd 완화 | 100ms 주기 × ~7명 | 1초에 한 번 70명이 아니라 100ms마다 7명씩 분산 |
| Polling 주기 | 순번 구간별 동적 | 1~100: 1초, 100~1000: 4초, 1000+: 10초 |

---

## 2. 전체 흐름

```
[유저]                    [Redis]                  [Scheduler 100ms]       [API Server]
  │                         │                         │                      │
  │ POST /queue/enter       │                         │                      │
  │────────────────────────→│ ZADD NX                 │                      │
  │ { position: 42 }       │                         │                      │
  │←────────────────────────│                         │                      │
  │                         │                         │                      │
  │ GET /queue/position     │                         │                      │
  │────────────────────────→│ ZRANK                   │                      │
  │ { position: 42,        │                         │                      │
  │   waitTime: 1초,       │                         │                      │
  │   polling: 1초 }       │                         │                      │
  │←────────────────────────│                         │                      │
  │                         │                         │                      │
  │                         │    100ms마다             │                      │
  │                         │←────────────────────────│                      │
  │                         │ ZPOPMIN 7명             │                      │
  │                         │ SET token (TTL 5분)     │                      │
  │                         │                         │                      │
  │ GET /queue/position     │                         │                      │
  │────────────────────────→│                         │                      │
  │ { position: 0,         │                         │                      │
  │   token: "abc-123" }   │                         │                      │
  │←────────────────────────│                         │                      │
  │                         │                         │                      │
  │ POST /orders            │                         │                      │
  │ (X-Queue-Token)         │                         │                      │
  │──────────────────────────────────────────────────────────────────────────→│
  │                         │                         │         Interceptor  │
  │                         │ GET token → 검증       │              ↓       │
  │                         │                         │         주문 처리   │
  │                         │ DEL token              │              ↓       │
  │ { orderId: 1234 }      │                         │         토큰 삭제   │
  │←──────────────────────────────────────────────────────────────────────────│
```

---

## 3. Redis Key 설계

| Key | Type | 용도 | TTL |
|-----|------|------|-----|
| `queue:waiting` | Sorted Set | 대기열. member=userId, score=timestamp(ms) | - |
| `queue:token:{userId}` | String | 입장 토큰. value=UUID | 300초 (5분) |

---

## 4. 스케줄러 배치 크기 산정

| 항목 | 값 | 근거 |
|------|:---:|------|
| DB 커넥션 풀 | 40 | jpa.yml maximum-pool-size |
| 주문 1건 처리 시간 | ~200ms | 재고 감소 + 쿠폰 + 주문 생성 |
| 이론적 최대 TPS | 40 / 0.2 = **200** | 커넥션 1개가 초당 5건 |
| 안전 마진 (1/3) | **~67 TPS** | 주문 외 API도 커넥션 사용 |
| 스케줄러 주기 | **100ms** | Thundering Herd 완화 |
| 100ms당 배치 크기 | **7명** | 67 × 0.1 ≈ 7 |
| 토큰 TTL | **5분** | 블프 유저는 1~2분이면 충분 + 여유 |
| 예상 대기 시간 | position / 70 초 | 초당 ~70명 처리 |

---

## 5. Polling 동적 주기

| 내 순번 | Polling 주기 | 이유 |
|--------|:-----------:|------|
| 1 ~ 100 | **1초** | 곧 입장 → 빠른 반응 필요 |
| 100 ~ 1,000 | **4초** | 중간 대기 |
| 1,000+ | **10초** | 한참 뒤 → 자주 확인해도 변화 적음 |

서버 응답에 `pollingIntervalSeconds`를 포함하여 클라이언트가 조절.

**부하 비교:**
- 전원 2초 Polling: 10,000명 → 초당 5,000건
- 동적 Polling: 100명×1 + 900명×0.25 + 9,000명×0.1 = 초당 ~1,225건 (75% 감소)

---

## 6. 토큰 검증 — Interceptor

```
HTTP 요청 → Filter (서블릿) → Interceptor (Spring MVC) → Controller → Service
                                    ↑
                           QueueTokenInterceptor
                           - POST /api/v1/orders에만 적용
                           - X-User-Id + X-Queue-Token 헤더 확인
                           - Redis에서 토큰 검증
                           - 불일치 → 403 Forbidden
```

Controller/Service가 아닌 Interceptor에서 검증하는 이유:
- **횡단 관심사** (cross-cutting concern) — 공통 전처리 로직
- 비즈니스 로직(주문 생성)과 인프라 관심사(토큰 검증) 분리

---

## 7. 구현 파일 목록

### 신규 생성 (10개)

| 계층 | 파일 | 책임 |
|------|------|------|
| Domain | `QueueRepository.java` | 대기열 ZSET 인터페이스 |
| Domain | `TokenRepository.java` | 토큰 String 인터페이스 |
| Domain | `QueueService.java` | 대기열 핵심 도메인 로직 |
| Domain | `QueueEntryResult.java` | 진입 결과 값 객체 |
| Domain | `QueuePositionResult.java` | 순번 조회 결과 값 객체 |
| Infrastructure | `QueueRepositoryImpl.java` | Redis ZSET 구현 (Master) |
| Infrastructure | `TokenRepositoryImpl.java` | Redis String+TTL 구현 (Master) |
| Application | `QueueFacade.java` | 유스케이스 오케스트레이션 |
| Application | `QueueAdmissionScheduler.java` | 100ms 주기 배치 토큰 발급 |
| Interfaces | `QueueV1Controller.java` | POST /enter, GET /position |

### 기존 수정 (2개)

| 파일 | 변경 |
|------|------|
| `OrderFacade.java` | 주문 완료 후 `queueService.consumeToken(userId)` |
| `application.yml` | `queue.interceptor.enabled` 설정 추가 |

---

## 8. 테스트 커버리지

| 테스트 | 유형 | 검증 내용 |
|--------|------|----------|
| QueueServiceTest (11개) | 단위 | 진입, 중복, 순번조회, 토큰발급, 토큰검증, Polling주기 |
| QueueAdmissionSchedulerTest (2개) | 단위 | 배치 처리, 빈 대기열 |
| QueueV1ApiE2ETest (5개) | E2E | 진입, 다중진입, 토큰발급확인, 404, 토큰없이주문403 |
| 전체 | 통합 | **284개 전체 통과, 0 실패** |

---

## 9. API 명세

### POST /api/v1/queue/enter
```json
// Request
Headers: X-User-Id: 1

// Response 200
{
  "meta": { "result": "SUCCESS" },
  "data": {
    "userId": 1,
    "position": 42,
    "totalWaiting": 500,
    "newEntry": true
  }
}
```

### GET /api/v1/queue/position
```json
// 대기 중
{
  "data": {
    "position": 42,
    "totalWaiting": 500,
    "estimatedWaitSeconds": 1,
    "pollingIntervalSeconds": 1,
    "token": null
  }
}

// 토큰 발급됨
{
  "data": {
    "position": 0,
    "totalWaiting": 0,
    "estimatedWaitSeconds": 0,
    "pollingIntervalSeconds": 0,
    "token": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

---

## 10. k6 부하 테스트 결과

### 테스트 시나리오

| Phase | 시나리오 | VU | iterations |
|-------|---------|:---:|:---:|
| 1 | 500명 동시 대기열 진입 | 50 | 500 |
| 2 | 순번 Polling + 토큰 대기 | 20 | 100 |
| 3 | 토큰 없이 주문 시도 | 5 | 5 |

### 결과

| 항목 | 결과 |
|------|:---:|
| 대기열 진입 성공 | **500명** |
| 중복 진입 | **0건** (ZADD NX) |
| 토큰 발급 확인 (Polling) | **100명** |
| 토큰 없이 주문 거부 (403) | **5건** |
| 대기열 최종 잔여 | **0명** (전원 처리) |
| 토큰 발급 수 (Redis) | **500개** (TTL 5분 대기 중) |

### Redis 처리 속도

- 500명 진입 → 스케줄러 100ms×7명 = 초당 ~70명 → **~7초만에 전원 토큰 발급 완료**
- 대기열 최종 잔여: 0명

---

## 11. API 명세

### POST /api/v1/orders (토큰 필요)
```
Headers:
  X-User-Id: 1
  X-Queue-Token: 550e8400-e29b-41d4-a716-446655440000

// 토큰 없거나 만료 → 403 Forbidden
```
