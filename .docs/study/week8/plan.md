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
  EntryToken.java       # 입장 토큰 값 객체 (isValid())
  QueuePosition.java    # 대기 순번 값 객체 (estimatedWaitSeconds(), nextPollAfter())
  QueueRepository.java
application/queue/
  QueueFacade.java
  QueuePositionResult.java  # getPosition() 반환 타입 — sealed interface (Waiting / Entered)
interfaces/api/queue/
  QueueController.java
  QueueDto.java
interfaces/scheduler/
  QueueScheduler.java
infrastructure/queue/
  QueueRepositoryImpl.java
```

**도메인 객체를 값 객체(record)로 두는 이유**:
- `EntryToken.isValid()` — 만료 판단 로직을 도메인에 캡슐화 (Facade가 epoch_ms를 직접 다루지 않음)
- `QueuePosition.estimatedWaitSeconds(schedulerIntervalSeconds, maxTokenCount)` — 대기 시간 계산은 대기열 비즈니스 규칙이므로 도메인에 캡슐화. 설정값은 의존이 아닌 입력값으로 받아 도메인 순수성 유지
- `QueuePosition.nextPollAfter(schedulerIntervalSeconds, maxTokenCount)` — estimatedWaitSeconds 기반 폴링 주기 계산도 같은 맥락의 도메인 규칙
- JPA Entity가 아닌 record — Redis에서 조회한 값을 도메인 언어로 표현하는 용도

---

### 2. 대기열 API 설계

#### POST /queue/enter
- `@LoginRequired`
- 대기열 진입 (Sorted Set에 userId, score=진입 timestamp 추가)
- 중복 진입 방지 — `queue:waiting` 또는 `queue:active`에 이미 존재하면 200 반환 (멱등 처리)
- 응답: 200 OK (body 없음)

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
  "status": "ENTERED",
  "token": "550e8400-e29b-41d4-a716-446655440000"
}
```

**QueuePositionResult 구조**:
```java
// Java 17 sealed interface — 컴파일러가 모든 케이스를 강제함
// Facade 반환 시 instanceof 패턴 매칭으로 Controller가 분기 처리
sealed interface QueuePositionResult {
    record Waiting(long rank, long estimatedWaitSeconds, long nextPollAfter)
        implements QueuePositionResult {}
    record Entered(String token)
        implements QueuePositionResult {}
}
```
- 단일 레코드(status 필드 포함)와의 차이: 컴파일러가 모든 케이스 처리를 강제 → null 체크나 if/else 분기 실수 방지
- Controller에서 `switch(result) { case Waiting w -> ... case Entered e -> ... }` 패턴 매칭 사용

**`status` 필드가 필요한 이유**:
- 스케줄러가 토큰을 발급하면 사용자는 `queue:waiting`에서 제거됨
- 이 시점에 폴링이 오면 rank도 없고, 대기열에도 없음 → `rank`만으로는 "입장됨"인지 "미진입"인지 구분 불가
- 따라서 토큰 존재 여부를 먼저 확인해 `ENTERED` / `WAITING`을 판별해야 함

**내부 로직**:
1. `isInActive(userId)` → true이면: `issueTokenIfAbsent(userId, uuid, ttl)` → ENTERED 응답 (token 포함)
2. `getRank(userId)` → present이면: WAITING 응답 (rank, estimatedWaitSeconds, nextPollAfter)
3. 둘 다 없음 → `404 NOT_FOUND` `"대기열에 진입하지 않은 사용자입니다."`

**예상 대기 시간 계산**:
```
estimatedWaitSeconds = rank * (스케줄러_주기_초 / MAX_TOKEN_COUNT)
```
- 정확한 계산이 아닌 추정치이며, UX 목적("대략 몇 분")에 충분
- 현재 빈 슬롯 수를 반영하면 추정치가 다소 나아지지만, 어차피 추정치인데 복잡도를 올리는 건 의미 없음
- 스케줄러 주기와 MAX_TOKEN_COUNT는 설정값으로 관리

**nextPollAfter 계산**:
```
estimatedWaitSeconds < 30   → 1초
estimatedWaitSeconds < 120  → 3초
estimatedWaitSeconds 이상   → 5초
```
- rank 기준이 아닌 estimatedWaitSeconds 기준을 사용하는 이유: rank는 절대 순번이라 설정값(MAX_TOKEN_COUNT, 스케줄러 주기)에 따라 같은 rank여도 실제 대기 시간이 달라지지만, estimatedWaitSeconds는 이미 그 계산이 반영된 값이라 구간 기준으로 적합

---

### 3. 스케줄러 설계: 순수 게이트식

**결정**: N초마다 무조건 batch_size명씩 발급 (조건 없음)

**MAX_TOKEN_COUNT 방식을 선택하지 않은 이유**:
- `ZCARD >= MAX → skip` 조건은 사실상 은행창구식 (자리가 있으면 발급)
- 조건을 추가해도 MAX 초과가 가능 (active=160, batch=18 → 178) 하고, MAX 근처에선 발급을 통째로 skip → 처리량 저하
- 순수 은행창구 대비 장점이 없음

**설정값 산정 공식**:
```
safe_TPS = 한계_TPS * 0.7
N * (TTL / T) ≤ safe_TPS
→ 설정값 자체가 안전장치 역할
```
- 최악의 케이스(활성 토큰 보유자가 TTL 내에 동시에 요청) 기준으로 계산
- 실제 운영 중 여유가 있으면 T를 줄이거나 N을 늘려 처리량 증가

**설정값 산정 근거**:
```
커넥션 풀 = 50
평균 처리 시간 ≈ 0.2초
이론적 최대 TPS = 50 / 0.2 = 250
safe_TPS = 250 * 0.7 = 175

N * (TTL / T) ≤ 175
→ T=1s, N=1: 1 * (180/1) = 180  (이론적 최악치 기준 아슬아슬 초과,
  실제 운영에서 180명이 동시에 1초에 요청하는 일은 없으므로 허용)
```

**설정값**:
```
batch-size: 1            # 스케줄러 1회 발급 수
scheduler-interval-ms: 1000   # 스케줄러 주기 (1초)
token-ttl-seconds: 180   # 토큰 TTL (3분)
```
※ 실제 운영에서 여유가 확인되면 N을 늘려 처리량 증가

**실행 로직**:
```
1. ZREMRANGEBYSCORE queue:active 0 now        → 만료 항목 정리 (housekeeping)
2. Lua 스크립트:
     ZRANGE queue:waiting 0 batch_size-1      → 앞에서 N명 peek
     for each userId: ZADD queue:active expiry userId  → active로 먼저 등록
     ZREM queue:waiting userId...             → waiting에서 제거
     return userId 목록
```

**순서를 "옮기고 → 꺼내기"로 한 이유**:
- ZPOPMIN(꺼내고) → ZADD active(옮기기) 순서면 중간 장애 시 userId 유실 (waiting에도 active에도 없음)
- ZADD active(옮기고) → ZREM waiting(꺼내기) 순서면 중간 장애 시 userId가 양쪽에 존재 → 폴링 시 isInActive → true → ENTERED 응답으로 자연 복구
- Lua 스크립트로 전체 원자적 처리 → 장애 자체를 방지

**토큰 발급은 스케줄러가 아닌 폴링 시 lazy하게**:
- 스케줄러는 waiting → active 이동만 담당
- `GET /queue/position`에서 isInActive → true 확인 후 `SET NX queue:token:{userId} UUID EX ttl` 발급
- `SET NX`(없을 때만 SET): 동시 폴링 시 첫 번째만 발급, 이후 요청은 기존 UUID 반환 → 멱등 보장

**대기열이 전역 단일인 이유**:
- 처음엔 상품별 대기열 분리를 고려했으나, 여러 상품 대기열이 있으면 전체 활성 토큰 수가 서버 TPS를 초과할 수 있음
- 모놀리식 환경에서는 모든 주문이 같은 서버 자원을 사용하므로 전역 단일 대기열이 맞음
- 상품별 재고 제어는 기존 비관적 락(OrderFacade)이 담당

---

### 4. 입장 토큰 설계

#### Redis Key 구조
```
queue:waiting              # 전역 대기열 (Sorted Set, score=진입 timestamp, member=userId)
queue:active               # 활성 토큰 목록 (Sorted Set, score=만료 epoch_ms, member=userId) — 카운팅 & 만료 관리
queue:token:{userId}       # 입장 토큰 UUID 값 (String, TTL) — 클라이언트 반환 + 인터셉터 검증용
```

**설계 근거**:

처음에는 `queue:active:count` 카운터로 활성 수를 관리하려 했으나 TTL 만료 시 카운터 drift 문제가 있어 `queue:active` Sorted Set으로 카운팅을 대체했다. 이후 클라이언트에 토큰 값을 내려줘야 한다는 요구사항에 따라 UUID를 저장하는 `queue:token:{userId}` String 키를 추가했다.

- **`queue:active`**: 카운팅 및 만료 정리 전담. `ZREMRANGEBYSCORE`로 만료 항목 정리 → `ZCARD`로 O(1) 활성 수 조회. drift 없음.
- **`queue:token:{userId}`**: UUID 토큰 값 저장. 클라이언트에 반환하고 인터셉터가 헤더 값과 대조 검증. TTL은 키 정리 목적 (카운팅은 `queue:active`에 의존).
- **원자성**: Lua 스크립트로 `ZADD queue:active` + `SET queue:token:{userId}` 동시 처리.

#### 인터셉터 검증 흐름
```
1. request.getAttribute("authenticatedUser") → userId 추출
   (AuthInterceptor가 먼저 실행되어 저장한 값)
2. request.getHeader("X-Loopers-EntryToken") → uuid 추출
3. QueueFacade.validateToken(userId, uuid) → findToken(userId) → UUID 일치 여부 확인
4. 일치하면 허용, 없거나 불일치 시 403
```

**`queue:active` 만료 체크를 인터셉터에서 하지 않는 이유**:
- 토큰은 스케줄러가 active로 이동시킨 시점에 만료 epoch가 결정되지만, 토큰(UUID)은 첫 폴링 시 lazy하게 발급됨
- `queue:active` score와 `queue:token:{userId}` TTL의 시작 시점이 달라 drift 발생 가능 → active 체크가 오히려 유효한 토큰을 거부할 수 있음
- `queue:token:{userId}` TTL을 만료 기준으로 삼음: 키가 존재하면 유효, 없으면 만료. `queue:active`는 카운팅 전용

#### 만료 처리 흐름
- 유저가 토큰 받고 아무것도 안 함 → TTL로 `queue:token:{userId}` 자동 삭제, 다음 스케줄러 실행 시 `ZREMRANGEBYSCORE`로 `queue:active` 정리 → 슬롯 반환
- 주문 완료 시 → `OrderCreatedEvent` 발행 → `QueueEventListener`에서 `ZREM queue:active userId` + `DEL queue:token:{userId}` (슬롯 즉시 반환)

**주문 완료 후 토큰 삭제 설계**:
- `@TransactionalEventListener(phase = BEFORE_COMMIT)` 사용
- 토큰 삭제 실패 시 예외를 삼켜 DB 트랜잭션 롤백 방지 (주문은 정상 처리)
- 삭제 실패 시 슬롯은 `queue:active` score(만료 시각)까지 점유 후 스케줄러가 정리

**`AFTER_COMMIT`을 선택하지 않은 이유**:
- `AFTER_COMMIT`이면 DB 커밋 후 토큰 삭제 실패 시 슬롯이 TTL 만료 시각까지 낭비
- `BEFORE_COMMIT` + 예외 삼킴으로 주문 롤백 없이 best-effort 삭제 시도

#### 토큰 검증 위치: 인터셉터

**결정**: `@EntryTokenRequired` 어노테이션 추가, `AuthInterceptor`에서 체크
- Order API에는 `@LoginRequired` + `@EntryTokenRequired` 둘 다 명시
- 인터셉터에서 `@LoginRequired` 처리 후 `@EntryTokenRequired` 처리 (순서 보장)

**`@EntryTokenRequired`가 `@LoginRequired`를 포함하지 않는 이유**:
- 입장 토큰과 로그인은 독립적인 관심사. 입장 토큰은 "줄 서서 들어왔는가"(트래픽 제어)이고, 로그인은 "너 누구야"(인증)
- IP 기반 대기열처럼 비회원도 토큰을 받을 수 있는 구조가 존재하므로 어노테이션 의미에 로그인을 포함시키면 재사용성이 사라짐
- 이 프로젝트에서 대기열 진입이 `@LoginRequired`이므로 사실상 로그인이 전제되지만, 그건 구현 세부사항이지 어노테이션 계약이 아님

**OrderFacade 내부에서 검증하지 않는 이유**:
- 인증/인가는 비즈니스 로직과 분리되어야 함 (기존 `@LoginRequired`, `@AdminOnly` 패턴과 일관성)
- OrderFacade가 Queue에 의존하면 단일 책임 원칙 위반
- 토큰 없이 OrderFacade를 단독 테스트하기 어려워짐

#### Order 도메인이 Queue를 모르는 이유

**결정**: Order는 Queue를 전혀 모름. 토큰 소멸은 `OrderCreatedEvent`를 구독하는 `QueueEventListener`가 담당

**핵심 근거**:
- OrderFacade가 직접 Queue를 호출하면 단일 책임 원칙 위반, 테스트 복잡도 증가
- 이벤트를 통한 간접 연결로 Order → Queue 의존성 제거
- `@TransactionalEventListener(phase = BEFORE_COMMIT)` + 예외 삼킴으로 토큰 삭제 실패가 주문 롤백으로 이어지지 않음

---

### 5. Graceful Degradation: Degraded Mode

**결정**: Redis 장애 시 신규 진입 차단, 기존 토큰 보유자는 허용

**Fail Closed와의 차이**:
- Fail Closed: Redis 장애 시 모든 주문 불가
- Degraded Mode: 이미 발급된 토큰 보유자는 만료 전까지 주문 가능 → 장애 영향 범위 최소화

**Fail Open을 선택하지 않은 이유**:
- 대기열의 목적이 트래픽 제어인데, Fail Open은 그 목적 자체를 훼손함
- Redis 장애 시 대기열 우회 허용 → 이벤트 시 서버 과부하 위험

**구현 전략: 모두 Nice-To-Have (Must-Have 완료 후 시간 남으면)**

try-catch 위치: Repository는 예외를 그대로 throw, 각 호출부에서 상황에 맞게 처리

| 상황 | try-catch 위치 | 응답 |
|---|---|---|
| `POST /queue/enter` Redis 장애 | Facade | 503 |
| `GET /queue/position` Redis 장애 | Facade | 503 |
| `EntryTokenInterceptor` Redis 장애 | Interceptor | 403 |
| 스케줄러 Redis 장애 | Scheduler | log + skip |

**Circuit Breaker (Nice-To-Have)**:
- Redis 간헐적 장애 시 매 요청마다 Redis에 시도 → 타임아웃 대기로 응답 지연
- CB open 시 Redis 시도 없이 즉시 fallback → 응답 속도 보호
- 구현: `QueueRepositoryImpl` Redis 호출부에 Resilience4j CB 적용 (신규 `queue` 인스턴스 추가)

---

## 구현 순서 (TDD: Red → Green → Refactor)

### Task 0. 처리량 측정 및 설정값 산정
- `POST /api/v1/orders` 부하 테스트로 주문 1건 평균 처리 시간 측정
- DB 커넥션 풀 크기 확인 (`application.yml` or `jpa.yml`)
- 산정 공식:
  ```
  safe_TPS = 한계_TPS * 0.7
  token-ttl-seconds = 180  (평균 주문 소요 시간 3분)
  N * (TTL / T) ≤ safe_TPS  →  N, T 결정
  ```
- 측정 결과 기반으로 `application.yml` 설정값 확정
- 산정 근거 문서화 (quest 체크리스트 항목)

### Task 1a. 도메인 객체 + QueueRepository 인터페이스 정의
- `EntryToken` record 정의 — `isValid()`
- `QueuePosition` record 정의 — `estimatedWaitSeconds()`, `nextPollAfter()`
- `QueueRepository` 인터페이스 정의:

```java
void enter(long userId, double score);                  // queue:waiting ZADD
boolean isInWaiting(long userId);                       // queue:waiting ZSCORE 존재 여부
boolean isInActive(long userId);                        // queue:active ZSCORE > now 여부
Optional<Long> getRank(long userId);                    // ZRANK queue:waiting
Optional<String> findToken(long userId);                // GET queue:token:{userId}
void removeExpiredActive();                             // ZREMRANGEBYSCORE 0 now
List<Long> moveToActive(int count, long expiry);        // Lua: ZRANGE waiting → ZADD active → ZREM waiting
Optional<String> issueTokenIfAbsent(long userId, String uuid, long ttl); // SET NX → 발급 or 기존 UUID 반환
void removeToken(long userId);                          // ZREM active + DEL token
```

**제거된 메서드**: `getActiveCount()` — MAX_TOKEN_COUNT 상한 체크 제거로 불필요

**QueueFacade 주요 메서드**:
```java
void enter(long userId);                          // POST /queue/enter
QueuePositionResult getPosition(long userId);     // GET /queue/position
void moveToActive();                              // 스케줄러 호출
boolean validateToken(long userId, String uuid);  // EntryTokenInterceptor 호출 — findToken → uuid 비교
void removeToken(long userId);                    // QueueEventListener 호출
```

**중복 체크 방식**: `enter` 시 Facade에서 `isInWaiting` + `isInActive` 순차 호출 (명확성 우선)
**`moveToActive` 설계 근거**: 옮기고(ZADD active) → 꺼내기(ZREM waiting) 순서로 장애 시 유실 방지. Lua로 원자적 처리
**`issueTokenIfAbsent` 설계 근거**: `SET NX`로 동시 폴링 시 첫 번째만 발급, 이후는 기존 UUID 반환. `GET /queue/position`에서 호출

### Task 1b. QueueRepositoryImpl 구현
- Redis Sorted Set 활용 (`queue:waiting`, `queue:active`, `queue:token:{userId}`)
- 스케줄러 발급 로직은 Lua 스크립트로 원자적 처리
- 테스트: Redis Testcontainers 활용 (`RedisTestContainersConfig`, `RedisCleanUp` — `testFixtures(modules:redis)`)
  - `queue:waiting` ZADD/ZRANK
  - `queue:active` ZADD/ZSCORE/ZREMRANGEBYSCORE/ZCARD
  - `queue:token:{userId}` SET/GET/DEL

### Task 2. 대기열 진입 API (`POST /queue/enter`)
- `QueueFacade.enter(userId)`
- `QueueController` + `QueueDto`
- 테스트: 중복 진입 방지, 정상 진입

### Task 3. 순번 조회 API (`GET /queue/position`)
- `QueueFacade.getPosition(userId)`
- ENTERED / WAITING 분기 처리
- 테스트: 토큰 있을 때 ENTERED, 대기열에 있을 때 WAITING, 둘 다 없을 때 에러

### Task 4. 스케줄러 + QueueEventListener
- `QueueFacade.moveToActive()` — batch_size만큼 waiting → active 이동, MAX_TOKEN_COUNT 초과 시 skip
- `QueueScheduler` — `@Scheduled(fixedDelay = ...)` 사용 (fixedRate 사용 시 처리 겹침으로 double-move 위험)
- `QueueEventListener` — `OrderCreatedEvent` 구독, `@TransactionalEventListener(phase = BEFORE_COMMIT)` + 예외 삼킴
- 테스트: batch_size만큼 정확히 이동됨, 활성 토큰 수가 MAX_TOKEN_COUNT 이상이면 이동 skip

### Task 5. 인터셉터 (`@EntryTokenRequired`)
- `@EntryTokenRequired` 어노테이션 추가
- `EntryTokenInterceptor` 별도 생성 — `AuthInterceptor` 수정 없음, `QueueFacade` 의존 (기존 `AuthInterceptor`가 `UserFacade`를 통하는 패턴과 일관성)
- `WebMvcConfig`에 `AuthInterceptor` 이후 순서로 등록
- `POST /api/v1/orders`에 `@LoginRequired` + `@EntryTokenRequired` 적용
- 테스트: 토큰 없을 때 403, 토큰 있을 때 통과

**`AuthInterceptor`와 분리한 이유**:
- `AuthInterceptor`는 인증/인가 관심사, `EntryTokenInterceptor`는 트래픽 제어 관심사 — 한 클래스에 두는 것이 어색함
- 기존 `AuthInterceptor` 수정 없이 확장 가능 → 사이드이펙트 없음
- 인터셉터 등록 순서로 `@LoginRequired` → `@EntryTokenRequired` 실행 순서 보장

### Task 6. Graceful Degradation (Nice-To-Have)
- Repository는 예외 그대로 throw, 각 호출부(Facade, Interceptor, Scheduler)에서 catch 후 상황별 응답 처리
- 장애 시 동작: 신규 진입 503, 순번 조회 503, 토큰 검증 실패 403, 스케줄러 발급 skip
- 테스트: Redis 예외 발생 시 각 상황별 응답 확인

### Task 7. Circuit Breaker (Nice-To-Have)
- `application.yml`에 `queue` CB 인스턴스 추가
- `QueueRepositoryImpl` Redis 호출부에 Resilience4j CB 적용
- 테스트: CB open 시 Redis 시도 없이 즉시 fallback 확인

---

## 주요 파일 경로

### 신규 생성
- `apps/commerce-api/src/main/java/com/loopers/domain/queue/`
- `apps/commerce-api/src/main/java/com/loopers/application/queue/QueueFacade.java`
- `apps/commerce-api/src/main/java/com/loopers/application/queue/QueuePositionResult.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueController.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueDto.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/scheduler/QueueScheduler.java`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueRepositoryImpl.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/auth/EntryTokenRequired.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/auth/EntryTokenInterceptor.java`
- `apps/commerce-api/src/main/java/com/loopers/application/queue/QueueEventListener.java`

### 기존 수정
- `apps/commerce-api/src/main/java/com/loopers/config/WebMvcConfig.java` — `EntryTokenInterceptor` 등록 (AuthInterceptor 이후)
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/order/OrderController.java` — `@EntryTokenRequired` 적용
- `apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java` — `SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, ...)` 추가
- `apps/commerce-api/src/main/resources/application.yml` — queue 설정 추가
  ```yaml
  queue:
    batch-size: 1
    scheduler-interval-ms: 1000
    token-ttl-seconds: 180
  ```
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueProperties.java` — `@ConfigurationProperties(prefix = "queue")` record
  ```java
  @ConfigurationProperties(prefix = "queue")
  public record QueueProperties(int batchSize, long schedulerIntervalMs, long tokenTtlSeconds) {}
  ```
  - 설정값 여러 클래스(QueueFacade, QueueScheduler)에서 공유 → `@Value` 대신 단일 Properties 클래스로 관리
  - `@EnableConfigurationProperties(QueueProperties.class)` 또는 `@ConfigurationPropertiesScan` 등록 필요

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
