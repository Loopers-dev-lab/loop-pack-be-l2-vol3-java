# Coupon 도메인 TDD 체크리스트

> 개발 순서: Facade(App) → Domain → Controller
> ★ = 사용자 필수 테스트

---

## App 계층 — CouponFacade

### 쿠폰 관리 (Admin)

- [x] 쿠폰 등록 성공
- [x] 쿠폰 상세 조회 성공
- [x] 쿠폰 목록 조회 — 페이지네이션
- [x] 쿠폰 수정 성공
- [x] 쿠폰 삭제 성공
- [x] 쿠폰 발급 내역 조회 — 페이지네이션

### 쿠폰 발급/조회 (Customer)

- [x] 쿠폰 발급 성공
- [x] 내 쿠폰 목록 조회

---

## App 계층 — OrderFacade (쿠폰 적용 주문)

### 주문 생성

- [x] ★ 쿠폰 적용 주문 생성 — 정액(FIXED) 할인이 적용된다
- [x] ★ 쿠폰 적용 주문 생성 — 정률(RATE) 할인이 적용된다
- [x] ★ 이미 사용된 쿠폰으로 주문 시 실패한다
- [x] 쿠폰 없이 주문 — 기존 흐름 그대로 동작한다
- [x] 존재하지 않는 쿠폰으로 주문 시 실패한다
- [x] 본인 소유가 아닌 쿠폰으로 주문 시 실패한다
- [x] 최소 주문 금액 미달 시 주문 실패한다

### 주문 취소

- [x] 전체 취소 시 쿠폰이 복원된다
- [x] 부분 취소 시 쿠폰은 복원하지 않는다

---

## Domain 계층 — CouponModel (엔티티)

### 생성

- [x] create — 정상 생성
- [x] create — name 빈값 시 예외
- [x] create — discountValue 0 이하 시 예외
- [x] create — RATE 타입 1~100 범위 벗어나면 예외
- [x] create — totalQuantity 1 미만 시 예외
- [x] create — expiredAt 과거 시 예외

### 수정

- [x] update — name, expiredAt 변경

### 할인 계산

- [x] ★ 정액 할인 계산 — discountValue 그대로 반환
- [x] ★ 정률 할인 계산 — orderAmount * discountValue / 100
- [x] 할인 금액이 주문 금액 초과 시 주문 금액까지만 적용

### 발급

- [x] 발급 가능 검증 — 삭제/만료/수량소진 시 실패
- [x] issue — issuedQuantity 증가

### 주문 검증

- [x] 최소 주문 금액 미달 시 실패

---

## Domain 계층 — OwnedCouponModel (엔티티)

### 생성

- [x] create — 정상 생성

### 사용

- [x] ★ 사용 — AVAILABLE → USED 전이
- [x] ★ 이미 사용된 쿠폰 재사용 시 실패
- [x] 본인 소유가 아닌 쿠폰 사용 시 실패
- [x] 만료된 쿠폰 사용 시 실패 (R13 유효기간 재검증)

### 복원

- [x] 복원 — 유효기간 내면 AVAILABLE
- [x] 복원 — 만료 시 EXPIRED

---

## Domain 계층 — CouponService (도메인 서비스)

### 템플릿 관리

- [x] 쿠폰 등록
- [x] 쿠폰 단건 조회 — 존재하지 않으면 예외
- [x] 쿠폰 목록 조회 — 페이지네이션
- [x] 쿠폰 수정 — name, expiredAt 변경
- [x] 쿠폰 삭제 — soft delete

### 발급

- [x] 쿠폰 발급 — 성공
- [x] 중복 발급 시 실패

### 사용/복원

- [x] useAndCalculateDiscount — 성공
- [x] 복원 — 성공

### 조회

- [x] 내 쿠폰 목록 조회 — userId로 조회
- [x] 쿠폰 발급 내역 조회 — couponId로 페이지네이션

---

## Controller 계층 (TLD — 별도 진행)

- [x] CouponV1Controller — 단위 테스트
- [x] AdminCouponV1Controller — 단위 테스트
- [x] OrderV1Controller — 쿠폰 적용 주문 단위 테스트
