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
- `QueuePosition.estimatedWaitSeconds(schedulerIntervalMs, batchSize)` — 대기 시간 계산은 대기열 비즈니스 규칙이므로 도메인에 캡슐화. 설정값은 의존이 아닌 입력값으로 받아 도메인 순수성 유지
- `QueuePosition.nextPollAfter(schedulerIntervalMs, batchSize)` — estimatedWaitSeconds 기반 폴링 주기 계산도 같은 맥락의 도메인 규칙
- JPA Entity가 아닌 record — Redis에서 조회한 값을 도메인 언어로 표현하는 용도

---

### 2. 대기열 API 설계

#### POST /queue/enter
- `@LoginRequired`
- 대기열 진입 (Sorted Set에 userId, score=진입 timestamp 추가)
- 중복 진입 방지 — `queue:waiting` 또는 `queue:token:{userId}`에 이미 존재하면 200 반환 (멱등 처리)
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
1. `findToken(userId)` → present이면: ENTERED 응답 (token 포함)
2. `getRank(userId)` → present이면: WAITING 응답 (rank, estimatedWaitSeconds, nextPollAfter)
3. 둘 다 없음 → `404 NOT_FOUND` `"대기열에 진입하지 않은 사용자입니다."`

**예상 대기 시간 계산**:
```
estimatedWaitSeconds = (rank + 1) * (스케줄러_주기_초 / batch_size)
```
- Redis ZRANK는 0-based 반환 → rank=0이면 "다음 배치에서 바로 나감"이므로 +1 필요
- 정확한 계산이 아닌 추정치이며, UX 목적("대략 몇 분")에 충분
- 현재 시점의 실제 발급량까지 반영하면 추정치는 조금 나아질 수 있지만, 어차피 추정치인데 복잡도를 올리는 건 의미 없음
- 스케줄러 주기와 batch_size는 설정값으로 관리

**nextPollAfter 계산**:
```
estimatedWaitSeconds < 30   → 1초
estimatedWaitSeconds < 120  → 3초
estimatedWaitSeconds 이상   → 5초
```
- rank 기준이 아닌 estimatedWaitSeconds 기준을 사용하는 이유: rank는 절대 순번이라 설정값(batch_size, 스케줄러 주기)에 따라 같은 rank여도 실제 대기 시간이 달라지지만, estimatedWaitSeconds는 이미 그 계산이 반영된 값이라 구간 기준으로 적합

---

### 3. 스케줄러 설계: 순수 게이트식

**결정**: N초마다 무조건 batch_size명씩 발급 (조건 없음)

**MAX_TOKEN_COUNT 방식을 선택하지 않은 이유**:
- `ZCARD >= MAX → skip` 조건은 사실상 은행창구식 (자리가 있으면 발급)
- 조건을 추가해도 MAX 초과가 가능 (active=160, batch=18 → 178) 하고, MAX 근처에선 발급을 통째로 skip → 처리량 저하
- 순수 은행창구 대비 장점이 없음

**설정값 산정 공식 (Little's Law 대기열 관점)**:
```
L = λ * W

L = 서버가 안전하게 수용 가능한 동시 활성 토큰 수 = safe_TPS
W = 유저 평균 체류 시간 (토큰 수령 후 주문 완료까지)
λ = 초당 흘려보낼 수 있는 인원 (= 배치 크기 N, T=1s 기준)

→ λ = L / W  →  N = safe_TPS / W
```
- L을 safe_TPS로 보는 근거: 활성 토큰 보유자가 모두 동시에 요청하는 최악의 경우, 활성 토큰 수 = 순간 TPS. 따라서 활성 토큰 수 상한을 safe_TPS로 맞추면 서버가 버스트에도 버틸 수 있음
- W는 실측 데이터 없이 가정: 상품 확인 + 옵션 선택 + 배송지/결제 입력 ≈ 평균 60초

**설정값 산정 근거**:
```
커넥션 풀 = 50
평균 처리 시간 ≈ 0.2초
이론적 최대 TPS = 50 / 0.2 = 250   ← Little's Law: λ = L / W = 50 / 0.2
safe_TPS = 250 * 0.7 = 175

W = 60초 (평균 체류 시간 가정)
N = safe_TPS / W = 175 / 60 ≈ 2.9  →  N = 3

검증 (최대 활성 토큰 수):
N * (TTL / T) = 3 * (120 / 1) = 360명
→ 360명 동시 버스트 시 safe_TPS 초과 가능
→ Resilience4j RateLimiter로 초당 175건 초과 시 대기 처리 (버스트 방어)
```

**설정값**:
```
batch-size: 3            # 스케줄러 1회 발급 수 (= safe_TPS / 평균 체류 시간)
scheduler-interval-ms: 1000   # 스케줄러 주기 (1초)
token-ttl-seconds: 120   # 토큰 TTL (2분, 평균 체류 60초 * 2 여유)
```

**실행 로직**:
```
1. QueueFacade가 batch_size만큼 UUID 생성
2. Lua 스크립트:
     ZRANGE queue:waiting 0 batch_size-1      → 앞에서 N명 조회
     for each userId: SET queue:token:{userId} uuid EX ttl
     ZREM queue:waiting userId...             → waiting에서 제거
     return userId 목록
```

**스케줄러에서 즉시 토큰 발급하는 이유**:
- 이 설계의 게이트는 "T초마다 N명 입장"이지, 별도 active 슬롯 카운팅이 아님
- 폴링 시 lazy 발급으로 미루면 실제 입장 시점이 폴링 타이밍에 종속되어 스케줄러 게이트 의미가 흐려짐
- Lua 스크립트로 `SET token` + `ZREM waiting`을 원자적으로 처리하면 중간 장애로 인한 유실/중복 발급을 방지할 수 있음

**서비스 보호 방식**:
- `MAX_TOKEN_COUNT` 같은 동시 활성 수 상한은 두지 않음
- 대신 `N * (TTL / T)`가 시스템 safe TPS를 넘지 않도록 `batch-size`, `scheduler-interval-ms`, `token-ttl-seconds`를 설정값으로 관리
- 이 프로젝트의 대기열은 놀이공원식 게이트로 보고, 발급 주기와 배치 크기 튜닝으로 서버 부하를 제어

**대기열이 전역 단일인 이유**:
- 처음엔 상품별 대기열 분리를 고려했으나, 여러 상품 대기열이 있으면 전체 활성 토큰 수가 서버 TPS를 초과할 수 있음
- 모놀리식 환경에서는 모든 주문이 같은 서버 자원을 사용하므로 전역 단일 대기열이 맞음
- 상품별 재고 제어는 기존 비관적 락(OrderFacade)이 담당

---

### 4. 입장 토큰 설계

#### Redis Key 구조
```
queue:waiting              # 전역 대기열 (Sorted Set, score=진입 timestamp, member=userId)
queue:token:{userId}       # 입장 토큰 UUID 값 (String, TTL) — 클라이언트 반환 + 인터셉터 검증용
```

**설계 근거**:

이 설계에서는 별도 active 집합 없이, 스케줄러가 정해진 주기마다 정해진 수의 사용자에게 즉시 토큰을 발급한다. 클라이언트에 토큰 값을 내려줘야 하므로 UUID를 저장하는 `queue:token:{userId}` String 키를 사용한다.

- **`queue:waiting`**: 순서 보장 전담. 입장 전 사용자만 포함하며, 스케줄러가 발급한 사용자는 제거된다.
- **`queue:token:{userId}`**: UUID 토큰 값 저장. 클라이언트에 반환하고 인터셉터가 헤더 값과 대조 검증한다. TTL은 토큰 만료와 키 정리를 동시에 담당한다.
- **원자성**: Lua 스크립트로 `SET queue:token:{userId}` + `ZREM queue:waiting`을 한 번에 처리한다.

#### 인터셉터 검증 흐름
```
1. request.getAttribute("authenticatedUser") → userId 추출
   (AuthInterceptor가 먼저 실행되어 저장한 값)
2. request.getHeader("X-Loopers-EntryToken") → uuid 추출
3. QueueFacade.validateToken(userId, uuid) → findToken(userId) → UUID 일치 여부 확인
4. 일치하면 허용, 없거나 불일치 시 403
```

**토큰 만료를 token key만으로 판단하는 이유**:
- 별도 active 상태를 두지 않으므로 검증 기준은 `queue:token:{userId}` 하나면 충분하다
- 키가 존재하면 유효, 없으면 만료로 해석하면 인터셉터 로직이 단순하고 일관된다
- 만료 판단이 분산되지 않아 구현/테스트 복잡도가 낮다

#### 만료 처리 흐름
- 유저가 토큰 받고 아무것도 안 함 → TTL로 `queue:token:{userId}` 자동 삭제
- 주문 완료 시 → `OrderCreatedEvent` 발행 → `QueueEventListener`에서 `DEL queue:token:{userId}`

**주문 완료 후 토큰 삭제 설계**:
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용
- 주문이 실제로 커밋된 뒤에만 토큰을 삭제해 "성공한 주문만 토큰 소멸" 의미를 보장
- 삭제 실패 시 토큰은 TTL 만료 시각까지 유지되며, 이후 자동 정리됨

**`BEFORE_COMMIT`을 선택하지 않은 이유**:
- `BEFORE_COMMIT`이면 이후 커밋 실패 시 주문은 실패했는데 토큰만 먼저 사라질 수 있음
- token-only 모델에서는 커밋 전에 토큰을 지워서 얻는 이점보다, 커밋 결과와 토큰 소멸 시점을 맞추는 것이 더 중요함

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
- `@TransactionalEventListener(phase = AFTER_COMMIT)`으로 주문 성공 이후에만 토큰을 제거함

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
Optional<Long> getRank(long userId);                    // ZRANK queue:waiting
Optional<String> findToken(long userId);                // GET queue:token:{userId}
List<Long> issueTokens(int count, long ttlSeconds, List<String> uuids); // Lua: ZRANGE waiting → SET token EX ttl → ZREM waiting
void removeToken(long userId);                          // DEL token
```

**제거된 메서드**: `getActiveCount()`, `isInActive()`, `removeExpiredActive()`, `issueTokenIfAbsent()` — token-only 게이트 모델에서는 불필요

**QueueFacade 주요 메서드**:
```java
void enter(long userId);                          // POST /queue/enter
QueuePositionResult getPosition(long userId);     // GET /queue/position
void issueTokens();                              // 스케줄러 호출
boolean validateToken(long userId, String uuid);  // EntryTokenInterceptor 호출 — findToken → uuid 비교
void removeToken(long userId);                    // QueueEventListener 호출
```

**중복 체크 방식**: `enter` 시 Facade에서 `isInWaiting` + `findToken` 순차 호출 (명확성 우선)
**`issueTokens` 설계 근거**: 스케줄러가 토큰 발급과 waiting 제거를 함께 책임진다. Lua로 원자적 처리해 중간 장애 시 유실/중복을 방지한다.

### Task 1b. QueueRepositoryImpl 구현
- Redis 자료구조 활용 (`queue:waiting`, `queue:token:{userId}`)
- 스케줄러 발급 로직은 Lua 스크립트로 원자적 처리
- 테스트: Redis Testcontainers 활용 (`RedisTestContainersConfig`, `RedisCleanUp` — `testFixtures(modules:redis)`)
  - `queue:waiting` ZADD/ZRANK
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
- `QueueFacade.issueTokens()` — batch_size만큼 waiting 앞에서 꺼내 토큰 발급 후 제거
- `QueueScheduler` — `@Scheduled(fixedDelay = ...)` 사용 (fixedRate 사용 시 처리 겹침으로 double-move 위험)
- `QueueEventListener` — `OrderCreatedEvent` 구독, `@TransactionalEventListener(phase = AFTER_COMMIT)`
- 테스트: batch_size만큼 정확히 토큰 발급됨, waiting 선두부터 제거됨, 주문 완료 이벤트 시 토큰 삭제됨

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

### Task 6. Rate Limiter (Must-Have)
- `POST /api/v1/orders`에 Resilience4j RateLimiter 적용
- `application.yml`에 `order` RateLimiter 인스턴스 추가:
  ```yaml
  resilience4j.ratelimiter:
    instances:
      order:
        limitForPeriod: 175       # 초당 허용 요청 수 (= safe_TPS)
        limitRefreshPeriod: 1s
        timeoutDuration: 2s       # permit 대기 최대 시간, 초과 시 429
  ```
- `OrderController`에 `@RateLimiter(name = "order")` 적용
- 429 응답 처리: `ErrorType`에 `TOO_MANY_REQUESTS` 추가
- 테스트: 175건 초과 요청 시 429 반환 확인

### Task 7. Graceful Degradation (Nice-To-Have)
- Repository는 예외 그대로 throw, 각 호출부(Facade, Interceptor, Scheduler)에서 catch 후 상황별 응답 처리
- 장애 시 동작: 신규 진입 503, 순번 조회 503, 토큰 검증 실패 403, 스케줄러 발급 skip
- 테스트: Redis 예외 발생 시 각 상황별 응답 확인

### Task 8. Circuit Breaker (Nice-To-Have)
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
    batch-size: 3
    scheduler-interval-ms: 1000
    token-ttl-seconds: 120
  ```
- `apps/commerce-api/src/main/java/com/loopers/config/QueueProperties.java` — `@ConfigurationProperties(prefix = "queue")` record
  ```java
  @ConfigurationProperties(prefix = "queue")
  public record QueueProperties(int batchSize, long schedulerIntervalMs, long tokenTtlSeconds) {}
  ```
  - 도메인 비즈니스 규칙이 아닌 운영 튜닝값 → `config/` 패키지에 위치 (WebMvcConfig와 같은 맥락)
  - 설정값 여러 클래스(QueueFacade, QueueScheduler)에서 공유 → `@Value` 대신 단일 Properties 클래스로 관리
  - `@ConfigurationPropertiesScan`은 이미 `CommerceApiApplication`에 등록되어 있음

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
