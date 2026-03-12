# Analysis Checklist

동시성 분석 시 점검해야 할 6개 영역의 상세 체크리스트.

## 목차

1. [공유 가변 상태 식별](#1-공유-가변-상태-식별-shared-mutable-state)
2. [락 전략 분석](#2-락-전략-분석-locking-strategy)
3. [Race Condition 탐지](#3-race-condition-탐지)
4. [@Retryable + @Transactional 조합 분석](#4-retryable--transactional-조합-분석)
5. [트랜잭션 범위 vs 락 유지 시간](#5-트랜잭션-범위-vs-락-유지-시간)
6. [동시성 테스트 평가](#6-동시성-테스트-평가)

---

## 1. 공유 가변 상태 식별 (Shared Mutable State)

동시에 여러 스레드/요청이 접근할 수 있는 상태를 식별한다.

확인 항목:
- 어떤 Entity의 어떤 필드가 동시 쓰기 대상인가?
- 해당 필드에 대한 보호 기법은 무엇인가?
- 보호가 없다면, 동시 접근 가능성은 현실적인가?

**출력 형식**
```markdown
| Entity | 동시 쓰기 필드 | 동시 접근 시나리오 | 보호 기법 | 평가 |
|--------|--------------|------------------|----------|------|
| ProductModel | stock | 동시 주문 | @Version + @Retryable | 적절 |
| UserModel | point | 동시 주문 | @Version (재시도 없음) | 위험 |
```

## 2. 락 전략 분석 (Locking Strategy)

프로젝트에서 사용 중인 모든 락 전략을 분류하고 적절성을 평가한다.

### 2.1 낙관적 락 (Optimistic Locking)
- `@Version` 필드가 있는 Entity 목록
- 충돌 시 재시도 메커니즘 존재 여부 (`@Retryable`, 수동 재시도)
- 재시도 횟수(`maxAttempts`)의 적절성
- 고경합 환경에서의 재시도 실패율 추정

### 2.2 비관적 락 (Pessimistic Locking)
- `@Lock(LockModeType.PESSIMISTIC_WRITE)` 등 선언 현황
- 락 타임아웃 설정 여부
- 데드락 가능성 (여러 엔티티를 순서 없이 락 획득하는 경우)

### 2.3 DB 수준 제어
- Unique 제약조건을 통한 중복 방지 패턴
- 벌크 UPDATE의 WHERE 조건을 통한 원자적 상태 전이
- `SELECT ... FOR UPDATE` 사용 여부

### 2.4 애플리케이션 수준 제어
- `synchronized`, `ReentrantLock` 등 JVM 내 락
- 단일 인스턴스에서만 유효하다는 한계 인식 여부

### 2.5 분산 락
- Redis 기반 락 (Redisson, Lettuce SET NX)
- 락 획득 타임아웃, TTL 설정 적절성
- 락 해제 보장 (finally 블록, try-with-resources)

**출력 형식**
```markdown
### 낙관적 락 현황

| Entity | @Version | @Retryable 위치 | maxAttempts | 평가 |
|--------|----------|----------------|-------------|------|
| CouponModel | O | CouponService.issue | 50 | 적절 |
| ProductModel | O | OrderFacade.createOrder | 10 | 적절 |
| UserModel | O | 없음 | - | 재시도 미적용 |
```

## 3. Race Condition 탐지

아래 패턴이 존재하는지 점검한다.

### 3.1 Read-then-Write (TOCTOU)
```
read: count = coupon.getIssuedQuantity()
check: if (count < totalQuantity)
write: coupon.issue()  // count가 이미 변경되었을 수 있음
```
- 읽기와 쓰기 사이에 다른 스레드가 끼어들 수 있는가?
- 락이나 원자적 연산으로 보호되고 있는가?

### 3.2 Check-then-Act
```
check: if (!ownedCouponRepository.existsByUserIdAndCouponId(...))
act:   ownedCouponRepository.save(...)  // 다른 스레드도 check 통과 가능
```
- 검증과 실행 사이의 갭에서 경합이 발생할 수 있는가?
- Unique 제약조건 등 DB 레벨 보호가 있는가?

### 3.3 Lost Update
```
thread1: read entity (version=1)
thread2: read entity (version=1)
thread1: write entity (version=2) -> 성공
thread2: write entity (version=2) -> @Version 있으면 실패, 없으면 thread1의 변경 소실
```
- @Version 없이 Dirty Checking에만 의존하는 쓰기 경로가 있는가?

**출력 형식**
```markdown
#### Race Condition 후보

| 위치 | 패턴 | 경합 시나리오 | 보호 기법 | 위험도 |
|------|------|-------------|----------|--------|
| CouponService.issue | Read-then-Write | 동시 발급 | @Version | LOW (보호됨) |
| ProductLikeService.like | Check-then-Act | 동시 좋아요 | Unique 제약 | LOW (보호됨) |
```

## 4. @Retryable + @Transactional 조합 분석

이 조합은 Spring AOP 프록시 순서에 민감하다. 다음을 반드시 확인한다.

### 4.1 프록시 순서
- `@Retryable` 프록시가 `@Transactional` 프록시를 **감싸야** 한다
- 같은 메서드에 선언된 경우: 프록시 Order가 명시적으로 설정되어 있는가?
- 다른 계층에 분리된 경우: 호출 구조상 올바른 순서인가?

### 4.2 트랜잭션 전파와 재시도
```
[Case A] 정상 동작
@Retryable → @Transactional 시작 → 비즈니스 로직 → 커밋 시 충돌 → 롤백 → 재시도 → 새 TX 시작

[Case B] 문제 상황
Outer @Transactional 시작 → Inner @Retryable + @Transactional(REQUIRED) 합류
→ 커밋은 Outer에서 발생 → Inner @Retryable이 예외를 잡지 못함
```

확인 항목:
- Facade에서 시작된 TX에 Service가 합류(REQUIRED)하는데, Service에 @Retryable이 있는 경우
- 예외 발생 시점(커밋 시)과 @Retryable 포착 범위의 불일치
- REQUIRES_NEW 사용 시의 부분 커밋 위험

**출력 형식**
```markdown
#### @Retryable + @Transactional 조합

| 위치 | @Retryable | @Transactional | 전파 속성 | 재시도 동작 | 평가 |
|------|-----------|---------------|----------|-----------|------|
| OrderFacade.createOrder | O (max=10) | O | REQUIRED | 정상 (TX 시작점) | OK |
| CouponService.issue | O (max=50) | O | REQUIRED | 위험 (Facade TX 합류) | WARNING |
```

## 5. 트랜잭션 범위 vs 락 유지 시간

트랜잭션이 길수록 락 유지 시간이 길어진다. 다음을 확인한다.

- 락 획득 후 트랜잭션 내에서 수행되는 작업 목록
- 락과 무관한 작업(외부 API 호출, 복잡한 조회)이 트랜잭션 내에 포함되어 있는가?
- 락 유지 시간으로 인해 다른 요청의 대기 시간이 증가하는가?
- @Version(낙관적 락)의 경우 락이 아닌 검증이므로, TX 길이가 충돌 확률에 영향

**출력 형식**
```markdown
#### 트랜잭션 범위와 락 유지

| 유즈케이스 | TX 범위 | 락 대상 | 락 유형 | TX 내 작업 수 | 불필요 포함 작업 | 평가 |
|-----------|--------|--------|--------|-------------|---------------|------|
| 주문 생성 | Facade | ProductModel | @Version | 6 | 브랜드 조회 | WARNING |
```

## 6. 동시성 테스트 평가

기존 동시성 테스트의 구조와 커버리지를 평가한다.

확인 항목:
- 동시 출발 보장 여부 (CountDownLatch 강화 패턴 사용 여부)
- 스레드 수와 경합 강도의 적절성
- 검증 항목: 성공/실패 카운트, 데이터 정합성, 초과 방지
- @RepeatedTest 사용 여부 (비결정적 테스트의 신뢰도)
- 동시성 보호가 필요하지만 테스트가 없는 시나리오

**출력 형식**
```markdown
#### 동시성 테스트 커버리지

| 시나리오 | 테스트 존재 | 동시 출발 | 스레드 수 | 반복 | 검증 항목 | 평가 |
|---------|-----------|----------|----------|------|---------|------|
| 쿠폰 발급 | O | O (강화) | 50 | 3회 | 초과 발급, 정합성 | 우수 |
| 재고 차감 | O | O (강화) | 30 | 3회 | 초과 차감, 정합성 | 우수 |
| 포인트 차감 | X | - | - | - | - | 미비 |
```
