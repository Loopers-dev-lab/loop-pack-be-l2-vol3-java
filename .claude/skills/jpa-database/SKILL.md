---
name: jpa-database
description: JPA 관련 패턴 (BaseEntity, Converter, Repository, Soft Delete, Dirty Checking). JPA 코드 작성 시 참조
user-invocable: true
allowed-tools: Read, Grep
---

# JPA & Database 패턴

> 경로 prefix: `apps/commerce-api/src/main/java/com/loopers/`

---

## BaseEntity

레퍼런스: `modules/jpa/src/main/java/com/loopers/domain/BaseEntity.java`

**제공 필드**: `id`, `createdAt`, `updatedAt`, `deletedAt`

**특징**
- `@CreatedDate`, `@LastModifiedDate` + `AuditingEntityListener` 로 자동 관리 (`@PrePersist`에서 직접 대입 불필요)
- `@PrePersist` / `@PreUpdate` 는 `guard()` 호출 전용
- `guard()` 오버라이드 → 저장/수정 전 필수값 검증
- `delete()` → Soft Delete (멱등 보장), `restore()` → 복원

JPA Auditing 활성화: `modules/jpa/src/main/java/com/loopers/config/jpa/JpaConfig.java`

---

## JPA Converter

**규칙**
- `@Converter(autoApply = false)` — 항상 Entity에서 `@Convert(converter = ...)` 명시
- null-safety 필수 (`attribute == null ? null : attribute.value()`)
- VO ↔ DB 타입 양방향 변환

레퍼런스:
- `infrastructure/jpa/converter/MemberIdConverter.java`
- `infrastructure/jpa/converter/RefMemberIdConverter.java`
- `infrastructure/jpa/converter/PriceConverter.java`

---

## Repository 3계층

```
Domain Repository Interface  (domain layer)
          ↓ implements
RepositoryImpl               (infrastructure layer)
          ↓ delegates
JpaRepository                (infrastructure layer)
```

레퍼런스:
- `domain/member/MemberRepository.java` — 도메인 인터페이스
- `infrastructure/member/MemberRepositoryImpl.java` — 구현체
- `infrastructure/member/MemberJpaRepository.java` — Spring Data JPA

**규칙**
```
❌ Service에서 JpaRepository 직접 사용 금지  →  RepositoryImpl 경유
❌ EntityManager 직접 사용 금지  →  JpaRepository @Query 사용
```

---

## Soft Delete

- `delete()` 호출 → `deletedAt` 설정 (이미 삭제된 경우 무시)
- 조회 쿼리에 `WHERE deleted_at IS NULL` 조건 필수

레퍼런스: `infrastructure/member/MemberJpaRepository.java` (findBy... 조건 확인)

---

## JPA Dirty Checking

트랜잭션 내에서 `findById()` 로 조회한 Entity를 변경하면 `save()` 없이 커밋 시 자동 UPDATE.

레퍼런스: `domain/member/MemberService.java` (`changePassword` 메서드)

---

## 트랜잭션 관리

- `@Transactional` → Service 계층에만 적용
- `@Transactional(readOnly = true)` → 읽기 전용 (FlushMode.MANUAL, 스냅샷 비교 생략)
- Controller, App 에는 `@Transactional` 사용 금지

---

## @Query 패턴

### EntityManager 금지 → JpaRepository @Query 사용

```
❌ EntityManager 직접 사용
✅ @Query(value = "SELECT ...", nativeQuery = true)
```

### 페이징 native query — countQuery 필수

```java
@Query(
    value = "SELECT p.* FROM products p WHERE p.deleted_at IS NULL ORDER BY ...",
    countQuery = "SELECT COUNT(*) FROM products p WHERE p.deleted_at IS NULL",
    nativeQuery = true
)
Page<ProductModel> findActiveProducts(Pageable pageable);
```

### 조건부 UPDATE — @Modifying 사용

```java
@Modifying
@Query(value = "UPDATE products SET stock_quantity = stock_quantity - :quantity " +
               "WHERE id = :productId AND stock_quantity >= :quantity", nativeQuery = true)
int decreaseStockIfAvailable(@Param("productId") Long productId, @Param("quantity") int quantity);
```

### nullable 파라미터 조건 — JPQL 조건부 표현식

```java
@Query("SELECT o FROM OrderModel o WHERE o.refMemberId = :refMemberId " +
       "AND (:startDate IS NULL OR o.createdAt >= :startDate) " +
       "AND (:endDate IS NULL OR o.createdAt <= :endDate)")
Page<OrderModel> findByRefMemberIdWithFilter(...);
```

레퍼런스: `infrastructure/product/ProductJpaRepository.java`, `infrastructure/order/OrderJpaRepository.java`

---

## 성능 최적화

**N+1 해결**
- Fetch Join: `JOIN FETCH` 쿼리
- Batch Size: `spring.jpa.properties.hibernate.default_batch_fetch_size: 100`

**인덱스 설계**
- 자주 조회하는 컬럼에 `@Index` 정의
- 레퍼런스: `domain/member/MemberModel.java` (`@Table(indexes = {...})` 확인)

---

## 주의사항

```
❌ 양방향 연관관계 지양  →  단방향 우선
❌ Cascade 신중 사용  →  의도하지 않은 삭제 위험
❌ Controller에 @Transactional  →  Service에서 관리
❌ Lazy Loading을 트랜잭션 밖에서 접근  →  LazyInitializationException 발생
```
