# 📊 상품 조회 인덱스 벤치마크

> 실행일시: 2026-03-09 23:42:17  
> MySQL 8.0 (TestContainers)

## 📋 테스트 환경

| 항목 | 값 |
|---|---|
| 데이터 수 | 500,000건 |
| 브랜드 수 | 100개 (Zipf 분포) |
| 좋아요 분포 | Log-normal (중앙값 ~12, 롱테일) |
| 측정 횟수 | 3회 평균 |
| 인기 브랜드 ID | 1 (95,920건) |
| 중간 브랜드 ID | 51 (1,900건) |

### 브랜드별 상품 수 (상위 10개)

| 순위 | 브랜드 ID | 상품 수 | 비율 |
|---|---|---|---|
| 1 | 1 | 95,920 | 19.2% |
| 2 | 2 | 48,095 | 9.6% |
| 3 | 3 | 32,227 | 6.4% |
| 4 | 4 | 24,068 | 4.8% |
| 5 | 5 | 19,347 | 3.9% |
| 6 | 6 | 16,055 | 3.2% |
| 7 | 7 | 13,564 | 2.7% |
| 8 | 8 | 12,114 | 2.4% |
| 9 | 9 | 10,675 | 2.1% |
| 10 | 10 | 9,668 | 1.9% |

### 좋아요 수 분포

| 구간 | 상품 수 | 비율 |
|---|---|---|
| 0~10 | 239,727 | 47.9% |
| 11~50 | 141,866 | 28.4% |
| 51~200 | 78,215 | 15.6% |
| 201~1,000 | 33,396 | 6.7% |
| 1,001~5,000 | 6,131 | 1.2% |
| 5,001+ | 665 | 0.1% |

---

## 🔍 인덱스 없음 (Baseline)

> PK(id)만 존재하는 상태

**적용된 인덱스:**

| Key_name | Column_name | Seq | Non_unique |
|---|---|---|---|
| PRIMARY | id | 1 | 0 |

### 벤치마크 결과

| # | 쿼리 | type | possible_keys | key | rows | filtered | Extra | 수행시간 |
|---|---|---|---|---|---|---|---|---|
| 1 | 브랜드(인기) + 좋아요순 | ALL | NULL | NULL | 497598 | 1.0 | Using where; Using filesort | **178ms** |
| 2 | 브랜드(인기) + 가격순 | ALL | NULL | NULL | 497598 | 1.0 | Using where; Using filesort | **157ms** |
| 3 | 브랜드(인기) + 최신순 | ALL | NULL | NULL | 497598 | 1.0 | Using where; Using filesort | **155ms** |
| 4 | 브랜드(중간) + 좋아요순 | ALL | NULL | NULL | 497598 | 1.0 | Using where; Using filesort | **149ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 497598 | 10.0 | Using where; Using filesort | **172ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 497598 | 10.0 | Using where; Using filesort | **192ms** |
| 7 | COUNT(브랜드 인기) | ALL | NULL | NULL | 497598 | 1.0 | Using where | **143ms** |
| 8 | COUNT(전체) | ALL | NULL | NULL | 497598 | 10.0 | Using where | **95ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 497598 | 10.0 | Using where; Using filesort | **442ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ALL | NULL | NULL | 497598 | 1.0 | Using where; Using filesort | **267ms** |

<details><summary>실행된 쿼리 상세</summary>

**1. 브랜드(인기) + 좋아요순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**2. 브랜드(인기) + 가격순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

**3. 브랜드(인기) + 최신순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20
```

**4. 브랜드(중간) + 좋아요순**
```sql
SELECT * FROM products WHERE brand_id = 51 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**5. 전체 + 좋아요순**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**6. 전체 + 가격순**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

**7. COUNT(브랜드 인기)**
```sql
SELECT COUNT(*) FROM products WHERE brand_id = 1 AND deleted_at IS NULL
```

**8. COUNT(전체)**
```sql
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL
```

**9. 딥페이징: 좋아요순 OFFSET 10000**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 10000
```

**10. 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 5000
```

</details>

---

## 🔍 단일 인덱스: (brand_id)

> 가장 기본적인 필터 컬럼 인덱스

**적용된 인덱스:**

| Key_name | Column_name | Seq | Non_unique |
|---|---|---|---|
| PRIMARY | id | 1 | 0 |
| idx_brand_id | brand_id | 1 | 1 |

```sql
CREATE INDEX idx_brand_id ON products(brand_id);
```

### 벤치마크 결과

| # | 쿼리 | type | possible_keys | key | rows | filtered | Extra | 수행시간 |
|---|---|---|---|---|---|---|---|---|
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_brand_id | idx_brand_id | 191116 | 10.0 | Using where; Using filesort | **831ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_brand_id | idx_brand_id | 191116 | 10.0 | Using where; Using filesort | **773ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_brand_id | idx_brand_id | 191116 | 10.0 | Using where; Using filesort | **696ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_brand_id | idx_brand_id | 1900 | 10.0 | Using where; Using filesort | **12ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 497965 | 10.0 | Using where; Using filesort | **180ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 497965 | 10.0 | Using where; Using filesort | **175ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_brand_id | idx_brand_id | 191116 | 10.0 | Using where | **771ms** |
| 8 | COUNT(전체) | ALL | NULL | NULL | 497965 | 10.0 | Using where | **75ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 497965 | 10.0 | Using where; Using filesort | **373ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_brand_id | idx_brand_id | 191116 | 10.0 | Using where; Using filesort | **965ms** |

<details><summary>실행된 쿼리 상세</summary>

**1. 브랜드(인기) + 좋아요순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**2. 브랜드(인기) + 가격순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

**3. 브랜드(인기) + 최신순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20
```

**4. 브랜드(중간) + 좋아요순**
```sql
SELECT * FROM products WHERE brand_id = 51 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**5. 전체 + 좋아요순**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**6. 전체 + 가격순**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

**7. COUNT(브랜드 인기)**
```sql
SELECT COUNT(*) FROM products WHERE brand_id = 1 AND deleted_at IS NULL
```

**8. COUNT(전체)**
```sql
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL
```

**9. 딥페이징: 좋아요순 OFFSET 10000**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 10000
```

**10. 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 5000
```

</details>

---

## 🔍 복합 인덱스: (brand_id, deleted_at, like_count DESC)

> 브랜드 필터 + soft delete + 좋아요순 정렬까지 커버

**적용된 인덱스:**

| Key_name | Column_name | Seq | Non_unique |
|---|---|---|---|
| PRIMARY | id | 1 | 0 |
| idx_brand_deleted_like | brand_id | 1 | 1 |
| idx_brand_deleted_like | deleted_at | 2 | 1 |
| idx_brand_deleted_like | like_count | 3 | 1 |

```sql
CREATE INDEX idx_brand_deleted_like ON products(brand_id, deleted_at, like_count DESC);
```

### 벤치마크 결과

| # | 쿼리 | type | possible_keys | key | rows | filtered | Extra | 수행시간 |
|---|---|---|---|---|---|---|---|---|
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 199050 | 100.0 | Using index condition | **2ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 199050 | 100.0 | Using index condition; Using filesort | **609ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 199050 | 100.0 | Using index condition; Using filesort | **623ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 1900 | 100.0 | Using index condition | **1ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 497598 | 10.0 | Using where; Using filesort | **177ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 497598 | 10.0 | Using where; Using filesort | **171ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_brand_deleted_like | idx_brand_deleted_like | 199050 | 100.0 | Using where; Using index | **14ms** |
| 8 | COUNT(전체) | index | idx_brand_deleted_like | idx_brand_deleted_like | 497598 | 10.0 | Using where; Using index | **53ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 497598 | 10.0 | Using where; Using filesort | **500ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 199050 | 100.0 | Using index condition | **40ms** |

<details><summary>실행된 쿼리 상세</summary>

**1. 브랜드(인기) + 좋아요순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**2. 브랜드(인기) + 가격순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

**3. 브랜드(인기) + 최신순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20
```

**4. 브랜드(중간) + 좋아요순**
```sql
SELECT * FROM products WHERE brand_id = 51 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**5. 전체 + 좋아요순**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**6. 전체 + 가격순**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

**7. COUNT(브랜드 인기)**
```sql
SELECT COUNT(*) FROM products WHERE brand_id = 1 AND deleted_at IS NULL
```

**8. COUNT(전체)**
```sql
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL
```

**9. 딥페이징: 좋아요순 OFFSET 10000**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 10000
```

**10. 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 5000
```

</details>

---

## 🔍 전체 커버링 인덱스 세트

> 모든 조회 패턴에 최적화된 인덱스 조합 (쓰기 비용 증가 트레이드오프)

**적용된 인덱스:**

| Key_name | Column_name | Seq | Non_unique |
|---|---|---|---|
| PRIMARY | id | 1 | 0 |
| idx_prod_brand_like | brand_id | 1 | 1 |
| idx_prod_brand_like | deleted_at | 2 | 1 |
| idx_prod_brand_like | like_count | 3 | 1 |
| idx_prod_brand_price | brand_id | 1 | 1 |
| idx_prod_brand_price | deleted_at | 2 | 1 |
| idx_prod_brand_price | price | 3 | 1 |
| idx_prod_brand_created | brand_id | 1 | 1 |
| idx_prod_brand_created | deleted_at | 2 | 1 |
| idx_prod_brand_created | created_at | 3 | 1 |
| idx_prod_deleted_like | deleted_at | 1 | 1 |
| idx_prod_deleted_like | like_count | 2 | 1 |
| idx_prod_deleted_price | deleted_at | 1 | 1 |
| idx_prod_deleted_price | price | 2 | 1 |

```sql
CREATE INDEX idx_prod_brand_like ON products(brand_id, deleted_at, like_count DESC);
CREATE INDEX idx_prod_brand_price ON products(brand_id, deleted_at, price ASC);
CREATE INDEX idx_prod_brand_created ON products(brand_id, deleted_at, created_at DESC);
CREATE INDEX idx_prod_deleted_like ON products(deleted_at, like_count DESC);
CREATE INDEX idx_prod_deleted_price ON products(deleted_at, price ASC);
```

### 벤치마크 결과

| # | 쿼리 | type | possible_keys | key | rows | filtered | Extra | 수행시간 |
|---|---|---|---|---|---|---|---|---|
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 196148 | 100.0 | Using index condition | **1ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_price | 196148 | 100.0 | Using index condition | **1ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_created | 196148 | 100.0 | Using index condition | **2ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 1900 | 100.0 | Using index condition | **1ms** |
| 5 | 전체 + 좋아요순 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_like | 248799 | 100.0 | Using index condition | **0ms** |
| 6 | 전체 + 가격순 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_price | 248799 | 100.0 | Using index condition | **1ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_created | 196148 | 100.0 | Using where; Using index | **24ms** |
| 8 | COUNT(전체) | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_deleted_like | 248799 | 100.0 | Using where; Using index | **112ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_like | 248799 | 100.0 | Using index condition | **241ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 196148 | 100.0 | Using index condition | **75ms** |

<details><summary>실행된 쿼리 상세</summary>

**1. 브랜드(인기) + 좋아요순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**2. 브랜드(인기) + 가격순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

**3. 브랜드(인기) + 최신순**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20
```

**4. 브랜드(중간) + 좋아요순**
```sql
SELECT * FROM products WHERE brand_id = 51 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**5. 전체 + 좋아요순**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

**6. 전체 + 가격순**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

**7. COUNT(브랜드 인기)**
```sql
SELECT COUNT(*) FROM products WHERE brand_id = 1 AND deleted_at IS NULL
```

**8. COUNT(전체)**
```sql
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL
```

**9. 딥페이징: 좋아요순 OFFSET 10000**
```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 10000
```

**10. 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000**
```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 5000
```

</details>

---

## 📈 전략별 성능 비교 요약

| 쿼리 | 인덱스 없음 (Baseline) | 단일 인덱스: (brand_id) | 복합 인덱스: (brand_id, deleted_at, like_count DESC) | 전체 커버링 인덱스 세트 |
|---|---|---|---|---|
| 브랜드(인기) + 좋아요순 | 178ms | 831ms | 2ms | 1ms |
| 브랜드(인기) + 가격순 | 157ms | 773ms | 609ms | 1ms |
| 브랜드(인기) + 최신순 | 155ms | 696ms | 623ms | 2ms |
| 브랜드(중간) + 좋아요순 | 149ms | 12ms | 1ms | 1ms |
| 전체 + 좋아요순 | 172ms | 180ms | 177ms | 0ms |
| 전체 + 가격순 | 192ms | 175ms | 171ms | 1ms |
| COUNT(브랜드 인기) | 143ms | 771ms | 14ms | 24ms |
| COUNT(전체) | 95ms | 75ms | 53ms | 112ms |
| 딥페이징: 좋아요순 OFFSET 10000 | 442ms | 373ms | 500ms | 241ms |
| 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | 267ms | 965ms | 40ms | 75ms |
