# 결제 상태 수동 확인 시퀀스다이어그램

## 개요
콜백이 수신되지 않은 결제에 대해 사용자가 수동으로 PG에 상태를 확인하여 결제를 확정하고, 실패 시 보상 트랜잭션을 수행하는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant PC as PaymentController
    participant PF as PaymentFacade
    participant PS as PaymentService
    participant PP as PaymentProcessor
    participant GE as PaymentGatewayExecutor
    participant GW as PaymentGateway
    participant SS as StockService
    participant ICS as IssuedCouponService
    participant OS as OrderService

    사용자->>PC: POST /api/v1/payments/{id}/verify
    activate PC
    PC->>PF: 결제 상태 확인
    activate PF

    PF->>PS: 결제 조회
    activate PS
    PS-->>PF: Payment
    deactivate PS

    Note over PF: 소유권 확인 (validateOwnership)<br/>확정 여부 확인 (isFinalized)

    PF->>GE: query(payment)
    activate GE
    GE->>GW: query(paymentKey) [CB: pg-query, Retry]
    activate GW
    GW-->>GE: PaymentQueryResult
    deactivate GW
    GE-->>PF: PaymentQueryResult
    deactivate GE

    alt PG 결과: found && done (결제 완료)
        PF->>PS: 결제 성공 처리 (SUCCEEDED)
        activate PS
        PS-->>PF: void
        deactivate PS

        Note over PF: 주문은 이미 PAID — 추가 작업 없음

    else PG 결과: 결제 미완료 또는 미존재
        critical @Transactional (보상)
            PF->>PP: failAndCompensate()
            activate PP
            PP->>PS: 결제 실패 처리 (FAILED, "결제 미완료")
            activate PS
            PS-->>PP: void
            deactivate PS

            PP->>SS: 재고 확정 복원 (releaseConfirmed)
            activate SS
            SS-->>PP: void
            deactivate SS

            opt 쿠폰 적용 주문인 경우
                PP->>ICS: 쿠폰 복원
                activate ICS
                ICS-->>PP: void
                deactivate ICS
            end

            PP->>OS: 주문 취소 (CANCELED)
            activate OS
            OS-->>PP: void
            deactivate OS
            PP-->>PF: void
            deactivate PP
        end
    end

    PF-->>PC: PaymentInfo
    deactivate PF
    PC-->>사용자: 200 OK
    deactivate PC
```

## 핵심 포인트
- REQUESTED 상태(미확정)의 결제만 확인 가능하다 — 확정(SUCCEEDED/FAILED/CANCELED) 상태는 거부
- PG에 조회하여 결제 완료 여부를 확인한 후 최종 결정한다
- PG 조회에는 별도 서킷 브레이커(pg-query) + Retry를 적용한다
- PG 결과 결제 미완료 또는 미존재이면 보상 트랜잭션을 수행한다
- 보상 로직은 PaymentProcessor.failAndCompensate()에 위임한다
- 결제 성공 확정 시 주문은 이미 PAID이므로 추가 작업 불필요
- Bulkhead(pg-payment)가 적용되어 동시 PG 호출을 제한한다
