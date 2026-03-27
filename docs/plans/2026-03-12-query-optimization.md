# 상품 조회 성능 최적화 (Query Optimization) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 상품 목록 조회의 N+1 문제 해결, 복합 인덱스 적용, QueryDSL 동적 쿼리 도입, likes UNIQUE 제약 추가

**Architecture:** ProductRepositoryImpl에 QueryDSL 기반 동적 쿼리를 도입하여 brandId 필터 + 정렬을 단일 메서드로 통합. ProductFacade에서 N+1 쿼리를 IN 배치 조회로 교체. Entity에 @Index 어노테이션으로 복합 인덱스 선언.

**Tech Stack:** Java 21, Spring Boot 3.4.4, QueryDSL 5.x (Jakarta), JPA @Index, MySQL 8.0

---

## 의사결정 기록

| # | 항목 | 결정 | 이유 |
|---|------|------|------|
| 1 | 인덱스 관리 | JPA `@Index` 어노테이션 | ddl-auto:create(local)와 자연스럽게 연동, 별도 마이그레이션 도구 불필요 |
| 2 | likes UNIQUE 제약 | `(user_id, product_id)` 추가 | DB 레벨 중복 방지, soft-delete restore 패턴과 호환 |
| 3 | 대량 데이터 시딩 | SQL 스크립트 (`docs/sql/seed-data.sql`) | 앱 독립적, 빠른 실행 |
| 4 | 동적 쿼리 | QueryDSL | brandId 유무에 따른 동적 WHERE, 향후 필터 확장 유연 |

## 트레이드오프 분석

### 인덱스 설계
- **비용**: `like_count` 업데이트마다 인덱스 재정렬 → 쓰기 성능 약간 저하
- **이득**: 10만+ 데이터에서 Full Table Scan → Index Scan으로 조회 성능 대폭 개선
- **결정**: 좋아요 업데이트 빈도 << 조회 빈도이므로 인덱스 적용이 유리

### N+1 해결: IN 배치 조회 vs JOIN
- **IN 배치 조회**: 기존 Entity 구조(brandId long 필드) 변경 없음, 3회 쿼리로 고정
- **JOIN**: 단일 쿼리지만 Entity 연관관계 매핑 필요 → 기존 설계 변경 범위 큼
- **결정**: IN 배치 조회 (기존 구조 유지, 외과적 변경)

### QueryDSL vs JPA 메서드 분리
- **QueryDSL**: 동적 WHERE 조합 가능, 정렬/필터 조합 증가 시 메서드 폭발 방지
- **JPA 메서드**: 단순하지만 brandId × sortType × deletedAt 조합마다 메서드 필요
- **결정**: QueryDSL (이미 의존성 존재, 향후 확장성)

### UNIQUE 제약 + Soft-Delete
- **문제**: `(user_id, product_id)` UNIQUE인데 deleted_at이 NULL이 아닌 row 존재 시?
- **해결**: 현재 로직은 기존 row를 restore하므로 같은 (user_id, product_id) row가 1개만 존재 → UNIQUE 제약과 호환
- **주의**: 물리 삭제(hard delete) 없이 soft-delete만 사용하는 한 문제 없음

---

## 멘토링 기준 적합성 분석 (2026-03-12 검사)

> 멘토링 문서: `docs/mentoring` (2026-03-11, Alen 멘토)

### 1. likeCount 테이블 분리 — 멘토 권장 vs 현재 설계

**멘토 권장 (Q6):**
> "좋아요 수가 자주 바뀜 → 상품(product) 테이블에서 분리 → product_likes 테이블 생성 → 상품 조회 시 join"
> "쓰기가 잦은 컬럼 → 별도 테이블로 정규화하여 격리 → 트레이드오프 자체를 배제"

**현재 설계:** `ProductModel.likeCount` 필드로 product 테이블에 내장

| 비교 항목 | 분리 (멘토 권장) | 내장 (현재) |
|---|---|---|
| 인덱스 쓰기 부담 | product 인덱스 영향 없음 | 좋아요마다 product 인덱스 전체 갱신 |
| 조회 쿼리 | JOIN 또는 서브쿼리 필요 | 단일 테이블 조회, 정렬 인덱스 직접 활용 |
| 구현 복잡도 | 높음 (테이블 분리 + 동기화) | 낮음 (원자적 UPDATE 쿼리) |
| 정합성 | 배치/이벤트 기반 동기화 시 지연 가능 | 즉시 반영 |
| 적합 규모 | 대규모 (9천만 레코드급) | 중소 규모 |

**현재 판단:** 프로젝트가 학습/부트캠프 단계이고 데이터 규모가 10만 이하이므로 내장 방식 유지.
단, **인덱스가 추가되면 좋아요 변경 시 인덱스 갱신 비용이 발생**하는 점은 인지.
실무에서 대규모 트래픽 시에는 멘토 권장대로 분리가 필요.

**개선 시점 신호:**
- product 인덱스 4개 이상 + 좋아요 QPS > 100 → 분리 검토
- EXPLAIN에서 좋아요 UPDATE 시 인덱스 갱신 지연 감지 시

---

### 2. 인덱스 설계 — 멘토링 체크리스트 대조

| 멘토링 체크리스트 | 현재 적합 여부 | 상세 |
|---|---|---|
| 실제 SELECT 쿼리 패턴 분석 | ✅ 분석 완료 | 4개 주요 조회 패턴 식별 |
| 복합 인덱스 순서 (Leftmost prefix) | ✅ 설계 반영 | `deleted_at` 선행 (WHERE 필수 조건) |
| Range 조건 위치 최적화 | ⚠️ 해당 없음 | 현재 Range 조건 미사용 (Equal + ORDER BY) |
| 커버링 인덱스 기회 | ❌ 미활용 | `SELECT *` 사용 중. 커버링 인덱스 불가 |
| EXPLAIN 분석 | ⏳ 시딩 SQL 준비됨 | `docs/sql/seed-data.sql`로 검증 예정 |
| ORDER BY 인덱스 (filesort 방지) | ✅ 설계 반영 | 정렬 컬럼이 인덱스 마지막에 위치 |

**커버링 인덱스 미활용 사유:**
멘토 원문 (§1.2): "SELECT * 대신 필요한 컬럼만 명시하면 커버링 활용"
→ 현재 QueryDSL에서 `selectFrom(product)` = `SELECT *`. 목록 조회에서 `name`, `price`, `brandId`, `likeCount`만 필요하지만,
  JPA Entity 특성상 부분 select는 DTO Projection이 필요 → 구현 복잡도 증가.
→ **현재 단계에서는 미적용. 성능 병목이 확인되면 DTO Projection + 커버링 인덱스 도입 검토.**

---

### 3. N+1 해결 — 멘토링 관점

멘토링에서 N+1을 직접 언급하지는 않지만, Q3 "슬로우 쿼리 해결 판단 기준"에서:
> "EXPLAIN 분석 → 인덱스 설계 → 쿼리 개선 → 데이터 모델 변경 → 캐시 도입"

N+1은 "쿼리 개선" 단계에 해당. 현재 `ProductFacade.getProducts()`에서:

```
AS-IS: 1(상품목록) + N(브랜드) + N(재고) = 2N+1 쿼리
TO-BE: 1(상품목록) + 1(브랜드 IN) + 1(재고 IN) = 3 쿼리 (고정)
```

**멘토의 순서와 부합:** 인덱스(Task 1) → N+1 쿼리 개선(Task 4) → 캐시(별도 브랜치)

---

### 4. findById + deletedAt 이중 체크 패턴

**현재 코드 (`ProductRepositoryImpl.findById`):**
```java
return productJpaRepository.findByIdAndDeletedAtIsNull(id);
```

**현재 코드 (`ProductService.getProduct`):**
```java
ProductModel product = productRepository.findById(id)
    .orElseThrow(...);
if (product.getDeletedAt() != null) { // 이미 findById에서 deletedAt IS NULL 필터링됨
    throw new CoreException(ErrorType.NOT_FOUND, ...);
}
```

**문제:** `findById`가 이미 `deletedAtIsNull` 조건을 포함하므로, Service의 `getDeletedAt() != null` 체크는 **도달 불가 코드(dead code)**.
→ `getProduct()`의 deletedAt 체크를 제거하거나, `findById`를 deletedAt 필터 없이 조회하도록 변경 필요.
→ 현재 의미: `getProduct()`는 customer용(삭제 상품 불가), `getProductForAdmin()`은 admin용(삭제 포함). 그러나 둘 다 같은 `findById`(=deletedAtIsNull)를 호출 → **admin도 삭제 상품 조회 불가 버그.**

**수정 방향 (개발자 확인 필요):**
- Option A: `findById`를 deletedAt 필터 없이 변경 → `getProduct()`에서 deletedAt 체크 유지
- Option B: `findByIdIncludeDeleted()` 별도 메서드 추가 → admin용으로 사용

---

## Task 1: ProductModel에 복합 인덱스 추가

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/domain/product/ProductModel.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/domain/product/ProductModelTest.java`

**Step 1: ProductModel @Table에 인덱스 선언 추가**

```java
@Entity
@Table(name = "product", indexes = {
    @Index(name = "idx_product_brand_deleted_like", columnList = "brand_id, deleted_at, like_count"),
    @Index(name = "idx_product_deleted_like", columnList = "deleted_at, like_count"),
    @Index(name = "idx_product_deleted_created", columnList = "deleted_at, created_at"),
    @Index(name = "idx_product_deleted_price", columnList = "deleted_at, price")
})
public class ProductModel extends BaseEntity {
```

**Step 2: 기존 단위 테스트 실행하여 깨지지 않는지 확인**

Run: `./gradlew test --tests "ProductModelTest" -q`
Expected: PASS

**Step 3: 커밋 대기 (개발자 승인 후)**

---

## Task 2: LikeModel에 UNIQUE 제약 추가

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/domain/like/LikeModel.java`

**Step 1: LikeModel @Table에 uniqueConstraints 선언**

```java
@Entity
@Table(name = "likes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_likes_user_product", columnNames = {"user_id", "product_id"})
})
public class LikeModel extends BaseEntity {
```

**Step 2: 기존 Like 관련 테스트 실행**

Run: `./gradlew test --tests "*Like*" -q`
Expected: PASS

**Step 3: 커밋 대기**

---

## Task 3: BrandService에 배치 조회 메서드 추가

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/brand/BrandService.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/application/brand/BrandServiceTest.java` (신규 또는 기존 확장)

**Step 1: 실패하는 테스트 작성**

```java
@DisplayName("여러 브랜드를 ID 목록으로 일괄 조회한다")
@Test
void getByIds_returnsMapOfBrands() {
    // given
    BrandModel nike = brandRepository.save(new BrandModel("나이키", "스포츠"));
    BrandModel adidas = brandRepository.save(new BrandModel("아디다스", "스포츠"));
    List<Long> ids = List.of(nike.getId(), adidas.getId());

    // when
    Map<Long, BrandModel> result = brandService.getByIds(ids);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(nike.getId()).name().value()).isEqualTo("나이키");
}
```

**Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "*BrandServiceTest.getByIds*" -q`
Expected: FAIL (메서드 미존재)

**Step 3: BrandService에 getByIds 구현**

```java
@Transactional(readOnly = true)
public Map<Long, BrandModel> getByIds(List<Long> ids) {
    return brandRepository.findAllByIdIn(ids)
        .stream()
        .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
}
```

**Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "*BrandServiceTest.getByIds*" -q`
Expected: PASS

**Step 5: 커밋 대기**

---

## Task 4: ProductFacade N+1 해결 - 배치 조회 적용

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/product/ProductFacade.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/application/product/ProductFacadeTest.java`

**Step 1: 실패하는 테스트 작성 (배치 조회 검증)**

```java
@DisplayName("상품 목록 조회 시 Brand/Stock을 배치로 조회한다")
@Test
void getProducts_usesBatchQuery() {
    // given
    Long brandId = createBrand("나이키");
    createProduct("상품1", 10000, brandId, 100);
    createProduct("상품2", 20000, brandId, 50);

    // when
    Page<ProductDetail> result = productFacade.getProducts(null, ProductSortType.CREATED_DESC, PageRequest.of(0, 10));

    // then
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent().get(0).brandName()).isEqualTo("나이키");
    assertThat(result.getContent().get(0).stockStatus()).isNotNull();
}
```

**Step 2: ProductFacade.getProducts() 리팩토링**

AS-IS (N+1):
```java
return products.map(product -> {
    String brandName = getBrandName(product.brandId());      // N회
    StockModel stock = stockService.getByProductId(product.getId()); // N회
    return ProductDetail.ofCustomer(product, brandName, stock.toStatus());
});
```

TO-BE (배치):
```java
public Page<ProductDetail> getProducts(Long brandId, ProductSortType sortType, Pageable pageable) {
    Page<ProductModel> products = productService.getProducts(brandId, sortType, pageable);

    List<Long> brandIds = products.getContent().stream()
        .map(ProductModel::getBrandId).distinct().toList();
    List<Long> productIds = products.getContent().stream()
        .map(ProductModel::getId).toList();

    Map<Long, BrandModel> brandMap = brandService.getByIds(brandIds);
    Map<Long, StockModel> stockMap = stockService.getByProductIds(productIds);

    return products.map(product -> {
        BrandModel brand = brandMap.get(product.getBrandId());
        String brandName = brand != null ? brand.name().value() : null;
        StockModel stock = stockMap.get(product.getId());
        StockStatus status = stock != null ? stock.toStatus() : StockStatus.OUT_OF_STOCK;
        return ProductDetail.ofCustomer(product, brandName, status);
    });
}
```

**Step 3: getProductsForAdmin()도 동일하게 리팩토링**

**Step 4: 기존 E2E 테스트 전체 통과 확인**

Run: `./gradlew test --tests "*ProductV1ApiE2ETest" -q`
Expected: PASS

**Step 5: 커밋 대기**

---

## Task 5: QueryDSL 동적 쿼리 도입

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/domain/product/ProductRepository.java`
- Modify: `apps/commerce-api/src/main/java/com/loopers/infrastructure/product/ProductRepositoryImpl.java`
- Remove (사용하지 않게 되는 메서드): `ProductJpaRepository`의 일부 메서드
- Test: `apps/commerce-api/src/test/java/com/loopers/application/product/ProductServiceIntegrationTest.java`

**Step 1: 실패하는 테스트 작성 (brandId + LIKES_DESC 조합)**

```java
@DisplayName("브랜드 필터 + 좋아요 순 정렬이 동시에 동작한다")
@Test
void getProducts_withBrandIdAndLikesSort() {
    // given
    Long nikeId = createBrand("나이키");
    ProductModel p1 = createProduct("에어맥스", 100000, nikeId, 10);
    ProductModel p2 = createProduct("조던", 200000, nikeId, 10);
    // p2에 좋아요 3개 부여
    productRepository.incrementLikeCount(p2.getId());
    productRepository.incrementLikeCount(p2.getId());
    productRepository.incrementLikeCount(p2.getId());

    // when
    Page<ProductModel> result = productService.getProducts(nikeId, ProductSortType.LIKES_DESC, PageRequest.of(0, 10));

    // then
    assertThat(result.getContent().get(0).getName()).isEqualTo("조던");
    assertThat(result.getContent().get(0).getLikeCount()).isEqualTo(3);
}
```

**Step 2: ProductRepository 인터페이스에 통합 메서드 시그니처 확인**

현재 `findAll(Pageable, ProductSortType)` 존재. brandId 파라미터를 추가:

```java
Page<ProductModel> findAll(Long brandId, Pageable pageable, ProductSortType sortType);
```

**Step 3: ProductRepositoryImpl에 QueryDSL 구현**

```java
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.loopers.domain.product.QProductModel;

// 필드 추가
private final JPAQueryFactory queryFactory;

@Override
public Page<ProductModel> findAll(Long brandId, Pageable pageable, ProductSortType sortType) {
    QProductModel product = QProductModel.productModel;

    BooleanBuilder where = new BooleanBuilder();
    where.and(product.deletedAt.isNull());
    if (brandId != null) {
        where.and(product.brandId.eq(brandId));
    }

    OrderSpecifier<?> orderSpecifier = toOrderSpecifier(product, sortType);

    List<ProductModel> content = queryFactory.selectFrom(product)
        .where(where)
        .orderBy(orderSpecifier)
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    Long total = queryFactory.select(product.count())
        .from(product)
        .where(where)
        .fetchOne();

    return new PageImpl<>(content, pageable, total != null ? total : 0);
}

private OrderSpecifier<?> toOrderSpecifier(QProductModel product, ProductSortType sortType) {
    return switch (sortType) {
        case CREATED_DESC -> product.createdAt.desc();
        case PRICE_ASC -> product.price.value.asc();
        case PRICE_DESC -> product.price.value.desc();
        case LIKES_DESC -> product.likeCount.desc();
    };
}
```

**Step 4: ProductService.getProducts() 시그니처 통합**

```java
public Page<ProductModel> getProducts(Long brandId, ProductSortType sortType, Pageable pageable) {
    return productRepository.findAll(brandId, pageable, sortType);
}
```

**Step 5: 전체 테스트 통과 확인**

Run: `./gradlew test --tests "*Product*" -q`
Expected: PASS

**Step 6: 커밋 대기**

---

## Task 6: 대량 데이터 시딩 SQL 작성

**Files:**
- Create: `docs/sql/seed-data.sql`

**Step 1: 시딩 SQL 작성**

- 브랜드 100개
- 상품 100,000개 (브랜드당 1,000개)
- like_count: 0~10,000 랜덤 분포
- price: 1,000~1,000,000 랜덤 분포

```sql
-- 브랜드 시딩
INSERT INTO brand (name, description, created_at, updated_at) VALUES ...

-- 상품 대량 시딩 (프로시저 활용)
DELIMITER //
CREATE PROCEDURE seed_products()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 100000 DO
    INSERT INTO product (name, description, price, brand_id, like_count, created_at, updated_at)
    VALUES (
      CONCAT('상품_', i),
      CONCAT('설명_', i),
      FLOOR(1000 + RAND() * 999000),
      FLOOR(1 + RAND() * 100),
      FLOOR(RAND() * 10000),
      NOW(), NOW()
    );
    SET i = i + 1;
  END WHILE;
END //
DELIMITER ;

CALL seed_products();
DROP PROCEDURE seed_products;
```

**Step 2: EXPLAIN 분석 쿼리도 함께 작성**

```sql
-- 인덱스 적용 전/후 비교용
EXPLAIN ANALYZE SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY like_count DESC LIMIT 20;

EXPLAIN ANALYZE SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY like_count DESC LIMIT 20;
```

**Step 3: 커밋 대기**

---

## Task 7: 전체 통합 검증

**Step 1: 전체 테스트 실행**

Run: `./gradlew test -q`
Expected: 모든 테스트 PASS

**Step 2: 빌드 확인**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋 대기**

---

## 실행 순서 요약

```
Task 1: @Index 추가 (ProductModel)
Task 2: UNIQUE 제약 추가 (LikeModel)
Task 3: BrandService 배치 조회 메서드
Task 4: ProductFacade N+1 해결
Task 5: QueryDSL 동적 쿼리 도입
Task 6: 대량 데이터 시딩 SQL
Task 7: 전체 통합 검증
```
