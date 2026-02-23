# 주문 생성 시퀀스다이어그램

## 개요
사용자가 여러 상품을 주문하면 재고를 차감하고 주문 시점 상품 정보를 스냅샷으로 저장하는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant OC as OrderController
    participant OF as OrderFacade
    participant PS as ProductService
    participant OS as OrderService

    사용자->>OC: POST /api/v1/orders
    activate OC
    OC->>OF: 주문 생성
    activate OF

    critical @Transactional
        OF->>PS: 주문 상품 조회
        activate PS
        PS-->>OF: 상품 목록
        deactivate PS

        OF->>PS: 재고 차감
        activate PS

        alt 재고 부족
            PS-->>OF: 재고 부족 예외
            OF-->>OC: 에러
            OC-->>사용자: 400 "재고가 부족한 상품이 있습니다"
        end

        PS-->>OF: 차감 완료
        deactivate PS

        OF->>OS: 주문 + 스냅샷 저장
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

- 재고 차감과 주문 생성은 같은 트랜잭션에서 처리한다
- 주문 상품 중 하나라도 재고가 부족하면 전체 주문이 실패한다 (부분 성공 없음)
- 주문 시점의 상품명, 가격, 브랜드명을 OrderItem에 스냅샷으로 저장한다
- Facade가 ProductService와 OrderService를 오케스트레이션한다
