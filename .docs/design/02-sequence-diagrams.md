# 02. 시퀀스 다이어그램

---

## 작성 원칙

- 기능 하나당 하나의 시퀀스
- 최소 레이어: User → Controller → Service/Facade → Domain(Entity/Policy) → Repository
- 존재 여부 체크, 분기(alt/else), 상태 변경, 이벤트 발행을 표현
- 도메인 객체가 "빈 껍데기"가 되지 않도록, 검증/상태 전이 책임을 도메인에 부여
- 01-requirements.md에 정의된 API URI 기준으로 작성

## 시퀀스 선별 기준

단순 CRUD(조회, 등록, 수정)는 시퀀스를 생략하고, **상태 전이/동시성 제어/연쇄 처리** 등 설계 의도가 드러나는 핵심 흐름만 선별하여 작성한다.

| # | 시퀀스 | 선별 이유 |
|---|--------|----------|
| 1 | 상품 좋아요 등록 | 중복 방지(UK) + like_count 동기 증감 |
| 2 | 상품 좋아요 취소 | soft delete + like_count 감소 흐름 |
| 3 | 주문 생성 | 원자적 재고 차감 + 스냅샷 저장 (가장 복잡한 트랜잭션) |
| 4 | 브랜드 삭제 | 연쇄 상품 소프트 삭제 (Aggregate 간 조율) |
| 5 | 상품 목록 조회 | 필터/정렬/페이징 조합 + 고객 노출 정보 매핑 |
| 6 | 어드민 상품 등록 | 브랜드 활성 검증 선행 조건 |

---

## 1) 상품 좋아요 등록

핵심: 로그인 확인 → 상품 존재/노출 확인 → 중복 좋아요 확인 → 생성 → like_count 반영

![상품 좋아요 등록 시퀀스](./images/seq-01-like-create.png)

<details>
<summary>Mermaid 원본</summary>

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as LikeController
  participant Service as LikeService
  participant ProductRepo as ProductRepository
  participant LikeRepo as ProductLikeRepository
  participant Product as Product(Entity)

  User->>Controller: POST /api/v1/products/{productId}/likes
  Note over Controller: X-Loopers-LoginId/LoginPw 헤더로 사용자 식별
  Controller->>Service: createLike(userId, productId)

  Service->>ProductRepo: findById(productId)
  alt 상품 없음 또는 노출 불가
    ProductRepo-->>Service: empty
    Service-->>Controller: 404 존재하지 않는 상품
    Controller-->>User: 404
  else 상품 존재
    ProductRepo-->>Service: product

    Service->>LikeRepo: findByUserIdAndProductId(userId, productId)
    alt 이미 좋아요 존재 (deleted_at IS NULL)
      LikeRepo-->>Service: like exists
      Service-->>Controller: 409 이미 좋아요한 상품
      Controller-->>User: 409
    else 좋아요 없음
      LikeRepo-->>Service: empty

      Note over Service,LikeRepo: DB UNIQUE(user_id, product_id) 제약으로 동시성 방어
      Service->>LikeRepo: save(ProductLike(userId, productId))
      LikeRepo-->>Service: saved

      Service->>Product: incrementLikeCount()
      Product-->>Service: updated
      Service->>ProductRepo: save(product)

      Service-->>Controller: 200 {liked: true, likeCount: N}
      Controller-->>User: 200 OK
    end
  end
```

</details>

**책임 분리 포인트**:
- `LikeService`: 존재 확인 + 중복 검증 (애플리케이션 규칙)
- `Product(Entity)`: like_count 증감 (도메인 규칙 - 카운트는 상품의 속성)
- `ProductLikeRepository`: 유니크 제약으로 동시성 최종 방어 (인프라)

---

## 2) 상품 좋아요 취소

![상품 좋아요 취소 시퀀스](./images/seq-02-like-cancel.png)

<details>
<summary>Mermaid 원본</summary>

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as LikeController
  participant Service as LikeService
  participant LikeRepo as ProductLikeRepository
  participant Product as Product(Entity)
  participant ProductRepo as ProductRepository

  User->>Controller: DELETE /api/v1/products/{productId}/likes
  Controller->>Service: cancelLike(userId, productId)

  Service->>LikeRepo: findByUserIdAndProductId(userId, productId)
  alt 좋아요 없음
    LikeRepo-->>Service: empty
    Service-->>Controller: 404 좋아요한 기록이 없습니다
    Controller-->>User: 404
  else 좋아요 존재
    LikeRepo-->>Service: like

    Service->>LikeRepo: softDelete(like)
    Note over Service,LikeRepo: deleted_at = now()
    LikeRepo-->>Service: deleted

    Note over Service: like에서 productId 추출 → 상품의 likeCount 갱신
    Service->>ProductRepo: findById(productId)
    ProductRepo-->>Service: product
    Service->>Product: decrementLikeCount()
    Product-->>Service: updated
    Service->>ProductRepo: save(product)

    Service-->>Controller: 200 {liked: false, likeCount: N}
    Controller-->>User: 200 OK
  end
```

</details>

---

## 3) 주문 생성 (핵심: 재고 확인/차감 + 스냅샷 저장)

핵심: 복수 상품 주문 → 전체 재고 확인 → **원자적 재고 차감** → Order + OrderItem(스냅샷) 생성

![주문 생성 시퀀스](./images/seq-03-order-create.png)

<details>
<summary>Mermaid 원본</summary>

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as OrderController
  participant Service as OrderService
  participant ProductRepo as ProductRepository
  participant StockPolicy as StockPolicy(Domain)
  participant Order as Order(Entity)
  participant OrderRepo as OrderRepository

  User->>Controller: POST /api/v1/orders {items: [{productId, quantity}, ...]}
  Controller->>Service: createOrder(userId, items)

  Service->>Service: validateItems(items)
  alt items 비어있음 또는 수량 비정상
    Service-->>Controller: 400 주문 항목이 비어있습니다
    Controller-->>User: 400
  else 입력값 유효
    loop 각 item에 대해
      Service->>ProductRepo: findById(item.productId)
      alt 상품 없음 또는 판매 불가
        ProductRepo-->>Service: empty / not saleable
        Service-->>Controller: 404/409 판매 불가 상품 포함
        Controller-->>User: error
      else 상품 존재 + 판매 가능
        ProductRepo-->>Service: product
      end
    end

    Note over Service,StockPolicy: 트랜잭션 시작 - 재고 차감은 원자적으로 수행
    Service->>StockPolicy: validateAndDeductStock(items)

    loop 각 item에 대해 (비관적 락)
      StockPolicy->>ProductRepo: findByIdForUpdate(productId)
      Note over StockPolicy,ProductRepo: SELECT ... FOR UPDATE (행 잠금)
      ProductRepo-->>StockPolicy: product(locked)
      StockPolicy->>StockPolicy: assertStockSufficient(product.stock, quantity)
      alt 재고 부족
        StockPolicy-->>Service: 409 재고 부족 (productId, 요청: N, 가용: M)
        Note over Service: 트랜잭션 롤백 - 이전 차감 모두 취소
        Service-->>Controller: 409
        Controller-->>User: 409 재고 부족
      else 재고 충분
        StockPolicy->>StockPolicy: product.deductStock(quantity)
        StockPolicy->>ProductRepo: save(product)
      end
    end
    StockPolicy-->>Service: 재고 차감 완료

    Note over Service,Order: 스냅샷 생성 - 주문 시점의 상품 정보를 고정
    Service->>Order: createOrder(userId, products, items)
    Note over Order: Order 생성 (status=PLACED)
    Note over Order: OrderItem 생성 (상품명, 브랜드명, 가격, 수량 스냅샷)
    Order-->>Service: order

    Service->>OrderRepo: save(order)
    OrderRepo-->>Service: saved(orderId)

    Service-->>Controller: 200 {orderId, status: PLACED, items: [...]}
    Controller-->>User: 200 OK
  end
```

</details>

**책임 분리 포인트**:
- `OrderService`: 흐름 조율 (애플리케이션 서비스)
- `StockPolicy(Domain)`: 재고 검증/차감 규칙 (도메인 정책 - "재고가 충분한가?", "차감 가능한가?")
- `Order(Entity)`: 주문 생성 + OrderItem 스냅샷 조립 (도메인 - 스냅샷은 주문의 책임)
- `ProductRepository`: 비관적 락 + 영속화 (인프라)

**트랜잭션 경계**: `createOrder` 전체가 하나의 `@Transactional`

---

## 4) 브랜드 삭제 (연쇄 상품 삭제)

핵심: 브랜드 삭제 시 소속 상품 일괄 소프트 삭제

![브랜드 삭제 시퀀스](./images/seq-04-brand-delete.png)

<details>
<summary>Mermaid 원본</summary>

```mermaid
sequenceDiagram
  autonumber
  actor Admin
  participant Controller as BrandAdminController
  participant Service as BrandAdminService
  participant BrandRepo as BrandRepository
  participant ProductRepo as ProductRepository
  participant Brand as Brand(Entity)

  Admin->>Controller: DELETE /api-admin/v1/brands/{brandId}
  Note over Controller: X-Loopers-Ldap 헤더 검증
  Controller->>Service: deleteBrand(brandId)

  Service->>BrandRepo: findById(brandId)
  alt 브랜드 없음
    BrandRepo-->>Service: empty
    Service-->>Controller: 404 존재하지 않는 브랜드
    Controller-->>Admin: 404
  else 브랜드 존재
    BrandRepo-->>Service: brand
    Service->>Brand: assertNotDeleted()
    alt 이미 삭제됨
      Brand-->>Service: already deleted
      Service-->>Controller: 409 이미 삭제된 브랜드
      Controller-->>Admin: 409
    else 삭제 가능
      Brand-->>Service: ok

      Note over Service,Brand: 브랜드 소프트 삭제
      Service->>Brand: delete()
      Note over Brand: deleted_at = now()
      Service->>BrandRepo: save(brand)

      Note over Service,ProductRepo: 소속 상품 일괄 소프트 삭제
      Service->>ProductRepo: softDeleteAllByBrandId(brandId)
      Note over ProductRepo: UPDATE products SET deleted_at=now() WHERE brand_id=? AND deleted_at IS NULL
      ProductRepo-->>Service: deletedCount

      Service-->>Controller: 200 {brandId, deletedProductCount: N}
      Controller-->>Admin: 200 OK
    end
  end
```

</details>

**설계 의도**:
- 브랜드 삭제와 상품 삭제는 하나의 트랜잭션으로 수행 (정합성 보장)
- 기존 주문의 OrderItem 스냅샷에는 영향 없음 (스냅샷은 주문 시점에 고정됨)

---

## 5) 상품 목록 조회 (필터/정렬)

![상품 목록 조회 시퀀스](./images/seq-05-product-list.png)

<details>
<summary>Mermaid 원본</summary>

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as ProductController
  participant Service as ProductQueryService
  participant ProductRepo as ProductRepository

  User->>Controller: GET /api/v1/products?brandId=1&sort=latest&page=0&size=20
  Controller->>Service: getProducts(brandId, sort, page, size)

  Service->>Service: validateParams(page, size, sort)
  alt 파라미터 비정상
    Service-->>Controller: 400 잘못된 요청
    Controller-->>User: 400
  else 파라미터 정상
    Note over Service,ProductRepo: 조회 조건: deleted_at IS NULL + 노출 가능 상태
    Service->>ProductRepo: findByConditions(brandId, sort, pageable)
    Note over ProductRepo: brandId 필터(선택) + status IN(ACTIVE, SOLD_OUT) + 정렬 + 페이징
    ProductRepo-->>Service: Page<Product>

    Service->>Service: toProductListResponse(products)
    Note over Service: 고객 노출 정보만 매핑 (재고 수량 제외, 품절 여부만)

    Service-->>Controller: 200 {content: [...], page, size, totalElements, hasNext}
    Controller-->>User: 200 OK
  end
```

</details>

---

## 6) 어드민 상품 등록 (브랜드 검증 포함)

![어드민 상품 등록 시퀀스](./images/seq-06-admin-product-create.png)

<details>
<summary>Mermaid 원본</summary>

```mermaid
sequenceDiagram
  autonumber
  actor Admin
  participant Controller as ProductAdminController
  participant Service as ProductAdminService
  participant BrandRepo as BrandRepository
  participant Product as Product(Entity)
  participant ProductRepo as ProductRepository

  Admin->>Controller: POST /api-admin/v1/products {brandId, name, price, stock, ...}
  Note over Controller: X-Loopers-Ldap 헤더 검증
  Controller->>Service: createProduct(request)

  Service->>BrandRepo: findById(request.brandId)
  alt 브랜드 없음
    BrandRepo-->>Service: empty
    Service-->>Controller: 404 존재하지 않는 브랜드
    Controller-->>Admin: 404
  else 브랜드 존재
    BrandRepo-->>Service: brand
    Service->>Service: assertBrandActive(brand)
    alt 브랜드 비활성/삭제
      Service-->>Controller: 409 삭제된 브랜드에 상품 등록 불가
      Controller-->>Admin: 409
    else 브랜드 활성
      Service->>Product: create(brandId, name, price, stock, ...)
      Note over Product: 초기 status=ACTIVE, 초기 재고 설정
      Product-->>Service: product
      Service->>ProductRepo: save(product)
      ProductRepo-->>Service: saved(productId)

      Service-->>Controller: 201 {productId, ...}
      Controller-->>Admin: 201 Created
    end
  end
```

</details>

---

> 향후 확장 시퀀스(재고 예약 + 쿠폰 홀드 + 결제, 결제 콜백 멱등 처리)는 [`future/02-sequence-diagrams-expansion.md`](./future/02-sequence-diagrams-expansion.md) 참조
