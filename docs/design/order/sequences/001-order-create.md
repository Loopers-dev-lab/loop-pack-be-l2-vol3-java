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
        OF->>PS: 상품 조회
        activate PS
        PS-->>OF: List~Product~
        deactivate PS

        opt 쿠폰 적용 시
            OF->>ICS: 사용 가능한 쿠폰 조회
            activate ICS
            ICS-->>OF: IssuedCoupon
            deactivate ICS
        end

        Note over OF: 재고 검증, 할인 계산

        OF->>PS: 재고 차감
        activate PS
        PS-->>OF: void
        deactivate PS

        opt 쿠폰 적용 시
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
- 검증 → 계산 → 상태 변경 → 주문 생성 순서로 진행한다
- 재고 차감, 쿠폰 사용 처리, 주문 생성은 하나의 트랜잭션에서 원자적으로 처리한다
- 쿠폰은 선택 사항 — couponId가 없으면 쿠폰 관련 단계를 건너뛴다
- IssuedCoupon이 발급 시점의 Coupon 데이터를 스냅샷하므로, 주문 시 CouponService 조회가 불필요하다
