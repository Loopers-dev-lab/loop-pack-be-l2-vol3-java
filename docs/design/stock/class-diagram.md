# Stock 클래스다이어그램

## 개요
상품별 재고를 점유/확정/해제 생명주기로 관리하는 객체 구조를 정의한다.

## 클래스다이어그램

```mermaid
classDiagram
    class Stock {
        -Long productId
        -int quantity
        -int reservedQuantity
        -int confirmedQuantity
        +create(productId, quantity)$ Stock
        +reserve(amount)
        +confirm(amount)
        +releaseReserved(amount)
        +releaseConfirmed(amount)
        +getAvailableQuantity() int
    }

    Stock ..> Product : productId 참조
```

## 설계 결정

- Stock은 Product와 별도 엔티티로 분리한다 (도메인 독립)
- Stock은 BaseEntity를 상속한다 (createdAt, updatedAt 필요 — 수량 변경 추적)
- `quantity`: 총 재고 수량 (변하지 않음, 입고/감모 등 별도 관리 시 변경)
- `reservedQuantity`: 현재 점유 중인 수량
- `confirmedQuantity`: 확정된 차감 수량
- `getAvailableQuantity()`: quantity - reservedQuantity - confirmedQuantity
- `reserve(amount)`: 가용 재고 확인 후 reservedQuantity 증가 (불변식: 가용 재고 >= amount)
- `confirm(amount)`: reservedQuantity 감소 + confirmedQuantity 증가
- `releaseReserved(amount)`: reservedQuantity 감소 (결제 실패 시 점유 해제)
- `releaseConfirmed(amount)`: confirmedQuantity 감소 (주문 취소 시 확정 복원)
- 동시성 제어: reserve 시 비관적 락(SELECT FOR UPDATE)을 사용한다
