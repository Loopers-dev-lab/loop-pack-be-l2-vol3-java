# 재고 해제 시퀀스다이어그램

## 개요
결제 실패 시 점유 해제, 주문 취소 시 확정 복원을 수행하는 보상 트랜잭션 흐름을 정의한다.

## 시퀀스 (결제 실패 — 점유 해제)

```mermaid
sequenceDiagram
    participant PF as PaymentFacade
    participant PS as PaymentService
    participant SS as StockService
    participant ICS as IssuedCouponService
    participant OS as OrderService

    Note over PF: PG 콜백 FAILED 수신 또는<br/>PG 요청 실패

    critical @Transactional (보상)
        PF->>PS: 결제 실패 처리 (FAILED)
        activate PS
        PS-->>PF: void
        deactivate PS

        PF->>SS: 점유 해제 (releaseReserved)
        activate SS
        SS-->>PF: void
        deactivate SS

        opt 쿠폰 적용 주문인 경우
            PF->>ICS: 쿠폰 복원
            activate ICS
            ICS-->>PF: void
            deactivate ICS
        end

        PF->>OS: 주문 취소 (CANCELED)
        activate OS
        OS-->>PF: void
        deactivate OS
    end
```

## 시퀀스 (주문 취소 — 확정 복원)

```mermaid
sequenceDiagram
    actor 사용자
    participant OC as OrderController
    participant OF as OrderFacade
    participant OS as OrderService
    participant PF as PaymentFacade
    participant PS as PaymentService
    participant PG as PgClient
    participant SS as StockService
    participant ICS as IssuedCouponService

    사용자->>OC: POST /api/v1/orders/{id}/cancel
    activate OC
    OC->>OF: 주문 취소
    activate OF

    OF->>OS: 주문 조회
    activate OS
    OS-->>OF: Order
    deactivate OS

    Note over OF: 소유권 확인, 주문 상태(PAID) 확인

    OF->>PF: 결제 취소
    activate PF

    PF->>PS: 결제 조회 (SUCCEEDED)
    activate PS
    PS-->>PF: Payment
    deactivate PS

    PF->>PG: PG 결제 취소 요청 (CB)
    activate PG
    PG-->>PF: 취소 성공
    deactivate PG

    critical @Transactional
        PF->>PS: 결제 취소 처리 (CANCELED)
        activate PS
        PS-->>PF: void
        deactivate PS
    end

    PF-->>OF: void
    deactivate PF

    critical @Transactional
        OF->>SS: 확정 복원 (releaseConfirmed)
        activate SS
        SS-->>OF: void
        deactivate SS

        opt 쿠폰 적용 주문인 경우
            OF->>ICS: 쿠폰 복원
            activate ICS
            ICS-->>OF: void
            deactivate ICS
        end

        OF->>OS: 주문 취소 (CANCELED)
        activate OS
        OS-->>OF: void
        deactivate OS
    end

    OF-->>OC: void
    deactivate OF
    OC-->>사용자: 200 OK
    deactivate OC
```

## 핵심 포인트
- 결제 실패 보상: 점유 해제(releaseReserved) — 아직 확정 전이므로 reservedQuantity만 감소
- 주문 취소 보상: 확정 복원(releaseConfirmed) — 이미 확정되었으므로 confirmedQuantity를 감소
- 쿠폰 복원은 쿠폰이 적용된 주문에서만 수행 (issuedCouponId != null)
- 보상 트랜잭션은 인프로세스로 즉시 실행하며, 실패 시 보정 스케줄러(ST-04)가 후속 처리
- 주문 취소 시 PG 취소는 트랜잭션 밖에서, 재고/쿠폰/주문 복원은 트랜잭션 안에서 처리
