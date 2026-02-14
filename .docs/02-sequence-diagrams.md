# 시퀀스 다이어그램

> 조건 분기, 도메인 간 협력, 예외 흐름이 존재하는 시나리오만 다이어그램으로 표현한다.
> 단순 CRUD(등록/수정/단건 조회/목록 조회)는 흐름이 자명하므로 생략한다.

---

## 좋아요 등록

> 시나리오 2.2 — 고객이 마음에 드는 상품에 좋아요를 누른다.

> 취소도 같은 흐름이라고 생각하면 된다.

```mermaid
sequenceDiagram
    actor 고객
    participant LikeV1Controller
    participant LikeFacade
    participant ProductService
    participant LikeService
    participant Like
    participant LikeRepository

    Note right of 고객: 인증된 고객

    고객->>LikeV1Controller: 좋아요 등록 요청
    LikeV1Controller->>LikeFacade: 좋아요 등록

    LikeFacade->>ProductService: 상품 조회
    alt 상품이 존재하지 않거나 삭제됨
        ProductService-->>고객: 실패
    end

    LikeFacade->>LikeService: 좋아요 등록
    LikeService->>LikeRepository: 중복 좋아요 확인
    alt 이미 좋아요한 상품
        LikeService-->>고객: 실패
    end

    LikeService->>Like: 좋아요 생성
    LikeService->>LikeRepository: 좋아요 저장

    LikeService-->>LikeFacade: 결과 반환
    LikeFacade-->>LikeV1Controller: 결과 반환
    LikeV1Controller-->>고객: 성공
```

---

## 장바구니 담기

> 시나리오 2.3 — 고객이 마음에 드는 상품을 장바구니에 담는다. 이미 담긴 상품이면 수량이 누적된다.

**다이어그램이 필요한 이유**
- 조건 분기: 상품 유효성 검증 + 중복 여부에 따른 수량 누적/신규 생성
- 도메인 간 협력: Cart가 Product의 상태를 확인해야 한다

```mermaid
sequenceDiagram
    actor 고객
    participant CartV1Controller
    participant CartFacade
    participant ProductService
    participant CartService
    participant CartItem
    participant CartRepository

    Note right of 고객: 인증된 고객

    고객->>CartV1Controller: 장바구니 담기 요청
    CartV1Controller->>CartFacade: 장바구니 담기

    CartFacade->>ProductService: 상품 조회
    alt 상품이 존재하지 않거나 삭제됨
        ProductService-->>고객: 실패
    end

    CartFacade->>CartService: 장바구니에 상품 담기
    CartService->>CartRepository: 기존 장바구니 항목 조회
    alt 이미 담긴 상품
        CartService->>CartItem: 수량 합산
    else 새로운 상품
        CartService->>CartItem: 항목 생성
    end
    CartService->>CartRepository: 저장

    CartService-->>CartFacade: 결과 반환
    CartFacade-->>CartV1Controller: 결과 반환
    CartV1Controller-->>고객: 성공
```

---

## 장바구니에서 주문하기

> 시나리오 2.3 / 2.4 — 고객이 장바구니의 모든 항목을 한 번에 주문한다.

**다이어그램이 필요한 이유**
- 도메인 간 협력: Cart → Product → Order 세 도메인이 협력
- 조건 분기: 장바구니 비어있음, 상품 유효성, 재고 부족
- 주문 성공 후 장바구니 비우기까지 하나의 트랜잭션

```mermaid
sequenceDiagram
    actor 고객
    participant OrderV1Controller
    participant OrderFacade
    participant CartService
    participant ProductService
    participant Product
    participant OrderService
    participant Order

    Note right of 고객: 인증된 고객

    고객->>OrderV1Controller: 장바구니 주문 요청
    OrderV1Controller->>OrderFacade: 장바구니 주문

    OrderFacade->>CartService: 장바구니 조회
    alt 장바구니가 비어있음
        CartService-->>고객: 실패
    end

    OrderFacade->>ProductService: 상품 유효성 확인
    alt 판매 불가 상품 존재
        ProductService-->>고객: 실패
    end

    OrderFacade->>ProductService: 재고 확인 및 차감
    ProductService->>Product: 재고 차감
    alt 재고 부족
        ProductService-->>고객: 실패
    end

    OrderFacade->>OrderService: 주문 생성 (스냅샷 포함)
    OrderService->>Order: 주문 생성

    OrderFacade->>CartService: 장바구니 비우기

    OrderFacade-->>OrderV1Controller: 결과 반환
    OrderV1Controller-->>고객: 성공
```

---

## 브랜드 삭제 (연쇄 삭제)

> 시나리오 2.5 — 어드민이 브랜드를 삭제한다. 이때 해당 브랜드의 모든 상품도 함께 삭제된다.

**다이어그램이 필요한 이유**
- 도메인 간 협력: Brand 삭제가 Product 연쇄 삭제를 트리거한다
- 삭제 순서: 상품을 먼저 삭제한 뒤 브랜드를 삭제해야 정합성이 유지된다

```mermaid
sequenceDiagram
    actor 어드민
    participant AdminBrandV1Controller
    participant BrandFacade
    participant BrandService
    participant ProductService
    participant Brand
    participant Product

    Note right of 어드민: 인증된 어드민

    어드민->>AdminBrandV1Controller: 브랜드 삭제 요청
    AdminBrandV1Controller->>BrandFacade: 브랜드 삭제

    BrandFacade->>BrandService: 브랜드 조회
    alt 브랜드가 존재하지 않거나 삭제됨
        BrandService-->>어드민: 실패
    end

    BrandFacade->>ProductService: 해당 브랜드의 상품 전체 삭제
    ProductService->>Product: 논리 삭제 (soft delete)

    BrandFacade->>BrandService: 브랜드 삭제
    BrandService->>Brand: 논리 삭제 (soft delete)

    BrandFacade-->>AdminBrandV1Controller: 결과 반환
    AdminBrandV1Controller-->>어드민: 성공
```

---

### 주문하기

> 시나리오 2.4 - 고객은 여러 상품을 한 번에 주문한다. 주문 후 자신의 주문 내역을 조회할 수 있다.

**다이어그램이 필요한 이유**
- 조건 분기: 상품 유효성 검증, 재고 부족 검증
- 도메인 간 협력: 주문이 상품의 상태/재고를 확인해야 한다
- 도메인 책임: 가격 정보 제공은 Product, 금액 계산은 Order의 책임

```mermaid
sequenceDiagram
    actor 고객
    participant OrderV1Controller
    participant OrderFacade
    participant ProductService
    participant Product
    participant OrderService
    participant Order

    Note right of 고객: 인증된 고객

    고객->>OrderV1Controller: 주문 요청
    OrderV1Controller->>OrderFacade: 주문 요청

    OrderFacade->>ProductService: 상품 유효성 확인
    alt 판매 불가 상품 존재
        ProductService-->>고객: 실패
    end

    OrderFacade->>ProductService: 재고 확인 및 차감
    ProductService->>Product: 재고 차감
    alt 재고 부족
        ProductService-->>고객: 실패
    end

    OrderFacade->>OrderService: 주문 생성
    OrderService->>Order: 주문 생성 (스냅샷 포함)

    OrderService-->>OrderFacade: 결과 반환
    OrderFacade-->>OrderV1Controller: 결과 반환
    OrderV1Controller-->>고객: 성공
```