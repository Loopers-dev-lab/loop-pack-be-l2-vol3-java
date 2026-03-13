# 상품 목록 조회 -인덱스 분석 보고서

## TL;DR

상품 목록 조회에서 브랜드 필터 + 정렬(좋아요순/가격순/최신순) 시나리오에 대해
복합 인덱스 3개를 설계하고 EXPLAIN 전후 비교를 수행했다.
10만 건 기준, **풀스캔(99,574건) → 인덱스 스캔(5,000건)으로 약 95% 스캔 감소 + filesort 제거**를 확인했다.
브랜드 필터 없는 전체 조회는 중간 컬럼 skip으로 filesort가 재발생했으며,
처음에는 캐시만으로 해결하려 했으나, **캐시 미스 시나리오를 고려하여 방어 인덱스 3개를 추가**했다.
최종 인덱스 6개 + Redis 캐시의 **이중 안전망** 구조로 설계했다.

---

## 1. 분석 대상

### 비즈니스 맥락

커머스 서비스에서 가장 빈번한 조회 패턴은 **"브랜드별 상품 목록을 특정 기준으로 정렬해서 보여주는 것"**이다.
사용자는 다음과 같은 방식으로 상품을 조회한다.

- 브랜드 A의 상품을 좋아요 많은 순으로 보기
- 브랜드 A의 상품을 가격 낮은 순으로 보기
- 브랜드 A의 상품을 최신순으로 보기
- 전체 상품을 좋아요순/최신순/가격순으로 보기 (메인 홈)

모든 조회에는 soft delete 필터(`deleted_at IS NULL`)가 공통으로 적용된다.

### 테이블 구조

```sql
CREATE TABLE products (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  brand_id       BIGINT NOT NULL,
  name           VARCHAR(200) NOT NULL,
  price          DECIMAL(12,2) NOT NULL,
  stock_quantity INT NOT NULL,
  like_count     INT NOT NULL,
  description    VARCHAR(1000) DEFAULT NULL,
  version        BIGINT DEFAULT NULL,
  deleted_at     DATETIME(6) DEFAULT NULL,
  created_at     DATETIME(6) NOT NULL,
  updated_at     DATETIME(6) NOT NULL,
  PRIMARY KEY (id)
);
```

### 데이터셋

| 항목 | 값 | 비고 |
|------|-----|------|
| 총 상품 수 | 100,000건 | 과제 요구사항 충족 |
| 브랜드 수 | 20개 | 브랜드당 약 5,000건 균등 분포 |
| price | 1,000 ~ 500,000 | 랜덤 분포 |
| like_count | 0 ~ 5,000 | 랜덤 분포 |
| deleted_at | 전부 NULL | soft delete 미적용 상태 |

---

## 2. AS-IS: 인덱스 없는 상태 (기준선)

### 시나리오 1~2: 브랜드 필터 + 정렬 (인덱스 없음)

```sql
-- [1] 브랜드 + 좋아요순
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND brand_id = 1
ORDER BY like_count DESC LIMIT 20;

-- [2] 브랜드 + 가격순
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND brand_id = 1
ORDER BY price ASC LIMIT 20;
```

| 시나리오 | type | key | rows | filtered | Extra |
|---------|------|-----|------|----------|-------|
| [1] 브랜드+좋아요순 | **ALL** | NULL | **99,574** | 1.0% | Using where; **Using filesort** |
| [2] 브랜드+가격순 | **ALL** | NULL | **99,574** | 1.0% | Using where; **Using filesort** |

### 문제점

- **type=ALL**: 10만 건 전체를 풀스캔한다. 인덱스를 전혀 사용하지 않는다.
- **rows=99,574**: brand_id = 1인 데이터는 5,000건인데, 99,574건을 모두 스캔해야 찾을 수 있다.
- **filtered=1.0%**: 99%의 행을 읽고 버린다.
- **Using filesort**: ORDER BY를 인덱스로 처리하지 못하고, 결과를 메모리/디스크에 올려 별도 정렬을 수행한다.
- 트래픽이 증가하면 스캔 행 수에 비례해 **선형적으로 느려진다.**

---

## 3. 인덱스 설계

### 적용한 복합 인덱스 3개

```sql
CREATE INDEX idx_products_brand_created ON products(deleted_at, brand_id, created_at DESC);
CREATE INDEX idx_products_brand_price   ON products(deleted_at, brand_id, price);
CREATE INDEX idx_products_brand_likes   ON products(deleted_at, brand_id, like_count DESC);
```

### 왜 이 순서인가?

복합 인덱스의 컬럼 순서는 쿼리 구조에 의해 결정된다.

```
WHERE deleted_at IS NULL   → 1번째: 등호 조건 (IS NULL도 등호 취급)
  AND brand_id = 1          → 2번째: 등호 조건
ORDER BY like_count DESC    → 3번째: 정렬
```

**원칙: 등호 조건을 앞에, 정렬/범위를 뒤에 배치해야 인덱스 정렬이 동작한다.**

B-Tree 인덱스는 왼쪽부터 순서대로 탐색한다.
등호 조건(deleted_at, brand_id)으로 범위를 좁힌 뒤, 마지막 컬럼(like_count)이 이미 정렬된 상태이므로 별도 정렬(filesort)이 필요 없다.

### 왜 deleted_at이 선두인가?

처음에는 **카디널리티가 높은 컬럼을 앞에 두는 것**이 최선이라고 생각했다.
그 논리라면 brand_id(20종)가 deleted_at(NULL/값 2종)보다 앞에 와야 한다.

하지만 복합 인덱스에서 **등호 조건끼리는 순서가 성능에 거의 영향을 주지 않는다.**
B-Tree에서 `(deleted_at, brand_id)` 든 `(brand_id, deleted_at)` 든 두 레벨을 타고 내려가 같은 지점에 도달하기 때문이다.

그렇다면 남는 기준은 **재사용성**이다.
`deleted_at IS NULL`은 **모든 조회 쿼리에 공통으로 들어가는 조건**이다.
brand_id 없이 전체 조회하는 경우에도 deleted_at IS NULL은 항상 있으므로,
선두에 두면 인덱스의 첫 번째 컬럼이라도 활용할 수 있다.

반대로 `(brand_id, deleted_at, ...)` 순서라면 brand_id 조건이 없는 쿼리에서는 인덱스를 아예 타지 못한다.

### 왜 인덱스를 3개로 나누었는가?

| 대안 | 설명 | 문제 |
|------|------|------|
| A. 하나의 인덱스로 합침 | `(deleted_at, brand_id, price, like_count, created_at)` | ORDER BY like_count 시 3번째 컬럼(price)을 건너뛸 수 없어 정렬 불가 |
| **B. 정렬 기준별 분리 (채택)** | 정렬 컬럼이 마지막에 위치하도록 3개 | 정렬 기준마다 인덱스 정렬이 동작 |

복합 인덱스에서 **정렬 컬럼은 반드시 마지막에 위치**해야 한다.
정렬 기준이 3가지(created_at, price, like_count)라면 인덱스도 3개가 필요하다.

---

## 4. TO-BE: 인덱스 적용 후

### 시나리오 3~5: 브랜드 필터 + 정렬 (인덱스 최적)

```sql
-- [3] 브랜드 + 좋아요순
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND brand_id = 1
ORDER BY like_count DESC LIMIT 20;

-- [4] 브랜드 + 가격순
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND brand_id = 1
ORDER BY price ASC LIMIT 20;

-- [5] 브랜드 + 최신순
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND brand_id = 1
ORDER BY created_at DESC LIMIT 20;
```

| 시나리오 | type | key | ref | rows | filtered | Extra |
|---------|------|-----|-----|------|----------|-------|
| [3] 브랜드+좋아요순 | ref | idx_products_brand_likes | const,const | 5,000 | 100.0% | Using index condition |
| [4] 브랜드+가격순 | ref | idx_products_brand_price | const,const | 5,000 | 100.0% | Using index condition |
| [5] 브랜드+최신순 | ref | idx_products_brand_created | const,const | 5,000 | 100.0% | Using index condition |

### 개선 포인트

- **type=ref**: 등호 조건 2개(deleted_at, brand_id)로 인덱스를 타고 들어간다.
- **ref=const,const**: 두 조건 모두 인덱스의 등호 탐색에 사용되었음을 의미한다.
- **rows=5,000**: 99,574 → 5,000으로 **약 95% 스캔 감소.**
- **filtered=100.0%**: 인덱스가 조건을 완전히 커버하여 불필요한 행을 읽지 않는다.
- **Using filesort 제거**: 인덱스 자체가 정렬 순서를 보장하므로 별도 정렬이 불필요하다.
- 각 정렬 기준에 맞는 인덱스가 자동으로 선택된다 (MySQL 옵티마이저 판단).

---

## 5. 인덱스 효과가 제한되는 케이스 -중간 컬럼 skip

### 시나리오 6~7: 브랜드 필터 없는 전체 조회 (인덱스 3개 상태)

```sql
-- [6] 브랜드 없이 최신순
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20;

-- [7] 브랜드 없이 좋아요순
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL
ORDER BY like_count DESC LIMIT 20;
```

| 시나리오 | type | key | ref | rows | filtered | Extra |
|---------|------|-----|-----|------|----------|-------|
| [6] 브랜드 없이 최신순 | ref | idx_products_brand_created | const | 49,787 | 100.0% | Using index condition; **Using filesort** |
| [7] 브랜드 없이 좋아요순 | ref | idx_products_brand_created | const | 49,787 | 100.0% | Using index condition; **Using filesort** |

### 왜 이런 결과가 나오는가?

- **ref=const 하나만**: deleted_at IS NULL 조건만 인덱스를 탔다.
- brand_id 조건이 없으므로 인덱스의 **두 번째 컬럼(brand_id)을 건너뛴다.**
- 복합 인덱스는 **중간 컬럼을 skip하면 그 뒤 컬럼의 정렬도 인덱스로 처리할 수 없다.**
- 결과: 49,787건 스캔 + **filesort 재발생.**

### 브랜드 있을 때 vs 없을 때 비교

| 조건 | key | ref | rows | filesort |
|------|-----|-----|------|----------|
| brand_id 있음 | idx_brand_likes | const,const | **5,000** | 없음 |
| brand_id 없음 | idx_brand_created | const | **49,787** | **있음** |

**같은 인덱스인데 중간 컬럼 하나 빠졌을 뿐인데 rows 10배, filesort 발생.**
이것이 복합 인덱스에서 컬럼 순서가 중요한 이유다.

---

## 6. 판단의 전환 -캐시만으로 충분한가?

### 처음 판단: 캐시만으로 해결하자

시나리오 6, 7의 filesort 문제를 확인한 후, 처음에는 **인덱스 추가 없이 캐시만으로 해결**하려 했다.

| 대안 | 설명 | 장점 | 단점 |
|------|------|------|------|
| A. 인덱스 추가 | `(deleted_at, created_at DESC)` 등 별도 생성 | filesort 제거 | 인덱스 6개로 증가 |
| **B. 캐시로 해결 (처음 채택)** | 전체 조회 결과를 Redis에 캐싱 | DB 자체를 안 타므로 근본 해결 | 캐시 미스 시 무방비 |

근거는 이랬다.
1. 전체 상품 목록은 모든 유저가 동일한 결과를 본다. 캐시 효율이 높다.
2. 인덱스를 6개로 늘리면 관리 복잡도가 올라간다.
3. 인덱스와 캐시의 역할을 나누는 것이 깔끔하다.

### 재고: 캐시 미스는 생각보다 자주 발생한다

하지만 실무를 고려하면 캐시 미스 시나리오가 적지 않다.

- **TTL 만료**: 5분마다 주기적으로 발생
- **상품 수정/등록/삭제**: 무효화로 목록 캐시 전체 삭제
- **좋아요 변경**: 빈도 높은 이벤트
- **Redis 장애**: 전체 캐시 유실
- **배포 시 Redis flush**: 운영 팀 판단에 따라 발생 가능
- **Redis 메모리 부족**: LRU 정책으로 키가 밀려남

캐시가 빠져있는 순간 49,787건 스캔 + filesort가 발생하면,
그 요청들이 DB에 직접 부하를 주게 된다.
캐시 미스가 동시에 몰리면 **cache stampede(캐시 폭주)** 로 이어질 수도 있다.

### 최종 판단: 방어 인덱스 추가

**캐시는 "있으면 좋은 것"이지, "없으면 안 되는 것"이어서는 안 된다.**
캐시가 빠져도 DB가 견딜 수 있는 구조가 실무에서는 더 안전하다.

인덱스 3개를 추가하면, 캐시 히트 시에는 DB를 안 타고, 캐시 미스 시에도 인덱스가 잡아주는 **이중 안전망**이 된다.

처음에는 최신순과 좋아요순 2개만 추가하려 했으나, 가격순 전체 조회도 기능으로 존재하는 이상 가격순만 빠뜨리면 **그 하나가 캐시 미스 시 유일한 약점**이 된다. 최신순/좋아요순은 인덱스로 잡아주는데 가격순만 filesort가 발생하면, DB 부하가 특정 정렬에 집중되는 불균형이 생긴다. 방어 인덱스를 두기로 한 이상, **모든 정렬 기준에 일관적으로 적용**하는 것이 맞다고 판단했다.

---

## 7. 방어 인덱스 적용 -filesort 제거 확인

### 추가한 인덱스

```sql
CREATE INDEX idx_products_created_only ON products(deleted_at, created_at DESC);
CREATE INDEX idx_products_likes_only   ON products(deleted_at, like_count DESC);
CREATE INDEX idx_products_price_only   ON products(deleted_at, price);
```

### 시나리오 9~11: 방어 인덱스 적용 후

```sql
-- [9] 브랜드 없이 최신순 (방어 인덱스 적용)
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL
ORDER BY created_at DESC LIMIT 20;

-- [10] 브랜드 없이 좋아요순 (방어 인덱스 적용)
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL
ORDER BY like_count DESC LIMIT 20;

-- [11] 브랜드 없이 가격순 (방어 인덱스 적용)
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL
ORDER BY price ASC LIMIT 20;
```

| 시나리오 | type | key | ref | rows | filtered | Extra |
|---------|------|-----|-----|------|----------|-------|
| [9] 브랜드 없이 최신순 | range | idx_products_created_only | - | 49,787 | 100.0% | Using index condition |
| [10] 브랜드 없이 좋아요순 | ref | idx_products_likes_only | const | 49,787 | 100.0% | Using index condition |
| [11] 브랜드 없이 가격순 | ref | idx_products_price_only | const | 49,787 | 100.0% | Using index condition |

### 방어 인덱스 전후 비교

| 시나리오 | Before (인덱스 3개) | After (인덱스 6개) |
|---------|-------------------|-------------------|
| 브랜드 없이 최신순 | filesort **있음** | filesort **없음** |
| 브랜드 없이 좋아요순 | filesort **있음** | filesort **없음** |
| 브랜드 없이 가격순 | filesort **있음** | filesort **없음** |

rows는 EXPLAIN 상 49,787로 표시되지만, 인덱스가 정렬 순서를 보장하므로
**LIMIT 20과 결합하면 실제로는 앞에서 20건만 읽고 멈춘다.**
filesort가 있을 때는 49,787건을 전부 정렬해야 20건을 뽑을 수 있었으나,
filesort가 없으면 인덱스 순서대로 20건만 스캔하면 끝이다.

---

## 8. 데이터 분포와 rows 차이

### 시나리오 8: 브랜드별 rows 비교

```sql
EXPLAIN SELECT * FROM products WHERE deleted_at IS NULL AND brand_id = 1  ORDER BY like_count DESC LIMIT 20;
EXPLAIN SELECT * FROM products WHERE deleted_at IS NULL AND brand_id = 10 ORDER BY like_count DESC LIMIT 20;
EXPLAIN SELECT * FROM products WHERE deleted_at IS NULL AND brand_id = 20 ORDER BY like_count DESC LIMIT 20;
```

| brand_id | rows | key |
|----------|------|-----|
| 1 | 5,000 | idx_products_brand_likes |
| 10 | 5,000 | idx_products_brand_likes |
| 20 | 5,000 | idx_products_brand_likes |

현재 데이터셋은 브랜드당 **균등 5,000건**으로 분포되어 있어 rows가 동일하다.

실무에서는 인기 브랜드(예: 나이키)에 상품이 2만 건, 소규모 브랜드에 500건처럼 **편차가 크다.**
이 경우 동일 인덱스라도 인기 브랜드 조회 시 rows가 크게 증가하며,
LIMIT과 결합 시 성능 차이가 두드러질 수 있다.

> **향후 개선**: 시드 데이터에 파레토 분포(상위 20% 브랜드에 80% 상품 집중)를 적용하면
> 더 현실적인 분석이 가능하다.

---

## 9. 전체 EXPLAIN 요약

| # | 시나리오 | type | key | rows | filesort | 비고 |
|---|---------|------|-----|------|----------|------|
| 1 | 브랜드+좋아요순 (인덱스 없음) | ALL | NULL | 99,574 | O | **Before 기준선** |
| 2 | 브랜드+가격순 (인덱스 없음) | ALL | NULL | 99,574 | O | Before 기준선 |
| 3 | 브랜드+좋아요순 (인덱스 적용) | ref | idx_brand_likes | 5,000 | X | **95% 감소** |
| 4 | 브랜드+가격순 (인덱스 적용) | ref | idx_brand_price | 5,000 | X | 95% 감소 |
| 5 | 브랜드+최신순 (인덱스 적용) | ref | idx_brand_created | 5,000 | X | 95% 감소 |
| 6 | 브랜드 없이 최신순 (3개) | ref | idx_brand_created | 49,787 | O | **중간 컬럼 skip** |
| 7 | 브랜드 없이 좋아요순 (3개) | ref | idx_brand_created | 49,787 | O | 중간 컬럼 skip |
| 8 | 브랜드별 rows 비교 | ref | idx_brand_likes | 5,000 | X | 균등 분포 확인 |
| 9 | 브랜드 없이 최신순 (6개) | range | idx_created_only | 49,787 | X | **방어 인덱스로 filesort 제거** |
| 10 | 브랜드 없이 좋아요순 (6개) | ref | idx_likes_only | 49,787 | X | **방어 인덱스로 filesort 제거** |
| 11 | 브랜드 없이 가격순 (6개) | ref | idx_price_only | 49,787 | X | **방어 인덱스로 filesort 제거** |

---

## 10. 트레이드오프 정리

### 인덱스 6개 설계의 장단점

| 장점 | 단점 |
|------|------|
| 브랜드 필터 유무와 관계없이 모든 정렬 패턴에서 filesort 제거 | 인덱스 6개 유지 비용 (INSERT/UPDATE 시 갱신) |
| 캐시 미스 시에도 DB가 안정적으로 응답 (이중 안전망) | 정렬 기준 추가 시 인덱스도 추가 필요 |
| 캐시와 인덱스의 역할 분담이 명확 | - |

### 인덱스 구조: 핵심 3개 + 방어 3개

| 구분 | 인덱스 | 역할 |
|------|--------|------|
| 핵심 | `(deleted_at, brand_id, created_at DESC)` | 브랜드 + 최신순 |
| 핵심 | `(deleted_at, brand_id, price)` | 브랜드 + 가격순 |
| 핵심 | `(deleted_at, brand_id, like_count DESC)` | 브랜드 + 좋아요순 |
| 방어 | `(deleted_at, created_at DESC)` | 전체 최신순 (캐시 미스 대비) |
| 방어 | `(deleted_at, like_count DESC)` | 전체 좋아요순 (캐시 미스 대비) |
| 방어 | `(deleted_at, price)` | 전체 가격순 (캐시 미스 대비) |

### 읽기 vs 쓰기 비용 판단

커머스 서비스에서 읽기:쓰기 비율은 약 99:1이다.
인덱스 6개로 인한 쓰기 비용 증가보다, **캐시 미스 시 49,787건 filesort가 서비스에 미치는 영향**이 훨씬 크다.
쓰기 비용은 선형적으로 소량 증가하지만, filesort는 트래픽 폭주 시 DB를 압박할 수 있다.

### 최종 전략: 이중 안전망

```
캐시 히트 시 → DB를 아예 안 탐 (Redis/Caffeine에서 반환)
캐시 미스 시 → 인덱스가 filesort 없이 처리 (방어 인덱스)
```

캐시는 "있으면 좋은 것"이지, "없으면 안 되는 것"이어서는 안 된다.
캐시가 빠져도 서비스가 버틸 수 있는 구조가 실무적으로 안전하다.

### 향후 개선 가능 사항

- 데이터가 수백만 건으로 커지면 **OFFSET 기반 페이지네이션의 deep page 문제** 발생 가능. Cursor 기반 전환 검토.
- 브랜드별 상품 수 편차가 커지면 **인기 브랜드의 rows 증가**로 성능 저하 가능. 파레토 분포 시드 데이터로 사전 검증 권장.

---

## 11. 최종 인덱스 DDL

```sql
-- 핵심 인덱스: 브랜드 필터 + 정렬 (3개)
CREATE INDEX idx_products_brand_created ON products(deleted_at, brand_id, created_at DESC);
CREATE INDEX idx_products_brand_price   ON products(deleted_at, brand_id, price);
CREATE INDEX idx_products_brand_likes   ON products(deleted_at, brand_id, like_count DESC);

-- 방어 인덱스: 브랜드 필터 없는 전체 조회 (3개)
CREATE INDEX idx_products_created_only  ON products(deleted_at, created_at DESC);
CREATE INDEX idx_products_likes_only    ON products(deleted_at, like_count DESC);
CREATE INDEX idx_products_price_only    ON products(deleted_at, price);
```

### 핵심 학습 포인트

1. **복합 인덱스 순서**: 등호 조건을 앞에, 정렬을 뒤에 배치해야 filesort를 제거할 수 있다.
2. **공통 조건의 선두 배치**: 모든 쿼리에 공통인 deleted_at을 선두에 두어 재사용성을 높인다. 등호 조건끼리는 순서보다 재사용성이 더 중요한 판단 기준이다.
3. **정렬 기준별 분리**: 정렬 컬럼이 다르면 하나의 인덱스로 합칠 수 없다. 정렬 기준 수만큼 인덱스가 필요하다.
4. **중간 컬럼 skip의 영향**: 복합 인덱스에서 중간 컬럼이 빠지면 그 뒤 컬럼의 정렬이 불가능하다. (시나리오 6, 7에서 실측 확인)
5. **방어적 설계**: 캐시만 믿지 말고, 캐시 미스 시에도 DB가 견딜 수 있는 인덱스를 함께 두는 것이 실무적 판단이다. 처음에는 캐시만으로 충분하다고 생각했으나, TTL 만료, 무효화, Redis 장애 등 캐시 미스 시나리오를 고려하면 **인덱스 + 캐시의 이중 안전망**이 더 안전하다.