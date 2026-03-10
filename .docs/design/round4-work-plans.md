# Round4 구현 계획

## 현재 상태 분석

| 도메인 | 상태 | 동시성 전략 | 비고 |
|--------|------|------------|------|
| Product (`stockQuantity`) | ✅ 완성 | Atomic UPDATE | `decreaseStockIfEnough` JPQL 원자적 처리 |
| Product (`likeCount`) | ⚠️ 취약 | Atomic UPDATE (Phase 4) | 현재 Lost Update 가능 |
| Coupon (`usedAt`) | ❌ 없음 | Atomic UPDATE (Phase 2) | 신규 구현 필요 |
| Order | ⚠️ 부분 | 별도 전략 없음 (stock/coupon에 위임) | 쿠폰 미적용, 할인 금액 필드 없음 |

---

## Phase 0: Order 필드 정리 (선행 작업)

**이후 Phase들이 이 구조에 의존하므로 가장 먼저 처리.**

**기존 필드 이름 변경 (rename)**

| 변경 전 | 변경 후 |
|---------|---------|
| `total_amount` (DB 컬럼) | `final_amount` |
| `Order.totalAmount` (Java) | `Order.finalAmount` |

**신규 필드 추가**

| 필드 | 타입 | 설명 |
|------|------|------|
| `original_amount` | Long | 쿠폰 적용 전 금액 (order_item 합산) |
| `discount_amount` | Long | 할인 금액 (쿠폰 없으면 0) |

> `final_amount = original_amount - discount_amount`

- 기존 `totalAmount` 참조 코드/테스트 전수 수정
- 수정 후 전체 테스트 통과 확인

---

## Phase 1: 요구사항/계획 문서 생성 ✅

- `.docs/design/round4-requirements.md` 생성
- `.docs/design/round4-work-plans.md` 생성 (현재 파일)

---

## Phase 2: Coupon 도메인 신규 구현 (TDD)

### 2.1 신규 테이블

**coupons** (쿠폰 원본)

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK |
| `name` | String | 쿠폰명 |
| `discount_type` | Enum (FIXED/RATE) | 할인 타입 |
| `discount_value` | Long | 할인 값 (FIXED: 원화 금액, RATE: % 숫자) |
| `min_order_amount` | Long | 최소 주문 금액 조건 (NOT NULL, DEFAULT 0) |
| `expires_at` | LocalDateTime | 만료일시 |

**issued_coupons** (발급된 쿠폰)

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK |
| `user_id` | Long | 사용자 ID |
| `coupon_id` | Long | 쿠폰 ID (FK → coupons) |
| `used_at` | LocalDateTime | 사용 일시 (nullable) |
| `expires_at` | LocalDateTime | 사용 만료일 (발급 시 `Coupon.expiresAt` 복사) |
| `created_at` | ZonedDateTime | 발급 일시 (BaseEntity 제공) |

> status 컬럼 없음. `usedAt` + `expiresAt`으로 파생 (아래 2.3 참고):
> - `usedAt != null` → USED / `expiresAt < NOW()` → EXPIRED / 그 외 → AVAILABLE

- `(user_id, coupon_id)` UNIQUE 제약 → 1인 1쿠폰 보장 (DB 레벨)

### 2.2 기존 테이블 변경

**orders** (컬럼 추가 - Phase 0에서 처리)

| 필드 | 타입 | 설명 |
|------|------|------|
| `issued_coupon_id` | Long | 사용된 발급 쿠폰 ID (FK, nullable) |
| `original_amount` | Long | 쿠폰 적용 전 금액 (order_item 합산) |
| `discount_amount` | Long | 할인 금액 (쿠폰 없으면 0) |
| `final_amount` | Long | 실제 결제 금액 = original_amount - discount_amount |

### 2.3 도메인 로직

**IssuedCoupon - 상태 계산 (inner enum)**

```java
public class IssuedCoupon {

    public enum Status { AVAILABLE, USED, EXPIRED }

    public Status getStatus() {
        if (usedAt != null) return Status.USED;
        if (expiresAt.isBefore(LocalDateTime.now())) return Status.EXPIRED;
        return Status.AVAILABLE;
    }
}
```

**IssuedCoupon.validate()** (사전 검증용)
- `getStatus() == USED` → `ALREADY_USED`
- `getStatus() == EXPIRED` → `EXPIRED`
- `userId != 요청자 userId` → `NOT_FOUND` (소유 여부 노출 방지 - 존재 자체를 숨김)

**Coupon.calculateDiscount(Long orderAmount)**
- `orderAmount < minOrderAmount` → `MIN_ORDER_AMOUNT_NOT_MET`
- FIXED: `discountValue` 반환
- RATE: `Math.floor(orderAmount * discountValue / 100.0)` 반환 (내림 처리)

### 2.4 IssuedCoupon 동시성 전략 (쿠폰 사용)

```java
@Modifying
@Query("UPDATE IssuedCoupon c SET c.usedAt = NOW() WHERE c.id = :id AND c.userId = :userId AND c.usedAt IS NULL")
int useById(Long id, Long userId);
```

**Atomic UPDATE 적용** (Pessimistic Lock 미사용)
> * 동일 쿠폰 row에 동시 접근 빈도가 낮음 → Pessimistic Lock 오버헤드 불필요
> * Optimistic Lock은 `@Version` 충돌 시 재시도를 유도하는데, 쿠폰은 재시도해도 이미 사용된 상태이므로 재시도 자체가 무의미 → Atomic UPDATE가 더 직관적

**where절 조건 선택**
> * 만료 여부는 사전 검증(조회) 시점에 판단하며, 검증 통과 시점에 유효했던 쿠폰은 처리 진행을 허용한다.
> * userId = :userId 조건을 포함해, 다른 유저의 쿠폰 사용 시도를 DB 레벨에서 차단한다.

**사용 흐름 (사전 검증 + Atomic UPDATE 조합)**

```
1. findByIdAndUserId()로 IssuedCoupon 조회 → 없으면 NOT_FOUND (소유자 불일치 포함)
2. validate() 호출:
   - EXPIRED → EXPIRED    (원인 명확히 응답)
   - USED    → ALREADY_USED (원인 명확히 응답)
3. Atomic UPDATE: WHERE id = :id AND userId = :userId AND usedAt IS NULL
4. affected rows == 0 → 동시 요청이 먼저 처리된 것 → ALREADY_USED
```

### 2.5 구현 순서 (TDD)

1. `Coupon` 단위 테스트 → 엔티티 구현
2. `IssuedCoupon` 단위 테스트 (getStatus, validate, calculateDiscount) → 엔티티 구현
3. 쿠폰 서비스 테스트 → 서비스 구현 (발급, 목록 조회)
4. 대고객 API E2E 테스트 → 컨트롤러/파사드 구현
5. 어드민 API E2E 테스트 → 어드민 컨트롤러/파사드 구현

---

## Phase 3: Order 쿠폰 적용

### 3.1 변경 대상

**OrderCreateCommand**
```java
// issuedCouponId 추가 (nullable)
Long issuedCouponId
```

**Order 엔티티** (Phase 0의 테이블 변경 반영)
```java
Long issuedCouponId   // 사용된 발급 쿠폰 ID (nullable)
Long originalAmount   // 원래 주문 금액
Long discountAmount   // 할인 금액 (쿠폰 없으면 0)
Long finalAmount      // 실제 결제 금액 = originalAmount - discountAmount
```

**OrderFacade**
```
1. 주문 금액 계산 (order items 합산 → originalAmount)
2. issuedCouponId가 있으면:
   a. IssuedCoupon 조회 (findById)
   b. validate() 호출 (만료/사용/소유자 검증)
   c. Coupon.calculateDiscount(originalAmount) (최소 주문 금액 검증 포함)
   d. discountAmount 확정
   → 쿠폰 검증 실패 시 여기서 중단 (재고 차감 없음)
3. 상품 재고 차감 (Atomic UPDATE)
4. 쿠폰 사용 처리 (Atomic UPDATE, affected rows == 0이면 예외 후 재고 차감 롤백)
5. Order 생성 (originalAmount, discountAmount, finalAmount 스냅샷)
```

### 3.2 OrderV1Dto 변경

**Request**
- `issuedCouponId`: Long (nullable, optional)

**Response**
- `originalAmount`: Long
- `discountAmount`: Long
- `finalAmount`: Long

---

## Phase 4: likeCount 동시성 수정

### 4.1 문제 분석

현재 `ProductService`에서 엔티티를 조회한 후 `product.increaseLikeCount()`를 호출하는 방식은
동시 요청 시 Lost Update가 발생할 수 있다.

### 4.2 해결 방법

`ProductJpaRepository`에 JPQL 원자적 UPDATE 쿼리 추가:

```java
@Modifying
@Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :productId")
int increaseLikeCount(Long productId);

@Modifying
@Query("UPDATE Product p SET p.likeCount = p.likeCount - 1 WHERE p.id = :productId AND p.likeCount > 0")
int decreaseLikeCount(Long productId);
```

**Lock 전략 선택 이유**
> * likeCount는 단순 누적 카운터로, 동시 요청 시 충돌이 발생하면 재시도 또는 예외 처리가 필요해진다.
> * 낙관적 락(@Version)은 충돌이 드물다는 전제에서 적합하지만, 좋아요는 짧은 시간에 동시 요청이 몰릴 수 있어 OptimisticLockException 및 재시도 비용이 커질 수 있다.
> * 따라서 DB에서 likeCount = likeCount + 1 형태로 원자적 증감(Atomic UPDATE)을 수행하여 Lost Update를 방지하고, 재시도 없이 일관된 결과를 보장한다. (`stockQuantity`와 동일한 패턴 적용)

---

## Phase 5: 동시성 테스트

### 5.1 재고 동시 차감 테스트 (기존 유지)

- 재고가 N개인 상품 생성
- M명이 동시에 1개씩 주문 요청 (M > N)

결과
- 정확히 N건 주문 성공
- (M - N)건 재고 부족으로 실패 (INSUFFICIENT_STOCK)
- 최종 재고 = 0
- 재고가 음수로 내려가지 않음

---

### 5.2 쿠폰 동시 사용 테스트

- IssuedCoupon 1개 생성 (AVAILABLE)
- 동일 사용자가 동일 IssuedCoupon으로 동시에 주문 요청 N건

결과
- 정확히 1건 주문 성공
- (N - 1)건 ALREADY_USED 예외 발생
- issued_coupon.used_at이 설정되어 쿠폰이 사용 상태가 됨

→ Atomic UPDATE 조건 (`used_at IS NULL`)에 의해 동일 쿠폰의 중복 사용이 방지됨

---

### 5.3 likeCount 동시성 테스트

- 상품 1개 생성 (likeCount = 0)
- 서로 다른 사용자 N명이 동시에 좋아요 요청

결과
- likeCount == N
- likes 테이블에 N개의 레코드 생성

---

해당 작업의 상세 내용은 `round4-work-log.md` 참고.
