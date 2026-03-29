# 선착순 쿠폰 발급 파이프라인 학습 노트

> 이 문서는 실제 프로젝트(loop-pack-be-l2-vol3-java) Step 3 구현 과정에서 나온 대화를 정리한 학습 자료입니다.

---

## 0. 시작 전: 설계 결정 사항 정리

### 문제 상황

기존 동기 발급 API:
```
POST /api/v1/coupons/{id}/issue
  → CouponFacade → DB에서 수량 확인 → UserCoupon 저장
```

1,000명이 동시에 요청하면?
→ 여러 스레드가 동시에 `수량 확인`을 통과 → **수량 초과 발급** 발생 가능

### Q&A로 결정된 것들

| 질문 | 결정 | 이유 | TODO |
|------|------|------|------|
| 유저가 결과를 어떻게 확인? | A. Polling | 구현 단순, requestId로 상태 조회 | B 전환: SSE/WebSocket, 기준: 처리 P99 > 3s 또는 폴링 TPS > 500 |
| 선착순 수량 제한을 어디서? | A. Consumer에서 DB COUNT | 파티션 키로 직렬화 보장 | B 전환: Redis DECR, 기준: P99 > 100ms 또는 TPS > 1,000 |
| 발급 요청 상태를 어디에? | A. DB 테이블 | 영속성 필요 | B 전환: Redis, 기준: 10M+ 행 또는 조회 P99 > 50ms |

---

## 1. 전체 아키텍처

```
[commerce-api] Producer 측
  POST /api/v1/coupons/{couponId}/issue-async
  ┌──────────────────────────────────────────────────────────┐
  │ CouponFacade.requestIssueAsync()                         │
  │   ① 유저 인증                                             │
  │   ② 쿠폰 존재/유효성 검증                                │
  │   ③ CouponIssueRequest(PENDING) DB 저장                  │
  │   ④ Kafka 발행 (key = couponId, for 파티션 직렬화)       │
  │   ⑤ requestId 즉시 반환 (202 Accepted)                   │
  └──────────────────────────────────────────────────────────┘
                         ↓
           Kafka Topic: coupon.issue.requests
           (같은 couponId → 같은 파티션 → 순서 보장)
                         ↓
[commerce-api] Consumer 측 (같은 앱, 별도 컨슈머 스레드)
  ┌──────────────────────────────────────────────────────────┐
  │ CouponIssueConsumer.consume()                            │
  │   → CouponIssueRequestProcessor.process() [별도 @Transactional] │
  │     ① CouponIssueRequest 조회 (requestId)                │
  │     ② 쿠폰 유효성 재확인                                  │
  │     ③ 수량 체크: countByCouponId < maxIssuable            │
  │     ④ 중복 체크: existsByUserIdAndCouponId               │
  │     ⑤ UserCoupon 저장 + request → SUCCESS                │
  │        또는 request → FAILED (사유 기록)                  │
  └──────────────────────────────────────────────────────────┘
                         ↓
           coupon_issue_requests 테이블 (PENDING → SUCCESS/FAILED)

[commerce-api] Polling 측
  GET /api/v1/coupons/issue-requests/{requestId}
  ┌────────────────────────────────────────────┐
  │ CouponFacade.getIssueRequestStatus()       │
  │   → CouponIssueRequest 상태 조회           │
  │   → PENDING / SUCCESS / FAILED 반환        │
  └────────────────────────────────────────────┘
```

---

## 2. 핵심 개념: Kafka 파티션 키로 동시성 문제 해결

### 왜 DB 락 없이 동시성이 보장되는가?

```
일반 동기 API (문제 상황):
  스레드 A: countByCouponId() = 99  (maxIssuable = 100)
  스레드 B: countByCouponId() = 99  ← 동시에 읽음! 둘 다 통과
  스레드 A: UserCoupon 저장 → 100번째
  스레드 B: UserCoupon 저장 → 101번째 ← 초과 발급!
```

```
Kafka 파티션 직렬화 (해결):
  couponId = 42 → 항상 파티션 7로 라우팅
  파티션 7의 Consumer는 단일 스레드로 순차 처리

  메시지 A: countByCouponId() = 99 → 통과 → 저장 → 100개
  메시지 B: countByCouponId() = 100 → 거절 (maxIssuable 초과)
```

**핵심**: 같은 couponId의 요청은 항상 같은 파티션 → 컨슈머 1개가 순차 처리 → DB 락 없이 동시성 보장.

### 파티션 키 = couponId

```java
// CouponFacade.requestIssueAsync()
kafkaTemplate.send(
    OutboxEventTopics.COUPON_ISSUE,
    couponId.toString(),   // ← 파티션 키: couponId
    new CouponIssueMessage(request.getRequestId(), couponId, user.getId())
);
```

---

## 3. 상태 흐름

```
POST /issue-async 요청
        ↓
CouponIssueRequest (PENDING)
        ↓ Kafka 메시지 처리
        ├── 쿠폰 없음/만료 → FAILED ("쿠폰을 찾을 수 없거나 만료됨")
        ├── 수량 초과      → FAILED ("선착순 마감")
        ├── 중복 발급      → FAILED ("중복 발급")
        └── 정상 발급      → SUCCESS + UserCoupon 저장
```

---

## 4. 트랜잭션 설계

### 왜 Processor를 별도 빈으로 분리했는가?

```java
// ❌ 이렇게 하면 @Transactional이 동작하지 않는다 (self-invocation)
@Component
public class CouponIssueConsumer {
    @Transactional
    private void process(CouponIssueMessage message) { ... } // 같은 클래스 내 호출 → 프록시 미경유
}

// ✅ 별도 빈으로 분리
@Component
public class CouponIssueRequestProcessor {
    @Transactional          // Spring AOP 프록시 경유 → TX 적용됨
    public void process(CouponIssueMessage message) { ... }
}

@Component
public class CouponIssueConsumer {
    private final CouponIssueRequestProcessor processor; // 주입받아 사용
}
```

이것은 Step 1에서 나왔던 `REQUIRES_NEW` self-invocation 문제와 같은 원리.

### 각 메시지가 독립 트랜잭션

```
배치 메시지 [A, B, C] 도착

메시지 A → processor.process(A) → @Transactional TX-1 → SUCCESS
메시지 B → processor.process(B) → @Transactional TX-2 → FAILED (중복)
메시지 C → processor.process(C) → @Transactional TX-3 → SUCCESS

acknowledgment.acknowledge() → 배치 전체 offset 커밋
```

B가 실패(비즈니스 실패)해도 A, C에 영향 없음. 각 TX가 독립.

---

## 5. 미래 개선 로드맵 (TODO)

### Q1 → B: Polling → WebSocket/SSE 전환

**현재**: 클라이언트가 `/issue-requests/{requestId}` 를 주기적으로 GET 요청

**언제 전환하는가?**
- 발급 처리 시간 P99 > 3초 (유저가 3번 이상 폴링해야 하는 경우)
- 폴링 요청 TPS > 500 (불필요한 트래픽 부담)

**어떻게 전환하는가?**
```
A. Spring WebSocket + STOMP
   → Consumer가 SUCCESS/FAILED 처리 후 특정 토픽으로 push

B. SSE (SseEmitter)
   → /api/v1/coupons/issue-requests/{requestId}/stream 엔드포인트
   → Consumer 완료 후 이벤트 emit
```

**테스트 방법**: 1,000명 동시 발급 시나리오에서 폴링 평균 횟수 측정. 평균 3회 미만이면 폴링 유지 판단.

---

### Q2 → B: DB COUNT → Redis Counter 전환

**현재**: Consumer에서 `userCouponRepository.countByCouponId()` DB 쿼리로 수량 체크

**한계**: Kafka 파티션 직렬화로 동시성은 해결되지만, COUNT 쿼리가 매 메시지마다 발생

**언제 전환하는가?**
- 쿠폰 발급 처리 시간 P99 > 100ms (DB COUNT 쿼리가 병목)
- 단일 쿠폰 발급 TPS > 1,000 이상

**어떻게 전환하는가?**
```java
// CouponFacade.requestIssueAsync() 에서 Kafka 발행 전에:
Long remaining = redisTemplate.opsForValue().decrement("coupon:remaining:" + couponId);
if (remaining < 0) {
    // Redis Counter 복원
    redisTemplate.opsForValue().increment("coupon:remaining:" + couponId);
    throw new CoreException(ErrorType.COUPON_ISSUE_LIMIT_EXCEEDED);
}
// 이후 Kafka 발행...
```

Redis `DECR`은 원자적(Atomic) 연산 → 분산 환경에서도 정확한 선착순 보장.

**주의사항**:
- Redis 장애 시 카운터 초기화 필요 → DB에서 재산출하는 복구 절차 설계 필요
- 쿠폰 생성 시 `SET coupon:remaining:{couponId} {maxIssuable}` 초기화 필요

**테스트 방법**: k6/Gatling으로 10,000 동시 요청 → 처리 시간 P99 및 발급 수량 정확성 검증.

---

### Q3 → B: DB 요청 저장 → Redis 전환

**현재**: `coupon_issue_requests` DB 테이블에 저장

**언제 전환하는가?**
- 테이블 누적 10M+ 행 초과 (인덱스 성능 저하)
- 상태 조회 P99 > 50ms

**주의사항**: Redis TTL 만료 시 상태 유실 → 영속성 전략 별도 설계 필요 (예: 발급 완료는 DB에도 기록).

---

## 6. 현재 구현의 한계 (Known Issues)

### Kafka 발행 실패 시 PENDING 영구 유지

```
CouponFacade.requestIssueAsync():
  1. CouponIssueRequest(PENDING) DB 저장 ✅
  2. kafkaTemplate.send() 실패 ❌

→ PENDING 상태가 영구히 남음 → 유저는 상태를 계속 폴링
```

**개선 방향**: `commerce-batch`에 `@Scheduled` 릴레이 추가
- PENDING 상태인 요청 중 N분 초과된 것을 재발행

### Consumer 처리 중 서버 재시작

Manual ACK를 사용하므로, ACK 전에 서버가 재시작되면 Kafka가 메시지를 재전달.
Consumer는 `process()` 내에서 이미 SUCCESS/FAILED인 요청을 재처리할 수 있음.

**개선 방향**: `process()` 시작 시 request status 확인 → PENDING이 아니면 스킵.

---

## 7. 엔드포인트 요약

| 메서드 | 경로 | 설명 | 응답 |
|--------|------|------|------|
| `POST` | `/api/v1/coupons/{couponId}/issue-async` | 비동기 발급 요청 | `202 + requestId` |
| `GET`  | `/api/v1/coupons/issue-requests/{requestId}` | 상태 폴링 | `PENDING / SUCCESS / FAILED` |

---

## 8. 쿠폰 생성 시 maxIssuable 설정

선착순 제한이 있는 쿠폰은 Admin API에서 `maxIssuable` 설정:

```json
POST /api-admin/v1/coupons
{
  "name": "선착순 100명 할인",
  "type": "FIXED",
  "value": 3000,
  "minOrderAmount": 10000,
  "expiredAt": "2026-12-31T23:59:59+09:00",
  "maxIssuable": 100
}
```

`maxIssuable = null`이면 수량 제한 없음.