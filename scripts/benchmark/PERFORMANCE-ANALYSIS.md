# 인덱스 성능 분석

## 테스트 환경

- MySQL 8.0 InnoDB
- 상품 수: 10만 / 20만 / 50만 개
- 50개 브랜드에 균등 분배
- like_count: Power Law 분포 (상위 1%가 1000~10000)

## 인덱스 정의

```sql
CREATE INDEX idx_products_like_count ON products (like_count DESC, created_at DESC);
CREATE INDEX idx_products_brand_id ON products (brand_id);
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC);
CREATE INDEX idx_products_brand_price ON products (brand_id, price ASC);
```

---

## 예상 성능 비교

### Test 1: 좋아요 순 정렬 (전체)

```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

| 상품 수 | 인덱스 없음 | 인덱스 있음 | 개선율 |
|---------|-------------|-------------|--------|
| 10만 | ~150ms | ~1ms | **150x** |
| 20만 | ~300ms | ~1ms | **300x** |
| 50만 | ~800ms | ~1ms | **800x** |

**분석:**
- 인덱스 없음: Full Table Scan + Filesort (O(n log n))
- 인덱스 있음: Index Scan (O(limit))

**EXPLAIN 비교:**
```
-- Without Index
type: ALL, rows: 500000, Extra: Using where; Using filesort

-- With Index
type: index, key: idx_products_like_count, rows: 20
```

---

### Test 2: 브랜드별 조회

```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL LIMIT 20
```

| 상품 수 | 인덱스 없음 | 인덱스 있음 | 개선율 |
|---------|-------------|-------------|--------|
| 10만 | ~80ms | ~2ms | **40x** |
| 20만 | ~160ms | ~2ms | **80x** |
| 50만 | ~400ms | ~2ms | **200x** |

**분석:**
- 인덱스 없음: Full Table Scan (O(n))
- 인덱스 있음: Index Lookup (O(log n + limit))
- 브랜드당 상품 수: 2000~4000개

---

### Test 3: 브랜드별 + 좋아요 순

```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20
```

| 상품 수 | 인덱스 없음 | 인덱스 있음 | 개선율 |
|---------|-------------|-------------|--------|
| 10만 | ~100ms | ~2ms | **50x** |
| 20만 | ~200ms | ~2ms | **100x** |
| 50만 | ~500ms | ~2ms | **250x** |

**분석:**
- 인덱스 없음: Index on brand_id + Filesort
- 인덱스 있음: Covering Index (brand_id, like_count)
- **Filesort 제거**가 핵심

**EXPLAIN 비교:**
```
-- Without idx_products_brand_like (only brand_id index)
type: ref, key: idx_products_brand_id, Extra: Using where; Using filesort

-- With idx_products_brand_like
type: ref, key: idx_products_brand_like, rows: 20 (no filesort!)
```

---

### Test 4: 브랜드별 + 가격순

```sql
SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20
```

| 상품 수 | 인덱스 없음 | 인덱스 있음 | 개선율 |
|---------|-------------|-------------|--------|
| 10만 | ~100ms | ~2ms | **50x** |
| 20만 | ~200ms | ~2ms | **100x** |
| 50만 | ~500ms | ~2ms | **250x** |

---

### Test 5: 깊은 페이지네이션

```sql
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 10000
```

| 상품 수 | 인덱스 없음 | 인덱스 있음 | 개선율 |
|---------|-------------|-------------|--------|
| 10만 | ~200ms | ~50ms | **4x** |
| 20만 | ~400ms | ~50ms | **8x** |
| 50만 | ~1000ms | ~50ms | **20x** |

**주의:** OFFSET이 크면 인덱스 있어도 성능 저하
- **권장:** Keyset Pagination (WHERE id > last_id)

---

### Test 6: COUNT 쿼리

```sql
SELECT COUNT(*) FROM products WHERE brand_id = 1 AND deleted_at IS NULL
```

| 상품 수 | 인덱스 없음 | 인덱스 있음 | 개선율 |
|---------|-------------|-------------|--------|
| 10만 | ~80ms | ~5ms | **16x** |
| 20만 | ~160ms | ~5ms | **32x** |
| 50만 | ~400ms | ~5ms | **80x** |

---

## 인덱스 크기 영향

| 상품 수 | 테이블 크기 | 인덱스 크기 (4개) | 총 크기 |
|---------|-------------|-------------------|---------|
| 10만 | ~50MB | ~15MB | ~65MB |
| 20만 | ~100MB | ~30MB | ~130MB |
| 50만 | ~250MB | ~75MB | ~325MB |

---

## 권장사항

### 1. 필수 인덱스 (3개)

```sql
-- 좋아요 순 정렬용 (가장 중요)
CREATE INDEX idx_products_like_count ON products (like_count DESC, created_at DESC);

-- 브랜드별 + 좋아요 순 복합 인덱스
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC);

-- 브랜드별 + 가격순 복합 인덱스
CREATE INDEX idx_products_brand_price ON products (brand_id, price ASC);
```

### 2. 제거 가능 인덱스

```sql
-- brand_id 단독 인덱스 (복합 인덱스로 커버됨)
-- idx_products_brand_id는 idx_products_brand_like가 대체
```

### 3. 깊은 페이지네이션 최적화

```sql
-- OFFSET 대신 Keyset Pagination
SELECT * FROM products
WHERE deleted_at IS NULL
  AND (like_count, created_at, id) < (?, ?, ?)  -- 마지막 행 기준
ORDER BY like_count DESC, created_at DESC, id DESC
LIMIT 20
```

---

## 실행 방법

```bash
# Docker MySQL 실행
docker-compose -f docker/docker-compose.yml up -d mysql

# 벤치마크 실행
chmod +x scripts/benchmark/run-benchmark.sh
./scripts/benchmark/run-benchmark.sh
```
