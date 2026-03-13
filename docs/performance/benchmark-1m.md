# 벤치마크 — 100만 건 (1m)

> 상품 100만 건 기준 AS-IS API 성능 측정 결과. 공통 절차: [README.md](README.md)

## 1. 테스트 환경

| 항목      | 값                                                              |
| --------- | --------------------------------------------------------------- |
| 상품 수   | 1,000,000건                                                     |
| 브랜드 수 | 500개 (시더 기준)                                               |
| 시드      | `ProductDataSeeder` (perf-seed), `--product.seed.count=1000000` |

## 2. 시드 초기화 및 재호출

1. DB 초기화: 스키마 재생성 또는 `product`, `brand` 등 관련 테이블 truncate.
2. 시드만 실행 후 종료 (100만 건은 시드 시간이 길어질 수 있음):
   ```bash
   ./gradlew bootRun -p apps/commerce-api --args='--spring.profiles.active=local,perf-seed --product.seed.count=1000000 --perf.seed.exit-after-run=true'
   ```
3. 동일 규모로 서버 기동 후 부하 테스트:
   ```bash
   ./gradlew bootRun -p apps/commerce-api --args='--spring.profiles.active=local,perf-seed --product.seed.count=1000000'
   ```
4. k6 실행: `./docs/load-test/run-all-k6-for-scale.sh 1000000 30`. 인증 필요 API는 [README.md](README.md) 절차 선행.

## 3. index-benchmark 참고 및 현재 프로젝트와의 차이

- **참고 문서**: `index-benchmark-1m.md` — 100만 건, 100개 브랜드(Zipf), `products.like_count` 가정, 10종 쿼리·4종 인덱스 전략별 수행시간(ms).
- **차이점**: 현재 프로젝트는 **`product` 테이블에 `like_count` 없음**. 좋아요는 **`likes` 테이블 + LEFT JOIN + GROUP BY + ORDER BY COUNT** 로 집계. index 벤치마크의 “like_count 컬럼 + 인덱스” 결과는 참고용이며, **실제 앱은 JOIN/집계 비용이 추가**되므로 API p95/p99가 더 나쁠 수 있음.
- **고려할 점** (index-benchmark-1m 기준):
  - 인덱스 없음: 전체+좋아요순 ~351ms, 딥페이징 OFFSET 10000 ~620ms.
  - 단일 (brand_id): 브랜드(인기)+정렬이 1.7s대로 악화 — API likes_desc는 이보다 더 무거울 가능성.
  - 복합 (brand_id, deleted_at, like_count DESC): 브랜드+좋아요순 1ms, 딥페이징 OFFSET 5000 ~49ms.
  - 현재 구조에서는 **DB 레벨**에서 동일 10종 쿼리를 “실제 앱 쿼리(JOIN/집계)”로 재현해 EXPLAIN·수행시간을 남기면, 이후 like_count 비정규화/인덱스 도입 시 비교 baseline이 됨.

## 4. API 측정 결과

### 4.1 PLP (상품 목록)

| 정렬       | 페이지 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고            |
| ---------- | ----------- | -------- | -------- | -------- | -------- | ------ | --------------- |
| latest     | 0~2         | 2.01     | 5.31     | 21.81    | 25.06    | 0.00%  |                 |
| price_asc  | 0~2         | 3.83     | 14.48    | 32.85    | 117.72   | 0.00%  |                 |
| likes_desc | 0~2         | 3.22     | 7.25     | 8.21     | 12.81    | 0.00%  | JOIN+GROUP BY   |
| latest     | 50~100      | 2.98     | 6.89     | 16.91    | 21.63    | 0.00%  | OFFSET 딥페이징 |

### 4.2 PDP (상품 상세)

| RPS 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고                   |
| -------- | -------- | -------- | -------- | -------- | ------ | ---------------------- |
| 50 VU/30s| 3.08     | 8.38     | 26.89    | 123.05   | ~0%    | MAX_PRODUCT_ID=1000000 |

### 4.3 주문 목록 — GET /api/v1/orders

| 페이지 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고        |
| ----------- | -------- | -------- | -------- | -------- | ------ | ----------- |
| 0~2         | 4.35     | 18.89    | 63.00    | 142.82   | 100.00%| 응답 비200  |
| 50~100      | TODO     | TODO     | TODO     | TODO     | TODO   | OFFSET 영향 |

### 4.4 주문 상세 — GET /api/v1/orders/{id}

| RPS 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고 |
| -------- | -------- | -------- | -------- | -------- | ------ | ---- |
| 50 VU/30s| 3.00     | 10.09    | 27.17    | 67.33    | 100.00%| 404 응답 다수 |

### 4.5 좋아요 — POST/DELETE /api/v1/likes

| 시나리오       | RPS(TPS) | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고               |
| -------------- | -------- | -------- | -------- | -------- | -------- | ------ | ------------------ |
| 좋아요 분산    | ~939     | 5.31     | 24.34    | 84.83    | 226.43   | 100.00%| 응답 비200         |
| 상위 상품 집중 | ~938     | 3.76     | 12.30    | 59.29    | 262.59   | 100.00%| Row lock 경합 관찰 |

## 5. DB 레벨 (선택)

- PLP 실제 쿼리(product + likes JOIN, GROUP BY, ORDER BY)에 대한 `EXPLAIN` 및 수행시간(ms)을 100만 건 기준으로 측정. index-benchmark-1m의 10종 쿼리와 비교해 “like_count 없이 JOIN만 쓸 때”의 격차를 정량화할 수 있음.
