# Partitioning 성능 비교 테스트

## 목적

gridSize=1(단일 스레드) vs gridSize=4(4 Partition)의 소요 시간을 비교하여 Partitioning의 효과를 측정한다.

## 요청

### 1. GRID_SIZE를 외부에서 주입 가능하게 변경

`ProductRankingMvJobConfig.java`의 `GRID_SIZE`를 `application.yml` 또는 JobParameter로 주입 가능하게 변경:

```java
// 현재: private static final int GRID_SIZE = 4;
// 변경: application.yml에서 주입
@Value("${ranking.mv.grid-size:4}")
private int gridSize;
```

또는 더 간단하게, 테스트에서만 GRID_SIZE를 1로 바꿔서 돌리는 방법:
- `ProductRankingMvJobConfig`를 상속한 테스트용 Config에서 GRID_SIZE를 override
- 또는 ReflectionTestUtils로 GRID_SIZE를 변경

### 2. 벤치마크 테스트 추가

`ProductRankingMvJobE2ETest`에 추가:

```java
@Test
@DisplayName("벤치마크 — gridSize=1 vs gridSize=4 소요 시간 비교")
void partitionBenchmark() throws Exception {
    int productCount = 100_000;
    seedProductsBulk(productCount);
    seedMetricsBulkWithTrends(productCount, 30, TARGET_DATE);

    // gridSize=1로 실행
    // (GRID_SIZE를 1로 변경하는 방법 적용)
    long t0 = System.currentTimeMillis();
    runJob("weekly");
    long singleMs = System.currentTimeMillis() - t0;

    // cleanup
    jdbcTemplate.update("DELETE FROM mv_product_rank_weekly WHERE period_key = ?", TARGET_DATE);
    jdbcTemplate.update("DELETE FROM mv_product_rank_staging WHERE period_key = ?", TARGET_DATE);

    // gridSize=4로 실행
    // (GRID_SIZE를 4로 복원)
    t0 = System.currentTimeMillis();
    runJob("weekly");
    long partitionedMs = System.currentTimeMillis() - t0;

    System.out.println("═══════════════════════════════════════");
    System.out.println("  Partitioning 벤치마크 (10만 상품)");
    System.out.println("═══════════════════════════════════════");
    System.out.printf("  gridSize=1: %,dms%n", singleMs);
    System.out.printf("  gridSize=4: %,dms%n", partitionedMs);
    System.out.printf("  향상률:     %.1fx%n", (double) singleMs / partitionedMs);
    System.out.println("═══════════════════════════════════════");
}
```

### 3. 결과 기록

PR draft(`10-pr-draft.md`)의 Partitioning 섹션을 업데이트:

```
10만 상품 × 30일(300만 행) 기준 측정값:

gridSize=1 (단일): weekly ?ms
gridSize=4 (병렬): weekly ?ms
향상률: ?x
```

### 4. 주의사항

- 시드 데이터는 한 번만 생성하고, gridSize만 바꿔서 2회 실행
- 각 실행 전 MV + staging을 DELETE
- Testcontainers MySQL의 `innodb-buffer-pool-size=256M` 설정 확인
- JVM `-Xmx2g` 설정 확인
