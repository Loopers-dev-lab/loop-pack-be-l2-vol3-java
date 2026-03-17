# 결제 상태 수동 확인 시퀀스다이어그램

## 개요
콜백이 수신되지 않은 결제에 대해 사용자가 수동으로 PG에 상태를 확인하여 결제를 확정하는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant PC as PaymentController
    participant PF as PaymentFacade
    participant PS as PaymentService
    participant PG as PgClient
    participant OS as OrderService

    사용자->>PC: POST /api/v1/payments/{id}/verify
    activate PC
    PC->>PF: 결제 상태 확인
    activate PF

    PF->>PS: 결제 조회
    activate PS
    PS-->>PF: Payment
    deactivate PS

    Note over PF: 소유권 확인, 확정 여부 확인

    alt REQUESTED 상태 (transactionKey 없음)
        critical @Transactional
            PF->>PS: 결제 실패 처리 (FAILED)
            activate PS
            PS-->>PF: void
            deactivate PS
        end
    else PENDING 상태 (transactionKey 있음)
        PF->>PG: PG 거래 조회 (CB)
        activate PG
        PG-->>PF: 거래 상태
        deactivate PG

        alt PG 결과 SUCCESS
            critical @Transactional
                PF->>PS: 결제 성공 처리 (SUCCESS)
                activate PS
                PS-->>PF: void
                deactivate PS

                PF->>OS: 주문 결제 완료 (PAID)
                activate OS
                OS-->>PF: void
                deactivate OS
            end
        else PG 결과 FAILED
            critical @Transactional
                PF->>PS: 결제 실패 처리 (FAILED)
                activate PS
                PS-->>PF: void
                deactivate PS
            end
        else PG 결과 PENDING
            Note over PF: 상태 변경 없이 현재 상태 반환
        end
    end

    PF-->>PC: PaymentInfo
    deactivate PF
    PC-->>사용자: 200 OK
    deactivate PC
```

## 핵심 포인트
- REQUESTED 상태(transactionKey 없음)는 PG에 요청 자체가 도달하지 않은 것이므로 조회 없이 FAILED 처리한다
- PG 조회 API에는 별도 서킷 브레이커(pg-query)를 적용한다
- PG 결과가 아직 PENDING이면 상태를 변경하지 않고 현재 상태를 그대로 반환한다
- 결제 확정과 주문 상태 전이는 같은 트랜잭션에서 원자적으로 처리한다
