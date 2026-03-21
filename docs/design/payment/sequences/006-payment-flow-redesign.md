# 결제 흐름 재설계 시퀀스다이어그램

## 개요
결제 요청 시 Payment만 생성(REQUESTED)하고 PG를 호출한 뒤, PG 성공 시에만 비즈니스를 확정(재고 확정 + 주문 PAID)하는 지연 확정 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant PC as PaymentController
    participant PF as PaymentFacade
    participant OS as OrderService
    participant PS as PaymentService
    participant PP as PaymentProcessor
    participant SS as StockService
    participant ICS as IssuedCouponService
    participant GE as PaymentGatewayExecutor
    participant GW as PaymentGateway

    사용자->>PC: POST /api/v1/payments
    activate PC
    PC->>PF: 결제 요청
    activate PF

    critical @Transactional (Payment 생성)
        PF->>OS: 주문 조회
        activate OS
        OS-->>PF: Order
        deactivate OS

        Note over PF: 소유권 확인, 주문 상태(CREATED) 확인

        PF->>PS: 중복 결제 확인
        activate PS
        PS-->>PF: 없음
        deactivate PS

        PF->>PS: 결제 생성 (REQUESTED, paymentKey=UUID)
        activate PS
        PS-->>PF: Payment
        deactivate PS
    end

    Note over PF: 재고는 reserve 상태 유지, 주문은 CREATED 유지

    PF->>GE: confirm(payment)
    activate GE

    alt PG 승인 성공
        GE->>GW: confirm(command) [CB: pg-request]
        activate GW
        GW-->>GE: PgResult.Confirm(success)
        deactivate GW
        GE-->>PF: PgConfirmOutcome.Success

        critical @Transactional (비즈니스 확정)
            PF->>PP: confirmAndSettle()
            activate PP
            PP->>PS: markSucceededIfRequested() [비관락]
            activate PS
            PS-->>PP: true
            deactivate PS
            PP->>SS: 재고 확정 (confirm)
            activate SS
            SS-->>PP: void
            deactivate SS
            PP->>OS: 주문 결제 완료 (PAID)
            activate OS
            OS-->>PP: void
            deactivate OS
            PP-->>PF: void
            deactivate PP
        end

    else PG 타임아웃 (readTimeout 3초 초과)
        GE->>GW: confirm(command) [CB: pg-request]
        activate GW
        GW--xGE: PgTimeoutException
        deactivate GW
        GE-->>PF: PgConfirmOutcome.Timeout
        deactivate GE

        Note over PF: REQUESTED 유지<br/>재고 reserve 유지, 주문 CREATED 유지<br/>수동확인/보정스케줄러로 최종 결정

    else PG 요청 실패 / 서킷 오픈
        GE->>GW: confirm(command) [CB: pg-request]
        activate GW
        GW--xGE: PgCommunicationException / CoreException
        deactivate GW
        GE-->>PF: PgConfirmOutcome.Failed
        deactivate GE

        critical @Transactional (예약 해제)
            PF->>PP: failAndRelease()
            activate PP
            PP->>PS: markFailedIfRequested() [비관락]
            activate PS
            PS-->>PP: true
            deactivate PS

            PP->>SS: 재고 예약 해제 (releaseReserved)
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
    PC-->>사용자: 200 OK (성공/타임아웃) / 500 에러 (실패)
    deactivate PC
```

## 핵심 포인트
- **지연 확정**: TX1에서는 Payment 생성만, PG 성공 후 TX2에서 비즈니스 확정 (재고 confirm + 주문 PAID)
- **PG 호출은 트랜잭션 밖**: DB 커넥션 점유 방지
- **PG 실패 시 예약 해제**: releaseReserved로 예약된 재고를 해제 (보상이 아닌 정리)
- **PG 타임아웃 시 일관된 상태**: REQUESTED + reserve + CREATED — 불일치 없음
- **멱등성**: API와 스케줄러 경쟁 시 비관락 + 상태 체크로 중복 실행 방지
- **Bulkhead(pg-payment)**: requestPayment에 동시 PG 호출 제한, 초과 시 즉시 거절
- **PaymentGatewayExecutor**: PG 예외를 PgConfirmOutcome으로 변환, Facade는 Outcome만 처리
- Facade의 결제 요청 메서드 자체에는 @Transactional을 선언하지 않는다 (TransactionTemplate 사용)
