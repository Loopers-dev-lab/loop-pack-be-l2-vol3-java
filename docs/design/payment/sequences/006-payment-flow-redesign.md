# 결제 흐름 재설계 시퀀스다이어그램

## 개요
결제 요청 시 비즈니스를 먼저 확정(재고 확정 + 주문 PAID)한 후 PG를 호출하고, 실패 시 보상 트랜잭션으로 되돌리는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant PC as PaymentController
    participant PF as PaymentFacade
    participant OS as OrderService
    participant PS as PaymentService
    participant SS as StockService
    participant ICS as IssuedCouponService
    participant PG as PgClient

    사용자->>PC: POST /api/v1/payments
    activate PC
    PC->>PF: 결제 요청
    activate PF

    critical @Transactional (비즈니스 확정)
        PF->>OS: 주문 조회
        activate OS
        OS-->>PF: Order
        deactivate OS

        Note over PF: 소유권 확인, 주문 상태(CREATED) 확인

        PF->>PS: 중복 결제 확인
        activate PS
        PS-->>PF: 없음
        deactivate PS

        PF->>PS: 결제 생성 (REQUESTED)
        activate PS
        PS-->>PF: Payment
        deactivate PS

        PF->>SS: 재고 확정 (confirm)
        activate SS
        SS-->>PF: void
        deactivate SS

        PF->>OS: 주문 결제 완료 (PAID)
        activate OS
        OS-->>PF: void
        deactivate OS
    end

    alt PG 접수 성공
        PF->>PG: 결제 요청 (CB + Retry)
        activate PG
        PG-->>PF: transactionKey
        deactivate PG

        critical @Transactional
            PF->>PS: 상태 변경 (IN_PROGRESS)
            activate PS
            PS-->>PF: void
            deactivate PS
        end

    else PG 타임아웃
        PF->>PG: 결제 요청 (CB + Retry)
        activate PG
        PG--xPF: 타임아웃
        deactivate PG

        Note over PF: REQUESTED 유지<br/>비즈니스는 이미 확정<br/>콜백/수동확인으로 최종 결정

    else PG 요청 실패 / 서킷 오픈
        PF->>PG: 결제 요청 (CB + Retry)
        activate PG
        PG--xPF: 실패
        deactivate PG

        critical @Transactional (보상)
            PF->>PS: 결제 실패 처리 (FAILED)
            activate PS
            PS-->>PF: void
            deactivate PS

            PF->>SS: 재고 확정 복원 (releaseConfirmed)
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
    end

    PF-->>PC: PaymentInfo
    deactivate PF
    PC-->>사용자: 200 OK / 500 에러
    deactivate PC
```

## 핵심 포인트
- **비즈니스 먼저 확정**: 결제 생성 + 재고 확정 + 주문 PAID를 하나의 트랜잭션으로 PG 호출 전에 수행
- **PG 호출은 트랜잭션 밖**: DB 커넥션 점유 방지
- **PG 실패 시 보상**: 별도 트랜잭션으로 재고 확정 복원 + 쿠폰 복원 + 주문 CANCELED
- **PG 타임아웃 시 즉시 보상하지 않음**: PG에서 처리되었을 수 있으므로 콜백/수동확인으로 최종 결정
- Facade의 결제 요청 메서드 자체에는 @Transactional을 선언하지 않는다 (내부 서비스 호출이 각자 트랜잭션 관리)
- 기존 001-payment-request 시퀀스를 대체한다
