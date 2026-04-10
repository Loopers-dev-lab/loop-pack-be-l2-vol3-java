# Week 8 구현 계획 리뷰

> 시니어 백엔드 개발자 관점의 비판적 리뷰. 설계 결함, 엣지 케이스, 장애 시나리오, 테스트 보완점을 다룬다.

---

## 1. 토큰 삭제 후 주문 실패 — 복구 불가 (Critical)

**Phase 3 설계의 가장 큰 구조적 결함.**

```
EntryTokenInterceptor.preHandle() → validateAndDelete(userId)  // 토큰 삭제
    → OrderFacade.create()  // 재고 부족, DB 장애 등으로 실패
    → 토큰은 이미 삭제됨 → 유저는 재주문 불가, 대기열도 이탈 상태
```

Interceptor에서 토큰을 **삭제까지** 하는 건 위험하다. 주문이 성공해야 토큰이 소멸되어야 하는데, Interceptor는 주문 결과를 모른다.

**대안:**
- Interceptor에서는 `exists` 검증만 (통과 여부 판단)
- 주문 성공 후 `OrderFacade` 또는 이벤트 리스너에서 토큰 삭제
- 또는 `afterCompletion()`에서 응답 status 확인 후 삭제

이 경우 "단일 사용 보장"은 GETDEL이 아니라, 토큰 TTL + 주문 성공 후 명시적 삭제로 전환해야 한다. 동시 주문 방지는 기존 주문 로직의 비관적 락이 이미 담당하고 있으므로, 토큰 레벨에서 원자적 단일 사용을 강제할 필요가 줄어든다.

---

## 2. EntryTokenInterceptor — userId 추출 방식 오류

기존 `AuthInterceptor`는 다음과 같이 저장한다:

```java
request.setAttribute(LoginUserArgumentResolver.ATTR_LOGIN_USER, userInfo);
```

`UserInfo` 객체를 저장하는데, 계획서에서는:

```java
Long userId = (Long) request.getAttribute("userId");  // ← 존재하지 않는 키
```

실제로는 `LoginUserArgumentResolver.ATTR_LOGIN_USER`로 `UserInfo`를 꺼낸 뒤 `userInfo.getId()`를 호출해야 한다.

---

## 3. Interceptor 경로 패턴 — HTTP 메서드 구분 불가

```java
registry.addInterceptor(entryTokenInterceptor)
    .addPathPatterns("/api/v1/orders")
    .excludePathPatterns("/api/v1/orders/*");
```

Spring Interceptor의 `addPathPatterns`는 **HTTP 메서드를 구분하지 않는다.** `GET /api/v1/orders` (주문 목록 조회)도 이 패턴에 걸린다.

**대안:** `preHandle` 내부에서 `request.getMethod().equals("POST")` 체크를 추가한다. 별도 경로(`/api/v1/orders/create`)를 쓰는 방법도 있지만 RESTful 원칙에 어긋나므로 전자가 현실적이다.

---

## 4. Domain Port와 Infrastructure 시그니처 불일치

Domain 레이어 (Phase 2-1):
```java
void issueTokens(List<Long> userIds, long ttlSeconds);
```

Infrastructure 레이어 (Phase 2-2):
```java
List<EntryToken> issueTokens(String queueKey, int batchSize, long ttlSeconds);
```

ZPOPMIN을 Repository 내부에서 실행하므로 `userIds`를 외부에서 받을 수 없다. **도메인 Port가 인프라 구현 세부사항(ZPOPMIN)에 의해 시그니처가 결정**되는 상황인데, 이러면 Port의 추상화가 깨진다.

**제안:** 책임을 분리한다.
- `WaitingQueueRepository.popBatch(int batchSize): List<Long>` — ZPOPMIN 담당
- `EntryTokenRepository.issueTokens(List<Long> userIds, long ttlSeconds)` — SET 담당

Lua로 원자성을 확보하려면 Infrastructure 레이어 내부에 이 두 Repository를 조합하는 `RedisQueueTokenIssuer` 같은 컴포넌트를 두고, 도메인에는 `QueueTokenIssuer` Port를 정의하는 게 DIP에 부합한다.

---

## 5. Lua 스크립트 — 토큰 수 > 실제 pop 수 불일치

```java
List<String> tokens = IntStream.range(0, batchSize)
    .mapToObj(i -> UUID.randomUUID().toString())
    .toList();
```

항상 `batchSize`(18)개의 UUID를 생성해서 ARGV로 전달하지만, 큐에 5명만 남아있으면 ZPOPMIN은 5개만 반환한다. Lua 스크립트는 `popped` 크기만큼만 순회하므로 **동작은 정상**이지만, 13개의 UUID가 낭비된다.

더 중요한 건 — Lua 스크립트의 **반환 타입**이다. `RedisScript<List>` raw type을 사용하는데, Lua가 반환하는 값은 Redis 프로토콜상 `List<String>`이 아니라 `List<Object>` (Long/String 혼합)일 수 있다. 기존 `coupon_issue_request.lua`가 `Long.class` 단일 값을 반환하는 것과 달리, 여기선 배열을 반환하므로 **역직렬화 검증이 필수**이다.

---

## 6. 토큰 만료 후 재진입 플로우 누락

유저 시나리오:
```
대기열 진입 → 토큰 발급 → 5분 내 주문 안 함 → TTL 만료 → ???
```

현재 설계에서:
- 대기열에서는 ZPOPMIN으로 이미 제거됨
- 토큰은 TTL로 만료됨
- **유저는 대기열에도 없고, 토큰도 없는 상태**

`getPosition()` 호출 시 `NOT_FOUND` 예외가 발생하고, 클라이언트는 "대기열에 없다"는 에러만 받는다. 재진입하면 되긴 하지만, **클라이언트가 이 상태를 어떻게 판별하고 자동 재진입할지** 계획서에 없다.

**제안:** `getPosition()`에서 대기열에도 없고 토큰도 없는 경우를 별도 상태(`QueuePosition.expired()` 또는 전용 응답)로 반환하거나, 에러 코드를 분리해서 클라이언트가 재진입 UX를 처리할 수 있게 해야 한다.

---

## 7. `@Scheduled` 스레드 풀 경합

기존에 `OutboxRelayScheduler`가 이미 `@Scheduled`를 사용하고 있다. Spring Boot의 기본 `@Scheduled` 스레드 풀은 **1개**이다.

```
OutboxRelayScheduler (fixedDelay) + EntryTokenScheduler (fixedDelay=100ms)
→ 동일 스레드에서 교대 실행 → 실제 간격 = 200ms+
```

`EntryTokenScheduler`의 100ms 간격이 보장되지 않을 수 있다. 배치 크기 산정이 "초당 10회 실행" 전제인데, 실제론 5회 이하일 수 있다.

**대안:** `application.yml`에 스케줄러 스레드 풀 크기를 설정한다:
```yaml
spring.task.scheduling.pool.size: 2
```
또는 `SchedulingConfigurer`를 구현해서 별도 스레드 풀을 지정한다.

---

## 8. Score 충돌 — 동일 밀리초 진입 시 FIFO 깨짐

```java
double score = System.currentTimeMillis();
```

동시에 여러 유저가 진입하면 **동일 score**를 가진다. Redis Sorted Set에서 동일 score인 경우 **member 문자열의 사전순**으로 정렬된다. 즉 `userId=9`가 `userId=100`보다 뒤에 위치한다 ("9" > "1"...).

FIFO 순서가 깨질 수 있다. 대기열 시스템에서 "먼저 들어온 사람이 먼저"라는 공정성이 중요하다면:

**대안:** Redis의 서버 시간을 사용하거나, score에 마이크로초 정밀도를 주거나, Lua 내에서 `redis.call('TIME')`으로 Redis 서버 시간을 score로 사용한다:
```lua
local time = redis.call('TIME')
local score = time[1] * 1000000 + time[2]  -- 마이크로초 정밀도
```

---

## 9. 폴링 시 Redis 이중 호출

```java
public QueuePosition getPosition(Long userId) {
    if (entryTokenRepository.existsByUserId(userId)) { ... }  // Redis 호출 1
    Long rank = waitingQueueRepository.getPosition(userId);      // Redis 호출 2
    long totalCount = waitingQueueRepository.getTotalCount();     // Redis 호출 3
}
```

순번 조회 1회에 Redis 호출 3회. 클라이언트가 1초마다 폴링하고 동시 대기자가 10,000명이면 **초당 30,000 Redis 커맨드**이다.

**대안:** Lua 스크립트 하나로 3개를 묶거나, 최소한 `existsByUserId` + `getPosition`을 Pipeline으로 처리한다. 혹은 토큰 확인을 ZRANK 결과가 null인 경우에만 수행하는 순서로 바꾸면 대부분의 경우 2회로 줄일 수 있다:

```java
Long rank = waitingQueueRepository.getPosition(userId);
if (rank == null) {
    // 큐에 없다 → 토큰이 발급됐거나, 아예 진입 안 한 유저
    if (entryTokenRepository.existsByUserId(userId)) {
        return QueuePosition.tokenIssued();
    }
    throw new CoreException(ErrorType.NOT_FOUND, "...");
}
```

---

## 10. 테스트 보완이 필요한 시나리오

| 누락된 시나리오 | 왜 중요한가 |
|---------------|-----------|
| **토큰 삭제 후 주문 실패 → 재시도 불가** | 이슈 #1의 핵심. 현재 테스트 계획에 없음 |
| **토큰 TTL 만료 후 재진입 플로우** | 이슈 #6. 클라이언트 상태 전이 검증 |
| **스케줄러 연속 실행 간 간격 검증** | 이슈 #7. fixedDelay가 실제로 100ms인지 |
| **동일 score 유저 간 순서** | 이슈 #8. FIFO 보장 여부 |
| **Redis 연결 실패 시 Interceptor 동작** | Redis 다운 → `validateAndDelete` 예외 → 주문 전면 차단. 의도된 것인지? |
| **대기열 진입 직후 즉시 ZPOPMIN** | 진입과 스케줄러가 동시에 실행될 때, 방금 들어온 유저가 바로 pop 되는 건 정상인데, 순번 조회 전에 토큰이 발급되는 타이밍 이슈 |
| **enterQueue 반환값 정합성** | `enterQueue()` → `getPosition()` 호출하는데, 그 사이에 스케줄러가 ZPOPMIN 하면 rank가 변경되거나 null이 될 수 있음 |

---

## 우선순위별 액션 아이템

| 우선순위 | 이슈 | 권장 조치 |
|---------|------|----------|
| **P0** | 토큰 삭제 후 주문 실패 복구 불가 (#1) | Interceptor에서 검증만, 삭제는 주문 성공 후 |
| **P0** | Domain Port / Infra 시그니처 불일치 (#4) | Port 재설계 또는 별도 Issuer Port 도입 |
| **P1** | userId 추출 방식 오류 (#2) | `ATTR_LOGIN_USER` → `UserInfo.getId()` |
| **P1** | HTTP 메서드 구분 불가 (#3) | `preHandle` 내 POST 체크 추가 |
| **P1** | 토큰 만료 후 상태 누락 (#6) | 별도 상태 or 에러코드 분리 |
| **P2** | Scheduled 스레드 경합 (#7) | 스레드 풀 크기 설정 |
| **P2** | Score 충돌 FIFO 깨짐 (#8) | Redis TIME 활용 |
| **P2** | 폴링 Redis 3회 호출 (#9) | 호출 순서 최적화 or Lua |
| **P3** | Lua 반환 타입 역직렬화 (#5) | 통합 테스트에서 반드시 검증 |
