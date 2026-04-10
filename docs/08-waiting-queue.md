# 대기열 시스템

## 1. 왜 대기열인가 — Rate Limiting vs Queuing

### 문제

블랙 프라이데이 행사 시작 직후, 평소 초당 100건이던 주문 요청이 초당 10,000건으로 폭증한다.

```
[10,000명 동시 접속]
     └── POST /orders
           ├── 재고 확인 & 차감
           ├── 결제 처리
           └── 주문 저장
           → DB 커넥션 풀 고갈 → 응답 지연 → 타임아웃 → 전체 서비스 장애
```

서버를 10배 늘려도 DB와 PG의 처리 한계는 그대로다. 결국 **시스템이 처리할 수 있는 속도로 요청 자체를 조절**하는 것이 핵심이다.

> 이 개념을 **Back-pressure**라고 한다. 하류 시스템(DB, PG)이 감당할 수 있는 속도만큼만 상류(유저 요청)를 흘려보내는 것.

### Rate Limiting으로 해결이 안 되는 이유

| | Rate Limiting | Queuing |
|---|---|---|
| 초과 요청 처리 | **거부** (429 Too Many Requests) | **보관** (대기열에 적재) |
| 유저 경험 | "나중에 다시 시도하세요" | "잠시만 기다려주세요 (현재 512번째)" |
| 유저 반응 | 새로고침 → 재시도 폭풍 | 기다림 → 순서대로 처리 |
| 적합한 상황 | API 보호, 봇 차단 | 행사 트래픽, 유저가 기다릴 의사가 있는 경우 |

블랙 프라이데이에 "나중에 다시 시도하세요"를 반환하면, 유저는 이탈하거나 더 세게 새로고침한다.
유저가 원하는 것을 기다려서라도 얻을 수 있는 구조가 필요하다.

> Rate Limiting과 Queuing은 양자택일이 아니다. 대기열 자체에도 최대 인원 제한을 둘 수 있고, 봇 차단은 Rate Limiting으로 먼저 걸러낸 뒤 정상 유저만 대기열에 진입시킬 수 있다.

---

## 2. Kafka 버퍼링(R7)과 대기열(R8)의 차이

지난 라운드의 선착순 쿠폰과 이번 대기열은 모두 "요청을 바로 처리하지 않는다"는 점에서 비슷하지만, 핵심 관심사가 다르다.

| | Kafka 버퍼링 (R7 쿠폰) | 대기열 시스템 (R8 주문) |
|---|---|---|
| 유저 경험 | 요청 후 나중에 결과 확인 (fire & forget) | 화면에서 순번을 보며 대기 |
| 결과 전달 | 비동기 polling | 입장 토큰 발급 → 즉시 주문 가능 |
| 제어 대상 | 처리 순서 | **처리 속도 (throughput)** |
| 핵심 관심사 | 메시지 유실 방지, 멱등 처리 | 공정한 순서, 실시간 피드백, 토큰 만료 |

쿠폰은 "신청 완료, 결과는 나중에"가 자연스럽다. 하지만 주문은 유저가 화면 앞에서 기다리고 있다. "내 순서가 언제인지"를 보여줘야 이탈하지 않는다.

---

## 3. Redis Sorted Set 기반 대기열

### 왜 Redis인가

| 요구사항 | Redis가 적합한 이유 |
|---|---|
| 빠른 읽기/쓰기 | 인메모리 기반, μs 단위 |
| 순서 보장 | Sorted Set: score 기반 정렬 |
| 원자적 연산 | `ZADD`, `ZRANK`, `ZPOPMIN` 모두 atomic |
| TTL 지원 | 입장 토큰 만료를 자연스럽게 처리 |

### 핵심 자료구조: Sorted Set

```
ZADD  waiting-queue  {timestamp}  {userId}    // 대기열 진입
ZRANK waiting-queue  {userId}                 // 내 순번 조회 (0-based)
ZCARD waiting-queue                           // 전체 대기 인원
ZPOPMIN waiting-queue {N}                     // 앞에서 N명 꺼내기 (스케줄러)
```

- **score = 진입 시각 (timestamp)** → 먼저 들어온 사람이 앞 순번 (공정성 보장)
- **member = userId** → 같은 userId로 재진입해도 score만 갱신 (중복 진입 자동 방지)

### 진입 흐름

```
[유저] → POST /queue/enter
      → ZADD waiting-queue {now} {userId}
      ← { position: 512, estimatedWaitSeconds: 45 }
```

---

## 4. 입장 토큰 설계

### 역할

대기열을 통과한 유저에게만 주문 API 진입 권한을 부여한다. 토큰이 없는 요청은 주문 API에서 거부한다.

```
[스케줄러] → ZPOPMIN N명 → 토큰 발급 (SET entry-token:{userId} {token} EX 300)
[유저]     → POST /orders (Header: X-Entry-Token: {token})
           → 토큰 검증 & DEL → 주문 처리
```

> 토큰은 주문 처리 **전**에 삭제한다. "검증 성공 → 주문 처리 → 완료 후 DEL" 순서로 구현하면, 주문 성공과 DEL 사이에 서버가 죽었을 때 동일 토큰으로 중복 주문이 가능해진다. 또한 주문 처리 중 같은 토큰으로 중복 요청이 들어오는 것도 차단할 수 없다. 토큰을 먼저 소비(DEL)함으로써 "한 번의 기회"를 원자적으로 보장한다.

### 토큰 관련 Redis 연산

```
SET entry-token:{userId}  {token}  EX {ttl}  // TTL 토큰 발급 (기본 300초, 프로파일별 설정 가능)
GET entry-token:{userId}                      // 토큰 검증
DEL entry-token:{userId}                      // 사용 완료 후 삭제
```

TTL은 `entry-token.ttl-seconds` 프로퍼티로 주입되어 프로파일별로 다르게 설정할 수 있다 (운영: 300초, 테스트: 1초).

### 토큰 TTL이 중요한 이유

토큰을 발급받고 주문 API를 호출하지 않으면(브라우저 닫음, 네트워크 끊김 등) 해당 토큰은 Redis에 영구히 남는다. TTL이 없으면 이탈한 유저의 토큰이 누적되어 Redis 메모리 누수가 발생하고, 오래된 토큰으로 나중에 대기열을 우회하는 보안 문제도 생긴다. TTL을 설정해 이탈한 유저의 토큰을 자동 만료시킨다.

TTL 만료는 대기열 진행과 **간접적으로 연결**된다. 스케줄러는 매 실행마다 현재 active 토큰 수를 확인하고, `MAX_CAPACITY - activeCount`만큼만 새 토큰을 발급한다. 따라서 TTL이 만료되어 active 토큰 수가 줄어들면, 그만큼 다음 배치에서 대기열의 다음 유저에게 토큰이 발급된다.

---

## 5. 스케줄러 기반 순차 입장

### 처리량(TPS) 설계 기준

시스템이 안정적으로 처리할 수 있는 TPS를 기준으로 동시 진행 가능한 최대 유저 수(MAX_CAPACITY)를 결정한다.

```
DB 커넥션 풀: 50
주문 1건 평균 처리 시간: 200ms
→ 이론적 최대 TPS: 50 / 0.2 = 250 TPS
→ 안전 마진 70%: 175 TPS (실측 후 200으로 조정)
→ MAX_CAPACITY = 200 (동시에 주문 진행 중인 유저 최대 수)
```

### Capacity 기반 토큰 발급

스케줄러는 100ms마다 현재 active 토큰 수를 확인하고, `MAX_CAPACITY - activeCount`만큼 대기열에서 꺼내 토큰을 발급한다.

```
active 토큰: 180명
→ 발급 가능: 200 - 180 = 20명
→ 대기열에서 20명 pop → 토큰 발급

active 토큰: 200명 (한계 도달)
→ 발급 가능: 0명 → 스케줄러 skip
```

TTL 만료나 주문 완료로 active 토큰이 줄어들면, 그만큼 다음 실행에서 대기열의 다음 유저에게 토큰이 발급된다. 이 방식은 동시 주문 처리 유저 수를 MAX_CAPACITY로 일정하게 유지하여 DB 커넥션 풀을 보호한다.

### Thundering Herd 문제

capacity 기반에서도 Thundering Herd는 이론적으로 발생할 수 있다.

```
200명이 동시에 주문 완료 or TTL 만료
→ active 토큰: 0명
→ 스케줄러: 200 - 0 = 200명 한번에 발급
→ 200명 동시에 POST /orders → DB 커넥션 스파이크
```

다만 현실적으로는 완화된다. 주문 완료 타이밍이 자연스럽게 분산되고, 토큰 발급 시점이 달라 TTL 만료도 분산되기 때문이다. 또한 100ms 간격으로 스케줄러가 실행되므로 한 번에 MAX_CAPACITY 전체가 발급되는 극단적인 상황은 드물다.

### Thundering Herd 완화 전략

| 전략 | 목적 | 비고 |
|---|---|---|
| **Capacity 기반 발급** | active 토큰 수 상한 고정 → DB 부하 제어 | 현재 구현 |
| **Jitter** | 토큰마다 랜덤 딜레이(0~2초) 부여 → 주문 API 호출 시점 분산 | 토큰에 `activateAt` 필드 필요, 클라이언트가 준수해야 효과 있음 |
| **주문 API Rate Limit** | 대기열과 독립적인 최종 안전장치 → 초과 시 429 반환 | Thundering Herd 완화보다 장애 대비 목적에 가까움, Rate Limit 수치는 MAX_CAPACITY보다 여유 있게 설정 필요 |

> 대기열은 피크를 평탄화(smoothing)하는 것이지, 부하를 없애는 것이 아니다. 하류 시스템의 한계를 항상 염두에 두고 설계해야 한다.

---

## 6. 순번 조회 & 예상 대기 시간

### 순번 조회 응답

```json
{
  "position": 128,
  "totalWaiting": 850,
  "estimatedWaitSeconds": 43,
  "token": null
}
```

토큰이 발급되면 `token` 필드가 채워진다. 클라이언트는 `position: 0`이 되거나 `token`이 null이 아닐 때 주문 API를 호출한다.

### 예상 대기 시간 계산

```
예상 대기 시간 = 내 순번 / 초당 처리량(TPS)

e.g. 순번 128, TPS 200 → 128 / 200 = 0.64초
```

이 수치는 추정값이다. 토큰 미사용(만료)이나 시스템 상태에 따라 달라지므로, "약 N초"로 표현한다.

---

## 7. Polling vs SSE

유저에게 순번을 알려주는 방식은 세 가지다.

| | Polling | Long Polling | SSE (Server-Sent Events) |
|---|---|---|---|
| 방식 | 클라이언트가 주기적으로 질의 | 클라이언트 요청 후 서버가 변경 시점까지 응답 보류 | 서버가 변경 시점에 Push |
| 구현 복잡도 | 낮음 | 중간 | 중간 |
| 서버 부하 | 대기 인원 × 조회 주기 | 대기 인원 × 1 커넥션 유지 (응답 보류 동안) | 대기 인원 × 1 커넥션 유지 |
| 지연 | 조회 주기만큼 | 거의 없음 | 거의 없음 |

### Long Polling이란

클라이언트가 요청을 보내면 서버가 즉시 응답하지 않고, 조건이 충족될 때까지 커넥션을 유지한 뒤 응답을 반환하는 방식이다.

```
클라이언트 → GET /position
서버: 토큰 아직 없음 → 응답 보류 (커넥션 유지)
서버: 스케줄러가 토큰 발급 → 그때 응답 반환
클라이언트: token 받으면 주문 API 호출
```

서버 구현 방식으로는 내부에서 Redis를 주기적으로 체크하거나, Spring의 `DeferredResult` / `CompletableFuture`로 비동기 응답을 보류하는 방법이 있다.

### 이 과제에서 Polling을 선택하는 이유

SSE는 연결 유지 설정(로드밸런서 timeout 등) 이슈가 있고, 구현 복잡도가 높다. Redis 기반이므로 순번 조회 자체는 빠르고, 수초 지연이 허용 가능한 비즈니스에서 Polling으로 충분하다.

### Polling 부하 고려

대기 인원이 많으면 순번 조회 요청 자체가 부하가 된다. 순번 구간별로 조회 주기를 달리하는 방식으로 완화할 수 있다.

```
순번 1~100:    1초마다 조회 (곧 입장, 빠른 피드백 필요)
순번 100~500:  3초마다 조회
순번 500+:     5초마다 조회
```

---

## 8. Graceful Degradation — Redis 장애 시

대기열의 핵심 인프라인 Redis가 죽으면?

| 전략 | 설명 | 장단점 |
|---|---|---|
| 전면 차단 | 대기열 진입 막고 "잠시 후 다시 시도" 안내 | 안전하지만 서비스 중단 |
| 대기열 우회 (bypass) | 주문 API 직접 접근 허용 | 서비스 유지, 과부하 위험 |
| Fallback 큐 | 로컬 메모리 큐로 임시 전환 | 순번 정확성 떨어지지만 서비스 유지 |

> 정답은 없다. "Redis 장애 시 우리 서비스는 어떻게 동작해야 하는가?"를 사전에 정의해두는 것 자체가 중요하다. 장애가 발생한 뒤에 판단하면 늦다.

---

## 9. 구현: 파일 구조

```
domain/queue/
  WaitingQueue.java               ← 대기열 도메인 (진입, 순번, 예상 대기 시간 계산)
  EntryToken.java                 ← 토큰 도메인 (발급, 검증)
  WaitingQueueRepository.java     ← 인터페이스 (enter, getPosition, popFront, getSize)
  EntryTokenRepository.java       ← 인터페이스 (issue, find, delete)

infrastructure/queue/
  WaitingQueueRedisStore.java     ← Sorted Set 구현 (ZADD, ZRANK, ZPOPMIN, ZCARD)
  EntryTokenRedisStore.java       ← String 구현 (SET EX, GET, DEL)

application/queue/
  QueueFacade.java                ← enter(), getPosition()
  QueueScheduler.java             ← @Scheduled(fixedDelay=100) → ZPOPMIN → 토큰 발급

interfaces/api/queue/
  QueueV1Controller.java          ← POST /api/v1/queue/enter, GET /api/v1/queue/position
  QueueV1Dto.java                 ← EnterRequest, EnterResponse, PositionResponse
```

### 주문 API 수정 포인트

```
interfaces/api/order/OrderV1Controller.java
  → createOrder() 진입 시 X-Entry-Token 헤더 검증
  → 토큰 유효하면 진행, 유효하지 않으면 403
  → 주문 완료 후 entryTokenRepository.delete(userId)
```

---

## 10. 핵심 패턴

### 대기열 진입

```java
// WaitingQueueRedisStore
public long enter(Long userId) {
    double score = System.currentTimeMillis();
    redisTemplate.opsForZSet().addIfAbsent(QUEUE_KEY, userId.toString(), score);
    Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString());
    return rank == null ? 1L : rank + 1;  // 1-based position
}
```

### 스케줄러 토큰 발급

```java
// QueueScheduler
@Scheduled(fixedDelay = 100)
public void issueTokens() {
    try {
        List<Long> userIds = waitingQueueRepository.popFront(BATCH_SIZE);
        for (Long userId : userIds) {
            EntryToken token = EntryToken.issue(userId);
            entryTokenRepository.save(token);
            log.debug("Entry token issued. userId={}", userId);
        }
    } catch (Exception e) {
        log.error("Failed to issue entry tokens. Skipping batch.", e);
    }
}
```

> Redis 장애 시 배치를 skip하고 다음 실행을 기다린다. try-catch가 없으면 스택트레이스가 100ms마다 출력되어 로그가 오염된다.

### 토큰 검증 (주문 API)

```java
// OrderV1Controller
@PostMapping
public ApiResponse<OrderV1Dto.CreateOrderResponse> createOrder(
    @RequestHeader("X-Entry-Token") String token,
    @AuthenticationPrincipal Long userId, ...) {

    // 토큰 검증과 동시에 삭제 (주문 처리 전)
    queueFacade.validateAndConsumeToken(userId, token);

    OrderInfo result = orderFacade.order(command);
    return ApiResponse.success(OrderV1Dto.CreateOrderResponse.from(result));
}
```

> 주문 완료 후 DEL하는 방식은 주문 성공~DEL 사이에 서버가 죽으면 동일 토큰으로 재주문이 가능하고, 주문 처리 중 중복 요청도 차단할 수 없다. 주문 실패 시 재시도가 불가능해지는 단점이 있지만, 중복 주문 방지와 원자적 일회성 보장을 위해 주문 전 삭제를 선택한다.

---

## 11. 운영 지표

대기열 시스템은 눈에 보이지 않는 곳에서 유저 경험을 결정한다.

| 지표 | 설명 | 왜 중요한가 |
|---|---|---|
| **Queue Depth** | 현재 대기 인원 (`ZCARD`) | 급격히 증가하면 유입 > 처리량 신호 |
| **Avg Wait Time** | 진입 → 토큰 발급까지 평균 시간 | 유저 체감 품질의 핵심 |
| **P99 Wait Time** | 상위 1% 유저의 대기 시간 | 평균 정상이어도 P99 높으면 특정 시점 병목 |
| **Token Conversion Rate** | 토큰 발급 → 주문 완료 비율 | < 50%면 TTL이 짧거나 주문 UX 문제 |
| **Token Expiry Rate** | 토큰 만료(이탈) 비율 | > 30%면 유저가 기다리다 포기하고 있다는 의미 |
| **Scheduler Health** | 스케줄러 마지막 실행 시각 | 1분 이상 미실행 시 대기열 전체가 멈춤 |
