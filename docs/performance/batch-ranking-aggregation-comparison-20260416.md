# 주간/월간 랭킹 배치 집계 방식 확인 및 비교 실험 (2026-04-16)

## 1. 결론 요약

- 현재 **주간/월간 배치 집계는 DB 쿼리 방식**이다.
- 근거:
  - 배치 집계 단계에서 `product_metrics_daily`를 대상으로 `SUM(...)`, `GROUP BY product_id`, `ORDER BY ranking_score`를 수행한다. `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:142-166`, `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:316-361`
  - 집계 결과는 `product_ranking_weekly_batch`, `product_ranking_monthly_batch`에 저장되고, 이후 Redis top 100으로 동기화된다. `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:168-196`, `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:256-314`
- 조회 시점에도 주간/월간은 Redis 우선 조회 후 miss 시 배치 테이블을 조회한다. 즉, **런타임 애플리케이션 집계 방식이 아니다.** `apps/commerce-api/src/main/java/com/loopers/infrastructure/ranking/redis/RedisProductRankingRepository.java:55-80`, `apps/commerce-api/src/main/java/com/loopers/infrastructure/ranking/redis/RedisProductRankingRepository.java:168-218`

## 2. 현재 구현 해석

### 2.1 배치 내부에서 무엇을 하는가

주간/월간 배치는 아래 3단계로 구성된다.

1. 기존 스냅샷 삭제
2. `product_metrics_daily`를 기간 조건으로 집계해 배치 테이블 적재
3. 배치 테이블 상위 100개를 Redis sorted set으로 동기화

관련 근거:
- 주간/월간 step 구성 `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:118-196`
- 집계 SQL `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:316-361`
- 배치 테이블 upsert `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:364-397`

핵심은 `JdbcPagingItemReader`가 이미 **DB에서 집계된 결과 행**을 읽고 있다는 점이다. Processor는 기간 메타데이터를 붙여 `ProductRankingPeriodBatchRow`로 매핑만 한다. `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:220-254`

따라서 현재 구현은 다음 분류가 맞다.

- 일간/시간별: Redis 기반 랭킹 조회
- 주간/월간 배치 생성: **DB 집계 쿼리 방식**
- 주간/월간 조회: Redis fallback + 배치 테이블 조회

## 3. 비교 실험 방법

비교용 테스트는 동일 데이터셋에 대해 아래 두 방식을 같은 점수식으로 실행한다.

- DB 집계 방식: MySQL에서 `SUM + GROUP BY + ORDER BY + LIMIT 100`
- 애플리케이션 방식: 기간 데이터 전체를 읽되, JVM에서 `HashMap` 누적 집계를 수행한 뒤 정렬

비교 테스트 구현 근거:
- 실험 데이터 크기, 반복 횟수, top 100 제한 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsAggregationStrategyComparisonTest.java:38-46`
- 수동 실행 활성화와 데이터셋 크기 파라미터 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsAggregationStrategyComparisonTest.java:62-88`
- DB 집계 구현 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsAggregationStrategyComparisonTest.java:122-152`
- 애플리케이션 집계 구현(행 단위 스트리밍 누적) `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsAggregationStrategyComparisonTest.java:155-200`
- 입력 데이터 적재 구현 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsAggregationStrategyComparisonTest.java:210-235`

### 3.1 실험 데이터셋

이번에는 두 규모를 측정했다.

#### 데이터셋 A
- 상품 수: `5,000`
- 입력 기간: `30일`
- 총 입력 행 수: `150,000`
- 측정 반복: `5회`

#### 데이터셋 B
- 상품 수: `50,000`
- 입력 기간: `30일`
- 총 입력 행 수: `1,500,000`
- 측정 반복: `5회`

공통 비교 대상:
- 주간 집계: 최근 7일
- 월간 집계: 최근 30일

### 3.2 실행 환경

- Docker Desktop 기반 Docker daemon
- Testcontainers가 Docker Desktop Unix socket에 정상 연결됨 `apps/commerce-batch/build/test-results/test/TEST-com.loopers.batch.job.ProductMetricsAggregationStrategyComparisonTest.xml:23-32`
- 테스트 실행 예시:

```bash
RUN_BATCH_AGGREGATION_COMPARISON=true \
JAVA_TOOL_OPTIONS='-DbatchAggregationComparison.productCount=5000' \
./gradlew :apps:commerce-batch:test \
  --tests "com.loopers.batch.job.ProductMetricsAggregationStrategyComparisonTest" \
  --rerun-tasks

RUN_BATCH_AGGREGATION_COMPARISON=true \
JAVA_TOOL_OPTIONS='-DbatchAggregationComparison.productCount=50000' \
./gradlew :apps:commerce-batch:test \
  --tests "com.loopers.batch.job.ProductMetricsAggregationStrategyComparisonTest" \
  --rerun-tasks
```

## 4. 실험 결과

### 4.1 데이터셋 A: 5,000 상품 / 150,000행

원본 로그: `apps/commerce-batch/build/test-results/test/TEST-com.loopers.batch.job.ProductMetricsAggregationStrategyComparisonTest.xml:69-70` 는 마지막 실행값만 남기므로, 아래 수치는 5,000개 재실행 로그 기준으로 정리했다.

#### 주간 집계
- 기간: `2025-09-03 ~ 2025-09-09`
- top rows: `100`
- DB median: `34.815083ms`
- Application median: `41.009041ms`
- 결과: **애플리케이션 방식이 1.18배 느림**

세부 실행값:
- DB: `[35.482583, 34.815083, 34.478083, 50.673417, 34.371625]`
- Application: `[45.378583, 41.009041, 40.52475, 44.44925, 40.137042]`

#### 월간 집계
- 기간: `2025-08-11 ~ 2025-09-09`
- top rows: `100`
- DB median: `140.398791ms`
- Application median: `170.477125ms`
- 결과: **애플리케이션 방식이 1.21배 느림**

세부 실행값:
- DB: `[142.280292, 140.126417, 143.430125, 132.274125, 140.398791]`
- Application: `[174.828417, 172.935958, 167.356166, 170.477125, 166.66]`

### 4.2 데이터셋 B: 50,000 상품 / 1,500,000행

원본 로그: `apps/commerce-batch/build/test-results/test/TEST-com.loopers.batch.job.ProductMetricsAggregationStrategyComparisonTest.xml:69-70`

#### 주간 집계
- 기간: `2025-09-03 ~ 2025-09-09`
- top rows: `100`
- DB median: `2718.979583ms`
- Application median: `657.715167ms`
- 결과: **애플리케이션 방식이 더 빠름** (`ratio=0.24x`, 즉 애플리케이션 시간이 DB의 약 24%)

세부 실행값:
- DB: `[2718.979583, 3077.759917, 3996.098709, 1317.537708, 1231.053458]`
- Application: `[841.625458, 1426.455166, 657.715167, 364.949792, 387.147208]`

#### 월간 집계
- 기간: `2025-08-11 ~ 2025-09-09`
- top rows: `100`
- DB median: `4994.464875ms`
- Application median: `2111.745458ms`
- 결과: **애플리케이션 방식이 더 빠름** (`ratio=0.42x`, 즉 애플리케이션 시간이 DB의 약 42%)

세부 실행값:
- DB: `[4994.464875, 4926.372708, 5598.077917, 5830.131666, 4975.036625]`
- Application: `[1757.879291, 1883.267416, 2450.618125, 2111.745458, 2194.016584]`

## 5. 해석

### 5.1 작은 데이터셋에서는 DB 집계가 우위

`5,000 상품 / 150,000행` 규모에서는 DB가 필터링, 집계, 정렬, 상위 100개 제한을 한 번에 수행하는 편이 더 유리했다. `apps/commerce-batch/src/main/java/com/loopers/batch/job/ProductMetricsDailyAggregationJobConfig.java:318-338`

### 5.2 큰 데이터셋에서는 현재 조건상 애플리케이션 스트리밍 집계가 우위

`50,000 상품 / 1,500,000행` 규모에서는 결과가 반대로 나왔다. 현재 비교 테스트의 애플리케이션 방식은 전체 행을 `List`로 모두 들고 있지 않고, 읽는 즉시 누적 집계하는 스트리밍 형태다. `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsAggregationStrategyComparisonTest.java:155-184`

즉 이번 50,000개 실험의 의미는 다음과 같다.

- **애플리케이션 집계 자체가 무조건 느린 것은 아니다.**
- 현재 스키마/인덱스/쿼리 조건에서는 큰 입력 규모에서 DB `GROUP BY + ORDER BY + LIMIT` 비용이 더 크게 나타났다.
- 다만 이것만으로 운영 설계를 즉시 뒤집을 근거는 부족하다. 실제 배치 설계는 단순 집계 시간뿐 아니라 DB 부하 집중, 배치 윈도우, Redis 적재, 운영 안정성까지 함께 봐야 한다.

### 5.3 OOM 이슈와 실험 방법 보정

초기 애플리케이션 비교 구현은 기간 전체 행을 `List`로 한 번에 읽는 방식이어서 `50,000 상품 / 1,500,000행`에서 OOM이 발생했다. 이후 행 단위 스트리밍 누적으로 바꿔 대규모 데이터셋도 측정 가능하게 보정했다. 스트리밍 구현 근거는 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsAggregationStrategyComparisonTest.java:155-184` 이다.

따라서 이번 문서의 50,000개 결과는 **현실적인 애플리케이션 집계 구현(스트리밍)** 기준 비교값으로 보는 것이 맞다.

## 6. 왜 Colima 얘기가 나왔는가

원인 확인 결과, 다음 두 근거 때문에 Colima socket 우회 가능성을 먼저 의심했다.

1. 과거 프로젝트 문서에 Colima socket override 실행 예가 남아 있었다. `docs/performance/k6/payment-pg-resilience-tuning-20260320.md:51-54`
2. 로컬 Testcontainers 설정이 Unix socket 전략을 강제하고 있었다. `/Users/anseonghun/.testcontainers.properties:1-4`

다만 이번 작업에서는 사용자 요청대로 **Docker Desktop 기준으로 전환 후 재실행**했고, 최종 실험도 Docker Desktop에서 성공했다. Docker Desktop 연결 확인 근거는 `apps/commerce-batch/build/test-results/test/TEST-com.loopers.batch.job.ProductMetricsAggregationStrategyComparisonTest.xml:23-32` 이다.

## 7. end-to-end 측정 추가 결과

사용자 요청에 따라, 순수 집계 시간 비교와 별도로 **실제 배치 적재 + Redis 동기화까지 포함한 end-to-end 측정**을 추가했다.

### 7.1 측정 범위

주간/월간 잡의 실제 실행 경로를 그대로 측정했다.

1. `product_metrics_daily` 기간 집계
2. `product_ranking_weekly_batch` 또는 `product_ranking_monthly_batch` 적재
3. Redis top 100 sorted set 동기화
4. 실행 중 MySQL global status delta와 Docker MySQL CPU 최대치 샘플링

측정 코드 근거:
- 주간 E2E 측정 진입점 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsWeeklyAggregationJobIntegrationTest.java:172-227`
- 월간 E2E 측정 진입점 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsMonthlyAggregationJobIntegrationTest.java:132-197`
- MySQL `SHOW GLOBAL STATUS` 수집 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsWeeklyAggregationJobIntegrationTest.java:402-427`, `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsMonthlyAggregationJobIntegrationTest.java:362-387`
- Docker MySQL CPU 샘플링 `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsWeeklyAggregationJobIntegrationTest.java:673-738`, `apps/commerce-batch/src/test/java/com/loopers/batch/job/ProductMetricsMonthlyAggregationJobIntegrationTest.java:617-682`

실행 명령:

```bash
RUN_BATCH_AGGREGATION_E2E_COMPARISON=true \
JAVA_TOOL_OPTIONS='-DrunBatchAggregationE2eComparison=true -DbatchAggregationE2e.productCount=5000 -DbatchAggregationE2e.iterations=3' \
./gradlew :apps:commerce-batch:test \
  --tests "com.loopers.batch.job.ProductMetricsWeeklyAggregationJobIntegrationTest.measuresWeeklyAggregationJobEndToEndPerformance" \
  --tests "com.loopers.batch.job.ProductMetricsMonthlyAggregationJobIntegrationTest.measuresMonthlyAggregationJobEndToEndPerformance" \
  --rerun-tasks
```

### 7.2 측정 결과

#### 데이터셋 A
- 상품 수: `5,000`
- 주간 입력 기간: `7일`
- 월간 입력 기간: `30일`
- Redis 적재 대상: `top 100`
- 반복 횟수: `3회`

##### 주간 E2E
- wall median: `139.297584ms`
- thread CPU median: `41.546ms`
- process CPU util median: `11.67%`
- Docker MySQL CPU max median: `1.58%`
- Redis 적재 row 수: `100`
- MySQL tmp tables median: `2`
- MySQL tmp disk tables median: `0`
- MySQL sort rows median: `200`
- MySQL sort scan median: `2`
- MySQL sort merge passes median: `0`

근거 로그: `apps/commerce-batch/build/test-results/test/TEST-com.loopers.batch.job.ProductMetricsWeeklyAggregationJobIntegrationTest.xml:96`

##### 월간 E2E
- wall median: `229.959458ms`
- thread CPU median: `50.475ms`
- process CPU util median: `7.42%`
- Docker MySQL CPU max median: `0.77%`
- Redis 적재 row 수: `100`
- MySQL tmp tables median: `2`
- MySQL tmp disk tables median: `0`
- MySQL sort rows median: `200`
- MySQL sort scan median: `2`
- MySQL sort merge passes median: `0`

근거 로그: `apps/commerce-batch/build/test-results/test/TEST-com.loopers.batch.job.ProductMetricsMonthlyAggregationJobIntegrationTest.xml:123`

#### 데이터셋 B
- 상품 수: `50,000`
- 주간 입력 기간: `7일`
- 월간 입력 기간: `30일`
- Redis 적재 대상: `top 100`
- 반복 횟수: `3회`

##### 주간 E2E
- wall median: `5000.237083ms`
- thread CPU median: `122.023ms`
- process CPU util median: `0.93%`
- Docker MySQL CPU max median: `207.32%`
- Redis 적재 row 수: `100`
- MySQL tmp tables median: `2`
- MySQL tmp disk tables median: `1`
- MySQL sort rows median: `200`
- MySQL sort scan median: `2`
- MySQL sort merge passes median: `0`

근거 로그: `apps/commerce-batch/build/test-results/test/TEST-com.loopers.batch.job.ProductMetricsWeeklyAggregationJobIntegrationTest.xml:96`

##### 월간 E2E
- wall median: `4580.84225ms`
- thread CPU median: `47.609ms`
- process CPU util median: `0.31%`
- Docker MySQL CPU max median: `106.69%`
- Redis 적재 row 수: `100`
- MySQL tmp tables median: `2`
- MySQL tmp disk tables median: `1`
- MySQL sort rows median: `200`
- MySQL sort scan median: `2`
- MySQL sort merge passes median: `0`

근거 로그: `apps/commerce-batch/build/test-results/test/TEST-com.loopers.batch.job.ProductMetricsMonthlyAggregationJobIntegrationTest.xml:123`

### 7.3 해석

이번 end-to-end 측정으로 확인된 점은 다음과 같다.

1. **Redis 적재 포함 경로도 정상적으로 top 100까지만 동기화된다.**
   - 5,000 / 50,000 상품 모두 `redisRows=100`으로 기록됐다.
2. **5,000 상품에서는 disk temporary table이 없었지만, 50,000 상품에서는 발생했다.**
   - 5,000 상품: `tmpDiskTablesMedian=0`
   - 50,000 상품: `tmpDiskTablesMedian=1`
3. **정렬 자체는 두 규모 모두 발생했지만, merge pass는 관측되지 않았다.**
   - 두 규모 모두 `sortRowsMedian=200`, `sortScanMedian=2`, `sortMergePassesMedian=0`
4. **큰 규모에서는 MySQL CPU 사용이 확실히 커진다.**
   - 주간 `207.32%`, 월간 `106.69%`
5. **wall time도 큰 폭으로 증가했다.**
   - 주간: `139ms -> 5000ms`
   - 월간: `230ms -> 4581ms`
6. 따라서 `50,000 상품` 조건에서는, 기존에 우려했던 `temporary/filesort` 및 DB 부하가 실제 병목 후보로 올라온다.

즉, `5,000 상품`에서는 구조 변경 근거가 약했지만, `50,000 상품`에서는 **DB 집계 경로의 부하 확대가 실제로 관측됐다.**

## 8. 최종 판단

- 현재 주간/월간 배치의 구현 방식 분류는 여전히 **DB 집계 쿼리 방식**이 맞다.
- 성능 비교 결과는 데이터 규모에 따라 달랐다.
  - `5,000 상품 / 150,000행`: DB 우위
  - `50,000 상품 / 1,500,000행`: 애플리케이션 스트리밍 집계 우위
- 추가한 end-to-end 측정에서는 규모별로 차이가 더 분명해졌다.
  - `5,000 상품`: Redis 적재 포함해도 주간 `139ms`, 월간 `230ms`, `tmp_disk_tables=0`
  - `50,000 상품`: 주간 `5000ms`, 월간 `4581ms`, `tmp_disk_tables=1`, MySQL CPU 피크 상승
- 따라서 지금 단계에서 가장 타당한 결론은 다음이다.
  1. **현재 구현 분류는 DB 집계 방식으로 문서화한다.**
  2. **소규모에서는 즉시 구조 변경 필요성이 낮다.**
  3. **대규모에서는 DB 집계 경로가 실제 병목 후보로 관측됐으므로, 실행계획과 인덱스/쿼리 개선 검토가 필요하다.**
  4. **설계 변경 여부는 `EXPLAIN ANALYZE`와 운영 스키마 기준 재측정 후 결정한다.**

## 9. 후속 권장 사항

필요 시 다음 정도까지만 추가 검증하면 충분하다.

1. `EXPLAIN ANALYZE`로 `product_metrics_daily` 집계 계획 확인
2. 실제 운영 스키마 인덱스 기준으로 tmp/filesort 변화 재확인
3. `50,000 상품` 기준으로 인덱스 보강 전/후 E2E 재측정

즉, **구현 방식 확인은 완료**, **순수 집계 비교 실험도 완료**, **Redis 적재 포함 end-to-end 측정도 5,000 / 50,000 규모까지 완료** 상태다.


