## Summary

**배경**: 커머스 도메인에서 재고 차감, 쿠폰 사용, 좋아요 카운트 등 동시 요청 시 Lost Update, 초과 차감, 중복 사용 등의 정합성 문제가 발생할 수 있습니다.

**목표**: 각 도메인의 동시성 리스크를 분석하고, 리스크 수준과 경합 특성에 맞는 동시성 제어 전략을 적용하여 데이터 정합성을 보장합니다.

**결과**: 3개 도메인에 차별화된 동시성 전략을 적용하고, `CountDownLatch + ExecutorService` 기반 동시성 통합 테스트 4종으로 검증을 완료했습니다. 쿠폰 도메인 신규 구현 및 주문-쿠폰 연동도 포함됩니다.

---

## Context & Decision

### 문제 정의

**현재 동작/제약**: 기존 코드에는 동시성 제어가 없어, 동시 요청 시 데이터 정합성이 보장되지 않습니다.

**문제 (리스크)**:
- **재고**: 10개 남은 상품에 동시 10명이 주문하면 재고가 음수로 떨어질 수 있습니다 (Lost Update → overselling → 주문 취소 → CS 비용)
- **쿠폰**: 동일 쿠폰을 여러 기기에서 동시에 사용하면 할인이 중복 적용됩니다 (매출 손실)
- **좋아요**: 동시 좋아요/취소 시 likeCount가 실제 좋아요 수와 불일치합니다 (데이터 신뢰도 하락)

**성공 기준**: 모든 동시성 시나리오에서 데이터 정합성이 유지되며, `CountDownLatch + ExecutorService` 기반 통합 테스트 4종으로 검증 가능해야 합니다.

---

### 동시성 제어 전략 선택 기준

위 문제들은 대부분 **Read-Modify-Write (R-M-W) 패턴**에서 발생합니다:
1. **Read**: DB에서 현재 값을 읽는다 (예: 재고 = 5)
2. **Modify**: 애플리케이션에서 계산한다 (예: 5 - 1 = 4)
3. **Write**: 계산 결과를 DB에 쓴다 (예: `UPDATE SET quantity = 4`)

Read와 Write 사이의 **시간 간격(gap)** 동안 다른 스레드가 같은 값을 읽으면, 두 스레드 모두 `quantity = 4`를 쓰게 되어 **하나의 차감이 사라집니다 (Lost Update)**.

이 gap을 해결하는 전략은 크게 3가지이며, 도메인별 경합 특성에 따라 다른 전략을 선택했습니다:

| 전략 | 원리 | R-M-W gap 해결 방식 |
|------|------|---------------------|
| 비관적 락 | `SELECT ... FOR UPDATE`로 행을 잠금 | Read 시점부터 다른 스레드 접근 차단 → gap 자체를 직렬화 |
| 낙관적 락 | `@Version`으로 Write 시 충돌 감지 | gap은 허용하되, Write 시점에 충돌을 감지하여 재시도 |
| 원자적 업데이트 | `SET col = col + 1`로 DB가 직접 처리 | R-M을 DB 내부로 이동 → 애플리케이션 레벨 gap 자체가 없음 |

### 전략 선택 판단 흐름

```
Q1. 충돌 시 비즈니스에 치명적인가?
    │
    ├── NO ──→ 원자적 업데이트 / 낙관적 락
    │          예: 좋아요 (likeCount 불일치는 치명적이지 않음)
    │          → 단순 증감이면 원자적 업데이트, R-M-W면 낙관적 락 + 재시도
    │
    └── YES ──→ Q2. 재시도 요청이 필요한가?
                    │
                    ├── YES ──→ 낙관적 락 + 재시도
                    │
                    └── NO ───→ Q3. 충돌 빈도가 높은가?
                                    │
                                    ├── NO ──→ 비관적 락
                                    │          예: 쿠폰 (1회 사용, 경합 낮음)
                                    │
                                    └── YES ─→ 비관적 락 + 트랜잭션 최소화
                                               예: 재고 (인기 상품 경합 높음)
                                               │
                                               ▼
                                    트랜잭션이 긴가?
                                        ├── YES → 락 점유 구간 최소화
                                        └── NO ──→ 여러 행을 잠그는가?
                                                       ├── YES → 락 순서 고정 (데드락 방지)
                                                       │         예: productId 오름차순 정렬
                                                       └── NO ──→ 적용 완료
```

---

### 선택지와 결정

#### 1. 재고 차감 — 비관적 락 (PESSIMISTIC_WRITE)

| 대안 | 장단점 |
|------|--------|
| A: 낙관적 락 (`@Version`) | 충돌 시 재시도 필요, 인기 상품은 재시도 폭주로 성능 저하 |
| B: 원자적 업데이트 (`SET quantity = quantity - 1`) | 재고 부족 검증이 애플리케이션 레벨에서 필요 (금액 계산, 부분 차감 등 복합 로직) |
| **C: 비관적 락 (`SELECT ... FOR UPDATE`)** | **직렬화로 정확성 보장, 대기 시간 발생** |

**최종 결정: 비관적 락**

- 재고는 정합성 실패의 비용이 극히 높습니다 (overselling → 주문 취소 → CS 비용 → 신뢰도 하락)
- 재고가 없다고 재시도할 필요 없음 — "모두 성공시키는 것"이 아니라 **"정확한 수량만 성공시키는 것"**이 목적
- 원자적 업데이트로는 부족합니다: 재고 차감은 단순 `quantity - 1`이 아니라, **재고 부족 검증 → 금액 계산 → 주문 생성**이 하나의 트랜잭션에서 이루어져야 합니다. Read 단계에서 가져온 값으로 비즈니스 판단을 해야 하므로, Read와 Write 사이의 gap을 근본적으로 차단하는 비관적 락이 적합합니다.
- `productId` 오름차순 정렬로 락 획득 순서를 통일하여 **데드락 방지**
- **트레이드오프**: 동시 처리량(throughput)이 직렬화로 제한되지만, 재고 정합성이 더 중요

#### 2. 쿠폰 사용 — 비관적 락 + UniqueConstraint

| 대안 | 장단점 |
|------|--------|
| A: 낙관적 락 (`@Version`) | 1회 사용에 적합하지만, 이미 사용된 쿠폰을 재시도하는 건 무의미 |
| B: 원자적 업데이트 | 쿠폰 사용은 단순 증감이 아님 — 상태 전이(AVAILABLE→USED) + 소유자 검증 + 금액 계산 등 복합 로직 |
| **C: 비관적 락 (`FOR UPDATE`)** | **사용됐으면 거절, 재시도 불필요** |

**최종 결정: 비관적 락 + UniqueConstraint**

- 쿠폰 사용은 금액과 직결 — 이중 쿠폰 적용 시 비즈니스 매출에 직접 영향
- "한 번만 성공하면 되고, 실패한 요청은 재시도할 이유가 없다" → 비관적 락
- 쿠폰 발급 중복은 `@UniqueConstraint(user_id, coupon_id)` + `DataIntegrityViolationException` 이중 방어
- **트레이드오프**: 없음 — 쿠폰별 경합이 낮아 성능 영향 미미

#### 3. 좋아요 — 원자적 업데이트 (최종) ← 낙관적 락에서 전환

**초기 구현: 낙관적 락 (`@Version` + 재시도)**

처음에는 `ProductModel`에 `@Version`을 두고, `LikeFacade`에서 `ObjectOptimisticLockingFailureException`을 catch하여 최대 10회 재시도하는 방식으로 구현했습니다.

**문제 발견: `@Version` 스코프 간섭**

`@Version`은 **엔티티 레벨**이지 **필드 레벨**이 아닙니다. 따라서:
- 좋아요 → version 증가 → 동시에 상품 수정 시 불필요한 충돌 (False Conflict)
- 상품 수정 → version 증가 → 동시에 좋아요 시 불필요한 재시도 폭주
- 좋아요와 상품 수정은 **서로 무관한 연산**인데 같은 version을 경합

| 대안 | 장단점 |
|------|--------|
| A: 낙관적 락 유지 (`@Version`) | `@Version` 스코프가 엔티티 전체 → 상품 수정과 좋아요가 False Conflict |
| B: likeCount 별도 테이블 분리 | `@Version` 간섭 해결, 하지만 테이블 추가 + JOIN 필요 |
| **C: 원자적 업데이트 (`SET like_count = like_count + 1`)** | **JPA 변경 감지 우회 → `@Version` 트리거 안 됨, 재시도 불필요** |

**최종 결정: 원자적 업데이트**

- `likeCount` 증감은 순수한 `+1` / `-1` 연산 — R-M-W가 아니라 **단순 증감**이므로 원자적 업데이트가 자연스럽게 들어맞음
- `@Modifying @Query("UPDATE ... SET like_count = like_count + 1")`로 DB가 직접 처리 → 애플리케이션 레벨 gap 없음
- JPA 변경 감지를 우회하므로 `@Version`이 트리거되지 않음 → **상품 수정과의 False Conflict 근본적 해결**
- 재시도 로직(`retryOnOptimisticLock`), 트랜잭션 분리(`LikeTransactionService`의 별도 트랜잭션 경계) 등 **복잡도 대폭 감소**
- `@Version` 제거 → 좋아요 전용 version이 아닌 상품 전체 version이었던 문제 해소
- **트레이드오프**: `@Modifying @Query`는 JPA 1차 캐시와 동기화되지 않으므로, 같은 트랜잭션 내에서 likeCount를 다시 읽으려면 `entityManager.refresh()` 필요. 현재 구조에서는 좋아요 후 즉시 likeCount를 재조회하지 않으므로 문제 없음.

### 왜 원자적 업데이트를 재고/쿠폰에는 쓰지 않았나?

| 기준 | 재고/쿠폰 | 좋아요 |
|------|-----------|--------|
| 연산 복잡도 | Read 후 비즈니스 판단 필요 (부족 검증, 소유자 확인, 금액 계산) | 단순 `+1` / `-1` |
| R-M-W 여부 | R-M-W 패턴 (Read 값으로 판단 후 Write) | 순수 증감 (Read 불필요) |
| 원자적 업데이트 적용 가능? | 불가 — 비즈니스 로직이 R과 W 사이에 있음 | **가능** — DB가 직접 처리 가능 |

### 락 선택 판단 기준 요약

| 질문 | 비관적 락 (재고, 쿠폰) | 원자적 업데이트 (좋아요) |
|------|----------------------|------------------------|
| 실패한 요청을 재시도해야 하나? | 거절하면 됨 | 재시도 불필요 (DB가 보장) |
| 모든 요청이 성공해야 하나? | 한정 자원, 일부만 성공 | 전부 정당한 요청, 전부 성공 |
| 실패 시 비즈니스 손실? | 크다 (이중 결제, 초과 판매) | 작다 (좋아요 1초 늦게 반영) |
| Read 후 비즈니스 판단이 필요한가? | 필요 (재고 부족, 쿠폰 상태 확인) | 불필요 (단순 증감) |

---

## Design Overview

### 변경 범위

**영향 받는 도메인**: Brand, Product, Stock, Order, Like, Coupon(신규), CouponIssue(신규)

**신규 추가**:
- Coupon / CouponIssue 도메인 — 쿠폰 정의 및 발급/사용 관리
- 동시성 통합 테스트 4종
- `@LoginMember`, `@AdminUser` 인증 어노테이션 + ArgumentResolver

**주요 변경**:
- `StockJpaRepository`: `findByProductIdForUpdate()` — 비관적 락 조회
- `CouponIssueJpaRepository`: `findByIdForUpdate()` — 비관적 락 조회
- `ProductJpaRepository`: `incrementLikeCount()` / `decrementLikeCount()` — 원자적 업데이트
- `CouponIssueModel`: `@UniqueConstraint(user_id, coupon_id)` — 중복 발급 방지

### 주요 컴포넌트 책임

| 컴포넌트 | 책임 |
|----------|------|
| `OrderFacade` | 주문 생성 시 productId 정렬 → 재고 차감(비관적 락) → 쿠폰 사용(비관적 락) → 주문 저장 |
| `LikeFacade` | 좋아요 진입점 — `LikeTransactionService`에 위임 |
| `LikeTransactionService` | 트랜잭션 경계 내에서 좋아요 토글 + 원자적 업데이트 실행 |
| `LikeToggleService` | 좋아요 도메인 의사결정 (신규/복구/멱등) — `LikeResult` 반환 |
| `CouponIssueService` | 쿠폰 발급(중복 방어) + 사용 처리(비관적 락) |
| `StockService` | `getByProductIdForUpdate()` — 비관적 락으로 재고 조회 |

---

## Flow Diagrams

### 1. 주문 처리 흐름 (재고 + 쿠폰 동시성 제어)

```
Client ─── POST /api/v1/orders ───> OrderV1Controller
                                          |
                                          v
                                    OrderFacade.placeOrder()
                                    +-- @Transactional --------------------------------+
                                    |                                                   |
                                    |  (1) 상품 조회 + 금액 계산 (락 없음)               |
                                    |      productId 오름차순 정렬                       |
                                    |                                                   |
                                    |  (2) 재고 차감 (PESSIMISTIC_WRITE)                |
                                    |      FOR UPDATE로 StockModel 잠금                 |
                                    |      stock.decrease(quantity)                      |
                                    |      재고 부족 시 -> 예외 -> 전체 롤백              |
                                    |                                                   |
                                    |  (3) 쿠폰 검증+사용 (PESSIMISTIC_WRITE)           |
                                    |      FOR UPDATE로 CouponIssueModel 잠금           |
                                    |      validateOwner() -> 소유권 확인                |
                                    |      validateUsable() -> 만료/금액 확인            |
                                    |      use() -> usedAt 설정 (USED 상태)             |
                                    |      실패 시 -> 예외 -> 전체 롤백                  |
                                    |                                                   |
                                    |  (4) 주문 생성 (INSERT)                           |
                                    |      OrderModel(총액, 할인액, 최종액)              |
                                    |      OrderItemModel(상품 스냅샷)                   |
                                    |                                                   |
                                    |  (5) 쿠폰에 orderId 연결                          |
                                    |                                                   |
                                    +--- 커밋 --- 모든 락 해제 ------------------------+
```

### 2. 재고 동시성 시나리오 (비관적 락)

```
재고: 5개  |  Thread 1~10 동시 주문 요청

Thread 1 --> FOR UPDATE (잠금) --> decrease(1) --> 커밋 (재고: 4)
Thread 2 --> [대기] --------------------------------> decrease(1) --> 커밋 (재고: 3)
Thread 3 --> [대기] --------------------------------> decrease(1) --> 커밋 (재고: 2)
Thread 4 --> [대기] --------------------------------> decrease(1) --> 커밋 (재고: 1)
Thread 5 --> [대기] --------------------------------> decrease(1) --> 커밋 (재고: 0)
Thread 6 --> [대기] --------------------------------> "재고 부족" 예외 X
Thread 7~10 --> 동일하게 실패 X

결과: 5명 성공, 5명 실패, 재고 = 0
```

### 3. 쿠폰 동시 사용 시나리오 (비관적 락)

```
쿠폰 1장 (AVAILABLE)  |  Thread 1~5 동시 주문 (같은 쿠폰)

Thread 1 --> FOR UPDATE (잠금) --> isAvailable()=true  --> use() --> 커밋 O
Thread 2 --> [대기] -----------------------------------> isAvailable()=false --> 예외 X
Thread 3~5 --> 동일하게 실패 X

결과: 1명만 성공, 쿠폰 상태 = USED
```

### 4. 좋아요 동시성 시나리오 (원자적 업데이트)

```
like_count: 0  |  Thread 1~10 동시 좋아요

Thread 1 --> INSERT like --> UPDATE SET like_count = like_count + 1 --> 성공 (like_count: 1)
Thread 2 --> INSERT like --> UPDATE SET like_count = like_count + 1 --> 성공 (like_count: 2)
Thread 3 --> INSERT like --> UPDATE SET like_count = like_count + 1 --> 성공 (like_count: 3)
  ...
Thread 10 --> INSERT like --> UPDATE SET like_count = like_count + 1 --> 성공 (like_count: 10)

재시도 없음 — DB가 원자적으로 처리
결과: 10명 전원 성공, like_count = 10
```

### 5. 데드락 방지 (productId 정렬)

```
X 정렬 없이:
  User A: 상품2 락 -> 상품1 락 요청 (대기)
  User B: 상품1 락 -> 상품2 락 요청 (대기)
  -> 교착 상태 (Deadlock)

O productId 오름차순 정렬:
  User A: 상품1 락 -> 상품2 락
  User B: 상품1 락 요청 (대기) -> User A 완료 후 -> 상품1 락 -> 상품2 락
  -> 교착 상태 없음
```

### 6. @Version 스코프 문제와 원자적 업데이트 전환 결정 흐름

```
[초기 설계] 좋아요 → ProductModel.incrementLikeCount() → @Version 충돌 감지 → 재시도
    │
    │  문제 발견
    ▼
@Version은 엔티티 레벨
    ├── 좋아요 → version++ ──┐
    │                        ├── 같은 version 경합 (False Conflict)
    └── 상품 수정 → version++ ─┘
    │
    │  해결 방안 검토
    ▼
좋아요는 순수 증감 연산인가?
    ├── incrementLikeCount(): this.likeCount++  → YES, 단순 +1
    └── decrementLikeCount(): if (> 0) likeCount--  → YES, SQL WHERE 조건으로 표현 가능
    │
    │  결론
    ▼
[최종 설계] 좋아요 → @Modifying @Query("SET like_count = like_count + 1")
    ├── JPA 변경 감지 우회 → @Version 미트리거
    ├── 재시도 로직 제거 (retryOnOptimisticLock 삭제)
    └── @Version 제거 → 상품 수정과의 간섭 근본 해결
```

---

## 동시성 테스트

| 테스트 | 시나리오 | 기대 결과 |
|--------|---------|-----------|
| `stockDecreasedCorrectlyUnderConcurrency` | 재고 100, 10명 동시 1개 주문 | 10명 성공, 재고 90 |
| `onlyAvailableStockSucceeds` | 재고 5, 10명 동시 1개 주문 | 5명 성공, 재고 0 |
| `couponUsedOnlyOnce` | 쿠폰 1장, 5명 동시 주문 | 1명만 성공 |
| `likeCountAccurateUnderConcurrency` | 10명 동시 좋아요 | 10명 성공, likeCount = 10 |

---

## Checklist

### Coupon 도메인
- [x] 쿠폰 소유자 검증 (`validateOwner`)
- [x] 정액/정률 할인 계산 (`CouponType.FIXED/RATE`)
- [x] 발급된 쿠폰 최대 1회 사용 (`use()` -> USED 상태)
- [x] 중복 발급 방지 (`@UniqueConstraint` + `DataIntegrityViolationException`)

### 주문
- [x] `@Transactional`로 원자성 보장 (재고+쿠폰+주문 단일 트랜잭션)
- [x] 사용 불가/존재하지 않는 쿠폰 -> 주문 실패
- [x] 재고 부족 -> 주문 실패
- [x] 부분 실패 -> 전체 롤백 (`rollsBackOnPartialFailure` 테스트 검증)
- [x] 주문 스냅샷: 할인 전 금액, 할인 금액, 최종 결제 금액

### 동시성
- [x] 좋아요 동시 요청 -> likeCount 정상 반영 (원자적 업데이트)
- [x] 동일 쿠폰 동시 주문 -> 1번만 사용 (비관적 락)
- [x] 동일 상품 동시 주문 -> 재고 정상 차감 (비관적 락)

### 인증
- [x] `@LoginMember` — 사용자 API 인증 (Order, Coupon, Like, Member)
- [x] `@AdminUser` — 어드민 API 인증 (Brand, Product, Order, Coupon Admin)

---

## 주요 파일

<details>
<summary>동시성 제어 핵심 파일</summary>

| 파일 | 역할 |
|------|------|
| `StockJpaRepository` | `@Lock(PESSIMISTIC_WRITE)` — 재고 비관적 락 |
| `CouponIssueJpaRepository` | `@Lock(PESSIMISTIC_WRITE)` — 쿠폰 비관적 락 |
| `ProductJpaRepository` | `@Modifying @Query` — 좋아요 원자적 업데이트 |
| `OrderFacade` | 주문 트랜잭션 경계, productId 정렬 데드락 방지 |
| `LikeFacade` | 좋아요 진입점 |
| `LikeTransactionService` | 좋아요 트랜잭션 경계 + 원자적 업데이트 호출 |
| `LikeToggleService` | 좋아요 도메인 의사결정 (LikeResult 반환) |
| `LikeResult` | 좋아요 토글 결과 (newLike + countChanged) |
| `CouponIssueModel` | `@UniqueConstraint` + `use()` 상태 전이 |
| `CouponIssueService` | 쿠폰 발급/사용, `DataIntegrityViolationException` 처리 |
| `ConcurrencyIntegrationTest` | 동시성 통합 테스트 4종 |

</details>

---
