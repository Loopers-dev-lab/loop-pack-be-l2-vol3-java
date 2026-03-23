# 상품 조회 인덱스 성능 테스트

## 1. 문제 인식

### 현재 상황

상품 목록 API에서 **브랜드 필터 + 좋아요 순 정렬** 기능을 제공하고 있다.

```sql
SELECT * FROM product
WHERE ref_brand_id = ?
ORDER BY like_count DESC
LIMIT 10;
```

### 의문점

현재 `product` 테이블에 PRIMARY KEY만 있는 상태에서 EXPLAIN을 실행해봤다.

```
type: ALL (풀 테이블 스캔)
rows: 99,694
Extra: Using where; Using filesort
```

**10만 개 전체를 스캔하고 있다.** 인덱스가 필요해 보인다.

---

## 2. 어떤 인덱스를 추가해야 할까?

### 후보 인덱스

| 인덱스 | 이유 |
|--------|------|
| `ref_brand_id` | WHERE 조건에 사용됨 |
| `like_count DESC` | ORDER BY에 사용됨 |
| `(ref_brand_id, like_count DESC)` | 둘 다 커버 |

### 첫 번째 질문: 무조건 복합 인덱스가 좋을까?

복합 인덱스가 WHERE + ORDER BY를 모두 커버하니까 무조건 좋을 것 같았다.

하지만 고민이 생겼다:
- 복합 인덱스는 크기가 더 크다
- 쓰기 성능에 영향을 줄 수 있다
- 단일 인덱스로 충분하다면 굳이?

**→ 실제로 테스트해서 비교해보자.**

### 두 번째 질문: 데이터 분포가 중요할까?

"브랜드당 상품 수"가 다르면 인덱스 효과가 달라질까?

| 상황 | 예시 |
|------|------|
| 대형 브랜드 소수 | 나이키, 아디다스만 있고 각각 상품 1만개 |
| 소형 브랜드 다수 | 셀러 1000명이 각각 상품 100개씩 |

**→ 데이터 분포별로 테스트해보자.**

---

## 3. 테스트 설계

### 테스트 환경
- MySQL 8.x (Docker)
- 총 상품 수: 100,000개

### 테스트 케이스

**데이터 분포:**

| 케이스 | 브랜드 수 | 브랜드당 상품 | 시나리오 |
|-------|---------|-------------|---------|
| A | 10 | 10,000 | 대형 브랜드 소수 |
| B | 100 | 1,000 | 중형 브랜드 |
| C | 1,000 | 100 | 소형 브랜드 다수 |

**인덱스 조합:**
- 좋아요순: 4가지 (없음, ref_brand_id, like_count, 복합)
- 최신순: 3가지 (없음, created_at, 복합)

**쿼리 패턴:**
- Q1: 브랜드 필터 + 좋아요순
- Q2: 전체 좋아요순 (브랜드 필터 없음)
- Q3: 브랜드 필터 + 최신순
- Q4: 전체 최신순 (브랜드 필터 없음)

왜 Q2, Q4도 테스트할까? → API에서 `brandId`가 선택적 파라미터라서 둘 다 사용될 수 있다.

---

## 4. 테스트 결과

### Q1: 브랜드 필터 + 좋아요순 (단위: ms)

| 인덱스 | 케이스A (10브랜드) | 케이스B (100브랜드) | 케이스C (1000브랜드) |
|--------|-------------------|--------------------|--------------------|
| 없음 | 29.2 | 20.4 | 27.3 |
| ref_brand_id | 21.8 | 1.01 | **0.17** |
| like_count | **0.28** | 0.58 | 6.27 |
| 복합 | **0.21** | **0.37** | **0.02** |

### Q2: 전체 좋아요순 (단위: ms)

| 인덱스 | 케이스A | 케이스B | 케이스C |
|--------|--------|--------|--------|
| 없음 | 33.0 | 34.7 | 30.7 |
| ref_brand_id | 32.1 | 29.6 | 30.1 |
| like_count | **0.05** | **0.05** | **0.05** |
| 복합 | 33.6 | 21.4 | 33.2 |

### Q3: 브랜드 필터 + 최신순 (단위: ms)

| 인덱스 | 결과 | 비고 |
|--------|------|------|
| 없음 | 0.31 | brand 인덱스 사용 + filesort |
| created_at | 0.27 | brand 인덱스 사용 + filesort |
| 복합 (brand, created_at) | **0.04** ✅ | filesort 없음 |

### Q4: 전체 최신순 (단위: ms)

| 인덱스 | 결과 | 비고 |
|--------|------|------|
| 없음 | 22.2 | 풀 테이블 스캔 |
| created_at | **0.05** ✅ | 인덱스 스캔 |
| 복합 (brand, created_at) | 0.03 | created_at 인덱스 사용 |

---

## 5. 예상과 다른 결과들

### 발견 1: 데이터 분포에 따라 최적 인덱스가 달라진다

**케이스 A (브랜드 10개, 브랜드당 10,000개)**
- `like_count` 인덱스: 0.28ms ✅
- `ref_brand_id` 인덱스: 21.8ms ❌

**케이스 C (브랜드 1000개, 브랜드당 100개)**
- `like_count` 인덱스: 6.27ms ❌
- `ref_brand_id` 인덱스: 0.17ms ✅

**왜 이런 차이가 날까?**

`like_count` 인덱스 동작 방식:
```
좋아요 순으로 스캔하면서 해당 브랜드 찾기
```

- 브랜드당 상품 많음 (10,000개) → 금방 찾음 (92개 스캔)
- 브랜드당 상품 적음 (100개) → 오래 걸림 (10,768개 스캔!)

**EXPLAIN ANALYZE 결과:**

케이스 A - like_count 인덱스:
```
Index scan by like_count (92 rows)
  → Filter by brand_id (10 rows)
    → Limit 10
⏱️ 0.28ms
```

케이스 C - like_count 인덱스:
```
Index scan by like_count (10,768 rows)  ← 10개 찾으려고 10,768개 스캔!
  → Filter by brand_id (10 rows)
    → Limit 10
⏱️ 6.27ms
```

### 발견 2: 복합 인덱스는 데이터 분포와 무관하게 안정적

| 케이스 | 복합 인덱스 |
|-------|-----------|
| A | 0.21ms |
| B | 0.37ms |
| C | 0.02ms |

모든 케이스에서 0.02 ~ 0.37ms로 안정적이다.

```
Index lookup by brand_id + like_count (10 rows)
  → Limit 10
⏱️ 0.02ms
```

인덱스에서 바로 10개만 가져온다. 필터링이나 정렬이 필요 없다.

### 발견 3: 복합 인덱스는 Q2에 사용 불가

Q2 (전체 좋아요순)에서 복합 인덱스가 있어도:
```
Table scan (100,000 rows)
  → Sort by like_count
    → Limit 10
⏱️ 33.6ms
```

복합 인덱스 `(ref_brand_id, like_count)`는 `ref_brand_id` 조건이 없으면 사용할 수 없다.

### 발견 4: 최신순도 동일한 패턴

Q3, Q4 테스트 결과 최신순(created_at) 정렬도 좋아요순과 동일한 패턴을 보인다.

**Q3 (브랜드 필터 + 최신순)**

인덱스 없을 때:
```
Index lookup by brand_id (100 rows)
  → Sort by created_at DESC
    → Limit 10
⏱️ 0.31ms
```

복합 인덱스 `(brand_id, created_at DESC)` 적용 후:
```
Index lookup by brand_id + created_at (10 rows)
  → Limit 10
⏱️ 0.04ms
```

**Q4 (전체 최신순)**

인덱스 없을 때:
```
Table scan (100,000 rows)
  → Sort by created_at DESC
    → Limit 10
⏱️ 22.2ms
```

`created_at` 단일 인덱스 적용 후:
```
Index scan by created_at (10 rows)
  → Limit 10
⏱️ 0.05ms
```

**결론**: 좋아요순과 마찬가지로 최신순도 복합 인덱스 + 단일 인덱스 조합이 필요하다.

---

## 6. 추가 질문: 페이지네이션은 괜찮을까?

인덱스가 있으면 깊은 페이지(OFFSET이 큰 경우)도 빠를까?

### 테스트

```sql
SELECT * FROM product ORDER BY like_count DESC LIMIT 10 OFFSET ?;
```

### 결과

| OFFSET | 페이지 | 인덱스 없음 | like_count 인덱스 |
|--------|-------|-----------|------------------|
| 0 | 1 | 28.1ms | **0.27ms** ✅ |
| 1,000 | 101 | 34.0ms | 33.9ms ❌ |
| 10,000 | 1,001 | 34.8ms | 42.6ms ❌ |

### 예상과 다른 결과

OFFSET이 커지면 **인덱스가 있어도 풀 스캔**을 한다!

OFFSET 0:
```
Index scan by like_count (10 rows)
  → Limit 10
⏱️ 0.27ms
```

OFFSET 1000:
```
Table scan (100,000 rows)
  → Sort by like_count (1,010 rows)
    → Skip 1,000 rows
      → Limit 10
⏱️ 33.9ms
```

**왜?** MySQL 옵티마이저가 "어차피 1000개 건너뛰려면 많이 읽어야 하니까 풀 스캔이 낫다"고 판단한다.

### 결론

**인덱스로는 깊은 페이지네이션 문제를 해결할 수 없다.**

대안:
- 커서 기반 페이지네이션: `WHERE like_count < ? LIMIT 10`
- 페이지 수 제한: 최대 100페이지까지만 허용

---

## 7. 추가 질문: 쓰기 성능은 괜찮을까?

인덱스를 추가하면 좋아요 증가(UPDATE) 시 느려지지 않을까?

### 테스트

```sql
UPDATE product SET like_count = like_count + 1 WHERE id = ?;
-- 1000회 반복
```

### 결과

| 인덱스 | 1000회 UPDATE | 평균/회 | 성능 저하 |
|-------|--------------|--------|----------|
| 없음 | 604ms | 0.60ms | - |
| like_count | 689ms | 0.69ms | **14% 느림** |
| like_count + 복합 | 752ms | 0.75ms | **25% 느림** |

### 분석

인덱스 없음:
```
UPDATE data
⏱️ 0.60ms/회
```

인덱스 2개:
```
UPDATE data
  → UPDATE idx_like_count (인덱스 재정렬)
  → UPDATE idx_brand_like (인덱스 재정렬)
⏱️ 0.75ms/회
```

### 결론

- 인덱스 2개 추가 시 쓰기 25% 느려짐
- 일반적인 서비스에서는 감수할 만한 수준
- 초당 수천 건의 좋아요가 발생하면 Redis 캐시나 비동기 처리 고려 필요

---

## 8. 최종 결론

### 질문에 대한 답

| 질문 | 답 |
|------|-----|
| 무조건 복합 인덱스가 좋을까? | Q1에는 최적이지만, Q2에는 사용 불가 |
| 데이터 분포가 중요할까? | 단일 인덱스 선택 시 매우 중요, 복합 인덱스는 무관 |
| 페이지네이션도 빨라질까? | 첫 페이지만 빠름, 깊은 페이지는 인덱스 무용지물 |
| 쓰기 성능은 괜찮을까? | 25% 느려지지만 감수할 만함 |

### 인덱스 선택

| 쿼리 패턴 | 추천 인덱스 |
|----------|------------|
| Q1만 사용 | `(ref_brand_id, like_count DESC)` 복합 |
| Q2만 사용 | `(like_count DESC)` 단일 |
| 둘 다 사용 | **두 인덱스 모두 필요** |

### 적용할 인덱스

처음에는 좋아요순만 고려해서 2개 인덱스를 설계했다:

```sql
-- Q1 커버: 브랜드 필터 + 좋아요순
CREATE INDEX idx_product_brand_like ON product(ref_brand_id, like_count DESC);

-- Q2 커버: 전체 좋아요순
CREATE INDEX idx_product_like ON product(like_count DESC);
```

### 추가 인덱스가 필요한가?

잠깐, API를 다시 확인해보자.

```java
@RequestParam(defaultValue = "LATEST") ProductSortType sortType
```

**기본 정렬이 최신순(LATEST)이다!** 그리고 정렬 옵션이 3가지가 있다:

```java
case LATEST -> productEntity.createdAt.desc();    // 최신순
case PRICE_ASC -> productEntity.price.asc();      // 가격순
case LIKES_DESC -> productEntity.likeCount.desc(); // 좋아요순
```

현재 인덱스는 좋아요순만 커버한다. 최신순과 가격순은?

### 우선순위 결정

| 정렬 | 사용 빈도 | 인덱스 필요성 |
|------|----------|-------------|
| 최신순 (LATEST) | ⭐ 기본값이므로 가장 많이 호출 | **필수** |
| 좋아요순 (LIKES_DESC) | 이미 구현됨 | ✅ 있음 |
| 가격순 (PRICE_ASC) | 사용 빈도 불명 | 보류 |

**최신순이 기본값**이므로 가장 많이 호출될 가능성이 높다. 인덱스 없이 방치하면 안 된다.

가격순은 실제 트래픽을 보고 나중에 결정해도 된다.

### 최종 적용 인덱스 (4개)

```sql
-- 좋아요순
CREATE INDEX idx_product_brand_like ON product(ref_brand_id, like_count DESC);
CREATE INDEX idx_product_like ON product(like_count DESC);

-- 최신순 (추가)
CREATE INDEX idx_product_brand_latest ON product(ref_brand_id, created_at DESC);
CREATE INDEX idx_product_latest ON product(created_at DESC);
```

| 인덱스 | 커버하는 쿼리 |
|--------|-------------|
| `idx_product_brand_like` | 브랜드 필터 + 좋아요순 |
| `idx_product_like` | 전체 좋아요순 |
| `idx_product_brand_latest` | 브랜드 필터 + 최신순 |
| `idx_product_latest` | 전체 최신순 |

### 가격순은 왜 안 만들었나?

- 인덱스가 많아지면 쓰기 성능 저하 (테스트에서 25% 느려짐 확인)
- 가격순의 실제 사용 빈도를 모름
- 필요하면 나중에 추가해도 됨

**트레이드오프**: 모든 경우를 커버하는 6개 인덱스 vs 핵심만 커버하는 4개 인덱스

→ 쓰기 성능을 고려해서 **4개로 시작**하고, 모니터링 후 필요하면 추가한다.

### 남은 고려사항

- 깊은 페이지네이션은 커서 기반으로 변경 검토
- 좋아요가 매우 빈번하면 Redis 캐시 검토
- 가격순 정렬 사용량 모니터링 후 인덱스 추가 검토