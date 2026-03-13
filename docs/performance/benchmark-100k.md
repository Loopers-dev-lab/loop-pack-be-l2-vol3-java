# 벤치마크 — 10만 건 (100k)

> 상품 10만 건 기준 AS-IS API 성능 측정 결과. 공통 절차: [README.md](README.md)

## 1. 테스트 환경

| 항목      | 값                                                             |
| --------- | -------------------------------------------------------------- |
| 상품 수   | 100,000건                                                      |
| 브랜드 수 | 500개 (시더 기준)                                              |
| 시드      | `ProductDataSeeder` (perf-seed), `--product.seed.count=100000` |

## 2. 시드 초기화 및 재호출

1. DB 초기화: 스키마 재생성(`ddl-auto: create`) 또는 `product`, `brand` 등 관련 테이블 truncate.
2. 시드만 실행 후 종료:
   ```bash
   ./gradlew bootRun -p apps/commerce-api --args='--spring.profiles.active=local,perf-seed --product.seed.count=100000 --perf.seed.exit-after-run=true'
   ```
3. 동일 규모로 서버 기동 후 부하 테스트:
   ```bash
   ./gradlew bootRun -p apps/commerce-api --args='--spring.profiles.active=local,perf-seed --product.seed.count=100000'
   ```
4. 다른 터미널에서 k6 실행 (예: `./docs/load-test/run-all-k6-for-scale.sh 100000 30`). 인증 필요 API는 [README.md](README.md) 절차 선행.

## 3. index-benchmark 참고 및 현재 프로젝트와의 차이

- **참고 문서**: `index-benchmark-500k.md` / `index-benchmark-1m.md` (DB 레벨, raw SQL, `products.like_count` 컬럼 가정).
- **차이점**: 현재 commerce-api는 **`product` 테이블에 `like_count` 컬럼이 없음**. 좋아요는 `likes` 테이블에서 **LEFT JOIN + GROUP BY + ORDER BY COUNT** 로 집계하므로, index 벤치마크의 “like_count 컬럼 + 인덱스” 수치는 **그대로 적용되지 않음**. API 측정은 실제 앱 쿼리(JOIN/집계) 기준으로 해석한다.
- **추가로 고려할 점** (index 벤치마크에서 가져온 패턴):
  - 브랜드(인기/중간) + 정렬(latest, price_asc, likes_desc)
  - 전체 목록 + 정렬
  - COUNT(브랜드별/전체) — PLP에서 total count 호출 시
  - 딥페이징: OFFSET 5000/10000 구간에서의 성능 저하

## 4. API 측정 결과

### 4.1 PLP (상품 목록)

| 정렬       | 페이지 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고            |
| ---------- | ----------- | -------- | -------- | -------- | -------- | ------ | --------------- |
| latest     | 0~2         | 4.65     | 13.19    | 25.56    | 35.64    | 0.00%  |                 |
| price_asc  | 0~2         | 6.52     | 28.12    | 69.83    | 106.05   | 0.00%  |                 |
| likes_desc | 0~2         | 3.76     | 8.46     | 32.34    | 33.99    | 0.00%  | JOIN+GROUP BY   |
| latest     | 50~100      | 3.22     | 7.71     | 11.25    | 16.20    | 0.00%  | OFFSET 딥페이징 |

### 4.2 PDP (상품 상세)

| RPS 구간  | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고                  |
| --------- | -------- | -------- | -------- | -------- | ------ | --------------------- |
| 50 VU/30s | 4.00     | 12.30    | 15.09    | 19.99    | ~0%    | MAX_PRODUCT_ID=100000 |

### 4.3 주문 목록 — GET /api/v1/orders

| 페이지 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율  | 비고        |
| ----------- | -------- | -------- | -------- | -------- | ------- | ----------- |
| 0~2         | 4.91     | 21.58    | 64.40    | 91.31    | 100.00% | 응답 비200  |
| 50~100      | TODO     | TODO     | TODO     | TODO     | TODO    | OFFSET 영향 |

### 4.4 주문 상세 — GET /api/v1/orders/{id}

| RPS 구간  | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율  | 비고          |
| --------- | -------- | -------- | -------- | -------- | ------- | ------------- |
| 50 VU/30s | 2.58     | 7.00     | 9.26     | 14.85    | 100.00% | 404 응답 다수 |

### 4.5 좋아요 — POST/DELETE /api/v1/likes

| 시나리오       | RPS(TPS) | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율  | 비고               |
| -------------- | -------- | -------- | -------- | -------- | -------- | ------- | ------------------ |
| 좋아요 분산    | ~934     | 4.68     | 11.53    | 75.61    | 559.60   | 100.00% | 응답 비200         |
| 상위 상품 집중 | ~969     | 2.41     | 8.07     | 17.12    | 94.25    | 100.00% | Row lock 경합 관찰 |

## 5. DB 레벨 (선택)

- 실제 QueryDSL/JPQL이 생성하는 **PLP 쿼리**(product + likes LEFT JOIN, GROUP BY, ORDER BY)에 대해 MySQL `EXPLAIN` 및 수행시간(ms)을 측정해 두면, 인덱스 도입 시 비교용 baseline이 된다.
