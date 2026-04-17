# MV 기반 주간/월간 랭킹 배치 시스템 구축

## Summary

- Spring Batch + Partitioning으로 `product_metrics`(일간 메트릭)를 주간/월간 단위로 합산하여 MV 테이블에 TOP 100 랭킹 적재
- Ranking API 확장: `scope=weekly|monthly` 요청 시 MV 단일 소스로 조회, 전일 MV fallback
- E2E 테스트 7/7 통과 + 시간 윈도우별 랭킹 차이 검증

## 변경 사항

### 1. Spring Batch Job — Partitioning + Map-Reduce 3-Step 구조

```
ProductRankingMvJob
  ├── Step 1: CleanupTasklet — DELETE MV + staging + 3일 이전 정리
  ├── Step 2: Partitioned Aggregate (4 Worker 병렬)
  │   └── JdbcCursorItemReader(GROUP BY + LOG10 score) → staging INSERT
  └── Step 3: Merge — ROW_NUMBER() OVER → Global TOP 100 → MV INSERT
```

**파일**:
- `ProductRankingMvJobConfig.java` — Job + 3 Step + Partitioner + Reader + Writer
- `CleanupTasklet.java` — DELETE + 데이터 보존 정책 (3일 보존)

### 2. MV 테이블 + 스테이징 테이블

- `mv_product_rank_weekly` / `mv_product_rank_monthly` — 최종 TOP 100 적재
- `mv_product_rank_staging` — 파티션별 병렬 집계 결과 수집 (Global TOP 100 추출 전 중간 저장)

### 3. Ranking API 확장

- `RankingFacade` 수정: `scope=daily` → Redis, `scope=weekly|monthly` → MV 단일 소스
- 전일 MV fallback: 당일 배치 실패 시 전일 MV 결과 반환 (같은 공식, 1일 stale)
- `MvProductRank` 엔티티 + Repository + JPA 구현체

### 4. E2E 테스트

7개 시나리오 모두 통과:

| 시나리오 | 검증 포인트 |
|---------|-----------|
| 주간 정상 (150개 상품) | TOP 100 적재, 1위 정확성, 전체 파이프라인 |
| 주간 100개 미만 | LIMIT 100이지만 있는 만큼만 |
| 월간 정상 (30일) | monthly 테이블 분기 |
| 멱등성 (2회 실행) | 중복 없이 동일 결과 |
| 데이터 없음 | Job COMPLETED, 빈 MV |
| 부분 데이터 (3일) | 있는 만큼만 집계 |
| 취소 반영 | 순매출 기준 순위 결정 |

---

## 설계 판단과 트레이드오프

### 1. 왜 Partitioning인가 — CursorReader의 장점을 유지하면서 병렬 처리

GROUP BY 집계 쿼리에서 Reader 선택은 제한적이다:

- **PagingReader**: 페이지마다 GROUP BY를 재실행한다. 상품 100만 × 30일 = 3,000만 행 GROUP BY를 페이지 수만큼 반복 → 대규모에서 치명적
- **CursorReader**: GROUP BY를 1회 실행하고 결과를 스트리밍한다. 하지만 ResultSet이 공유 상태를 갖기 때문에 멀티스레드에서 사용 불가

Partitioning은 이 딜레마를 해결한다. product_id 범위로 데이터를 분할하여, 각 Worker가 **독립 커넥션 + 독립 CursorReader**로 자기 범위만 GROUP BY한다. CursorReader의 장점(1회 쿼리)을 유지하면서 병렬 처리를 달성한다.

```
단일 CursorReader:  GROUP BY 3,000만 행 1회 → ~30초
Partitioning (4):   GROUP BY 750만 행 × 4 병렬 → ~10초
```

**참고 자료**:
- [Scaling and Parallel Processing — Spring Batch Reference](https://docs.spring.io/spring-batch/reference/scalability.html): Partitioning은 각 Worker가 독립 Step으로 실행. IO-intensive Step에 유용
- [ColumnRangePartitioner — SpringOne2GX 2014](https://github.com/SpringOne2GX-2014/spring-batch-performance-tuning/blob/master/sample_code/remote-partitioning/remote-partitioning-master/src/main/java/io/spring/remotepartitioningmaster/partition/ColumnRangePartitioner.java): MIN/MAX → 범위 분할 → ExecutionContext 패턴
- [Partitioner 성능 개선 사례](https://prostars.net/357): 파티션 1→5, 30초→17초 (1.8배 향상)
- [Netflix Distributed Counter](https://netflixtechblog.com/netflixs-distributed-counter-abstraction-8d0c45eb66b2): 시간 기반 파티셔닝 + 병렬 집계 → merge 패턴
- [Shopify BFCM Flink](https://shopify.engineering/bfcm-live-map-2021-apache-flink-redesign): 윈도우 분할 → 독립 집계 → 머지

### 2. Score 계산을 SQL에서 처리한 이유 — DB가 잘하는 일은 DB에서

처음에는 Reader에서 메트릭을 읽고, Processor에서 Java로 score를 계산하는 구조였다. 하지만 MySQL에 `LOG10()` 함수가 있고, `ORDER BY score DESC LIMIT 100`으로 TOP 100까지 DB에서 결정할 수 있다.

SQL 실행 순서(GROUP BY → SELECT → ORDER BY → LIMIT)에 의해, **DB가 전체 상품의 score를 계산하고 정렬한 후 상위 100건만 반환**한다. Java로 수만 건을 읽어와서 정렬/필터링하는 것은 DB가 이미 최적화된 작업을 애플리케이션에서 반복하는 것이다.

### 3. MV 단일 소스 원칙 — Redis fallback을 제거한 이유

처음에는 "MV primary, Redis fallback" 구조였다. 하지만 Redis(지수 감쇠)와 MV(균등 합산)는 **같은 기간에 대해 다른 순위를 반환**한다.

```
정상 시: MV(균등 합산) → 상품 A가 1위
MV 장애: Redis(지수 감쇠) → 상품 B가 1위
→ 소스 전환 시 순위가 바뀌는 데이터 불일치
```

다른 공식의 결과를 같은 API의 fallback으로 쓰는 것은 데이터 일관성을 깨뜨린다. 대신 **전일 MV fallback**을 도입했다. 전일 MV는 같은 공식, 같은 소스에서 계산한 결과이므로, 1일 stale이지만 순위 불일치는 발생하지 않는다.

### 4. 전체 재계산 vs 증분 계산 — Late-Arriving Fact

매일 30일치를 처음부터 GROUP BY하는 대신, "어제 결과 - 가장 오래된 날 + 오늘"로 증분 계산하면 93% 데이터 절감이 가능하다. 하지만 이커머스에서 주문 취소/환불은 원주문과 다른 날에 발생한다:

```
4/10: 상품 A 주문 100건 (1000만원)
4/15: 그 중 30건 취소 → product_metrics 4/10 행의 cancel_by_order_date 갱신

증분: 4/10의 값은 이미 어제 MV에 반영됨 → 사후 변경을 감지 못함
전체 재계산: 4/10~4/16 전체를 다시 읽음 → 변경된 값이 자동 반영
```

증분 계산은 "과거 데이터가 불변"이라는 전제가 필요하지만, Late-Arriving Fact 설계가 이 전제를 깨뜨린다. 성능 차이(Partitioning 4 Worker 기준 ~10초 vs ~3초)는 1일 1회 배치에서 운영 영향이 없다.

### 5. Chunk vs Tasklet — 운영 기능의 가치

이 작업은 Tasklet(`INSERT INTO...SELECT + RANK() OVER + LIMIT 100`)으로도 가능하다. 네트워크 효율만 따지면 Tasklet이 우위다. 하지만 Chunk를 선택하면 Spring Batch의 운영 기능을 활용할 수 있다:

- `faultTolerant + retry(3) + ExponentialBackOffPolicy`: 일시적 DB 에러(데드락, 타임아웃) 시 100ms → 200ms → 400ms 간격 재시도
- `StepExecution` 자동 기록: 각 Worker별 readCount, writeCount 추적
- `StepMonitorListener`: 실패 시 알림

100건에 대한 네트워크 왕복 비용(< 1ms)보다 이 운영 기능의 가치가 크다.

### 6. 균등 합산 vs 지수 감쇠 — 공개 랭킹 보드의 비즈니스 의미

Redis monthly는 지수 감쇠(`daily × 0.97^i`, 반감기 약 23일)로 최근 트렌드를 우대한다. MV도 같은 방식을 쓸 수 있지만, **MV가 Redis와 같은 결과를 내면 MV를 만든 이유가 없다.**

"이번 달 베스트셀러"는 총 판매량 기준이 이커머스 업계 표준이다. 균등 합산은 이 비즈니스 의미에 부합한다:
- MD/상품기획팀: "이번 달 어떤 상품이 가장 많이 팔렸나?" → 총 실적
- 소비자: "다들 뭘 사고 있나?" → 총 판매량 순위
- 경영진: "매출 기여도가 높은 상품은?" → 총 매출 기준

---

## 테스트 결과

### E2E 테스트: 9/9 PASSED

| 항목 | 값 |
|------|-----|
| DB | MySQL 8.0 (Testcontainers) |
| 테스트 클래스 | `ProductRankingMvJobE2ETest` |
| 데이터 | 테스트마다 독립 시드 (JdbcTemplate) |
| 결과 | **9/9 PASSED** (기능 7 + 시각화 1 + 대규모 1) |

### 실환경 검증: 1,020개 상품 × 30일 메트릭

6가지 트렌드 패턴(급상승, 장기강자, 하락, 바이럴, 취소, 일반)으로 30일 데이터를 생성하여 **일간/주간/월간 랭킹이 실제로 서로 다른 결과**를 보여주는 것을 확인했다:

| 순위 | 일간 (Redis) | 주간 (MV) | 월간 (MV) |
|:----:|-------------|-----------|-----------|
| 1 | 바이럴 상품 (오늘 폭발) | 급상승 상품 (최근 7일 폭발) | 장기강자 (30일 꾸준) |

같은 데이터, 같은 Score 공식인데 시간 윈도우만 달라도 TOP 20이 완전히 달라진다. 이것이 일간/주간/월간 랭킹을 별도 제공하는 이유이며, Lambda Architecture에서 Speed Layer(Redis)와 Batch Layer(MV)가 공존하는 이유다.

### 성능

| 규모 | 상품 수 | 메트릭 행 수 | weekly | monthly |
|------|--------|------------|--------|---------|
| 기능 테스트 | 150 | 1,050 | ~90ms | — |
| 실환경 검증 | 1,020 | 30,600 | 275ms | 309ms |
| **대규모 테스트** | **100,000** | **3,000,000** | **2,205ms** | **2,564ms** |

10만 상품 × 30일(300만 행)에서 4 Partition 병렬 집계 + Merge까지 약 2.5초. 데이터 100배 증가 시 소요 시간 ~8배 증가 (sub-linear scaling).

---

## 리뷰 포인트

### 1. Partitioning + CursorReader 조합

요구사항에 "대량의 데이터를 읽고 처리할 수 있도록 구성"이 명시되어 있어, 활성 상품 수가 수십만~수백만 규모를 가정하고 읽기(Group By 집계) 성능을 고민해봤습니다. 단일 스레드에서 GROUP BY를 실행하면 데이터가 증가함에 따라 점차 속도도 증가할 것이므로, 병렬 처리가 필요하다고 판단했습니다.

GROUP BY 집계 쿼리에서 PagingReader는 페이지마다 집계를 재실행하므로 부적합하고, CursorReader는 1회 실행으로 효율적이지만 ResultSet 공유 상태 때문에 멀티스레드에서 사용이 어려워서 병렬 처리에 직접 활용하기 어렵다고 생각했습니다.

그래서 Spring Batch의 `ColumnRangePartitioner` 샘플을 참고해서, Partitioning으로 product_id 범위를 분할하여 각 Worker가 독립 CursorReader를 갖도록 했습니다. 각 파티션이 독립 Step 인스턴스로 실행되므로 읽기의 thread-safety 문제가 발생하지 않고, 쓰기 시에도 product_id 범위가 겹치지 않아 staging INSERT 충돌이 없는 것을 확인했습니다.

의견을 구하고 싶은 점:
- **gridSize를 4로 고정**했는데, 커넥션 풀 크기나 CPU 코어 수에 연동하거나 데이터 볼륨에 따라 동적으로 조정하는 것이 바람직한지?
- **스테이징 테이블에 전체 상품 집계 결과를 적재**한 후 mergeStep에서 TOP 100만 추출하는 구조인데, 상품 수가 많아지면 스테이징 적재 비용이 커집니다. 이 중간 저장 비용 대비 Partitioning의 병렬 처리 이점이 충분한지?

### 2. MV 단일 소스 + 전일 fallback

Redis(지수 감쇠)와 MV(균등 합산)는 다른 공식이므로, MV 장애 시 Redis로 전환하면 순위가 바뀝니다. 이를 피하기 위해 Redis fallback을 제거하고, 전일 MV를 fallback으로 사용합니다 (같은 공식, 1일 stale).

"잘못된 순위를 보여주는 것보다 약간 오래된 정확한 순위가 낫다"는 판단인데, 이 접근에 대한 의견을 구합니다.

### 3. Score 계산을 SQL에 넣은 것에 대하여

Score 공식(`LOG10 + 가중치`)을 Reader SQL에 넣어서 DB가 집계 + score + 정렬 + TOP 100을 한 번에 처리합니다. Java의 RankingCorrectionJob에도 동일한 공식이 있어 이중 관리가 됩니다.

두 Job은 입력이 다르고(일간 메트릭 vs 기간 합산 메트릭), 가중치는 `application.yml`에서 관리하므로 합리적 중복이라고 판단했습니다. 공식 일원화가 필요한지 의견을 구합니다.
