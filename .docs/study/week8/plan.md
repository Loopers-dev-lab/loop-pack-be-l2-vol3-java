# 대기열 시스템 구현 계획

## Context
이벤트성 트래픽으로 인한 서버 과부하를 방지하기 위해 대기열 시스템을 구현한다.
목적은 **서버 전체 TPS 보호**이며, 순서를 보장하며 안전한 수의 사용자만 주문 API에 진입시킨다.

---

## 의사결정 및 결정 배경

### 1. 패키지 구조: `queue` 독립 도메인

**결정**: `queue` 독립 도메인으로 분리, 입장 토큰도 queue 패키지에 포함

**이유**:
- 대기열은 order와 독립적인 관심사 (트래픽 제어 목적)
- order 도메인에 붙이면 order가 비대해지고, 대기열이 order에 종속됨
- 모놀리식이지만 추후 확장 시 분리 비용 최소화

```
domain/queue/
  Queue.java
  QueueToken.java
  QueueRepository.java
application/queue/
  QueueFacade.java
interfaces/api/queue/
  QueueController.java
  QueueDto.java
interfaces/scheduler/
  QueueScheduler.java
infrastructure/queue/
  QueueRepositoryImpl.java
```

---

### 2. 대기열 API 설계

#### POST /queue/enter
- `@LoginRequired`
- 대기열 진입 (Sorted Set에 userId, score=진입 timestamp 추가)
- 이미 진입한 경우 중복 진입 방지

#### GET /queue/position
- `@LoginRequired`
- 응답 구조:

```json
// 대기 중
{
  "status": "WAITING",
  "rank": 42,
  "estimatedWaitSeconds": 126,
  "nextPollAfter": 3
}

// 입장 가능 (토큰 발급됨)
{
  "status": "ENTERED"
}
```

**`status` 필드가 필요한 이유**:
- 스케줄러가 토큰을 발급하면 사용자는 `queue:waiting`에서 제거됨
- 이 시점에 폴링이 오면 rank도 없고, 대기열에도 없음 → `rank`만으로는 "입장됨"인지 "미진입"인지 구분 불가
- 따라서 토큰 존재 여부를 먼저 확인해 `ENTERED` / `WAITING`을 판별해야 함

**내부 로직**:
1. `queue:token:{userId}` 존재 → ENTERED 응답
2. `queue:waiting`에서 rank 조회 → WAITING 응답
3. 둘 다 없음 → 대기열 미진입 에러

**토큰 값을 응답에 포함하지 않는 이유**:
- 토큰 key가 `queue:token:{userId}`이므로 클라이언트가 토큰 값을 알 필요 없음
- 주문 API 호출 시 인터셉터가 userId로 직접 Redis 조회

---

### 3. 스케줄러 설계: 최대 토큰 수 기반

**결정**: 최대 활성 토큰 수 상한을 두고, 빈 슬롯만큼 발급

**TPS 기준 방식을 선택하지 않은 이유**:
- TPS 기준(N초마다 N명 발급)은 발급 속도만 제어하며, 실제 동시 활성 토큰 수를 보장하지 못함
- TTL이 길면 활성 토큰이 계속 쌓여 TPS를 초과할 수 있음
- 대기열 목적이 "동시 접속자 수 제어"이므로 직접적인 상한 제어가 필요

**최대 토큰 수 산정 방법**:
```
서버 한계 TPS 측정 → 안전 마진 적용 (예: 70%) → 최대 토큰 수 확정
```

**Thundering Herd가 문제되지 않는 이유**:
- 최대 토큰 수 = 안전 TPS로 설정하면, 토큰 보유자 전원이 동시에 주문 API를 호출해도 안전 TPS 이내
- 별도 ms 단위 분산 발급 불필요

**실행 로직**:
```
queue:active:count < MAX_TOKEN_COUNT
  → 빈 슬롯 수 = MAX_TOKEN_COUNT - active:count
  → queue:waiting에서 앞에서부터 빈 슬롯 수만큼 pop
  → queue:token:{userId} 발급 (TTL 설정)
  → queue:active:count 증가
```

**대기열이 전역 단일인 이유**:
- 처음엔 상품별 대기열 분리를 고려했으나, 여러 상품 대기열이 있으면 전체 활성 토큰 수가 서버 TPS를 초과할 수 있음
- 모놀리식 환경에서는 모든 주문이 같은 서버 자원을 사용하므로 전역 단일 대기열이 맞음
- 상품별 재고 제어는 기존 비관적 락(OrderFacade)이 담당

---

### 4. 입장 토큰 설계

#### Redis Key 구조
```
queue:waiting                  # 전역 대기열 (Sorted Set, score=진입 timestamp)
queue:token:{userId}           # 입장 토큰 (String, TTL)
queue:active:count             # 활성 토큰 수 카운터
```

#### TTL
- 토큰 발급 후 사용자가 주문 완료까지 허용되는 시간 (TBD - 운영 정책 결정 필요, 예시: 5분)
- **TTL이 핵심 안전망인 이유**: Redis와 DB는 동일 트랜잭션이 불가능하므로 토큰을 코드에서 명시적으로 삭제하는 방식은 신뢰할 수 없음 → TTL이 유일한 보장
- TTL 만료 시 `queue:active:count` 감소 처리 필요

#### 토큰 검증 위치: 인터셉터

**결정**: `@QueueRequired` 어노테이션 추가, `AuthInterceptor`에서 체크

**OrderFacade 내부에서 검증하지 않는 이유**:
- 인증/인가는 비즈니스 로직과 분리되어야 함 (기존 `@LoginRequired`, `@AdminOnly` 패턴과 일관성)
- OrderFacade가 Queue에 의존하면 단일 책임 원칙 위반
- 토큰 없이 OrderFacade를 단독 테스트하기 어려워짐

#### Order 도메인이 Queue를 모르는 이유

**결정**: Order는 Queue를 전혀 모름, 토큰 소멸은 TTL에 위임

**핵심 근거 — 두 가지 실패 시나리오**:

1. `@Transactional` 내에서 토큰 삭제 시도 → DB 롤백 발생
   - DB 트랜잭션은 롤백되지만 Redis 삭제는 원복 안 됨
   - 결과: 주문은 없는데 토큰도 사라짐 → 사용자가 대기열부터 다시 시작

2. 주문 저장 후 토큰 삭제 시도 → Redis 삭제 실패
   - 결과: 주문은 됐는데 토큰이 살아있음 → 중복 주문 가능

두 경우 모두 완전한 원자성 보장이 불가능하므로, TTL을 안전망으로 삼고 Order가 Queue를 알 필요 자체를 없앰

---

### 5. Graceful Degradation: Degraded Mode

**결정**: Redis 장애 시 신규 진입 차단, 기존 토큰 보유자는 허용

**Fail Closed와의 차이**:
- Fail Closed: Redis 장애 시 모든 주문 불가
- Degraded Mode: 이미 발급된 토큰 보유자는 TTL 만료 전까지 주문 가능 → 장애 영향 범위 최소화

**Fail Open을 선택하지 않은 이유**:
- 대기열의 목적이 트래픽 제어인데, Fail Open은 그 목적 자체를 훼손함
- Redis 장애 시 대기열 우회 허용 → 이벤트 시 서버 과부하 위험

**구현**:
- Redis 장애 감지: Resilience4j Circuit Breaker 활용 (기존 설정 재사용)
- 신규 대기열 진입 차단: `POST /queue/enter` → 503
- 기존 토큰 보유자는 허용: TTL 만료 전까지 주문 가능
- 신규 토큰 발급 중단: 스케줄러 Circuit Breaker open 시 발급 skip
- 토큰 검증 실패 시: Redis 조회 불가 → 토큰 없음으로 처리 → 403

---

## 구현 순서 (TDD: Red → Green → Refactor)

### Task 1. Redis key 구조 및 Queue 도메인 기반 설계
- `QueueRepository` 인터페이스 정의
- `QueueRepositoryImpl` (Redis Sorted Set, String 활용)
- 테스트: `queue:waiting` ZADD/ZRANK, `queue:token:{userId}` SET/GET/DEL, `queue:active:count` INCR/DECR

### Task 2. 대기열 진입 API (`POST /queue/enter`)
- `QueueFacade.enter(userId)`
- `QueueController` + `QueueDto`
- 테스트: 중복 진입 방지, 정상 진입

### Task 3. 순번 조회 API (`GET /queue/position`)
- `QueueFacade.getPosition(userId)`
- ENTERED / WAITING 분기 처리
- 테스트: 토큰 있을 때 ENTERED, 대기열에 있을 때 WAITING, 둘 다 없을 때 에러

### Task 4. 스케줄러 (`QueueScheduler`)
- `QueueFacade.issueTokens()` — 빈 슬롯만큼 토큰 발급
- 테스트: 최대 토큰 수 초과 발급 안 됨, 빈 슬롯만큼 정확히 발급

### Task 5. 인터셉터 (`@QueueRequired`)
- `@QueueRequired` 어노테이션 추가
- `AuthInterceptor`에 체크 로직 추가
- `POST /api/v1/orders`에 `@QueueRequired` 적용
- 테스트: 토큰 없을 때 403, 토큰 있을 때 통과

### Task 6. Graceful Degradation
- Circuit Breaker 적용 (`QueueRepositoryImpl` Redis 호출부)
- 장애 시 신규 진입 503, 토큰 검증 실패 시 403
- 테스트: Circuit Breaker open 시 동작 확인

---

## 주요 파일 경로

### 신규 생성
- `apps/commerce-api/src/main/java/com/loopers/domain/queue/`
- `apps/commerce-api/src/main/java/com/loopers/application/queue/QueueFacade.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueController.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueDto.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/scheduler/QueueScheduler.java`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueRepositoryImpl.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/auth/QueueRequired.java`

### 기존 수정
- `apps/commerce-api/src/main/java/com/loopers/interfaces/auth/AuthInterceptor.java` — `@QueueRequired` 체크 추가
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/order/OrderController.java` — `@QueueRequired` 적용

### 참고 (패턴 재사용)
- `RedisProductCacheStore.java` — Redis 사용 패턴
- `AuthInterceptor.java` — 인터셉터 패턴
- `PaymentReconciliationScheduler.java` — 스케줄러 패턴
- `LoginRequired.java` — 어노테이션 패턴

---

## 검증 방법
1. `POST /queue/enter` → 대기열 진입 확인
2. `GET /queue/position` → WAITING 상태 및 rank 확인
3. 스케줄러 실행 → `GET /queue/position` ENTERED 전환 확인
4. `POST /api/v1/orders` — 토큰 없으면 403, 토큰 있으면 주문 성공
5. TTL 만료 후 `GET /queue/position` → 미진입 에러 확인
6. `.http/queue.http` 에 E2E 테스트 케이스 작성
