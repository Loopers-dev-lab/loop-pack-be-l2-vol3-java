# Step 3 — Kafka 기반 선착순 쿠폰 발급

## 목표
- `POST /api/v1/coupons/{couponId}/issue-async` : 발급 요청을 Kafka에 발행만 하고 `requestId` 반환 (202)
- `commerce-streamer` Consumer가 실제 발급 처리 (수량 제한 + 중복 방지)
- `coupon_issue_request` 테이블로 결과(PENDING/SUCCESS/FAILED) 추적
- `GET /api/v1/coupons/issue-requests/{requestId}` : Polling API

## 동시성 전략
`couponId`를 partition key로 설정 → 동일 쿠폰 요청은 동일 파티션 → 단일 스레드 순차 처리 → DB COUNT로 수량 체크

---

## 구현 파일 목록

### commerce-api

| 구분 | 파일 | 변경 |
|------|------|------|
| domain | `Coupon.java` | `totalQuantity` 필드 추가, `of()` 파라미터 추가 |
| domain | `CouponIssueRequest.java` | **NEW** 엔티티 (requestId, couponId, userId, status, failReason) |
| domain | `CouponIssueStatus.java` | **NEW** enum (PENDING, SUCCESS, FAILED) |
| domain | `CouponEvent.java` | **NEW** `IssueRequested` record |
| domain | `CouponIssueRequestRepository.java` | **NEW** 인터페이스 |
| application | `CouponFacade.java` | `requestIssue()`, `getIssueRequestStatus()` 추가 |
| application | `CouponIssueRequestInfo.java` | **NEW** Application DTO |
| infrastructure | `CouponIssueRequestJpaRepository.java` | **NEW** |
| infrastructure | `CouponIssueRequestRepositoryImpl.java` | **NEW** |
| infrastructure | `OutboxEventHandler.java` | `handle(CouponEvent.IssueRequested)` 추가, topic=`coupon-issue-requests`, partitionKey=`couponId` |
| interfaces | `CouponController.java` | POST `/issue-async`, GET `/issue-requests/{requestId}` 추가 |
| interfaces | `CouponDto.java` | `IssueAsyncResponse`, `IssueRequestStatusResponse` 추가 |
| interfaces | `CouponAdminDto.java` | `RegisterRequest`에 `totalQuantity` 추가 |

### commerce-streamer

| 구분 | 파일 | 변경 |
|------|------|------|
| domain | `Coupon.java` | **NEW** 조회 전용 (totalQuantity, expiredAt) |
| domain | `UserCoupon.java` | **NEW** INSERT 전용 |
| domain | `CouponIssueRequest.java` | **NEW** status 업데이트 전용 |
| application | `CouponIssueFacade.java` | **NEW** 수량 체크 + 중복 방지 + 발급 처리 |
| infrastructure | `CouponJpaRepository.java` | **NEW** |
| infrastructure | `UserCouponJpaRepository.java` | **NEW** (`countByCouponTemplateId`, `existsByUserIdAndCouponTemplateId`) |
| infrastructure | `CouponIssueRequestJpaRepository.java` | **NEW** |
| consumer/payload | `CouponIssuePayload.java` | **NEW** (eventId, requestId, couponId, userId) |
| consumer | `CouponIssueRequestConsumer.java` | **NEW** topic=`coupon-issue-requests` |

---

## TDD 순서

### Red Phase — 테스트 먼저

#### 1. `CouponTest` (수정)
```
- totalQuantity <= 0 → 예외
- totalQuantity 정상값으로 생성 성공
```

#### 2. `CouponIssueRequestTest` (NEW — domain)
```
- of(requestId, couponId, userId) → status = PENDING
- markSuccess() → status = SUCCESS
- markFailed(reason) → status = FAILED, failReason 저장
```

#### 3. `CouponFacadeTest` (NEW — commerce-api application)
```
- 쿠폰 없음 → NOT_FOUND
- 유저 없음 → NOT_FOUND
- 정상 요청 → CouponIssueRequest(PENDING) 저장 + ApplicationEventPublisher.publishEvent() verify
```

#### 4. `CouponIssueFacadeTest` (NEW — commerce-streamer application)
```
- eventId 중복 → early return (멱등)
- user_coupon count >= totalQuantity → CouponIssueRequest FAILED ("수량 초과")
- 동일 userId+couponId 존재 → FAILED ("중복 발급")
- 정상 → UserCoupon INSERT + CouponIssueRequest SUCCESS + event_handled 저장
```

#### 5. `CouponApiE2ETest` (NEW — commerce-api E2E)
```
- POST issue-async 성공 → 202 + requestId
- GET issue-requests/{requestId} → { status, failReason }
- GET issue-requests/{존재하지않는ID} → 404
```

#### 6. `CouponIssueConcurrencyTest` (NEW — commerce-streamer)
```
- 총 수량 100, 200 스레드 동시 processIssue() 호출
- user_coupon count == 100
- CouponIssueRequest FAILED count == 100
```

### Green Phase — 핵심 구현

#### `CouponFacade.requestIssue()` (commerce-api)
```java
@Transactional
public String requestIssue(Long couponId, Long userId) {
    couponRepository.findById(couponId).orElseThrow(...);
    userRepository.findById(userId).orElseThrow(...);
    String requestId = UUID.randomUUID().toString();
    couponIssueRequestRepository.save(CouponIssueRequest.of(requestId, couponId, userId));
    eventPublisher.publishEvent(new CouponEvent.IssueRequested(requestId, couponId, userId));
    return requestId;
}
```

#### `OutboxEventHandler` 추가 (commerce-api)
```java
private static final String COUPON_ISSUE_TOPIC = "coupon-issue-requests";

@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void handle(CouponEvent.IssueRequested event) {
    String eventId = UUID.randomUUID().toString();
    String payload = toPayload(Map.of(
        "eventId", eventId,
        "requestId", event.requestId(),
        "couponId", event.couponId(),
        "userId", event.userId()
    ));
    outboxEventJpaRepository.save(
        OutboxEvent.of(eventId, COUPON_ISSUE_TOPIC, event.couponId().toString(), payload)
    );
}
```

#### `CouponIssueFacade.processIssue()` (commerce-streamer)
```java
@Transactional
public void processIssue(CouponIssuePayload payload) {
    if (eventHandledJpaRepository.existsById(payload.eventId())) return;

    CouponIssueRequest request = couponIssueRequestJpaRepository
        .findByRequestId(payload.requestId()).orElseThrow();
    Coupon coupon = couponJpaRepository.findById(payload.couponId()).orElseThrow();

    long issuedCount = userCouponJpaRepository.countByCouponTemplateId(payload.couponId());
    if (issuedCount >= coupon.totalQuantity()) {
        request.markFailed("수량 초과");
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
        return;
    }

    boolean alreadyIssued = userCouponJpaRepository
        .existsByUserIdAndCouponTemplateId(payload.userId(), payload.couponId());
    if (alreadyIssued) {
        request.markFailed("중복 발급");
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
        return;
    }

    userCouponJpaRepository.save(UserCoupon.of(coupon, payload.userId()));
    request.markSuccess();
    eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
}
```

---

## API 명세

```
POST /api/v1/coupons/{couponId}/issue-async   [LoginRequired]
→ 202 Accepted
→ { "data": { "requestId": "uuid" } }

GET /api/v1/coupons/issue-requests/{requestId}  [LoginRequired]
→ 200 OK
→ { "data": { "requestId": "uuid", "status": "PENDING|SUCCESS|FAILED", "failReason": null } }
```

---

## 주의사항 — DDL 충돌

`commerce-streamer`에 `coupon`, `user_coupon`, `coupon_issue_request` 엔티티를 추가하면
두 앱 모두 `ddl-auto: create`로 같은 테이블을 DROP/CREATE 시도 → 충돌.

**조치**: `jpa.yml` local/test 프로파일 `ddl-auto`를 `create` → `update`로 변경 필요.
