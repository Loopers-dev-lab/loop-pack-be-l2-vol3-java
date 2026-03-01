# ADR: Product 엔티티에 likeCount 비정규화

STATUS: Accepted
DATE: 2026-02-22

## 목차
- [배경](#배경)
- [결정](#결정)
- [동시성 제어 전략](#동시성-제어-전략)
- [조회 시 영향 범위](#조회-시-영향-범위)
- [삭제 시 likeCount 처리](#삭제-시-likecount-처리)
- [트레이드오프](#트레이드오프)
- [대안 검토](#대안-검토)

---

## 배경

### 현재 구조

좋아요 수는 `likes` 테이블에서 매번 집계하여 조회한다.

```
likes 테이블 (정규화된 원본)
┌────┬─────────┬────────────┬───────────┐
│ id │ user_id │ product_id │ liked_at  │
├────┼─────────┼────────────┼───────────┤
│ 1  │ 100     │ 1          │ ...       │
│ 2  │ 101     │ 1          │ ...       │
│ 3  │ 100     │ 2          │ ...       │
└────┴─────────┴────────────┴───────────┘

→ 상품 1의 likeCount = SELECT COUNT(*) FROM likes WHERE product_id = 1
```

### 문제

| 사용처 | 쿼리 | 비용 |
|--------|------|------|
| 상품 목록 조회 | `SELECT product_id, COUNT(*) FROM likes WHERE product_id IN (...) GROUP BY product_id` | 매 페이지 요청마다 GROUP BY |
| 상품 상세 조회 | `SELECT COUNT(*) FROM likes WHERE product_id = ?` | 매 조회마다 COUNT |
| 좋아요 순 정렬 | `LEFT JOIN likes GROUP BY product ORDER BY COUNT(l) DESC` | 전체 likes 테이블 스캔 |
| 좋아요한 상품 목록 | 동일한 `countByProductIdIn` | 동일 비용 |

좋아요 수는 **쓰기보다 읽기가 압도적으로 많은** 데이터이다. 상품 목록 조회는 모든 사용자가 반복적으로 수행하지만, 좋아요 등록/취소는 특정 사용자가 간헐적으로 수행한다. 매 조회마다 likes 테이블을 집계하는 것은 읽기 비용 대비 비효율적이다.

---

## 결정

**Product 엔티티에 `likeCount` 필드를 비정규화한다.**

```java
@Entity
public class Product extends BaseEntity {
    // ... 기존 필드

    @Column(nullable = false)
    private long likeCount = 0;

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
}
```

- 좋아요 등록 시 `product.increaseLikeCount()` 호출
- 좋아요 취소 시 `product.decreaseLikeCount()` 호출
- 조회 시 `product.getLikeCount()`로 직접 읽기 (likes 테이블 집계 제거)

---

## 동시성 제어 전략

### 문제: Lost Update

두 사용자가 동시에 같은 상품에 좋아요를 누를 경우:

```
시간  TX-A (user1 좋아요)           TX-B (user2 좋아요)
─────────────────────────────────────────────────────
t1    SELECT likeCount → 5
t2                                  SELECT likeCount → 5
t3    likeCount = 5 + 1 = 6
t4    UPDATE likeCount = 6
t5                                  likeCount = 5 + 1 = 6
t6                                  UPDATE likeCount = 6  ← Lost Update!
```

기대 결과는 7이지만, 6이 저장된다.

### 해결: 비관적 락 (Pessimistic Lock)

```java
// ProductJpaRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
Optional<Product> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);
```

```
시간  TX-A (user1 좋아요)           TX-B (user2 좋아요)
─────────────────────────────────────────────────────
t1    SELECT ... FOR UPDATE → 5
t2                                  SELECT ... FOR UPDATE → 대기(락)
t3    likeCount = 5 + 1 = 6
t4    COMMIT (락 해제)
t5                                  SELECT ... FOR UPDATE → 6 (락 획득)
t6                                  likeCount = 6 + 1 = 7
t7                                  COMMIT
```

- `SELECT ... FOR UPDATE`로 해당 Product 행에 배타적 락 획득
- 다른 트랜잭션은 락이 해제될 때까지 대기
- 도메인 메서드(`increaseLikeCount()`)로 비즈니스 로직이 엔티티에 캡슐화
- 트랜잭션 범위: `@Transactional`이 이미 적용된 `LikeService.likeProduct()` / `unlikeProduct()`

### 좋아요 등록/취소 흐름

```
[좋아요 등록]
1. Product SELECT FOR UPDATE (비관적 락 획득)
2. likes 테이블에서 중복 확인 (existsByUserIdAndProductId)
3. 중복 아닐 경우: Like INSERT + product.increaseLikeCount()
4. COMMIT (락 해제)

[좋아요 취소]
1. Product SELECT FOR UPDATE (비관적 락 획득)
2. likes 테이블에서 Like 조회
3. Like 존재 시: Like DELETE + product.decreaseLikeCount()
4. COMMIT (락 해제)
```

### 락 범위와 성능

- **락 대상**: 단일 Product 행 (행 레벨 락)
- **락 유지 시간**: 트랜잭션 시작 ~ 커밋 (수 ms 이내)
- **경합 빈도**: 동일 상품에 대한 동시 좋아요/취소만 경합. 서로 다른 상품은 영향 없음
- **데드락 가능성**: 단일 테이블 단일 행 락이므로 데드락 위험 없음

---

## 조회 시 영향 범위

### 변경 전 → 변경 후

| 사용처 | 변경 전 | 변경 후 |
|--------|---------|---------|
| 상품 목록 조회 | `likeRepository.countByProductIdIn()` → Map 구성 | `product.getLikeCount()` 직접 읽기 |
| 상품 상세 조회 | `likeRepository.countByProductId()` | `product.getLikeCount()` 직접 읽기 |
| 좋아요 순 정렬 | `LEFT JOIN likes GROUP BY ORDER BY COUNT(l)` | `ORDER BY p.likeCount DESC` |
| 좋아요한 상품 목록 | `likeRepository.countByProductIdIn()` | `product.getLikeCount()` 직접 읽기 |

### 제거 가능한 메서드

- `LikeRepository.countByProductId(Long productId)`
- `LikeRepository.countByProductIdIn(List<Long> productIds)`
- `LikeJpaRepository`의 대응 JPQL 쿼리
- `LikeRepositoryImpl`의 구현

---

## 삭제 시 likeCount 처리

### 상품 삭제

```java
// ProductService.deleteProduct()
likeRepository.deleteAllByProductId(productId);  // likes 물리 삭제 (유지)
product.delete();                                 // 상품 soft delete
```

- likes 레코드는 물리 삭제하여 데이터 정합성 유지
- likeCount는 soft delete된 상품에 남지만, 삭제된 상품은 조회되지 않으므로 문제 없음

### 브랜드 삭제

```java
// BrandService.deleteBrand()
likeRepository.deleteAllByProductIdIn(productIds);  // likes 일괄 물리 삭제 (유지)
productRepository.softDeleteAllByBrandId(brandId);   // 상품 일괄 soft delete
```

- 동일한 이유로 likeCount 초기화 불필요

---

## 트레이드오프

### 장점

| 항목 | 설명 |
|------|------|
| 조회 성능 | likes 테이블 집계 제거. 인덱스 스캔 없이 Product 컬럼에서 직접 읽기 |
| 정렬 성능 | LEFT JOIN + GROUP BY 제거. `ORDER BY likeCount DESC`로 단순 정렬 |
| 쿼리 단순화 | countByProductIdIn 배치 쿼리 제거. Map 구성 로직 제거 |
| 코드 단순화 | ProductService에서 LikeRepository 의존 감소 |

### 단점

| 항목 | 설명 | 대응 |
|------|------|------|
| 데이터 이중 저장 | likes 테이블과 product.likeCount에 동일 정보 존재 | 쓰기 시점에 동일 트랜잭션에서 동기화 |
| 정합성 위험 | 애플리케이션 버그로 likeCount가 실제와 불일치할 가능성 | 비관적 락으로 동시성 보장. 필요 시 배치로 보정 가능 |
| 쓰기 비용 증가 | 좋아요 등록/취소마다 Product UPDATE 추가 발생 | 읽기 대비 쓰기 빈도가 낮으므로 수용 가능 |
| 락 대기 | 동일 상품 동시 좋아요 시 트랜잭션 대기 | 행 레벨 락이므로 영향 범위 제한적 |

---

## 대안 검토

### 1. 벌크 UPDATE (atomic increment)

```java
@Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
void increaseLikeCount(@Param("id") Long id);
```

- 엔티티 로드 없이 1쿼리로 처리. 성능 최적.
- **기각 이유**: 비즈니스 로직(`likeCount` 증감)이 인프라스트럭처 레이어(JPQL)에 위치. 도메인 엔티티에 캡슐화되지 않아 프로젝트의 DDD 설계 원칙과 불일치.

### 2. 낙관적 락 + 재시도

```java
@Version
private Long version;
```

- 충돌 시 `OptimisticLockException` 발생 후 재시도 로직 필요
- **기각 이유**: 재시도 로직 구현 복잡도 증가. 인기 상품에서 충돌 빈도가 높아질 경우 재시도 비용 증가.

### 3. 현행 유지 (집계 쿼리)

- **기각 이유**: 상품 수와 좋아요 수 증가 시 GROUP BY + COUNT 쿼리 비용이 선형적으로 증가. 정렬 시 LEFT JOIN 비용이 특히 큼.