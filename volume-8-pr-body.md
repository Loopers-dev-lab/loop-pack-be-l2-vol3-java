## 📌 Summary

- **배경**: Black Friday 주문 API에 트래픽이 집중되면 HikariCP 커넥션 풀이 고갈되어 DB가 다운된다. Rate Limiting(즉시 거절)은 사용자가 재시도를 반복해 오히려 Thundering Herd 문제를 유발한다.
- **목표**: Redis Sorted Set 기반 대기열로 번호표를 발급하고, 처리 가능한 수(N=80)만큼만 주문 API에 진입할 수 있도록 Back-pressure를 구현한다.
- **결과**: 대기열 진입 → 입장 토큰 발급(스케줄러) → 토큰 검증(인터셉터) → Adaptive Polling 순번 조회의 3단계 파이프라인 구현 완료. 동시 진입, 토큰 TTL 만료, 처리량 초과 검증 포함 19개 테스트 통과.

---

## 🧭 Context & Decision

### 1. 대기열 중복 진입을 어떻게 방지할 것인가?

| 항목 | ZSCORE 조회 후 분기 | ZADD NX (채택) |
|------|-------------------|---------------|
| 원자성 | ZSCORE → ZADD 사이 끼어들기 가능 (TOCTOU) | 단일 명령어로 원자적 처리 |
| 순번 조회 | O(N) 스캔 | O(log N) ZRANK |
| 코드 복잡도 | 2단계 | 1단계 |

- **결정**: `ZADD NX` (`addIfAbsent`)
- **근거**: Redis 싱글스레드 특성상 확인+추가가 원자적으로 처리된다. ZSCORE 후 분기는 두 명령 사이에 다른 요청이 끼어드는 TOCTOU 문제가 발생한다.
- **트레이드오프**: 동일 밀리초에 진입한 사용자 간 순서가 undefined이다. 같은 밀리초 내 순서는 비즈니스상 무의미하므로 허용한다.

![mermaid-diagram.png](docs/image/mermaid-diagram.png)
**전체 흐름**:



### 2. 토큰을 어떻게 설계할 것인가?

| 항목 | 단순 값 ("1") | UUID + Redis String TTL (채택) |
|------|-------------|-------------------------------|
| 위조 방지 | userId만 알면 토큰 추측 가능 | userId 알아도 UUID 값 모르면 차단 |
| 만료 처리 | 직접 관리 | Redis TTL 자동 만료 |
| 검증 방식 | 존재 여부만 확인 | 존재 + 값 일치 이중 확인 |

- **결정**: UUID + Redis String (TTL 300초)
- **근거**: 주문 API는 userId 헤더만 있으면 누구나 호출할 수 있다. UUID로 토큰 값을 모르면 위조가 불가능하다. Redis TTL로 만료를 자동 처리해 DB 커넥션 소모 없이 빠른 조회가 가능하다.
- **트레이드오프**: 클라이언트가 토큰 값을 저장해야 하고, TTL(300초) 안에 주문을 완료해야 한다. 결제 중 만료되면 403이 반환된다.

### 3. 토큰 검증을 어디에서 수행할 것인가?

| 항목 | Filter (Servlet) | Interceptor (채택) | AOP |
|------|-----------------|-------------------|-----|
| Spring Bean 주입 | 불편 (수동 getBean) | 가능 | 가능 |
| URL 패턴 적용 | 가능 | 가능 | 불가 (메서드 레벨) |
| ControllerAdvice 연동 | 불가 | **가능** | 가능 |

- **결정**: `HandlerInterceptor`
- **근거**: Redis 조회에 `TokenService` Spring Bean이 필요하고, `/api/v1/orders/**` URL 패턴 적용과 `CoreException → ControllerAdvice` 처리 연동이 동시에 필요하다. Filter는 ControllerAdvice가 예외를 잡지 못한다.
- **트레이드오프**: Filter 대비 Spring MVC에 의존한다. 단, 대기열 토큰은 Spring 컨텍스트 내에서 처리가 필요하므로 허용 가능한 의존성이다.

### 4. 스케줄러 배치 팝의 원자성을 어떻게 보장할 것인가?

| 항목 | Java 3단계 분리 | Lua 스크립트 (채택) |
|------|---------------|-------------------|
| ZRANGE → EXISTS → ZREM 사이 | 중간 실패 시 유령 상태 발생 | 단일 스크립트로 원자적 실행 |
| 중간 단계 끼어들기 | 가능 | 불가 (Redis 싱글스레드) |
| 디버깅 | 쉬움 | 어려움 |

- **결정**: Lua 스크립트로 `ZRANGE + EXISTS + ZREM` 원자적 처리
- **근거**: 3단계를 분리하면 ZREM 성공 후 토큰 발급 실패 시 대기열에서는 제거됐지만 토큰이 없는 유령 상태가 된다. Lua는 Redis에서 단일 명령으로 실행되어 중간 실패가 구조적으로 불가능하다.
- **트레이드오프**: Lua 디버깅이 어렵고 Java 상수(`QueueConstants.TOKEN_KEY_PREFIX`)를 직접 참조할 수 없어 `'token:'`을 스크립트에 하드코딩했다. 주석으로 상수와 연결을 명시했다.

> **리뷰 포인트**: 토큰을 보유한 유저가 주문을 완료하지 않고 대기열에 재진입하면, Lua에서 `EXISTS == 1`이라 스킵되어 TTL(300초) 만료까지 대기열에 머문다. 진입 시점에 토큰 존재 여부를 체크해 차단하는 것이 맞을지, 아니면 스케줄러에서 즉시 ZREM하는 것이 나은지 판단이 서지 않는다. → [`TokenScheduler.executeIssue()`][scheduler-execute]

### 5. 멀티 인스턴스 환경에서 스케줄러 중복 실행을 어떻게 방지할 것인가?

| 항목 | @ConditionalOnSingleCandidate | Redisson 분산 락 (채택) |
|------|------------------------------|----------------------|
| 멀티 인스턴스 | 인스턴스마다 실행 | 락 획득한 1개만 실행 |
| 락 TTL | 없음 | 4초 (비정상 종료 시 자동 해제) |
| 구현 복잡도 | 낮음 | 중간 |

- **결정**: Redisson 분산 락 (`tryLock(0, 4, SECONDS)`)
- **근거**: 단일 인스턴스는 문제가 없지만, 멀티 인스턴스 환경에서 스케줄러가 동시에 실행되면 같은 userId에게 토큰이 중복 발급된다. Lua 스크립트의 EXISTS 체크가 1차 방어이지만, 스케줄러 자체를 직렬화하는 것이 더 근본적이다.
- **트레이드오프**: 락 TTL(4초)이 실제 처리 시간보다 짧으면 락이 만료되어 중복 실행 가능성이 생긴다. 대기열 인원 폭증 시 처리 시간이 4초를 초과할 수 있다.

> **리뷰 포인트**: 스케줄러 주기(5초)보다 짧은 락 TTL(4초)을 선택했다. 처리 시간이 4초를 초과하는 케이스에서 락이 먼저 만료되면 다른 인스턴스가 진입할 수 있다. 실무에서 락 TTL을 주기보다 짧게 설정하는 것이 일반적인지, 보완 전략이 있는지 조언을 구하고 싶다. → [`TokenScheduler.issueTokens()`][scheduler-issue]

### 6. 대기자에게 Polling 주기를 어떻게 안내할 것인가?

| 항목 | 고정 주기 | Adaptive Polling (채택) |
|------|---------|------------------------|
| 순번 멀 때 | 불필요한 요청 반복 | 긴 주기(30초)로 부하 절감 |
| 순번 가까울 때 | 늦게 반응 | 짧은 주기(5초)로 빠른 반응 |
| 클라이언트 구현 | 단순 | `nextPollAfterSeconds` 필드 필요 |

- **결정**: `nextPollAfterSeconds`를 응답에 포함하는 Adaptive Polling
- **근거**: 모든 대기자가 동일 주기로 Polling하면 순번이 먼 사용자도 불필요한 요청을 반복한다. 서버가 주기를 제어함으로써 Thundering Herd 없이 부하를 분산한다.
- **트레이드오프**: 클라이언트가 고정 주기 대신 응답의 `nextPollAfterSeconds`를 따라야 한다. 수치(≤10→5초, ≤50→15초, >50→30초)는 시뮬레이션 전 초기값이며 실측 후 조정이 필요하다.

---

## 🏗️ Design Overview

### 변경 범위

| 커밋 | 분류 | 변경 요약 |
|------|------|----------|
| [`8a8b4a7`][commit-step1] | feat | Step 1 — Redis Sorted Set 대기열 진입 + 순번 조회 |
| [`15cf54a`][commit-step2-token] | feat | Step 2 — 입장 토큰 & 스케줄러 (Lua + Redisson) |
| [`3ce5c1a`][commit-step2-interceptor] | feat | Step 2 — 토큰 검증 인터셉터 |
| [`26d73178`][commit-step3] | feat | Step 3 — Adaptive Polling 순번 조회 |
| [`7e6725e`][commit-test] | test | Step 2/3 — 통합·E2E·동시성·검증 테스트 |
| [`2013bf6`][commit-fix] | fix | KafkaConfig 제네릭 타입 수정 |

### Step 1 — 대기열 진입 + 순번 조회

```java
// QueueRepositoryImpl.java
public long enter(String userId, long score) {
    redisTemplate.opsForZSet().addIfAbsent(QUEUE_KEY, userId, score); // ZADD NX
    Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId);   // ZRANK
    return rank + 1; // 1-indexed
}
```

| 컴포넌트 | 파일 | 메서드 | 역할 |
|----------|------|--------|------|
| `QueueService` | [`domain/queue/`][queue-service] | `enter()`, `getPosition()` | score에 currentTimeMillis 주입, NOT_FOUND 예외 |
| `QueueRepositoryImpl` | [`infrastructure/queue/`][queue-repo] | `enter()`, `findPosition()` | ZADD NX + ZRANK + ZCARD |
| `QueueFacade` | [`application/queue/`][queue-facade] | `enter()`, `getPosition()` | position + totalCount 조합 |
| `QueueV1Controller` | [`interfaces/api/queue/`][queue-controller] | `POST /enter`, `GET /position` | 진입/순번 조회 API |

### Step 2 — 입장 토큰 & 스케줄러

```java
// TokenScheduler.java — Lua로 원자적 팝
private static final String POP_ELIGIBLE_USERS_SCRIPT = """
    local members = redis.call('ZRANGE', KEYS[1], 0, ARGV[1])
    for i, member in ipairs(members) do
        if redis.call('EXISTS', 'token:' .. member) == 0 then
            redis.call('ZREM', KEYS[1], member)
            table.insert(eligible, member)
        end
    end
    return eligible
    """;

// TokenService.java — UUID 발급
String token = UUID.randomUUID().toString();
tokenRepository.save(userId, token, 300, TimeUnit.SECONDS); // EX 300
```

| 컴포넌트 | 파일 | 메서드 | 역할 |
|----------|------|--------|------|
| `TokenScheduler` | [`domain/queue/`][scheduler] | `issueTokens()` | @Scheduled(5초) + Redisson 락 + Lua 팝 |
| `TokenService` | [`domain/queue/`][token-service] | `issue()`, `isValid()`, `revoke()` | UUID 발급 · 검증 · 폐기 |
| `TokenRepositoryImpl` | [`infrastructure/queue/`][token-repo] | `save()`, `findToken()` | Redis String EX 300 |
| `QueueTokenInterceptor` | [`interfaces/api/queue/`][interceptor] | `preHandle()` | X-User-Id + X-Queue-Token 검증 → 403 |
| `QueueConstants` | [`domain/queue/`][constants] | — | QUEUE_KEY, TOKEN_KEY_PREFIX, BATCH_SIZE, SCHEDULER_INTERVAL_SECONDS |

### Step 3 — Adaptive Polling 순번 조회

```java
// QueueFacade.java
private long calculateEstimatedWait(long position) {
    return (long) Math.ceil((double) position / BATCH_SIZE * SCHEDULER_INTERVAL_SECONDS);
}
private long calculateNextPollAfter(long position) {
    if (position <= 10) return 5;   // 곧 차례
    if (position <= 50) return 15;  // 중간
    return 30;                      // 뒤쪽
}
```

응답 예시:
```json
{ "position": 45, "totalCount": 200,
  "estimatedWaitSeconds": 30, "nextPollAfterSeconds": 15, "token": null }
```

---

## 🧪 테스트

| # | 테스트 클래스 | 전략 | 검증 항목 |
|---|-------------|------|----------|
| 1 | [`QueueServiceIntegrationTest`][test-queue-service] | 통합 | 진입·순번·전체 인원·NOT_FOUND 예외 |
| 2 | [`TokenSchedulerIntegrationTest`][test-scheduler] | 통합 | 배치 발급, 중복 발급 방지, 배치 크기 초과 |
| 3 | [`QueueTokenInterceptorE2ETest`][test-interceptor] | E2E | 토큰 없음/만료/불일치 → 403, 유효 토큰 → 주문 API 통과 |
| 4 | [`QueuePositionIntegrationTest`][test-position] | 통합 | estimatedWait·nextPollAfter 계산, 토큰 발급 후 응답 포함 |
| 5 | [`QueueVerificationTest`][test-verification] | 동시성 | 같은 userId 10스레드 → 1건만 등록 |
| 6 | [`QueueVerificationTest`][test-verification] | 동시성 | 20명 동시 진입 → 각자 고유 순번 |
| 7 | [`QueueVerificationTest`][test-verification] | TTL | TTL 1초 강제 후 2초 대기 → 토큰 무효화 |
| 8 | [`QueueVerificationTest`][test-verification] | 처리량 | 100명 진입, N=80 → 1회 실행 후 20명 잔여 |
| 9 | [`QueueVerificationTest`][test-verification] | 처리량 | 2회 실행 → 전원 처리, 대기열 비어있음 |

---

## 🔁 Flow Diagram

```mermaid
sequenceDiagram
    actor User
    participant Controller as QueueV1Controller
    participant Facade as QueueFacade
    participant Redis

    User->>Controller: POST /api/v1/queue/enter?userId=user-1
    Controller->>Facade: enter(userId)
    Facade->>Redis: ZADD queue:waiting NX currentTimeMillis() user-1
    Facade->>Redis: ZRANK queue:waiting user-1
    Facade->>Redis: ZCARD queue:waiting
    Facade-->>Controller: QueueInfo(position, totalCount)
    Controller-->>User: { position: 1, totalCount: 1 }

    Note over Facade,Redis: @Scheduled fixedDelay=5000ms

    loop TokenScheduler (Redisson 락 획득 시)
        Facade->>Redis: Lua(ZRANGE+EXISTS+ZREM, N=80)
        Redis-->>Facade: eligible userId 목록
        Facade->>Redis: SET token:{userId} UUID EX 300
    end

    User->>Controller: GET /api/v1/queue/position?userId=user-1
    Controller->>Facade: getPosition(userId)
    Facade->>Redis: ZRANK + ZCARD + GET token:{userId}
    Facade-->>User: { position, estimatedWaitSeconds, nextPollAfterSeconds, token }

    User->>Controller: POST /api/v1/orders (X-User-Id + X-Queue-Token)
    Controller->>Redis: GET token:{userId} → 값 일치 확인
    alt 유효
        Controller-->>User: 200 OK
        Controller->>Redis: DEL token:{userId}
    else 무효/만료
        Controller-->>User: 403 FORBIDDEN
    end
```

---

## ✅ Checklist

### Step 1 — 대기열
- [x] `POST /queue/enter` — Redis Sorted Set 기반 대기열 진입 (ZADD NX) → [`QueueRepositoryImpl.enter()`][queue-repo-enter]
- [x] `GET /queue/position` — 순번 + 전체 대기 인원 조회 → [`QueueFacade.getPosition()`][facade-get-position]
- [x] userId 중복 진입 방지 (ZADD NX 원자적 처리)
- [x] 전체 대기 인원 조회 (ZCARD)
- [x] 이탈 감지 — `presence:{userId}` TTL(90초) 기반, 폴링 없으면 자동 만료 → 다음 배치에서 ZREM → [`QueueService`][queue-service], [`TokenScheduler`][scheduler]

### Step 2 — 입장 토큰 & 스케줄러
- [x] 5초마다 상위 80명에게 UUID 토큰 발급 (Lua 스크립트 + Redisson 락) → [`TokenScheduler`][scheduler]
- [x] 토큰 TTL 300초 (Redis String EX)
- [x] 주문 API 진입 시 토큰 검증 → [`QueueTokenInterceptor.preHandle()`][interceptor-prehandle]
- [x] 주문 완료 후 토큰 삭제 → [`TokenService.revoke()`][token-revoke]

### Step 3 — 실시간 순번 조회
- [x] 예상 대기 시간: `순번 / N × 주기` → [`QueueFacade.calculateEstimatedWait()`][facade-estimated]
- [x] Adaptive Polling: `nextPollAfterSeconds` 응답 포함 → [`QueueFacade.calculateNextPollAfter()`][facade-next-poll]
- [x] 토큰 발급 시 `GET /position` 응답에 token 포함

---

<!-- Reference Links -->

<!-- Commits -->
[commit-step1]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/commit/8a8b4a7
[commit-step2-token]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/commit/15cf54a
[commit-step2-interceptor]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/commit/3ce5c1a
[commit-step3]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/commit/26d73178
[commit-test]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/commit/7e6725e
[commit-fix]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/commit/2013bf6

<!-- Source -->
[queue-service]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/domain/queue/QueueService.java
[queue-repo]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueRepositoryImpl.java
[queue-repo-enter]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueRepositoryImpl.java#L18-L22
[queue-facade]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/application/queue/QueueFacade.java
[queue-controller]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueV1Controller.java
[scheduler]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/domain/queue/TokenScheduler.java
[scheduler-issue]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/domain/queue/TokenScheduler.java#L42-L60
[scheduler-execute]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/domain/queue/TokenScheduler.java#L62-L77
[token-service]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/domain/queue/TokenService.java
[token-revoke]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/domain/queue/TokenService.java#L36-L38
[token-repo]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/TokenRepositoryImpl.java
[interceptor]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueTokenInterceptor.java
[interceptor-prehandle]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueTokenInterceptor.java#L22-L30
[constants]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/domain/queue/QueueConstants.java
[facade-get-position]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/application/queue/QueueFacade.java#L29-L36
[facade-estimated]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/application/queue/QueueFacade.java#L38-L40
[facade-next-poll]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/main/java/com/loopers/application/queue/QueueFacade.java#L42-L49

<!-- Tests -->
[test-queue-service]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/test/java/com/loopers/domain/queue/QueueServiceIntegrationTest.java
[test-scheduler]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/test/java/com/loopers/domain/queue/TokenSchedulerIntegrationTest.java
[test-interceptor]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/test/java/com/loopers/interfaces/api/QueueTokenInterceptorE2ETest.java
[test-position]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/test/java/com/loopers/domain/queue/QueuePositionIntegrationTest.java
[test-verification]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-8/apps/commerce-api/src/test/java/com/loopers/domain/queue/QueueVerificationTest.java
