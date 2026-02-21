---
name: repository-rules
description: "Repository Interface 설계 규칙, 도메인 언어 사용, 기술 의존성 금지, 조회 전용 경로 분리. Repository Interface를 설계하거나 구현체를 작성할 때 활성화한다."
---

Repository Interface의 설계 원칙과 규칙을 정의한다.
슬랙 QA 논쟁(14, 15번)과 멘토(앨런, 데빈) 피드백으로 확정된 규칙이다.

## 핵심 원칙

> "Repository는 '도메인 언어'만을 사용하여 '도메인 객체'의 '저장, 조회, 수정, 삭제'에 해당하는 기능들을 선언하는,
> '도메인 레이어의 구성요소'이다."

---

## Repository Interface 위치 규칙

1. **Repository Interface는 Domain Layer에 위치한다** (확정)
2. **Repository 구현체는 Infrastructure Layer에 위치한다**
3. **Application Layer에서 Repository를 호출하는 것은 의존 방향상 문제 없다** (Application → Domain 방향)

### 위치를 Domain에 두는 이유

- DIP를 "만족시키려고" 두는 것이 아니라, **도메인을 보호하기 위해** 두는 것
- Repository Interface = 도메인 객체의 영속화 **계약** = 도메인의 책임 (What)

### 층 건너뛰기 금지

Repository Interface를 Application Layer에 놓으면 Infrastructure가 Domain을 건너뛰어 Application을 참조하게 된다:

```
❌ Infrastructure → Application (Domain 건너뜀)
✅ Infrastructure → Domain (인접 레이어 참조, 자연스러움)
```

---

## Repository Interface에 넣어도 되는 것

```java
// ✅ 도메인 언어만 사용
public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findByBrandId(Long brandId);
    List<Product> findAll(int page, int size, ProductSortType sort);
    boolean existsById(Long id);
}
```

## Repository Interface에 넣으면 안 되는 것

```java
// ❌ Spring 기술 의존성
Page<Product> findAll(Pageable pageable);        // Page, Pageable = Spring Data
List<Product> findAll(Sort sort);                // Sort = Spring Data

// ❌ 유스케이스 전용 DTO
List<ProductListDto> findProductSummaries();     // 화면용 DTO
List<OrderSalesDto> findTopSelling(int limit);   // 통계용 DTO

// ❌ JPA Query Method 네이밍 (Infrastructure 관심사)
Optional<User> findByLoginIdValue(String loginId); // JPA 필드 탐색 규칙 노출
```

---

## 기술 의존성 변환은 Infrastructure에서

```java
// Domain Layer — 순수 도메인 언어
public interface ProductRepository {
    List<Product> findAll(int page, int size, ProductSortType sort);
}

// Infrastructure Layer — 기술 변환은 여기서
@Repository
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository jpaRepository;

    @Override
    public List<Product> findAll(int page, int size, ProductSortType sort) {
        Pageable pageable = PageRequest.of(page, size);  // Spring 기술 변환
        OrderSpecifier<?> orderBy = toOrderSpecifier(sort); // QueryDSL 변환
        // QueryDSL 또는 JPA로 실행
    }
}
```

---

## 조회 최적화가 필요한 경우 — 별도 Query 경로

도메인 Repository에 넣기 부적절한 조회(화면용 DTO, 통계, 복잡한 조건)는
별도 Query 경로로 분리한다 (CQRS 적용).

```java
// Command 경로 (쓰기) — Domain Repository
// 도메인 모델을 거쳐 비즈니스 규칙 실행
public interface ProductRepository {       // domain/ 에 위치
    Product save(Product product);
    Optional<Product> findById(Long id);
}

// Query 경로 (읽기) — 별도 인터페이스
// 도메인 모델을 거치지 않고 DTO로 직접 조회
public interface ProductQueryRepository {  // infrastructure/ 에 위치
    List<ProductListDto> findProducts(Long brandId, ProductSortType sort,
                                      int page, int size);
    ProductDetailDto findProductDetail(Long productId);
}
```

**Command vs Query 관심사:**

| 구분 | Command (쓰기) | Query (읽기) |
|------|---------------|-------------|
| 관심사 | 도메인 규칙 준수, 불변 조건 검증 | 빠른 조회, 화면에 맞는 형태 |
| 경로 | Domain Repository → Entity | Query Repository → DTO 직접 |
| Repository 위치 | Domain Layer | Infrastructure Layer |
| 반환 타입 | Entity | DTO, Projection |

---

## 쿼리 객체 패턴 (도메인 레벨 조회 조건)

복합 조회 조건이 필요하면 Domain Layer에 쿼리 객체를 정의한다:

```java
// Domain Layer — 조회 조건 객체 (도메인 언어)
public record ProductSearchCriteria(
    Long brandId,
    ProductSortType sort,
    int page,
    int size
) {}

// Domain Layer — Repository Interface
public interface ProductRepository {
    List<Product> search(ProductSearchCriteria criteria);
}

// Infrastructure Layer — 구현체에서 기술 변환
@Repository
public class ProductRepositoryImpl implements ProductRepository {
    @Override
    public List<Product> search(ProductSearchCriteria criteria) {
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size());
        // QueryDSL로 변환하여 실행
    }
}
```

---

## 정렬 설계 — DIP 적용

- `ProductSortType` enum은 **Domain Layer** (비즈니스 개념: "최신순", "인기순")
- QueryDSL `OrderSpecifier` 변환은 **Infrastructure Layer** (기술 구현)

```java
// domain/product/ProductSortType.java
public enum ProductSortType { LATEST, PRICE_ASC, LIKES_DESC; }

// infrastructure/product/ProductRepositoryImpl.java
OrderSpecifier<?> orderBy = switch (sort) {
    case LATEST -> product.createdAt.desc();
    case PRICE_ASC -> product.basePrice.asc();
    case LIKES_DESC -> product.likeCount.desc();
};
```

---

## 설계 체크리스트

- [ ] Repository Interface가 Domain Layer에 위치하는가?
- [ ] Repository Interface에 Spring 기술 의존성(Page, Pageable, Sort)이 없는가?
- [ ] Repository Interface에 유스케이스 전용 DTO 반환이 없는가?
- [ ] Repository Interface의 메서드 이름이 도메인 언어로 표현되는가?
- [ ] 기술 변환(Pageable 생성, OrderSpecifier 변환 등)이 Infrastructure 구현체에서 수행되는가?
- [ ] 화면/통계용 조회가 별도 Query 경로로 분리되어 있는가?