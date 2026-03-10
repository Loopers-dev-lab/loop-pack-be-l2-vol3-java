# 📊 상품 조회 인덱스 벤치마크

> 실행일시: 2026-03-09 23:44:04  
> MySQL 8.0 (TestContainers)

## 📋 테스트 환경

| 항목 | 값 |
|---|---|
| 데이터 수 | 1,000,000건 |
| 브랜드 수 | 100개 (Zipf 분포) |
| 좋아요 분포 | Log-normal (중앙값 ~12, 롱테일) |
| 측정 횟수 | 3회 평균 |
| 인기 브랜드 ID | 1 (192,381건) |
| 중간 브랜드 ID | 51 (3,796건) |

### 브랜드별 상품 수 (상위 10개)

| 순위 | 브랜드 ID | 상품 수 | 비율 |
|---|---|---|---|
| 1 | 1 | 192,381 | 19.2% |
| 2 | 2 | 95,894 | 9.6% |
| 3 | 3 | 64,495 | 6.4% |
| 4 | 4 | 48,220 | 4.8% |
| 5 | 5 | 38,807 | 3.9% |
| 6 | 6 | 32,031 | 3.2% |
| 7 | 7 | 27,455 | 2.7% |
| 8 | 8 | 24,185 | 2.4% |
| 9 | 9 | 21,253 | 2.1% |
| 10 | 10 | 19,345 | 1.9% |

### 좋아요 수 분포

| 구간 | 상품 수 | 비율 |
|---|---|---|
| 0~10 | 479,535 | 48.0% |
| 11~50 | 283,415 | 28.3% |
| 51~200 | 156,275 | 15.6% |
| 201~1,000 | 66,948 | 6.7% |
| 1,001~5,000 | 12,474 | 1.2% |
| 5,001+ | 1,353 | 0.1% |

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
| 1 | 브랜드(인기) + 좋아요순 | ALL | NULL | NULL | 995153 | 1.0 | Using where; Using filesort | **332ms** |
| 2 | 브랜드(인기) + 가격순 | ALL | NULL | NULL | 995153 | 1.0 | Using where; Using filesort | **347ms** |
| 3 | 브랜드(인기) + 최신순 | ALL | NULL | NULL | 995153 | 1.0 | Using where; Using filesort | **333ms** |
| 4 | 브랜드(중간) + 좋아요순 | ALL | NULL | NULL | 995153 | 1.0 | Using where; Using filesort | **302ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 995153 | 10.0 | Using where; Using filesort | **351ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 995153 | 10.0 | Using where; Using filesort | **342ms** |
| 7 | COUNT(브랜드 인기) | ALL | NULL | NULL | 995153 | 1.0 | Using where | **182ms** |
| 8 | COUNT(전체) | ALL | NULL | NULL | 995153 | 10.0 | Using where | **156ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 995153 | 10.0 | Using where; Using filesort | **620ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ALL | NULL | NULL | 995153 | 1.0 | Using where; Using filesort | **394ms** |

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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_brand_id | idx_brand_id | 412896 | 10.0 | Using where; Using filesort | **1727ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_brand_id | idx_brand_id | 412896 | 10.0 | Using where; Using filesort | **1711ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_brand_id | idx_brand_id | 412896 | 10.0 | Using where; Using filesort | **1902ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_brand_id | idx_brand_id | 3796 | 10.0 | Using where; Using filesort | **22ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 995153 | 10.0 | Using where; Using filesort | **366ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 995153 | 10.0 | Using where; Using filesort | **375ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_brand_id | idx_brand_id | 412896 | 10.0 | Using where | **1939ms** |
| 8 | COUNT(전체) | ALL | NULL | NULL | 995153 | 10.0 | Using where | **181ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 995153 | 10.0 | Using where; Using filesort | **606ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_brand_id | idx_brand_id | 412896 | 10.0 | Using where; Using filesort | **1678ms** |

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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 414398 | 100.0 | Using index condition | **1ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 414398 | 100.0 | Using index condition; Using filesort | **1218ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 414398 | 100.0 | Using index condition; Using filesort | **1269ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 3796 | 100.0 | Using index condition | **1ms** |
| 5 | 전체 + 좋아요순 | ALL | NULL | NULL | 995887 | 10.0 | Using where; Using filesort | **364ms** |
| 6 | 전체 + 가격순 | ALL | NULL | NULL | 995887 | 10.0 | Using where; Using filesort | **535ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_brand_deleted_like | idx_brand_deleted_like | 414398 | 100.0 | Using where; Using index | **27ms** |
| 8 | COUNT(전체) | index | idx_brand_deleted_like | idx_brand_deleted_like | 995887 | 10.0 | Using where; Using index | **149ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ALL | NULL | NULL | 995887 | 10.0 | Using where; Using filesort | **863ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_brand_deleted_like | idx_brand_deleted_like | 414398 | 100.0 | Using index condition | **49ms** |

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
| 1 | 브랜드(인기) + 좋아요순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 406656 | 100.0 | Using index condition | **0ms** |
| 2 | 브랜드(인기) + 가격순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_price | 406656 | 100.0 | Using index condition | **1ms** |
| 3 | 브랜드(인기) + 최신순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_created | 406656 | 100.0 | Using index condition | **0ms** |
| 4 | 브랜드(중간) + 좋아요순 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 3796 | 100.0 | Using index condition | **0ms** |
| 5 | 전체 + 좋아요순 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_like | 497576 | 100.0 | Using index condition | **0ms** |
| 6 | 전체 + 가격순 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_price | 497576 | 100.0 | Using index condition | **0ms** |
| 7 | COUNT(브랜드 인기) | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_created | 406656 | 100.0 | Using where; Using index | **30ms** |
| 8 | COUNT(전체) | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_deleted_like | 497576 | 100.0 | Using where; Using index | **119ms** |
| 9 | 딥페이징: 좋아요순 OFFSET 10000 | ref | idx_prod_deleted_like,idx_prod... | idx_prod_deleted_like | 497576 | 100.0 | Using index condition | **90ms** |
| 10 | 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | ref | idx_prod_brand_like,idx_prod_b... | idx_prod_brand_like | 406656 | 100.0 | Using index condition | **43ms** |

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
| 브랜드(인기) + 좋아요순 | 332ms | 1727ms | 1ms | 0ms |
| 브랜드(인기) + 가격순 | 347ms | 1711ms | 1218ms | 1ms |
| 브랜드(인기) + 최신순 | 333ms | 1902ms | 1269ms | 0ms |
| 브랜드(중간) + 좋아요순 | 302ms | 22ms | 1ms | 0ms |
| 전체 + 좋아요순 | 351ms | 366ms | 364ms | 0ms |
| 전체 + 가격순 | 342ms | 375ms | 535ms | 0ms |
| COUNT(브랜드 인기) | 182ms | 1939ms | 27ms | 30ms |
| COUNT(전체) | 156ms | 181ms | 149ms | 119ms |
| 딥페이징: 좋아요순 OFFSET 10000 | 620ms | 606ms | 863ms | 90ms |
| 딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000 | 394ms | 1678ms | 49ms | 43ms |
