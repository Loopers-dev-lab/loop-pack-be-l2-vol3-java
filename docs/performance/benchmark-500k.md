# 벤치마크 — 50만 건 (500k)

> 상품 50만 건 기준 AS-IS API 성능 측정 결과. 공통 절차: [README.md](README.md)

## 1. 테스트 환경

| 항목      | 값                                                             |
| --------- | -------------------------------------------------------------- |
| 상품 수   | 500,000건                                                      |
| 브랜드 수 | 500개 (시더 기준)                                              |
| 시드      | `ProductDataSeeder` (perf-seed), `--product.seed.count=500000` |

## 2. 시드 초기화 및 재호출

1. DB 초기화: 스키마 재생성 또는 `product`, `brand` 등 관련 테이블 truncate.
2. 시드만 실행 후 종료:
   ```bash
   ./gradlew bootRun -p apps/commerce-api --args='--spring.profiles.active=local,perf-seed --product.seed.count=500000 --perf.seed.exit-after-run=true'
   ```
3. 동일 규모로 서버 기동 후 부하 테스트:
   ```bash
   ./gradlew bootRun -p apps/commerce-api --args='--spring.profiles.active=local,perf-seed --product.seed.count=500000'
   ```
4. k6 실행: `./docs/load-test/run-all-k6-for-scale.sh 500000 30`. 인증 필요 API는 [README.md](README.md) 절차 선행.

## 3. index-benchmark 참고 및 현재 프로젝트와의 차이

- **참고 문서**: `index-benchmark-500k.md`, `index-benchmark-1m.md` — DB 레벨, `products.like_count` 컬럼 가정, 10종 쿼리 패턴 및 인덱스 전략별 수행시간(ms).
- **차이점**: 현재 프로젝트는 **`product.like_count` 없음**. 좋아요는 `likes` 테이블 JOIN + GROUP BY + ORDER BY COUNT 로 집계. 따라서 index 벤치마크의 “like_count 컬럼 + 복합 인덱스” 수치는 참고용이며, **실제 API/쿼리**는 JOIN 비용이 추가로 발생.
- **고려할 점**: 브랜드(인기/중간)+정렬, 전체+정렬, COUNT(브랜드/전체), 딥페이징(OFFSET 5000/10000). 50만 건에서 index 벤치마크 기준으로도 단일 (brand_id) 인덱스만 쓰면 브랜드(인기)+정렬이 수백 ms 수준으로 악화됨 — API에서 likes_desc 시 이보다 더 무거울 수 있음.

## 4. API 측정 결과

### 4.1 PLP (상품 목록)

| 정렬       | 페이지 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고            |
| ---------- | ----------- | -------- | -------- | -------- | -------- | ------ | --------------- |
| latest     | 0~2         | 4.72     | 22.21    | 51.75    | 150.38   | 0.00%  |                 |
| price_asc  | 0~2         | 3.83     | 15.11    | 47.18    | 131.67   | 0.00%  |                 |
| likes_desc | 0~2         | 3.13     | 7.30     | 8.95     | 9.43     | 0.00%  | JOIN+GROUP BY   |
| latest     | 50~100      | 3.08     | 7.43     | 14.56    | 21.17    | 0.00%  | OFFSET 딥페이징 |

### 4.2 PDP (상품 상세)

| RPS 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고                  |
| -------- | -------- | -------- | -------- | -------- | ------ | --------------------- |
| 50 VU/30s| 3.46     | 8.41     | 14.75    | 37.37    | ~0%    | MAX_PRODUCT_ID=500000 |

### 4.3 주문 목록 — GET /api/v1/orders

| 페이지 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고        |
| ----------- | -------- | -------- | -------- | -------- | ------ | ----------- |
| 0~2         | 4.94     | 13.78    | 68.76    | 235.17   | 100.00%| 응답 비200  |
| 50~100      | TODO     | TODO     | TODO     | TODO     | TODO   | OFFSET 영향 |

### 4.4 주문 상세 — GET /api/v1/orders/{id}

| RPS 구간 | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고 |
| -------- | -------- | -------- | -------- | -------- | ------ | ---- |
| 50 VU/30s| 2.98     | 7.99     | 25.24    | 52.67    | 100.00%| 404 응답 다수 |

### 4.5 좋아요 — POST/DELETE /api/v1/likes

| 시나리오       | RPS(TPS) | avg (ms) | p95 (ms) | p99 (ms) | max (ms) | 에러율 | 비고               |
| -------------- | -------- | -------- | -------- | -------- | -------- | ------ | ------------------ |
| 좋아요 분산    | ~954     | 2.95     | 8.17     | 22.28    | 763.40   | 100.00%| 응답 비200         |
| 상위 상품 집중 | ~969     | 2.57     | 7.84     | 28.18    | 155.41   | 100.00%| Row lock 경합 관찰 |

## 5. DB 레벨 (선택)

- PLP용 실제 SQL(QueryDSL 생성)에 대해 `EXPLAIN` 및 수행시간(ms) 기록. index-benchmark-500k와 비교 시, like_count 없이 JOIN/집계만 사용할 때의 차이를 정량화할 수 있음.
