# 결제 흐름 재설계

## 배경
기존 결제 흐름은 PG 호출 전에 재고 확정 + 주문 PAID를 먼저 수행하고, PG 실패 시 보상 트랜잭션으로 되돌리는 "낙관적 확정" 패턴이었다. 이 방식은 PG 응답 전까지 실제 결제가 안 된 상태에서 주문이 PAID로 보이는 불일치 구간이 존재했다. 지연 확정 패턴으로 전환하여, PG 승인 성공 후에만 비즈니스를 확정한다.

## API
기존 API 엔드포인트 변경 없음 (내부 흐름 변경)

## 인증
기존과 동일

## 인수 조건

### 결제 요청 흐름 (PM-01)
- [ ] 결제 요청 시 Payment 생성(REQUESTED)만 수행하고, 재고와 주문 상태는 변경하지 않는다
- [ ] PG 승인 성공 시 재고 확정(confirm) + 주문 PAID + 결제 SUCCEEDED를 하나의 트랜잭션으로 처리한다
- [ ] PG 타임아웃 시 결제 상태는 REQUESTED를 유지하고, 재고는 reserve 상태를 유지한다
- [ ] PG 요청 실패 시 재고 예약 해제(releaseReserved) + 쿠폰 복원 + 주문 CANCELED + 결제 FAILED를 하나의 트랜잭션으로 처리한다

### 수동 확인 흐름 (PM-05)
- [ ] REQUESTED 상태의 결제를 확인하면 PG에 조회하여 최종 결정한다
- [ ] PG 조회 결과 결제 완료(found && done)이면 재고 확정 + 주문 PAID + 결제 SUCCEEDED를 처리한다
- [ ] PG 조회 결과 결제 미완료이면 재고 예약 해제 + 주문 CANCELED + 결제 FAILED를 처리한다

### 보정 스케줄러 (미결 결제)
- [ ] REQUESTED 상태에서 2분 경과한 결제를 PG에 조회하여 최종 결정한다
- [ ] PG 성공이면 confirmAndSettle, 실패면 failAndRelease를 실행한다
- [ ] API 요청과 스케줄러가 동시에 처리해도 멱등하게 동작한다 (비관락 + 상태 체크)

### 결제 취소 (PM-06)
- [ ] SUCCEEDED 상태의 결제만 취소할 수 있다
- [ ] 결제 취소 시 CANCEL_REQUESTED로 선점한다 (비관락, 이중 취소 방지)
- [ ] CANCEL_REQUESTED 선점 후 PG에 취소를 요청한다 (실패 시 1회 재시도)
- [ ] PG 취소 성공 시 확정 재고 복원(releaseConfirmed) + 쿠폰 복원 + 주문 CANCELED + 결제 CANCELED를 처리한다
- [ ] PG 취소 실패 시 CANCEL_REQUESTED 상태를 유지하고 보정 스케줄러가 후속 처리한다

### 멱등성 보장
- [ ] markSucceededIfRequested: 비관락 조회 후 이미 SUCCEEDED면 false 반환, 아니면 전이 후 true
- [ ] markFailedIfRequested: 비관락 조회 후 이미 FAILED면 false 반환, 아니면 전이 후 true
- [ ] markCanceledIfRequested: 비관락 조회 후 이미 CANCELED면 false 반환, 아니면 전이 후 true
- [ ] PaymentProcessor의 각 메서드는 반환값 false 시 early return하여 중복 실행을 방지한다

### 주문 상태 변경
- [ ] CREATED → PAID 전이는 PG 승인 성공 시 confirmAndSettle에서 수행된다
- [ ] CREATED → CANCELED 전이는 PG 실패 시 failAndRelease에서 수행된다
- [ ] PAID → CANCELED 전이는 결제 취소 시 cancelAndCompensate에서 수행된다

## 제약
- Payment 상태는 REQUESTED, SUCCEEDED, FAILED, CANCEL_REQUESTED, CANCELED 5가지이다
- TX1(Payment 생성)은 가볍게, PG 호출은 트랜잭션 밖, TX2(비즈니스 확정/해제)는 PG 결과에 따라 처리한다
- 재고는 주문 생성 시 reserve, PG 성공 시 confirm, PG 실패 시 releaseReserved로 생명주기를 관리한다
- 취소 시에는 이미 confirmed된 재고를 releaseConfirmed로 복원한다
- CANCEL_REQUESTED는 되돌릴 수 없는 상태이다 — 스케줄러가 PG 취소를 재시도하여 CANCELED로 확정한다
