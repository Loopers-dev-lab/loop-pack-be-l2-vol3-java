# 결제 콜백 수신 시퀀스다이어그램

## 개요
PG 시스템이 결제 처리 완료 후 콜백을 전송하면, 결제 상태를 확정하고 성공 시 주문을 PAID로 전이하는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    participant PG시스템
    participant PC as PaymentCallbackController
    participant PF as PaymentFacade
    participant PS as PaymentService
    participant OS as OrderService

    PG시스템->>PC: POST /api/v1/payments/callback
    activate PC
    PC->>PF: 콜백 처리
    activate PF

    critical @Transactional
        PF->>PS: transactionKey로 결제 조회
        activate PS
        PS-->>PF: Payment
        deactivate PS

        alt 이미 확정된 결제
            Note over PF: 무시 (멱등성)
        else PG 결과 SUCCESS
            PF->>PS: 결제 성공 처리 (SUCCESS)
            activate PS
            PS-->>PF: void
            deactivate PS

            PF->>OS: 주문 결제 완료 (PAID)
            activate OS
            OS-->>PF: void
            deactivate OS
        else PG 결과 FAILED
            PF->>PS: 결제 실패 처리 (FAILED)
            activate PS
            PS-->>PF: void
            deactivate PS

            Note over PF: 주문 상태 PENDING 유지
        end
    end

    PF-->>PC: void
    deactivate PF
    PC-->>PG시스템: 200 OK
    deactivate PC
```

## 핵심 포인트
- 콜백 처리와 주문 상태 전이는 같은 트랜잭션에서 원자적으로 처리한다
- 이미 확정(SUCCESS/FAILED)된 결제에 대한 중복 콜백은 무시한다 (멱등성)
- 결제 성공 시 Order.pay()로 PENDING → PAID 전이한다
- 결제 실패 시 주문은 PENDING을 유지한다 (재결제 가능)
- 인증 없이 호출 가능하다 (PG 시스템이 호출하는 내부 API)
