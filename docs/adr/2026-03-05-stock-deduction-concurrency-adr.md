# ADR: 주문 핵심 동시성 제어 전략 (재고 / 좋아요 / 쿠폰 사용)

- 상태: Accepted
- 날짜: 2026-03-05
- 작성자: OpenCode
- 관련 모듈: `apps/commerce-api`

## 맥락

주문 핵심 흐름에서 동시성 경합이 집중되는 구간은 다음 3개다.

1. 재고 차감
2. 좋아요 수 변경
3. 쿠폰 사용/사용 취소

기존에는 재고 중심으로만 전략 비교가 있었고, 좋아요/쿠폰은 “원자적 업데이트 확정” 결정이 문서로 통합되지 않아 운영 판단 근거가 분산되어 있었다.

## 최종 결정

### 1) 재고(Stock)

재고는 도메인 특성(품절/충돌/처리량)과 운영 전략 비교를 위해 3전략을 유지한다.

- 원자 업데이트: `update ... set stock = stock - :q where stock >= :q`
- 낙관락: `@Version` + `@Lock(OPTIMISTIC)` + `saveAndFlush`
- 비관락: `@Lock(PESSIMISTIC_WRITE)` 조회 후 차감

### 2) 좋아요(Like)

좋아요는 **원자적 업데이트로 확정**한다.

- 경로: `ProductLikeAplicationService` -> `ProductRepository.updateLikeCount` -> JPQL `update`
- 선택 이유: 단순 카운터 성격이며, 행 단위 원자 증가/감소가 경합 대비 효율적

### 3) 쿠폰 사용(Coupon Use)

쿠폰 사용/사용 취소는 **원자적 업데이트로 확정**한다.

- 사용: `AVAILABLE -> USED` 조건부 update
- 사용 취소: `USED -> AVAILABLE` 조건부 update
- 조건 실패(0 row update)는 `CONFLICT`로 해석

## 구현 요약

### 메인 코드

- 재고
  - `ProductStockApplicationService`: 원자/낙관/비관 메서드 분리
  - `ProductJpaRepository`: `@Lock(PESSIMISTIC_WRITE)`, `@Lock(OPTIMISTIC)`, `decreaseStockAtomically`
  - `ProductEntity`: `@Version`, `@DynamicUpdate`

- 좋아요
  - `ProductLikeAplicationService`: `increaseLikeCount/decreaseLikeCount` 모두 원자 update 사용
  - `ProductJpaRepository.updateLikeCount`: 음수 방지 케이스 포함 원자 쿼리

- 쿠폰
  - `CouponApplicationService.use`: `markUsedAtomically`
  - `CouponApplicationService.cancelUse`: `markAvailableAtomically`
  - `IssuedCouponJpaRepository`: 사용/사용취소 모두 `@Modifying` 조건부 update

- 주문 취소 연계
  - `OrderUseCase.cancel`: 주문 취소 후 재고 복구 + 쿠폰 사용 취소 호출
  - 관리자 취소(`memberId=null`)에서도 주문 소유자(`cancelled.memberId()`)로 쿠폰 복구

## 테스트 근거

### 재고 전략 동시성

- 실행 명령:
  - `./gradlew :apps:commerce-api:compileTestJava :apps:commerce-api:test --tests "com.loopers.application.product.ProductStockApplicationServiceConcurrencyTest"`
  - `./gradlew :apps:commerce-api:test --tests "com.loopers.application.product.ProductStockApplicationServiceConcurrencyTest" --rerun-tasks --info`
- 결과: `BUILD SUCCESSFUL`
- 참고: 낙관락 경로는 테스트 코드에서 `CONFLICT` 발생 시 최대 5회 재시도

| 전략 | 요청수 | 성공 | 품절 | 충돌 | 기타실패 | 소요시간(ms) | 처리량(success/sec) | 최종재고 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| PESSIMISTIC | 20 | 10 | 10 | 0 | 0 | 54 | 184.27 | 0 |
| ATOMIC UPDATE | 20 | 10 | 10 | 0 | 0 | 37 | 266.69 | 0 |
| OPTIMISTIC (retry 5) | 20 | 10 | 3 | 7 | 0 | 201 | 49.66 | 0 |

### 좋아요(원자) + 재고(전략별) 혼합 동시성

- 실행 결과: `BUILD SUCCESSFUL` (`ProductStockApplicationServiceConcurrencyTest`)

| 재고 전략 | like 요청수 | stock 요청수 | like 성공 | like 실패 | stock 성공 | stock 품절 | stock 충돌 | stock 기타실패 | 소요시간(ms) | like 처리량 | stock 처리량 | 최종 likeCount | 최종 stock |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| ATOMIC UPDATE | 20 | 20 | 20 | 0 | 10 | 10 | 0 | 0 | 77 | 259.48 | 129.74 | 20 | 0 |
| PESSIMISTIC | 20 | 20 | 20 | 0 | 10 | 10 | 0 | 0 | 88 | 225.70 | 112.85 | 20 | 0 |
| OPTIMISTIC (retry 5) | 20 | 20 | 20 | 0 | 10 | 0 | 10 | 0 | 172 | 115.97 | 57.99 | 20 | 0 |

### 쿠폰 사용/취소 동시성

- `CouponUseConcurrencyTest`
  - 동일 couponId 동시 주문 시 1건만 성공
  - 동일 couponId 사용 취소 동시 요청 시 1건만 성공

- `OrderUseCaseIntegrationTest`
  - 주문 취소 시 쿠폰 복구 후 재주문에서 동일 쿠폰 재사용 가능
  - 관리자 취소에서도 쿠폰 복구 동작

## 트레이드오프

- 재고
  - 비관락: 정합성 강함, 대기 시간 증가 가능
  - 낙관락: 저경합 유리, 고경합 충돌 비용 큼
  - 원자 업데이트: 단순/고성능, 복합 비즈니스 규칙 확장 시 제약 가능

- 좋아요/쿠폰
  - 원자 update는 단일 상태 전이에 매우 적합
  - 다단계 상태 머신이나 외부 연동이 추가되면 별도 락/보상 설계 필요

## 후속 작업

1. 재고 전략 반복 측정(워밍업 + N회)로 평균/분산 지표 보강
2. 쿠폰 발급(issue) 경로도 원자화 필요 여부를 정책 차원에서 결정
3. 운영 장애 대응 문서에 충돌(`CONFLICT`) 재시도 가이드 추가
