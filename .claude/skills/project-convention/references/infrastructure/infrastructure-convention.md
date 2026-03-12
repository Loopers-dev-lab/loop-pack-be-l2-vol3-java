# Infrastructure 계층 컨벤션

---

## 1. Repository 패턴

### 3-클래스 패턴

Repository는 **domain 인터페이스 + infrastructure JpaRepository + RepositoryImpl 어댑터** 3개로 구성한다. domain의 Spring 비의존성을 보장하고 Fake 테스트를 가능하게 한다.

```
domain/
└── order/
    └── OrderRepository.java            ← 순수 인터페이스 (Spring 의존 없음)

infrastructure/
└── order/
    ├── OrderJpaRepository.java         ← Spring Data JPA 인터페이스
    └── OrderRepositoryImpl.java        ← 어댑터: OrderRepository 구현, JpaRepository에 위임
```

### domain Repository 인터페이스

Spring 의존 없는 순수 Java 인터페이스. domain이 필요로 하는 메서드만 선언한다. `Page`/`Pageable`은 허용한다.

```java
// domain/order/OrderRepository.java
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findByUserId(Long userId);
}
```

### JpaRepository 인터페이스

```java
// infrastructure/order/OrderJpaRepository.java
public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    Optional<Order> findByIdAndDeletedAtIsNull(Long id);
}
```

### RepositoryImpl -- 어댑터

domain Repository를 구현하고 JpaRepository에 위임한다. `@Repository`를 붙여 JPA 예외를 Spring `DataAccessException`으로 변환한다.

```java
// infrastructure/order/OrderRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(id);  // soft delete 필터링
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderJpaRepository.findByUserId(userId);
    }
}
```

### Soft Delete 조회 처리

RepositoryImpl의 모든 조회 메서드는 `deletedAt IS NULL`을 기본 적용한다. Domain은 soft delete를 모른다.

```java
// domain -- soft delete를 모른다
Optional<Order> findById(Long id);

// RepositoryImpl -- 모든 조회에서 soft delete 필터링
@Override
public Optional<Order> findById(Long id) {
    return orderJpaRepository.findByIdAndDeletedAtIsNull(id);
}
```

### 네이밍 규칙

| 클래스 | 네이밍 | 어노테이션 | 위치 |
|--------|--------|-----------|------|
| domain 인터페이스 | `{Domain}Repository` | 없음 | `domain/{domain}/` |
| JPA 인터페이스 | `{Domain}JpaRepository` | 없음 (자동) | `infrastructure/{domain}/` |
| 어댑터 구현체 | `{Domain}RepositoryImpl` | `@Repository` | `infrastructure/{domain}/` |

---

## 2. QueryDSL 규칙

### RepositoryImpl에 직접 작성

QueryDSL 쿼리는 RepositoryImpl에 직접 작성한다. 단순 CRUD(JpaRepository 위임)와 복잡 쿼리(QueryDSL)를 한 곳에서 관리한다.

```java
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    // JpaRepository 위임
    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    // QueryDSL
    @Override
    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        QProduct product = QProduct.product;
        QBrand brand = QBrand.brand;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(product.deletedAt.isNull());
        builder.and(brand.deletedAt.isNull());

        if (condition.brandId() != null) {
            builder.and(product.brand.id.eq(condition.brandId()));
        }

        List<Product> content = queryFactory
            .selectFrom(product)
            .join(product.brand, brand)
            .where(builder)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .orderBy(buildOrderSpecifier(condition.sort(), product))
            .fetch();

        Long total = queryFactory
            .select(product.count())
            .from(product)
            .join(product.brand, brand)
            .where(builder)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
}
```

### 분리 시점

QueryDSL 메서드가 **5개 이상**이면 `QueryRepository` 클래스로 분리한다.

```
infrastructure/
└── product/
    ├── ProductJpaRepository.java
    ├── ProductQueryRepository.java      ← QueryDSL 전용 (분리 후)
    └── ProductRepositoryImpl.java       ← JPA 위임 + QueryRepository에 위임
```

```java
// 분리 후 QueryRepository
@Repository
@RequiredArgsConstructor
public class ProductQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        // QueryDSL 쿼리 ...
    }
}

// 분리 후 RepositoryImpl -- QueryRepository에 위임
@Override
public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
    return productQueryRepository.search(condition, pageable);
}
```

### 동적 정렬

정렬 조건은 QueryDSL `OrderSpecifier`로 변환한다.

```java
private OrderSpecifier<?> buildOrderSpecifier(String sort, QProduct product) {
    if (sort == null) return product.createdAt.desc();

    return switch (sort) {
        case "price_asc" -> product.price.asc();
        default -> product.createdAt.desc();
    };
}
```

### 검색 조건 DTO

검색 조건은 domain 패키지에 record로 정의한다.

```java
// domain/product/dto/ProductSearchCondition.java
public record ProductSearchCondition(
    Long brandId,
    String sort
) {}
```

---

## 3. BaseEntity

모든 Entity는 `BaseEntity`를 상속한다. `modules/jpa`의 `com.loopers.domain` 패키지에 위치한다.

```java
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    protected void guard() {}            // 검증 훅 (PrePersist/PreUpdate)

    @PrePersist
    private void prePersist() { ... }    // createdAt, updatedAt 자동 설정

    @PreUpdate
    private void preUpdate() { ... }     // updatedAt 자동 갱신

    public void delete() { ... }         // 멱등 soft delete
    public void restore() { ... }        // 멱등 복원
}
```

| 기능 | 메서드/필드 | 동작 |
|------|-----------|------|
| PK 자동 생성 | `id` (IDENTITY) | DB에서 자동 할당 |
| 생성일 자동 기록 | `createdAt` | `@PrePersist`에서 설정, 이후 변경 불가 |
| 수정일 자동 갱신 | `updatedAt` | `@PrePersist`/`@PreUpdate`에서 갱신 |
| Soft Delete | `deletedAt` + `delete()` | 멱등, null이면 삭제 안 됨 |
| 복원 | `restore()` | 멱등, `deletedAt`을 null로 |
| 검증 훅 | `guard()` | 하위 Entity가 override하여 저장 시점 검증 |

### Entity 사용 예시

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    public static Brand create(String name) {
        Brand brand = new Brand();
        brand.name = name;
        return brand;
    }

    @Override
    protected void guard() {
        if (name == null || name.isBlank()) {
            throw new CoreException(BrandErrorCode.NAME_REQUIRED);
        }
    }
}
```

### 주의사항

- `id`, `createdAt`, `updatedAt`, `deletedAt`은 직접 설정하지 않는다 -- BaseEntity가 관리
- 정적 팩토리 메서드에서 `id`를 파라미터로 받지 않는다

---

## 4. DB 제약조건 규칙

**FK 미사용.** 무결성은 애플리케이션 레벨에서 보장한다.

| 관계 | 참조 방식 | 예시 |
|------|----------|------|
| **같은 도메인** (Brand -> Product) | 객체참조 + `NO_CONSTRAINT` | `@ManyToOne` |
| **다른 도메인** 간 | ID 참조 | `private Long userId` |

```java
// 같은 도메인: 객체참조
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
    name = "brand_id",
    foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
private Brand brand;

// 다른 도메인: ID 참조
@Column(name = "user_id", nullable = false)
private Long userId;
```

**@OneToMany 미사용.** 하위 엔티티는 ID로 참조하고 별도 Repository로 조회한다.

```java
// OrderItem에 orderId 필드
@Column(name = "order_id", nullable = false)
private Long orderId;

// 조회는 Repository에서
List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
```

**유니크 제약은 사용한다.** 동시성 상황에서 중복을 방지한다.

```java
// 단일 컬럼
@Column(nullable = false, unique = true)
private String name;

// 복합 유니크
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "product_id"})
})
public class Like extends BaseEntity { ... }
```

---

## 5. 멀티 모듈 구조

```
loop-pack-be-l2-vol3-java/
├── apps/
│   ├── commerce-api/           ← 메인 API 앱
│   ├── commerce-streamer/      ← Kafka 컨슈머
│   └── commerce-batch/         ← 배치
└── modules/
    ├── jpa/                    ← BaseEntity, QueryDSL/JPA/DataSource Config
    ├── kafka/
    └── redis/
```

| 위치 | 포함하는 것 |
|------|-----------|
| `modules/jpa` | `BaseEntity`, `QueryDslConfig`, `JpaConfig`, `DataSourceConfig` |
| `apps/commerce-api` | 도메인 코드, Controller, Facade, Service, Entity, Repository, Infrastructure |

### 패키지 배치 원칙

- `modules/jpa`의 BaseEntity는 `com.loopers.domain` 패키지에 위치
- Config 클래스는 `com.loopers.config.jpa` 패키지에 위치
- 도메인 로직은 modules에 넣지 않는다 -- modules는 기술 인프라만 제공
