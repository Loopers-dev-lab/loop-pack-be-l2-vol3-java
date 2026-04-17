# 이벤트 파이프라인 리뷰 — 시니어 아키텍트 관점

---

## 0. 과제 범위 요약

| Step | 주제 | 핵심 |
|---|---|---|
| Step 1 | ApplicationEvent로 경계 나누기 | 핵심 로직 vs 부가 로직 판단 + 트랜잭션 분리 |
| Step 2 | Kafka 이벤트 파이프라인 | Outbox → Kafka → commerce-streamer, product_metrics 집계, 멱등 처리 |
| Step 3 | 선착순 쿠폰 발급 | API → Kafka 발행만 → Consumer 순차 처리, 수량 제한 동시성 제어 |

---

## 1. 현재 코드베이스 분석

### 1.1 현재 인프라 상태

| 구성 요소 | 상태 | 비고 |
|---|---|---|
| commerce-api | Kafka 미사용 | 모든 흐름 동기 처리, kafka.yml 미임포트 |
| commerce-streamer | DemoKafkaConsumer 1개 | demo.internal.topic-v1 소비만 |
| modules/kafka | 설정 완료 | KafkaTemplate, BATCH_LISTENER (manual ack, concurrency 3, max poll 3000) |
| Docker Kafka | KRaft 모드 | 단일 브로커, port 19092 (외부), 토픽 자동 생성 비활성화 |

### 1.2 현재 주문 흐름 (`OrderFacade.createOrder`)

```
[단일 TX — @Transactional]
  1. 상품 비관적 락 (deadlock 방지 위해 ID 정렬)
  2. 브랜드 조회 (N+1 방지)
  3. 스냅샷 생성 (OrderItem)
  4. 재고 차감 (Product.decreaseStock)
  5. 쿠폰 적용 (CouponFacade.applyCouponToOrder — CAS UPDATE)
  6. 주문 저장 (Order.create)
  7. 쿠폰-주문 연결 (CouponIssue.linkOrder)
[TX commit]
```

**문제점:**
- 부가 로직(유저 행동 로깅, 판매량 집계, 알림)이 존재하지 않지만, 추가된다면 TX 안에 들어갈 구조
- 쿠폰 적용은 가격 계산에 직접 영향 → 핵심 로직 (분리 불가)

### 1.3 현재 좋아요 흐름 (`LikeFacade.addLike`)

```
[단일 TX — @Transactional]
  1. 상품 존재 확인
  2. 중복 좋아요 확인 (existsByMemberIdAndProductId)
  3. Like INSERT
  4. Product.incrementLikeCount (SQL atomic UPDATE)
[TX commit]

[Controller에서 인라인 처리]
  5. 캐시 무효화 (productCachePort.evictProductDetail + evictProductList)
```

**문제점:**
- Like INSERT(핵심)와 likeCount UPDATE(부가/집계)가 같은 TX
- 집계 실패 시 좋아요 자체도 롤백됨
- 캐시 무효화가 Controller에 인라인 — 관심사 분리 안 됨

### 1.4 현재 좋아요 집계 구조

```
product_like_stats 테이블:
  product_id (PK), like_count, synced_at

LikeCountSyncTasklet (commerce-batch):
  1단계: likes COUNT(*) GROUP BY product_id → REPLACE INTO product_like_stats
  2단계: product_like_stats.like_count → Product.like_count 드리프트 보정

역할: Product.like_count의 정합성 안전망 (incrementLikeCount 누락 시 보정)
```

### 1.5 현재 상품 조회 흐름

```
ProductFacade.getProductDetailCached():
  L1(Caffeine) → L2(Redis) → DB → 캐시 저장

조회수 추적: 없음 (7주차에서 신규 추가)
```

### 1.6 현재 쿠폰 구조

```
Coupon: name, discountType, discountValue, minOrderAmount, expiredAt
CouponIssue: couponId, memberId, status(AVAILABLE/USED/EXPIRED), expiredAt

수량 제한: 없음 → 7주차에서 선착순 수량 제한 추가 필요
중복 발급 방지: 없음 (같은 쿠폰을 같은 유저가 여러 번 발급 가능)
```

---

## 2. Step 1 분석 — 핵심 vs 부가 로직 판단 기준

### 2.1 판단 프레임워크

```
핵심 로직 = "이것이 실패하면 사용자 요청 자체가 실패해야 하는가?"
  → YES: 핵심 TX 안에 유지
  → NO:  이벤트로 분리 가능

부가 로직 = "이것이 실패해도 사용자에게는 성공으로 보여야 하는가?"
  → YES: 이벤트 분리 (eventual consistency)
```

### 2.2 주문 플로우 — 핵심 vs 부가

| 처리 | 핵심/부가 | 판단 근거 | 이벤트 분리 |
|---|---|---|---|
| 재고 차감 | **핵심** | 재고 없으면 주문 불가. 즉시 검증 필요 | X |
| 쿠폰 적용 | **핵심** | 할인 금액이 totalPrice 계산에 직접 영향 | X |
| 주문 저장 | **핵심** | 주문 자체 | X |
| 유저 행동 로깅 | 부가 | 로깅 실패해도 주문은 성공해야 함 | O |
| 판매량 집계 | 부가 | 집계 실패해도 주문에 영향 없음 | O |
| 주문 알림 | 부가 | 알림 실패해도 주문은 완료 | O |

```
분리 후:
  [TX] 재고 차감 + 쿠폰 적용 + 주문 저장 → commit
  [AFTER_COMMIT] OrderCreatedEvent 발행
    → 유저 행동 로깅 (비동기)
    → Outbox 기록 → Kafka → product_metrics.sales_count 집계
```

### 2.3 좋아요 플로우 — 핵심 vs 부가

| 처리 | 핵심/부가 | 판단 근거 | 이벤트 분리 |
|---|---|---|---|
| Like INSERT | **핵심** | 사용자 의도 (좋아요 누르기) | X |
| Product.incrementLikeCount | 부가 | "집계 실패와 무관하게 좋아요는 성공" — 과제 요구사항 | O |
| 캐시 무효화 | 부가 | 캐시 무효화 실패해도 좋아요는 성공해야 함 | O |

```
분리 후:
  [TX] Like INSERT + Outbox 기록 → commit
  [AFTER_COMMIT] LikeCreatedEvent 발행
    → Product.incrementLikeCount (best-effort, 같은 스레드)
    → 캐시 무효화
    → Outbox → Kafka → product_metrics.like_count 집계
```

> **incrementLikeCount를 완전히 제거하지 않는 이유:**
> 사용자가 좋아요 직후 목록을 새로고침하면 반영되어 있기를 기대한다.
> AFTER_COMMIT에서 best-effort로 실행하되, 실패해도 Like 자체는 이미 저장됨.
> product_metrics + 배치가 최종 정합성을 보장하는 안전망 역할.

### 2.4 상품 조회 플로우 — 조회수 추적

| 처리 | 핵심/부가 | 판단 근거 | 이벤트 분리 |
|---|---|---|---|
| 상품 데이터 반환 | **핵심** | 사용자 요청 목적 | X |
| 조회수 기록 | 부가 | 조회수 기록 실패해도 상품은 보여야 함 | O |

```
분리 후:
  [TX 없음 — 읽기] 상품 조회 + 캐시
  [이벤트] ProductViewedEvent 발행 (조회수 로깅)
    → Outbox 기록 → Kafka → product_metrics.view_count 집계
```

> **조회 이벤트는 Outbox를 경유할 필요가 있는가?**
> 조회는 DB 쓰기가 없으므로 Outbox TX에 묶을 수 없다.
> 선택지:
>   A. 조회 시 별도 TX로 Outbox INSERT → 오버헤드
>   B. ApplicationEvent → 직접 Kafka 발행 (fire-and-forget) → 유실 가능
>   C. ApplicationEvent → Redis 버퍼 → 배치로 Kafka 발행
>
> 조회수는 정확성보다 근사치가 중요. 일부 유실 허용 가능.
> → B 방식 (직접 Kafka 발행) 또는 메모리 버퍼 후 배치 발행이 실용적.
> → 08 설계에서 최종 결정.

### 2.5 주문 취소 플로우

| 처리 | 핵심/부가 | 이벤트 분리 |
|---|---|---|
| Order.cancel() | **핵심** | X |
| 재고 복원 | **핵심** | X (재고 복원 실패 시 데이터 불일치) |
| 쿠폰 복원 | **핵심** | X (쿠폰 복원 실패 시 고객 손해) |
| 유저 행동 로깅 | 부가 | O |
| 판매량 차감 집계 | 부가 | O |

### 2.6 @TransactionalEventListener phase 선택 기준

| phase | 실행 시점 | 적합한 용도 |
|---|---|---|
| BEFORE_COMMIT | TX 커밋 직전 | TX 안에서 추가 검증/기록이 필요할 때 |
| **AFTER_COMMIT** | TX 커밋 성공 후 | **부가 로직 (집계, 로깅, 알림, Kafka 발행)** |
| AFTER_ROLLBACK | TX 롤백 후 | 롤백 시 보상 작업 |
| AFTER_COMPLETION | TX 완료 후 (성공/실패 무관) | 리소스 정리 |

**이 프로젝트에서는 AFTER_COMMIT이 기본.**
핵심 TX 성공 후에만 부가 로직을 실행해야 하므로.

### 2.7 @Async 적용 판단

```
@TransactionalEventListener(AFTER_COMMIT)만 쓰면:
  → 같은 스레드에서 실행
  → 이벤트 리스너 완료까지 HTTP 응답 지연

@TransactionalEventListener(AFTER_COMMIT) + @Async:
  → 별도 스레드에서 실행
  → HTTP 응답 즉시 반환
  → 단, 실패 시 사용자에게 노출 안 됨 (예외 은닉)

판단:
  - 유저 행동 로깅, Kafka 발행 → @Async (응답 지연 불필요)
  - incrementLikeCount → 동기 (즉시 반영 UX, 단 실패해도 Like는 저장됨)
  - 캐시 무효화 → 동기 (다음 조회 시 최신 데이터 보장)
```

---

## 3. Step 2 분석 — Kafka 이벤트 파이프라인

### 3.1 ApplicationEvent vs Kafka 경계 판단

```
ApplicationEvent = 이 JVM 안에서 후속 처리를 트리거
  → 메모리 기반, 보존 없음, JVM 재시작 시 유실
  → 빠름, 의존성 없음

Kafka = 시스템 경계를 넘는 이벤트 전달
  → 디스크 보존, 재처리 가능, At Least Once
  → 네트워크 I/O, 상대적 느림

판단 기준:
  "이 이벤트가 다른 애플리케이션(commerce-streamer)에서 처리되어야 하는가?"
  → YES: Kafka
  → NO:  ApplicationEvent만으로 충분
```

| 이벤트 | ApplicationEvent | Kafka | 근거 |
|---|---|---|---|
| incrementLikeCount | O | X | 같은 JVM, 즉시 반영, DB UPDATE 1건 |
| 캐시 무효화 | O | X | 같은 JVM, Redis eviction |
| 유저 행동 로깅 | O | O | 내부 로깅 + 외부 데이터 파이프라인 |
| product_metrics 집계 | X | **O** | commerce-streamer에서 처리 |
| 선착순 쿠폰 발급 | X | **O** | commerce-streamer에서 처리 |

### 3.2 Outbox와 ApplicationEvent의 역할 분리

```
두 가지는 동시에 사용한다. 역할이 다르다.

[TX 시작]
  Like INSERT
  Outbox INSERT (eventType: LIKE_CREATED, payload: {productId, memberId, ...})
[TX commit]

[AFTER_COMMIT — ApplicationEvent]
  → incrementLikeCount (best-effort, 동기)
  → 캐시 무효화 (동기)
  → 유저 행동 로깅 (@Async)

[Outbox Poller — 별도 스케줄러]
  → Outbox PENDING 조회 → Kafka 발행 → Outbox PROCESSED

ApplicationEvent가 하는 것: 즉시 반영이 필요한 내부 후속 처리
Outbox가 하는 것: 시스템 경계를 넘는 이벤트의 보장 발행
```

### 3.3 Outbox → Kafka 발행 흐름

```
[commerce-api]

  도메인 TX:
    [TX] 도메인 데이터 변경 + event_outbox INSERT → commit

  Outbox Poller (@Scheduled, 5초):
    1. SELECT * FROM event_outbox WHERE status = 'PENDING' ORDER BY id LIMIT 100
    2. 각 건에 대해 Kafka 발행 (KafkaTemplate.send())
    3. 발행 성공 → UPDATE status = 'PROCESSED'
    4. 발행 실패 → retry_count++, 최대 초과 시 FAILED + 운영 알림

  event_outbox 테이블:
    id, aggregate_type, aggregate_id, event_type, payload(JSON),
    status(PENDING/PROCESSED/FAILED), created_at, processed_at, retry_count
```

### 3.4 토픽 설계

| 토픽 | Key | 이벤트 유형 | Producer | Consumer |
|---|---|---|---|---|
| `catalog-events` | productId | PRODUCT_VIEWED, LIKE_CREATED, LIKE_REMOVED | commerce-api | commerce-streamer |
| `order-events` | orderId | ORDER_CREATED, ORDER_CANCELLED | commerce-api | commerce-streamer |
| `coupon-issue-requests` | couponId | COUPON_ISSUE_REQUESTED | commerce-api | commerce-streamer |

**Key 설계 근거:**
- catalog-events key=productId → 같은 상품의 이벤트는 같은 파티션 → 순서 보장
- order-events key=orderId → 같은 주문의 이벤트는 같은 파티션
- coupon-issue-requests key=couponId → 같은 쿠폰의 발급 요청은 같은 파티션 → 순차 처리로 수량 제어

### 3.5 Consumer (commerce-streamer) 설계

```
[commerce-streamer]

  catalog-events Consumer:
    → PRODUCT_VIEWED: product_metrics.view_count += 1
    → LIKE_CREATED: product_metrics.like_count += 1
    → LIKE_REMOVED: product_metrics.like_count -= 1

  order-events Consumer:
    → ORDER_CREATED: product_metrics.sales_count += item.quantity (상품별)
    → ORDER_CANCELLED: product_metrics.sales_count -= item.quantity

  coupon-issue-requests Consumer:
    → COUPON_ISSUE_REQUESTED: 수량 확인 → 발급 or 거절

  공통:
    - manual Ack (AckMode.MANUAL)
    - event_handled 테이블로 멱등 처리
    - version/updated_at 기준 최신 이벤트만 반영
```

### 3.6 product_metrics 테이블 설계

```sql
CREATE TABLE product_metrics (
    product_id    BIGINT PRIMARY KEY,
    like_count    BIGINT NOT NULL DEFAULT 0,
    view_count    BIGINT NOT NULL DEFAULT 0,
    sales_count   BIGINT NOT NULL DEFAULT 0,
    sales_amount  BIGINT NOT NULL DEFAULT 0,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**기존 product_like_stats와의 관계:**
- product_like_stats는 product_metrics로 흡수 (역할 확장)
- like_count + view_count + sales_count + sales_amount 통합 관리
- LikeCountSyncTasklet → MetricsReconcileTasklet로 진화

**Product.like_count 컬럼은 유지:**
- 정렬 인덱스(idx_product_like_count)가 이 컬럼 기준
- 제거하면 좋아요순 정렬 성능 하락
- 비정규화 캐시로 유지, 배치가 product_metrics 기준으로 보정

### 3.7 멱등 처리 설계

```sql
CREATE TABLE event_handled (
    event_id    VARCHAR(100) PRIMARY KEY,
    handled_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**왜 event_handled와 event_log를 분리하는가?**

| 구분 | event_handled | event_log (별도 설계 시) |
|---|---|---|
| 목적 | 멱등성 보장 (중복 체크) | 감사/분석/디버깅 |
| 데이터 | event_id만 (최소) | 전체 페이로드 |
| 조회 패턴 | PK lookup (O(1)) | 범위 검색, 필터링 |
| 수명 | 짧음 (7~30일이면 충분) | 장기 보존 (규제, 감사) |
| 크기 | 작음 (ID만) | 큼 (전체 이벤트 데이터) |

분리하면:
- event_handled는 작고 빨라서 PK lookup이 O(1) 유지
- event_log가 커져도 멱등 체크 성능에 영향 없음
- 각각 독립적인 보존 정책 적용 가능

### 3.8 Producer 설정

```yaml
# acks=all: 모든 ISR에 기록 확인 후 응답 → 메시지 유실 방지
# enable.idempotence=true: 중복 발행 방지 (Producer 레벨)
spring:
  kafka:
    producer:
      acks: all
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
```

### 3.9 Consumer 설정

```yaml
# enable-auto-commit: false → manual Ack
# auto-offset-reset: latest → 신규 Consumer는 최신 메시지부터
spring:
  kafka:
    consumer:
      enable-auto-commit: false
      auto-offset-reset: latest
    listener:
      ack-mode: manual
```

### 3.10 조회 이벤트의 Outbox 경유 여부

```
문제:
  상품 조회 = 읽기 전용 (DB 쓰기 없음)
  → Outbox INSERT를 위한 별도 TX가 필요
  → 조회마다 DB 쓰기 1건 추가 = 오버헤드

선택지:
  A. 별도 TX로 Outbox INSERT          → 정확하지만 오버헤드
  B. 직접 Kafka 발행 (fire-and-forget)  → 일부 유실 허용
  C. 메모리 버퍼 → 주기적 Kafka 발행     → 버퍼 유실 가능 (JVM 재시작)
  D. Kafka 직접 발행 + 실패 시 로그      → 실용적

결정: D
근거:
  - 조회수는 정확성보다 추세가 중요 (±수 건 허용)
  - 조회마다 DB 쓰기를 추가하면 조회 TPS에 영향
  - KafkaTemplate.send()는 내부적으로 배치 + 버퍼링 (효율적)
  - 발행 실패 시 에러 로그만 남기고, 배치로 보정하지 않음
```

---

## 4. Step 3 분석 — 선착순 쿠폰 발급

### 4.1 현재 쿠폰 모델의 한계

```
현재:
  CouponFacade.issueCoupon(couponId, memberId)
  → Coupon 조회 → 만료 확인 → CouponIssue 생성

부족한 것:
  1. 수량 제한 없음 (maxIssuanceCount)
  2. 중복 발급 방지 없음 (같은 쿠폰 + 같은 유저)
  3. 동기 처리 → 1만 명 동시 요청 시 DB 부하
```

### 4.2 Kafka 기반 구조

```
[사용자] → POST /api/v1/coupons/{couponId}/issue-request
  → [commerce-api]
      1. 기본 검증 (쿠폰 존재, 만료 여부)
      2. coupon_issue_request 테이블에 PENDING 상태로 기록
      3. Kafka에 COUPON_ISSUE_REQUESTED 발행 (key=couponId)
      4. 즉시 응답: { requestId, status: PENDING }

  → [Kafka] coupon-issue-requests 토픽

  → [commerce-streamer]
      1. event_handled 확인 (멱등)
      2. Coupon.issuedCount 확인 (수량 초과?)
      3. 중복 발급 확인 (couponId + memberId)
      4. CouponIssue 생성 + Coupon.issuedCount++ (CAS UPDATE)
      5. coupon_issue_request 상태 업데이트 (COMPLETED / REJECTED)

[사용자] → GET /api/v1/coupons/issue-requests/{requestId}
  → 결과 조회 (PENDING / COMPLETED / REJECTED)
```

### 4.3 동시성 제어 — Kafka만으로는 불충분

```
오해: "key=couponId → 같은 파티션 → 순차 소비 → 동시성 해결"

현실:
  1. Consumer 장애 → Rebalancing → 메시지 재처리 (At Least Once)
     → 같은 요청이 2번 처리될 수 있음
  2. Consumer Group 내 파티션 재할당 중 중복 소비 가능
  3. 배치 리스너 (현재 설정: 3000건/poll) → 배치 내에서는 순차이지만
     동일 couponId의 여러 요청이 같은 배치에 포함될 수 있음

결론:
  Kafka는 "부하 버퍼 + 순서 힌트"이지 "동시성 제어 수단"이 아님.
  DB 레벨 동시성 제어가 반드시 필요.
```

### 4.4 DB 레벨 동시성 제어

```sql
-- 1. 수량 제한: CAS UPDATE (Compare-And-Swap)
UPDATE coupon
SET issued_count = issued_count + 1
WHERE id = :couponId
  AND issued_count < max_issuance_count
  AND deleted_at IS NULL;
-- affected rows = 0 → 수량 소진

-- 2. 중복 발급 방지: UNIQUE 제약
ALTER TABLE coupon_issue
ADD UNIQUE INDEX uk_coupon_issue_coupon_member (coupon_id, member_id);
-- INSERT 시 중복이면 예외 → 거절
```

### 4.5 Coupon 모델 확장

```
Coupon 테이블 추가 컬럼:
  max_issuance_count  INT          -- NULL이면 무제한
  issued_count        INT DEFAULT 0 -- 현재 발급 수

coupon_issue_request 테이블 (신규):
  id             BIGINT PK
  coupon_id      BIGINT NOT NULL
  member_id      BIGINT NOT NULL
  status         VARCHAR(20) -- PENDING / COMPLETED / REJECTED
  reject_reason  VARCHAR(100)
  created_at     DATETIME
  completed_at   DATETIME
```

### 4.6 Redis vs Kafka 선착순 처리 비교

```
Redis 방식:
  INCR coupon:{id}:count → 100 이하면 발급
  → 장점: 초고속 (O(1)), 원자적 카운트
  → 단점: Redis 장애 시 발급 불가, 영속성 약함

Kafka 방식:
  API → Kafka → Consumer 순차 처리 → DB CAS UPDATE
  → 장점: 부하 버퍼, 영속성 (디스크), 재처리 가능
  → 단점: Redis보다 느림 (ms vs ns), 순서 보장이 파티션 단위

이 프로젝트 선택: Kafka (과제 요구사항)
  - 단, DB CAS UPDATE로 정확한 수량 제어
  - Kafka는 "폭주 요청 버퍼링" 역할
```

### 4.7 발급 결과 확인 구조

```
선택지:
  A. Polling — GET /coupon-issue-requests/{requestId}
  B. SSE (Server-Sent Events)
  C. WebSocket

결정: A (Polling)
근거:
  - 구현 단순, 인프라 추가 불필요
  - 쿠폰 발급은 수 초 내 완료 → 1~2회 polling이면 충분
  - SSE/WebSocket은 커넥션 유지 오버헤드
```

---

## 5. 아키텍트 점검 — 리스크 분석

### 5.1 AFTER_COMMIT 이벤트 실패 시 대응

```
리스크:
  AFTER_COMMIT에서 incrementLikeCount 실패
  → Like는 저장됨, likeCount는 업데이트 안 됨 → 불일치

대응:
  1. try-catch + 에러 로깅 (예외 전파 방지)
  2. product_metrics (Kafka 경유)가 정확한 집계값 보유
  3. MetricsReconcileTasklet이 주기적으로 Product.like_count 보정
  → 3중 안전망: best-effort 즉시 반영 + Kafka 집계 + 배치 대사
```

### 5.2 Outbox Poller 실패 시

```
리스크:
  Outbox Poller가 Kafka 발행 실패 → PENDING 상태 유지

대응:
  - Poller가 재시도 (retry_count++)
  - 최대 재시도 초과 시 FAILED + 운영 알림
  - Consumer 측 멱등 처리로 중복 발행 안전
```

### 5.3 Consumer 처리 실패 시

```
리스크:
  commerce-streamer가 이벤트 처리 실패
  → manual Ack를 하지 않으면 Kafka가 재전달

대응:
  - 재시도 가능: 멱등 처리로 안전
  - 반복 실패: DLQ (Dead Letter Queue)로 격리
  - DLQ 처리: 운영자 수동 확인 또는 별도 Consumer
```

### 5.4 product_metrics 정합성

```
리스크:
  Kafka 이벤트 유실/순서 역전 → product_metrics 부정확

대응:
  - At Least Once + 멱등 처리 → 유실 방지
  - MetricsReconcileTasklet → 원본 데이터(likes, order_items) 기준 대사
  - product_metrics는 "실시간 근사치", 배치가 "정확한 값" 보정
```

### 5.5 event_outbox vs payment_outbox

```
6주차에서 PaymentOutbox를 설계했다.
7주차에서 event_outbox를 추가한다.

이 둘은 다른 테이블인가, 같은 테이블인가?

분석:
  PaymentOutbox: PG 호출 보장용 (event_type: PAYMENT_REQUEST)
  event_outbox: Kafka 발행 보장용 (event_type: LIKE_CREATED, ORDER_CREATED, ...)

  목적이 다르다:
    PaymentOutbox → PG API 호출 재시도
    event_outbox → Kafka 메시지 발행 재시도

  처리 주체도 다르다:
    PaymentOutbox → Outbox Poller가 PG 호출
    event_outbox → Outbox Poller가 Kafka 발행

결정: 별도 테이블로 분리
근거:
  - 단일 테이블에 두 가지 목적을 혼합하면 Poller 로직이 복잡해짐
  - PaymentOutbox는 PG 호출 + 상태 확인 로직 포함 (Kafka와 완전히 다름)
  - 각각 독립적인 Poller, 독립적인 retry 정책 적용 가능
```

---

## 6. Consumer Group 분리 (Nice-To-Have)

### 6.1 현재 단일 Consumer Group

```
commerce-streamer (Consumer Group: loopers-default-consumer)
  → catalog-events 소비 → product_metrics upsert
  → order-events 소비 → product_metrics upsert
  → coupon-issue-requests 소비 → 쿠폰 발급
```

### 6.2 관심사별 Consumer Group 분리

```
Consumer Group: metrics-collector
  → catalog-events → product_metrics upsert (like, view)
  → order-events → product_metrics upsert (sales)

Consumer Group: coupon-issuer
  → coupon-issue-requests → 선착순 쿠폰 발급

이점:
  - 쿠폰 발급 실패가 metrics 집계에 영향 안 줌
  - 각 Consumer Group 독립 스케일링 가능
  - 장애 격리
```

---

## 7. DLQ 구성 (Nice-To-Have)

### 7.1 DLQ 설계

```
반복 실패 메시지를 격리하여 정상 메시지 처리를 방해하지 않음.

원본 토픽: catalog-events
DLQ 토픽: catalog-events.DLT (Dead Letter Topic)

동작:
  Consumer가 메시지 처리 3회 실패
  → DLQ 토픽으로 이동
  → 운영 알림
  → 수동 확인 후 재처리 or 폐기
```

### 7.2 Spring Kafka DLQ 설정

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
}
```

---

## 8. 고도화 분석 — Outbox Poller 중복 처리

### 8.1 문제

다중 인스턴스에서 Outbox Poller가 같은 PENDING 행을 동시에 SELECT → 같은 이벤트를 Kafka에 2번 발행.

### 8.2 선택지 분석

| 선택지 | 설명 | 문제 |
|---|---|---|
| SELECT FOR UPDATE SKIP LOCKED | 행 잠금, 잠긴 건 건너뜀 | **DB 커넥션 점유** — Kafka 장애 시 잠긴 행 재시도 불가 |
| Debezium CDC | binlog에서 직접 Kafka 발행 | 인프라 추가 (Kafka Connect) |
| 중복 허용 + 멱등 Consumer | 잠금 없이 SELECT → Consumer가 event_handled로 중복 제거 | Consumer PK lookup 1회 (~0.1ms) |

### 8.3 결정: Debezium CDC

- **근거 1**: 실무 적용 전 Debezium 설정 경험 확보에 의의
- **근거 2**: 중복 발행 원천 해결 (binlog 오프셋 기반, 단일 처리)
- **근거 3**: Near real-time (Poller 5초 → Debezium 수백 ms)
- **근거 4**: DB 부하 없음 (SELECT 폴링 제거)

### 8.4 Debezium 구성

```
Docker 추가:
  - kafka-connect (debezium/connect:2.5)
  - MySQL binlog 활성화 (--log-bin, --binlog-format=ROW)

Connector:
  - Debezium MySQL Connector
  - Outbox Event Router SMT
  - route.by.field=aggregate_type → 토픽 라우팅
```

### 8.5 Debezium 도입으로 달라지는 점

1. event_outbox에 status 컬럼 불필요 (PENDING/PROCESSED 구분 없음)
2. OutboxPollerScheduler 불필요 (Debezium이 대체)
3. 테이블 정리: 1시간 보존 후 단순 DELETE (최대 6.25만 건 → ~1초)
4. PaymentOutbox는 기존 Poller 유지 (PG 호출 전용, Kafka 발행이 아님)

---

## 9. 고도화 분석 — Outbox 테이블 정리 전략

### 9.1 규모 산정 (쿠팡급 기준)

```
좋아요: 일 100만 건, 주문: 일 50만 건, 조회: Outbox 미경유
→ event_outbox: 일 150만 건 (행당 ~500 bytes)
→ Debezium 도입 → 1시간 보존 기준 최대 6.25만 건
```

### 9.2 선택지 비교

| 방법 | 정리 속도 | JPA 호환 | 복잡도 | 대규모 적합 |
|---|---|---|---|---|
| Batch DELETE | 소량 시 빠름 | O | 낮음 | Debezium 시 O |
| 라운드 로빈 | TRUNCATE O(1) | **X** (Native SQL) | 높음 | O |
| PARTITION DROP | O(1) | O | 중간 | O |
| Debezium + DELETE | 소량 DELETE | O | **낮음** | **O** |

### 9.3 결정: Debezium + 단순 Batch DELETE

Debezium이 binlog에서 읽으므로 테이블 누적이 발생하지 않음.
1시간 보존 후 DELETE → 최대 6.25만 건 → 부담 없음.

라운드 로빈 미채택 이유: JPA Entity의 @Table(name) 고정 → Native Query 강제 → DIP 위반.
파티셔닝 미채택 이유: Debezium 덕에 테이블이 작게 유지 → 파티셔닝은 과도한 최적화.

---

## 10. 고도화 분석 — Kafka와 아키텍처 관계

### 10.1 프로젝트 아키텍처 명명

현재 구조는 "멀티 프로세스 모듈러 아키텍처" — 3개 JVM, 공유 DB, 공유 레포.
모놀리스(단일 JVM)도 아니고, MSA(서비스별 DB)도 아님.

### 10.2 Kafka 적용 지점

| # | 토픽 | Producer | Consumer | 목적 |
|---|---|---|---|---|
| 1 | catalog-events | commerce-api | commerce-streamer | 좋아요/조회수 → product_metrics |
| 2 | order-events | commerce-api | commerce-streamer | 판매량 → product_metrics |
| 3 | coupon-issue-requests | commerce-api | commerce-streamer | 선착순 쿠폰 버퍼링 |

### 10.3 MSA 전환 불필요

- Kafka는 MSA 전용이 아닌 "프로세스 간 비동기 통신 인프라"
- 현재 멀티 프로세스에서 충분히 유효
- MSA 전환 트리거: 팀 분리, 극단적 스케일 차이, 기술 스택 분리, 물리적 장애 격리
- 현재 해당 없음

### 10.4 commerce-api에 modules:kafka 의존성 추가

Outbox INSERT는 commerce-api에서 발생 → Debezium이 발행하므로 KafkaTemplate 불필요.
단, 조회수는 Outbox 미경유 → 직접 Kafka 발행 → **KafkaTemplate 필요**.
→ commerce-api에 `implementation(project(":modules:kafka"))` 추가.

---

## 11. 고도화 분석 — Kafka 설정 점검

### 11.1 발견된 문제점

| # | 문제 | 위치 | 심각도 |
|---|---|---|---|
| 1 | Consumer `value-serializer` → `value-deserializer` 오타 | kafka.yml:21 | 경미 (Converter가 대체) |
| 2 | Producer acks/idempotence 미설정 | kafka.yml:14-17 | **중요** (메시지 유실 가능) |
| 3 | auto.offset.reset 위치 (글로벌 → Consumer 전용) | kafka.yml:12 | 경미 |
| 4 | 단건 처리용 Consumer Factory 부재 | KafkaConfig.java | **중요** (쿠폰 발급용) |
| 5 | Error Handler / DLQ 미설정 | KafkaConfig.java | **중요** |
| 6 | 토픽 생성 전략 없음 | N/A | 중간 |

### 11.2 보완 사항 → 08 반영

- Producer: acks=all, enable.idempotence=true, linger.ms=50, batch.size=32KB
- Consumer: value-deserializer 수정, SINGLE_LISTENER 추가
- Error Handler: DefaultErrorHandler + DeadLetterPublishingRecoverer
- 토픽: @Bean NewTopic으로 선언적 생성

---

## 12. 고도화 분석 — Redis 설정 점검

### 12.1 현재 상태

- Master-Replica 구성 완료 (ReadFrom.REPLICA_PREFERRED)
- Lettuce NIO multiplexing (커넥션 풀 불필요)
- StringRedisSerializer (적절)

### 12.2 보완 필요: 커맨드 타임아웃

현재: 타임아웃 미설정 → Redis 장애 시 스레드 무한 대기 가능.
보완: commandTimeout(Duration.ofMillis(500)) 추가.
근거: 정상 응답 ~1ms, 500ms 초과 = 장애 판단.

---

## 13. 고도화 분석 — @Async 스레드 풀

### 13.1 @Async 작업 분류

| 작업 | @Async 여부 | DB | Redis | Kafka |
|---|---|---|---|---|
| incrementLikeCount | 동기 (Tomcat) | O | X | X |
| 캐시 무효화 | 동기 (Tomcat) | X | O | X |
| 유저 행동 로깅 | **@Async** | X | X | X |
| 조회수 Kafka 발행 | **@Async** | X | X | 논블로킹 |

### 13.2 결정: core=2, max=4

@Async 작업은 DB/Redis 커넥션 불사용 → 초경량.
HikariCP(max 40)과 경합 없음. 큰 풀은 컨텍스트 스위칭만 유발.
CallerRunsPolicy로 큐 초과 시 배압.

---

## 14. → 08 반영 사항 (설계 명세 반영 대기)

| 반영 대상 | 내용 |
|---|---|
| Step 1 | ApplicationEvent 분리 대상 목록 + 리스너 설계 + @Async 스레드 풀 |
| Step 2 — Debezium | event_outbox DDL, Debezium Connector 설정, Kafka Connect Docker |
| Step 2 — Kafka | Producer 보완 (acks, idempotence), SINGLE_LISTENER, Error Handler, 토픽 선언 |
| Step 2 — Redis | commandTimeout 추가 |
| Step 2 — 집계 | product_metrics DDL, Consumer 설계 |
| Step 3 | Coupon 모델 확장, coupon_issue_request DDL, 동시성 제어 설계 |
| 의존성 | commerce-api에 modules:kafka 추가 |
| 패키지 구조 | commerce-api 이벤트 패키지, commerce-streamer Consumer 패키지 |
| 테이블 정리 | Debezium + 1시간 보존 + Batch DELETE |
| 테스트 | Phase별 테스트 전략 |
