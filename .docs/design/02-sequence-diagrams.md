# 시퀀스 다이어그램 (도메인: Brands, Products, Likes, Cart, Orders)

> 본 문서는 **책임 분리**, **호출 순서**, **트랜잭션 경계** 확인을 위해 시퀀스 다이어그램을 사용한다.  
> 각 다이어그램은 **이유 → 다이어그램 → 해석 → 잠재 리스크** 순서로 제시한다.  
> 참조: [00-ubiquitous-language.md](./00-ubiquitous-language.md)

---

## 0. 인증·인가 (전역 및 컨트롤러)

- **인증(전역)**
  - **AdminAuthInterceptor**: 경로 `/api-admin/**` 전담. `X-Loopers-Ldap` 검증, Admin SecurityContext 설정. 실패 시 401.
  - **CustomerAuthInterceptor**: **로그인이 필요한 고객 API 경로에만** 적용된다. 해당 경로를 반드시 거쳐야 하며, 헤더(`X-Loopers-LoginId` 등) 검증 후 고객 SecurityContext 설정, 실패 시 401.
  - **비회원 허용(조회)**: 상품·브랜드 조회 등 로그인 불필요 API는 인터셉터 적용 대상에서 **제외**한다. 따라서 해당 경로는 인터셉터를 거치지 않으며 비회원도 접근 가능하다.
- **인가(컨트롤러)**: 어드민 전용 API는 해당 Controller에 `@PreAuthorize("hasRole('ADMIN')")`를 적용한다. 검증 실패 시 해당 지점에서 차단(403 등).

### 다이어그램 — 어드민 요청 흐름

아래는 **어드민 API 공통** 흐름이다. 상품·브랜드·주문 등 도메인별로 Admin*V1Controller, *Facade, \*Service가 동일 패턴으로 적용된다.

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAuth as AdminAuthInterceptor
    participant Controller as Admin*V1Controller
    participant Facade as *Facade
    participant Service as *Service

    Note over Admin, AdminAuth: [인증] /api-admin/** 전 구간
    Admin->>+AdminAuth: API 호출 (X-Loopers-Ldap)
    AdminAuth->>AdminAuth: 헤더 검증, SecurityContext(Admin) 설정
    AdminAuth->>-Controller: 요청 전달

    Note over Controller: [인가] @PreAuthorize("hasRole('ADMIN')")
    Controller->>+Facade: 비즈니스 호출
    Facade->>+Service: 도메인 호출
    Service-->>-Facade: 결과
    Facade-->>-Controller: 결과
    Controller-->>Admin: 응답
```

### 다이어그램 — 고객 요청 흐름

아래는 **로그인 필요한 고객 API** 공통 흐름이다. 회원(users), 좋아요(likes), 장바구니(cart), 주문(orders) 등 도메인별로 *V1Controller, *Facade, \*Service가 동일 패턴으로 적용된다. **User 도메인**(회원가입, 내 정보 조회, 비밀번호 변경 등)도 동일하게 Controller → Facade → Service 순이며, 트랜잭션 경계는 Facade에 둔다. 상세 클래스·API는 [AGENTS.md](../../AGENTS.md) §User Domain Class Design 및 §API & Error Specifications를 참조한다.

```mermaid
sequenceDiagram
    actor Customer
    participant CustomerAuth as CustomerAuthInterceptor
    participant Controller as *V1Controller
    participant Facade as *Facade
    participant Service as *Service

    Note over Customer, CustomerAuth: [인증] 로그인 필요 경로 적용
    Customer->>+CustomerAuth: API 호출 (X-Loopers-LoginId 등)
    CustomerAuth->>CustomerAuth: 헤더 검증, SecurityContext(고객) 설정
    CustomerAuth->>-Controller: 요청 전달

    Controller->>+Facade: 비즈니스 호출
    Facade->>+Service: 도메인 호출
    Service-->>-Facade: 결과
    Facade-->>-Controller: 결과
    Controller-->>Customer: 응답
```

---

## 1. Brands · Products — 상품 등록 (Product ↔ Brand)

- 상품 등록 시 Brands 도메인(브랜드)과의 경계, Controller → Facade → Service 호출 순서, 트랜잭션 경계가 한 요청 안에서 어떻게 유지되는지 검증하기 위함.
- 브랜드 유효성(존재·미삭제) 검사 후 상품 생성이 단일 트랜잭션으로 이루어지는지, 실패 시 롤백이 기대대로 동작하는지 검증하기 위함.

### 다이어그램

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as AdminProductV1Controller
    participant Facade as ProductFacade
    participant ProductService
    participant BrandRepository
    participant ProductRepository

    Note over Admin, Controller: 인증된 관리자
    Admin->>Controller: 상품 등록 요청

    Controller->>+Facade: 상품 등록 위임 register(request)
    Note over Facade: @Transactional

    Facade->>+ProductService: 브랜드 검증 후 상품 생성 register(brandId, name, price, ...)

    ProductService->>+BrandRepository: 브랜드 조회 findById(brandId)
    BrandRepository-->>-ProductService: Brand or empty

    alt 브랜드 미존재 또는 삭제됨
        ProductService-->>Facade: 예외(BAD_REQUEST / NOT_FOUND)
    else 브랜드 유효
        ProductService->>+ProductRepository: 상품 저장 save(product)
        ProductRepository-->>-ProductService: 저장된 Product
        ProductService-->>Facade: ProductInfo
    end

    % alt 블록이 끝난 후 ProductService 비활성화
    deactivate ProductService

    Facade-->>-Controller: 상품 등록 결과(ProductInfo)
    Controller-->>Admin: 201 Created
```

### 해석

- **봐야 할 포인트**: 인증된 어드민 요청(§0 참조)이 AdminProductV1Controller → ProductFacade → ProductService 순으로 처리된다. **Facade는 interfaces 계층의 request(DTO)를 받아, 도메인/애플리케이션에서 사용할 파라미터나 VO로 변환한 뒤 Service를 호출한다. Domain은 interfaces DTO를 알지 않는다.** 트랜잭션은 Facade에서 시작되므로, 브랜드 조회·상품 생성·저장이 한 단위로 커밋/롤백된다. 브랜드가 없거나 삭제된 경우 예외로 빠져 나가며 DB 변경 없이 끝난다.
- **설계 의도**: 상품은 반드시 유효한 브랜드에만 속한다는 도메인 규칙을 Service에서 한 번에 검증·생성하도록 했다.

### 잠재 리스크

- **리스크**: BrandRepository가 soft delete를 구분하지 않으면, 삭제된 브랜드에 상품이 등록될 수 있다.
- **선택지**: (A) Repository에 `findByIdAndNotDeleted` 등 삭제 제외 조회를 두고 사용. (B) Service에서 조회된 Brand 엔티티의 삭제 플래그를 검사 후 진행.

---

## 2. Products — 상품 수정 (관리자 권한, 브랜드 검증)

- 어드민 상품 수정 시 권한 검증이 support에서 선행되고, 그 다음 도메인(Products)만 다루는지 호출 순서를 명확히 하기 위함.
- 인증/권한이 Controller 이전(support)에서 처리되는지, 브랜드 변경 불가·삭제된 상품 수정 불가가 Service에서 일관되게 적용되는지 검증하기 위함.

### 다이어그램

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as AdminProductV1Controller
    participant Facade as ProductFacade
    participant ProductService
    participant ProductRepository

    Note over Admin, Controller: 인증된 관리자
    Admin->>Controller: 상품 수정 요청

    Controller->>+Facade: 상품 수정 위임 update(productId, request)
    Note over Facade: @Transactional

    Facade->>+ProductService: 수정 가능 검증 후 갱신 update(productId, name, price, stockQuantity, ...)

    ProductService->>+ProductRepository: 상품 조회 findById(productId)
    ProductRepository-->>-ProductService: Product or empty

    alt 상품 미존재·삭제됨·브랜드 변경 시도
        ProductService-->>Facade: 예외(NOT_FOUND / BAD_REQUEST)
    else 유효
        ProductService->>+ProductRepository: 상품 저장 save(product)
        ProductRepository-->>-ProductService: 저장된 Product
        ProductService-->>Facade: ProductInfo
    end

    deactivate ProductService

    Facade-->>-Controller: 상품 수정 결과(ProductInfo)
    Controller-->>Admin: 200 OK
```

### 해석

- **봐야 할 포인트**: Facade는 request(DTO)를 도메인 파라미터로 변환한 뒤 Service를 호출한다. 인증·인가는 §0에서 선행되므로 Controller·Facade·Service는 호출되지 않는다. 상품 수정 비즈니스 규칙(브랜드 변경 불가, 삭제된 상품 404)은 모두 Service에만 있다.
- **설계 의도**: 인증은 인터셉터, 인가는 컨트롤러에서 처리하고, Products 도메인은 “수정 가능 여부”만 판단하고 “누가 요청했는지”는 보지 않는다.

### 잠재 리스크

- **리스크**: 인터셉터와 도메인 레이어의 테스트가 분리되어 있어, 권한 실패 + 상품 수정 실패 조합 시나리오를 E2E에서만 검증하게 될 수 있다.
- **선택지**: (A) 어드민 E2E에서 권한 없음 → 401, 권한 있음 + 잘못된 상품 → 404를 각각 한 번씩 검증. (B) AdminAuthInterceptor 단위 테스트로 401 케이스를 고정하고, 도메인 E2E는 유효한 어드민 헤더만 사용.

---

## 3. Likes — 좋아요 등록 (Product ↔ Likes)

- Likes가 Products에 의존하는 경계(상품 존재·미삭제, 1인 1좋아요)와 호출 순서·트랜잭션 경계를 보이기 위함.
- 삭제된 상품에는 좋아요가 불가한지, 중복 좋아요 시 CONFLICT로 실패하는지, 한 트랜잭션 안에서 검증·생성·저장이 이루어지는지 검증하기 위함.

### 다이어그램

```mermaid
sequenceDiagram
    actor Customer
    participant Controller as LikeV1Controller
    participant Facade as LikeFacade
    participant LikeService
    participant ProductService
    participant LikeRepository

    Note over Customer, Controller: 인증된 고객
    Customer->>Controller: 좋아요 등록 요청

    Controller->>+Facade: 좋아요 등록 위임 addLike(userId, productId)
    Note over Facade: @Transactional
    Facade->>+LikeService: 상품 유효·중복 검사 후 좋아요 추가 addLike(userId, productId)

    LikeService->>+ProductService: 판매 중 상품 조회 findByIdAndNotDeleted(productId)
    ProductService-->>-LikeService: Product or empty
    alt 상품 미존재 또는 삭제됨
        LikeService-->>Facade: 예외(NOT_FOUND)
    else 상품 유효
        LikeService->>+LikeRepository: 기존 좋아요 존재 여부 조회 existsByUserIdAndProductId(userId, productId)
        LikeRepository-->>-LikeService: boolean
        alt 이미 좋아요 존재
            LikeService-->>Facade: 예외(CONFLICT)
        else 중복 아님
            LikeService->>+LikeRepository: 좋아요 저장 save(like)
            LikeRepository-->>-LikeService: 저장된 Like
            LikeService-->>Facade: LikeInfo
        end
    end
    deactivate LikeService
    Facade-->>-Controller: 좋아요 등록 결과(LikeInfo)
    Controller-->>Customer: 201 Created
```

### 해석

- **봐야 할 포인트**: 상품 검증 → 중복 검사 → Like 생성·저장이 한 트랜잭션으로 묶여 있어, 동시에 같은 사용자가 같은 상품에 좋아요를 두 번 요청해도 한 건만 성공하고 나머지는 CONFLICT로 처리할 수 있다(DB unique 제약과 함께).
- **설계 의도**: “삭제된 상품 좋아요 불가”, “1인 1좋아요”를 Service에서 순서대로 검증하고, Facade는 트랜잭션 경계만 담당한다.

### 잠재 리스크

- **리스크**: Likes가 ProductService에 직접 의존하므로, Products의 조회 스펙(findByIdAndNotDeleted)이 바뀌면 Likes 쪽이 영향을 받는다.
- **선택지**: (A) 현재처럼 ProductService 메서드로 조회(단순, 결합 명시적). (B) 이벤트/캐시로 상품 상태를 공유해 결합 완화(복잡도·일관성 부담 증가).

---

## 4. Cart — 장바구니 옵션 수정 (Cart ↔ Product)

- 장바구니 수정 시 Cart 도메인과 Product 도메인(판매 상태·재고·옵션)이 어떻게 협력하는지, 검증 순서와 트랜잭션 경계를 확인하기 위함.
- 판매 중지·삭제된 상품 → 재고/옵션 순으로 검증하는지, 실패 시 롤백이 되는지 검증하기 위함.

### 다이어그램

```mermaid
sequenceDiagram
    actor Customer
    participant Controller as CartV1Controller
    participant Facade as CartFacade
    participant CartService
    participant ProductService
    participant CartRepository

    Note over Customer, Controller: 인증된 고객
    Customer->>Controller: 장바구니 옵션 수정 요청

    Controller->>+Facade: 장바구니 항목 수정 위임 updateItem(userId, cartItemId, request)
    Note over Facade: @Transactional
    Facade->>+CartService: 소유·상품 검증 후 수량·옵션 갱신 updateItem(userId, cartItemId, quantity, optionId, ...)

    CartService->>+CartRepository: 장바구니 항목 조회 findByUserIdAndCartItemId(userId, cartItemId)
    CartRepository-->>-CartService: CartItem or empty
    alt 장바구니 항목 미존재
        CartService-->>Facade: 예외(NOT_FOUND)
    else 항목 존재
        CartService->>+ProductService: 판매·재고·옵션 검증 validateProductAvailability(productId, quantity, optionId)
        ProductService-->>-CartService: 검증 결과(유효 시 옵션 정보)
        alt 판매 불가/삭제/재고·옵션 불일치
            CartService-->>Facade: 예외(BAD_REQUEST / NOT_FOUND)
        else 유효
            CartService->>+CartRepository: 장바구니 항목 저장 save(cartItem)
            CartRepository-->>-CartService: 저장된 CartItem
            CartService-->>Facade: CartInfo
        end
    end
    deactivate CartService
    Facade-->>-Controller: 장바구니 수정 결과(CartInfo)
    Controller-->>Customer: 200 OK
```

### 해석

- **봐야 할 포인트**: Facade는 request(DTO)를 도메인 파라미터(수량, optionId 등)로 변환한 뒤 CartService를 호출한다. 장바구니 항목(CartItem) 소유 확인 후, ProductService의 `validateProductAvailability` 한 번 호출로 판매 상태·재고·옵션을 원자적으로 검증한다. 검증 순서(상태 → 재고/옵션)는 ProductService 내부에서 유지되며, 호출 1회로 트랜잭션 길이와 레이스 조건 가능성을 줄인다. 모든 변경은 Facade 트랜잭션 안에서만 커밋된다.
- **설계 의도**: 요구사항(추가·수정 시 판매 상태 및 재고 확인)을 단일 검증 메서드로 묶어 정합성과 성능을 함께 확보했다.

### 잠재 리스크

- **리스크**: ProductService의 validateProductAvailability 내부 스펙(검증 순서, 에러 타입 구분)이 바뀌면 Cart 도메인이 영향을 받는다.
- **선택지**: (A) ProductService에서 실패 원인별 구체적인 예외/에러 코드를 반환해 클라이언트 메시지 구분 가능하게 유지한다. (B) 단순히 BAD_REQUEST/NOT_FOUND만 반환하고 메시지는 공통 문구로 처리한다.

---

## 5. Orders — 주문 생성 (Order ↔ Product, 재고 확인/스냅샷)

- 주문 생성 시 여러 상품에 대한 검증·스냅샷·저장의 호출 순서와, 재고 차감은 결제 완료 시점이라는 정책이 시퀀스에 드러나도록 하기 위함.
- 삭제된 상품 주문 불가, 재고 확인 후 주문만 생성·재고는 나중에 차감하는 흐름, 트랜잭션 경계가 주문 저장까지임을 확인하기 위함.

### 다이어그램

```mermaid
sequenceDiagram
    actor Customer
    participant Controller as OrderV1Controller
    participant Facade as OrderFacade
    participant OrderService
    participant ProductService
    participant OrderRepository

    Note over Customer, Controller: 인증된 고객
    Customer->>Controller: 주문 생성 요청

    Controller->>+Facade: 주문 생성 위임 create(userId, request)
    Note over Facade: @Transactional
    Facade->>+OrderService: 주문 항목 검증·스냅샷 후 주문 생성 create(userId, orderItemRequestList)

    OrderService->>+ProductService: 주문 항목 일괄 검증(존재·재고) validateProducts(orderItemRequestList)
    ProductService-->>-OrderService: 검증 결과(유효 시 스냅샷 정보)
    alt 하나라도 미존재/삭제/재고 부족
        OrderService-->>Facade: 예외(NOT_FOUND / BAD_REQUEST)
    else 검증 통과
        OrderService->>+OrderRepository: 주문 저장 save(order)
        OrderRepository-->>-OrderService: 저장된 Order
        Note over OrderService: 재고 차감은 결제 완료 시점(비관적 락)
        OrderService-->>Facade: OrderInfo
    end
    deactivate OrderService
    Facade-->>-Controller: 주문 생성 결과(OrderInfo)
    Controller-->>Customer: 201 Created
```

### 해석

- **봐야 할 포인트**: Facade는 request(DTO)를 주문 항목 리스트(도메인/애플리케이션 계층 타입)로 변환한 뒤 OrderService를 호출한다. 주문 항목 전체를 `validateProducts(orderItemRequestList)` 한 번으로 검증하여 N번 조회를 피하고, 트랜잭션 길이를 줄인다. 주문 생성 트랜잭션에는 “재고 확인”만 들어가고 “재고 차감”은 없으며, Note대로 결제 완료 시점에 처리한다. 그 시점에 재고 부족이면 주문 실패/취소로 정합성을 맞추는 설계이다.
- **설계 의도**: 주문 생성과 결제를 분리해 두었고, 삭제된 상품·재고 부족은 Bulk 검증 단계에서 NOT_FOUND/BAD_REQUEST로 처리한다.

### 잠재 리스크

- **리스크**: 주문 생성 후 결제 전에 다른 주문으로 재고가 소진되면, 결제 완료 시 재고 부족으로 실패할 수 있다(결제·주문 보상 처리 필요).
- **선택지**: (A) 주문 생성 시 재고 예약(선점) 후 결제 완료 시 예약→차감, 미결제 타임아웃 시 예약 해제. (B) 현재처럼 주문은 재고 확인만 하고, 결제 완료 시 차감 실패하면 주문 실패/취소로 처리.

---

## 6. Orders — 주문 취소 (Order ↔ Product, 상태 검증)

- 주문 취소 시 본인/타인 구분(404 통일), 상태 검증, 결제 완료 건의 재고 복구가 한 트랜잭션으로 처리되는지 확인하기 위함.
- 타인 주문 접근 시 NOT_FOUND로 응답하는지, 재고 복구 실패 시 취소 전체가 롤백되는지 검증하기 위함.

### 다이어그램

```mermaid
sequenceDiagram
    actor Customer
    participant Controller as OrderV1Controller
    participant Facade as OrderFacade
    participant OrderService
    participant ProductService
    participant OrderRepository

    Note over Customer, Controller: 인증된 고객
    Customer->>Controller: 주문 취소 요청

    Controller->>+Facade: 주문 취소 위임 cancel(userId, orderId)
    Note over Facade: @Transactional
    Facade->>+OrderService: 본인·상태 검증 후 취소(재고 복구) cancel(userId, orderId)

    OrderService->>+OrderRepository: 주문 조회 findById(orderId)
    OrderRepository-->>-OrderService: Order or empty
    alt 주문 미존재 또는 타인 주문(404 통일)
        OrderService-->>Facade: 예외(NOT_FOUND)
    else 본인 주문
        alt 취소 불가 상태(배송 중 등)
            OrderService-->>Facade: 예외(BAD_REQUEST)
        else 취소 가능
            opt 결제 완료된 주문
                OrderService->>+ProductService: 재고 복구 restoreStock(orderItems)
                ProductService-->>-OrderService: 복구 완료(비관적 락)
            end
            OrderService->>+OrderRepository: 주문 상태 저장 save(order)
            OrderRepository-->>-OrderService: 저장된 Order
            OrderService-->>Facade: OrderInfo
        end
    end
    deactivate OrderService
    Facade-->>-Controller: 주문 취소 결과(OrderInfo)
    Controller-->>Customer: 200 OK
```

### 해석

- **봐야 할 포인트**: 주문 조회/취소/수정 시, “주문 없음”과 “타인 주문”은 서비스 레이어에서 구분하지 않고 동일하게 NOT_FOUND로 반환하여 요구사항 4.3(존재 여부 비노출)을 준수한다. 결제 완료 주문은 재고 복구 후 상태를 취소로 바꾸며, 재고 복구 실패 시 예외로 트랜잭션이 롤백되어 취소가 반영되지 않는다. 구현 시 ProductService에서 상품/옵션 삭제 등으로 복구가 불가한 경우를 최소화하는 방어 로직(예: Soft Delete만 사용, 삭제된 상품은 복구 스킵 등)을 두고, 그래도 실패하는 예외 케이스는 로그 및 운영 대응 정책으로 정의한다.
- **설계 의도**: 본인/상태/재고 복구를 Service에서 순서대로 검증하고, Facade는 트랜잭션 경계만 담당한다. 재고 복구 실패 시 전체 롤백으로 주문·재고 정합성을 유지한다.

### 잠재 리스크

- **리스크**: 재고 복구 중 상품이 삭제되었거나 옵션이 없어지면 복구 실패가 나고, 사용자 입장에서는 “취소가 안 된다”는 인지가 필요하다(에러 메시지 설계 필요).
- **선택지**: (A) 재고 복구 실패 시에도 주문 상태만 CANCELLED로 두고, 재고는 수동/배치로 보정. (B) 현재처럼 재고 복구 실패 시 전체 롤백해 “취소 실패”로 응답하고, ProductService에 재고 복구 실패 케이스(완전 삭제 등)를 최소화하는 방어 로직을 두어 롤백이 발생하는 상황을 줄인다.
