# 1. 인덱스 적용 전 슬로우 쿼리 분석

## 테스트 환경

| 항목 | 값 |
|------|-----|
| DB | MySQL 8.0 (Docker) |
| 상품(products) | **5,000만건** |
| 인덱스 | **없음** (PK만 존재) |
| 애플리케이션 | Spring Boot 3.4.4 + JPA |

인덱스 효과를 체감하려면 데이터가 많아야 한다. MySQL 옵티마이저는 데이터가 적으면 인덱스를 무시하고 Full Scan을 선택하기 때문에 5,000만건을 넣었다.

---

## 슬로우 쿼리 발생 대상 API

Controller → Facade → Service → Repository 흐름을 추적하여 실제 DB에 실행되는 쿼리를 식별했다.

### 1. 상품 최신순 조회 — 23.86초

```
GET /api/v1/products?sort=latest&page=0&size=20
```

**실행되는 SQL**
```sql
SELECT * FROM products
WHERE deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 20;
```

**EXPLAIN 분석**
```
type:  ALL               ← 전체 테이블 스캔
key:   NULL              ← 사용 가능한 인덱스 없음
rows:  49,756,822        ← 5,000만 행 전부 스캔
Extra: Using where; Using filesort  ← 필터 후 별도 정렬
```

20건만 요청했는데 **23.86초**. LIMIT 20이 의미가 없다.

### 2. 브랜드별 + 가격순 조회 — 22.81초

```
GET /api/v1/products?brandId=1&sort=price_asc&page=0&size=20
```

**실행되는 SQL**
```sql
SELECT * FROM products
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY price ASC
LIMIT 20;
```

**EXPLAIN 분석**
```
type:  ALL
key:   NULL
rows:  49,756,822
Extra: Using where; Using filesort
```

`brand_id = 1`인 행이 약 250만건이지만, 인덱스가 없어서 5,000만건 전체를 스캔해서 찾는다. **22.81초**.

### 3. 좋아요순 조회 — 22.70초

```
GET /api/v1/products?sort=likes_desc&page=0&size=20
```

**실행되는 SQL**
```sql
SELECT * FROM products
WHERE deleted_at IS NULL
ORDER BY likes_count DESC
LIMIT 20;
```

**EXPLAIN 분석**
```
type:  ALL
key:   NULL
rows:  49,756,822
Extra: Using where; Using filesort
```

쿼리 1과 동일한 구조. 정렬 기준만 `likes_count`로 다를 뿐 전건 스캔 + filesort는 같다. **22.70초**.

---

## 왜 LIMIT 20인데 20초가 걸리는가

3개 쿼리 모두 EXPLAIN 결과가 동일하다:

```
type:  ALL    → 테이블 전체 스캔
key:   NULL   → 인덱스 미사용
Extra: Using filesort → 메모리/디스크 정렬
```

인덱스가 없으면 MySQL은 "어떤 행이 조건에 맞고, 어떤 순서가 맞는지"를 모른다. 그래서:

1. 5,000만건을 **전부 읽는다** (Full Table Scan)
2. `WHERE` 조건으로 **필터링**한다
3. `ORDER BY`로 **정렬**한다 (filesort)
4. 그제서야 상위 20건을 **반환**한다

**LIMIT은 마지막에 적용된다.** 정렬이 끝나야 "상위 20건"을 알 수 있기 때문이다.

---

## 측정 결과 요약

| API | SQL 직접 실행 | API 응답 | 원인 |
|-----|------------|----------|------|
| 상품 최신순 | 14.60초 | **23.86초** | Full Scan + filesort |
| 브랜드별 가격순 | 12.84초 | **22.81초** | Full Scan + filesort |
| 좋아요순 | 14.01초 | **22.70초** | Full Scan + filesort |

SQL과 API 사이의 약 9초 차이는 JPA 영속성 컨텍스트, Entity → DTO 변환, JSON 직렬화 비용이다.

**DB 쿼리가 느리면 애플리케이션 전체가 느려진다.**
