# 📊 상품 조회 인덱스 벤치마크

> 실행일시: 2026-03-09 23:41:10  
> MySQL 8.0 (TestContainers)

## 📋 테스트 환경

| 항목 | 값 |
|---|---|
| 데이터 수 | 200,000건 |
| 브랜드 수 | 100개 (Zipf 분포) |
| 좋아요 분포 | Log-normal (중앙값 ~12, 롱테일) |
| 측정 횟수 | 3회 평균 |
| 인기 브랜드 ID | 1 (38,323건) |
| 중간 브랜드 ID | 52 (760건) |

### 브랜드별 상품 수 (상위 10개)

| 순위 | 브랜드 ID | 상품 수 | 비율 |
|---|---|---|---|
| 1 | 1 | 38,323 | 19.2% |
| 2 | 2 | 19,128 | 9.6% |
| 3 | 3 | 13,030 | 6.5% |
| 4 | 4 | 9,593 | 4.8% |
| 5 | 5 | 7,775 | 3.9% |
| 6 | 6 | 6,518 | 3.3% |
| 7 | 7 | 5,314 | 2.7% |
| 8 | 8 | 4,917 | 2.5% |
| 9 | 9 | 4,250 | 2.1% |
| 10 | 10 | 3,819 | 1.9% |

### 좋아요 수 분포

| 구간 | 상품 수 | 비율 |
|---|---|---|
| 0~10 | 96,024 | 48.0% |
| 11~50 | 56,596 | 28.3% |
| 51~200 | 31,348 | 15.7% |
| 201~1,000 | 13,354 | 6.7% |
| 1,001~5,000 | 2,425 | 1.2% |
| 5,001+ | 253 | 0.1% |

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
| 1 | 브랜드(인기) + 좋아요순 | ALL | NULL | NULL | 199329 | 1.0 | Using where; Using filesort | **59ms** |
| 2 | 브랜드(인기) + 가격순 | ALL | NULL | NULL | 199329 | 1.0 | Using where; Using filesort | **60ms** |
| 3 | 브랜드(인기) + 최신순 | ALL | NULL | NULL | 199329 | 1.0 | Using where; Using filesort | **65ms** |
| 4 | 브랜드(중간) + 좋아요순 | ALL | NULL | NULL | 199329 | 1.0 | Using where; Using filesort | **61ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **65ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **65ms** |
| 7 | COUNT(브랜드 인기) | ALL | NULL | NULL | 199329 | 1.0 | Using where | **31ms** |
| 8 | COUNT(전체) | ALL | NULL | NULL | 199329 | 10.0 | Using where | **28ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **104ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ALL | NULL | NULL | 199329 | 1.0 | Using where; Using filesort | **64ms** |

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
SELECT * FROM products WHERE brand_id = 52 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_brand_id | idx_brand_id | 79594 | 10.0 | Using where; Using filesort | **207ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_brand_id | idx_brand_id | 79594 | 10.0 | Using where; Using filesort | **189ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_brand_id | idx_brand_id | 79594 | 10.0 | Using where; Using filesort | **188ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_brand_id | idx_brand_id | 760 | 10.0 | Using where; Using filesort | **2ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **68ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **75ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_brand_id | idx_brand_id | 79594 | 10.0 | Using where | **221ms** |
| 8 | COUNT(전체) | ALL | NULL | NULL | 199329 | 10.0 | Using where | **29ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **131ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_brand_id | idx_brand_id | 79594 | 10.0 | Using where; Using filesort | **205ms** |

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
SELECT * FROM products WHERE brand_id = 52 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 78640 | 100.0 | Using index condition | **1ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 78640 | 100.0 | Using index condition; Using filesort | **160ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 78640 | 100.0 | Using index condition; Using filesort | **160ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 760 | 100.0 | Using index condition | **1ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **74ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **73ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_brand_deleted_like | idx_brand_deleted_like | 78640 | 100.0 | Using where; Using index | **7ms** |
| 8 | COUNT(전체) | index | idx_brand_deleted_like | idx_brand_deleted_like | 199329 | 10.0 | Using where; Using index | **29ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 199329 | 10.0 | Using where; Using filesort | **130ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 78640 | 100.0 | Using index condition | **26ms** |

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
SELECT * FROM products WHERE brand_id = 52 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 75798 | 100.0 | Using index condition | **1ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_price | 75798 | 100.0 | Using index condition | **1ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_created | 75798 | 100.0 | Using index condition | **1ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 760 | 100.0 | Using index condition | **2ms** |
| 5 | 전체 + 좋아요순 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_like | 97156 | 100.0 | Using index condition | **2ms** |
| 6 | 전체 + 가격순 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_price | 97156 | 100.0 | Using index condition | **2ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_created | 75798 | 100.0 | Using where; Using index | **10ms** |
| 8 | COUNT(전체) | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_deleted_like | 97156 | 100.0 | Using where; Using index | **30ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_like | 97156 | 100.0 | Using index condition | **61ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 75798 | 100.0 | Using index condition | **30ms** |

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
SELECT * FROM products WHERE brand_id = 52 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
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
| 브랜드(인기) + 좋아요순 | 59ms | 207ms | 1ms | 1ms |
| 브랜드(인기) + 가격순 | 60ms | 189ms | 160ms | 1ms |
| 브랜드(인기) + 최신순 | 65ms | 188ms | 160ms | 1ms |
| 브랜드(중간) + 좋아요순 | 61ms | 2ms | 1ms | 2ms |
| 전체 + 좋아요순 | 65ms | 68ms | 74ms | 2ms |
| 전체 + 가격순 | 65ms | 75ms | 73ms | 2ms |
| COUNT(브랜드 인기) | 31ms | 221ms | 7ms | 10ms |
| COUNT(전체) | 28ms | 29ms | 29ms | 30ms |
| 딥페이징: 좋아요순 OFFSET 10000 | 104ms | 131ms | 130ms | 61ms |
| 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | 64ms | 205ms | 26ms | 30ms |
