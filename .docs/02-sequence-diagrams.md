# 시퀀스 다이어그램

> 조건 분기, 도메인 간 협력, 예외 흐름이 존재하는 시나리오만 다이어그램으로 표현한다.
> 단순 CRUD(등록/수정/단건 조회/목록 조회)는 흐름이 자명하므로 생략한다.

---

## 좋아요 등록

> 시나리오 2.2 — 고객이 마음에 드는 상품에 좋아요를 누른다.

> 취소도 같은 흐름이라고 생각하면 된다. (incrementLikeCount → decrementLikeCount)

```mermaid
sequenceDiagram
    actor 고객
    participant LikeV1Controller
    participant LikeApplicationService
    participant LikeDomainService
    participant Like
    participant LikeRepository
    participant ProductDomainService

    Note right of 고객: 인증된 고객

    고객->>+LikeV1Controller: 좋아요 등록 요청
    LikeV1Controller->>+LikeApplicationService: 좋아요 등록

    LikeApplicationService->>+LikeDomainService: 좋아요 등록
    LikeDomainService->>+LikeRepository: 중복 좋아요 확인
    LikeRepository-->>-LikeDomainService: 결과
    alt 이미 좋아요한 상품
        LikeDomainService-->>고객: 실패
    end

    LikeDomainService->>+Like: 좋아요 생성
    Like-->>-LikeDomainService: 좋아요
    LikeDomainService->>+LikeRepository: 좋아요 저장
    LikeRepository-->>-LikeDomainService: 완료

    LikeDomainService-->>-LikeApplicationService: 결과 반환

    LikeApplicationService->>+ProductDomainService: 좋아요 수 증가 (@Modifying 벌크 UPDATE)
    Note right of ProductDomainService: 엔티티 락 불필요. JPQL UPDATE로 직접 증감
    ProductDomainService-->>-LikeApplicationService: 완료

    LikeApplicationService-->>-LikeV1Controller: 결과 반환
    LikeV1Controller-->>-고객: 성공
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
    participant CartApplicationService
    participant ProductDomainService
    participant CartDomainService
    participant CartItem
    participant CartRepository

    Note right of 고객: 인증된 고객

    고객->>+CartV1Controller: 장바구니 담기 요청
    CartV1Controller->>+CartApplicationService: 장바구니 담기

    CartApplicationService->>+ProductDomainService: 상품 조회
    alt 상품이 존재하지 않거나 삭제됨
        ProductDomainService-->>고객: 실패
    end
    ProductDomainService-->>-CartApplicationService: 상품

    CartApplicationService->>+CartDomainService: 장바구니에 상품 담기
    CartDomainService->>+CartRepository: 기존 장바구니 항목 조회
    CartRepository-->>-CartDomainService: 항목
    alt 이미 담긴 상품
        CartDomainService->>+CartItem: 수량 합산
        CartItem-->>-CartDomainService: 완료
    else 새로운 상품
        CartDomainService->>+CartItem: 항목 생성
        CartItem-->>-CartDomainService: 항목
    end
    CartDomainService->>+CartRepository: 저장
    CartRepository-->>-CartDomainService: 완료

    CartDomainService-->>-CartApplicationService: 결과 반환
    CartApplicationService-->>-CartV1Controller: 결과 반환
    CartV1Controller-->>-고객: 성공
```

---

## 쿠폰 발급

> 시나리오 2.8 — 고객이 쿠폰 발급을 요청한다.

**다이어그램이 필요한 이유**
- 조건 분기: 쿠폰 유효성 검증(만료, 삭제), 중복 발급 검증
- 도메인 간 협력: CouponIssue가 Coupon의 상태를 확인해야 한다

```mermaid
sequenceDiagram
    actor 고객
    participant CouponV1Controller
    participant CouponApplicationService
    participant CouponDomainService
    participant CouponIssueDomainService
    participant CouponIssue

    Note right of 고객: 인증된 고객

    고객->>+CouponV1Controller: 쿠폰 발급 요청
    CouponV1Controller->>+CouponApplicationService: 쿠폰 발급

    CouponApplicationService->>+CouponDomainService: 쿠폰 조회
    alt 쿠폰이 존재하지 않거나 삭제됨
        CouponDomainService-->>고객: 실패
    end
    CouponDomainService-->>-CouponApplicationService: 쿠폰

    CouponApplicationService->>+CouponIssueDomainService: 쿠폰 발급
    CouponIssueDomainService->>CouponIssueDomainService: 중복 발급 확인
    alt 이미 발급된 쿠폰
        CouponIssueDomainService-->>고객: 실패
    end
    CouponIssueDomainService->>CouponIssueDomainService: 만료 확인
    alt 만료된 쿠폰
        CouponIssueDomainService-->>고객: 실패
    end
    CouponIssueDomainService->>+CouponIssue: 발급 생성
    CouponIssue-->>-CouponIssueDomainService: 쿠폰 발급

    CouponIssueDomainService-->>-CouponApplicationService: 결과 반환
    CouponApplicationService-->>-CouponV1Controller: 결과 반환
    CouponV1Controller-->>-고객: 성공
```

---

## 주문하기 (쿠폰 포함)

> 시나리오 2.4 - 고객은 여러 상품을 한 번에 주문한다. 쿠폰을 적용하여 할인을 받을 수 있다.

**다이어그램이 필요한 이유**
- 조건 분기: 상품 유효성 검증, 재고 부족 검증, 중복 상품 검증, 쿠폰 검증
- 도메인 간 협력: 주문이 상품, 브랜드, 쿠폰의 상태를 확인해야 한다
- 도메인 책임: 재고 차감은 Product, 쿠폰 할인 계산은 Coupon, 중복 검증과 금액 계산은 OrderDomainService의 책임

```mermaid
sequenceDiagram
    actor 고객
    participant OrderV1Controller
    participant OrderApplicationService
    participant CouponIssueDomainService
    participant CouponDomainService
    participant Coupon
    participant ProductDomainService
    participant Product
    participant BrandDomainService
    participant OrderDomainService
    participant Order

    Note right of 고객: 인증된 고객

    고객->>+OrderV1Controller: 주문 요청 (쿠폰 포함)
    OrderV1Controller->>+OrderApplicationService: 주문 요청

    opt 쿠폰 적용 시
        OrderApplicationService->>+CouponIssueDomainService: 쿠폰 발급 조회 (본인 확인)
        alt 쿠폰이 존재하지 않거나 타인 소유
            CouponIssueDomainService-->>고객: 실패
        end
        CouponIssueDomainService-->>-OrderApplicationService: 쿠폰 발급

        OrderApplicationService->>+CouponDomainService: 쿠폰 템플릿 조회
        CouponDomainService-->>-OrderApplicationService: 쿠폰

        OrderApplicationService->>+Coupon: 적용 가능 여부 검증 (만료, 최소 주문 금액)
        alt 적용 불가
            Coupon-->>고객: 실패
        end
        Coupon-->>-OrderApplicationService: 검증 완료
    end

    loop 각 주문 항목 (productId 순으로 정렬)
        OrderApplicationService->>+ProductStockDomainService: 재고 차감 (비관적 락 on product_stocks)
        alt 재고 부족
            ProductStockDomainService-->>고객: 실패
        end
        ProductStockDomainService-->>-OrderApplicationService: 완료
    end

    OrderApplicationService->>+ProductDomainService: 상품 일괄 조회 (락 없이)
    alt 삭제된 상품 포함
        ProductDomainService-->>고객: 실패
    end
    ProductDomainService-->>-OrderApplicationService: 상품 목록

    OrderApplicationService->>+BrandDomainService: 브랜드 정보 조회 (스냅샷용)
    BrandDomainService-->>-OrderApplicationService: 브랜드 목록

    opt 쿠폰 적용 시
        OrderApplicationService->>+Coupon: 할인 금액 계산
        Coupon-->>-OrderApplicationService: 할인 금액
        OrderApplicationService->>+CouponIssueDomainService: 쿠폰 사용 처리
        CouponIssueDomainService-->>-OrderApplicationService: 완료
    end

    OrderApplicationService->>+OrderDomainService: 주문 생성 (스냅샷 + 할인 데이터 전달)
    OrderDomainService->>OrderDomainService: 중복 상품 검증
    alt 중복 상품 존재
        OrderDomainService-->>고객: 실패
    end
    OrderDomainService->>OrderDomainService: 총 금액 계산
    OrderDomainService->>+Order: 주문 및 주문 항목 생성
    Order-->>-OrderDomainService: 주문

    OrderDomainService-->>-OrderApplicationService: 결과 반환
    OrderApplicationService-->>-OrderV1Controller: 결과 반환
    OrderV1Controller-->>-고객: 성공
```

---

## 장바구니에서 주문하기

> 시나리오 2.3 / 2.4 — 고객이 장바구니의 모든 항목을 한 번에 주문한다.

**다이어그램이 필요한 이유**
- 도메인 간 협력: Cart → Product → Brand → Order 네 도메인이 협력
- 조건 분기: 장바구니 비어있음, 상품 유효성, 재고 부족
- 주문 성공 후 장바구니 비우기까지 하나의 트랜잭션

```mermaid
sequenceDiagram
    actor 고객
    participant OrderV1Controller
    participant OrderApplicationService
    participant CartDomainService
    participant ProductDomainService
    participant ProductStockDomainService
    participant BrandDomainService
    participant OrderDomainService
    participant Order

    Note right of 고객: 인증된 고객

    고객->>+OrderV1Controller: 장바구니 주문 요청
    OrderV1Controller->>+OrderApplicationService: 장바구니 주문

    OrderApplicationService->>+CartDomainService: 장바구니 조회
    CartDomainService-->>-OrderApplicationService: 장바구니
    alt 장바구니가 비어있음
        OrderApplicationService-->>고객: 실패
    end

    OrderApplicationService->>+ProductDomainService: 장바구니 상품 일괄 조회
    ProductDomainService-->>-OrderApplicationService: 유효한 상품 목록

    alt 유효하지 않은 상품이 포함됨
        OrderApplicationService->>+CartDomainService: 유효하지 않은 상품 제거
        CartDomainService-->>-OrderApplicationService: 완료
        alt 유효한 상품이 하나도 없음
            OrderApplicationService-->>고객: 실패
        end
    end

    loop 각 장바구니 항목 (productId 순으로 정렬)
        OrderApplicationService->>+ProductStockDomainService: 재고 차감 (비관적 락 on product_stocks)
        alt 재고 부족
            ProductStockDomainService-->>고객: 실패
        end
        ProductStockDomainService-->>-OrderApplicationService: 완료
    end

    OrderApplicationService->>+ProductDomainService: 상품 일괄 조회 (락 없이)
    alt 삭제된 상품 포함
        ProductDomainService-->>고객: 실패
    end
    ProductDomainService-->>-OrderApplicationService: 상품 목록

    OrderApplicationService->>+BrandDomainService: 브랜드 정보 조회 (스냅샷용)
    BrandDomainService-->>-OrderApplicationService: 브랜드 목록

    OrderApplicationService->>+OrderDomainService: 주문 생성 (스냅샷 데이터 전달)
    OrderDomainService->>OrderDomainService: 중복 상품 검증
    OrderDomainService->>OrderDomainService: 총 금액 계산
    OrderDomainService->>+Order: 주문 및 주문 항목 생성
    Order-->>-OrderDomainService: 주문

    OrderDomainService-->>-OrderApplicationService: 결과 반환

    OrderApplicationService->>+CartDomainService: 장바구니 비우기
    CartDomainService-->>-OrderApplicationService: 완료

    OrderApplicationService-->>-OrderV1Controller: 결과 반환
    OrderV1Controller-->>-고객: 성공
```

---

## 브랜드 삭제 (연쇄 삭제)

> 시나리오 2.5 — 어드민이 브랜드를 삭제한다. 이때 해당 브랜드의 모든 상품도 함께 삭제된다.

**다이어그램이 필요한 이유**
- 도메인 간 협력: Brand 삭제가 Product, ProductStock 연쇄 삭제를 트리거한다
- 삭제 순서: 비관적 락으로 브랜드를 먼저 삭제한 뒤, 재고 → 상품 순으로 삭제한다
- 동시성: 상품 등록(registerWithStock)도 Brand 비관적 락을 사용하여, 삭제와 등록이 직렬화된다

```mermaid
sequenceDiagram
    actor 어드민
    participant AdminBrandV1Controller
    participant BrandApplicationService
    participant BrandDomainService
    participant ProductStockDomainService
    participant ProductDomainService
    participant Brand

    Note right of 어드민: 인증된 어드민

    어드민->>+AdminBrandV1Controller: 브랜드 삭제 요청
    AdminBrandV1Controller->>+BrandApplicationService: 브랜드 삭제

    BrandApplicationService->>+BrandDomainService: 브랜드 삭제 (비관적 락)
    BrandDomainService->>BrandDomainService: 브랜드 조회 (SELECT FOR UPDATE)
    alt 브랜드가 존재하지 않거나 삭제됨
        BrandDomainService-->>어드민: 실패
    end
    BrandDomainService->>+Brand: 논리 삭제 (soft delete)
    Brand-->>-BrandDomainService: 완료
    BrandDomainService-->>-BrandApplicationService: 완료

    BrandApplicationService->>+ProductStockDomainService: 해당 브랜드의 재고 전체 삭제
    Note right of ProductStockDomainService: brandId 기준 벌크 soft delete
    ProductStockDomainService-->>-BrandApplicationService: 완료

    BrandApplicationService->>+ProductDomainService: 해당 브랜드의 상품 전체 삭제
    Note right of ProductDomainService: brandId 기준 벌크 soft delete
    ProductDomainService-->>-BrandApplicationService: 완료

    BrandApplicationService-->>-AdminBrandV1Controller: 결과 반환
    AdminBrandV1Controller-->>-어드민: 성공
```

---

## 주문 취소 (쿠폰 복원)

> 시나리오 2.4 — 고객이 주문을 취소한다. 쿠폰이 적용된 주문이면 쿠폰을 복원한다.

**다이어그램이 필요한 이유**
- 조건 분기: 주문 상태에 따른 취소 가능 여부 검증
- 도메인 간 협력: 주문 취소 시 재고 복원 + 쿠폰 복원이 필요할 수 있다
- 도메인 로직: ORDERED 상태에서만 CANCELLED로 전이 가능

```mermaid
sequenceDiagram
    actor 고객
    participant OrderV1Controller
    participant OrderApplicationService
    participant OrderDomainService
    participant Order
    participant ProductStockDomainService
    participant CouponIssueDomainService

    Note right of 고객: 인증된 고객

    고객->>+OrderV1Controller: 주문 취소 요청
    OrderV1Controller->>+OrderApplicationService: 주문 취소

    OrderApplicationService->>+OrderDomainService: 주문 조회 (본인 확인)
    alt 주문이 존재하지 않거나 본인의 주문이 아님
        OrderDomainService-->>고객: 실패
    end
    OrderDomainService-->>-OrderApplicationService: 주문

    OrderApplicationService->>+Order: cancel()
    alt ORDERED 상태가 아님
        Order-->>고객: 실패
    end
    Order-->>-OrderApplicationService: 완료

    loop 각 주문 항목 (productId 순으로 정렬, 데드락 방지)
        OrderApplicationService->>+ProductStockDomainService: 재고 복원 (비관적 락 on product_stocks)
        ProductStockDomainService-->>-OrderApplicationService: 완료
    end

    opt 쿠폰이 적용된 주문
        OrderApplicationService->>+CouponIssueDomainService: 쿠폰 복원
        CouponIssueDomainService-->>-OrderApplicationService: 완료
    end

    OrderApplicationService-->>-OrderV1Controller: 결과 반환
    OrderV1Controller-->>-고객: 성공
```
