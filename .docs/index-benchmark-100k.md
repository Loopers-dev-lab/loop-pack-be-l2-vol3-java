# 📊 상품 조회 인덱스 벤치마크

> 실행일시: 2026-03-09 23:36:02  
> MySQL 8.0 (TestContainers)

## 📋 테스트 환경

| 항목 | 값 |
|---|---|
| 데이터 수 | 100,000건 |
| 브랜드 수 | 100개 (Zipf 분포) |
| 좋아요 분포 | Log-normal (중앙값 ~12, 롱테일) |
| 측정 횟수 | 3회 평균 |
| 인기 브랜드 ID | 1 (19,045건) |
| 중간 브랜드 ID | 49 (384건) |

### 브랜드별 상품 수 (상위 10개)

| 순위 | 브랜드 ID | 상품 수 | 비율 |
|---|---|---|---|
| 1 | 1 | 19,045 | 19.0% |
| 2 | 2 | 9,549 | 9.5% |
| 3 | 3 | 6,568 | 6.6% |
| 4 | 4 | 4,869 | 4.9% |
| 5 | 5 | 3,915 | 3.9% |
| 6 | 6 | 3,324 | 3.3% |
| 7 | 7 | 2,598 | 2.6% |
| 8 | 8 | 2,480 | 2.5% |
| 9 | 9 | 2,109 | 2.1% |
| 10 | 10 | 1,911 | 1.9% |

### 좋아요 수 분포

| 구간 | 상품 수 | 비율 |
|---|---|---|
| 0~10 | 48,093 | 48.1% |
| 11~50 | 28,237 | 28.2% |
| 51~200 | 15,560 | 15.6% |
| 201~1,000 | 6,775 | 6.8% |
| 1,001~5,000 | 1,213 | 1.2% |
| 5,001+ | 122 | 0.1% |

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
| 1 | 브랜드(인기) + 좋아요순 | ALL | NULL | NULL | 99642 | 1.0 | Using where; Using filesort | **28ms** |
| 2 | 브랜드(인기) + 가격순 | ALL | NULL | NULL | 99642 | 1.0 | Using where; Using filesort | **28ms** |
| 3 | 브랜드(인기) + 최신순 | ALL | NULL | NULL | 99642 | 1.0 | Using where; Using filesort | **28ms** |
| 4 | 브랜드(중간) + 좋아요순 | ALL | NULL | NULL | 99642 | 1.0 | Using where; Using filesort | **26ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **32ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **41ms** |
| 7 | COUNT(브랜드 인기) | ALL | NULL | NULL | 99642 | 1.0 | Using where | **15ms** |
| 8 | COUNT(전체) | ALL | NULL | NULL | 99642 | 10.0 | Using where | **12ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **56ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ALL | NULL | NULL | 99642 | 1.0 | Using where; Using filesort | **33ms** |

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
SELECT * FROM products WHERE brand_id = 49 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_brand_id | idx_brand_id | 37050 | 10.0 | Using where; Using filesort | **18ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_brand_id | idx_brand_id | 37050 | 10.0 | Using where; Using filesort | **18ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_brand_id | idx_brand_id | 37050 | 10.0 | Using where; Using filesort | **35ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_brand_id | idx_brand_id | 384 | 10.0 | Using where; Using filesort | **1ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **31ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **30ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_brand_id | idx_brand_id | 37050 | 10.0 | Using where | **14ms** |
| 8 | COUNT(전체) | ALL | NULL | NULL | 99642 | 10.0 | Using where | **12ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **54ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_brand_id | idx_brand_id | 37050 | 10.0 | Using where; Using filesort | **21ms** |

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
SELECT * FROM products WHERE brand_id = 49 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 37026 | 100.0 | Using index condition | **0ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 37026 | 100.0 | Using index condition; Using filesort | **22ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 37026 | 100.0 | Using index condition; Using filesort | **21ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 384 | 100.0 | Using index condition | **1ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **31ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **30ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_brand_deleted_like | idx_brand_deleted_like | 37026 | 100.0 | Using where; Using index | **3ms** |
| 8 | COUNT(전체) | index | idx_brand_deleted_like | idx_brand_deleted_like | 99642 | 10.0 | Using where; Using index | **9ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 99642 | 10.0 | Using where; Using filesort | **55ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 37026 | 100.0 | Using index condition | **6ms** |

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
SELECT * FROM products WHERE brand_id = 49 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 37026 | 100.0 | Using index condition | **0ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_price | 37026 | 100.0 | Using index condition | **1ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_created | 37026 | 100.0 | Using index condition | **1ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 384 | 100.0 | Using index condition | **0ms** |
| 5 | 전체 + 좋아요순 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_like | 49821 | 100.0 | Using index condition | **1ms** |
| 6 | 전체 + 가격순 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_price | 49821 | 100.0 | Using index condition | **0ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 37026 | 100.0 | Using where; Using index | **2ms** |
| 8 | COUNT(전체) | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_deleted_like | 49821 | 100.0 | Using where; Using index | **10ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_like | 49821 | 100.0 | Using index condition | **28ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 37026 | 100.0 | Using index condition | **8ms** |

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
SELECT * FROM products WHERE brand_id = 49 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
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
| 브랜드(인기) + 좋아요순 | 28ms | 18ms | 0ms | 0ms |
| 브랜드(인기) + 가격순 | 28ms | 18ms | 22ms | 1ms |
| 브랜드(인기) + 최신순 | 28ms | 35ms | 21ms | 1ms |
| 브랜드(중간) + 좋아요순 | 26ms | 1ms | 1ms | 0ms |
| 전체 + 좋아요순 | 32ms | 31ms | 31ms | 1ms |
| 전체 + 가격순 | 41ms | 30ms | 30ms | 0ms |
| COUNT(브랜드 인기) | 15ms | 14ms | 3ms | 2ms |
| COUNT(전체) | 12ms | 12ms | 9ms | 10ms |
| 딥페이징: 좋아요순 OFFSET 10000 | 56ms | 54ms | 55ms | 28ms |
| 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | 33ms | 21ms | 6ms | 8ms |
