# ADR: 유저 상품 목록 조회 인덱스 전략

## 맥락

유저 상품 목록 조회는 현재 `ProductSpecifications.from(criteria)`를 통해 아래 조건을 조합한다.

- 고정 조건: `deleted_at IS NULL`
- 선택 조건: `brand_reference_id`, `category_reference_id`, `price >= minPrice`, `price <= maxPrice`
- 정렬: `created_at DESC`, `like_count DESC`, `price ASC`

이번 실험의 최우선 쿼리는 다음이다.

```sql
SELECT *
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = ?
ORDER BY p.like_count DESC;
```

목표는 다음 2가지다.

1. 현재 유저 목록 조회 쿼리들에 대해 어떤 인덱스 조합이 가장 큰 효과를 주는지 확인한다.
2. 인덱스 적용 후 페이징 방식 비교를 위한 기준 상태를 만든다.

## 실험 범위

이번 ADR은 **인덱스 후보 벤치마크**까지만 다룬다.

- 포함
  - 유저 상품 목록 조회용 대표 쿼리 20개
  - 인덱스 없는 상태와 후보 인덱스 3종의 단계별 비교
  - `EXPLAIN ANALYZE` 기준 실행 시간과 정렬(filesort) 여부 비교
- 제외
  - admin 목록 조회
  - 상세 조회
  - 페이징 방식(offset vs cursor) 최종 비교

## 대표 쿼리 세트

전체 조합 64개 대신 대표 WHERE 5종 x ORDER BY 4종 = 20개로 축약했다.

### WHERE 그룹

1. 조건 없음
2. `brand_reference_id = ?`
3. `category_reference_id = ?`
4. `brand_reference_id = ? AND category_reference_id = ?`
5. `brand_reference_id = ? AND category_reference_id = ? AND price BETWEEN ? AND ?`

### ORDER BY 그룹

1. `created_at DESC`
2. `like_count DESC`
3. `price ASC`
4. `price DESC`

### 실험에 사용한 실제 값

- 브랜드: `7155ede0-55b8-57eb-85ae-120633a7c73c`
- 카테고리: `ec371f79-c4d7-5337-a816-3c3a92208f45`
- 가격 범위: `50000 ~ 100000`

선정 근거:

- 해당 브랜드 활성 상품 수: `11,825`
- 해당 카테고리 활성 상품 수: `93,135`
- 해당 브랜드+카테고리 활성 상품 수: `11,825`
- 해당 브랜드+카테고리+가격범위 활성 상품 수: `3,373`

## 실험 환경

- DB: Podman `loopers-mysql`, MySQL 8.0
- 데이터: `products` 300,000건, `brands` 30건, `categories` 5건
- baseline 인덱스: `PRIMARY(id)`만 유지
- 측정 방식: 각 쿼리에 대해 `EXPLAIN ANALYZE` 1회 실행

### baseline 인덱스 상태

```sql
SHOW INDEX FROM products;
```

결과:

- `PRIMARY(id)`만 존재

## 벤치마크한 인덱스 후보

### A. baseline

- 보조 인덱스 없음

### B. 브랜드 전용 3종

```sql
CREATE INDEX idx_products_brand_deleted_like
ON products (brand_reference_id, deleted_at, like_count DESC, id);

CREATE INDEX idx_products_brand_deleted_created
ON products (brand_reference_id, deleted_at, created_at DESC, id);

CREATE INDEX idx_products_brand_deleted_price
ON products (brand_reference_id, deleted_at, price, id);
```

### C. 카테고리 전용 3종

```sql
CREATE INDEX idx_products_category_deleted_like
ON products (category_reference_id, deleted_at, like_count DESC, id);

CREATE INDEX idx_products_category_deleted_created
ON products (category_reference_id, deleted_at, created_at DESC, id);

CREATE INDEX idx_products_category_deleted_price
ON products (category_reference_id, deleted_at, price, id);
```

### D. 브랜드+카테고리 전용 3종

```sql
CREATE INDEX idx_products_brand_category_deleted_like
ON products (brand_reference_id, category_reference_id, deleted_at, like_count DESC, id);

CREATE INDEX idx_products_brand_category_deleted_created
ON products (brand_reference_id, category_reference_id, deleted_at, created_at DESC, id);

CREATE INDEX idx_products_brand_category_deleted_price
ON products (brand_reference_id, category_reference_id, deleted_at, price, id);
```

### E. 전체 9종 동시 적용

- 브랜드 전용 3종
- 카테고리 전용 3종
- 브랜드+카테고리 전용 3종

## 결과 요약

### 핵심 결과

1. 최우선 쿼리인 `brand + likes DESC`는 baseline `72.7ms`에서 브랜드 전용 3종 적용 후 `10.6ms`로 가장 크게 개선됐다.
2. 카테고리 단독 조회는 category 전용 3종을 적용하면 filesort는 사라지지만, 시간은 `98~100ms -> 77~95ms` 수준으로 개선 폭이 제한적이었다.
3. `brand + category` 조회는 brand+category 전용 3종만으로도 filesort가 사라졌지만, 브랜드 전용 3종 대비 체감 우위는 크지 않았다.
4. `brand + category + price range + price sort`는 브랜드 전용 3종이나 brand+category 전용 3종에서 모두 `3~5ms`대로 크게 빨라졌다.
5. 전체 9종을 동시에 적용하면 category 단독 최신순은 개선되지만, 일부 brand+category likes 케이스는 브랜드 전용 3종 단독보다 오히려 느려졌다.

## 상세 결과

### 1) 최우선 쿼리: brand + likes DESC

| 인덱스 상태 | access | filesort | end time(ms) | rows |
|---|---|---|---:|---:|
| baseline | table-scan | Y | 72.7 | 11825 |
| brand 3종 | index-lookup | N | 10.6 | 11825 |
| category 3종 | table-scan | Y | 69.9 | 11825 |
| brand+category 3종 | index-lookup | Y | 14.3 | 11825 |
| 전체 9종 | index-lookup | N | 10.6 | 11825 |

### 2) 브랜드 필터 + 최신순

| 인덱스 상태 | access | filesort | end time(ms) | rows |
|---|---|---|---:|---:|
| baseline | table-scan | Y | 70.6 | 11825 |
| brand 3종 | index-lookup | N | 12.9 | 11825 |
| category 3종 | table-scan | Y | 70.4 | 11825 |
| brand+category 3종 | index-lookup | Y | 13.8 | 11825 |
| 전체 9종 | index-lookup | N | 11.6 | 11825 |

### 3) 브랜드 필터 + 가격순

| 쿼리 | baseline | brand 3종 | category 3종 | brand+category 3종 | 전체 9종 |
|---|---:|---:|---:|---:|
| brand + price ASC | 69.3ms / Y | 10.8ms / N | 70.0ms / Y | 14.8ms / Y | 10.9ms / N |
| brand + price DESC | 70.7ms / Y | 10.2ms / N | 71.9ms / Y | 14.0ms / Y | 10.6ms / N |

### 4) 카테고리 단독 조회

| 쿼리 | baseline | brand 3종 | category 3종 | brand+category 3종 | 전체 9종 |
|---|---:|---:|---:|---:|
| category + latest | 99.4ms / Y | 100ms / Y | 95.6ms / N | 101ms / Y | 80.2ms / N |
| category + likes | 98.8ms / Y | 98.8ms / Y | 80.3ms / N | 99.8ms / Y | 101ms / N |
| category + price ASC | 98.4ms / Y | 99.6ms / Y | 81.1ms / N | 98.7ms / Y | 104ms / N |
| category + price DESC | 98.1ms / Y | 100ms / Y | 77.0ms / N | 99.4ms / Y | 113ms / N |

### 5) 브랜드 + 카테고리 조회

| 쿼리 | baseline | brand 3종 | category 3종 | brand+category 3종 | 전체 9종 |
|---|---:|---:|---:|---:|
| brand + category + latest | 71.3ms / Y | 10.6ms / N | 77.1ms / N | 11.1ms / N | 14.0ms / N |
| brand + category + likes | 71.4ms / Y | 10.5ms / N | 78.4ms / N | 10.3ms / N | 18.2ms / N |
| brand + category + price ASC | 71.0ms / Y | 10.7ms / N | 77.6ms / N | 11.4ms / N | 14.8ms / N |
| brand + category + price DESC | 71.5ms / Y | 11.5ms / N | 75.4ms / N | 11.8ms / N | 12.0ms / N |

### 6) 브랜드 + 카테고리 + 가격 범위

| 쿼리 | baseline | brand 3종 | category 3종 | brand+category 3종 | 전체 9종 |
|---|---:|---:|---:|---:|---:|
| range + latest | 69.5ms / Y | 4.55ms / Y | 77.9ms / N | 4.60ms / Y | 5.18ms / Y |
| range + likes | 68.7ms / Y | 4.50ms / Y | 78.5ms / N | 4.65ms / Y | 12.3ms / Y |
| range + price ASC | 68.6ms / Y | 3.73ms / N | 21.5ms / N | 3.44ms / N | 4.26ms / N |
| range + price DESC | 70.7ms / Y | 3.74ms / N | 23.8ms / N | 3.61ms / N | 3.89ms / N |

## 해석

### 1. 브랜드 기준 좋아요순은 브랜드 전용 인덱스가 가장 효과적이다

- baseline은 30만 건 전체 table scan 후 sort로 처리됐다.
- `idx_products_brand_deleted_like`가 포함된 브랜드 전용 3종이 가장 안정적으로 개선됐다.
- 이 쿼리는 트래픽 우선순위 0이므로 전용 인덱스 유지 가치가 높다.

### 2. 최신순과 가격순은 여전히 분리 인덱스가 필요하다

- `created_at`, `price`, `like_count`는 정렬 키가 다르므로 각각 전용 컬럼 축을 가진 인덱스가 필요했다.

### 3. brand+category 전용 인덱스는 범위 + 가격정렬에서 가장 강했다

- `brand + category + price range + price sort`에서 `brand_category_deleted_price`가 포함된 상태가 가장 좋았다.
- `3.44ms`, `3.61ms` 수준으로 내려갔다.

### 4. category 단독 케이스는 category 전용 인덱스로만 일부 개선된다

- category 전용 인덱스는 filesort는 제거했지만, 카테고리 카디널리티가 크지 않아 시간 개선 폭은 제한적이었다.
- 전체 9종 동시 적용은 category 최신순에선 개선되지만, 모든 category 정렬에서 일관되게 최고는 아니었다.

### 5. 전체 9종 동시 적용이 항상 최고는 아니다

- 옵티마이저가 더 많은 선택지를 가지면서 일부 `brand + category + likes` 케이스는 브랜드 전용 3종보다 느렸다.
- 따라서 “실험 결과”와 “운영 적용 범위”는 구분해서 판단해야 한다.

## 최종 제안

최종 적용안은 전체 9종이 아니라 아래 6종이다. category 단독 3종은 카디널리티와 개선 폭을 고려했을 때 기본 DDL에 포함하지 않는다.

```sql
CREATE INDEX idx_products_brand_deleted_like
ON products (brand_reference_id, deleted_at, like_count DESC, id);

CREATE INDEX idx_products_brand_deleted_created
ON products (brand_reference_id, deleted_at, created_at DESC, id);

CREATE INDEX idx_products_brand_deleted_price
ON products (brand_reference_id, deleted_at, price, id);

CREATE INDEX idx_products_brand_category_deleted_like
ON products (brand_reference_id, category_reference_id, deleted_at, like_count DESC, id);

CREATE INDEX idx_products_brand_category_deleted_created
ON products (brand_reference_id, category_reference_id, deleted_at, created_at DESC, id);

CREATE INDEX idx_products_brand_category_deleted_price
ON products (brand_reference_id, category_reference_id, deleted_at, price, id);
```

### 적용 이유

- 브랜드 전용 3종은 0순위 `brand + likes DESC`와 브랜드 필터 최신순/가격순에서 가장 안정적으로 효과를 냈다.
- 브랜드+카테고리 전용 3종은 범위 + 가격 정렬에서 가장 좋은 결과를 냈고, 브랜드+카테고리 조합 조회도 filesort 없이 처리했다.
- category 단독 3종은 filesort 제거 효과는 있었지만 시간 개선 폭이 제한적이었고, 전체 9종 동시 적용 시 일부 쿼리에서 오히려 옵티마이저 선택이 흔들렸다.

### 현재까지의 운영 우선순위 제안

1. 필수 유지 후보: 브랜드 전용 3종
2. 필수 유지 후보: 브랜드+카테고리 전용 3종
3. 미적용: 카테고리 전용 3종

## 보류 사항

1. `deleted_at` 선두 대안 인덱스와의 비교 필요 여부 검토
2. category-only 쿼리 비중이 예상보다 높을 경우 category 전용 인덱스 재실험 여부 검토

## 후속 작업

1. `brand + likes DESC`와 `brand + latest/price` 경로의 운영 적용 결과를 추적한다.
2. category-only 쿼리 비중이 높아질 경우 category 전용 3종을 별도 검토한다.
