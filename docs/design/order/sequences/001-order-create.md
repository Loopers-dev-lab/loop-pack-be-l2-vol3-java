# 주문 요청 시퀀스다이어그램

## 개요
사용자가 상품을 주문할 때 재고 차감, 쿠폰 적용, 주문 생성을 트랜잭션으로 처리하는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant OC as OrderController
    participant OF as OrderFacade
    participant ICS as IssuedCouponService
    participant PS as ProductService
    participant OS as OrderService

    사용자->>OC: POST /api/v1/orders
    activate OC
    OC->>OF: 주문 요청
    activate OF

    critical @Transactional
        opt 쿠폰 적용 시
            OF->>ICS: 발급 쿠폰 조회
            activate ICS
            ICS-->>OF: IssuedCoupon
            deactivate ICS

            Note over OF: 소유 검증 (Facade)<br/>사용 가능 검증 (IssuedCoupon.validateUsable)
        end

        OF->>PS: 상품 조회 + 재고 차감 (락)
        activate PS
        PS-->>OF: List~Product~
        deactivate PS

        opt 쿠폰 적용 시
            Note over OF: 최소 주문 금액 검증 (IssuedCoupon.validateMinOrderAmount)<br/>할인 금액 계산 (IssuedCoupon.calculateDiscount)
            OF->>ICS: 쿠폰 사용 처리
            activate ICS
            ICS-->>OF: void
            deactivate ICS
        end

        OF->>OS: 주문 생성
        activate OS
        OS-->>OF: Order
        deactivate OS
    end

    OF-->>OC: OrderInfo
    deactivate OF
    OC-->>사용자: 200 OK
    deactivate OC
```

## 핵심 포인트
- 재고 차감, 쿠폰 사용 처리, 주문 생성은 하나의 트랜잭션에서 원자적으로 처리한다
- 쿠폰은 선택 사항 — couponId가 없으면 쿠폰 관련 단계를 건너뛴다
- IssuedCoupon이 발급 시점의 Coupon 데이터를 스냅샷하므로, 주문 시 CouponService 조회가 불필요하다
- 사용 가능 검증(`validateUsable`), 최소 주문 금액 검증(`validateMinOrderAmount`), 할인 계산(`calculateDiscount`)은 IssuedCoupon 엔티티에 위임한다
- 상품 재고 차감은 비관적 락 + ID 정렬로 데드락을 방지한다
- 최소 주문 금액 검증은 상품 조회 후 totalAmount가 확정된 시점에 수행한다
