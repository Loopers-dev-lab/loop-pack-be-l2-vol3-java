# Phase 1: AtomicInteger + Atomic UPDATE (2중 문지기)

## 핵심 변경 코드

### 1차 문지기 — AtomicInteger 인메모리 카운터

```java
// domain/coupon/CouponIssueCounter.java
public class CouponIssueCounter {

    private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long couponId, int totalQuantity) {
        AtomicInteger counter = counters.computeIfAbsent(couponId, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() <= totalQuantity) {
            return true;
        }
        counter.decrementAndGet();
        return false;
    }

    public void release(Long couponId) {
        AtomicInteger counter = counters.get(couponId);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }
}
```

- CAS 연산으로 ns 단위 처리, DB 커넥션을 잡지 않는다
- 정확한 제한이 아닌 "대략 N명"을 통과시키는 것이 목적
- 쿠폰별로 독립 카운터 관리 (`ConcurrentHashMap<Long, AtomicInteger>`)

### 2차 문지기 — DB Atomic UPDATE

```java
// infrastructure/coupon/CouponJpaRepository.java
@Transactional
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("UPDATE CouponModel c"
        + " SET c.issuedQuantity = c.issuedQuantity + 1"
        + " WHERE c.id = :id AND c.issuedQuantity < c.totalQuantity")
int incrementIssuedQuantity(@Param("id") Long id);
```

- 단일 atomic UPDATE로 정합성 보장 (SELECT 후 UPDATE가 아님)
- `affected rows == 1`이면 발급, `0`이면 거절
- 별도 비관적 락/낙관적 락 불필요

### Facade — 2중 문지기 조율

```java
// application/coupon/CouponFacade.java
public CouponResult.IssuedDetail issueCoupon(Long couponId, Long userId) {
    // 1차 문지기: AtomicInteger (트랜잭션 밖, DB 부하 경감)
    CouponModel coupon = couponService.getById(couponId);
    if (!couponIssueCounter.tryAcquire(couponId, coupon.getTotalQuantity())) {
        throw new CoreException(CouponErrorCode.QUANTITY_EXHAUSTED);
    }
    try {
        // 2차 문지기 + OwnedCoupon INSERT (CouponService @Transactional)
        return CouponResult.IssuedDetail.from(
                couponService.issue(couponId, userId));
    } catch (Exception e) {
        couponIssueCounter.release(couponId);
        throw e;
    }
}
```

- `@Transactional` 없음 — 1차 판정은 트랜잭션 밖에서 수행
- 2차 실패 시 `release()`로 카운터 복원 → 다른 요청이 통과 가능

### Service — 2차 문지기 + INSERT

```java
// domain/coupon/CouponService.java
@Transactional
public OwnedCouponModel issue(Long couponId, Long userId) {
    CouponModel coupon = getById(couponId);
    coupon.validateIssuable();
    ownedCouponRepository.findByCouponIdAndUserId(couponId, userId)
            .ifPresent(owned -> {
                throw new CoreException(CouponErrorCode.ALREADY_ISSUED);
            });
    if (couponRepository.incrementIssuedQuantity(couponId) == 0) {
        throw new CoreException(CouponErrorCode.QUANTITY_EXHAUSTED);
    }
    return ownedCouponRepository.save(OwnedCouponModel.create(coupon, userId));
}
```

### AS-IS에서 제거된 것

```java
// CouponModel.java — 제거
@Version
private Long version;

public void issue() {
    validateIssuable();
    this.issuedQuantity++;
}

// CouponFacade.java — 제거
@Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 5,
        backoff = @Backoff(delay = 50, random = true))
```

---

## AS-IS vs TO-BE 비교

| 지표 | AS-IS (@Version + @Retryable) | TO-BE (AtomicInteger + Atomic UPDATE) | 변화 |
|------|------|------|------|
| **정합성** | 100/100 발급 | 100/100 발급 | 동일 |
| **HTTP 실패율** | **80.18%** (955/1191) | **0.00%** (0/1607) | 80%p 개선 |
| **발급 성공 p99** | 10,658ms | 2,755ms | **74% 감소** |
| **발급 거절 p99** | 8,990ms | 1,644ms | **82% 감소** |
| **전체 p50** | 5,770ms | 307ms | **95% 감소** |
| **전체 p99** | 11,230ms | 2,630ms | **77% 감소** |
| **처리량 (req/s)** | 35.9 | 47.9 | **33% 증가** |
| **완료 iterations** | 1,157 | 1,233 | 6.6% 증가 |
| **dropped iterations** | 68 | 0 | 제거 |

### SLA 달성 여부

| SLA 항목 | 목표 | AS-IS | TO-BE |
|----------|------|-------|-------|
| 발급 성공 p99 < 500ms | < 500ms | 10,658ms | 2,755ms |
| 발급 거절 p99 < 200ms | < 200ms | 8,990ms | 1,644ms |
| HTTP 실패율 < 0.1% | < 0.1% | 80.18% | **0.00%** |

- HTTP 실패율: SLA 달성
- 응답 시간: SLA 미달 (추가 최적화 필요 — Phase 2에서 Redis 도입 검토)

---

## 개선 원리

```
AS-IS: 1,000 요청 → 전부 DB 접근 → @Version 충돌 → Retry 폭주 → 5xx 연쇄
TO-BE: 1,000 요청 → AtomicInteger ~900명 즉시 거절 → ~100명만 DB 접근 → 충돌 없음
```

| 구간 | AS-IS | TO-BE |
|------|-------|-------|
| DB 도달 요청 수 | ~1,000 | ~100 |
| 동시성 제어 방식 | 낙관적 락 (충돌 시 Retry) | Atomic UPDATE (충돌 없음) |
| 5xx 원인 | Retry 소진 후 OptimisticLockException 전파 | 발생하지 않음 |
| 거절 응답 속도 | DB 왕복 후 Retry 반복 | AtomicInteger CAS (ns 단위) |
