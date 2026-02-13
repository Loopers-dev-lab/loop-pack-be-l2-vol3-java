# 향후 확장 설계 방향

> 현재 구현 범위(Brand, Product, Like, Order) 이후 확장할 도메인의 설계 방향을 정리한다.
> 본 문서는 방향성만 제시하며, 구현 시 별도 요구사항 명세를 작성한다.

---

## A. 결제(Payment) 도메인

- 향후 추가 개발 예정
- Order 상태와 분리된 별도 상태 머신 (INITIATED → AUTHORIZED → FAILED/CANCELED)
- PG 콜백 멱등 처리 (pg_txn_id 유니크)
- 결제 실패 시 재고 보상 트랜잭션

## B. 옵션/Variant 시스템

- Product → OptionGroup → OptionValue → Variant 구조
- 재고는 Variant 단위로 관리
- 가격은 base_price + variant_extra_price

## C. 장바구니(Cart)

- 유저당 ACTIVE Cart 1개 정책
- CartLine merge: (cart_id, variant_id) 기준 수량 합산
- Cart는 "표시용" (확정은 Checkout에서)

## D. 주문서(OrderSheet) / Checkout

- Cart → OrderSheet 스냅샷 생성
- DRAFT → VALIDATING → READY_FOR_PAYMENT 상태 전이
- 재고 Reservation + 쿠폰 RESERVED 홀드
- TTL 만료 시 자동 해제 (배치)

## E. 쿠폰(Coupon)

- CouponTemplate (어드민 생성) → IssuedCoupon (유저 귀속)
- 상태: ISSUED → RESERVED → REDEEMED
- 대상 정책: ALL / PRODUCT / CATEGORY / BRAND

## F. Reservation(재고 예약) 모델

- 현재: 주문 시 즉시 차감
- 확장: Checkout 시 HELD → 결제 성공 시 CONFIRMED → 실패 시 RELEASED
- 장점: 결제 중 재고 보호, 만료 시 자동 해제
