# 2. 인덱스 설계 및 적용 결과

## 복합 인덱스 컬럼 순서 원칙

복합 인덱스의 컬럼 순서에는 원칙이 있다.

```
1. 동등 조건(=)이 먼저    → brand_id = ?
2. 범위/NULL 조건이 다음   → deleted_at IS NULL
3. 정렬 컬럼이 마지막      → ORDER BY price ASC
```

**왜 이 순서인가?**

- **동등 조건을 앞에 두면** 인덱스에서 해당 값으로 바로 점프할 수 있다. `brand_id = 1`이면 5,000만건 → 250만건으로 범위가 줄어든다.
- **범위/NULL 조건은 그 다음** 좁혀진 범위 내에서 추가 필터링한다.
- **정렬 컬럼을 마지막에 두면** 인덱스 자체가 이미 정렬되어 있으므로 filesort가 발생하지 않는다.

순서를 반대로 하면(정렬 → 범위 → 동등) 인덱스가 있어도 filesort가 발생할 수 있다.

---

## 설계한 인덱스 3개

각 API의 WHERE + ORDER BY 조합에 맞춰 설계했다.

### 1. 최신순 조회용

```sql
CREATE INDEX idx_products_deleted_created
  ON products(deleted_at, created_at DESC);
```

| 컬럼 | 역할 | 쿼리 |
|------|------|------|
| `deleted_at` | 범위 조건 | `WHERE deleted_at IS NULL` |
| `created_at DESC` | 정렬 | `ORDER BY created_at DESC` |

동등 조건이 없어서 범위 조건부터 시작한다.

### 2. 브랜드별 가격순 조회용

```sql
CREATE INDEX idx_products_brand_deleted_price
  ON products(brand_id, deleted_at, price);
```

| 컬럼 | 역할 | 쿼리 |
|------|------|------|
| `brand_id` | 동등 조건 | `WHERE brand_id = ?` |
| `deleted_at` | 범위 조건 | `AND deleted_at IS NULL` |
| `price` | 정렬 | `ORDER BY price ASC` |

동등 → 범위 → 정렬 원칙을 가장 잘 따르는 인덱스다.

### 3. 좋아요순 조회용

```sql
CREATE INDEX idx_products_deleted_likes
  ON products(deleted_at, likes_count DESC);
```

| 컬럼 | 역할 | 쿼리 |
|------|------|------|
| `deleted_at` | 범위 조건 | `WHERE deleted_at IS NULL` |
| `likes_count DESC` | 정렬 | `ORDER BY likes_count DESC` |

최신순과 동일한 구조. 정렬 컬럼만 다르다.

---

## 인덱스 적용 후 EXPLAIN 비교

3개 쿼리 모두 깨끗하게 바뀌었다.

```
Before                              After
type:  ALL (전체 스캔)        →     type:  ref (인덱스 조회)
key:   NULL                  →     key:   idx_products_xxx
Extra: Using filesort        →     Extra: Using index condition
```

**filesort가 사라졌다.** 인덱스에 이미 정렬이 되어있으니 별도 정렬이 필요 없다.

---

## 인덱스 적용 후 측정 결과

| API | Before | After | 개선율 |
|-----|--------|-------|--------|
| 상품 최신순 | 23.86초 | **~4.8초** | 80% |
| 브랜드별 가격순 | 22.81초 | **0.31초** | **98%** |
| 좋아요순 | 22.70초 | **~4.6초** | 80% |

### 브랜드별 가격순만 98% 개선된 이유

`brand_id = 1`이라는 **동등 조건(=)**이 있기 때문이다.

- 5,000만건 중 `brand_id = 1`인 건 약 250만건
- 인덱스에서 250만건으로 먼저 좁히고, 그 안에서 정렬된 20건만 읽으면 끝

나머지 2개(최신순, 좋아요순)는 `deleted_at IS NULL`만으로 시작하는데, 테스트 데이터가 전부 삭제되지 않은 상태라서 범위를 좁히는 효과가 거의 없었다. 인덱스를 타긴 하지만 범위가 좁혀지지 않아 테이블 I/O가 여전히 발생한다.

---

## 인덱스만으로 충분한가?

| API | After | 판단 |
|-----|-------|------|
| 브랜드별 가격순 | 0.31초 | 충분 |
| 상품 최신순 | 4.8초 | 부족 |
| 좋아요순 | 4.6초 | 부족 |

인덱스로 Full Table Scan + filesort는 제거했지만, 최신순과 좋아요순은 여전히 5초 수준이다.

DB 쿼리를 아무리 빠르게 해도 DB를 치는 것 자체가 비용이다. **DB를 빠르게 치는 게 아니라 DB를 아예 안 치면** 더 줄일 수 있다.

→ 캐시 적용이 필요한 지점이다.
