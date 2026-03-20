# 상품 목록 조회 인덱스 성능 분석 보고서

## 1. 개요

상품 목록 조회 API(`GET /api/v1/products`)의 쿼리 실행 계획을 분석하고,
인덱스 미적용 vs 개별 인덱스 단위 적용 상황을 비교하여 최적의 인덱스 전략을 도출한다.

---

## 2. 테스트 환경

| 항목 | 내용 |
|------|------|
| 테이블 | `product` |
| 데이터 건수 | 100,000건 (활성 ~94,861건 / 삭제 ~5,139건) |
| 기존 인덱스 | PK(`id`) 1개만 존재 |
| MySQL 버전 | InnoDB Engine |
| 브랜드 필터 테스트 대상 | brand_id = 1 (활성 상품 14,254건) |

### 테이블 스키마 (인덱스 적용 전)

```sql
CREATE TABLE `product` (
  `like_count` int NOT NULL,
  `price`      int NOT NULL,
  `stock`      int NOT NULL,
  `brand_id`   bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id`         bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name`       varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;
```

---

## 3. 분석 대상 유즈케이스

상품 목록 API(`GET /api/v1/products`)는 다음 파라미터 조합으로 쿼리가 발생한다.

### 정렬 기준 (`ProductSortType`)

| sort 파라미터 | 정렬 컬럼 | 방향 |
|-------------|---------|------|
| `latest` (기본) | `created_at` | **DESC** |
| `price_asc` | `price` | **ASC** |
| `likes_desc` | `like_count` | **DESC** |

### 쿼리 목록

| # | brandId 필터 | 정렬 기준 | 실행되는 SQL 요약 |
|---|-------------|----------|-----------------|
| Q1 | 없음 | latest | `WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20` |
| Q2 | 없음 | price_asc | `WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20` |
| Q3 | 없음 | likes_desc | `WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20` |
| Q4 | 없음 | (COUNT) | `SELECT COUNT(*) WHERE deleted_at IS NULL` |
| Q5 | 있음 | latest | `WHERE brand_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20` |
| Q6 | 있음 | price_asc | `WHERE brand_id = ? AND deleted_at IS NULL ORDER BY price ASC LIMIT 20` |
| Q7 | 있음 | likes_desc | `WHERE brand_id = ? AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20` |
| Q8 | 있음 | (COUNT) | `SELECT COUNT(*) WHERE brand_id = ? AND deleted_at IS NULL` |

> Spring Data JPA의 Page 조회는 데이터 쿼리 + COUNT 쿼리를 각 1회씩 실행한다.
> API 호출 한 번에 Q1~Q3 중 하나 + Q4가 함께, Q5~Q7 중 하나 + Q8이 함께 발생한다.

---

## 4. 테스트 방법론

### 인덱스 개별 단위 테스트

각 인덱스의 독립적 기여도를 정확히 측정하기 위해 다음 절차를 반복했다.

```
① ADD INDEX (인덱스 1개만 존재)
② 해당 인덱스가 영향을 주는 쿼리를 2회 실행 (버퍼풀 워밍업)
③ 실제 측정 (1회)
④ EXPLAIN 실행
⑤ DROP INDEX
→ 다음 인덱스로 반복
```

### 버퍼풀 워밍업의 필요성

첫 번째 실행은 디스크 I/O가 발생하는 **콜드 상태**이고, 두 번째부터는 데이터가 메모리에 적재된 **워밍업 상태**다.
워밍업 없이 측정하면 실제 캐시 히트율이 높은 운영 환경과 다른 수치가 나온다.

### 6개 인덱스 동시 적용의 문제점

여러 인덱스가 공존하면 **optimizer가 여러 선택지 중 하나를 고르는 과정**에서 예상치 못한 인덱스를 선택할 수 있다.
본 테스트에서 Q4(COUNT)가 이 문제를 실증했다 (7절 참고).

---

## 5. 인덱스 미적용 상태 (베이스라인)

> 버퍼풀 워밍업 후 측정. 8개 쿼리 전부 Full Table Scan 발생.

| # | type | rows | Extra | 실행 시간 |
|---|------|------|-------|---------|
| Q1 | ALL | 99,596 | Using where; **Using filesort** | 38.43ms |
| Q2 | ALL | 99,596 | Using where; **Using filesort** | 31.35ms |
| Q3 | ALL | 99,596 | Using where; **Using filesort** | 32.12ms |
| Q4 | ALL | 99,596 | Using where | 12.30ms |
| Q5 | ALL | 99,596 | Using where; **Using filesort** | 29.41ms |
| Q6 | ALL | 99,596 | Using where; **Using filesort** | 23.23ms |
| Q7 | ALL | 99,596 | Using where; **Using filesort** | 27.13ms |
| Q8 | ALL | 99,596 | Using where | 19.47ms |

### 문제점 분석

**① Full Table Scan (type: ALL)**
인덱스를 전혀 활용하지 못해 100,000건 전수 스캔 발생.
데이터가 증가할수록 선형으로 성능이 저하된다.

**② Using filesort**
인덱스에서 정렬 순서를 얻지 못해 별도 메모리/디스크 정렬 작업 필요.
Q1~Q3, Q5~Q7처럼 ORDER BY가 있는 쿼리에서 매 요청마다 발생한다.

**③ Q5~Q8: filtered 1%**
`brand_id = ?` 조건이 있는 쿼리에서 `filtered = 1`로 측정됨.
optimizer가 전체를 스캔한 뒤 1%만 조건에 일치한다고 추정 → 불필요한 99% 행을 읽고 버리는 낭비 발생.

---

## 6. 추가한 인덱스 목록

### 인덱스 선언 시 정렬 방향 정책

인덱스의 정렬 컬럼 방향을 **실제 쿼리의 ORDER BY 방향과 일치**시켜야 한다.
방향이 다를 경우 InnoDB가 B-Tree를 역방향으로 읽는 **Backward index scan**이 발생하고,
페이지 탐색 방향이 반대가 되어 ICP(Index Condition Pushdown) 활성화가 제한될 수 있다.

| sort 파라미터 | ORDER BY | 인덱스 정렬 방향 |
|-------------|----------|--------------|
| `latest` | `created_at DESC` | `created_at DESC` |
| `price_asc` | `price ASC` | `price ASC` (기본값과 동일) |
| `likes_desc` | `like_count DESC` | `like_count DESC` |

### 인덱스 DDL

```sql
-- 전체 상품 조회 (brandId 없음) 3종
ALTER TABLE product ADD INDEX idx_deleted_created (deleted_at, created_at DESC);
ALTER TABLE product ADD INDEX idx_deleted_price   (deleted_at, price);
ALTER TABLE product ADD INDEX idx_deleted_likes   (deleted_at, like_count DESC);

-- 브랜드별 상품 조회 (brandId 있음) 3종
ALTER TABLE product ADD INDEX idx_brand_deleted_created (brand_id, deleted_at, created_at DESC);
ALTER TABLE product ADD INDEX idx_brand_deleted_price   (brand_id, deleted_at, price);
ALTER TABLE product ADD INDEX idx_brand_deleted_likes   (brand_id, deleted_at, like_count DESC);
```

### 컬럼 순서 설계 원칙

```
등가 조건(=) → IS NULL 조건 → 정렬 컬럼(방향 일치)
```

| 인덱스명 | 컬럼 구성 | 설계 의도 |
|---------|---------|---------|
| `idx_deleted_created` | `(deleted_at, created_at DESC)` | soft delete 필터 + 최신순 정렬 |
| `idx_deleted_price` | `(deleted_at, price)` | soft delete 필터 + 가격 오름차순 정렬 |
| `idx_deleted_likes` | `(deleted_at, like_count DESC)` | soft delete 필터 + 좋아요 내림차순 정렬 |
| `idx_brand_deleted_created` | `(brand_id, deleted_at, created_at DESC)` | 브랜드 등가 + soft delete + 최신순 정렬 |
| `idx_brand_deleted_price` | `(brand_id, deleted_at, price)` | 브랜드 등가 + soft delete + 가격 오름차순 정렬 |
| `idx_brand_deleted_likes` | `(brand_id, deleted_at, like_count DESC)` | 브랜드 등가 + soft delete + 좋아요 내림차순 정렬 |

---

## 7. 인덱스 개별 적용 — EXPLAIN 및 실행 시간

> 각 인덱스를 단독으로 추가한 상태에서 버퍼풀 워밍업 후 측정.

### 7-1. idx_deleted_created (deleted_at, created_at DESC)

해당 쿼리: **Q1** (전체/최신), **Q4** (전체/COUNT)

| # | type | key | rows | Extra | 실행 시간 |
|---|------|-----|------|-------|---------|
| Q1 | ref | idx_deleted_created | 49,798 | **Using index condition** | 2.48ms |
| Q4 | ref | idx_deleted_created | 49,798 | Using where; **Using index** | 19.69ms |

### 7-2. idx_deleted_price (deleted_at, price)

해당 쿼리: **Q2** (전체/가격), **Q4** (전체/COUNT)

| # | type | key | rows | Extra | 실행 시간 |
|---|------|-----|------|-------|---------|
| Q2 | ref | idx_deleted_price | 49,798 | **Using index condition** | 1.89ms |
| Q4 | ref | idx_deleted_price | 49,798 | Using where; **Using index** | 17.47ms |

### 7-3. idx_deleted_likes (deleted_at, like_count DESC)

해당 쿼리: **Q3** (전체/좋아요), **Q4** (전체/COUNT)

| # | type | key | rows | Extra | 실행 시간 |
|---|------|-----|------|-------|---------|
| Q3 | ref | idx_deleted_likes | 49,798 | **Using index condition** | 1.28ms |
| Q4 | ref | idx_deleted_likes | 49,798 | Using where; **Using index** | 18.83ms |

### 7-4. idx_brand_deleted_created (brand_id, deleted_at, created_at DESC)

해당 쿼리: **Q5** (브랜드/최신), **Q8** (브랜드/COUNT)

| # | type | key | rows | Extra | 실행 시간 |
|---|------|-----|------|-------|---------|
| Q5 | ref | idx_brand_deleted_created | 28,704 | **Using index condition** | 1.64ms |
| Q8 | ref | idx_brand_deleted_created | 28,704 | Using where; **Using index** | 4.91ms |

### 7-5. idx_brand_deleted_price (brand_id, deleted_at, price)

해당 쿼리: **Q6** (브랜드/가격), **Q8** (브랜드/COUNT)

| # | type | key | rows | Extra | 실행 시간 |
|---|------|-----|------|-------|---------|
| Q6 | ref | idx_brand_deleted_price | 27,648 | **Using index condition** | 1.73ms |
| Q8 | ref | idx_brand_deleted_price | 27,648 | Using where; **Using index** | 4.18ms |

### 7-6. idx_brand_deleted_likes (brand_id, deleted_at, like_count DESC)

해당 쿼리: **Q7** (브랜드/좋아요), **Q8** (브랜드/COUNT)

| # | type | key | rows | Extra | 실행 시간 |
|---|------|-----|------|-------|---------|
| Q7 | ref | idx_brand_deleted_likes | 27,648 | **Using index condition** | 2.62ms |
| Q8 | ref | idx_brand_deleted_likes | 27,648 | Using where; **Using index** | 5.59ms |

---

## 8. 전·후 핵심 수치 비교 (개별 인덱스 기준)

| 쿼리 | 베이스라인 | 인덱스 적용 후 | 개선율 | Extra 변화 |
|------|----------|-------------|------|-----------|
| Q1 (전체/최신) | 38.43ms | **2.48ms** | 약 15배 ↑ | Using filesort → **Using index condition** |
| Q2 (전체/가격) | 31.35ms | **1.89ms** | 약 17배 ↑ | Using filesort → **Using index condition** |
| Q3 (전체/좋아요) | 32.12ms | **1.28ms** | 약 25배 ↑ | Using filesort → **Using index condition** |
| Q4 (전체/COUNT) | 12.30ms | **17~19ms** | 약 1.4배 ↓ | Using where → **Using index** (커버링)* |
| Q5 (브랜드/최신) | 29.41ms | **1.64ms** | 약 18배 ↑ | Using filesort → **Using index condition** |
| Q6 (브랜드/가격) | 23.23ms | **1.73ms** | 약 13배 ↑ | Using filesort → **Using index condition** |
| Q7 (브랜드/좋아요) | 27.13ms | **2.62ms** | 약 10배 ↑ | Using filesort → **Using index condition** |
| Q8 (브랜드/COUNT) | 19.47ms | **4~5ms** | 약 4~5배 ↑ | Using where → **Using index** (커버링) |

> \* Q4는 개별 인덱스 기준으로 커버링 인덱스 동작. 오히려 베이스라인보다 소폭 느린 이유는 9절 참고.

---

## 9. 개선 포인트 분석

**① Full Table Scan → ref 스캔으로 전환**
모든 쿼리에서 `type: ALL` → `type: ref`로 개선.
`deleted_at IS NULL`이 등가 조건(=)으로 처리되어, 인덱스의 NULL 구간만 스캔한다.

**② Using filesort 완전 제거 + ICP 활성화 (Q1~Q3, Q5~Q7)**
정렬 컬럼을 쿼리 방향과 일치시킨 덕분에 Backward scan 없이 Forward scan으로 동작하며,
ICP(Index Condition Pushdown)가 활성화되어 스토리지 엔진 레벨에서 WHERE 필터링을 처리한다.
불필요한 행을 버퍼풀에 올리지 않아 메모리 I/O도 절감된다.

**③ COUNT(*) 쿼리의 커버링 인덱스 동작 (Q4, Q8)**
`deleted_at`을 선두 컬럼으로 가진 인덱스는 COUNT(*) 시 `Using index` 커버링 인덱스로 동작한다.
인덱스 페이지만 읽고 실제 행 데이터에는 전혀 접근하지 않는다.

Q8은 베이스라인 대비 4~5배 빠르지만, Q4는 오히려 베이스라인(12.30ms)보다 소폭 느리다(17~19ms).
이는 버퍼풀에 데이터가 모두 올라온 상태에서 Full Table Scan의 **순차 I/O**가
인덱스 B-Tree 탐색 오버헤드보다 효율적이기 때문이다.
데이터 건수가 증가하면 커버링 인덱스가 압도적으로 유리해진다.

**④ 6개 인덱스 동시 환경에서 Q4 Optimizer의 오선택 (주의)**

동일한 Q4를 6개 인덱스가 모두 존재하는 환경에서 실행하면, optimizer가
`idx_brand_deleted_price`의 **skip scan**을 선택해 **56.72ms**로 오히려 느려진다.
skip scan은 brand_id 고유값마다 내부 범위를 반복 탐색해 **Random I/O**가 많아지기 때문이다.

| 테스트 환경 | 선택된 인덱스 | Extra | 실행 시간 |
|-----------|------------|-------|---------|
| 개별 인덱스 (idx_deleted_created만 존재) | idx_deleted_created | Using index (커버링) | 19.69ms |
| 6개 인덱스 동시 존재 | idx_brand_deleted_price | **Using index for skip scan** | **56.72ms** |

> 이것이 "인덱스를 많이 달수록 좋다"는 생각이 위험한 이유다.
> Optimizer는 여러 인덱스가 있을 때 오히려 잘못된 선택을 할 수 있으며,
> 개별 단위 테스트가 필요한 핵심 이유가 여기에 있다.

---

## 10. EXPLAIN 주요 용어 설명

| 용어 | 의미 |
|------|------|
| `type: ALL` | Full Table Scan — 인덱스 미사용, 전 행 스캔 |
| `type: index` | 인덱스 Full Scan — 테이블 대신 인덱스 전체를 순회 (LIMIT와 함께 Early stop 가능) |
| `type: ref` | 인덱스 등가 조건 스캔 — 특정 값과 일치하는 행만 탐색 |
| `type: range` | 인덱스 범위 스캔 — IS NULL 등 범위 조건 처리 |
| `Using filesort` | 별도 정렬 작업 발생 — 메모리 또는 디스크 정렬 |
| `Using where` | 스토리지 엔진에서 가져온 행을 서버 레이어에서 추가 필터링 |
| `Using index condition` | ICP 활성화 — 스토리지 엔진 레벨에서 WHERE 조건 평가, 불필요한 행 접근 감소 |
| `Using index` | 커버링 인덱스 — 실제 행 접근 없이 인덱스만으로 결과 완성 |
| `Using index for skip scan` | leading 컬럼을 건너뛰고 내부 컬럼 범위 반복 스캔 — Random I/O 주의 |
| `Backward index scan` | 인덱스 방향과 정렬 방향 불일치 — 역방향 탐색 (Forward보다 비효율) |

---

## 11. `deleted_at IS NULL` 인덱스의 한계

`deleted_at IS NULL` 조건의 **selectivity(선택도)가 낮다**는 점은 주의해야 한다.

- 본 테스트 환경에서는 삭제된 상품이 ~5,139건으로 전체의 약 5% — `deleted_at IS NULL`이 약 95%를 차지
- 인덱스 적용 후에도 Q1~Q3에서 여전히 ~49,798건을 스캔하는 이유가 이것
- MySQL optimizer는 selectivity가 너무 낮으면 인덱스를 무시하고 Full Scan을 선택하는 경우도 있음

> 만약 삭제 비율이 낮아 `deleted_at IS NULL`이 전체의 80% 이상이라면,
> `(deleted_at, ...)` 인덱스보다 **고선택도 컬럼을 앞에 두는 것**을 검토할 수 있다.
> 예: `(created_at DESC)` 단독 인덱스 — LIMIT와 함께 Early stop이 가능해 실제 스캔 행 수를 획기적으로 줄일 수 있다.
> 단, COUNT(*) 쿼리에서 deleted_at 필터를 인덱스로 처리할 수 없어 커버링 인덱스 효과가 사라진다.

---

## 12. 쓰기 비용 (Write Amplification) 고려

인덱스가 늘어날수록 INSERT / UPDATE / DELETE 시 B-Tree 갱신 비용이 증가한다.

### 작업별 인덱스 갱신 범위

| 작업 | 영향받는 인덱스 수 |
|------|----------------|
| `INSERT product` | 6개 모두 갱신 |
| `UPDATE price` | idx_deleted_price, idx_brand_deleted_price (2개) |
| `UPDATE like_count` | idx_deleted_likes, idx_brand_deleted_likes (2개) — **좋아요 클릭마다 발생** |
| soft delete (`UPDATE deleted_at`) | 6개 모두 갱신 |

> **특히 주의**: `like_count`는 좋아요 생성/취소마다 UPDATE되므로
> `idx_deleted_likes`, `idx_brand_deleted_likes` 두 인덱스는 쓰기 핫스팟이 될 수 있다.

---

## 13. 결론 및 권장 인덱스 전략

### 권장 안 (균형 전략)

조회와 쓰기 비용을 고려한 최소한의 필수 인덱스:

```sql
-- 브랜드 필터 + 정렬 커버 (선택도 높고 활용 범위 넓음)
ALTER TABLE product ADD INDEX idx_brand_deleted_created (brand_id, deleted_at, created_at DESC);
ALTER TABLE product ADD INDEX idx_brand_deleted_price   (brand_id, deleted_at, price);
ALTER TABLE product ADD INDEX idx_brand_deleted_likes   (brand_id, deleted_at, like_count DESC);

-- 전체 조회 (brandId 없음) — selectivity 낮아 효과 제한적이나 filesort 제거 효과 있음
ALTER TABLE product ADD INDEX idx_deleted_created (deleted_at, created_at DESC);
```

### 인덱스별 트레이드오프

| 인덱스 | 조회 이득 | 쓰기 비용 | 권장 여부 |
|-------|---------|---------|---------|
| `idx_brand_deleted_created` | 브랜드 필터 최신순 (가장 일반적인 케이스) | 낮음 | ✅ 필수 |
| `idx_brand_deleted_price` | 브랜드 필터 가격순 + Q8 커버링 | 낮음 | ✅ 필수 |
| `idx_brand_deleted_likes` | 브랜드 필터 좋아요순 | like_count UPDATE 빈번 시 부하 | ⚠️ 트래픽 확인 후 결정 |
| `idx_deleted_created` | 전체 최신순 (selectivity 낮아 효과 제한) | 낮음 | ✅ 권장 |
| `idx_deleted_price` | 전체 가격순 | 낮음 | ⚠️ 사용 빈도에 따라 결정 |
| `idx_deleted_likes` | 전체 좋아요순 | like_count UPDATE마다 갱신 | ⚠️ 트래픽 확인 후 결정 |

### 핵심 메시지

> "인덱스를 많이 달수록 읽기 성능이 좋아질 것이라는 기대는 항상 맞지 않는다.
> 본 테스트에서 Q4(COUNT)는 6개 인덱스 동시 환경에서 skip scan(56.72ms)으로 오히려 악화됐지만,
> 개별 인덱스 환경에서는 커버링 인덱스(17~19ms)로 동작했다.
> **각 인덱스를 단독으로 추가하고 EXPLAIN으로 실행 계획을 검증한 뒤 채택하는 것이 필수다.**
> `like_count`처럼 자주 갱신되는 컬럼의 인덱스는 쓰기 핫스팟이 될 수 있으므로,
> 실제 트래픽에서 해당 정렬 옵션의 사용 빈도를 모니터링한 뒤 결정하는 것이 바람직하다."

---

## 14. 최종 채택 인덱스 및 선택 이유

### 채택된 인덱스 (4개)

```java
// Product.java @Table indexes
@Index(name = "idx_brand_deleted_created", columnList = "brand_id, deleted_at, created_at DESC"),
@Index(name = "idx_brand_deleted_price",   columnList = "brand_id, deleted_at, price"),
@Index(name = "idx_brand_deleted_likes",   columnList = "brand_id, deleted_at, like_count DESC"),
@Index(name = "idx_deleted_created",       columnList = "deleted_at, created_at DESC")
```

| 인덱스 | 채택 이유 |
|-------|---------|
| `idx_brand_deleted_created` | 브랜드 필터 + 최신순은 e커머스의 가장 일반적인 진입 패턴. `brand_id` 선두 컬럼이 선택도를 높여 실질적인 스캔 범위 축소 효과가 크다. |
| `idx_brand_deleted_price` | 브랜드 필터 + 가격순은 구매 전 비교 유즈케이스에서 빈번. `(brand_id, deleted_at)`으로 Q8(COUNT) 커버링 인덱스 효과도 겸한다. |
| `idx_brand_deleted_likes` | 브랜드 인기순 정렬 지원. `like_count` 갱신마다 B-Tree 갱신이 발생하는 쓰기 비용을 감수하고 채택 — 브랜드 페이지에서 인기순 정렬 수요가 충분하다고 판단했다. |
| `idx_deleted_created` | 브랜드 필터 없는 전체 최신순은 랜딩 페이지 기본 정렬로 트래픽이 가장 높다. `deleted_at IS NULL` 선택도가 낮아 효과가 제한적이나, filesort 제거만으로도 p95 응답 시간을 개선하기에 충분하다. |

### 미채택 인덱스 (2개) 및 이유

| 인덱스 | 미채택 이유 |
|-------|-----------|
| `idx_deleted_price` | 브랜드 필터 없는 전체 가격순은 사용 빈도가 낮다고 판단. `deleted_at IS NULL` 선택도가 낮아 스캔 범위 축소 효과도 제한적이다. 쓰기 비용 대비 조회 이득이 크지 않아 제외. |
| `idx_deleted_likes` | 브랜드 필터 없는 전체 좋아요순은 역시 낮은 사용 빈도로 판단. `like_count`는 좋아요 생성/취소마다 갱신되는 핫스팟 컬럼이므로, 전체 조회용으로까지 인덱스를 두는 것은 쓰기 부하 대비 실익이 없다. |

### 결정 요약

> 브랜드 필터가 있는 3가지 정렬(최신/가격/좋아요)은 선택도가 높아 인덱스 효과가 확실하므로 전부 채택.
> 브랜드 필터가 없는 경우는 랜딩 페이지 기본값인 최신순 1개만 채택 — 나머지 둘은 사용 빈도와 쓰기 비용을 고려해 제외.

---

*분석 기준일: 2026-03-12*
*데이터 건수: product 테이블 100,000건*
*테스트 방식: 인덱스를 1개씩 개별 추가/DROP, 버퍼풀 워밍업(2회) 후 측정*
