# Concurrency Analysis Report

> 분석 일자: 2026-03-06
> 대상: commerce-api 전체 도메인 (Product, Coupon, Order, User, Brand, ProductLike)
> 이전 분석: 2026-03-05 — 이후 적용된 수정 사항 반영

---

## 목차

1. [전체 동시성 제어 구조 요약](#1-전체-동시성-제어-구조-요약)
2. [공유 가변 상태 식별](#2-공유-가변-상태-식별)
3. [락 전략 분석](#3-락-전략-분석)
4. [Race Condition 탐지](#4-race-condition-탐지)
5. [@Retryable + @Transactional 조합 분석](#5-retryable--transactional-조합-분석)
6. [트랜잭션 범위 vs 락 유지 시간](#6-트랜잭션-범위-vs-락-유지-시간)
7. [동시성 테스트 평가](#7-동시성-테스트-평가)
8. [식별된 문제점 및 위험도](#8-식별된-문제점-및-위험도)
9. [개선 제안](#9-개선-제안)

---

## 이전 분석 대비 변경 사항

| 이전 이슈 | 상태 | 적용 커밋 |
|----------|------|----------|
| P1: CouponService.issue @Retryable 위치 문제 | **해결** | @Retryable을 CouponFacade로 이동 |
| P2: UserModel.deductPoint 독립 재시도 부재 | **유지** | OrderFacade.createOrder @Retryable이 간접 커버 중 |
| P3: OrderModel @Version 미적용 | **해결** | `9947e99` — @Version 추가, cancelMyOrderItem/cancelOrderItem에 @Retryable 추가 |
| P4: ProductLikeService.like 예외 타입 불일치 | **해결** | `f3dad8e` — DataIntegrityViolationException → CONFLICT 예외 변환 |
| P5: OrderFacade.createOrder TX 범위 | **유지** | 개선 대상이나 현재 수용 가능 수준 |
| 동시성 테스트 미비 (포인트, 좋아요, 취소) | **해결** | `ec04377` — 3개 테스트 신규 추가 |

---

## 1. 전체 동시성 제어 구조 요약

### 아키텍처 개요

```
Controller (TX 없음, 동시성 제어 없음)
  └─ Facade (@Retryable + @Transactional — TX 시작점이자 재시도 포착점)
       └─ Service (@Transactional — REQUIRED 기본값으로 Facade TX에 합류)
            └─ Repository (JPA Dirty Checking / 벌크 UPDATE)
```

### 동시성 제어 기법 사용 현황

| 기법 | 사용 여부 | 적용 위치 |
|------|----------|----------|
| 낙관적 락 (@Version) | O | ProductModel, UserModel, CouponModel, OrderModel |
| 비관적 락 (@Lock) | **X** | 미사용 |
| @Retryable | O | CouponFacade.issueCoupon, OrderFacade.createOrder/cancelMyOrderItem/cancelOrderItem |
| 벌크 UPDATE (WHERE 조건) | O | OwnedCouponJpaRepository.useByIdWhenAvailable |
| DB Unique 제약조건 | O | ProductLikeModel (user_id, product_id), BrandModel (name) |
| DataIntegrityViolation 변환 | O | ProductLikeService.like |
| 분산 락 (Redis) | **X** | 미사용 (Redis는 Master-Replica 캐싱 용도만) |
| synchronized / ReentrantLock | **X** | 미사용 |
| @Async | **X** | 미사용 |

### @Version 적용 현황

| Entity | @Version | @Retryable 연결 | 동시 쓰기 필드 |
|--------|----------|----------------|--------------|
| ProductModel | O | OrderFacade.createOrder (max=10), cancelMyOrderItem/cancelOrderItem (max=5) | `stock` |
| CouponModel | O | CouponFacade.issueCoupon (max=50) | `issuedQuantity` |
| UserModel | O | OrderFacade.createOrder (max=10) — 간접 | `point` |
| OrderModel | O | OrderFacade.cancelMyOrderItem/cancelOrderItem (max=5) | `status`, `totalPrice` |
| OwnedCouponModel | X | - | 벌크 UPDATE WHERE 조건으로 대체 |
| ProductLikeModel | X | - | Unique 제약조건으로 대체 |
| BrandModel | X | - | 저경합, 불필요 |

---

## 2. 공유 가변 상태 식별

| Entity | 동시 쓰기 필드 | 동시 접근 시나리오 | 보호 기법 | 평가 |
|--------|--------------|------------------|----------|------|
| ProductModel | `stock` | 동시 주문으로 재고 차감, 동시 취소로 재고 복구 | @Version + @Retryable(max=10/5) | **적절** |
| CouponModel | `issuedQuantity` | 동시 쿠폰 발급 | @Version + @Retryable(max=50) | **적절** |
| UserModel | `point` | 동시 주문으로 포인트 차감 | @Version + OrderFacade @Retryable(max=10) 간접 | **조건부 적절** |
| OrderModel | `status`, `totalPrice` | 동일 주문의 다른 아이템 동시 취소 | @Version + @Retryable(max=5) | **적절** |
| OwnedCouponModel | `orderId`, `usedAt` | 동시 주문으로 쿠폰 사용 | 벌크 UPDATE WHERE orderId IS NULL | **적절** |
| ProductLikeModel | INSERT 자체 | 동일 상품 동시 좋아요 | Unique 제약 + DataIntegrityViolation 변환 | **적절** |
| BrandModel | `name` | 동일 이름 브랜드 동시 등록 | Unique 제약 | **적절** (저경합) |

---

## 3. 락 전략 분석

### 3.1 낙관적 락 (Optimistic Locking)

| Entity | @Version 타입 | @Retryable 위치 | maxAttempts | backoff | 평가 |
|--------|-------------|----------------|-------------|---------|------|
| ProductModel | Long | OrderFacade.createOrder | 10 | 50ms random | 적절 |
| ProductModel | Long | OrderFacade.cancelMyOrderItem/cancelOrderItem | 5 | 50ms random | 적절 |
| CouponModel | Long | CouponFacade.issueCoupon | 50 | 50ms random | 적절 |
| UserModel | Long | OrderFacade.createOrder (간접) | 10 | 50ms random | 조건부 적절 |
| OrderModel | Long | OrderFacade.cancelMyOrderItem/cancelOrderItem | 5 | 50ms random | 적절 |

**maxAttempts 적절성 평가:**

| 유즈케이스 | maxAttempts | 최대 동시 경합 수 | 평가 |
|-----------|-------------|----------------|------|
| 쿠폰 발급 (선착순) | 50 | totalQuantity (최대 수십~수백) | 적절 — 고경합 시나리오 |
| 주문 생성 | 10 | 인기 상품 동시 주문 수 | 적절 — 중경합 시나리오 |
| 주문 아이템 취소 | 5 | 같은 주문의 아이템 수 (보통 3~5) | 적절 — 저경합 시나리오 |

### 3.2 비관적 락 (Pessimistic Locking)

**미사용.** 프로젝트 전체에서 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 등의 선언 없음.

### 3.3 DB 수준 제어

#### 벌크 UPDATE (원자적 상태 전이)

**OwnedCouponJpaRepository.useByIdWhenAvailable:**
```sql
UPDATE OwnedCouponModel o
SET o.orderId = :orderId, o.usedAt = :usedAt
WHERE o.id = :id AND o.orderId IS NULL
```
- `WHERE orderId IS NULL` 조건으로 원자적 "사용 가능 → 사용됨" 전이
- 반환값 0이면 이미 사용된 쿠폰 → `ALREADY_USED` 예외
- `@Modifying(flushAutomatically = true, clearAutomatically = true)` 적용
- **평가:** 동시 쿠폰 사용 방지에 효과적. @Version 없이도 안전.

#### Unique 제약조건

| Entity | 제약 이름 | 컬럼 | 용도 |
|--------|---------|------|------|
| ProductLikeModel | `uk_likes_user_product` | (user_id, product_id) | 중복 좋아요 방지 |
| BrandModel | (컬럼 단위 unique) | name | 중복 브랜드명 방지 |

#### DataIntegrityViolation 변환 패턴

```java
// ProductLikeService.like()
try {
    productLikeRepository.save(ProductLikeModel.create(userId, productId));
} catch (DataIntegrityViolationException e) {
    throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다");
}
```
- 애플리케이션 검사(`findByUserIdAndProductId`) + DB Unique 이중 방어
- 동시 요청에도 일관된 비즈니스 예외(CONFLICT) 반환

### 3.4 애플리케이션 수준 제어

**미사용.** `synchronized`, `ReentrantLock`, `Atomic*` 등은 프로덕션 코드에 없음.

### 3.5 분산 락

**미사용.** Redis는 Master-Replica 캐싱 구성만 존재.
- `RedisConfig`에 `defaultRedisTemplate` (Replica 선호)과 `masterRedisTemplate` (Master 강제) 정의
- Redisson, SET NX 등의 분산 락 패턴 없음

---

## 4. Race Condition 탐지

### Race Condition 후보

| 위치 | 패턴 | 경합 시나리오 | 보호 기법 | 위험도 |
|------|------|-------------|----------|--------|
| CouponService.issue | Read-then-Write | 동시 발급 → issuedQuantity 경합 | @Version + @Retryable(50) | **LOW** (보호됨) |
| CouponService.issue | Check-then-Act | 중복 발급 검사 → save 사이 갭 | @Version 재시도 시 재검사 | **LOW** (보호됨) |
| ProductLikeService.like | Check-then-Act | findBy → save 사이 동시 INSERT | DB Unique + DIV 변환 | **LOW** (DB 보호) |
| UserService.deductPoint | Read-then-Write | 동시 주문 → 포인트 경합 | @Version + 간접 @Retryable(10) | **LOW** (간접 보호) |
| ProductService.decreaseStock | Read-then-Write | 동시 주문 → 재고 경합 | @Version + @Retryable(10) | **LOW** (보호됨) |
| OrderModel.cancelItem | Read-then-Write | 동일 주문 다른 아이템 동시 취소 | @Version + @Retryable(5) | **LOW** (보호됨) |
| BrandService.register | Check-then-Act | 동일 이름 동시 등록 | DB Unique 제약 | **LOW** (DB 보호) |

### 상세 분석

#### 4.1 CouponService.issue — 동일 유저 중복 발급 방지

```java
// CouponService.issue() 내부 흐름
findByCouponIdAndUserId(couponId, userId)  // [1] 검사: 이미 발급 여부
coupon.issue()                              // [2] 쓰기: issuedQuantity++
ownedCouponRepository.save(...)             // [3] 쓰기: OwnedCoupon INSERT
```

**동일 유저 동시 요청 시나리오:**
```
Thread A: [1] 없음 → [2] issuedQuantity++ → [3] INSERT → 커밋 (version 1→2) → 성공
Thread B: [1] 없음 → [2] issuedQuantity++ → 커밋 시 version 충돌 → 실패 → 재시도
Thread B retry: [1] 발견! → ALREADY_ISSUED 예외
```

- @Version + @Retryable 조합으로 재시도 시 중복 검사가 다시 수행됨
- **단, owned_coupons 테이블에 (coupon_id, user_id) Unique 제약이 없음** → DB 레벨 안전망 부재
- 현재 로직상 문제없지만, 방어적 관점에서 Unique 제약 추가 권장 (섹션 9 참조)

#### 4.2 OrderFacade.createOrder — 다중 @Version 엔티티 동시 수정

```java
// OrderFacade.createOrder() 내부 흐름
productService.validateAndDeductStock(...)   // ProductModel @Version 충돌 가능
brandService.getNameMapByIds(...)            // 읽기 전용
orderService.createOrder(...)                // INSERT (충돌 없음)
couponService.useAndCalculateDiscount(...)   // 벌크 UPDATE (원자적)
orderService.applyDiscount(...)              // Dirty Checking
userService.deductPoint(...)                 // UserModel @Version 충돌 가능
```

**경합 시나리오: 같은 상품 + 같은 유저 동시 주문**
```
Thread A: stock 차감 → 주문 생성 → 포인트 차감 → 커밋 (product v1→v2, user v1→v2)
Thread B: stock 차감 → 커밋 시 product version 충돌 → 전체 롤백 → 재시도
Thread B retry: 새 TX에서 product v2 읽기 → stock 차감 → ... → 커밋 성공
```

- maxAttempts=10으로 대부분 성공
- ProductModel과 UserModel 두 곳에서 충돌 가능하지만, 동시에 충돌할 확률은 낮음
- random backoff가 경합 분산에 기여

#### 4.3 OrderFacade.cancelMyOrderItem — 동시 취소 직렬화

```
Order (item1, item2, item3 — @Version v=1)

Thread A: cancel item1, read order v=1 → recalculate → 커밋 v=1→v=2 → 성공
Thread B: cancel item2, read order v=1 → 커밋 시 version 충돌 → 재시도
Thread B retry: read order v=2 → cancel item2 → recalculate → 커밋 v=2→v=3 → 성공
Thread C: 동일 패턴 → v=3→v=4 → 성공
```

- @Version이 동시 취소를 자연스럽게 직렬화
- 재시도 시 최신 상태를 읽으므로 `recalculateTotalPrice()` 정합성 보장
- maxAttempts=5는 일반적인 주문 아이템 수(3~5개)에 충분

---

## 5. @Retryable + @Transactional 조합 분석

### 프록시 순서 전제

`@EnableRetry`가 `CommerceApiApplication`에 선언됨. Spring Boot 3.x 기본 동작에서:
- `@Transactional`의 AOP order = `Ordered.LOWEST_PRECEDENCE` (Integer.MAX_VALUE)
- `@Retryable` 인터셉터가 `@Transactional` 인터셉터보다 외부에 위치
- 결과: `@Retryable` → `@Transactional` → 비즈니스 로직 → 커밋/롤백 → 재시도 판단

### 조합 현황

| 위치 | @Retryable | @Transactional | TX 역할 | 재시도 동작 | 평가 |
|------|-----------|---------------|--------|-----------|------|
| CouponFacade.issueCoupon | max=50 | REQUIRED | TX 시작점 | **정상** — 커밋 시 충돌 → 롤백 → 새 TX 재시도 | **OK** |
| OrderFacade.createOrder | max=10 | REQUIRED | TX 시작점 | **정상** — 동일 구조 | **OK** |
| OrderFacade.cancelMyOrderItem | max=5 | REQUIRED | TX 시작점 | **정상** — 동일 구조 | **OK** |
| OrderFacade.cancelOrderItem | max=5 | REQUIRED | TX 시작점 | **정상** — 동일 구조 | **OK** |

### 공통 패턴 (정상 동작)

```
호출 체인 (모든 @Retryable 메서드 공통):
Controller [TX 없음]
  └─ Facade [@Retryable + @Transactional]     ← TX 시작점 + 재시도 포착점
       └─ Service [@Transactional(REQUIRED)]   ← Facade TX에 합류
            └─ Repository
```

**동작 흐름:**
1. @Retryable 프록시 진입
2. @Transactional 프록시 진입 → TX 시작
3. Service 호출 (REQUIRED → 합류, 새 TX 시작하지 않음)
4. TX 커밋 시 `ObjectOptimisticLockingFailureException` 발생
5. @Transactional 프록시 → TX 롤백
6. @Retryable 프록시 → 예외 포착 → 재시도 판단
7. 재시도 시 2번부터 새로운 TX로 반복

**이전 분석(P1) 해소:**
- 이전에는 `CouponService.issue()`에 @Retryable이 있어 Facade TX 합류 시 커밋 시점 불일치 위험이 있었음
- 현재 @Retryable이 `CouponFacade.issueCoupon()`으로 이동되어 TX 시작점과 일치
- 모든 @Retryable이 Facade(TX 시작점)에 통일되어 패턴 일관성 확보

### Service 레이어 @Transactional 전파

| Service 메서드 | 전파 속성 | 호출 Facade | 합류 여부 |
|---------------|----------|------------|----------|
| CouponService.issue | REQUIRED (기본값) | CouponFacade.issueCoupon | 합류 |
| ProductService.validateAndDeductStock | REQUIRED | OrderFacade.createOrder | 합류 |
| UserService.deductPoint | REQUIRED | OrderFacade.createOrder | 합류 |
| OrderService.cancelItem | REQUIRED | OrderFacade.cancelMyOrderItem | 합류 |
| ProductService.increaseStock | REQUIRED | OrderFacade.cancelMyOrderItem | 합류 |

모든 Service가 REQUIRED(기본값)로 Facade TX에 합류하므로, 커밋은 Facade 반환 시점에만 발생. @Retryable이 Facade에 있으므로 정상 동작.

---

## 6. 트랜잭션 범위 vs 락 유지 시간

### 낙관적 락에서 TX 길이의 의미

낙관적 락(@Version)은 실제 락을 잡지 않고 커밋 시 충돌을 검증한다:
- TX가 길수록 **다른 TX와 충돌할 확률이 높아짐** (같은 row를 동시에 읽고 쓸 시간 창이 넓어짐)
- 충돌 시 전체 TX가 롤백되므로, TX 내 작업이 많을수록 **롤백 비용이 커짐**

### 유즈케이스별 분석

| 유즈케이스 | TX 범위 | @Version 대상 | TX 내 작업 수 | 불필요 포함 작업 | 평가 |
|-----------|--------|--------------|-------------|---------------|------|
| 주문 생성 | OrderFacade | ProductModel, UserModel | 6 | 브랜드명 조회 | **WARNING** |
| 쿠폰 발급 | CouponFacade | CouponModel | 3 | 없음 | OK |
| 쿠폰 사용 (주문 내) | OrderFacade | OwnedCouponModel (벌크 UPDATE) | 1 | 없음 | OK |
| 주문 아이템 취소 | OrderFacade | OrderModel, ProductModel | 3~4 | 없음 | OK |

### 주문 생성 TX 상세

```
OrderFacade.createOrder() [단일 TX, @Retryable max=10]
  ├─ [1] productService.validateAndDeductStock()   [쓰기] ProductModel @Version 충돌 가능
  ├─ [2] brandService.getNameMapByIds()             [읽기] 스냅샷 용도 — TX 외부 가능
  ├─ [3] orderService.createOrder()                 [쓰기] INSERT
  ├─ [4] couponService.useAndCalculateDiscount()    [쓰기] 벌크 UPDATE
  ├─ [5] orderService.applyDiscount()               [쓰기] Dirty Checking
  └─ [6] userService.deductPoint()                  [쓰기] UserModel @Version 충돌 가능
```

- [1]에서 ProductModel @Version 충돌 시, [2]~[6] 전체 롤백 + 재시도
- [6]에서 UserModel @Version 충돌 시, [1]~[5] 전체 롤백 + 재시도
- [2] 브랜드명 조회는 읽기 전용으로, TX 외부에서 수행 가능하지만 현재 TX 내 포함
- 재시도마다 [1]~[6] 전체 재실행 비용 발생

### 주문 아이템 취소 TX 상세

```
OrderFacade.cancelMyOrderItem() [단일 TX, @Retryable max=5]
  ├─ [1] orderService.getByIdAndUserId()            [읽기] OrderModel 조회
  ├─ [2] orderService.cancelItem()                  [쓰기] OrderModel @Version 충돌 가능
  ├─ [3] productService.increaseStock()             [쓰기] ProductModel @Version 충돌 가능
  └─ [4] couponService.restoreByOrderId()           [쓰기] 조건부 (전체 취소 시에만)
```

- [2]에서 OrderModel 충돌과 [3]에서 ProductModel 충돌이 동시에 발생 가능하나, 확률은 매우 낮음
- maxAttempts=5는 일반적인 아이템 수(3~5개)에 충분

---

## 7. 동시성 테스트 평가

### 테스트 커버리지

| 시나리오 | 테스트 클래스 | 동시 출발 | 스레드 수 | 반복 | 검증 항목 | 평가 |
|---------|------------|----------|----------|------|---------|------|
| 쿠폰 발급 | CouponIssueConcurrencyTest | O (4가지 방식 비교) | 10 | 3회 | 초과 발급, issuedQuantity-OwnedCoupon 정합성 | **우수** |
| 재고 동시 차감 | StockDeductionConcurrencyTest | O (강화) | 10 | 3회 | 재고 정합성, 초과 차감 방지, 재고>=0 | **우수** |
| 주문 아이템 동시 취소 | OrderItemCancelConcurrencyTest | O (강화) | 3 | 3회 | 전체 성공, CANCELLED 상태, totalPrice=0, 재고 원복 | **우수** |
| 동시 좋아요 | ProductLikeConcurrencyTest | O (강화) | 10 | 3회 | 1건만 성공, DB 레코드 1건 | **우수** |
| 포인트 동시 차감 | PointDeductionConcurrencyTest | O (강화) | 10 | 3회 | 포인트>=0, 정합성, 최대 성공 수 제한 | **우수** |
| 보유 쿠폰 동시 사용 | OwnedCouponUseConcurrencyTest | O (강화) | 10 | 3회 | 1건만 성공, 쿠폰 사용 상태 | **우수** |

### 테스트 구조 강점

1. **일관된 강화 CountDownLatch 패턴**: `readyLatch` + `startLatch` + `doneLatch` 3단계로 최대 경합 재현 (5/6 테스트)
2. **@RepeatedTest(3)**: 비결정적 테스트의 신뢰도 확보
3. **CouponIssueConcurrencyTest**: 4가지 동시성 패턴(Thread, 기본 Latch, 강화 Latch, CompletableFuture) 비교 — 교육적 구조
4. **포괄적 검증**: 성공/실패 카운트 + DB 정합성 + 초과 방지 모두 검증
5. **콘솔 리포트**: 실행 결과를 사람이 읽을 수 있는 형태로 출력

### 누락된 테스트 시나리오

| 시나리오 | 설명 | 위험도 | 우선순위 |
|---------|------|--------|---------|
| 동일 유저 동일 쿠폰 동시 발급 | 같은 유저가 같은 쿠폰을 동시 요청 → 중복 발급 방지 검증 | INFO | 낮음 |
| 다른 상품 동시 주문 (같은 유저) | 같은 유저가 서로 다른 상품을 동시 주문 → UserModel @Version 충돌 처리 검증 | INFO | 낮음 |

**참고:** 현재 기존 테스트가 대부분의 주요 시나리오를 커버하고 있으며, 누락된 시나리오는 기존 보호 기법(Version+Retryable)이 로직적으로 커버하는 영역이므로 우선순위가 낮음.

---

## 8. 식별된 문제점 및 위험도

### [WARNING] P1: OwnedCoupon (coupon_id, user_id) Unique 제약 부재

**현상:**
- `CouponService.issue()`에서 `findByCouponIdAndUserId()` 검사 후 `save()` 수행
- `owned_coupons` 테이블에 `(coupon_id, user_id)` 복합 Unique 제약 없음
- 애플리케이션 레벨 + @Version 재시도로 중복 방지 중이나, DB 레벨 안전망 부재

**현재 보호 메커니즘:**
```
Thread A: find → 없음 → issue → 커밋(v1→v2) → 성공
Thread B: find → 없음 → issue → 커밋 실패(v=1) → 재시도 → find → 있음! → ALREADY_ISSUED
```

- @Version + @Retryable 조합으로 사실상 보호되고 있음
- 그러나 `ProductLikeModel`에 Unique 제약을 설정한 것과 비교하면 방어 계층이 얕음

**위험 시나리오:**
- @Retryable maxAttempts(50) 소진 시 — 극단적 고경합에서 이론적으로 가능
- 별도 코드 경로(배치, 관리자 API 등)에서 검사 없이 INSERT 시

---

### [INFO] P2: UserModel.deductPoint — 독립 재시도 경로 부재

**현상:**
- `UserModel`에 `@Version`이 있어 동시 수정 시 `ObjectOptimisticLockingFailureException` 발생
- `UserService.deductPoint()`를 직접 호출하는 경로에는 @Retryable 없음
- 현재 유일한 쓰기 경로인 `OrderFacade.createOrder(@Retryable max=10)`이 간접 커버

**현재 동작:**
- 포인트 차감은 `OrderFacade.createOrder()` 내에서만 호출됨
- Facade의 @Retryable이 UserModel @Version 충돌도 재시도
- **현재는 정상 동작 — 독립적인 포인트 차감 API가 추가될 때 대응 필요**

---

### [INFO] P3: OrderFacade.createOrder — TX 범위 최적화 여지

**현상:**
- 6개 서비스 호출이 단일 TX에 포함
- `brandService.getNameMapByIds()`는 읽기 전용 스냅샷 조회
- TX 내 포함이 필수적이지 않으나, TX 시간을 미세하게 늘림

**영향:**
- TX 시간 증가 → @Version 충돌 확률 미세 증가
- 롤백 시 브랜드 조회도 재실행
- 현재 수준에서는 수용 가능 (브랜드 조회는 경량 연산)

---

## 9. 개선 제안

### [제안 1] owned_coupons 테이블에 Unique 제약 추가

**현재 구조:**
- 애플리케이션 레벨 `findByCouponIdAndUserId()` 검사만 수행
- DB 레벨 Unique 제약 없음

**개선안:**
```java
@Entity
@Table(name = "owned_coupons", uniqueConstraints = {
    @UniqueConstraint(name = "uk_owned_coupon_user", columnNames = {"coupon_id", "user_id"})
})
public class OwnedCouponModel extends BaseEntity {
```

추가로 `CouponService.issue()`에 `DataIntegrityViolationException` 변환 (ProductLikeService 패턴 적용):
```java
try {
    return ownedCouponRepository.save(OwnedCouponModel.create(coupon, userId));
} catch (DataIntegrityViolationException e) {
    throw new CoreException(CouponErrorCode.ALREADY_ISSUED);
}
```

**Trade-off:**
- 장점: ProductLikeModel과 동일한 이중 방어 구조로 일관성 확보, DB 레벨 안전망
- 장점: 별도 코드 경로(배치, 관리 도구)에서도 중복 방지
- 단점: 마이그레이션 스크립트 필요, 기존 데이터에 중복이 있으면 제약 추가 실패
- 고려: 현재 @Version + @Retryable로 잘 동작 중이므로 긴급하지 않음

---

### [제안 2] OrderFacade.createOrder — 브랜드 조회 TX 외부 분리 (선택적)

**현재 구조:**
```java
@Retryable(max=10) @Transactional
public OrderResult.OrderSummary createOrder(Long userId, OrderCriteria.Create criteria) {
    List<ProductInfo.StockDeduction> deductionInfos = productService.validateAndDeductStock(...);
    Map<Long, String> brandNameMap = brandService.getNameMapByIds(...);  // ← 읽기 전용
    OrderModel order = orderService.createOrder(userId, ...);
    // ...
}
```

**개선안:**
```java
@Retryable(max=10) @Transactional
public OrderResult.OrderSummary createOrder(Long userId, OrderCriteria.Create criteria) {
    List<ProductInfo.StockDeduction> deductionInfos = productService.validateAndDeductStock(...);
    // brandNameMap은 deductionInfos에서 이미 brandId를 알고 있으므로
    // 재시도 시에도 동일한 결과 → TX 외부 분리 가능
    // 단, 현재 구조에서는 TX 내에서 호출해도 성능 영향 미미
}
```

**Trade-off:**
- 장점: TX 시간 미세 단축 → 충돌 확률 미세 감소
- 단점: 메서드 분리 시 코드 복잡도 증가
- 결론: **현재 수준에서는 불필요** — 브랜드 조회는 경량 연산이고 성능 병목이 아님

---

### [제안 3] 누락된 동시성 테스트 추가 (선택적)

**우선순위 순:**

1. **동일 유저 동일 쿠폰 동시 발급**
   - 시나리오: 같은 유저가 같은 쿠폰을 10회 동시 발급 시도
   - 기대: 1건만 성공, 나머지 ALREADY_ISSUED
   - 목적: Unique 제약 없이 @Version + @Retryable만으로 중복 방지 검증

2. **동일 유저 다른 상품 동시 주문**
   - 시나리오: 같은 유저(point=100,000)가 서로 다른 상품(각 30,000원) 5건 동시 주문
   - 기대: 최대 3건 성공, UserModel @Version 충돌 재시도 검증
   - 목적: 다중 @Version 엔티티(ProductModel + UserModel) 동시 충돌 시 재시도 정상 동작 확인

**참고:** 기존 테스트가 주요 시나리오를 이미 커버하므로, 이 추가 테스트는 방어적 검증 목적.

---

## 부록: Kafka / Spring Batch 동시성

### Kafka Consumer

| 설정 | 값 | 비고 |
|------|-----|------|
| concurrency | 3 | 3개 스레드 동시 소비 |
| ackMode | MANUAL | 수동 커밋 |
| batchListener | true | 배치 처리 |
| maxPollRecords | 3000 | 한 번에 최대 3000 메시지 |
| maxPollIntervalMs | 120,000ms | 2분 |
| sessionTimeoutMs | 60,000ms | 1분 |
| heartbeatIntervalMs | 20,000ms | session_timeout의 1/3 |

- 현재 `DemoKafkaConsumer`만 존재하며, 프로덕션 비즈니스 로직 미구현
- 동시성 이슈는 Consumer 비즈니스 로직 구현 시 점검 필요

### Spring Batch

- TaskExecutor 미설정 → 단일 스레드 실행
- DemoJob만 존재 (Tasklet 기반, ResourcelessTransactionManager)
- 동시성 이슈 없음

---

## 종합 평가

| 영역 | 점수 | 코멘트 |
|------|------|--------|
| 공유 가변 상태 보호 | **A** | 모든 쓰기 대상 Entity에 @Version 또는 DB 레벨 보호 적용 |
| 락 전략 일관성 | **A** | 낙관적 락 + @Retryable 패턴이 모든 Facade에 통일 |
| Race Condition 방어 | **A-** | 주요 패턴 보호됨. owned_coupons Unique 제약 부재만 개선 여지 |
| @Retryable + @Transactional 조합 | **A** | 모든 @Retryable이 TX 시작점(Facade)에 위치, 프록시 순서 정상 |
| TX 범위 최적화 | **B+** | createOrder TX가 다소 넓지만 수용 가능 수준 |
| 동시성 테스트 커버리지 | **A** | 6개 핵심 시나리오 커버, 강화 Latch + @RepeatedTest 적용 |
