# 10. 배치 랭킹 시스템 설계 — MV 기반 주간/월간 랭킹

> Spring Batch로 product_metrics를 기간 집계하여 MV 테이블에 TOP 100 랭킹을 적재하고,
> API에서 주간/월간 요청 시 MV를 primary 소스로 조회하는 시스템.

---

## 요구사항

### 과제 요구사항 (10-batch-ranking-quests.md)

| # | 요구사항 | 상세 | Checklist |
|---|---------|------|-----------|
| **R1** | Spring Batch Job 구현 | `product_metrics`를 **Chunk-Oriented** 방식으로 집계 처리 | Job을 작성하고 **파라미터 기반**으로 동작시킬 수 있다 |
| **R2** | Materialized View 설계 | `mv_product_rank_weekly` (주간 TOP 100), `mv_product_rank_monthly` (월간 TOP 100) | MV 구조를 설계하고 **올바르게 적재**했다 |
| **R3** | Ranking API 확장 | 기존 `GET /api/v1/rankings`에서 **기간 정보**를 받아 일간/주간/월간 랭킹 제공 | 조회 형태에 따라 **적절한 데이터 소스** 기반 랭킹 제공 |
| **R4** | Technical Writing | 블로그 + 10주 회고 (TL;DR 포함, "왜 그렇게 판단했는가" 중심) | — |

### 기존 구현 현황 (Round 9)

| 항목 | 상태 | 비고 |
|------|------|------|
| commerce-batch 모듈 | ✅ | 6개 Job 운영 중 |
| product_metrics 테이블 | ✅ | PK: (product_id, metric_date), daily grain |
| RankingCorrectionJob | ✅ | Chunk 1,000, JdbcCursorItemReader → Redis |
| Redis 일간/주간/월간 ZSET | ✅ | carry-over + ZUNIONSTORE |
| Ranking API (scope 파라미터) | ✅ | daily/weekly/monthly → **모두 Redis 조회** |
| MV 테이블 | ❌ | **Round 10 핵심 과제** |

---

## 설계 결정 요약

| 질문 | 결정 | 근거 |
|------|------|------|
| 시간 윈도우 | **슬라이딩 윈도우 (매일 갱신)** | Redis weekly와 동일한 시간 범위. 무신사 방식. 사용자에게 매일 갱신되는 랭킹 제공 |
| Score 계산 방식 | **방식 A — 메트릭 균등 합산 후 score 1회 계산** | MV는 "기간 총 실적" 관점. Redis(지수 감쇠)와 다른 관점을 제공하는 것이 MV의 존재 이유 |
| Reader | **JdbcCursorItemReader** | 기존 RankingCorrectionJob과 일관성 유지. GROUP BY 결과(수천 행)는 커서로 충분 |
| 비즈니스 로직 위치 | **Reader SQL에서 집계, Processor에서 score 계산** | DB가 잘하는 것(GROUP BY)은 DB에, score 공식(log₁₀)은 Java에 |
| Writer 전략 | **DELETE + INSERT** | TOP 100은 기간마다 대상이 바뀜. UPSERT는 빠진 상품 잔여 데이터 문제 |
| 멱등성 | **DELETE WHERE period_key = ? → INSERT로 자연 멱등** | 같은 파라미터로 몇 번 실행해도 결과 동일 |
| Redis vs MV 역할 | **daily → Redis, weekly/monthly → MV primary + Redis fallback** | MV가 정확값. Redis 장애 시에도 주간/월간 조회 가능 |
| Job 구조 | **scope 파라미터로 주간/월간 분기하는 단일 Job** | Job Config 중복 방지. 회사 코드의 batchTyp 패턴 참고 |

---

## 시간 윈도우 전략

### 슬라이딩 윈도우 (매일 갱신)

MV는 캘린더 기반(월~일, 1일~말일)이 아닌, **매일 갱신되는 슬라이딩 윈도우**로 집계한다.

```
targetDate = 2026-04-16 기준:

주간: 2026-04-10 ~ 2026-04-16 (최근 7일)
      ├─ 다음날 실행 시: 2026-04-11 ~ 2026-04-17 (1일 슬라이드)
      └─ 매일 갱신되어 "오늘 기준 최근 7일" 유지

월간: 2026-03-18 ~ 2026-04-16 (최근 30일)
      ├─ 다음날 실행 시: 2026-03-19 ~ 2026-04-17 (1일 슬라이드)
      └─ 매일 갱신되어 "오늘 기준 최근 30일" 유지
```

**선택 근거**:
- Redis weekly도 슬라이딩 7일 (ZUNIONSTORE 최근 7일 daily). MV와 시간 범위가 일치해야 fallback이 의미 있음
- 무신사 등 이커머스에서 주간/월간 랭킹도 매일 갱신하는 것이 UX에 유리
- period_key는 targetDate 자체 (`20260416`) — "이 날짜 기준 최근 N일" 의미

### Redis monthly(지수 감쇠)와 MV monthly(균등 합산)의 차이

Redis monthly는 **지수 감쇠** 방식이다:

```
내일_monthly = 오늘_monthly × 0.97 + 오늘_daily × 1.0
```

이를 30일간 풀어쓰면:

```
monthly = Σ(i=0 ~ 29) daily_(today-i) × 0.97^i

= daily_today     × 0.97⁰   (= 1.000)
+ daily_1일전     × 0.97¹   (= 0.970)
+ daily_2일전     × 0.97²   (= 0.941)
+ ...
+ daily_29일전    × 0.97²⁹  (= 0.413)
```

**주의**: Redis는 "딱 30일"이 아니라 서비스 시작 이후 **모든 날**이 반영된다.
다만 0.97을 계속 곱하므로 오래된 날일수록 가중치가 0에 수렴한다.
가중치가 절반이 되는 데 걸리는 일수(**반감기**) ≈ 23일 (`ln(0.5) / ln(0.97) ≈ 22.8`).

```
일수    가중치(0.97^i)   누적 기여
 0일    1.000            5.0%     (오늘)
 6일    0.833           32.9%     ← 최근 7일이 전체의 1/3
13일    0.673           57.4%     ← 최근 14일이 전체의 57%
22일    0.502           80.2%     ← 반감기: 23일 전 = 50%
29일    0.413          100.0%
```

**MV 방식 A(균등 합산)와의 차이**:

```
Redis monthly (지수 감쇠, 윈도우 없음):
  상품 A: 30일 전 매출 1000만원 → 가중치 0.40으로 반영
  상품 B: 오늘 매출 1000만원     → 가중치 1.00으로 반영
  → B가 유리 (최근 활동 우대)

MV 방식 A (균등 합산, 30일 고정 윈도우):
  상품 A: 30일 전 매출 1000만원 → 가중치 1.0
  상품 B: 오늘 매출 1000만원     → 가중치 1.0
  → 동일 (기간 내 총량만 평가)
```

**두 방식은 관점이 다르다:**
- Redis: "최근에 뜨는 상품" (트렌드)
- MV: "기간 총 실적이 높은 상품" (누적 성과)

MV가 Redis와 동일한 지수 감쇠를 쓰면 MV를 만들 이유가 없다. 다른 관점을 제공하는 것이 MV의 존재 가치다.

---

## 아키텍처

### 전체 데이터 흐름

```
[product_metrics (DB 원장, daily grain)]
  │
  │ Reader: GROUP BY product_id, SUM(최근 7일 or 30일)
  ▼
[상품별 기간 메트릭 균등 합계]
  │
  │ Processor: Score v2 공식 (log₁₀ 정규화 + tiebreaker)
  ▼
[상품별 score → 정렬 → TOP 100]
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
  ├── scope=weekly → MV 테이블 (primary, 균등 합산)
  │                   └── Redis ZSET (fallback, 일별 score 합산)
  │
  └── scope=monthly → MV 테이블 (primary, 균등 합산)
                       └── Redis ZSET (fallback, 지수 감쇠)
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
    period_key VARCHAR(8) NOT NULL,   -- '20260416' (targetDate, 슬라이딩 윈도우 기준일)
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
    period_key VARCHAR(8) NOT NULL,   -- '20260416' (targetDate, 슬라이딩 윈도우 기준일)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period_ranking (period_key, ranking)
) ENGINE=InnoDB;
```

**설계 판단**:
- **PK**: AUTO_INCREMENT id. DELETE+INSERT 전략이므로 단순한 PK가 유리
- **period_key**: targetDate 문자열 (`20260416`). "이 날짜 기준 최근 7일/30일" 의미. 슬라이딩 윈도우이므로 매일 새로운 period_key 생성
- **인덱스**: `(period_key, ranking)` — API 조회 패턴 `WHERE period_key = ? ORDER BY ranking` 에 최적화
- **개별 메트릭 저장**: score뿐 아니라 view_count, like_count 등도 저장 — 분석/디버깅 및 향후 다차원 정렬 확장 용도
- **이전 기간 데이터**: 당일 기준 period_key만 유지. 이전 날짜 데이터는 CleanupTasklet에서 삭제 (또는 보존 후 별도 정리 Job)

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
      ├── Processor: score 계산 + TOP 100 필터링 + 순위 부여
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
```

- **주간**: `startDate = targetDate - 6`, `endDate = targetDate` (7일)
- **월간**: `startDate = targetDate - 29`, `endDate = targetDate` (30일)
- Additive Measure 원칙 준수: 취소는 별도 컬럼이므로 조회 시 차감

### Processor

기존 RankingCorrectionJobConfig의 Score v2 공식 재활용:

```
score = categoryPriority
      + 0.1 × log₁₀(totalViewCount + 1) / 7.0
      + 0.2 × log₁₀(totalNetLikeCount + 1) / 7.0
      + 0.7 × log₁₀(totalNetSalesAmount + 1) / 7.0
      + epochSeconds × 1e-16  (tiebreaker)
```

**TOP 100 필터링 + 순위 부여**: Reader에서 전체 상품을 조회하고, Processor에서 score를 계산한 후, Writer 직전에 전체 결과를 score 내림차순 정렬하여 TOP 100만 Writer에 전달. 상품 수가 수천~수만 수준이므로 메모리 부담 없음.

### Writer

```sql
INSERT INTO mv_product_rank_{scope}
(product_id, ranking, score, view_count, like_count, sales_count, sales_amount, period_key, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
```

---

## API 확장

### 현재 구조 (모두 Redis 조회)

```java
// RankingFacade — scope별 Redis prefix 분기
return switch (scope) {
    case "weekly" -> WEEKLY_ZSET_PREFIX;   // Redis
    case "monthly" -> MONTHLY_ZSET_PREFIX; // Redis
    default -> DAILY_ZSET_PREFIX;          // Redis
};
```

### 변경 후 구조 (weekly/monthly → MV primary)

```java
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

**기존 API 시그니처 변경 없음**: `/api/v1/rankings?scope=weekly&date=20260416&size=20&page=0`

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
- 매일 실행하여 슬라이딩 윈도우 유지

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

apps/commerce-batch/src/main/resources/
  └── schema-mv.sql                        ← DDL
```

---

## 구현 순서

### Phase 0: 설계 (완료)

- ✅ 0-1. 아키텍처 결정 — Redis vs MV 역할 분담 (MV primary, Redis fallback)
- ✅ 0-2. MV 스키마 설계 — DDL 확정, 슬라이딩 윈도우 period_key
- ✅ 0-3. Job 설계 — Chunk-Oriented, DELETE+INSERT, 파라미터 기반
- ✅ 0-4. Score 전략 — 방식 A (균등 합산) 확정, Redis 지수 감쇠와의 차이 분석
- ✅ 0-5. 시간 윈도우 — 슬라이딩 윈도우 (매일 갱신) 확정
- ✅ 0-6. 설계 문서 작성 — 분석 보고서, 코드 참고 스니펫, 시스템 설계

### Phase 1: 배치 Job 구현 → R1, R2 충족

| # | 작업 | 산출물 |
|---|------|--------|
| 1-1 | DDL 작성 | `mv_product_rank_weekly`, `mv_product_rank_monthly` 테이블 |
| 1-2 | CleanupTasklet | period_key 기준 DELETE (Step 1) |
| 1-3 | ProductRankingMvJobConfig | Job + Step 구성 (Reader/Processor/Writer) |
| 1-4 | 파라미터 처리 | targetDate, scope → 기간 계산, 테이블 분기, period_key |

### Phase 2: API 확장 → R3 충족

| # | 작업 | 산출물 |
|---|------|--------|
| 2-1 | MV 엔티티/리포지토리 | MvProductRank, MvProductRankRepository, JPA 구현체 |
| 2-2 | RankingFacade 수정 | weekly/monthly → MV 우선 조회 + Redis fallback |

### Phase 3: 테스트

| # | 작업 | 산출물 |
|---|------|--------|
| 3-1 | Score 단위 테스트 | 기존 RankingCorrectionScoreTest와 공식 일관성 검증 |
| 3-2 | Job 통합 테스트 | 시드 → Job → MV 결과 검증 (Testcontainers + @SpringBatchTest) |
| 3-3 | 멱등성 테스트 | 같은 파라미터 2회 실행 → MV 결과 동일 |
| 3-4 | 엣지 케이스 | 데이터 없는 날짜, 7일 미만 데이터 |
| 3-5 | API 통합 테스트 | MV 조회 + Redis fallback 동작 검증 |

### Phase 4: 시나리오 검증 & 모니터링

| # | 작업 | 산출물 |
|---|------|--------|
| 4-1 | 정상 실행 시나리오 | 시드 데이터 기반 주간/월간 Job 실행 결과 |
| 4-2 | MV vs Redis 비교 | 같은 기간 TOP 20 대조, score 차이 분석 |
| 4-3 | 성능 측정 | Job 실행 시간, 처리 건수 기록 |

### Phase 5: 문서 & PR → R4 충족

| # | 작업 | 산출물 |
|---|------|--------|
| 5-1 | 설계 문서 갱신 | 구현 결과, 성능 수치, 트레이드오프 반영 |
| 5-2 | PR 작성 | 변경 요약 + 리뷰 포인트 2~3개 |
| 5-3 | 블로그 + 10주 회고 | TL;DR 포함, 설계 판단 중심 |
