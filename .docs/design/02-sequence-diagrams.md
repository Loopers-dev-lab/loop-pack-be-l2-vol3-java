# 시퀀스 다이어그램

## 다이어그램 목적
시퀀스 다이어그램을 통해 다음을 검증한다:
- 책임 분리: 각 객체가 맡은 역할이 명확한가
- 호출 순서: 비즈니스 로직의 흐름이 올바른가
- 트랜잭션 경계: 원자성이 보장되는 범위가 적절한가

---

## 1. 주문 생성 시퀀스

### 1.1 정상 흐름 (다건 주문)

**목적**: 재고 검증, 비관적 락, 트랜잭션 경계 확인

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as OrderV1Controller
    participant F as OrderFacade
    participant OS as OrderService
    participant PS as ProductService
    participant OR as OrderRepository
    participant PR as ProductRepository
    participant DB as Database

    C->>+Ctrl: POST /api/v1/orders
    Note over C,Ctrl: Headers: X-Loopers-LoginId, X-Loopers-LoginPw
    Note over C,Ctrl: Body: { items: [{productId, quantity}] }

    Ctrl->>+F: createOrder(userId, items)

    F->>+OS: createOrder(userId, items)

    Note over OS: @Transactional 시작

    loop 각 주문 항목에 대해
        OS->>+PS: getProductForOrder(productId)
        PS->>+PR: findByIdWithLock(productId)
        PR->>+DB: SELECT ... FOR UPDATE
        DB-->>-PR: Product
        PR-->>-PS: Product
        PS-->>-OS: Product

        OS->>OS: 재고 검증 (stock >= quantity)

        alt 재고 부족
            OS-->>F: throw CoreException(INSUFFICIENT_STOCK)
            Note over OS,DB: 전체 롤백
        end

        OS->>+PS: decreaseStock(productId, quantity)
        PS->>+PR: save(product)
        PR->>+DB: UPDATE products SET stock = stock - quantity
        DB-->>-PR: OK
        PR-->>-PS: Product
        PS-->>-OS: void
    end

    OS->>OS: totalPrice 계산
    OS->>OS: Order 엔티티 생성
    OS->>OS: OrderItem 엔티티들 생성 (가격 스냅샷)

    OS->>+OR: save(order)
    OR->>+DB: INSERT orders, order_items
    DB-->>-OR: Order (with ID)
    OR-->>-OS: Order

    Note over OS: @Transactional 커밋

    OS-->>-F: Order
    F->>F: OrderInfo.from(order)
    F-->>-Ctrl: OrderInfo

    Ctrl->>Ctrl: OrderV1Dto.CreateResponse.from(info)
    Ctrl-->>-C: 201 Created + { orderId, totalPrice }
```

**핵심 포인트:**
- `SELECT ... FOR UPDATE`로 비관적 락 획득 → 동시 주문 시 재고 경쟁 방지
- 모든 상품 검증 후 차감 → 하나라도 실패 시 전체 롤백
- OrderItem에 가격 스냅샷 저장 → 상품 가격 변경 시에도 주문 가격 유지

---

### 1.2 재고 부족 실패 흐름

**목적**: 전체 실패 정책, 롤백 동작 확인

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as OrderV1Controller
    participant F as OrderFacade
    participant OS as OrderService
    participant PS as ProductService
    participant PR as ProductRepository
    participant DB as Database

    C->>+Ctrl: POST /api/v1/orders
    Note over C,Ctrl: items: [{productId: 1, qty: 10}, {productId: 2, qty: 5}]

    Ctrl->>+F: createOrder(userId, items)
    F->>+OS: createOrder(userId, items)

    Note over OS: @Transactional 시작

    OS->>+PS: getProductForOrder(productId: 1)
    PS->>+PR: findByIdWithLock(productId: 1)
    PR->>+DB: SELECT ... FOR UPDATE
    DB-->>-PR: Product (stock: 10)
    PR-->>-PS: Product
    PS-->>-OS: Product
    OS->>OS: 재고 검증 통과 (10 >= 10)
    OS->>PS: decreaseStock(1, 10)

    OS->>+PS: getProductForOrder(productId: 2)
    PS->>+PR: findByIdWithLock(productId: 2)
    PR->>+DB: SELECT ... FOR UPDATE
    DB-->>-PR: Product (stock: 3)
    PR-->>-PS: Product
    PS-->>-OS: Product

    OS->>OS: 재고 검증 실패 (3 < 5)

    OS-->>-F: throw CoreException(INSUFFICIENT_STOCK)
    Note over OS,DB: 전체 롤백 (상품1 재고 복구)

    F-->>-Ctrl: throw CoreException
    Ctrl-->>-C: 400 Bad Request + INSUFFICIENT_STOCK
```

**핵심 포인트:**
- 두 번째 상품 재고 부족 시 첫 번째 상품 재고 차감도 롤백
- 트랜잭션 단위로 원자성 보장

---

## 2. 좋아요 등록 시퀀스 (토글 방식)

### 2.1 신규 좋아요 등록

**목적**: 상품 유효성 검증 및 좋아요 등록 확인

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as ProductLikeV1Controller
    participant F as ProductLikeFacade
    participant LS as ProductLikeService
    participant PS as ProductService
    participant LR as ProductLikeRepository
    participant DB as Database

    C->>+Ctrl: POST /api/v1/products/{productId}/likes
    Note over C,Ctrl: Headers: X-Loopers-LoginId, X-Loopers-LoginPw

    Ctrl->>+F: like(userId, productId)

    F->>+LS: like(userId, productId)

    LS->>+PS: getProduct(productId)
    PS-->>-LS: Product

    LS->>LS: 상품 삭제 여부 검증

    LS->>+LR: findByUserIdAndProductId(userId, productId)
    LR->>+DB: SELECT FROM product_likes
    DB-->>-LR: null (미존재)
    LR-->>-LS: Optional<ProductLike> (empty)

    LS->>LS: ProductLike 엔티티 생성 (신규 등록)

    LS->>+LR: save(productLike)
    LR->>+DB: INSERT product_likes
    DB-->>-LR: ProductLike
    LR-->>-LS: ProductLike

    LS-->>-F: ProductLike
    F-->>-Ctrl: void

    Ctrl-->>-C: 200 OK + { message: "좋아요가 등록되었습니다." }
```
**핵심 포인트:**

- *좋아요 기능 설계 시 아래와 같은 두 가지 선택지가 있었다. 정렬 쿼리를 위해 Product 내부에 좋아요 필드를 두는 방법과 좋아요 테이블을 따로 두는 선택지 중 정합성을 높이는 방식을 선택했다.*

*1. 비정규화: Product에 likeCount 필드를 두고 좋아요 등록/취소 시 동기 업데이트. 정렬 쿼리 성능 우수*

*2. 실시간 집계(적용): 좋아요 테이블에서 COUNT 집계. 정합성 높으나 정렬 시 쿼리 비용 증가*




- 상품 존재 및 삭제 여부 먼저 검증
- 기존 좋아요가 없으면 신규 등록

---

### 2.2 기존 좋아요 존재 시 (토글 - 취소 처리)

**목적**: 토글 방식 동작 확인 - 이미 좋아요가 있으면 취소

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as ProductLikeV1Controller
    participant F as ProductLikeFacade
    participant LS as ProductLikeService
    participant PS as ProductService
    participant LR as ProductLikeRepository
    participant DB as Database

    C->>+Ctrl: POST /api/v1/products/{productId}/likes

    Ctrl->>+F: like(userId, productId)
    F->>+LS: like(userId, productId)

    LS->>+PS: getProduct(productId)
    PS-->>-LS: Product

    LS->>+LR: findByUserIdAndProductId(userId, productId)
    LR->>+DB: SELECT FROM product_likes
    DB-->>-LR: ProductLike (존재)
    LR-->>-LS: Optional<ProductLike> (present)

    Note over LS: 이미 존재하므로 좋아요 취소 (토글)

    LS->>+LR: delete(productLike)
    LR->>+DB: DELETE FROM product_likes
    DB-->>-LR: OK
    LR-->>-LS: void

    LS-->>-F: void
    F-->>-Ctrl: void

    Ctrl-->>-C: 200 OK + { message: "좋아요가 취소되었습니다." }
```

**핵심 포인트:**
- 좋아요가 이미 존재하면 삭제 (토글 방식)
- POST 요청 한 번으로 등록/취소 모두 처리

---

## 3. 브랜드 삭제 시퀀스 (Cascade 삭제)

**목적**: Cascade 삭제 순서, 트랜잭션 경계 확인

```mermaid
sequenceDiagram
    autonumber
    participant C as Admin Client
    participant Int as AdminAuthInterceptor
    participant Ctrl as BrandAdminV1Controller
    participant F as BrandFacade
    participant BS as BrandService
    participant PS as ProductService
    participant LS as ProductLikeService
    participant BR as BrandRepository
    participant PR as ProductRepository
    participant LR as ProductLikeRepository
    participant DB as Database

    C->>+Int: DELETE /api-admin/v1/brands/{brandId}
    Note over C,Int: Headers: X-Loopers-Ldap: loopers.admin

    Int->>Int: Admin 권한 검증
    Int->>+Ctrl: 요청 전달

    Ctrl->>+F: deleteBrand(brandId)
    F->>+BS: deleteBrand(brandId)

    Note over BS: @Transactional 시작

    BS->>+BR: findById(brandId)
    BR->>+DB: SELECT FROM brands
    DB-->>-BR: Brand
    BR-->>-BS: Brand

    alt 브랜드 없음
        BS-->>F: throw CoreException(BRAND_NOT_FOUND)
    end

    BS->>+PS: getProductsByBrandId(brandId)
    PS->>+PR: findAllByBrandId(brandId)
    PR->>+DB: SELECT FROM products WHERE brand_id = ?
    DB-->>-PR: List<Product>
    PR-->>-PS: List<Product>
    PS-->>-BS: List<Product>

    loop 각 상품에 대해
        BS->>+LS: deleteAllByProductId(productId)
        LS->>+LR: deleteAllByProductId(productId)
        LR->>+DB: DELETE FROM product_likes WHERE product_id = ?
        DB-->>-LR: OK
        LR-->>-LS: void
        LS-->>-BS: void

        BS->>+PS: deleteProduct(productId)
        PS->>PS: product.delete()
        PS->>+PR: save(product)
        PR->>+DB: UPDATE products SET deleted_at = NOW()
        DB-->>-PR: OK
        PR-->>-PS: Product
        PS-->>-BS: void
    end

    BS->>BS: brand.delete()
    BS->>+BR: save(brand)
    BR->>+DB: UPDATE brands SET deleted_at = NOW()
    DB-->>-BR: OK
    BR-->>-BS: Brand

    Note over BS: @Transactional 커밋

    BS-->>-F: void
    F-->>-Ctrl: void

    Ctrl-->>-C: 200 OK + { message: "브랜드가 삭제되었습니다." }
```

**핵심 포인트:**
- 삭제 순서: 좋아요(Hard) → 상품(Soft) → 브랜드(Soft)
- 단일 트랜잭션으로 원자성 보장
- 좋아요는 Hard Delete, 상품/브랜드는 Soft Delete

---

## 4. 상품 목록 조회 시퀀스 (좋아요 수 포함)

**목적**: 좋아요 실시간 집계, 정렬 옵션 처리 확인

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as ProductV1Controller
    participant F as ProductFacade
    participant PS as ProductService
    participant LS as ProductLikeService
    participant PR as ProductRepository
    participant LR as ProductLikeRepository
    participant DB as Database

    C->>+Ctrl: GET /api/v1/products?sort=like_desc&brandId=1&page=0&size=20
    Note over C,Ctrl: Headers: X-Loopers-LoginId, X-Loopers-LoginPw

    Ctrl->>+F: getProducts(sort, brandId, pageable)

    F->>+PS: getProducts(sort, brandId, pageable)

    alt sort = like_desc (좋아요 많은순)
        PS->>+PR: findAllOrderByLikeCountDesc(brandId, pageable)
        PR->>+DB: SELECT p.*, COUNT(pl.id) as like_count<br/>FROM products p<br/>LEFT JOIN product_likes pl<br/>GROUP BY p.id<br/>ORDER BY like_count DESC
        DB-->>-PR: Page<Product, likeCount>
        PR-->>-PS: Page<ProductWithLikeCount>
    else sort = latest | price_asc
        PS->>+PR: findAll(brandId, pageable, sort)
        PR->>+DB: SELECT FROM products WHERE ...
        DB-->>-PR: Page<Product>
        PR-->>-PS: Page<Product>

        PS->>+LS: getLikeCounts(productIds)
        LS->>+LR: countByProductIdIn(productIds)
        LR->>+DB: SELECT product_id, COUNT(*)<br/>FROM product_likes<br/>WHERE product_id IN (...)<br/>GROUP BY product_id
        DB-->>-LR: Map<productId, count>
        LR-->>-LS: Map<Long, Long>
        LS-->>-PS: Map<Long, Long>
    end

    PS-->>-F: Page<ProductWithLikeCount>

    F->>F: List<ProductInfo>.from(products)
    F-->>-Ctrl: Page<ProductInfo>

    Ctrl->>Ctrl: ProductV1Dto.ListResponse.from(page)
    Ctrl-->>-C: 200 OK + { products: [...], pageInfo: {...} }
```

**핵심 포인트:**
- `like_desc` 정렬 시 JOIN + COUNT로 한 번에 조회
- 다른 정렬 시 상품 조회 후 좋아요 수 별도 조회 (N+1 방지를 위해 IN 쿼리 사용)
- *쿠팡과 오늘의 집에서 하듯이, 주문 목록 조회 시 기간(startAt, endAt)으로 조회하는 방안을 검토해 보았으나 설계에 집중하기 위해 넣지 않았다.*
---

## 5. 어드민 인증 흐름

**목적**: Interceptor + ArgumentResolver 조합 확인

```mermaid
sequenceDiagram
    autonumber
    participant C as Admin Client
    participant F as Filter Chain
    participant Int as AdminAuthInterceptor
    participant AR as AdminUserArgumentResolver
    participant Ctrl as AdminController

    C->>+F: Request to /api-admin/v1/**
    Note over C,F: Headers: X-Loopers-Ldap: loopers.admin

    F->>+Int: preHandle()

    Int->>Int: Extract X-Loopers-Ldap header

    alt Header missing
        Int-->>C: 401 Unauthorized<br/>ADMIN_UNAUTHORIZED
    else Header != "loopers.admin"
        Int-->>C: 401 Unauthorized<br/>ADMIN_UNAUTHORIZED
    else Header = "loopers.admin"
        Int-->>-F: true (continue)
    end

    F->>+AR: resolveArgument()
    Note over AR: AdminUser 파라미터 존재 시
    AR->>AR: Create AdminUser object
    AR-->>-F: AdminUser

    F->>+Ctrl: Controller method
    Ctrl-->>-F: Response
    F-->>-C: Response
```

**핵심 포인트:**
- Interceptor가 1차 방어선 (헤더 누락/불일치 시 401)
- ArgumentResolver는 컨트롤러에 AdminUser 객체 주입
- 이중 안전장치로 보안성 강화
- *Interceptor 방식은 컨트롤러에서 어드민 정보 접근 시 Request에서 다시 추출 필요하고 특정 메서드만 예외 처리하려면 추가 로직 필요한 문제*
- *ArgumentResolver는 모든 메서드에 @AdminAuth 파라미터 추가 필요하고, 실수로 어노테이션을 누락하면 보안 위험한 문제*

*-> Interceptor + ArgumentResolver 조합으로 문제를 해결했다.*