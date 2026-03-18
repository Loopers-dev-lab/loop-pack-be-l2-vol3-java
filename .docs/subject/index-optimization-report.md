# 상품 목록 조회 인덱스 최적화 보고서

## 1. 목표

상품 목록 조회 API의 성능을 분석하고, 인덱스 전략별 성능 차이를 실측하여 최적의 인덱스를 선정한다.

- 데이터량: 10만 / 20만 / 50만 / 100만건
- 대상 쿼리: 브랜드 필터 + 정렬(좋아요순, 가격순, 최신순) 조합
- 환경: MySQL 8.0 (TestContainers)

---

## 2. 테스트 설계 과정

### 2.1 데이터 시딩 방식

JPA `save()`를 반복하면 엔티티 라이프사이클(@PrePersist, 변경감지 등) 오버헤드가 크다.
JDBC PreparedStatement batch insert를 선택했다. 5,000건 단위로 `executeBatch()`하여 100만건도 약 30초 내에 삽입 가능했다.

### 2.2 데이터 분포 설계

현실 세계의 커머스 데이터를 반영하기 위해 균등 분포 대신 편향된 분포를 적용했다.

**브랜드 분포: Zipf 분포 (100개 브랜드)**

현실에서는 인기 브랜드에 상품이 집중된다. Zipf 분포(`P(brand_i) ∝ 1/i`)를 적용하여 상위 브랜드에 상품이 쏠리도록 했다.

실제 결과 (시드 고정, 10만건 기준):
| 순위 | 브랜드 ID | 상품 수 | 비율 |
|---|---|---|---|
| 1 | 1 | 19,045 | 19.0% |
| 2 | 2 | 9,549 | 9.5% |
| 3 | 3 | 6,568 | 6.6% |
| ... | ... | ... | ... |
| 50 | 49 | 384 | 0.4% |

→ 상위 10개 브랜드가 전체의 약 56%를 차지. 인기 브랜드 필터 시 대량 데이터가 걸리는 현실적 시나리오를 재현했다.

**좋아요 수 분포: Log-normal 분포**

소셜 engagement는 "대부분 적고, 소수만 많은" 롱테일 패턴을 따른다.
`Math.exp(random.nextGaussian() * 2.0 + 2.5)`로 생성하여:

| 구간 | 비율 |
|---|---|
| 0~10 | 48.1% |
| 11~50 | 28.2% |
| 51~200 | 15.6% |
| 201~1,000 | 6.8% |
| 1,001~5,000 | 1.2% |
| 5,001+ | 0.1% |

→ 절반 가까이가 좋아요 10개 이하이고, 1% 미만만 1,000개 이상. 실제 커머스 플랫폼의 좋아요 분포와 유사하다.

**가격 분포**

실제 커머스 가격대를 반영하여 13개 기준가(5,000 ~ 499,000원)에서 랜덤 선택 후 ±20% 노이즈를 적용했다.

### 2.3 벤치마크 쿼리 선정

현재 API가 지원하는 실제 쿼리 패턴을 그대로 테스트 대상으로 삼았다:

```java
// ProductSortType: LATEST, PRICE_ASC, LIKES_DESC
// brandId: optional
Page<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);
Page<Product> findAllByDeletedAtIsNull(Pageable pageable);
```

이로부터 도출된 10개 벤치마크 쿼리:

| # | 쿼리 | 근거 |
|---|---|---|
| 1~3 | 브랜드(인기) + 좋아요순/가격순/최신순 | 인기 브랜드 필터(19% 데이터) + 3가지 정렬 |
| 4 | 브랜드(중간) + 좋아요순 | 소규모 브랜드(0.4%) 대비 성능 차이 확인 |
| 5~6 | 전체 + 좋아요순/가격순 | 브랜드 필터 없는 경우 |
| 7~8 | COUNT 쿼리 | Spring Data Page의 totalElements 산출용 (매 조회마다 실행됨) |
| 9~10 | 딥 페이지네이션 (OFFSET 5000/10000) | OFFSET 기반 페이징의 한계 확인 |

### 2.4 측정 방식

- **ANALYZE TABLE 선행**: 시딩 직후 `ANALYZE TABLE products` 실행하여 통계 정보를 갱신한 뒤 벤치마크 시작. 초기 버전에서 이를 누락하여 Baseline의 EXPLAIN `rows=20`(실제 10만건)이라는 부정확한 결과가 나왔고, 수정 후 `rows=99,642`로 정상화됐다.
- **Warm-up**: 각 쿼리마다 측정 전 1회 실행하여 버퍼 풀에 데이터를 로드한 후 3회 측정 평균을 기록.
- **인덱스 전략 간 독립성**: 전략마다 CREATE INDEX → ANALYZE → 벤치마크 → DROP INDEX 순으로 실행하여 이전 전략의 인덱스가 남지 않도록 했다.

---

## 3. 테스트한 인덱스 전략

| 전략 | 인덱스 | 의도 |
|---|---|---|
| 0. Baseline | PK만 | 비교 기준 |
| 1. 단일 | `(brand_id)` | 가장 직관적인 필터 컬럼 인덱스 |
| 2. 복합 1개 | `(brand_id, deleted_at, like_count DESC)` | 가장 핵심 쿼리(브랜드+좋아요순) 최적화 |
| 3. 전체 커버링 | 아래 5개 | 모든 쿼리 패턴 최적화 |

전체 커버링 세트:
```sql
CREATE INDEX idx_prod_brand_like ON products(brand_id, deleted_at, like_count DESC);
CREATE INDEX idx_prod_brand_price ON products(brand_id, deleted_at, price ASC);
CREATE INDEX idx_prod_brand_created ON products(brand_id, deleted_at, created_at DESC);
CREATE INDEX idx_prod_deleted_like ON products(deleted_at, like_count DESC);
CREATE INDEX idx_prod_deleted_price ON products(deleted_at, price ASC);
```

복합 인덱스 설계 근거:
- 컬럼 순서: `(등치 조건) → (등치 조건) → (정렬 컬럼)` 순으로 배치
- `deleted_at IS NULL`은 MySQL 8.0에서 등치 조건(ref)으로 처리되므로 인덱스 정렬까지 활용 가능
- 브랜드 필터 쿼리용 `(brand_id, deleted_at, ...)` + 전체 조회용 `(deleted_at, ...)` 분리

---

## 4. 결과 요약

> 개별 데이터량별 상세 결과는 `index-benchmark-100k.md`, `index-benchmark-200k.md`, `index-benchmark-500k.md`, `index-benchmark-1m.md` 참조.

### 4.1 핵심 쿼리: 브랜드(인기) + 좋아요순

| 데이터량 | Baseline | 단일(brand_id) | 복합 1개 | 전체 커버링 |
|---|---|---|---|---|
| 10만 | 28ms | 18ms | **0ms** | **0ms** |
| 20만 | 59ms | 207ms | **1ms** | **1ms** |
| 50만 | 178ms | 831ms | **2ms** | **1ms** |
| 100만 | 332ms | 1,727ms | **1ms** | **0ms** |

### 4.2 브랜드(인기) + 가격순 (복합 인덱스가 커버하지 못하는 쿼리)

| 데이터량 | Baseline | 단일(brand_id) | 복합 1개 | 전체 커버링 |
|---|---|---|---|---|
| 10만 | 28ms | 18ms | 22ms | **1ms** |
| 20만 | 60ms | 189ms | 160ms | **1ms** |
| 50만 | 157ms | 773ms | 609ms | **1ms** |
| 100만 | 347ms | 1,711ms | 1,218ms | **1ms** |

### 4.3 전체 + 좋아요순 (브랜드 필터 없음)

| 데이터량 | Baseline | 단일(brand_id) | 복합 1개 | 전체 커버링 |
|---|---|---|---|---|
| 10만 | 32ms | 31ms | 31ms | **1ms** |
| 20만 | 65ms | 68ms | 74ms | **2ms** |
| 50만 | 172ms | 180ms | 177ms | **0ms** |
| 100만 | 351ms | 366ms | 364ms | **0ms** |

### 4.4 딥 페이지네이션 (전체 + 좋아요순 OFFSET 10000)

| 데이터량 | Baseline | 단일(brand_id) | 복합 1개 | 전체 커버링 |
|---|---|---|---|---|
| 10만 | 56ms | 54ms | 55ms | 28ms |
| 20만 | 104ms | 131ms | 130ms | 61ms |
| 50만 | 442ms | 373ms | 500ms | 241ms |
| 100만 | 620ms | 606ms | 863ms | **90ms** |

---

## 5. 분석

### 5.1 단일 인덱스의 함정

`(brand_id)` 단일 인덱스는 데이터가 늘어날수록 **Baseline보다 느려졌다**.

원인: 옵티마이저가 인덱스를 사용해 `brand_id = 1` 조건으로 19만건을 필터링한 뒤, 이 19만건에 대해 filesort를 수행한다. Full Scan(100만건 순차 읽기)보다 "인덱스 랜덤 I/O + 대량 filesort"가 더 비싼 것이다.

교훈: **필터만 되고 정렬을 커버하지 못하는 인덱스는 대량 데이터에서 역효과**를 낼 수 있다.

### 5.2 복합 인덱스 1개의 한계

`(brand_id, deleted_at, like_count DESC)`는 좋아요순 쿼리에서 극적인 효과를 보였지만, 가격순/최신순에서는 단일 인덱스와 같은 문제(인덱스 필터 후 filesort)가 발생했다.

EXPLAIN 비교 (100만건, 브랜드(인기) + 가격순):
- 복합 인덱스: `type=ref, key=idx_brand_deleted_like, Extra=Using index condition; Using filesort` → **1,218ms**
- 전체 커버링: `type=ref, key=idx_prod_brand_price, Extra=Using index condition` → **1ms**

### 5.3 전체 커버링 세트의 효과

모든 쿼리에서 데이터량에 관계없이 0~2ms를 유지했다. EXPLAIN에서 모든 쿼리가 `Using index condition` (filesort 없음)으로 처리되었다.

### 5.4 딥 페이지네이션의 구조적 한계

커버링 인덱스로도 100만건 OFFSET 10000에서 90ms가 소요되었다. OFFSET 기반 페이징은 "건너뛸 행을 모두 읽고 버리는" 방식이므로 인덱스만으로는 근본적 해결이 어렵다. 향후 cursor-based pagination(keyset pagination) 등 구조적 대안을 고려할 수 있다.

### 5.5 `deleted_at IS NULL`과 인덱스

MySQL 8.0에서 `IS NULL`은 등치 조건(ref access type)으로 처리된다. 따라서 `(brand_id, deleted_at, like_count)` 인덱스에서 `WHERE brand_id = ? AND deleted_at IS NULL ORDER BY like_count DESC` 쿼리가 인덱스 정렬까지 활용 가능하다. 이는 `IS NULL`이 range 조건으로 처리되던 이전 MySQL 버전과의 중요한 차이점이다.

---

## 6. 인덱스 선정

### 6.1 벤치마크 결과만 보면

전체 커버링 인덱스 세트(5개)가 모든 쿼리에서 0~2ms를 달성하여 성능 측면에서는 최적이다. 하지만 "인덱스 5개를 그대로 적용한다"가 현실의 최선인지는 별개의 문제다.

### 6.2 현실 세계의 비즈니스 특성 고려

**쿼리별 실제 트래픽 비중 분석**

모든 쿼리가 동일한 빈도로 호출되지 않는다. 실제 커머스 UX를 기준으로:

| 쿼리 패턴 | UX 시나리오 | 트래픽 비중 |
|---|---|---|
| 브랜드 + 좋아요순 | 브랜드 페이지 "인기순" | 높음 |
| 브랜드 + 가격순 | 브랜드 페이지 "가격순" | 높음 |
| 브랜드 + 최신순 | 브랜드 페이지 "최신순" | 높음 |
| 전체 + 좋아요순 | 홈페이지 "인기 상품" | 높음 |
| 전체 + 가격순 | 필터 없이 가격순 | **낮음** — 브랜드/카테고리 없이 가격만으로 정렬하는 UX는 드뭄 |
| 전체 + 최신순 | 홈페이지 기본 목록 | 높음, 그러나 캐시 1순위 대상 |

브랜드 필터가 있는 쿼리 3종은 조합이 많아(100개 브랜드 × 3정렬 × 페이지) 캐시 효율이 낮다. 반면 전체 조회는 조합이 적어(3정렬 × 페이지) 캐시로 커버하기 좋다.

**인덱스와 캐시의 역할 분담**

이번 과제에는 Redis 캐시 적용도 포함되어 있다. 두 기술을 함께 고려하면:

- **인덱스가 필수인 영역**: 브랜드 필터 조회 — 조합이 많아 캐시만으로는 비효율적
- **캐시로 위임 가능한 영역**: 전체 조회 — 조합이 적고, 홈페이지 등 반복 요청이 많아 캐시 적중률이 높음
- **양쪽 모두 필요한 영역**: 전체 + 좋아요순 — 홈페이지 인기상품은 캐시 대상이지만, 캐시 miss/갱신 시 원본 쿼리도 빨라야 함

**쓰기 비용 평가**

| 쓰기 작업 | 빈도 | 영향 |
|---|---|---|
| 상품 등록 | 하루 수십건 (관리자) | 모든 인덱스 |
| 가격 변경 | 하루 수건 (관리자) | price 인덱스만 |
| like_count 증감 | 하루 수천~수만건 (사용자) | like_count 포함 인덱스 2개 |
| soft delete | 매우 드뭄 | deleted_at 인덱스 |

products 테이블은 근본적으로 read-heavy다. 가장 빈번한 쓰기인 like_count 증감도 영향받는 인덱스는 2개뿐이며, 인덱스가 4개든 5개든 쓰기 비용 차이는 미미하다.

### 6.3 의사결정: 인덱스 4개 + 캐시 보완

**채택한 인덱스 (4개):**

```sql
-- 브랜드 필터 + 정렬 (3개) — 인덱스가 필수인 영역
CREATE INDEX idx_prod_brand_like ON products(brand_id, deleted_at, like_count DESC);
CREATE INDEX idx_prod_brand_price ON products(brand_id, deleted_at, price ASC);
CREATE INDEX idx_prod_brand_created ON products(brand_id, deleted_at, created_at DESC);

-- 전체 조회 + 인기순 (1개) — 캐시 miss/갱신 시에도 빠르게
CREATE INDEX idx_prod_deleted_like ON products(deleted_at, like_count DESC);
```

**버린 인덱스와 근거:**

| 버린 인덱스 | 커버하는 쿼리 | 버린 이유 |
|---|---|---|
| `(deleted_at, price ASC)` | 전체 + 가격순 | 필터 없이 가격순 정렬은 실제 UX에서 드뭄. 발생해도 캐시로 커버 |
| `(deleted_at, created_at DESC)` | 전체 + 최신순 | 캐시 대상 1순위 (홈페이지 기본). 캐시 miss 시 100만건 332ms는 수용 가능 |

**타협점:**

- "전체 + 가격순"과 "전체 + 최신순"은 인덱스 없이 100만건 기준 332~342ms가 소요된다. 이는 사용자 체감에 영향을 줄 수 있는 수치이지만, 캐시가 적용되면 실제 DB 호출 빈도가 크게 줄어든다. TTL 내 캐시 히트 시 <1ms로 응답 가능하다.
- 만약 캐시 없이 운영하거나, 캐시 miss 빈도가 예상보다 높다면 `(deleted_at, price ASC)`를 추가하여 5개로 확장하면 된다. 인덱스 추가는 `ALTER TABLE`로 언제든 가능하고, products 테이블의 쓰기 특성상 부담이 적다.

**예상 성능 (100만건):**

| 쿼리 | 인덱스 | 캐시 | 최종 응답 |
|---|---|---|---|
| 브랜드 + 좋아요순 | 0~1ms | - | **0~1ms** |
| 브랜드 + 가격순 | 1ms | - | **1ms** |
| 브랜드 + 최신순 | 0~1ms | - | **0~1ms** |
| 전체 + 좋아요순 | 0~1ms | 히트 시 <1ms | **<1ms** |
| 전체 + 가격순 | 342ms (미커버) | 히트 시 <1ms | **<1ms** (캐시) |
| 전체 + 최신순 | 332ms (미커버) | 히트 시 <1ms | **<1ms** (캐시) |

인덱스 4개로 전체 커버링(5개)의 95% 효과를 달성하면서, 나머지 5%는 캐시가 보완하는 구조다.
