# 결제 콜백 수신 시퀀스다이어그램

## 개요
PG 시스템이 결제 처리 완료 후 콜백을 전송하면, 결제 상태를 확정하고 실패 시 보상 트랜잭션을 수행하는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    participant PG시스템
    participant PC as PaymentCallbackController
    participant PF as PaymentFacade
    participant PS as PaymentService
    participant PP as PaymentProcessor
    participant SS as StockService
    participant ICS as IssuedCouponService
    participant OS as OrderService

    PG시스템->>PC: POST /api/v1/payments/callback
    activate PC
    PC->>PF: 콜백 처리
    activate PF

    critical @Transactional
        PF->>PS: paymentKey로 결제 조회
        activate PS
        PS-->>PF: Payment (or null)
        deactivate PS

        alt 결제 미존재 또는 이미 확정된 결제
            Note over PF: 무시 (멱등성)
        else PG 결과 SUCCESS
            PF->>PS: 결제 성공 처리 (markSucceeded)
            activate PS
            PS-->>PF: void
            deactivate PS

            Note over PF: 주문은 이미 PAID — 추가 작업 없음
        else PG 결과 FAILED
            PF->>PP: failAndCompensate()
            activate PP
            PP->>PS: 결제 실패 처리 (FAILED)
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

    PF-->>PC: void
    deactivate PF
    PC-->>PG시스템: 200 OK
    deactivate PC
```

## 핵심 포인트
- 콜백 처리와 보상 트랜잭션은 같은 트랜잭션에서 원자적으로 처리한다
- paymentKey로 결제를 조회한다 (PG가 paymentKey를 콜백에 포함)
- 결제 미존재 또는 이미 확정(SUCCEEDED/FAILED/CANCELED)된 결제에 대한 콜백은 무시한다 (멱등성)
- 결제 성공 시 주문은 이미 PAID 상태이므로 추가 상태 전이가 불필요하다
- 결제 실패 시 보상 로직은 PaymentProcessor.failAndCompensate()에 위임한다
- 인증 없이 호출 가능하다 (PG 시스템이 호출하는 내부 API)
