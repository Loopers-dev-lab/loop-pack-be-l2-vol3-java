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
    participant PP as PaymentProcessor
    participant SS as StockService
    participant ICS as IssuedCouponService
    participant GE as PaymentGatewayExecutor
    participant GW as PaymentGateway

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

        PF->>PS: 결제 생성 (REQUESTED, paymentKey=UUID)
        activate PS
        PS-->>PF: Payment
        deactivate PS

        PF->>PP: confirm(order)
        activate PP
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

    PF->>GE: confirm(payment)
    activate GE

    alt PG 승인 성공
        GE->>GW: confirm(command) [CB: pg-request]
        activate GW
        GW-->>GE: PaymentConfirmResult(success)
        deactivate GW
        GE-->>PF: PgConfirmOutcome.Success

        critical @Transactional
            PF->>PS: markSucceeded()
            activate PS
            PS-->>PF: void
            deactivate PS
        end

    else PG 타임아웃 (readTimeout 3초 초과)
        GE->>GW: confirm(command) [CB: pg-request]
        activate GW
        GW--xGE: PgTimeoutException
        deactivate GW
        GE-->>PF: PgConfirmOutcome.Timeout
        deactivate GE

        Note over PF: REQUESTED 유지<br/>비즈니스는 이미 확정<br/>수동확인/보정스케줄러로 최종 결정

    else PG 요청 실패 / 서킷 오픈
        GE->>GW: confirm(command) [CB: pg-request]
        activate GW
        GW--xGE: PgCommunicationException / CoreException
        deactivate GW
        GE-->>PF: PgConfirmOutcome.Failed
        deactivate GE

        critical @Transactional (보상)
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

    PF-->>PC: PaymentInfo
    deactivate PF
    PC-->>사용자: 200 OK (성공/타임아웃) / 500 에러 (실패)
    deactivate PC
```

## 핵심 포인트
- **비즈니스 먼저 확정**: 결제 생성 + 재고 확정 + 주문 PAID를 하나의 트랜잭션으로 PG 호출 전에 수행
- **PG 호출은 트랜잭션 밖**: DB 커넥션 점유 방지
- **PG 승인 성공 → 즉시 SUCCEEDED**: PG confirm 응답이 success면 바로 SUCCEEDED 처리 (동기 확인)
- **PG 실패 시 보상**: PaymentProcessor가 별도 트랜잭션으로 재고 확정 복원 + 쿠폰 복원 + 주문 CANCELED
- **PG 타임아웃 시 즉시 보상하지 않음**: PG에서 처리되었을 수 있으므로 수동확인/보정스케줄러로 최종 결정
- **타임아웃 시에도 정상 응답**: PaymentInfo(status=REQUESTED) 반환 → 프론트가 polling 시작
- **Bulkhead(pg-payment)**: requestPayment에 동시 PG 호출 20건 제한, 초과 시 즉시 거절
- **PaymentGatewayExecutor**: PG 예외를 PgConfirmOutcome으로 변환, Facade는 Outcome만 처리
- **PG 예외 도메인화**: Gateway 구현체에서 ResourceAccessException → PgTimeoutException으로 래핑, Resilience4j retryExceptions에 도메인 예외 사용
- Facade의 결제 요청 메서드 자체에는 @Transactional을 선언하지 않는다 (TransactionTemplate 사용)
- 기존 001-payment-request 시퀀스를 대체한다
