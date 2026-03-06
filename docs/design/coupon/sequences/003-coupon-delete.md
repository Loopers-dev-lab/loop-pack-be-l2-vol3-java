# 쿠폰 삭제 시퀀스다이어그램

## 개요
관리자가 쿠폰 템플릿을 삭제하면 미사용 발급 쿠폰을 연쇄 삭제하는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 관리자
    participant CC as CouponController
    participant CF as CouponFacade
    participant CS as CouponService
    participant ICS as IssuedCouponService

    관리자->>CC: DELETE /api-admin/v1/coupons/{couponId}
    activate CC
    CC->>CF: 쿠폰 삭제
    activate CF

    critical @Transactional
        CF->>CS: 쿠폰 삭제 (멱등)
        activate CS
        Note right of CS: 조회 후 delete()<br/>이미 삭제된 경우에도 정상 처리
        CS-->>CF: void
        deactivate CS

        CF->>ICS: 미사용 발급 쿠폰 연쇄 삭제
        activate ICS
        ICS-->>CF: void
        deactivate ICS
    end

    CF-->>CC: void
    deactivate CF
    CC-->>관리자: 200 OK
    deactivate CC
```

## 핵심 포인트
- 쿠폰 삭제와 발급 쿠폰 연쇄 삭제는 하나의 트랜잭션에서 원자적으로 처리한다
- 연쇄 삭제 대상은 미사용(AVAILABLE) 발급 쿠폰만 — 이미 사용된(USED) 쿠폰은 보존
- 삭제는 멱등하게 처리한다 — 이미 삭제된 쿠폰을 다시 삭제해도 정상 응답
