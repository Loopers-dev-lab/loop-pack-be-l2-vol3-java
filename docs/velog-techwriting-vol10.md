# 일간은 Redis, 주간/월간은 왜 다른가 — 이커머스 랭킹 배치 설계기

*Redis에 이미 주간/월간 랭킹이 있는데, 같은 걸 DB에 또 만들어야 할까?*

> 실시간 Redis 랭킹만으로 충분하다고 생각했다. 그런데 log₁₀의 비선형성을 숫자로 검증하는 순간, "같은 데이터인데 왜 결과가 다르지?"라는 질문이 시작됐고, 이 질문은 Score 방식 선택, 시간 윈도우 전략, Chunk vs Tasklet 판단, CursorReader의 병렬화 한계, 전체 재계산 vs 증분까지 연쇄적으로 이어졌다. Lambda Architecture에서 Speed Layer와 Batch Layer가 왜 공존해야 하는지를 배치 설계 전 과정에 걸쳐 확인한 기록이다.

---

## 1. 이 글의 맥락

Round 9에서 Kafka → Redis ZSET 파이프라인으로 실시간 일간/주간/월간 랭킹을 구축했다. 이벤트가 발생할 때마다 score를 갱신하고, ZUNIONSTORE carry-over로 주간/월간을 근사 계산하는 구조다.

Round 10의 과제는 Spring Batch로 MV(Materialized View) 기반 주간/월간 랭킹을 만드는 것이다. 처음에 든 생각은 단순했다.

*"Redis에 이미 있는 걸 왜 DB에 또 만들어?"*

이 질문에 답하려면, 두 시스템이 정말 같은 결과를 내는지부터 확인해야 했다.

---

## 2. Redis에 이미 랭킹이 있는데, MV를 왜 만드는가

### log₁₀는 선형이 아니다

Redis 주간 랭킹은 **일별 score를 합산**한다. ZUNIONSTORE로 7일치 ZSET을 합치면 `Σ daily_score`가 된다. MV는 **기간 메트릭을 합산한 뒤 score를 한 번 계산**한다. `f(SUM(7일 메트릭))`. 같은 7일 데이터인데, 순서가 다르다.

log₁₀는 비선형 함수이므로, 이 순서의 차이가 결과를 바꾼다.

```
Σ log(daily) ≠ log(Σ daily)
```

숫자로 확인했다.

```
상품 X: 7일간 view = [100, 100, 100, 100, 100, 100, 100]  (총 700)
상품 Y: 7일간 view = [0, 0, 0, 0, 0, 0, 700]              (총 700)

Redis (일별 score 합산):
  X: 7 × log₁₀(101)/7 = 2.003
  Y: 6 × 0 + log₁₀(701)/7 = 0.406
  → X 압도적 유리 (꾸준한 상품 우대)

MV (메트릭 합산 후 score):
  X: log₁₀(701)/7 = 0.406
  Y: log₁₀(701)/7 = 0.406
  → 동점 (총 활동량 동일)
```

총 조회수가 같은 두 상품이, 계산 순서만 다른데 **Redis에서는 X가 5배 높고, MV에서는 동점**이다. 같은 원천 데이터에서 출발하지만, 계산 방식의 차이가 다른 관점의 랭킹을 만들어내는 것이다.

### "다른 결과를 내는 것"이 오히려 가치다

처음에는 "MV가 Redis보다 정확하니까 MV를 만드는 것"이라고 생각했다. 그런데 생각을 정리하다 보니 방향이 달랐다.

| 관점 | Redis (Speed Layer) | MV (Batch Layer) |
|------|---------------------|-------------------|
| **Score 특성** | `Σ daily_score` — 꾸준히 팔린 상품 우대 | `f(Σ daily_metrics)` — 총 실적 기준 |
| **비즈니스 의미** | "지금 뜨는 상품" (트렌드) | "이번 달 베스트셀러" (누적 성과) |
| **소비자 시나리오** | 메인 페이지 실시간 인기 | 카테고리별 베스트, 기간별 랭킹 |
| **사업자 시나리오** | 실시간 모니터링 | 주간/월간 리포트, MD 성과 분석 |

**MV가 Redis와 같은 결과를 내면, MV를 만들 이유가 없다.** 두 시스템이 다른 관점을 제공하는 것이 Lambda Architecture에서 Speed Layer와 Batch Layer의 존재 이유다.

### 그러면 fallback으로 쓰면 안 되는가

설계 초기에는 "MV primary, Redis fallback"으로 구성하려 했다. MV 배치가 실패하면 Redis에서 조회하는 구조. 그런데 위에서 확인했듯이 두 시스템은 같은 기간에 대해 **다른 순위를 반환**한다.

```
정상 시: MV 조회 → 상품 A가 1위 (균등 합산)
MV 장애: Redis fallback → 상품 B가 1위 (일별 합산 + 감쇠)
→ "어제는 A가 1위였는데 오늘은 B?"
```

다른 공식으로 계산한 결과를 같은 API의 fallback으로 쓰면 데이터 일관성이 깨진다. 잘못된 순위를 보여주는 것보다 "현재 랭킹을 준비 중입니다"가 더 안전하다.

**최종 결정은 단일 소스 원칙이다.**

```
daily   → Redis (단일 소스)
weekly  → MV (단일 소스)
monthly → MV (단일 소스)
```

```java
// RankingFacade.java — scope에 따라 데이터 소스를 분리
public RankingDto.PagedRankingResponse getRankings(
        String scope, String date, int page, int size, Long memberId) {
    String resolvedDate = (date != null) ? date
            : LocalDate.now(KST).format(DATE_FORMATTER);

    return switch (scope) {
        case "weekly", "monthly" -> getFromMv(scope, resolvedDate, page, size);
        default -> getFromRedis(scope, resolvedDate, page, size, memberId);
    };
}
```

각 scope의 데이터 소스가 하나이므로, 소스 전환에 의한 순위 불일치가 발생하지 않는다.

---

## 3. 설계 판단들

### 3.1 "주간 베스트"는 총 판매량인가, 최근 인기인가

MV의 score를 어떤 방식으로 계산할 것인가. 세 가지를 검토했다.

| 방식 | 수식 | 특성 |
|------|------|------|
| **균등 합산 (채택)** | `f(SUM(30일 메트릭))` | 기간 총 실적. 30일 전이나 오늘이나 동등 |
| **지수 감쇠** | `Σ(daily_score × 0.97^i)` | 최근에 높은 가중치. 반감기 약 23일 |
| **일평균** | `f(SUM(메트릭) / COUNT(전시일))` | 전시 기간 편향 보정 |

**균등 합산을 선택한 이유는 공개 랭킹 보드의 비즈니스 의미에 있다.**

쿠팡, 무신사, 교보문고의 공개 랭킹 보드는 기간 총 실적 기준이다. "이번 달 베스트셀러"를 볼 때 소비자가 기대하는 것은 "가장 많이 팔린 상품"이지, "일평균 판매량이 높은 상품"이나 "최근에 급등한 상품"이 아니다.

숫자로도 확인했다.

```
상품 A: 30일간 매일 매출 100만원 (꾸준)
상품 B: 최근 5일간 매일 600만원 (급등), 나머지 0원
총 실적: 둘 다 3000만원

              일간     주간(균등)   주간(감쇠)   월간(균등)   월간(감쇠)
상품 A        0.600    0.693       4.09        0.735       12.0
상품 B        0.678    0.735       3.33        0.735        3.33
승자          B        B           A           동점         A 압승
```

감쇠를 쓰더라도 월간이 일간/주간과 비슷해지지는 않는다. 하지만 감쇠를 쓸 *이유*가 없는 것이 핵심이다. Redis가 이미 트렌드를 반영하고 있으므로, MV까지 감쇠를 적용하면 두 시스템의 결과가 수렴한다.

**지수 감쇠를 기각한 근거**: Lambda Architecture에서 Speed Layer(Redis)가 이미 트렌드를 반영하고 있으므로, Batch Layer(MV)는 "정확한 기간 집계"에 집중하는 것이 아키텍처적으로 맞다. 같은 일을 두 시스템에서 반복하면 MV의 존재 가치가 떨어진다.

**일평균을 기각한 근거**: 수학적으로 공정한 비교와 비즈니스적으로 의미 있는 비교는 다를 수 있다. 1일 전시에 매출 500만원이면 일평균 기준으로 30일 전시 + 매출 3000만원인 상품보다 위에 올라간다. MD팀이 원하는 "이번 달 베스트"는 총 매출 3000만원인 상품이다.

다만 일평균은 내부 분석에서는 유용하다. 다시 한다면, `avg_daily_sales = total_sales / active_days`를 MV에 별도 컬럼으로 함께 저장할 것이다. 공개 랭킹의 정렬 기준은 총 실적을 유지하면서, MD 대시보드에서 "판매 효율 기준 정렬"을 재집계 없이 제공할 수 있다.

### 3.2 시간 윈도우: 매주 월요일에 리셋되는 랭킹이 맞는가

MV의 "주간"을 어떻게 정의할 것인가.

| 전략 | 예시 | 갱신 주기 |
|------|------|----------|
| 캘린더 | 주간: 월~일, 월간: 1일~말일 | 주 1회, 월 1회 |
| 슬라이딩 (채택) | 오늘 기준 최근 7일/30일 | 매일 |

**슬라이딩을 선택한 4가지 근거:**

1. **Redis와 시간 범위 일치**: Redis ZUNIONSTORE가 "최근 7일 daily"를 합산하는 슬라이딩 방식. MV가 캘린더이면 시간 범위가 불일치하여 두 시스템 간 비교·검증이 어렵다.
2. **이커머스 업계 관행**: 무신사, 쿠팡 등에서 주간/월간 랭킹을 매일 갱신한다. "주간 인기 상품"이 월요일에만 바뀌면 사용자가 매일 같은 랭킹을 보게 되어 재방문 유인이 떨어진다.
3. **배치 비용 대비 효과**: GROUP BY + TOP 100 INSERT는 상품 수만 건 기준 수초 내 완료. 매일 실행해도 시스템 부하가 미미하며, 매일 갱신되는 효과가 크다.
4. **운영 단순성**: period_key가 targetDate(`20260416`) 그 자체이므로 "이 날짜 기준 최근 N일"이라는 명확한 의미. 캘린더 방식은 ISO 주차(`2026-W16`)나 월(`2026-04`) 계산이 필요하고, 월말/주초 경계 처리가 복잡하다.

캘린더를 기각했지만, 정산/리포팅 시스템에서는 캘린더가 맞다. "4월 매출 정산"은 4/1~4/30 고정 기간이어야 한다. 슬라이딩이면 기준일에 따라 금액이 달라져 정산 불일치가 생긴다. 우리 과제는 정산이 아닌 소비자 대상 랭킹 보드이므로 슬라이딩이 적합하다.

### 3.3 Chunk vs Tasklet: 90개 실무 Job이 알려준 것

이 판단의 출발은 약간 엉뚱한 곳이었다. 회사 배치 프로젝트 2개(90개 Job)를 분석했더니 통계/집계 Job의 대다수가 Tasklet이었다. 처음에는 "Tasklet이 실무 표준"이라고 결론 내렸는데, **"다른 개발자들의 이야기를 들어보면 Chunk 방식이 보편적이라고 하는데?"**라는 반론이 나왔다.

다시 생각해보니, 분석한 배치 프로젝트가 MyBatis + SQL 중심 아키텍처여서 `INSERT INTO...SELECT`가 자연스러운 선택이었을 뿐, 이것을 업계 표준으로 일반화한 것은 **한 조직의 패턴을 확대 해석**한 것이었다.

Spring Batch는 Chunk를 중심으로 설계되어 있다. Chunk가 보편적 선택인 이유는 프레임워크가 Chunk에만 제공하는 운영 기능에 있다.

```java
// Chunk-Oriented에서만 쓸 수 있는 운영 기능
.faultTolerant()
    .retry(DeadlockLoserDataAccessException.class)   // DB 데드락 시 자동 재시도
    .retryLimit(3)                                     // 최대 3회
.skip(DataIntegrityViolationException.class)          // 불량 레코드 건너뛰기
.skipLimit(100)

// Chunk가 자동으로 기록하는 것
StepExecution:
  readCount, writeCount, skipCount, commitCount, rollbackCount
```

**Tasklet에서 동일한 운영 안정성을 확보하려면 retry 루프, skip 카운터, 진행 상태 저장, 처리 건수 추적을 모두 직접 구현해야 한다.** 대부분의 배치 작업에서 이 운영 기능의 가치가 네트워크 왕복 비용보다 크기 때문에 Chunk가 보편적 선택이 된다.

그런데 90개 실무 Job을 다시 살펴보니 흥미로운 사실이 있었다.

| 운영 기능 | 90개 Job 사용 여부 |
|----------|-------------------|
| `.faultTolerant()` | 0개 |
| `.retry()` / `retryLimit` | 0개 |
| `.skip()` / `skipLimit` | 0개 |
| `ItemReadListener` / `ItemWriteListener` | 0개 |
| `allowStartIfComplete` | 0개 |

**90개 Job 중 단 하나도 retry, skip, restart를 사용하지 않는다.** 이것은 "운영에서 문제가 없었다"로 해석할 수도 있지만, 동시에 "1건의 일시적 DB 에러가 전체 배치를 실패시키는 구조"이기도 하다. 야간 배치가 데드락으로 실패하면 아침에 출근해서 수동 재실행해야 한다. retry를 걸어두면 자동으로 복구됐을 에러다.

**우리 프로젝트에서 retry + ExponentialBackOffPolicy를 적용하는 것은, 실무에서 빠져 있는 운영 안정성을 보완하는 설계 판단이다.**

```java
// 우리 MV Job의 workerStep — Chunk + retry + 지수 백오프
ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
backOff.setInitialInterval(100);
backOff.setMultiplier(2.0);
backOff.setMaxInterval(1000);

return new StepBuilder("workerStep", jobRepository)
    .<ScoredProductRow, ScoredProductRow>chunk(CHUNK_SIZE, transactionManager)
    .reader(stagingReader(null, null, null, null))
    .writer(stagingWriter(null))
    .faultTolerant()
        .retry(DeadlockLoserDataAccessException.class)
        .retry(TransientDataAccessException.class)
        .retryLimit(3)
        .backOffPolicy(backOff)
    .listener(stepMonitorListener)
    .build();
```

retry 간격이 100ms → 200ms → 400ms로 지수적으로 증가하는 이유는, 즉시 재시도하면 데드락 상태에서 같은 충돌이 반복될 가능성이 높기 때문이다.

Tasklet의 세 조건(SQL 한 문장 완결, retry 불필요, 중간 상태 무의미)을 모두 충족하면 Tasklet이 효율적이다. 우리의 mergeStep은 Tasklet으로 구현했다 — TOP 100 추출 INSERT 한 문장으로 완결되고, 100건이므로 실패 시 전체 재실행해도 수초 내 완료된다.

### 3.4 Score 계산은 DB에서 끝내야 한다

처음에는 Reader에서 전체 상품을 조회하고 Processor에서 score를 계산한 후, Writer에서 TOP 100만 INSERT하는 구조를 설계했다. 그런데 수만 건을 INSERT했다가 100건만 남기고 삭제하는 것은 불필요한 I/O다.

**"Reader가 100건만 조회해도 TOP 100이 맞아?"** SQL 실행 순서가 이것을 보장한다.

```
1. FROM / JOIN     → product_metrics × product 조인
2. WHERE           → 날짜 범위 필터
3. GROUP BY        → product_id별 그룹핑 + SUM 집계
4. SELECT          → score 계산 (LOG10 함수)
5. ORDER BY        → score 내림차순 정렬 (전체 상품 대상)
6. LIMIT 100       → 상위 100건만 반환
```

DB가 전체 상품의 score를 계산하고 정렬한 후 상위 100건만 네트워크로 전달한다. Reader는 100건만 받지만, 그 100건이 score 기준 TOP 100인 것은 DB가 보장한다.

```sql
-- Reader SQL (ProductRankingMvJobConfig.stagingReader)
SELECT
    pm.product_id,
    SUM(pm.view_count) AS total_view_count,
    SUM(pm.like_count - pm.unlike_count) AS total_net_like_count,
    SUM(pm.sales_count) AS total_sales_count,
    SUM(pm.sales_amount - pm.cancel_amount_by_event_date) AS total_net_sales_amount,
    (
        0.1 * LOG10(GREATEST(SUM(pm.view_count), 0) + 1) / 7.0
      + 0.2 * LOG10(GREATEST(SUM(pm.like_count - pm.unlike_count), 0) + 1) / 7.0
      + 0.7 * LOG10(GREATEST(SUM(pm.sales_amount
          - pm.cancel_amount_by_event_date), 0) + 1) / 7.0
      + UNIX_TIMESTAMP() * 1e-16
    ) AS score
FROM product_metrics pm
JOIN product p ON pm.product_id = p.id
WHERE pm.metric_date BETWEEN ? AND ?
  AND pm.product_id BETWEEN ? AND ?
  AND p.deleted_at IS NULL
GROUP BY pm.product_id
```

회사 코드를 분석한 결과도 이것을 뒷받침한다. **12개 매퍼에서 `RANK()`, `DENSE_RANK()`, `ROW_NUMBER()`, `PERCENT_RANK()` 윈도우 함수로 TOP-N을 처리하고 있었고, Java에서 랭킹/스코어링을 처리하는 배치 Job은 없었다.** "DB가 잘하는 일(집계, 정렬, 필터링)은 DB에서 끝낸다"가 이 회사의 실무 표준이었다.

**트레이드오프가 하나 있다.** SQL에 score 공식을 넣으면, RankingCorrectionJob(일간 보정, Java)과 MV Job(주간/월간, SQL)에 같은 공식이 두 곳에 존재한다. 가중치(0.1/0.2/0.7) 변경 시 두 곳 모두 수정이 필요하다.

이것을 허용한 근거는, 두 Job의 입력이 다르기 때문이다. Correction은 일간 메트릭(CURDATE() 1일)을 읽고, MV는 기간 합산 메트릭(7/30일 SUM)을 읽는다. 같은 공식이지만 적용 대상이 다르므로 하나의 Java 메서드를 공유하는 것이 오히려 부자연스럽다. 가중치 자체는 `application.yml`의 `RankingCorrectionProperties`에 중앙화되어 있어서 SQL에도 파라미터로 주입된다.

### 3.5 CursorReader: GROUP BY 집계에서 PagingReader가 위험한 이유

처음에는 "기존 RankingCorrectionJob과 일관성"이라는 이유로 CursorReader를 골랐다. 대규모 기준으로 다시 따져보니, 이유가 훨씬 근본적이었다.

**PagingReader는 페이지마다 독립된 쿼리를 재실행한다.** GROUP BY가 포함된 집계 쿼리에서 이것은 치명적이다.

```
CursorReader:
  GROUP BY 3,000만 행 → 1번 실행 → 결과 스트리밍
  총 집계 실행: 1회

PagingReader (pageSize=1000, 상품 100만건 = 1,000페이지):
  페이지 1:    GROUP BY 3,000만 행 → 정렬 → OFFSET 0      (30초)
  페이지 2:    GROUP BY 3,000만 행 → 정렬 → OFFSET 1000   (30초)
  ...
  페이지 1000: GROUP BY 3,000만 행 → 정렬 → OFFSET 999000 (30초+)
  총 집계 실행: 1,000회 → 8시간 이상
```

| 관점 | CursorReader | PagingReader |
|------|-------------|-------------|
| **GROUP BY 쿼리** | 1회 실행 후 스트리밍 | 페이지마다 재실행 — 대규모에서 치명적 |
| **커넥션 점유** | Step 전체 동안 1개 점유 | 페이지 조회 시만 점유 |
| **멀티스레드** | 불가 (ResultSet 공유 상태) | 가능 (각 스레드 독립 쿼리) |
| **재시작** | 제한적 (read count 기반) | 자연스러움 (페이지 번호 저장) |

CursorReader의 약점은 멀티스레드에서 쓸 수 없다는 것이다. ResultSet이 "현재 커서 위치"라는 상태를 가지고 있어서, 두 스레드가 동시에 `next()`를 호출하면 행이 누락되거나 중복된다.

**상품이 수백만 건으로 늘어나면 어떻게 병렬화하는가?** PagingReader로 전환하면 GROUP BY 반복 실행이라는 더 큰 문제가 생긴다. 답은 **Partitioning**이다.

### 3.6 매번 원장에서 재계산하는 게 비효율 아닌가

MV가 매일 원장(product_metrics)에서 7일/30일치를 처음부터 GROUP BY한다. **"어제 결과에서 가장 오래된 날을 빼고 오늘을 더하면 되지 않나?"** 증분 계산은 월간 기준 데이터 처리량을 93% 줄일 수 있다.

```
어제 MV (4/10~4/16 합산): 상품 A = view 700, sales 3000만
오늘 MV (4/11~4/17 합산):
  = 어제 결과 - 4/10의 메트릭 + 4/17의 메트릭
  → 30일치 GROUP BY 대신 2일치만 조회
```

수학적으로 정확하다. 근사치가 아니다. **하지만 하나의 전제가 필요하다: "과거 데이터가 변경되지 않는다."**

이커머스에서 이 전제는 깨진다. 주문 취소는 원주문과 다른 날에 발생한다.

```
4/10: 상품 A 주문 100건 (1000만원)
4/15: 그 중 30건 취소 → product_metrics 4/10 행의 cancel_by_order_date 갱신

증분 계산:
  4/10의 값은 이미 MV에 반영됨 (취소 전 1000만원 기준)
  4/15에 4/10 행이 변경됐지만, 증분은 "4/15의 메트릭만 추가"
  → 4/10 행의 사후 변경을 감지 못함

전체 재계산:
  4/10~4/16 전체를 다시 읽음
  → 4/10 행의 cancel_by_order_date 변경이 자동 반영
```

| 시나리오 | 전체 재계산 | 증분 계산 |
|---------|-----------|----------|
| 정상 주문 | 정확 | 정확 |
| 지연 취소 | 자동 반영 | 감지 못함 |
| 운영팀 데이터 보정 | 다음 배치 자동 반영 | 전체 재계산을 별도 실행해야 함 |
| 오류 전파 | 없음 (매번 원장 독립 계산) | 어제 MV가 틀리면 오늘도 틀림 |

성능 차이는 운영에 영향 없는 수준이다.

```
전체 재계산 (Partitioning 4 Worker): ~10초
증분 계산:                            ~3초
→ 1일 1회 배치에서 7초 차이
```

**전체 재계산을 유지한다.** 7초의 성능 이점보다 Late-Arriving Fact 자동 반영 + 오류 자동 복구 + 구현 단순성이 이커머스 랭킹에서 더 가치 있다. 또한, MV의 존재 이유가 "Redis 근사치와 다른 정확한 기간 집계"인데, MV까지 과거 변경을 반영하지 못하는 증분 방식을 쓰면 MV의 정확성이라는 존재 이유가 약해진다.

---

## 4. 구현: 3-Step Chunk Job

위의 판단들이 구현에서 어떻게 결합되는지 정리한다.

### 파이프라인 구조

```
Step 1: cleanupStep (Tasklet)
  DELETE FROM mv_product_rank_{scope} WHERE period_key = ?
  DELETE FROM mv_product_rank_staging WHERE period_key = ?
  + 3일 이전 과거 데이터 정리

Step 2: partitionedAggregateStep (Chunk × 4 Workers)
  [Partitioner] product_id MIN~MAX를 4개 범위로 분할
  ┌───────────────────────────────────────────┐
  │ Worker 1: id 1~250K     → CursorReader    │
  │ Worker 2: id 250K~500K  → CursorReader    │ ← 병렬 실행
  │ Worker 3: id 500K~750K  → CursorReader    │
  │ Worker 4: id 750K~1M    → CursorReader    │
  └───────────────────────────────────────────┘
  각 Worker: GROUP BY + score 계산 → 스테이징 테이블 INSERT

Step 3: mergeStep (Tasklet)
  SELECT ... FROM staging ORDER BY score DESC LIMIT 100
  → INSERT INTO mv_product_rank_{scope}
```

**이 3-Step은 분산 시스템의 Map-Reduce 패턴이다.** Step 2가 Map(병렬 집계), Step 3가 Reduce(전역 정렬 + TOP 100 추출). 스테이징 테이블이 두 단계를 연결하는 중간 저장소 역할을 한다.

각 Worker가 독립 커넥션 + 독립 CursorReader를 가지므로, CursorReader의 멀티스레드 한계를 극복하면서 GROUP BY 1회 실행이라는 장점을 유지한다.

### 왜 PagingReader 멀티스레드가 아닌 Partitioning인가

| 방식 | GROUP BY 실행 횟수 | 소요 시간 (상품 100만) |
|------|-----------------|---------------------|
| 단일 CursorReader | 1회 (3,000만 행) | ~30초 |
| PagingReader 멀티스레드 | 페이지 수 × 스레드 수 | **수 시간** |
| **Partitioning + CursorReader** | Worker 수 (각 750만 행) | **~10초** |

Partitioning은 데이터를 범위로 분할하여 각 Worker가 자기 범위만 GROUP BY하므로, 전체 데이터를 매번 재집계하는 PagingReader와 근본적으로 다르다.

### Partitioner 구현

```java
private Partitioner createPartitioner(String targetDate, String scope) {
    return gridSize -> {
        int days = "weekly".equals(scope) ? 6 : 29;
        LocalDate endDate = LocalDate.parse(targetDate, DATE_FORMATTER);
        LocalDate startDate = endDate.minusDays(days);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long minId = jdbc.queryForObject(
            "SELECT COALESCE(MIN(product_id), 0) FROM product_metrics " +
            "WHERE metric_date BETWEEN ? AND ?",
            Long.class, startDate, endDate);
        Long maxId = jdbc.queryForObject(
            "SELECT COALESCE(MAX(product_id), 0) FROM product_metrics " +
            "WHERE metric_date BETWEEN ? AND ?",
            Long.class, startDate, endDate);

        long range = (maxId - minId) / gridSize + 1;
        Map<String, ExecutionContext> partitions = new HashMap<>();

        for (int i = 0; i < gridSize; i++) {
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong("minProductId", minId + (i * range));
            ctx.putLong("maxProductId",
                Math.min(minId + ((i + 1) * range) - 1, maxId));
            partitions.put("partition" + i, ctx);
        }
        return partitions;
    };
}
```

product_id 범위를 균등 분할한다. 각 Worker의 Reader SQL에 `WHERE pm.product_id BETWEEN ? AND ?` 조건이 추가되어 자기 범위만 집계한다.

### 멱등성: DELETE + INSERT

```java
// CleanupTasklet — Step 1에서 타겟 날짜의 기존 데이터를 전부 정리
int deletedMv = jdbcTemplate.update(
    "DELETE FROM " + mvTable + " WHERE period_key = ?", targetDate);
int deletedStaging = jdbcTemplate.update(
    "DELETE FROM mv_product_rank_staging WHERE period_key = ?", targetDate);
```

같은 파라미터로 몇 번을 실행해도 결과가 동일하다. `RunIdIncrementer`가 `run.id`를 증가시켜 재실행을 허용하고, cleanupStep이 기존 데이터를 삭제하고 새로 적재한다.

Step 2에서 일부 Worker만 실패하면? 전체 재실행이 가장 단순하고 안전하다. 수십 초 수준의 작업이므로 "실패한 파티션만 재실행"보다 "전부 정리하고 처음부터"가 운영상 안전하다. cleanupStep의 `allowStartIfComplete(true)`가 이미 완료된 Step의 재실행을 허용한다.

---

## 5. 시행착오

### "Tasklet이 실무 표준" — 한 조직의 패턴을 일반화한 오류

90개 Job 분석에서 Tasklet이 대다수인 것을 보고 "통계/집계 Job에서 Tasklet이 표준"이라고 결론 내렸다. MyBatis + SQL 중심 아키텍처라는 맥락을 무시한 확대 해석이었다.

교훈: 실무 코드를 분석할 때 "무엇을 하고 있는가"뿐 아니라 "어떤 기술 스택·조직 문화에서 이 선택이 나왔는가"를 함께 봐야 한다. 하나의 코드베이스에서 관찰한 패턴은 그 조직의 맥락에서 합리적인 선택이지, 업계 표준과 동치가 아니다.

### MV를 Redis fallback으로 쓰려다 — 데이터 불일치 함정

초기 설계에서 "MV primary, Redis fallback"이 자연스러워 보였다. MV가 장애나면 Redis에서라도 보여주면 되니까. log₁₀ 비선형성 검증을 하기 전까지는 두 시스템이 "대충 비슷한 결과"를 낼 것이라고 암묵적으로 가정하고 있었다.

교훈: fallback을 설계할 때, primary와 fallback이 **같은 계약을 이행하는지** 확인해야 한다. "비슷한 데이터를 제공한다"와 "같은 기준의 데이터를 제공한다"는 다르다.

### Chunk-Oriented인데 Processor가 할 일이 없다?

Score 계산과 TOP-N 필터링을 SQL에서 끝내니까 Processor가 비어버렸다. "Chunk-Oriented에서 Processor가 비즈니스 로직을 담당해야 한다"는 일반론에 어긋나는 것 같아서 불편했다.

그런데 회사 12개 매퍼를 분석한 결과, DB에서 윈도우 함수로 정렬·필터링까지 끝내고 Java는 오케스트레이션만 하는 것이 실무 패턴이었다. **"어디서 계산하느냐"는 효율의 문제이지 패턴 준수의 문제가 아니다.**

---

## 6. 실전에서라면

### Replica DB 분리

CursorReader의 커넥션 점유가 문제가 되는 것은 여러 Job이 동시에 실행되어 커넥션 풀이 고갈될 때다. 분석한 회사 배치 프로젝트 2개도 RODB/RWDB를 5~6쌍으로 분리하여 이 문제를 해결하고 있었다. 배치가 Replica에서 읽으면 서비스 DB의 커넥션 풀과 독립되므로, CursorReader의 커넥션 점유가 서비스에 영향을 주지 않는다.

### 사전 집계 파이프라인의 필요성

쿠팡급(상품 100만, product_metrics 30일치 3,000만 행)에서 Chunk든 Tasklet이든 집계 쿼리의 DB 부하는 동일하다. 진짜 해결해야 할 문제는 처리 모델 선택이 아니라 **"이 집계를 서비스 DB에서 할 것인가"**이다. Flink/Spark 같은 사전 집계 파이프라인이나 DW에서 집계하는 것이 대규모에서의 정석이다.

우리 프로젝트에서는 이미 Kafka → MetricsConsumer → product_metrics라는 사전 집계 레이어가 존재한다. 원시 이벤트(수억 건)가 아닌 일간 집계 테이블(수만 건)을 배치에서 읽는 구조이므로, 사전 집계가 Reader의 입력 볼륨을 줄이는 역할을 하고 있다.

### gridSize 튜닝

현재 gridSize=4로 고정했지만, 실무에서는 상품 수와 DB 커넥션 풀 크기에 따라 동적으로 조정해야 한다. 커넥션 풀이 20개이고 다른 Job과 공유한다면 gridSize를 8 이상으로 올리면 커넥션 부족이 발생할 수 있다. `MIN/MAX(product_id)` 쿼리로 데이터 분포를 확인하고 gridSize를 결정하는 방식을 기본으로 하되, 설정값으로 외부화하여 운영 중 변경할 수 있도록 하는 것이 실용적이다.

### Drift Detection — 배치 사이의 빈 시간

1시간 주기 배치 보정(RankingCorrectionJob) 사이에 Redis drift가 누적될 수 있다. 이것을 조기 감지하기 위해 5분 주기로 Redis Top-20과 DB score를 비교하는 경량 모니터링(RankingDriftScheduler)을 추가했다. 부하는 ~2ms/5분으로 서비스 요청 경로에 영향 없이, "실시간 경로가 얼마나 벗어나고 있는가"를 지속적으로 관찰한다.

실무에서는 이 drift 메트릭에 알림 임계치를 걸어서 "drift > 20%면 즉시 보정 Job 트리거"와 같은 자동 대응을 구성할 수 있다.

---

## 7. 돌아보며

### Lambda Architecture에서 배운 것

이 과제를 시작했을 때는 "MV는 Redis의 백업"이라고 생각했다. Redis가 장애나면 MV에서 읽으면 되니까. 그런데 log₁₀ 비선형성을 숫자로 확인하면서, 두 시스템이 같은 데이터로 다른 결과를 내는 것이 단점이 아니라 **설계 의도**라는 것을 이해했다.

**"Lambda Architecture에서 두 Layer의 가치는 같은 결과를 내는 것이 아니라, 같은 데이터로 다른 관점을 제공하는 것이다."**

이 관점이 모든 후속 판단의 출발점이 되었다. Score 방식을 균등 합산으로 정한 것도, MV를 Redis fallback으로 쓰지 않기로 한 것도, 전체 재계산을 유지한 것도 — "MV는 Redis와 다른 관점을 정확하게 제공해야 한다"는 한 줄에서 파생되었다.

### 10주간의 흐름

돌이켜보면 1~10주차가 하나의 연결된 흐름이었다.

초반에는 요구사항을 그대로 구현하는 데 집중했다. "JPA로 CRUD"에서 시작해서, "왜 이 구조인가"라는 질문 없이 동작하는 코드를 만들었다. 전환점은 Round 7~8쯤이었다. Kafka 파이프라인을 설계하면서 "실시간 이벤트가 DB와 Redis에 각각 어떤 시점에 반영되는가"를 추적해야 했고, 이때부터 "동작하는 코드"와 "설명할 수 있는 설계"의 차이를 인식하기 시작했다.

Round 9에서 Redis 랭킹을 만들면서 가중합의 함정, log₁₀ 정규화, 지수 감쇠 같은 판단을 처음 경험했다. 선택지가 여러 개인데 정답이 없는 상황에서 "왜 이것을 골랐는가"를 숫자로 검증하는 습관이 생겼다.

Round 10에서는 그 습관이 자연스러워졌다. 균등 합산 vs 지수 감쇠를 비교할 때 직관이 아니라 실제 데이터를 넣어서 확인했고, CursorReader vs PagingReader를 비교할 때 3,000만 행 기준 소요 시간을 산정했다. **"왜 이렇게 했는가"에 숫자로 답할 수 있게 된 것**이 10주간 가장 크게 달라진 점이다.

### 가장 큰 전환점

"실무 코드를 분석했더니 Tasklet이 대다수" → "그러니까 Tasklet이 표준"으로 곧장 결론 내린 순간. 그리고 그 결론이 깨진 순간.

하나의 코드베이스에서 관찰한 패턴을 일반화하는 것은 위험하다. 그 패턴이 어떤 기술 스택, 조직 문화, 도메인 맥락에서 나왔는지를 함께 봐야 한다. 이것을 경험으로 체득한 것이 이번 과제의 가장 큰 수확이다.
