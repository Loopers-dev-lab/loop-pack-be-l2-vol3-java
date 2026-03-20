# 주문 도메인 -인덱스 분석 보고서

## TL;DR

주문 도메인에서 3가지 조회 패턴(사용자 주문 목록, 관리자 상태별 목록, 상품별 주문 내역)에 대해
인덱스를 설계하고 EXPLAIN 전후 비교를 수행했다.
50만 주문 + 150만 아이템 기준, **498,012건 풀스캔 → 17건 인덱스 스캔(API 1)**으로 극적 개선을 확인했다.
특히 **같은 인덱스에서 데이터 분포(SHIPPING 9만 vs DELIVERED 25만)에 따라 성능이 3배 차이** 나는 것을 실측으로 증명했다.

---

## 1. 분석 대상

### 비즈니스 맥락

주문 데이터는 다양한 주체가 다양한 조건으로 조회한다.

- **고객**: "내 주문 확인해주세요" → user_id + 기간 + 상태 필터
- **관리자**: "배송중인 주문 보여줘" → status 필터 + 최신순 정렬
- **비즈니스 분석가**: "이 상품 얼마나 팔렸어?" → product_id 기반 JOIN 조회

각 패턴은 서로 다른 인덱스 트레이드오프를 보여준다.

| API | 핵심 포인트 |
|-----|-----------|
| 1. 사용자 주문 목록 | 등호 vs 범위 조건의 인덱스 순서 |
| 2. 관리자 상태별 목록 | 데이터 분포가 인덱스 효과에 미치는 영향 |
| 3. 상품별 주문 내역 | JOIN에서의 covering index 활용 |

### 테이블 구조

```sql
CREATE TABLE orders (
                        id               BIGINT NOT NULL AUTO_INCREMENT,
                        user_id          BIGINT NOT NULL,
                        status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        total_amount     DECIMAL(15,2) NOT NULL,
                        discount_amount  DECIMAL(15,2) NOT NULL,
                        final_amount     DECIMAL(15,2) NOT NULL,
                        issued_coupon_id BIGINT DEFAULT NULL,
                        created_at       DATETIME(6) NOT NULL,
                        PRIMARY KEY (id)
);

CREATE TABLE order_item (
                            id           BIGINT NOT NULL AUTO_INCREMENT,
                            order_id     BIGINT NOT NULL,
                            product_id   BIGINT NOT NULL,
                            product_name VARCHAR(255) NOT NULL,
                            price        DECIMAL(12,2) NOT NULL,
                            quantity     INT NOT NULL,
                            created_at   DATETIME(6) NOT NULL,
                            PRIMARY KEY (id),
                            KEY FKt4dc2r9nbvbujrljv3e23iibt (order_id),
                            FOREIGN KEY (order_id) REFERENCES orders (id)
);
```

### 데이터셋 -현업 유사 분포

| 항목 | 값 | 비고 |
|------|-----|------|
| 총 주문 | 500,000건 | 중소 커머스 1년 분량 |
| 총 주문 아이템 | 1,500,000건 | 주문당 3개 고정 |
| 유저 | 10,000명 | 파레토 분포 |
| 상품 | 5,000개 | - |

#### 상태별 분포 (핵심)

| status | 건수 | 비율 | 시기 |
|--------|------|------|------|
| DELIVERED | 272,052 | 54% | 2주 전 ~ 1년 전 |
| CANCELLED | 146,807 | 29% | 전 기간 |
| SHIPPING | 46,673 | 9% | 최근 2주 |
| PAID | 24,566 | 5% | 최근 2~3일 |
| PENDING | 9,902 | 2% | 최근 수시간 |

**완료/취소 상태가 전체의 83%**, 진행중(SHIPPING+PAID+PENDING)이 16%.
이 분포가 API 2에서 같은 인덱스의 성능이 극단적으로 달라지는 원인이 된다.

#### 유저별 주문 분포 (파레토)

| 주문 수 | 유저 수 | 비고 |
|---------|---------|------|
| 200건 이상 | 982명 | 상위 헤비 유저 |
| 50~199건 | 18명 | - |
| 10~49건 | 8,493명 | 대다수 |
| 1~9건 | 507명 | 저빈도 유저 |

---

## 2. AS-IS: 인덱스 없는 상태

```sql
-- [API 1] 사용자 주문 목록
EXPLAIN SELECT * FROM orders
WHERE user_id = 50 AND status = 'SHIPPING'
AND created_at BETWEEN '2026-03-01' AND '2026-03-13'
ORDER BY created_at DESC LIMIT 20;

-- [API 2] 관리자 상태별 주문 목록
EXPLAIN SELECT * FROM orders
WHERE status = 'SHIPPING'
ORDER BY created_at DESC LIMIT 20;

-- [API 3] 상품별 주문 내역
EXPLAIN SELECT o.* FROM orders o
JOIN order_item oi ON o.id = oi.order_id
WHERE oi.product_id = 100
ORDER BY o.created_at DESC LIMIT 20;
```

| API | table | type | key | rows | filtered | Extra |
|-----|-------|------|-----|------|----------|-------|
| 1 | orders | **ALL** | NULL | **498,012** | 0.11% | Using where; Using filesort |
| 2 | orders | **ALL** | NULL | **498,012** | 10.0% | Using where; Using filesort |
| 3 | oi | **ALL** | NULL | **1,492,800** | 10.0% | Using where; Using temporary; Using filesort |
| 3 | o | eq_ref | PRIMARY | 1 | 100.0% | - |

### 문제점

- **API 1**: 50만건 풀스캔, filtered=0.11% → 99.89%를 읽고 버린다. user_id 인덱스 자체가 없음.
- **API 2**: 50만건 풀스캔 + filesort.
- **API 3**: order_item **149만건 풀스캔** + temporary table + filesort. 가장 심각.

---

## 3. 인덱스 설계

### 적용한 인덱스 3개

```sql
-- API 1: 사용자 주문 목록
CREATE INDEX idx_orders_user_status_created ON orders(user_id, status, created_at DESC);

-- API 2: 관리자 상태별 주문 목록
CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);

-- API 3: 상품별 주문 내역
CREATE INDEX idx_order_item_product ON order_item(product_id, order_id);
```

### API 1 인덱스 설계 근거 -등호 vs 범위 순서

쿼리 구조:
```
WHERE user_id = 50        → 등호
  AND status = 'SHIPPING'  → 등호
  AND created_at BETWEEN ... → 범위
ORDER BY created_at DESC   → 정렬
```

| 후보 | 컬럼 | 특징 |
|------|------|------|
| A | (user_id, created_at DESC) | 기간만 필터 시 최적, status 필터 시 비효율 |
| **B (채택)** | (user_id, status, created_at DESC) | 등호 2개 + 범위/정렬 마지막 |

**핵심 원칙: 등호 조건을 앞에, 범위/정렬을 뒤에.**
user_id와 status는 등호 조건이므로 인덱스 앞에 배치하면 범위를 최대한 좁힌 후 created_at 정렬이 인덱스 순서와 일치하여 filesort가 불필요하다.

#### 후보 A vs B 실측 비교

| 케이스 | 후보 B만 | 후보 A + B 모두 |
|--------|---------|---------------|
| status 있음 (`WHERE user_id=50 AND status='SHIPPING' AND created_at BETWEEN ...`) | rows=17, filesort 없음 | rows=17, filesort 없음 |
| status 없음 (`WHERE user_id=50 AND created_at BETWEEN ...`) | rows=1,535, **filesort 있음** | rows=186, **filesort 없음** |

status 있을 때는 후보 B가 최적(rows=17). 하지만 status 없이 "내 전체 주문 보기"를 할 때, **후보 B만으로는 중간 컬럼(status) skip으로 filesort가 발생**한다(rows=1,535).

후보 A를 추가하면 옵티마이저가 상황에 따라 최적 인덱스를 자동 선택한다.
"전체 주문 보기(status 없음)"는 기본 화면에서 자주 사용되는 패턴이고, 헤비 유저(200건 이상 982명)에서는 filesort 비용이 커질 수 있으므로 **후보 A도 방어 인덱스로 유지**한다.

이는 상품 인덱스에서 "캐시 미스 대비 방어 인덱스"를 추가한 판단과 동일한 맥락이다.

만약 status 없이 `WHERE user_id = 50 AND created_at BETWEEN ...`만 오는 경우에도,
`(user_id, status, created_at)` 인덱스는 user_id까지는 타고 들어가므로 풀스캔보다는 낫다.
다만 status를 건너뛰면 created_at 정렬은 인덱스로 처리할 수 없어 filesort가 발생한다.
(상품 인덱스에서 확인한 **중간 컬럼 skip** 문제와 동일)

실제로 "내 전체 주문 보기(status 필터 없음)"는 기본 화면에서 자주 사용되는 패턴이다. 이 경우 `(user_id, created_at DESC)` 후보 A가 더 적합할 수 있으나, 해당 유저의 주문 수가 일반적으로 50건 미만이므로 filesort 비용이 미미하다고 판단하여 현재는 후보 B로 통합했다.

> **상품 인덱스와의 연결**: 상품에서는 `IN` 조건이 없어 WHERE 선두가 정석이었지만,
> 주문에서 `status IN ('SHIPPING', 'PAID')` 같은 다중 상태 조회가 필요해지면
> IN이 정렬을 깨뜨리는 문제가 발생한다. 이 경우 정렬 선두 인덱스를 검토해야 한다.

### API 2 인덱스 설계 근거 -단순하지만 데이터 분포가 핵심

```
WHERE status = ?
ORDER BY created_at DESC
```

`(status, created_at DESC)` -등호 필터 + 정렬 순서 일치. 설계 자체는 단순하다.
**이 인덱스의 진짜 분석 포인트는 데이터 분포에 있다.** (섹션 5에서 상세 분석)

### API 3 인덱스 설계 근거 -covering index

```
SELECT o.* FROM orders o
JOIN order_item oi ON o.id = oi.order_id
WHERE oi.product_id = 100
ORDER BY o.created_at DESC
```

| 후보 | 컬럼 (order_item) | 특징 |
|------|-------------------|------|
| A | (product_id) | 기본. product_id로 필터 후 order_id를 테이블에서 읽음 |
| **B (채택)** | (product_id, order_id) | covering index. order_id까지 인덱스에 포함 → 테이블 접근 불필요 |

`(product_id, order_id)` 인덱스는 JOIN에 필요한 order_id까지 인덱스에 포함하므로,
order_item **테이블에 접근하지 않고 인덱스만으로** JOIN 키를 제공할 수 있다.
EXPLAIN의 `Using index`가 이를 확인해준다.

---

## 4. TO-BE: 인덱스 적용 후

| API | type | key | rows | filtered | Extra |
|-----|------|-----|------|----------|-------|
| 1 | **range** | idx_orders_user_status_created | **17** | 100.0% | Using index condition |
| 2 (SHIPPING) | **ref** | idx_orders_status_created | **90,062** | 100.0% | - |
| 3 (oi) | **ref** | idx_order_item_product | **324** | 100.0% | **Using index**; Using temporary; Using filesort |
| 3 (o) | eq_ref | PRIMARY | 1 | 100.0% | - |

### Before/After 비교

| API | Before rows | After rows | 개선율 | filesort |
|-----|------------|-----------|--------|----------|
| 1. 사용자 주문 | 498,012 | **17** | **29,295x** | 제거 ✅ |
| 2. 관리자 상태별 | 498,012 | 90,062 | 5.5x | 제거 ✅ |
| 3. 상품별 주문 (oi) | 1,492,800 | **324** | **4,608x** | 유지 (orders 정렬) |

### API별 분석

**API 1**: `rows=17`로 가장 극적인 개선. user_id + status + created_at 범위 세 조건이 모두 인덱스를 타서 정확히 필요한 행만 스캔한다. filesort도 제거.

**API 2**: 인덱스를 타지만 `rows=90,062`로 여전히 많다. 이는 SHIPPING 상태의 데이터가 9만건이기 때문. 인덱스 문제가 아니라 **데이터 분포 문제**다. (다음 섹션에서 상세 분석)

**API 3**: order_item은 `rows=324, Using index`로 최적. 하지만 **orders 테이블의 created_at 정렬** 때문에 `Using temporary; Using filesort`가 남아있다. 324건의 filesort이므로 실질적 성능 영향은 미미하나, 인기 상품(수만건)에서는 체감될 수 있다.

---

## 5. 데이터 분포가 인덱스 효과에 미치는 영향 (API 2)

### 같은 인덱스, 극단적으로 다른 성능

```sql
EXPLAIN SELECT * FROM orders WHERE status = 'SHIPPING'  ORDER BY created_at DESC LIMIT 20;
EXPLAIN SELECT * FROM orders WHERE status = 'DELIVERED' ORDER BY created_at DESC LIMIT 20;
```

| status | 건수 | 비율 | EXPLAIN rows |
|--------|------|------|-------------|
| SHIPPING | 46,673 | 9% | **90,062** |
| DELIVERED | 272,052 | 54% | **249,006** |

> **참고**: EXPLAIN의 rows는 MySQL 옵티마이저의 **추정값**이므로 실제 건수(46,673 / 272,052)와 차이가 있다. 이는 통계 정보 기반 추정이기 때문이며, 추세(3배 차이)는 정확히 반영된다.

**같은 인덱스 `(status, created_at DESC)`를 타는데, rows가 3배 차이가 난다.**

### 왜 이런 차이가 발생하는가?

인덱스는 status = 'SHIPPING'인 행들의 범위를 빠르게 찾아준다.
하지만 **그 범위 안의 데이터 수가 status마다 다르다.**

- SHIPPING: 4만 6천건 → 인덱스가 좁혀주는 범위가 작아 효율적
- DELIVERED: 27만건 → 인덱스가 좁혀봤자 절반이 넘음

### 실무적 시사점

관리자가 "배송중(SHIPPING)" 주문을 조회할 때는 빠르지만,
"배달완료(DELIVERED)" 주문을 조회할 때는 느릴 수 있다.

이것이 멘토가 강조한 **"데이터셋을 현업과 유사하게 만드는 것이 중요하다"**의 의미다.
균등 분포로 테스트하면 이 차이를 발견할 수 없다.
완료/취소가 압도적으로 많은 현실적 분포에서만 이 문제가 드러난다.

### 해결 방향

| 대안 | 설명 | 적합한 경우 |
|------|------|-----------|
| A. 현재 유지 | DELIVERED 조회 시 rows가 크지만 LIMIT 20이면 인덱스 순서대로 20건만 읽고 끝 | 대부분의 경우 충분 |
| B. 기간 필터 추가 | `WHERE status = 'DELIVERED' AND created_at > '2026-01-01'` | 관리자가 전체가 아닌 최근만 볼 때 |
| C. 캐시 | 관리자 페이지는 동일 결과를 반복 조회할 가능성 높음 | 조회 빈도가 높을 때 |

현재는 **A로 충분**하다고 판단했다. `(status, created_at DESC)` 인덱스가 정렬 순서를 보장하므로, LIMIT 20과 결합하면 DELIVERED 27만건을 전부 스캔하지 않고 **앞에서 20건만 읽고 멈춘다(Early Termination).** 인덱스 자체가 created_at DESC로 이미 정렬된 상태이므로, MySQL은 인덱스를 순서대로 탐색하다가 20건이 채워지는 순간 즉시 중단한다. EXPLAIN의 rows=249,006은 **해당 status의 전체 추정 행 수**이며, LIMIT과 결합한 실제 스캔 행 수와는 다르다.

---

## 6. Covering Index 효과 (API 3)

### (product_id) vs (product_id, order_id)

```sql
-- covering index 확인
EXPLAIN SELECT oi.order_id FROM order_item oi WHERE oi.product_id = 100;
-- rows=324, Extra=Using index
```

`Using index`는 **인덱스만으로 쿼리를 완전히 처리**했다는 의미다.
order_item 테이블의 실제 데이터 페이지에 접근하지 않고, 인덱스 B-Tree만 읽어 order_id를 반환한다.

만약 `(product_id)` 단일 인덱스였다면, product_id로 필터 후 **각 행의 order_id를 가져오기 위해 테이블을 다시 읽어야** 한다. 이 추가 I/O가 건수가 많을수록 성능 차이를 만든다.

---

## 7. 전체 EXPLAIN 요약

| # | 시나리오 | type | key | rows | filesort | 비고 |
|---|---------|------|-----|------|----------|------|
| 1 | 사용자 주문 (인덱스 없음) | ALL | NULL | 498,012 | O | Before |
| 2 | 관리자 상태별 (인덱스 없음) | ALL | NULL | 498,012 | O | Before |
| 3 | 상품별 주문 (인덱스 없음) | ALL | NULL | 1,492,800 | O | Before |
| 4 | 사용자 주문 -status 있음 (인덱스 적용) | range | idx_user_status_created | **17** | X | **29,295x 개선** |
| 5 | 사용자 주문 -status 없음, 후보 B만 | ref | idx_user_status_created | 1,535 | O | **중간 컬럼 skip → filesort** |
| 6 | 사용자 주문 -status 없음, 후보 A+B | range | idx_user_created | **186** | X | **방어 인덱스로 filesort 제거** |
| 7 | 관리자 SHIPPING (인덱스 적용) | ref | idx_status_created | 90,062 | X | 5.5x 개선 |
| 8 | 관리자 DELIVERED (인덱스 적용) | ref | idx_status_created | 249,006 | X | **분포 영향: SHIPPING의 3배** |
| 9 | 상품별 주문 (인덱스 적용) | ref | idx_item_product | **324** | O (orders) | covering index + 4,608x 개선 |

---

## 8. 트레이드오프 정리

### 인덱스 4개 설계의 장단점

| 장점 | 단점 |
|------|------|
| 3가지 조회 패턴 + status 유무 모두 커버 | 인덱스 4개 유지 비용 |
| API 1에서 rows 29,295배 감소 | API 2에서 DELIVERED 조회 시 rows가 여전히 큼 |
| status 없는 기본 화면에서도 filesort 제거 | API 3에서 orders 정렬 filesort 잔존 |
| Covering index로 order_item 테이블 접근 제거 | - |

### API 3의 filesort 잔존 -왜 허용했는가?

order_item에서 product_id로 필터한 결과(324건)를 orders.created_at으로 정렬하는데,
이 **두 테이블 간 정렬**은 단일 인덱스로 해결할 수 없다.
324건의 filesort는 실행 시간에 거의 영향을 주지 않으며,
이를 해결하려면 비정규화(order_item에 created_at 중복 저장) 등 구조 변경이 필요하다.
현재 규모에서는 **비용 대비 이득이 적다**고 판단하여 허용했다.

### 주문은 캐시 미적용 -인덱스가 유일한 방어선

상품과 달리 주문은 **개인 데이터**이므로 캐시 히트율이 구조적으로 낮다.
"같은 유저가 같은 조건으로 반복 조회"하는 빈도가 낮기 때문이다.
따라서 현재 주문 조회는 **인덱스가 유일한 성능 방어선**이며,
상품처럼 "캐시 + 방어 인덱스" 이중 안전망 구조가 아닌 인덱스 단독으로 성능을 보장해야 한다.

### 향후 개선 가능 사항

- **API 3 filesort 제거**: 현재 단일 JOIN 쿼리를 **2단계 쿼리(ID 추출 → ID IN FETCH)**로 분리하면 temporary table을 제거할 수 있다. 1단계에서 covering index로 order_id만 추출하고, 2단계에서 PK IN 조회로 orders를 가져오면 JOIN 없이 처리 가능. 324건에서는 체감 차이가 적으나, 인기 상품(수만건)에서는 유의미한 개선이 기대된다.
- `status IN ('SHIPPING', 'PAID')` 같은 다중 상태 조회가 필요해지면, IN이 정렬을 깨뜨리는 문제 발생. **정렬 선두 인덱스** 검토 필요.
- 인기 상품(order_item 수만건)의 API 3 성능이 문제되면, 비정규화 또는 Pre-aggregation 검토.
- OFFSET 기반 deep page 문제 발생 시 Cursor 전환 검토. 다만 관리자 페이지는 "N페이지로 점프" UX가 필요하므로 OFFSET 유지가 적합할 수 있다.

---

## 9. 최종 인덱스 DDL

```sql
-- API 1: 사용자 주문 목록 -status 있을 때 (등호 + 등호 + 범위/정렬)
CREATE INDEX idx_orders_user_status_created ON orders(user_id, status, created_at DESC);

-- API 1: 사용자 주문 목록 -status 없을 때 (방어 인덱스)
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- API 2: 관리자 상태별 주문 목록 (등호 + 정렬)
CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);

-- API 3: 상품별 주문 내역 (covering index)
CREATE INDEX idx_order_item_product ON order_item(product_id, order_id);
```

### 핵심 학습 포인트

1. **등호 vs 범위 순서**: 등호 조건(user_id, status)을 앞에, 범위/정렬(created_at)을 뒤에 배치해야 인덱스 정렬이 동작한다.
2. **데이터 분포의 영향**: 같은 인덱스라도 조건값의 데이터 양에 따라 성능이 극단적으로 달라진다. 현업 유사 분포로 테스트해야 이 차이를 발견할 수 있다.
3. **Covering index**: JOIN에 필요한 컬럼까지 인덱스에 포함하면 테이블 접근을 제거할 수 있다.
4. **IN 조건과 정렬의 충돌**: 단일 등호는 문제없지만, IN(multiple equality)이 들어오면 정렬이 깨질 수 있다. 이 경우 정렬 선두 인덱스를 검토해야 한다.
5. **filesort 허용 판단**: 모든 filesort를 제거하는 것이 목표가 아니다. 소량(324건)의 filesort는 감수하고, 구조 변경 비용 대비 이득을 판단하는 것이 실무적 접근이다.