# ADR: 동시성 제어 전략

STATUS: Accepted
DATE: 2026-03-03

## 배경

### 문제

시스템 내 여러 도메인에서 동시성 제어가 필요하다.
각 도메인의 충돌 빈도, 접근 패턴, 실패 시 행동이 다르므로 일관된 기준으로 전략을 선택해야 한다.

### 동시성 제어가 필요한 대상

| 대상 | 시나리오 | 충돌 빈도 |
|------|---------|----------|
| 상품 재고 차감 | 인기 상품에 여러 사용자가 동시 주문 | 높음 |
| 좋아요 수 증감 | 여러 사용자가 동시에 좋아요/취소 | 높음 |
| 쿠폰 사용 | 동일 사용자의 중복 요청 (더블 클릭, 네트워크 재시도) | 매우 낮음 |

---

## 결정

| 대상 | 전략 | 이유 |
|------|------|------|
| 상품 재고 차감 | 비관적 락 (`SELECT FOR UPDATE`) + lock timeout 2초 | 충돌 빈도 높음, 읽은 값 기반 검증(품절/부족) 필요, 대기 후 순차 처리 적합 |
| 좋아요 수 증감 | 아토믹 업데이트 (`UPDATE SET likeCount = likeCount + 1`) | 단순 +1/-1 연산, 엔티티 조회 불필요, 1쿼리로 완료 |
| 쿠폰 사용 | 낙관적 락 (`@Version`) | 충돌 빈도 매우 낮음 (소유자 1명), 즉시 실패가 적절 |

### 1. 상품 재고 차감 — 비관적 락

**선택 이유:**
- 재고 차감은 현재 값을 읽고 검증(품절 여부, 수량 부족)한 뒤 갱신해야 하므로 read-then-write 패턴이다.
- 인기 상품은 충돌 빈도가 높아, 낙관적 락의 반복 실패보다 대기 후 순차 처리가 효율적이다.

**lock timeout 설정:**
- `jakarta.persistence.lock.timeout = 2000`(2초)으로 설정한다.
- HikariCP connection-timeout(3초)보다 짧게 설정하여, 락 대기가 커넥션 풀 고갈로 이어지는 것을 방지한다.
- 타임아웃 시 `PessimisticLockException`이 발생하여 즉시 실패한다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
@Query("SELECT p FROM Product p WHERE p.id = :productId AND p.deletedAt IS NULL")
Optional<Product> findByIdAndDeletedAtIsNullForUpdate(@Param("productId") Long productId);
```

### 2. 좋아요 수 증감 — 아토믹 업데이트

**선택 이유:**
- 좋아요 수 변경은 단순한 +1/-1 연산이므로, 엔티티를 조회할 필요 없이 단일 UPDATE 쿼리로 완료할 수 있다.
- 비관적 락(SELECT FOR UPDATE → 엔티티 변경 → flush)은 2쿼리가 필요하지만, 아토믹 업데이트는 1쿼리로 동시성 안전과 성능을 모두 확보한다.
- DB 자체의 row-level lock이 UPDATE 실행 중 동시성을 보장한다.

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :productId AND p.deletedAt IS NULL")
int incrementLikeCount(@Param("productId") Long productId);
```

**기존 비관적 락 대비 개선:**

| 항목 | 비관적 락 (기존) | 아토믹 업데이트 (변경) |
|------|-----------------|---------------------|
| 쿼리 수 | 2 (SELECT FOR UPDATE + UPDATE) | 1 (UPDATE) |
| 락 보유 시간 | SELECT ~ 커밋 | UPDATE 순간 |
| 엔티티 로딩 | 필요 | 불필요 |

### 3. 쿠폰 사용 — 낙관적 락

**선택 이유:**
- `OwnedCoupon`은 특정 사용자 1명에게만 귀속된다.
- 동시 충돌은 정상적 사용이 아니라 비정상적 중복 요청(더블 클릭, 네트워크 재시도)이므로, 대기보다 즉시 실패가 적절하다.
- 충돌 빈도가 매우 낮아 비관적 락의 오버헤드가 불필요하다.

**예외 처리:**
- 충돌 시 `ObjectOptimisticLockingFailureException`이 발생한다.
- 재시도 없이 `CoreException(ALREADY_USED_COUPON)`으로 변환한다.
- 동시 요청 시 2번째 요청은 `OwnedCoupon.use()`의 상태 검증에 의해 `ALREADY_USED_COUPON`으로 실패한다.

```java
// OwnedCoupon.java
@Version
private Long version;
```

---

## 트레이드오프

### 장점

| 항목 | 설명 |
|------|------|
| 전략 일관성 | 도메인 특성에 맞는 최적 전략 적용 |
| 커넥션 풀 보호 | lock timeout으로 무한 대기 방지 |
| 성능 | 좋아요 아토믹 업데이트로 쿼리 50% 감소 |
| 동시성 안전 | 모든 동시성 포인트에 대한 제어 완비 |

### 단점 및 주의사항

| 항목 | 설명 | 대응 |
|------|------|------|
| 아토믹 업데이트 한계 | 엔티티 상태와 DB 값 불일치 가능 | `clearAutomatically = true`로 영속성 컨텍스트 동기화 |
| lock timeout 미지원 | MySQL의 InnoDB는 `innodb_lock_wait_timeout`을 사용하며, JPA 힌트가 무시될 수 있음 | MySQL 서버 설정으로 보완 가능 |
| 낙관적 락 커밋 시점 | 버전 체크가 커밋 시점에 발생하여 메서드 내 try-catch 불가 | `OwnedCoupon.use()`의 상태 검증이 2차 방어선 역할 |
