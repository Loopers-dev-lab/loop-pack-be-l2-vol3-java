---
name: analyze-lock-index
description: >
  JPA 비관락(@Lock) 또는 직접 작성한 FOR UPDATE 쿼리에서
  WHERE 조건 컬럼에 인덱스가 없어 로우락이 아닌 테이블락(또는 과도한 락 범위)이
  발생하는지 분석하고, 성능 이슈 가능성을 진단한다.

  특히 다음을 중점적으로 점검한다.
  - @Lock이 걸린 Repository 메서드의 WHERE 조건 컬럼에 인덱스가 있는가?
  - 인덱스 없이 FOR UPDATE → 풀스캔 → 불필요한 row 락 발생 여부
  - 세컨더리 인덱스 사용 시 2중 락(세컨더리 + 클러스터 인덱스) 인지 여부
  - 락 범위가 의도한 row 단위인지, 사실상 테이블 단위인지 판단

  단순한 정답 제시가 아니라, 현재 쿼리 구조와 인덱스 설계의 trade-off를 드러내고
  개선 가능 지점을 선택적으로 제시한다.

  다음 키워드가 포함된 요청에는 반드시 이 스킬을 사용한다:
  비관락 인덱스, FOR UPDATE 인덱스, 락 범위, 테이블락, 로우락,
  락 성능, PESSIMISTIC_WRITE 인덱스, 비관락 성능, 락 풀스캔,
  인덱스 없는 비관락, analyze-lock-index
---

### 📌 Analysis Scope

이 스킬은 아래 대상에 대해 분석한다.

- `@Lock(LockModeType.PESSIMISTIC_WRITE / PESSIMISTIC_READ)` 가 붙은 Repository 메서드
- `FOR UPDATE` 또는 `LOCK IN SHARE MODE` 가 포함된 JPQL / Native Query
- 위 쿼리의 WHERE 조건에 사용된 컬럼의 인덱스 존재 여부
- 해당 Entity의 `@Index`, `@Column`, `@Table` 어노테이션

> 락이 걸린 쿼리 하나만 분석하지 않고,
> 그 쿼리가 호출되는 Service / Transaction 흐름 전체를 기준으로 판단한다.

---

### 🔍 Analysis Checklist

#### 1. 비관락 쿼리 식별

분석 대상에서 아래 항목을 먼저 찾는다.

- `@Lock` 어노테이션이 붙은 Repository 메서드 전수 수집
- `@Query` 또는 메서드명으로 생성되는 WHERE 조건 파악
- Native Query라면 SQL 직접 확인

**수집 예시**
```markdown
- CouponRepository.findByIdWithLock(Long id)
  → @Lock(PESSIMISTIC_WRITE)
  → WHERE c.id = :id
  → 조건 컬럼: id (PK)

- CouponRepository.findByStatusWithLock(String status)
  → @Lock(PESSIMISTIC_WRITE)
  → WHERE c.status = :status
  → 조건 컬럼: status
```

---

#### 2. WHERE 조건 컬럼 인덱스 유무 확인

각 쿼리의 WHERE 조건 컬럼에 대해 아래를 확인한다.

**2-1. Entity 어노테이션 확인**

```java
// ✅ PK → 클러스터 인덱스 자동 생성
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

// ✅ 세컨더리 인덱스 명시
@Column(name = "coupon_code")
@Index(name = "idx_coupon_code", columnList = "coupon_code")
private String couponCode;

// ❌ 인덱스 없음
@Column(name = "status")
private String status;
```

**2-2. @Table 레벨 인덱스 확인**

```java
@Entity
@Table(
    name = "coupon",
    indexes = {
        @Index(name = "idx_coupon_member", columnList = "member_id"),
        @Index(name = "idx_coupon_code",   columnList = "coupon_code")
    }
)
public class Coupon { ... }
```

**2-3. 판정 기준**

| WHERE 조건 컬럼 | 인덱스 유형 | 락 범위 판정 |
|---|---|---|
| PK (`id`) | 클러스터 인덱스 | ✅ 정확한 로우락 |
| 인덱스 있는 컬럼 | 세컨더리 인덱스 | ✅ 조건 맞는 row만 락 |
| 인덱스 없는 컬럼 | 없음 | ❌ 풀스캔 → 테이블 전체 락 |
| 복합 조건 (AND) | 복합 인덱스 여부에 따라 다름 | 별도 판단 필요 |

---

#### 3. 락 범위 위험도 판정

**3-1. 안전 (로우락)**

```java
// WHERE 조건 = PK
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Coupon c WHERE c.id = :id")
Optional<Coupon> findByIdWithLock(Long id);

// 생성 SQL
// SELECT * FROM coupon WHERE id = 1 FOR UPDATE;
// → PK = 클러스터 인덱스 → 정확히 1 row만 락
```

**3-2. 주의 (세컨더리 인덱스 락)**

```java
// WHERE 조건 = 세컨더리 인덱스 컬럼
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Coupon c WHERE c.couponCode = :couponCode")
Optional<Coupon> findByCouponCodeWithLock(String couponCode);

// 동작
// 1단계: 세컨더리 인덱스(coupon_code) 탐색 → 세컨더리 인덱스 레코드에 X락
// 2단계: PK 획득 → 클러스터 인덱스 레코드에 X락
// → 2중 락 발생 (정상 동작이지만 인지 필요)
// → 같은 row를 PK로 접근하는 다른 트랜잭션도 블로킹됨
```

**3-3. 위험 (테이블락)**

```java
// WHERE 조건 = 인덱스 없는 컬럼
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<Coupon> findByStatus(String status);

// 생성 SQL
// SELECT * FROM coupon WHERE status = 'ACTIVE' FOR UPDATE;
// → 인덱스 없음 → 풀스캔
// → 스캔한 모든 row에 X락 → 사실상 테이블 전체 락
// → 다른 모든 FOR UPDATE 블로킹 💥
```

**출력 예시**
```markdown
[분석 결과]

✅ CouponRepository.findByIdWithLock(Long id)
   → WHERE id = :id → PK → 로우락 (안전)

⚠️  CouponRepository.findByCouponCodeWithLock(String couponCode)
   → WHERE coupon_code = :couponCode → 세컨더리 인덱스
   → 2중 락 발생 (세컨더리 + 클러스터 인덱스)
   → 인덱스 있으므로 락 범위는 적절, 2중 락 구조 인지 필요

❌ CouponRepository.findByStatus(String status)
   → WHERE status = :status → 인덱스 없음
   → 풀스캔 → 전체 테이블 락 → 성능 이슈 위험
```

---

#### 4. 추가 위험 패턴 점검

비관락 쿼리 자체가 안전해도 아래 패턴이 있으면 추가 위험이 있다.

**4-1. @Transactional 누락**

```java
// ❌ @Transactional 없으면 조회 직후 락 해제
public void issueCoupon(Long couponId) {
    Coupon coupon = couponRepository.findByIdWithLock(couponId).orElseThrow();
    coupon.decrease(); // 이미 락이 풀린 상태 → 의미 없음
}

// ✅ @Transactional 필수
@Transactional
public void issueCoupon(Long couponId) {
    Coupon coupon = couponRepository.findByIdWithLock(couponId).orElseThrow();
    coupon.decrease();
} // 커밋 시 락 해제
```

**4-2. 락 타임아웃 미설정**

```java
// ❌ 타임아웃 없음 → 무한 대기 → 스레드 고갈 위험
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Coupon> findByIdWithLock(Long id);

// ✅ 타임아웃 설정
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(
    @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
)
Optional<Coupon> findByIdWithLock(Long id);
```

**4-3. 락 획득 순서 불일치 (데드락)**

```java
// ❌ 서로 다른 순서로 락 획득 → 데드락 가능
// 트랜잭션 A: coupon 락 → order 락
// 트랜잭션 B: order 락  → coupon 락

// ✅ 모든 트랜잭션에서 락 획득 순서 통일
couponRepository.findByIdWithLock(couponId); // 항상 coupon 먼저
orderRepository.findByIdWithLock(orderId);   // 항상 order 나중
```

**4-4. PESSIMISTIC_READ 남용**

```java
// ❌ 수량 차감처럼 조회 후 수정이 확정된 시나리오
//    → PESSIMISTIC_READ(S락) 사용 시 데드락 위험
//    → 두 트랜잭션이 동시에 S락 획득 후 X락 업그레이드 시도
@Lock(LockModeType.PESSIMISTIC_READ) // ← 위험
Optional<Coupon> findByIdForIssue(Long id);

// ✅ 수정 확정 시나리오에는 PESSIMISTIC_WRITE
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Coupon> findByIdWithLock(Long id);
```

---

#### 5. 개선 제안 (선택적)

분석 결과 위험 항목이 있을 때만 개선 방향을 제시한다.

**인덱스 추가**

```java
// Entity에 인덱스 추가
@Entity
@Table(
    name = "coupon",
    indexes = {
        @Index(name = "idx_coupon_status", columnList = "status")
    }
)
public class Coupon { ... }
```

**쿼리 방향 변경 — PK 기반으로 전환**

```java
// ❌ 인덱스 없는 조건으로 다건 조회 후 락
List<Coupon> findByStatusWithLock(String status);

// ✅ 이미 알고 있는 ID 기반으로 락 전환
Optional<Coupon> findByIdWithLock(Long id);
// 호출부에서 ID를 먼저 조회(일반 SELECT)한 뒤
// ID로 비관락 조회하는 구조로 분리
```

**출력 예시**

```markdown
[개선안] CouponRepository.findByStatus → PK 기반 전환

현재: WHERE status = 'ACTIVE' FOR UPDATE → 인덱스 없음 → 테이블락
개선: 
  1단계: SELECT id FROM coupon WHERE status = 'ACTIVE' (일반 SELECT, 빠름)
  2단계: SELECT ... FROM coupon WHERE id IN (:ids) FOR UPDATE (PK 기반 락)

또는 status 컬럼에 인덱스 추가:
  @Index(name = "idx_coupon_status", columnList = "status")
  → 풀스캔 제거 → 로우락으로 전환
```

---

### ⚠️ 분석 시 주의사항

- **InnoDB 기준으로 분석한다.** MyISAM 등 다른 스토리지 엔진은 동작이 다르다.
- **인덱스 유무는 Entity 어노테이션 기준으로 판단한다.** 실제 DB 스키마와 다를 수 있으므로, 확실하지 않으면 "인덱스 확인 필요"로 표기한다.
- **세컨더리 인덱스 락은 위험이 아니다.** 2중 락 구조를 인지하는 것이 목적이다.
- **락 범위 최소화가 항상 정답은 아니다.** 트랜잭션 격리 요구사항에 따라 넓은 락이 필요한 경우도 있다. trade-off를 드러내는 것이 목적이다.
- **성능 이슈 판정은 트래픽 규모에 따라 다르다.** 단건 처리에서는 테이블락도 문제없을 수 있다. 동시 요청이 많은 시나리오 기준으로 판단한다.
