# 결제 요청 시퀀스다이어그램

## 개요
사용자가 주문에 대해 결제를 요청할 때, Payment를 생성하고 PG에 결제를 요청하는 흐름을 정의한다. PG 호출은 트랜잭션 밖에서 수행하며, 서킷 브레이커와 재시도를 적용한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant PC as PaymentController
    participant PF as PaymentFacade
    participant OS as OrderService
    participant PS as PaymentService
    participant PG as PgClient

    사용자->>PC: POST /api/v1/payments
    activate PC
    PC->>PF: 결제 요청
    activate PF

    critical @Transactional
        PF->>OS: 주문 조회
        activate OS
        OS-->>PF: Order
        deactivate OS

        Note over PF: 소유권 확인, 주문 상태 확인

        PF->>PS: 중복 결제 확인
        activate PS
        PS-->>PF: 없음
        deactivate PS

        PF->>PS: 결제 생성 (REQUESTED)
        activate PS
        PS-->>PF: Payment
        deactivate PS
    end

    alt PG 접수 성공
        PF->>PG: 결제 요청 (CB + Retry)
        activate PG
        PG-->>PF: transactionKey, PENDING
        deactivate PG

        critical @Transactional
            PF->>PS: 상태 변경 (PENDING)
            activate PS
            PS-->>PF: void
            deactivate PS
        end
    else PG 타임아웃
        PF->>PG: 결제 요청 (CB + Retry)
        activate PG
        PG--xPF: 타임아웃
        deactivate PG
        Note over PF: REQUESTED 상태 유지 (보류)
    else PG 요청 실패 / 서킷 오픈
        PF->>PG: 결제 요청 (CB + Retry)
        activate PG
        PG--xPF: 실패
        deactivate PG

        critical @Transactional
            PF->>PS: 상태 변경 (FAILED)
            activate PS
            PS-->>PF: void
            deactivate PS
        end
    end

    PF-->>PC: PaymentInfo
    deactivate PF
    PC-->>사용자: 200 OK / 500 에러
    deactivate PC
```

## 핵심 포인트
- Payment 생성(REQUESTED)은 트랜잭션 안에서, PG 호출은 트랜잭션 밖에서 처리한다 (DB 커넥션 점유 방지)
- PG 호출에는 서킷 브레이커(pg-request)와 재시도(pg-request)를 적용한다
- CB(outer) → Retry(inner) 구조로 메서드를 분리한다
- 타임아웃 시 REQUESTED 상태를 유지한다 — PG에서 처리되었을 수 있으므로 즉시 실패 처리하지 않는다
- PG 요청 실패 시에만 즉시 FAILED 처리하고 사용자에게 에러를 반환한다
- Facade의 결제 요청 메서드 자체에는 @Transactional을 선언하지 않는다 (내부 서비스 호출이 각자 트랜잭션 관리)
