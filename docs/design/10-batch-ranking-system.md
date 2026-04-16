# 10. 배치 랭킹 시스템 설계 — MV 기반 주간/월간 랭킹

> Spring Batch로 product_metrics를 기간 집계하여 MV 테이블에 TOP 100 랭킹을 적재하고,
> API에서 주간/월간 요청 시 MV를 primary 소스로 조회하는 시스템.

---

## 설계 결정 요약

| 질문 | 결정 | 근거 |
|------|------|------|
| Score 계산 방식 | **방식 A — 메트릭 합산 후 score 1회 계산** | MV는 DB 원장 기반 정확값을 제공하는 Batch Layer. Redis carry-over 근사치를 복제할 이유 없음 |
| Reader | **JdbcCursorItemReader** | 기존 RankingCorrectionJob과 일관성 유지. GROUP BY 결과(수천 행)는 커서로 충분 |
| 비즈니스 로직 위치 | **Reader SQL에서 집계, Processor에서 score 계산** | DB가 잘하는 것(GROUP BY)은 DB에, score 공식(log₁₀)은 Java에 |
| Writer 전략 | **DELETE + INSERT** | TOP 100은 기간마다 대상이 바뀜. UPSERT는 빠진 상품 잔여 데이터 문제 |
| 멱등성 | **DELETE WHERE period_key = ? → INSERT로 자연 멱등** | 같은 파라미터로 몇 번 실행해도 결과 동일 |
| Redis vs MV 역할 | **daily → Redis, weekly/monthly → MV primary + Redis fallback** | MV가 정확값. Redis 장애 시에도 주간/월간 조회 가능 |
| Job 구조 | **scope 파라미터로 주간/월간 분기하는 단일 Job** | Job Config 중복 방지. 회사 코드의 batchTyp 패턴 참고 |

---

## 아키텍처

### 전체 데이터 흐름

```
[product_metrics (DB 원장, daily grain)]
  │
  │ Reader: GROUP BY product_id, SUM(7일 or 30일)
  ▼
[상품별 기간 메트릭 합계]
  │
  │ Processor: Score v2 공식 (log₁₀ 정규화 + tiebreaker)
  ▼
[상품별 score + 순위]
  │
  │ Writer: DELETE period_key → INSERT TOP 100
  ▼
[mv_product_rank_weekly / mv_product_rank_monthly]
  │
  │ API: SELECT WHERE period_key = ? ORDER BY ranking
  ▼
[클라이언트]
```

### Redis와 MV의 역할 분담

```
[API 요청]
  │
  ├── scope=daily  → Redis ZSET (기존, primary)
  │
  ├── scope=weekly → MV 테이블 (primary)
  │                   └── Redis ZSET (fallback, 기존 carry-over)
  │
  └── scope=monthly → MV 테이블 (primary)
                       └── Redis ZSET (fallback, 기존 carry-over)
```

---

## MV 테이블 스키마

### mv_product_rank_weekly

```sql
CREATE TABLE mv_product_rank_weekly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    ranking INT NOT NULL,
    score DOUBLE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    period_key VARCHAR(8) NOT NULL,   -- '2026-W16'
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period_ranking (period_key, ranking)
) ENGINE=InnoDB;
```

### mv_product_rank_monthly

```sql
CREATE TABLE mv_product_rank_monthly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    ranking INT NOT NULL,
    score DOUBLE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    period_key VARCHAR(7) NOT NULL,   -- '2026-04'
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period_ranking (period_key, ranking)
) ENGINE=InnoDB;
```

**설계 판단**:
- **PK**: AUTO_INCREMENT id (기간+상품 복합 PK 대신). DELETE+INSERT 전략이므로 단순한 PK가 유리
- **period_key**: ISO 기반 문자열. 주간 = `2026-W16`, 월간 = `2026-04`
- **인덱스**: `(period_key, ranking)` — API 조회 패턴에 최적화
- **개별 메트릭 저장**: score만이 아닌 view_count, like_count 등도 저장 — 분석/디버깅 용도

---

## Spring Batch Job 설계

### Job 구조

```
ProductRankingMvJob
  ├── Parameter: targetDate (yyyyMMdd), scope (weekly|monthly)
  │
  ├── Step 1: cleanupStep (Tasklet)
  │   └── DELETE FROM mv_product_rank_{scope} WHERE period_key = :periodKey
  │   └── on("FAILED").end()  ← 삭제 실패 시 적재 Step 미실행
  │
  └── Step 2: aggregateStep (Chunk, chunkSize=1000)
      ├── Reader: JdbcCursorItemReader (GROUP BY 집계)
      ├── Processor: score 계산 + 순위 부여
      └── Writer: JdbcBatchItemWriter (INSERT)
```

### Reader SQL

```sql
SELECT
    pm.product_id,
    SUM(pm.view_count) AS total_view_count,
    SUM(pm.like_count - pm.unlike_count) AS total_net_like_count,
    SUM(pm.sales_count) AS total_sales_count,
    SUM(pm.sales_amount - pm.cancel_amount_by_event_date) AS total_net_sales_amount,
    p.category_id
FROM product_metrics pm
JOIN product p ON pm.product_id = p.id
WHERE pm.metric_date BETWEEN :startDate AND :endDate
  AND p.deleted_at IS NULL
GROUP BY pm.product_id, p.category_id
ORDER BY total_net_sales_amount DESC  -- score 계산 전이지만 대략적 정렬로 TOP-N 필터링 효율화
```

- **주간**: `startDate = targetDate - 6`, `endDate = targetDate` (7일)
- **월간**: `startDate = targetDate - 29`, `endDate = targetDate` (30일)

### Processor

기존 RankingCorrectionJobConfig의 Score v2 공식 재활용:

```java
record AggregatedMetrics(long productId, long viewCount, long likeCount,
                         long salesCount, long salesAmount, Long categoryId) {}

record RankedProduct(long productId, int ranking, double score,
                     long viewCount, long likeCount, long salesCount,
                     long salesAmount, String periodKey) {}
```

**순위 부여 전략**: Processor에서 score만 계산하고, Writer 직전에 전체 chunk의 score 내림차순 정렬 후 순위 부여.
또는 Reader SQL에서 ORDER BY로 정렬된 순서를 활용하여 AtomicInteger 카운터로 순위 부여.

### Writer

```sql
INSERT INTO mv_product_rank_{scope}
(product_id, ranking, score, view_count, like_count, sales_count, sales_amount, period_key, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
```

### TOP 100 제한

**Reader SQL에서 제한하지 않는 이유**: GROUP BY 결과에 score 계산이 포함되지 않으므로 SQL 단계에서 TOP 100을 정할 수 없음.

**선택지**:
1. Reader SQL에서 전체 조회 → Processor에서 score 계산 → 전체 결과를 메모리에 정렬 후 TOP 100만 Writer에 전달
2. Reader SQL에서 `LIMIT 200` 등 넉넉하게 조회 → Processor에서 필터링 (score 기반)

**결정**: Reader에서 전체 조회 → 별도 Step 또는 Processor에서 TOP 100 필터링.
상품 수가 수천~수만 수준이므로 메모리 부담 없음.

---

## API 확장

### 현재 구조

```java
// RankingFacade.getRankings()
return switch (scope) {
    case "weekly" -> WEEKLY_ZSET_PREFIX;   // Redis
    case "monthly" -> MONTHLY_ZSET_PREFIX; // Redis
    default -> DAILY_ZSET_PREFIX;          // Redis
};
```

### 변경 후 구조

```java
// RankingFacade.getRankings()
return switch (scope) {
    case "daily" -> getFromRedis(DAILY_ZSET_PREFIX, ...);
    case "weekly" -> getFromMvWithRedisFallback("weekly", ...);
    case "monthly" -> getFromMvWithRedisFallback("monthly", ...);
};
```

**MV 조회 흐름**:
1. `MvProductRankRepository.findByPeriodKey(periodKey, pageable)` → MV 테이블 조회
2. MV 결과가 없으면 → 기존 Redis ZSET 조회 (fallback)
3. Product 상세 정보 조합 → 응답

### 필요한 새 컴포넌트

| 레이어 | 파일 | 역할 |
|--------|------|------|
| domain | `MvProductRank.java` | MV 엔티티 (@Entity) |
| domain | `MvProductRankRepository.java` | Repository 인터페이스 |
| infrastructure | `MvProductRankJpaRepository.java` | JPA 구현체 |
| application | `RankingFacade.java` (수정) | MV 우선 조회 + Redis fallback |

---

## 실행 전략

### 스케줄링

| Job | 실행 시점 | 근거 |
|-----|----------|------|
| 주간 MV Job | **매일 01:00** | 전날까지의 7일 데이터 집계. RankingCorrectionJob(1시간 주기)과 시간 분리 |
| 월간 MV Job | **매일 01:30** | 전날까지의 30일 데이터 집계. 주간 Job 완료 후 실행 |

- 기존 23:50 carry-over 스케줄러와 시간 충돌 없음
- 매일 실행하여 "오늘 기준 최근 7일/30일" 슬라이딩 윈도우 유지

### 실행 명령

```bash
# 주간 랭킹
java -jar commerce-batch.jar --job.name=productRankingMvJob targetDate=20260416 scope=weekly

# 월간 랭킹
java -jar commerce-batch.jar --job.name=productRankingMvJob targetDate=20260416 scope=monthly
```

---

## 파일 구조

```
apps/commerce-batch/src/main/java/com/loopers/batch/job/rankingmv/
  ├── ProductRankingMvJobConfig.java        ← Job + Step 구성
  ├── ProductRankingMvProperties.java       ← score 가중치 설정 (기존 재활용)
  └── step/
      └── CleanupTasklet.java              ← DELETE Step

apps/commerce-api/src/main/java/com/loopers/
  ├── domain/ranking/
  │   ├── MvProductRank.java               ← MV 엔티티
  │   └── MvProductRankRepository.java     ← Repository 인터페이스
  ├── infrastructure/ranking/
  │   └── MvProductRankJpaRepository.java  ← JPA 구현체
  └── application/ranking/
      └── RankingFacade.java               ← (수정) MV 우선 조회
```

---

## 구현 순서

### Phase 1: 배치 Job 구현

1. **DDL 작성**: `mv_product_rank_weekly`, `mv_product_rank_monthly` 테이블 생성
2. **CleanupTasklet**: period_key 기준 DELETE
3. **ProductRankingMvJobConfig**: Job + Step 구성
   - Reader: JdbcCursorItemReader (GROUP BY 집계 SQL)
   - Processor: Score v2 계산 + 순위 부여 + TOP 100 필터링
   - Writer: JdbcBatchItemWriter (INSERT)
4. **파라미터 처리**: targetDate, scope → 기간 계산, 테이블 분기, period_key 생성

### Phase 2: API 확장

5. **MV 엔티티/리포지토리**: MvProductRank, MvProductRankRepository
6. **RankingFacade 수정**: weekly/monthly 요청 시 MV 우선 조회 + Redis fallback

### Phase 3: 테스트

7. **Job 통합 테스트**: Testcontainers + @SpringBatchTest
   - 시드 데이터 → Job 실행 → MV 결과 검증
   - 멱등성 검증 (2회 실행 → 결과 동일)
   - 엣지 케이스 (데이터 없는 날짜, 7일 미만 데이터)
8. **Score 단위 테스트**: 기존 RankingCorrectionScoreTest와 일관성 검증
9. **API 통합 테스트**: MV 조회 + Redis fallback 동작 검증

### Phase 4: 모니터링 & 검증

10. **시나리오 실행**: 시드 데이터 기반 주간/월간 Job 실행
11. **MV vs Redis 비교**: 같은 기간 TOP 20 대조 (score 차이 분석)
12. **성능 측정**: Job 실행 시간, 처리 건수

### Phase 5: 문서 & PR

13. **설계 문서 갱신**: 구현 결과, 트레이드오프, 성능 수치 반영
14. **PR 작성**: 변경 사항 요약 + 리뷰 포인트 2~3개
15. **테크니컬 라이팅**: 블로그 초안 + 10주 회고
