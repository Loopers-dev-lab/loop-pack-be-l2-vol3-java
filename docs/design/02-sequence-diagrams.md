# 시퀀스 다이어그램

## 1. 개요

핵심 유스케이스의 객체 간 상호작용을 시퀀스 다이어그램으로 표현한다.

### 다이어그램 읽는 법

| 표기 | 의미 |
|------|------|
| 실선 화살표 (`->>`) | 동기 메시지 (호출) |
| 점선 화살표 (`-->>`) | 응답 (반환) |
| 세로 막대 (activate/deactivate) | 액티베이션 바 - 객체가 일하고 있는 시간 |
| `rect` 블록 | 트랜잭션 경계 등 논리적 그룹 |
| `alt` 블록 | 조건 분기 (if-else) |
| `loop` 블록 | 반복문 |
| `Note over` | 설명 노트 |

---

## 2. 주문 생성 (Order Creation)

### 왜 이 다이어그램이 필요한가?

주문 생성은 시스템에서 가장 복잡한 흐름이다. 다음을 검증하기 위해 필요:
- 재고 확인 → 비관적 락 차감이 **단일 트랜잭션**으로 처리되는지
- 쿠폰 적용이 **조건부 UPDATE**로 원자적으로 처리되는지
- 스냅샷 생성 시점이 올바른지
- 예외 상황(재고 부족, 쿠폰 사용 불가)에서 롤백이 보장되는지

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as OrderController
    participant Facade as OrderFacade
    participant ProductRepo as ProductRepository
    participant BrandRepo as BrandRepository
    participant CouponFacade as CouponFacade
    participant CouponIssueRepo as CouponIssueRepository
    participant OrderRepo as OrderRepository
    participant DB as Database

    Client->>Controller: POST /orders (items, couponId?)
    activate Controller
    Controller->>Facade: createOrder(memberId, items, couponId)
    activate Facade

    rect rgb(240, 248, 255)
        Note over Facade,DB: 트랜잭션 경계

        Note over Facade: 1. 상품 ID 정렬 후 일괄 비관적 락
        Facade->>ProductRepo: findAllByIdsWithLock(sortedIds)
        activate ProductRepo
        ProductRepo->>DB: SELECT ... FOR UPDATE (ID 정렬)
        DB-->>ProductRepo: List~Product~
        ProductRepo-->>Facade: Map~Long, Product~
        deactivate ProductRepo

        loop 각 주문 항목에 대해
            alt 상품 미존재
                Facade-->>Controller: NotFound 예외 → 롤백
            else 재고 부족
                Facade-->>Controller: BadRequest 예외 → 롤백
            else 정상
                Note over Facade: product.decreaseStock(quantity)
            end
        end

        Note over Facade: 2. 브랜드 일괄 조회 (N+1 방지)
        Facade->>BrandRepo: findAllByIds(brandIds)
        activate BrandRepo
        BrandRepo-->>Facade: Map~Long, Brand~
        deactivate BrandRepo

        Note over Facade: 3. 스냅샷 생성 (상품명, 가격, 브랜드명, 수량)

        opt couponId가 있는 경우
            Note over Facade: 4. 쿠폰 적용
            Facade->>CouponFacade: applyCouponToOrder(couponId, memberId, totalPrice)
            activate CouponFacade
            CouponFacade->>CouponIssueRepo: findById(couponId)
            CouponIssueRepo-->>CouponFacade: CouponIssue

            alt 쿠폰 미존재 / 타인 소유 / 만료 / 최소금액 미달
                CouponFacade-->>Facade: 예외 → 롤백
            end

            CouponFacade->>CouponIssueRepo: markAsUsed(id, now)
            Note over CouponIssueRepo: UPDATE ... WHERE status='AVAILABLE' AND expired_at > now
            CouponIssueRepo-->>CouponFacade: affected rows

            alt affected rows = 0
                CouponFacade-->>Facade: 이미 사용/만료 예외 → 롤백
            end

            CouponFacade-->>Facade: CouponApplyResult(couponIssueId, discountAmount)
            deactivate CouponFacade
        end

        Note over Facade: 5. 주문 저장
        Facade->>OrderRepo: save(Order.create(...))
        activate OrderRepo
        OrderRepo->>DB: INSERT orders + order_items (CASCADE)
        DB-->>OrderRepo: Order (with ID)
        OrderRepo-->>Facade: Order
        deactivate OrderRepo

        opt couponId가 있는 경우
            Note over Facade: 6. 쿠폰에 주문 ID 연결
            Facade->>CouponFacade: linkCouponToOrder(couponIssueId, orderId)
        end
    end

    Facade-->>Controller: Order
    deactivate Facade
    Controller-->>Client: 201 Created
    deactivate Controller
```

### 읽는 법

1. **rect 블록**이 트랜잭션 경계 — 이 안의 모든 작업이 성공하거나 모두 롤백
2. **비관적 락**: ID 정렬 후 `SELECT ... FOR UPDATE`로 데드락 방지
3. **opt 블록**: 쿠폰이 있을 때만 실행되는 선택적 흐름
4. **조건부 UPDATE**: markAsUsed가 `WHERE status='AVAILABLE'`로 이중 사용 원자적 방지

### 핵심 설계 포인트

| 포인트 | 설명 |
|--------|------|
| **단일 트랜잭션** | 재고 차감 + 쿠폰 사용 + 주문 저장이 원자적으로 처리 |
| **비관적 락** | Product를 ID 정렬 후 일괄 `SELECT ... FOR UPDATE` (데드락 방지) |
| **조건부 UPDATE** | 쿠폰은 `markAsUsed` 조건부 UPDATE로 이중 사용 방지 (락 불필요) |
| **스냅샷** | 주문 시점의 상품명, 가격, 브랜드명 + 할인 정보 저장 |
| **N+1 방지** | 상품은 `findAllByIdsWithLock`, 브랜드는 `findAllByIds`로 일괄 조회 |

---

## 3. 좋아요 등록 (Like - POST)

### 왜 이 다이어그램이 필요한가?

좋아요 등록은 다음을 검증하기 위해 필요:
- POST/DELETE 분리 방식의 RESTful 설계가 올바른지
- **멱등성**이 어떻게 보장되는지 (이미 좋아요 시 무시)
- 락 없이 UNIQUE 제약으로 동시성을 처리하는 구조

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as LikeController
    participant Facade as LikeFacade
    participant ProductRepo as ProductRepository
    participant LikeRepo as LikeRepository
    participant DB as Database

    Client->>Controller: POST /products/{productId}/likes
    activate Controller
    Controller->>Facade: addLike(memberId, productId)
    activate Facade

    rect rgb(240, 248, 255)
        Note over Facade,DB: 트랜잭션 경계

        Facade->>ProductRepo: findById(productId)
        activate ProductRepo
        ProductRepo->>DB: SELECT product WHERE id = ?
        DB-->>ProductRepo: Product
        ProductRepo-->>Facade: Product
        deactivate ProductRepo

        alt 상품이 존재하지 않거나 삭제됨
            Facade-->>Controller: NotFound 예외
            Controller-->>Client: 404 Not Found
        end

        Facade->>LikeRepo: existsByMemberIdAndProductId(memberId, productId)
        activate LikeRepo
        LikeRepo->>DB: SELECT EXISTS(...)
        DB-->>LikeRepo: true/false
        LikeRepo-->>Facade: boolean
        deactivate LikeRepo

        alt 이미 좋아요 존재 (멱등성 보장)
            Note over Facade: 변경 없이 성공 반환
        else 좋아요 없음 → 저장
            Facade->>LikeRepo: save(new Like)
            activate LikeRepo
            LikeRepo->>DB: INSERT INTO likes
            Note over DB: UNIQUE(member_id, product_id) 제약으로 중복 방지
            DB-->>LikeRepo: Like
            LikeRepo-->>Facade: Like
            deactivate LikeRepo
        end
    end

    Facade-->>Controller: OK
    deactivate Facade
    Controller-->>Client: 200 OK
    deactivate Controller
```

### 읽는 법

1. **rect 블록** 안에서 Like 저장이 처리됨 (Product 수정 없음)
2. **alt "이미 좋아요 존재"** 분기에서 아무것도 하지 않고 성공 반환 → 멱등성 보장
3. likes 테이블의 **UNIQUE 제약**이 DB 레벨에서 중복을 방지

### 핵심 설계 포인트

| 포인트 | 설명 |
|--------|------|
| **RESTful** | POST로 리소스 생성 의도 명확 |
| **멱등성** | 이미 좋아요 존재 시 무시 (에러 아님) |
| **락 불필요** | Product.likeCount 제거. UNIQUE 제약 + COUNT(*) 파생으로 Product 행 경합 원천 제거 |
| **좋아요 수** | 조회 시 `SELECT COUNT(*) FROM likes WHERE product_id = ?` 또는 배치 GROUP BY |

---

## 4. 좋아요 취소 (Like - DELETE)

### 왜 이 다이어그램이 필요한가?

좋아요 취소 흐름에서 다음을 검증:
- DELETE 요청으로 리소스 삭제 의도가 명확한지
- **멱등성** — 이미 취소된 상태에서 다시 취소해도 에러 없이 성공

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as LikeController
    participant Facade as LikeFacade
    participant LikeRepo as LikeRepository
    participant DB as Database

    Client->>Controller: DELETE /products/{productId}/likes
    activate Controller
    Controller->>Facade: removeLike(memberId, productId)
    activate Facade

    rect rgb(240, 248, 255)
        Note over Facade,DB: 트랜잭션 경계

        Facade->>LikeRepo: findByMemberIdAndProductId(memberId, productId)
        activate LikeRepo
        LikeRepo->>DB: SELECT like WHERE member_id = ? AND product_id = ?
        DB-->>LikeRepo: Like or null
        LikeRepo-->>Facade: Optional~Like~
        deactivate LikeRepo

        alt 좋아요 없음 (멱등성 보장)
            Note over Facade: 변경 없이 성공 반환
        else 좋아요 존재 → 삭제
            Facade->>LikeRepo: delete(like)
            activate LikeRepo
            LikeRepo->>DB: DELETE FROM likes WHERE id = ?
            DB-->>LikeRepo: OK
            LikeRepo-->>Facade: OK
            deactivate LikeRepo
        end
    end

    Facade-->>Controller: OK
    deactivate Facade
    Controller-->>Client: 200 OK
    deactivate Controller
```

### 읽는 법

1. **alt "좋아요 없음"** 분기 — 이미 취소 상태면 아무것도 안 하고 성공
2. Like 삭제만 수행 (Product 수정 없음 — likeCount 컬럼이 없으므로)

### 핵심 설계 포인트

| 포인트 | 설명 |
|--------|------|
| **RESTful** | DELETE로 리소스 삭제 의도 명확 |
| **멱등성** | 좋아요 없을 때도 에러 아닌 성공 응답 |
| **락 불필요** | Like 행만 삭제. Product 행 수정 없어 경합 발생하지 않음 |

---

## 5. 좋아요 목록 조회 (My Likes)

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as LikeController
    participant Facade as LikeFacade
    participant LikeRepo as LikeRepository
    participant DB as Database

    Client->>Controller: GET /users/{userId}/likes
    activate Controller

    alt userId != memberId
        Controller-->>Client: 403 Forbidden
    end

    Controller->>Facade: getLikesByMemberId(userId)
    activate Facade

    Facade->>LikeRepo: findAllByMemberId(memberId)
    activate LikeRepo
    LikeRepo->>DB: SELECT * FROM likes WHERE member_id = ?
    DB-->>LikeRepo: List~Like~
    LikeRepo-->>Facade: List~Like~
    deactivate LikeRepo

    Facade-->>Controller: List~Like~
    deactivate Facade
    Controller-->>Client: 200 OK (좋아요 목록)
    deactivate Controller
```

### 핵심 설계 포인트

| 포인트 | 설명 |
|--------|------|
| **권한 검증** | Controller에서 userId == memberId 확인 (본인만 조회) |
| **단순 조회** | Like 엔티티 목록 반환 (productId 포함) |

---

## 6. 상품 등록 (Admin)

관리자가 새 상품을 등록하는 흐름이다.

```mermaid
sequenceDiagram
    autonumber
    participant Admin
    participant Controller as ProductController
    participant Service as ProductService
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository
    participant DB as Database

    Admin->>Controller: POST /api-admin/v1/products
    activate Controller
    Controller->>Service: createProduct(brandId, name, price, stockQuantity)
    activate Service

    Note over Service: 트랜잭션 시작

    Service->>BrandRepo: findById(brandId)
    activate BrandRepo
    BrandRepo->>DB: SELECT brand WHERE id = ?
    DB-->>BrandRepo: Brand
    BrandRepo-->>Service: Brand
    deactivate BrandRepo

    alt 브랜드가 존재하지 않거나 삭제됨
        Service-->>Controller: NotFound 예외
        Controller-->>Admin: 404 Not Found
    end

    Note over Service: Price VO 생성 (가격 검증)
    Note over Service: Stock VO 생성 (재고 검증)
    Note over Service: Product 생성

    Service->>ProductRepo: save(product)
    activate ProductRepo
    ProductRepo->>DB: INSERT INTO products
    DB-->>ProductRepo: Product (with ID)
    ProductRepo-->>Service: Product
    deactivate ProductRepo

    Note over Service: 트랜잭션 커밋

    Service-->>Controller: Product
    deactivate Service
    Controller-->>Admin: 201 Created (productId)
    deactivate Controller
```

### 핵심 설계 포인트

| 포인트 | 설명 |
|--------|------|
| **브랜드 검증** | 상품 등록 전 브랜드 존재 여부 확인 |
| **VO 검증** | Price, Stock 생성 시 유효성 검증 |
| **ID 참조** | Product는 brandId만 저장 (Brand 객체 참조 X) |

---

## 7. 브랜드 삭제 (연쇄 삭제)

브랜드 삭제 시 연관 데이터를 함께 처리하는 흐름이다.

```mermaid
sequenceDiagram
    autonumber
    participant Admin
    participant Controller as BrandAdminController
    participant Facade as BrandFacade
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository
    participant LikeRepo as LikeRepository
    participant DB as Database

    Admin->>Controller: DELETE /api-admin/v1/brands/{brandId}
    activate Controller
    Controller->>Facade: deleteBrand(brandId)
    activate Facade

    Note over Facade: 트랜잭션 시작

    Facade->>BrandRepo: findById(brandId)
    activate BrandRepo
    BrandRepo->>DB: SELECT brand WHERE id = ?
    DB-->>BrandRepo: Brand
    BrandRepo-->>Facade: Brand
    deactivate BrandRepo

    alt 브랜드가 존재하지 않거나 이미 삭제됨
        Facade-->>Controller: NotFound 예외
        Controller-->>Admin: 404 Not Found
    end

    Facade->>ProductRepo: findAllByBrandId(brandId)
    activate ProductRepo
    ProductRepo->>DB: SELECT products WHERE brand_id = ?
    DB-->>ProductRepo: List~Product~
    ProductRepo-->>Facade: List~Product~
    deactivate ProductRepo

    Note over Facade: 1단계: 좋아요 일괄 삭제 (배치)

    Facade->>LikeRepo: deleteAllByProductIdIn(productIds)
    activate LikeRepo
    LikeRepo->>DB: DELETE FROM likes WHERE product_id IN (...)
    DB-->>LikeRepo: OK
    LikeRepo-->>Facade: OK
    deactivate LikeRepo

    Note over Facade: 2단계: 상품들 soft delete

    loop 각 Product에 대해
        Note over Facade: product.delete() → dirty checking
    end

    Note over Facade: 3단계: 브랜드 soft delete
    Note over Facade: brand.delete() → dirty checking

    Note over Facade: 트랜잭션 커밋 → UPDATE products, UPDATE brand

    Facade-->>Controller: OK
    deactivate Facade
    Controller-->>Admin: 200 OK
    deactivate Controller
```

### 핵심 설계 포인트

| 포인트 | 설명 |
|--------|------|
| **Soft Delete** | 브랜드, 상품은 deleted_at 설정 (주문 이력 보존) |
| **Hard Delete** | 좋아요는 의미 없어 완전 삭제 |
| **삭제 순서** | 좋아요 → 상품 → 브랜드 (의존 순서 역순) |
| **단일 트랜잭션** | 연쇄 삭제가 원자적으로 처리 |

---

## 8. 쿠폰 발급 (Coupon Issue)

### 왜 이 다이어그램이 필요한가?

쿠폰 발급 흐름에서 다음을 검증:
- 쿠폰 템플릿의 만료 여부 확인
- CouponIssue 생성 및 저장

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as CouponController
    participant Facade as CouponFacade
    participant CouponRepo as CouponRepository
    participant IssueRepo as CouponIssueRepository
    participant DB as Database

    Client->>Controller: POST /coupons/{couponId}/issue
    activate Controller
    Controller->>Facade: issueCoupon(couponId, memberId)
    activate Facade

    rect rgb(240, 248, 255)
        Note over Facade,DB: 트랜잭션 경계

        Facade->>CouponRepo: findById(couponId)
        activate CouponRepo
        CouponRepo->>DB: SELECT coupon WHERE id = ?
        DB-->>CouponRepo: Coupon
        CouponRepo-->>Facade: Coupon
        deactivate CouponRepo

        alt 쿠폰 미존재
            Facade-->>Controller: NotFound 예외
        else 쿠폰 만료됨
            Facade-->>Controller: BadRequest 예외
        end

        Note over Facade: CouponIssue 생성 (couponId, memberId, expiredAt)

        Facade->>IssueRepo: save(couponIssue)
        activate IssueRepo
        IssueRepo->>DB: INSERT INTO coupon_issue
        DB-->>IssueRepo: CouponIssue (with ID)
        IssueRepo-->>Facade: CouponIssue
        deactivate IssueRepo
    end

    Facade-->>Controller: CouponIssue
    deactivate Facade
    Controller-->>Client: 201 Created
    deactivate Controller
```

### 핵심 설계 포인트

| 포인트 | 설명 |
|--------|------|
| **만료 검증** | 발급 시점에 쿠폰 템플릿 만료 여부 확인 |
| **상태 초기화** | CouponIssue는 AVAILABLE 상태로 생성 |
| **만료일 복사** | 쿠폰 템플릿의 expiredAt을 CouponIssue에 복사 |

---

## 9. 주문 취소 (Order Cancellation)

### 왜 이 다이어그램이 필요한가?

주문 취소 시 재고 복원과 쿠폰 복원이 원자적으로 처리되는지 검증:

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as OrderController
    participant Facade as OrderFacade
    participant OrderRepo as OrderRepository
    participant ProductRepo as ProductRepository
    participant CouponFacade as CouponFacade
    participant DB as Database

    Client->>Controller: POST /orders/{orderId}/cancel
    activate Controller
    Controller->>Facade: cancelOrder(orderId, memberId)
    activate Facade

    rect rgb(240, 248, 255)
        Note over Facade,DB: 트랜잭션 경계

        Facade->>OrderRepo: findById(orderId)
        OrderRepo-->>Facade: Order

        alt 주문 미존재 / 타인 주문
            Facade-->>Controller: 예외 → 롤백
        end

        Note over Facade: order.cancel() → status = CANCELLED

        Note over Facade: 재고 복원 (비관적 락)
        Facade->>ProductRepo: findAllByIdsWithLock(sortedProductIds)
        ProductRepo-->>Facade: List~Product~

        loop 각 OrderItem에 대해
            Note over Facade: product.increaseStock(quantity)
        end

        opt 쿠폰이 있는 경우
            Facade->>CouponFacade: restoreCoupon(couponIssueId)
            Note over CouponFacade: couponIssue.cancelUse() → status = AVAILABLE
        end
    end

    Facade-->>Controller: OK
    deactivate Facade
    Controller-->>Client: 200 OK
    deactivate Controller
```

### 핵심 설계 포인트

| 포인트 | 설명 |
|--------|------|
| **원자적 복원** | 재고 복원 + 쿠폰 복원이 단일 트랜잭션 |
| **비관적 락** | 재고 복원도 비관적 락으로 동시 주문과의 경합 방지 |
| **쿠폰 복원** | USED → AVAILABLE, usedOrderId = null |

---

## 10. 잠재 리스크

| 리스크 | 현재 상태 | 대응 방안 |
|--------|----------|----------|
| **트랜잭션 비대화** | 주문 생성 시 여러 상품 + 쿠폰 처리 | 상품 수 제한 또는 배치 처리 고려 |
| **좋아요 COUNT 비용** | 배치 GROUP BY로 최적화 완료 | 극단적 트래픽 시 캐시 도입 |
| **브랜드 삭제 시 대량 처리** | 배치 DELETE로 최적화 완료 | 상품이 매우 많으면 비동기 이벤트 처리 고려 |
| **쿠폰 조건부 UPDATE 경합** | affected rows 검증 | 동시 사용 시 1건만 성공, 나머지는 명확한 에러 |
