# 벤치마크 — 20만 건 (200k)

> 상품 20만 건 기준 AS-IS API 성능 측정 결과. 공통 절차: [README.md](README.md)

## 1. 테스트 환경

| 항목      | 값                                                             |
| --------- | -------------------------------------------------------------- |
| 상품 수   | 200,000건                                                      |
| 브랜드 수 | 500개 (시더 기준)                                              |
| 시드      | `ProductDataSeeder` (perf-seed), `--product.seed.count=200000` |

## 2. 시드 초기화 및 재호출

1. DB 초기화: 스키마 재생성 또는 `product`, `brand` 등 관련 테이블 truncate.
2. 시드만 실행 후 종료:
   ```bash
   ./gradlew bootRun -p apps/commerce-api --args='--spring.profiles.active=local,perf-seed --product.seed.count=200000 --perf.seed.exit-after-run=true'
   ```
3. 동일 규모로 서버 기동 후 부하 테스트:
   ```bash
   ./gradlew bootRun -p apps/commerce-api --args='--spring.profiles.active=local,perf-seed --product.seed.count=200000'
   ```
4. k6 실행: `./docs/load-test/run-all-k6-for-scale.sh 200000 30`. 인증 필요 API는 [README.md](README.md) 절차 선행.

## 3. index-benchmark 참고 및 현재 프로젝트와의 차이

- **참고 문서**: `index-benchmark-500k.md` / `index-benchmark-1m.md` (DB 레벨, `products.like_count` 가정).
- **차이점**: 현재 프로젝트는 **`product`에 `like_count` 없음**. `likes` 테이블 JOIN + GROUP BY + ORDER BY COUNT 로 집계하므로, index 벤치마크의 like_count 인덱스 수치는 직접 적용 불가. API 측정은 실제 앱 쿼리 기준으로 해석.
- **고려할 점**: 브랜드(인기/중간)+정렬, 전체+정렬, COUNT, 딥페이징(OFFSET) — index 벤치마크와 동일 패턴을 API 레벨에서 관찰.

## 4. API 측정 결과

### 4.1 PLP (상품 목록)

| 정렬       | 페이지 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고            |
| ---------- | ----------- | -------- | -------- | -------- | -------- | ------ | --------------- |
| latest     | 0~2         | 4.38     | 15.07    | 52.35    | 70.03    | 0.00%  |                 |
| price_asc  | 0~2         | 4.58     | 12.95    | 46.05    | 49.72    | 0.00%  |                 |
| likes_desc | 0~2         | 4.56     | 15.95    | 44.90    | 82.64    | 0.00%  | JOIN+GROUP BY   |
| latest     | 50~100      | 3.92     | 12.22    | 43.02    | 49.82    | 0.00%  | OFFSET 딥페이징 |

### 4.2 PDP (상품 상세)

| RPS 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고                  |
| -------- | -------- | -------- | -------- | -------- | ------ | --------------------- |
| 50 VU/30s| 5.95     | 18.55    | 69.05    | 185.61   | ~0%    | MAX_PRODUCT_ID=200000 |

### 4.3 주문 목록 — GET /api/v1/orders

| 페이지 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고        |
| ----------- | -------- | -------- | -------- | -------- | ------ | ----------- |
| 0~2         | 6.72     | 15.83    | 159.80   | 218.89   | 100.00%| 응답 비200  |
| 50~100      | TODO     | TODO     | TODO     | TODO     | TODO   | OFFSET 영향 |

### 4.4 주문 상세 — GET /api/v1/orders/{id}

| RPS 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고 |
| -------- | -------- | -------- | -------- | -------- | ------ | ---- |
| 50 VU/30s| 4.37     | 13.64    | 45.10    | 275.12   | 100.00%| 404 응답 다수 |

### 4.5 좋아요 — POST/DELETE /api/v1/likes

| 시나리오       | RPS(TPS) | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고               |
| -------------- | -------- | -------- | -------- | -------- | -------- | ------ | ------------------ |
| 좋아요 분산    | ~802     | 12.91    | 65.96    | 188.63   | 1070.00  | 100.00%| 응답 비200         |
| 상위 상품 집중 | ~866     | 9.37     | 44.49    | 133.79   | 529.42   | 100.00%| Row lock 경합 관찰 |

## 5. DB 레벨 (선택)

- PLP 실제 쿼리(product + likes JOIN, GROUP BY, ORDER BY)에 대한 `EXPLAIN` 및 수행시간(ms) 측정 시, 50만/100만 규모와 비교용 baseline으로 활용.
