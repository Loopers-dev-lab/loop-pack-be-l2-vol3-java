# 쿠폰 발급 시퀀스다이어그램

## 개요
사용자가 쿠폰을 발급받을 때 만료/수량/중복 검증과 동시성 제어를 처리하는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant CC as CouponController
    participant CF as CouponFacade
    participant CS as CouponService
    participant ICS as IssuedCouponService

    사용자->>CC: POST /api/v1/coupons/{couponId}/issue
    activate CC
    CC->>CF: 쿠폰 발급
    activate CF

    critical @Transactional
        CF->>CS: 활성 쿠폰 조회
        activate CS
        CS-->>CF: Coupon
        deactivate CS

        CF->>CF: validateIssuable()
        Note right of CF: 만료 검증 + 수량 검증<br/>(Fail-Fast)

        CF->>CS: 발급 수량 증가 (atomic UPDATE)
        activate CS
        Note right of CS: UPDATE SET issuedCount+1<br/>WHERE id = ? AND issuedCount < maxIssueCount
        CS-->>CF: void
        deactivate CS

        CF->>ICS: 발급 쿠폰 생성
        activate ICS
        Note right of ICS: 중복 발급 검증<br/>(coupon_id + user_id)
        ICS-->>CF: IssuedCoupon
        deactivate ICS
    end

    CF-->>CC: IssuedCouponInfo
    deactivate CF
    CC-->>사용자: 200 OK
    deactivate CC
```

## 핵심 포인트
- Fail-Fast & Atomic Update 전략: 먼저 Entity에서 검증(validateIssuable)하고, 원자적 UPDATE로 issuedCount를 증가시킨다
- 비관적 락 없이 동시성을 보장한다 — atomic UPDATE의 WHERE 조건으로 수량 초과를 방지
- 중복 발급 검증은 IssuedCouponService에서 처리 (DB unique constraint 활용)
- 발급 수량 증가와 발급 쿠폰 생성은 하나의 트랜잭션에서 원자적으로 처리한다
