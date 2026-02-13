# 시퀀스 다이어그램 — 브랜드 & 상품

## API 공통 규칙

- 대고객 API: `/api/v1` prefix. 유저 식별 시 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더 사용.
- 어드민 API: `/api-admin/v1` prefix. 어드민 식별 시 `X-Loopers-Ldap: loopers.admin` 헤더 사용.
- 인증/인가는 구현하지 않으며, 헤더 기반 식별만 수행한다.

---

## 1. 일반 사용자 — 브랜드 상세 조회

Facade가 BrandService와 ProductService를 조합하여 브랜드 기본 정보 + 판매중 상품 수를 반환한다.

```mermaid
sequenceDiagram
    actor User as 일반 사용자
    participant Controller as BrandController
    participant Facade as BrandFacade
    participant BrandService as BrandService
    participant ProductService as ProductService
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository

    User->>Controller: GET /api/v1/brands/{brandId}
    Controller->>Facade: getBrandDetail(brandId)
    Facade->>BrandService: getBrand(brandId)
    BrandService->>BrandRepo: findById(brandId)
    BrandRepo-->>BrandService: BrandEntity
    BrandService-->>Facade: BrandEntity
    Facade->>ProductService: countSellingProducts(brandId)
    ProductService->>ProductRepo: countByBrandIdAndStatus(brandId, SELLING)
    ProductRepo-->>ProductService: count
    ProductService-->>Facade: count
    Facade-->>Controller: BrandInfo (이름, 소개, 웹사이트, 판매중 상품수)
    Controller-->>User: ApiResponse<BrandDetailResponse>
```

- 포인트: Facade가 BrandService와 ProductService를 각각 호출하여 조합한다. 브랜드 기본 정보와 판매중 상품 개수는 서로 다른 서비스의 책임이므로, Facade에서 조합하는 것이 적절하다.

## 2. 일반 사용자 — 상품 목록 조회 (정렬/페이징)

정렬 조건은 Controller에서 enum으로 변환, Repository에서 동적 정렬 처리.

```mermaid
sequenceDiagram
    actor User as 일반 사용자
    participant Controller as ProductController
    participant Facade as ProductFacade
    participant ProductService as ProductService
    participant ProductRepo as ProductRepository

    User->>Controller: GET /api/v1/brands/{brandId}/products?sort=PRICE_ASC&page=0&size=20
    Controller->>Facade: getProducts(brandId, sort, pageable)
    Facade->>ProductService: getProductsByBrand(brandId, sort, pageable)
    ProductService->>ProductRepo: findByBrandId(brandId, sort, pageable)
    ProductRepo-->>ProductService: Page<ProductEntity>
    ProductService-->>Facade: Page<ProductEntity>
    Facade-->>Controller: PageInfo<ProductInfo>
    Controller-->>User: ApiResponse<PageResponse<ProductListResponse>>
```

- 포인트: 정렬 조건은 Controller에서 enum으로 변환하여 전달하고, Repository에서 동적 정렬을 처리한다. Pageable은 Spring Data의 표준 페이징을 활용한다.

## 3. 일반 사용자 — 브랜드 검색

```mermaid
sequenceDiagram
    actor User as 일반 사용자
    participant Controller as BrandController
    participant Facade as BrandFacade
    participant BrandService as BrandService
    participant BrandRepo as BrandRepository

    User->>Controller: GET /api/v1/brands/search?keyword=나이키&page=0&size=20
    Controller->>Facade: searchBrands(keyword, pageable)
    Facade->>BrandService: searchBrands(keyword, pageable)
    BrandService->>BrandRepo: searchByName(keyword, pageable)
    BrandRepo-->>BrandService: Page<BrandEntity>
    BrandService-->>Facade: Page<BrandEntity>
    Facade-->>Controller: PageInfo<BrandInfo>
    Controller-->>User: ApiResponse<PageResponse<BrandListResponse>>
```

## 4. 일반 사용자 — 상품 검색

```mermaid
sequenceDiagram
    actor User as 일반 사용자
    participant Controller as ProductController
    participant Facade as ProductFacade
    participant ProductService as ProductService
    participant ProductRepo as ProductRepository

    User->>Controller: GET /api/v1/products/search?keyword=운동화&page=0&size=20
    Controller->>Facade: searchProducts(keyword, pageable)
    Facade->>ProductService: searchProducts(keyword, pageable)
    ProductService->>ProductRepo: searchByName(keyword, pageable)
    ProductRepo-->>ProductService: Page<ProductEntity>
    ProductService-->>Facade: Page<ProductEntity>
    Facade-->>Controller: PageInfo<ProductInfo>
    Controller-->>User: ApiResponse<PageResponse<ProductListResponse>>
```

## 5. Admin — 브랜드 목록 조회

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminBrandController
    participant Facade as BrandFacade
    participant BrandService as BrandService
    participant BrandRepo as BrandRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: GET /api-admin/v1/brands?page=0&size=20
    Controller->>Facade: getBrands(pageable)
    Facade->>BrandService: getBrands(pageable)
    BrandService->>BrandRepo: findAll(pageable)
    BrandRepo-->>BrandService: Page<BrandEntity>
    BrandService-->>Facade: Page<BrandEntity>
    Facade-->>Controller: PageInfo<BrandInfo>
    Controller-->>Admin: ApiResponse<PageResponse<BrandListResponse>>
```

## 6. Admin — 브랜드 등록

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminBrandController
    participant Facade as BrandFacade
    participant BrandService as BrandService
    participant BrandRepo as BrandRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: POST /api-admin/v1/brands
    Controller->>Facade: registerBrand(command)
    Facade->>BrandService: registerBrand(brand)
    Note over BrandService: Brand 도메인 객체 생성 (유효성 검증)
    BrandService->>BrandRepo: save(brandEntity)
    BrandRepo-->>BrandService: BrandEntity
    BrandService-->>Facade: BrandEntity
    Facade-->>Controller: BrandInfo
    Controller-->>Admin: ApiResponse<BrandDetailResponse>
```

## 7. Admin — 브랜드 수정

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminBrandController
    participant Facade as BrandFacade
    participant BrandService as BrandService
    participant BrandRepo as BrandRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: PUT /api-admin/v1/brands/{brandId}
    Controller->>Facade: updateBrand(brandId, command)
    Facade->>BrandService: updateBrand(brandId, brand)
    BrandService->>BrandRepo: findById(brandId)
    BrandRepo-->>BrandService: BrandEntity
    Note over BrandService: Brand 도메인 객체로 유효성 검증
    Note over BrandService: brandEntity.update(name, introduction, websiteUrl)
    BrandService->>BrandRepo: save(brandEntity)
    BrandRepo-->>BrandService: BrandEntity
    BrandService-->>Facade: BrandEntity
    Facade-->>Controller: BrandInfo
    Controller-->>Admin: ApiResponse<BrandDetailResponse>
```

## 8. Admin — 브랜드 삭제 (상품 일괄 삭제)

하나의 트랜잭션 안에서 상품 벌크 soft delete -> 브랜드 soft delete 순서로 처리.

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminBrandController
    participant Facade as BrandFacade
    participant BrandService as BrandService
    participant ProductService as ProductService
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: DELETE /api-admin/v1/brands/{brandId}
    Controller->>Facade: deleteBrand(brandId)

    rect rgb(255, 240, 240)
        Note over Facade, ProductRepo: @Transactional 경계
        Facade->>ProductService: deleteAllByBrandId(brandId)
        ProductService->>ProductRepo: bulkSoftDeleteByBrandId(brandId)
        Note over ProductRepo: UPDATE product SET deleted_at = NOW() WHERE brand_id = ?
        ProductRepo-->>ProductService: void
        ProductService-->>Facade: void
        Facade->>BrandService: deleteBrand(brandId)
        BrandService->>BrandRepo: softDelete(brandId)
        BrandRepo-->>BrandService: void
        BrandService-->>Facade: void
    end

    Facade-->>Controller: void
    Controller-->>Admin: ApiResponse<Void>
```

- 포인트: 브랜드 삭제와 상품 일괄 삭제는 하나의 트랜잭션 안에서 처리된다. 상품을 먼저 삭제한 후 브랜드를 삭제하는 순서. Facade 레벨에서 @Transactional을 걸어 두 서비스 호출을 묶는다.

## 9. Admin — 상품 등록

상품 등록 시 브랜드 존재 여부 확인 필수. Product 도메인 객체에서 유효성 검증 수행.

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminProductController
    participant Facade as ProductFacade
    participant BrandService as BrandService
    participant ProductService as ProductService
    participant BrandRepo as BrandRepository
    participant ProductRepo as ProductRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: POST /api-admin/v1/brands/{brandId}/products
    Controller->>Facade: registerProduct(brandId, command)
    Facade->>BrandService: getBrand(brandId)
    BrandService->>BrandRepo: findById(brandId)
    BrandRepo-->>BrandService: BrandEntity
    BrandService-->>Facade: BrandEntity
    Facade->>ProductService: registerProduct(brandId, command)
    Note over ProductService: Product 도메인 객체 생성 (유효성 검증)
    ProductService->>ProductRepo: save(productEntity)
    ProductRepo-->>ProductService: ProductEntity
    ProductService-->>Facade: ProductEntity
    Facade-->>Controller: ProductInfo
    Controller-->>Admin: ApiResponse<ProductDetailResponse>
```

- 포인트: 상품 등록 시 브랜드 존재 여부를 먼저 확인한다. 존재하지 않으면 CoreException(NOT_FOUND)을 던진다. Product 도메인 객체에서 비즈니스 유효성 검증(가격 > 0 등)을 수행한다.

## 10. Admin — 상품 수정

상품 수정 시 소속 브랜드는 변경 불가.

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminProductController
    participant Facade as ProductFacade
    participant ProductService as ProductService
    participant ProductRepo as ProductRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: PUT /api-admin/v1/brands/{brandId}/products/{productId}
    Controller->>Facade: updateProduct(productId, command)
    Facade->>ProductService: updateProduct(productId, command)
    ProductService->>ProductRepo: findById(productId)
    ProductRepo-->>ProductService: ProductEntity
    Note over ProductService: Product 도메인 객체로 유효성 검증
    Note over ProductService: productEntity.update(name, price, description, imageUrl, status)
    ProductService->>ProductRepo: save(productEntity)
    ProductRepo-->>ProductService: ProductEntity
    ProductService-->>Facade: ProductEntity
    Facade-->>Controller: ProductInfo
    Controller-->>Admin: ApiResponse<ProductDetailResponse>
```

## 11. Admin — 상품 삭제

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminProductController
    participant Facade as ProductFacade
    participant ProductService as ProductService
    participant ProductRepo as ProductRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: DELETE /api-admin/v1/brands/{brandId}/products/{productId}
    Controller->>Facade: deleteProduct(productId)
    Facade->>ProductService: deleteProduct(productId)
    ProductService->>ProductRepo: findById(productId)
    ProductRepo-->>ProductService: ProductEntity
    Note over ProductService: productEntity.delete() (soft delete)
    ProductService->>ProductRepo: save(productEntity)
    ProductRepo-->>ProductService: void
    ProductService-->>Facade: void
    Facade-->>Controller: void
    Controller-->>Admin: ApiResponse<Void>
```

## 12. Admin — 브랜드 상품 목록 조회

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminProductController
    participant Facade as ProductFacade
    participant ProductService as ProductService
    participant ProductRepo as ProductRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: GET /api-admin/v1/brands/{brandId}/products?page=0&size=20
    Controller->>Facade: getProductsByBrand(brandId, pageable)
    Facade->>ProductService: getProductsByBrand(brandId, pageable)
    ProductService->>ProductRepo: findByBrandId(brandId, pageable)
    ProductRepo-->>ProductService: Page<ProductEntity>
    ProductService-->>Facade: Page<ProductEntity>
    Facade-->>Controller: PageInfo<ProductInfo>
    Controller-->>Admin: ApiResponse<PageResponse<ProductListResponse>>
```

## 13. Admin — 브랜드 상품 상세 조회

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminProductController
    participant Facade as ProductFacade
    participant ProductService as ProductService
    participant ProductRepo as ProductRepository

    Note over Admin, Controller: Header: X-Loopers-Ldap: loopers.admin
    Admin->>Controller: GET /api-admin/v1/brands/{brandId}/products/{productId}
    Controller->>Facade: getProduct(productId)
    Facade->>ProductService: getProduct(productId)
    ProductService->>ProductRepo: findById(productId)
    ProductRepo-->>ProductService: ProductEntity
    ProductService-->>Facade: ProductEntity
    Facade-->>Controller: ProductInfo
    Controller-->>Admin: ApiResponse<ProductDetailResponse>
```

---

# 시퀀스 다이어그램 — 좋아요 & 주문

## 14. 좋아요 등록

멱등성 보장이 핵심. 서비스 레이어에서 기존 레코드 존재 여부를 먼저 확인하고, 이미 좋아요 상태면 에러 없이 기존 레코드를 반환한다. likeCount 증감은 같은 트랜잭션 내에서 비관적 락으로 처리한다.

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as LikeController
    participant Facade as LikeFacade
    participant UserSvc as UserService
    participant ProductSvc as ProductService
    participant LikeSvc as LikeService
    participant LikeRepo as LikeRepository
    participant ProductRepo as ProductRepository

    User->>Controller: POST /api/v1/products/{productId}/likes
    Note over User, Controller: X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 사용자 식별
    Controller->>Facade: like(loginId, productId)

    Facade->>UserSvc: getUserByLoginId(loginId)
    UserSvc-->>Facade: UserEntity

    Facade->>ProductSvc: getProduct(productId)
    ProductSvc-->>Facade: ProductEntity (존재 확인)

    rect rgb(255, 240, 220)
        Note over LikeSvc, ProductRepo: 트랜잭션 경계
        Facade->>LikeSvc: like(userId, productId)
        LikeSvc->>LikeRepo: findByUserIdAndProductId(userId, productId)

        alt 이미 좋아요 상태
            LikeRepo-->>LikeSvc: Optional(LikeEntity)
            LikeSvc-->>Facade: 기존 레코드 반환 (멱등)
        else 좋아요 없음
            LikeRepo-->>LikeSvc: Optional.empty()
            LikeSvc->>LikeRepo: save(new LikeEntity)
            LikeSvc->>ProductSvc: increaseLikeCount(productId)
            Note right of ProductSvc: 비관적 락으로 likeCount + 1
            ProductSvc->>ProductRepo: SELECT FOR UPDATE → UPDATE likeCount
            LikeSvc-->>Facade: 새 LikeEntity 반환
        end
    end

    Facade-->>Controller: LikedProductInfo
    Controller-->>User: ApiResponse (SUCCESS)
```

- 포인트: 서비스 레이어 사전 검증이 불필요한 likeCount 변경을 방지한다. DB 복합 유니크 키 `(user_id, product_id)`는 동시 요청 시 최종 방어선 역할.

---

## 15. 좋아요 취소

등록과 대칭적으로 멱등성을 보장한다. 좋아요 레코드가 없으면 에러 없이 무시한다.

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as LikeController
    participant Facade as LikeFacade
    participant UserSvc as UserService
    participant LikeSvc as LikeService
    participant ProductSvc as ProductService
    participant LikeRepo as LikeRepository
    participant ProductRepo as ProductRepository

    User->>Controller: DELETE /api/v1/products/{productId}/likes
    Note over User, Controller: X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 사용자 식별
    Controller->>Facade: unlike(loginId, productId)

    Facade->>UserSvc: getUserByLoginId(loginId)
    UserSvc-->>Facade: UserEntity

    rect rgb(255, 240, 220)
        Note over LikeSvc, ProductRepo: 트랜잭션 경계
        Facade->>LikeSvc: unlike(userId, productId)
        LikeSvc->>LikeRepo: findByUserIdAndProductId(userId, productId)

        alt 좋아요 레코드 없음
            LikeRepo-->>LikeSvc: Optional.empty()
            LikeSvc-->>Facade: 무시 (멱등)
        else 좋아요 존재
            LikeRepo-->>LikeSvc: Optional(LikeEntity)
            LikeSvc->>LikeRepo: delete(likeEntity)
            Note right of LikeRepo: Hard delete (soft delete 아님)
            LikeSvc->>ProductSvc: decreaseLikeCount(productId)
            Note right of ProductSvc: 비관적 락으로 likeCount - 1
            ProductSvc->>ProductRepo: SELECT FOR UPDATE → UPDATE likeCount
            LikeSvc-->>Facade: 완료
        end
    end

    Facade-->>Controller: void
    Controller-->>User: ApiResponse (SUCCESS)
```

- 포인트: 좋아요는 hard delete를 사용한다. soft delete 시 유니크 키에 deletedAt을 포함해야 하며, 재좋아요 시 복잡도가 증가하기 때문.

---

## 16. 좋아요 목록 조회

사용자가 좋아요 한 상품 목록을 상품 상세 포함하여 조회한다.

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as LikeController
    participant Facade as LikeFacade
    participant UserSvc as UserService
    participant LikeSvc as LikeService
    participant LikeRepo as LikeRepository

    User->>Controller: GET /api/v1/likes?page=0&size=20
    Note over User, Controller: X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 사용자 식별
    Controller->>Facade: getLikedProducts(loginId, pageable)

    Facade->>UserSvc: getUserByLoginId(loginId)
    UserSvc-->>Facade: UserEntity

    Facade->>LikeSvc: getLikedProducts(userId, pageable)
    LikeSvc->>LikeRepo: findByUserIdWithProduct(userId, pageable)
    Note right of LikeRepo: Like JOIN Product 조회
    LikeRepo-->>LikeSvc: Page<LikeEntity + ProductEntity>
    LikeSvc-->>Facade: Page<LikedProductInfo>

    Facade-->>Controller: Page<LikedProductInfo>
    Controller-->>User: ApiResponse (Page)
```

- 포인트: 헤더의 loginId로 사용자를 식별하므로 별도의 pathVariable 없이 본인의 좋아요 목록만 조회 가능하다.

---

## 17. 주문 생성

핵심 흐름: 상품 존재/판매 상태 확인 → 재고 비관적 락 차감 → 스냅샷과 함께 주문 생성. 하나라도 실패하면 전체 롤백.

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Facade as OrderFacade
    participant UserSvc as UserService
    participant ProductSvc as ProductService
    participant StockSvc as StockService
    participant OrderSvc as OrderService
    participant StockRepo as StockRepository
    participant OrderRepo as OrderRepository

    User->>Controller: POST /api/v1/orders { items: [{productId, quantity}, ...] }
    Note over User, Controller: X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 사용자 식별
    Controller->>Facade: createOrder(loginId, orderCommand)

    Facade->>UserSvc: getUserByLoginId(loginId)
    UserSvc-->>Facade: UserEntity

    rect rgb(255, 240, 220)
        Note over Facade, OrderRepo: 트랜잭션 경계
        Note over Facade: productId 오름차순 정렬 (데드락 방지)

        loop 각 주문 항목 (productId 오름차순)
            Facade->>ProductSvc: getProduct(productId)
            ProductSvc-->>Facade: ProductEntity (존재/판매중 확인)

            Facade->>StockSvc: deductStock(productId, quantity)
            StockSvc->>StockRepo: findByProductIdWithLock(productId)
            Note right of StockRepo: SELECT FOR UPDATE
            StockRepo-->>StockSvc: StockEntity

            alt 재고 부족 (quantity > stock)
                StockSvc-->>Facade: CoreException (재고 부족)
                Note over Facade, OrderRepo: 전체 롤백
            else 재고 충분
                StockSvc->>StockRepo: save (quantity 차감)
                alt 차감 후 재고 = 0
                    StockSvc->>ProductSvc: changeStatus(productId, OUT_OF_STOCK)
                end
                StockSvc-->>Facade: StockEntity
            end
        end

        Note over Facade: 상품 정보 스냅샷 생성 (name, price, imageUrl)
        Facade->>OrderSvc: createOrder(userId, orderItemSnapshots)
        OrderSvc->>OrderRepo: save(OrderEntity + OrderItemEntities)
        OrderSvc-->>Facade: OrderEntity
    end

    Facade-->>Controller: OrderInfo
    Controller-->>User: ApiResponse (SUCCESS)
```

- 포인트: productId 오름차순 정렬 후 순차적으로 락을 획득하여 데드락을 방지한다. 스냅샷 생성은 재고 차감이 모두 성공한 후, 주문 저장 직전에 수행.

---

## 18. 주문 목록 조회 (사용자)

기간 필터(startAt, endAt)와 페이징을 지원한다.

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Facade as OrderFacade
    participant UserSvc as UserService
    participant OrderSvc as OrderService
    participant OrderRepo as OrderRepository

    User->>Controller: GET /api/v1/orders?startAt=2026-01-31&endAt=2026-02-10&page=0&size=20
    Note over User, Controller: X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 사용자 식별
    Controller->>Facade: getOrders(loginId, startAt, endAt, pageable)

    Facade->>UserSvc: getUserByLoginId(loginId)
    UserSvc-->>Facade: UserEntity

    Facade->>OrderSvc: getOrdersByUserId(userId, startAt, endAt, pageable)
    OrderSvc->>OrderRepo: findByUserIdAndCreatedAtBetween(userId, startAt, endAt, pageable)
    OrderRepo-->>OrderSvc: Page<OrderEntity>
    OrderSvc-->>Facade: Page<OrderEntity>

    Facade-->>Controller: Page<OrderInfo>
    Controller-->>User: ApiResponse (Page)
```

- 포인트: 기간 필터는 `createdAt` 기준. startAt, endAt은 선택적 파라미터로, 미제공 시 전체 조회.

---

## 19. 주문 상세 조회 (사용자)

주문 기본 정보 + 주문 항목(스냅샷 포함)을 조회한다.

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderController
    participant Facade as OrderFacade
    participant UserSvc as UserService
    participant OrderSvc as OrderService
    participant OrderRepo as OrderRepository
    participant OrderItemRepo as OrderItemRepository

    User->>Controller: GET /api/v1/orders/{orderId}
    Note over User, Controller: X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 사용자 식별
    Controller->>Facade: getOrderDetail(loginId, orderId)

    Facade->>UserSvc: getUserByLoginId(loginId)
    UserSvc-->>Facade: UserEntity

    Facade->>OrderSvc: getOrder(orderId)
    OrderSvc->>OrderRepo: findById(orderId)
    OrderRepo-->>OrderSvc: OrderEntity
    OrderSvc-->>Facade: OrderEntity

    Note over Facade: 주문의 userId와 요청자 userId 일치 확인

    Facade->>OrderSvc: getOrderItems(orderId)
    OrderSvc->>OrderItemRepo: findByOrderId(orderId)
    OrderItemRepo-->>OrderSvc: List<OrderItemEntity>
    OrderSvc-->>Facade: List<OrderItemEntity>

    Facade-->>Controller: OrderDetailInfo (주문 + 항목 스냅샷)
    Controller-->>User: ApiResponse (OrderDetail)
```

- 포인트: Facade에서 주문 소유자 확인. 타 유저의 주문 상세 조회를 차단.

---

## 20. 주문 목록 조회 (어드민)

전체 주문을 페이징으로 조회한다. 필터 없음.

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminOrderController
    participant Facade as OrderFacade
    participant OrderSvc as OrderService
    participant OrderRepo as OrderRepository

    Admin->>Controller: GET /api-admin/v1/orders?page=0&size=20
    Note over Admin, Controller: X-Loopers-Ldap: loopers.admin 헤더 확인
    Controller->>Facade: getAllOrders(pageable)

    Facade->>OrderSvc: getAllOrders(pageable)
    OrderSvc->>OrderRepo: findAll(pageable)
    OrderRepo-->>OrderSvc: Page<OrderEntity>
    OrderSvc-->>Facade: Page<OrderEntity>

    Facade-->>Controller: Page<OrderInfo>
    Controller-->>Admin: ApiResponse (Page)
```

---

## 21. 주문 상세 조회 (어드민)

어드민은 소유자 확인 없이 모든 주문의 상세를 조회할 수 있다.

```mermaid
sequenceDiagram
    actor Admin as 관리자
    participant Controller as AdminOrderController
    participant Facade as OrderFacade
    participant OrderSvc as OrderService
    participant OrderRepo as OrderRepository
    participant OrderItemRepo as OrderItemRepository

    Admin->>Controller: GET /api-admin/v1/orders/{orderId}
    Note over Admin, Controller: X-Loopers-Ldap: loopers.admin 헤더 확인
    Controller->>Facade: getOrderDetailForAdmin(orderId)

    Facade->>OrderSvc: getOrder(orderId)
    OrderSvc->>OrderRepo: findById(orderId)
    OrderRepo-->>OrderSvc: OrderEntity
    OrderSvc-->>Facade: OrderEntity

    Facade->>OrderSvc: getOrderItems(orderId)
    OrderSvc->>OrderItemRepo: findByOrderId(orderId)
    OrderItemRepo-->>OrderSvc: List<OrderItemEntity>
    OrderSvc-->>Facade: List<OrderItemEntity>

    Facade-->>Controller: OrderDetailInfo (주문 + 항목 스냅샷)
    Controller-->>Admin: ApiResponse (OrderDetail)
```

- 포인트: 사용자 상세 조회(19번)와 동일한 흐름이지만 소유자 확인을 생략한다. Facade에 별도 메서드(`getOrderDetailForAdmin`)로 분리.
