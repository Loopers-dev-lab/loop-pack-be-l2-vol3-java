# Transaction & Persistence Context Analysis Report (v2)

> 분석 일자: 2026-03-06
> 대상: commerce-api 전체 도메인 (Brand, Product, Like, Coupon, Order, User)
> 비교 기준: 2026-03-05 초회 분석 결과

---

## 목차

1. [이전 분석 대비 개선 사항](#1-이전-분석-대비-개선-사항)
2. [전체 트랜잭션 구조 요약](#2-전체-트랜잭션-구조-요약)
3. [도메인별 유즈케이스 분석](#3-도메인별-유즈케이스-분석)
   - [Order](#31-order-도메인)
   - [Product](#32-product-도메인)
   - [Coupon](#33-coupon-도메인)
   - [Brand](#34-brand-도메인)
   - [User](#35-user-도메인)
   - [ProductLike](#36-productlike-도메인)
4. [영속성 컨텍스트 관점 분석](#4-영속성-컨텍스트-관점-분석)
5. [잔존 문제점 및 위험도](#5-잔존-문제점-및-위험도)
6. [개선 제안](#6-개선-제안)

---

## 1. 이전 분석 대비 개선 사항

### 해결된 문제

| 이전 ID | 문제 | 해결 방법 | 상태 |
|---------|------|----------|------|
| P2-1 | CouponService.issue에 @Retryable 선언 — Facade TX에 합류하여 재시도 무효 | @Retryable을 CouponFacade.issueCoupon으로 이동, Service에서 제거 | **RESOLVED** |
| P2-2 | OrderFacade.createOrder의 @Retryable 프록시 순서 미보장 | 구조 유지 + Backoff 추가 (`delay=50, random=true`) — 프록시 순서 자체는 Spring Boot 기본값으로 동작 | **MITIGATED** |
| (미명시) | OrderModel에 @Version 없음 — 주문 취소 동시성 미제어 | OrderModel에 `@Version` 추가 | **RESOLVED** |
| (미명시) | cancelMyOrderItem/cancelOrderItem에 @Retryable 없음 | 두 메서드에 `@Retryable(max=5)` + `@Backoff(delay=50, random=true)` 추가 | **RESOLVED** |

### 변경 상세

**1. CouponFacade.issueCoupon (P2 해결)**
```
[이전]
CouponFacade [@Transactional] → CouponService [@Retryable + @Transactional(REQUIRED)]
→ Service가 Facade TX에 합류하므로 @Retryable이 실질적으로 무효

[현재]
CouponFacade [@Retryable(max=50) + @Transactional] → CouponService [@Transactional(REQUIRED)]
→ @Retryable이 @Transactional 바깥에서 감싸므로, 재시도 시 새 TX 생성. 정상 동작.
```

**2. OrderModel @Version 추가**
```java
// OrderModel.java:47
@Version
private Long version;
```
- 주문 취소 시 동일 주문에 대한 동시 접근을 낙관적 락으로 제어
- cancelMyOrderItem/cancelOrderItem 모두 @Retryable(max=5)로 충돌 시 재시도

**3. @Backoff 전략 통일**
모든 @Retryable에 `@Backoff(delay = 50, random = true)` 적용:
- 고정 지연(50ms) + 랜덤 지터로 thundering herd 완화
- createOrder(max=10), issueCoupon(max=50), cancelItem(max=5)로 도메인 특성에 맞는 차등 적용

---

## 2. 전체 트랜잭션 구조 요약

### 아키텍처 개요

```
Controller (TX 없음)
  └─ Facade (@Transactional 시작점, @Retryable 필요 시 선언)
       └─ Service (@Transactional — 동일 TX 참여, REQUIRED 기본값)
            └─ Repository (DB 접근)
```

- 트랜잭션 시작점은 **Facade 레이어**로 일관됨
- @Retryable도 **Facade 레이어**에 선언 — TX 바깥에서 감싸는 구조로 통일
- 읽기 전용 메서드에 `@Transactional(readOnly = true)` 적용이 일관적

### @Version(낙관적 락) 적용 현황

| Entity | @Version | @Retryable (위치) | 비고 |
|--------|----------|-------------------|------|
| ProductModel | O | OrderFacade.createOrder(max=10) | 재고 동시성 제어 |
| CouponModel | O | CouponFacade.issueCoupon(max=50) | 쿠폰 발급 동시성 제어 |
| UserModel | O | OrderFacade.createOrder(max=10)로 커버 | 포인트 차감은 주문 생성 TX 내 |
| OrderModel | **O (신규)** | cancelMyOrderItem(max=5), cancelOrderItem(max=5) | 주문 취소 동시성 제어 |
| BrandModel | X | 없음 | 불필요 (낮은 동시성) |
| OwnedCouponModel | X | 없음 | JPQL 벌크 UPDATE로 제어 |
| ProductLikeModel | X | 없음 | Unique 제약조건으로 제어 |

---

## 3. 도메인별 유즈케이스 분석

### 3.1 Order 도메인

#### UC-1: 주문 생성 (`OrderFacade.createOrder`)

```
OrderFacade.createOrder() [@Retryable(max=10) + @Transactional]
  ├─ productService.validateAndDeductStock()          [쓰기] 상품 조회 + 가격 검증 + 재고 차감
  │     읽기: 락 없음 (MVCC 스냅샷)
  │     쓰기: @Version 낙관적 락 (ProductModel) — flush 시 UPDATE ... WHERE version=?
  ├─ brandService.getNameMapByIds()                   [읽기] 브랜드명 조회
  │     락 없음 (MVCC 스냅샷)
  ├─ orderService.createOrder()                       [쓰기] 주문 + 주문항목 INSERT (Cascade)
  │     INSERT 행 X-Lock (Auto-increment)
  ├─ couponService.useAndCalculateDiscount()           [쓰기] 쿠폰 사용 (벌크 UPDATE) — 조건부
  │     벌크 UPDATE 행 X-Lock + WHERE orderId IS NULL (원자적 CAS)
  ├─ orderService.applyDiscount()                     [쓰기] 주문 할인액 반영 (Dirty Checking) — 조건부
  │     Dirty Checking → flush 시 행 X-Lock (신규 INSERT 엔티티이므로 @Version 충돌 없음)
  └─ userService.deductPoint()                        [쓰기] 포인트 차감 (Dirty Checking)
        읽기: 락 없음 (MVCC 스냅샷)
        쓰기: @Version 낙관적 락 (UserModel) — flush 시 UPDATE ... WHERE version=?
```

**현재 트랜잭션 범위:**
- 전체 흐름이 **단일 트랜잭션**으로 묶여 있음
- 5개 서비스(Product, Brand, Order, Coupon, User) 호출이 동일 TX에 포함

**트랜잭션이 필요한 핵심 작업:**
- 재고 차감 (ProductModel.decreaseStock)
- 주문 생성 (OrderModel + OrderItemModel INSERT)
- 쿠폰 사용 (OwnedCoupon 벌크 UPDATE)
- 포인트 차감 (UserModel.deductPoint)

**트랜잭션이 불필요한 작업:**
- 브랜드명 조회 (getNameMapByIds) — 순수 읽기, 스냅샷 용도

---

#### UC-2: 주문 항목 취소 (`OrderFacade.cancelMyOrderItem`)

```
OrderFacade.cancelMyOrderItem() [@Retryable(max=5) + @Transactional]
  ├─ orderService.getByIdAndUserId()                  [읽기] 주문 조회 + 소유자 검증
  │     락 없음 (MVCC 스냅샷)
  ├─ orderService.cancelItem()                        [쓰기] 항목 취소 + 총액 재계산 + 주문 상태 변경
  │     @Version 낙관적 락 (OrderModel) — flush 시 UPDATE ... WHERE version=?
  │     Dirty Checking (OrderItemModel) — flush 시 행 X-Lock
  │   └─ OrderInfo.CancelledItem 반환                  [캡슐화] productId, quantity, orderFullyCancelled
  ├─ productService.increaseStock()                   [쓰기] 재고 복구
  │     읽기: 락 없음 (MVCC 스냅샷)
  │     쓰기: @Version 낙관적 락 (ProductModel) — flush 시 UPDATE ... WHERE version=?
  └─ couponService.restoreByOrderId()                 [쓰기] 전체 취소 시 쿠폰 복원 (조건부)
        읽기: 락 없음 (MVCC 스냅샷)
        쓰기: Dirty Checking (OwnedCouponModel) — flush 시 행 X-Lock
```

**이전 대비 개선:**
- `@Retryable(max=5)` 추가 — OrderModel @Version과 연계하여 동시 취소 제어
- `OrderInfo.CancelledItem` DTO로 도메인 상태 캡슐화 — Facade가 Entity 내부 상태에 직접 의존하지 않음

---

#### UC-3/4: 주문 조회 (`getMyOrders`, `getMyOrderDetail`, `getAllOrders`, `getOrderDetail`)

- 모두 `@Transactional(readOnly = true)` 적용
- `getMyOrderDetail`에서 `order.getItems()` 호출 시 LAZY 로딩 → `@BatchSize(100)` 적용으로 N+1 방지
- 조회 메서드 구조 적절

---

### 3.2 Product 도메인

#### UC-1: 상품 등록 (`ProductFacade.registerProduct`)

```
ProductFacade.registerProduct() [@Transactional]
  ├─ brandService.validateExists()                    [읽기] 브랜드 존재 검증
  │     락 없음 (MVCC 스냅샷)
  └─ productService.register()                        [쓰기] 상품 INSERT
        INSERT 행 X-Lock (Auto-increment)
```

- 읽기 검증 + 쓰기가 하나의 TX. 합리적 범위.

#### UC-2: 상품 목록 조회 - 좋아요 정렬 (`ProductFacade.getProductsWithActiveBrandSortedByLikes`)

```
ProductFacade.getProductsWithActiveBrandSortedByLikes() [@Transactional(readOnly = true)]
  ├─ productService.getAll()                          [읽기] 전체 상품 조회 (페이지네이션 없음)
  │     락 없음 (MVCC 스냅샷, readOnly TX)
  ├─ brandService.getActiveNameMapByIds()              [읽기] 활성 브랜드명 조회
  │     락 없음 (MVCC 스냅샷, readOnly TX)
  ├─ productLikeService.countLikesByProductIds()       [읽기] 좋아요 수 배치 조회
  │     락 없음 (MVCC 스냅샷, readOnly TX)
  └─ 인메모리 정렬 + 수동 페이지네이션                    [메모리] Comparator + PaginationUtils
```

- **전체 상품을 메모리에 로딩**하여 정렬 후 페이지 잘라내기
- 이전 분석과 동일한 구조 유지

#### UC-3/4: 상품 수정/삭제

- Dirty Checking 기반. 깔끔한 구조.

---

### 3.3 Coupon 도메인

#### UC-1: 쿠폰 발급 (`CouponFacade.issueCoupon` → `CouponService.issue`)

```
CouponFacade.issueCoupon() [@Retryable(max=50) + @Transactional]
  └─ CouponService.issue() [@Transactional(REQUIRED → 합류)]
      ├─ couponRepository.findById()                   [읽기] 쿠폰 조회
      │     락 없음 (MVCC 스냅샷)
      ├─ ownedCouponRepository.findByCouponIdAndUserId() [읽기] 중복 발급 검증
      │     락 없음 (MVCC 스냅샷)
      ├─ coupon.issue()                                [Dirty Checking] issuedQuantity++
      │     @Version 낙관적 락 (CouponModel) — flush 시 UPDATE ... WHERE version=?
      └─ ownedCouponRepository.save()                  [쓰기] 보유 쿠폰 INSERT
            INSERT 행 X-Lock (Auto-increment)
```

**이전 대비 개선:**
- @Retryable이 Facade로 이동 → TX 커밋 시점의 OptimisticLockingFailure를 정상 캐치
- @Retryable이 @Transactional 바깥에서 감싸므로, 재시도 시 새 TX 생성
- 프록시 순서: `Retryable → Transactional → 실제 메서드` (정상)

#### UC-2: 쿠폰 사용 (`CouponService.useAndCalculateDiscount`)

```
CouponService.useAndCalculateDiscount() [@Transactional(REQUIRED → 합류)]
  ├─ ownedCouponRepository.findById()                  [읽기] 보유쿠폰 조회 → 영속성 컨텍스트 등록
  │     락 없음 (MVCC 스냅샷)
  ├─ owned.validateMinOrderAmount()                    [검증] 최소주문금액 검증
  ├─ owned.validateUsable()                            [검증] 사용 가능 여부 검증
  ├─ ownedCouponRepository.useByIdWhenAvailable()      [벌크 UPDATE] 쿠폰 사용 처리
  │     벌크 UPDATE 행 X-Lock + WHERE orderId IS NULL (원자적 CAS)
  │   └─ @Modifying(flushAutomatically=true, clearAutomatically=true)
  └─ owned.calculateDiscount()                         [계산] 할인액 산출 (detached 엔티티)
        락 없음 (영속성 컨텍스트에서 분리된 메모리 연산)
```

- 이전 분석과 동일 구조
- detached 엔티티의 `calculateDiscount()`는 `discountType`, `discountValue`만 참조 → 현재 안전

---

### 3.4 Brand 도메인

#### UC-1: 브랜드 삭제 (`BrandFacade.deleteBrand`)

```
BrandFacade.deleteBrand() [@Transactional]
  ├─ brandService.delete()                             [쓰기] 브랜드 Soft Delete
  │     읽기: 락 없음 (MVCC 스냅샷)
  │     쓰기: Dirty Checking — flush 시 행 X-Lock (deletedAt UPDATE)
  └─ productService.deleteAllByBrandId()               [쓰기] 해당 브랜드 상품 전체 Soft Delete
        읽기: 락 없음 (MVCC 스냅샷)
        쓰기: Dirty Checking — flush 시 N건 각각 행 X-Lock (deletedAt UPDATE)
```

- forEach Dirty Checking 기반 삭제 — 상품 수만큼 UPDATE 쿼리 발생
- 이전 분석과 동일 구조

---

### 3.5 User 도메인

#### UC-1: 비밀번호 변경 (`UserService.changePassword`)

```
UserService.changePassword() [@Transactional]
  ├─ userRepository.findByLoginId()                    [읽기] 사용자 조회
  │     락 없음 (MVCC 스냅샷)
  ├─ passwordEncoder.matches() x 2                     [메모리] 비밀번호 검증
  ├─ user.changePassword()                             [Dirty Checking] 비밀번호 변경
  │     @Version 낙관적 락 (UserModel) — flush 시 UPDATE ... WHERE version=?
  └─ userRepository.save(user)                         [불필요] 이미 managed 상태 — merge 호출이나 실질 효과 없음
```

- 이전 분석에서 지적한 **불필요한 save** 여전히 존재 (동작에 영향 없음)

---

### 3.6 ProductLike 도메인

#### UC-1: 내 좋아요 목록 (`ProductLikeFacade.getMyLikedProducts`)

```
ProductLikeFacade.getMyLikedProducts() [@Transactional(readOnly = true)]
  ├─ productLikeService.getLikesByUserId()             [읽기] 전체 좋아요 조회
  │     락 없음 (MVCC 스냅샷, readOnly TX)
  └─ .filter(like -> productService.existsById(...))   [읽기] 상품별 존재 검증 (N+1!)
        락 없음 (MVCC 스냅샷, readOnly TX) — N건 개별 SELECT
```

- 이전 분석과 동일 — N+1 쿼리 패턴 유지

---

## 4. 영속성 컨텍스트 관점 분석

### 4.1 Dirty Checking 의존 패턴

| 위치 | 메서드 | 패턴 |
|------|--------|------|
| ProductService.update() | `getById().update()` | managed 엔티티 변경 → flush 시 UPDATE |
| ProductService.delete() | `getById().delete()` | managed 엔티티 deletedAt 설정 |
| ProductService.deleteAllByBrandId() | `findAll().forEach(delete)` | 다수 엔티티 각각 UPDATE |
| OrderService.applyDiscount() | `order.applyDiscount()` | Facade에서 전달받은 managed 엔티티 |
| OrderService.cancelItem() | `order.cancelItem()` → OrderInfo.CancelledItem 반환 | managed 엔티티의 상태 변경 |
| UserService.changePassword() | `user.changePassword()` + `save()` | 불필요한 save 포함 |
| CouponService.update() | `getById().update()` | managed 엔티티 변경 |

### 4.2 지연 로딩 분석

| 관계 | 로딩 전략 | 최적화 | 접근 시점 |
|------|----------|--------|----------|
| OrderModel.items (OneToMany) | LAZY | @BatchSize(100) | OrderDetail 조회 시 |
| OrderItemModel.order (ManyToOne) | LAZY | 개별 접근 시 SELECT | 사용 빈도 낮음 |

- Product, Brand, Coupon, User 엔티티는 **관계 매핑 없이 ID 참조**만 사용
- 지연 로딩 위험이 **Order 도메인에 집중**됨

### 4.3 벌크 연산과 영속성 컨텍스트 불일치

**`CouponService.useAndCalculateDiscount()` 상세 분석:**

```java
OwnedCouponModel owned = ownedCouponRepository.findById(id);  // (1) 영속성 컨텍스트 등록
owned.validateMinOrderAmount(orderAmount);                      // (2) 검증
owned.validateUsable(userId);                                   // (3) 검증
int updated = ownedCouponRepository.useByIdWhenAvailable(       // (4) 벌크 UPDATE
        ownedCouponId, orderId, ZonedDateTime.now());           //     → flush + clear 실행
return owned.calculateDiscount(orderAmount);                    // (5) detached 엔티티 접근
```

- (4)에서 `clearAutomatically=true`로 영속성 컨텍스트가 초기화됨
- (5)에서 `owned`는 detached 상태지만, `calculateDiscount()`는 `discountType`과 `discountValue`만 참조
- 이 값들은 (1)에서 로딩된 메모리 값이므로 현재는 **안전**
- **위험 시나리오**: 향후 `calculateDiscount()` 내부에서 lazy-loaded 필드를 접근하면 `LazyInitializationException` 발생 가능

### 4.4 Entity 반환 vs DTO Projection

| 계층 | 반환 타입 | 평가 |
|------|----------|------|
| Service → Facade | Entity (ProductModel, OrderModel 등) | 변경 감지 대상으로 유지 (의도적) |
| Facade → Controller | Result DTO (ProductResult, OrderResult 등) | 적절 — Entity 노출 방지 |
| Repository → Service | Entity | JPA 표준 패턴 |

---

## 5. 잔존 문제점 및 위험도

### [MODERATE] P1: OrderFacade.createOrder — 과도한 트랜잭션 범위

> 이전 분석: CRITICAL → 현재: MODERATE (OrderModel @Version 추가로 동시성 리스크 일부 완화)

**현상:**
5개 서비스 호출(Product, Brand, Order, Coupon, User)이 단일 트랜잭션에 포함

**현재 트랜잭션 내 작업:**
```
OrderFacade.createOrder()
  ├─ [쓰기] 재고 차감 (ProductModel x N개) — @Version 낙관적 락
  ├─ [읽기] 브랜드명 조회 — 락 없음 (MVCC 스냅샷)
  ├─ [쓰기] 주문 생성 (OrderModel + OrderItemModel INSERT) — INSERT 행 X-Lock
  ├─ [쓰기] 쿠폰 사용 (벌크 UPDATE) — 행 X-Lock + WHERE orderId IS NULL (원자적 CAS), 조건부
  ├─ [쓰기] 할인 적용 (Dirty Checking) — 행 X-Lock, 조건부
  └─ [쓰기] 포인트 차감 (Dirty Checking) — @Version 낙관적 락
```

**영향:**
- 트랜잭션 지속 시간 동안 ProductModel에 대한 행 레벨 락 유지 (재고 차감)
- 다른 주문 요청의 동일 상품 재고 접근이 대기 상태로 전환
- 쿠폰/포인트 처리 중 오류 시 재고 차감도 롤백 — 데이터 일관성은 유지
- OrderModel @Version 추가로 주문 자체의 동시성은 개선되었으나, TX 범위는 여전히 넓음

**위험도:** 높은 동시성 환경에서 처리량 저하 가능. 단, 현재 구조에서 원자성이 보장되므로 데이터 정합성 문제는 없음.

---

### [INFO] P2: ProductLikeFacade.getMyLikedProducts — N+1 쿼리

**현상:**
```java
productLikeService.getLikesByUserId(userId).stream()
    .filter(like -> productService.existsById(like.getProductId()))  // 건당 SELECT
    .map(ProductLikeResult::from)
    .toList();
```

- 좋아요 N건에 대해 상품 존재 확인을 건건이 수행
- readOnly TX 내이므로 락 문제는 없으나, 쿼리 수가 선형 증가

---

### [INFO] P3: ProductFacade 좋아요 정렬 — 전체 조회

**현상:**
```java
productService.getAll()  // Pageable 없이 전체 List 조회
```

- 좋아요 정렬을 위해 전체 상품을 메모리에 로딩
- 상품 수 증가 시 OOM 위험 및 응답 시간 증가
- readOnly TX이므로 스냅샷 비용은 낮으나, 데이터 전송/메모리 비용은 존재

---

### [INFO] P4: BrandFacade.deleteBrand — 연쇄 Soft Delete

**현상:**
```java
brandService.delete(id);                    // 브랜드 1건 UPDATE
productService.deleteAllByBrandId(id);      // 상품 N건 각각 UPDATE (forEach Dirty Checking)
```

- `deleteAllByBrandId`는 조회 후 forEach로 Dirty Checking 기반 삭제
- 상품 수만큼 UPDATE 쿼리 발생 (벌크 UPDATE 미사용)
- 브랜드 삭제는 빈번하지 않으므로 현실적 위험도는 낮음

---

### [INFO] P5: UserService.changePassword — 불필요한 save 호출

**현상:**
```java
user.changePassword(passwordEncoder.encode(rawNewPassword));
userRepository.save(user);  // managed 엔티티에 대한 불필요한 save
```

- `user`는 이미 `findByLoginId`로 조회된 managed 엔티티
- `changePassword()`로 필드 변경 후 Dirty Checking이 자동 적용
- `save()` 호출은 불필요하지만 동작에 영향 없음

---

## 6. 개선 제안

### [제안 1] OrderFacade.createOrder — 조회를 트랜잭션 외부로 분리

**현재:**
```
[---- 단일 트랜잭션 --------------------------------------------------]
재고차감 → 브랜드조회 → 주문생성 → 쿠폰사용 → 할인적용 → 포인트차감
```

**개선안:**
```
[TX 외부] 브랜드명 조회 (스냅샷 용도)
[---- 트랜잭션 ----------------------------------------]
재고차감 → 주문생성 → 쿠폰사용 → 할인적용 → 포인트차감
```

**고려 사항:**
- 브랜드명은 ProductSnapshot에 저장하는 시점의 스냅샷이므로 TX 외부 조회 허용 가능
- 조회 시점과 저장 시점 사이 브랜드명 변경 가능성 존재하나, 스냅샷 특성상 허용 가능
- 트랜잭션 시간을 브랜드 조회만큼 단축 — 효과는 제한적이나 원칙적으로 올바른 방향

---

### [제안 2] ProductLikeFacade.getMyLikedProducts — 배치 조회

**현재:** 건당 existsById 호출 (N+1)

**개선안:**
```java
@Transactional(readOnly = true)
public List<ProductLikeResult> getMyLikedProducts(Long userId) {
    List<ProductLikeModel> likes = productLikeService.getLikesByUserId(userId);
    Set<Long> existingIds = productService.findExistingIds(
            likes.stream().map(ProductLikeModel::getProductId).toList());
    return likes.stream()
        .filter(like -> existingIds.contains(like.getProductId()))
        .map(ProductLikeResult::from)
        .toList();
}
```

**고려 사항:**
- `getAllByIds`는 요청 수와 응답 수 불일치 시 예외를 던지므로, 별도의 유연한 배치 메서드 필요
- `findAllByIdIn`을 직접 사용하여 존재하는 ID만 Set으로 수집하는 방식
- N개 쿼리 → 1개 쿼리로 감소

---

### [제안 3] ProductFacade 좋아요 정렬 — DB 레벨 처리

**현재:** 전체 로딩 + 인메모리 정렬

**개선안 A — QueryDSL 서브쿼리:**
- 좋아요 수 기준 정렬 + 페이지네이션을 DB에서 처리

**개선안 B — 비정규화:**
- `products` 테이블에 `like_count` 컬럼 추가
- 좋아요/취소 시 카운트 동기화

**고려 사항:**
- 비정규화 시 좋아요/취소마다 카운트 동기화 필요 (이벤트 or 직접 갱신)
- 현재 상품 수가 적다면 인메모리 정렬도 허용 가능

---

### [제안 4] UserService.changePassword — 불필요한 save 제거

**현재:**
```java
user.changePassword(passwordEncoder.encode(rawNewPassword));
userRepository.save(user);  // 불필요
```

**개선안:**
```java
user.changePassword(passwordEncoder.encode(rawNewPassword));
// save 제거 — Dirty Checking으로 자동 UPDATE
```

**고려 사항:**
- 동작에 영향 없는 코드 정리 수준. 우선순위 낮음.

---

## 부록: 전체 @Transactional 선언 일람

### Facade Layer

| 클래스 | 메서드 | 어노테이션 | 비고 |
|--------|--------|-----------|------|
| OrderFacade | createOrder | @Retryable(max=10) + @Transactional | 주문 생성 |
| OrderFacade | getMyOrders | @Transactional(readOnly=true) | |
| OrderFacade | getMyOrderDetail | @Transactional(readOnly=true) | |
| OrderFacade | getAllOrders | @Transactional(readOnly=true) | |
| OrderFacade | getOrderDetail | @Transactional(readOnly=true) | |
| OrderFacade | cancelMyOrderItem | @Retryable(max=5) + @Transactional | 주문 항목 취소 |
| OrderFacade | cancelOrderItem | @Retryable(max=5) + @Transactional | 어드민 항목 취소 |
| ProductFacade | registerProduct | @Transactional | |
| ProductFacade | getProduct | @Transactional(readOnly=true) | |
| ProductFacade | updateProduct | @Transactional | |
| ProductFacade | deleteProduct | @Transactional | |
| ProductFacade | getProducts | @Transactional(readOnly=true) | |
| ProductFacade | getProductsByBrandId | @Transactional(readOnly=true) | |
| ProductFacade | getProductsWithActiveBrand | @Transactional(readOnly=true) | |
| ProductFacade | getProductsWithActiveBrandByBrandId | @Transactional(readOnly=true) | |
| ProductFacade | getProductsWithActiveBrandSortedByLikes | @Transactional(readOnly=true) | 전체 로딩 |
| ProductFacade | getProductsWithActiveBrandByBrandIdSortedByLikes | @Transactional(readOnly=true) | |
| ProductLikeFacade | like | @Transactional | |
| ProductLikeFacade | unlike | @Transactional | |
| ProductLikeFacade | getMyLikedProducts | @Transactional(readOnly=true) | N+1 |
| CouponFacade | registerCoupon | @Transactional | |
| CouponFacade | getCoupon | @Transactional(readOnly=true) | |
| CouponFacade | getCoupons | @Transactional(readOnly=true) | |
| CouponFacade | updateCoupon | @Transactional | |
| CouponFacade | deleteCoupon | @Transactional | |
| CouponFacade | getIssuedCoupons | @Transactional(readOnly=true) | |
| CouponFacade | issueCoupon | @Retryable(max=50) + @Transactional | 쿠폰 발급 |
| CouponFacade | getMyOwnedCoupons | @Transactional(readOnly=true) | |
| BrandFacade | registerBrand | @Transactional | |
| BrandFacade | getBrand | @Transactional(readOnly=true) | |
| BrandFacade | updateBrand | @Transactional | |
| BrandFacade | deleteBrand | @Transactional | 연쇄 삭제 |
| BrandFacade | getBrands | @Transactional(readOnly=true) | |
| UserFacade | signup | @Transactional | |
| UserFacade | getMyInfo | @Transactional(readOnly=true) | |
| UserFacade | changePassword | @Transactional | |

### Service Layer

| 클래스 | 메서드 | 어노테이션 | 비고 |
|--------|--------|-----------|------|
| CouponService | issue | @Transactional | Facade TX에 합류 |
| CouponService | useAndCalculateDiscount | @Transactional | 벌크 UPDATE |
| CouponService | restoreByOrderId | @Transactional | |
| ProductService | validateAndDeductStock | @Transactional | |
| ProductService | deleteAllByBrandId | @Transactional | forEach 삭제 |
| OrderService | createOrder | @Transactional | CASCADE 포함 |
| OrderService | applyDiscount | @Transactional | Dirty Checking |
| OrderService | cancelItem | @Transactional | OrderInfo.CancelledItem 반환 |
| UserService | deductPoint | @Transactional | |
| UserService | changePassword | @Transactional | 불필요한 save |
