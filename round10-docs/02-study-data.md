# Round 10 Study Data - Spring Batch 기반 주간/월간 랭킹

> 학습 진행 중 실시간 업데이트

---

## Keywords 목록

| # | Keyword | 학습 상태 |
|---|---------|----------|
| 1 | 실시간 처리 vs 배치 처리 | 완료 ✅ |
| 2 | Spring Batch Job / Step 실행 모델 | 완료 ✅ |
| 3 | Chunk-Oriented Processing & Tasklet | 완료 ✅ |
| 4 | ItemReader / ItemProcessor / ItemWriter 책임 분리 | 완료 ✅ |
| 5 | product_metrics 기반 주간/월간 집계 설계 | 완료 ✅ (4번에 통합) |
| 6 | Materialized View 역할과 테이블 설계 | 완료 ✅ |
| 7 | Ranking API 기간 파라미터 확장 | 완료 ✅ |
| 8 | 배치 운영 전략: 파라미터, 재실행, 모니터링 | 완료 ✅ |

---

## 1. 실시간 처리 vs 배치 처리

### 결론

| 차원 | 일간 (Redis ZSET) | 주간/월간 (Batch + MV) |
|---|---|---|
| **업데이트 방식** | 이벤트 발생마다 `zincrby` (누적 덧셈) | 집계 주기마다 전체 재계산 |
| **Sliding Window** | 필요 없음 (오늘 날짜 키만 관리) | 필요함 → Redis로 구현 시 "빼기"가 구조적으로 불가 |
| **업데이트 빈도 필요성** | 초 단위 반응성 필요 (UX에 직결) | 하루 1회 재계산으로 충분 |

- Redis ZSET은 `zincrby`(누적 덧셈)는 쉽지만, Sliding Window의 "오래된 기여분 빼기"가 구조적으로 불가능하다.
- 주간/월간 랭킹은 초 단위 반응성이 필요 없으므로, 하루 1회 배치 재계산으로 충분하다.
- `product_metrics`에 일별 집계가 DB에 이미 존재하므로, 배치가 날짜 범위 조건으로 읽어 합산 후 MV 테이블에 저장하면 API는 단순 조회만 수행한다.

**상태: 완료 ✅**

---

## 2. Spring Batch Job / Step 실행 모델

### 결론

- **`JobRepository`**: Job 실행 이력을 DB에 저장. 실패 시 동일 파라미터로 재실행하면 실패 지점부터 이어서 처리 가능.
- **`JobParameters`**: Job 실행의 입력값. `LocalDate.now()` 같은 런타임 값이 아닌, 명시적 파라미터로 "어느 날짜를 처리하는 Job인지" 결정론적으로 식별.
- **`JobInstance`** = Job 이름 + JobParameters 조합으로 결정됨.
  - 이전 실행이 `COMPLETED` → `JobInstanceAlreadyCompleteException` (재실행 불가)
  - 이전 실행이 `FAILED` → 재실행 허용, 실패 지점부터 이어서 처리

**상태: 완료 ✅**

---

## 3. Chunk-Oriented Processing & Tasklet

### 결론

**Chunk-Oriented Processing:**
- `chunk(N)`: N건씩 묶어 트랜잭션 관리. 100만 건 + chunk(1000) = 트랜잭션 1000번.
- 실패 시 해당 청크 롤백 → 해당 청크 첫 번째 건부터 재시작.
- chunk 사이즈 트레이드오프:
  - 너무 크면: 실패 시 롤백/재처리 데이터가 많아짐 (리스크 증가)
  - 너무 작으면: 트랜잭션 횟수 + 네트워크 통신 증가 (비효율)
  - 정답 없음 → 프로파일링으로 결정 (일반적으로 100~1000 사이)

**Tasklet:**
- "읽고-처리-쓰기" 루프가 없는 단발성 작업에 적합 (TRUNCATE, DELETE, 알림 발송, 파일 이동 등)
- Spring Batch Job 안에 속하므로 JobRepository의 이력/재실행 인프라를 그대로 활용
- `@Scheduled`와 달리 실행 이력, 실패 상태, 재실행이 자동 관리됨
- `RepeatStatus.FINISHED`: Step 완료
- `RepeatStatus.CONTINUABLE`: Tasklet을 다시 호출 → 내부에서 루프 구현 가능 (Chunk와 유사한 구조 구현 가능하나, 재시작 지점이 Step 단위라 대용량에는 비효율적)

| | Chunk-Oriented | Tasklet + CONTINUABLE |
|---|---|---|
| 청크 경계 관리 | Spring Batch 자동 | 개발자가 직접 |
| 트랜잭션 | 청크마다 자동 커밋 | 개발자가 직접 관리 |
| 재시작 지점 | 청크 단위 자동 기록 | Step 단위만 기록 |
| 적합 상황 | 대용량 + 정밀 재시작 필요 | 단순 로직, 분리 과한 경우 |

**상태: 완료 ✅**

---

## 4. ItemReader / ItemProcessor / ItemWriter 책임 분리

### 결론

```
Reader   → 1건씩 반환 (프레임워크가 루프 관리)
Processor→ 1건씩 변환 (프레임워크가 루프 관리)
Writer   → chunk 단위 List 한 번에 받음 (벌크 처리용)
```

- **Reader**: SQL 레벨에서 `GROUP BY` + `SUM`으로 집계 완료 후 `RowMapper`로 DTO 매핑하여 1건씩 반환. 합산 로직을 Java가 아닌 DB에서 처리하는 것이 핵심.
- **Processor**: 1건씩 입력받아 타입 변환 + 비즈니스 로직(점수 계산 등) 적용. 무상태(stateless) 유지.
- **Writer**: `List<T>` (chunk 단위)를 한 번에 받아 `JdbcBatchItemWriter`로 벌크 INSERT/UPDATE.

**데이터 흐름 (주간 랭킹 예시):**
```
Reader: product_metrics → GROUP BY SUM → ProductWeeklyMetrics 1건씩
Processor: ProductWeeklyMetrics → 점수 계산 → MvProductRankWeekly 1건씩
Writer: List<MvProductRankWeekly> 1000건 → mv_product_rank_weekly 벌크 저장
```

**상태: 완료 ✅**

---

## 5. product_metrics 기반 주간/월간 집계 설계

### 결론

4번 (ItemReader/Processor/Writer)에서 함께 다룸.

핵심 내용:
- `product_metrics` 테이블에서 날짜 범위(`BETWEEN startDate AND endDate`)로 필터 후 `GROUP BY product_id` + `SUM(like_count, order_count, view_count)`로 집계
- 집계는 Java가 아닌 **SQL 레벨(Reader 쿼리)** 에서 처리
- 결과를 RowMapper로 집계 DTO에 매핑 → Processor에서 점수 계산 → Writer에서 MV 테이블 저장

**상태: 완료 ✅**

---

## 6. Materialized View 역할과 테이블 설계

### 결론

- MySQL은 MV 기능이 없으므로 **별도 테이블 + 배치 적재** 방식으로 구현
- MV = 완전한 Read Model → 쿼리 시점에 필요한 모든 필드를 denormalize하여 저장
- 상품 정보(이름, 가격 등) 변경 시 → 이벤트 기반 동기화로 MV 업데이트
- 배치 → 집계/랭킹 정보(rank, score, 집계 수치, 기간) 갱신

**JOIN vs 이벤트 기반 동기화 판단 기준:**
1. 쿼리 성능이 문제 없으면 → JOIN (단순, 항상 정확)
2. 쿼리 성능이 필요하면 → denormalize + 이벤트 기반 동기화
   - 데이터가 자주 바뀌면 → 이벤트 비용 커서 배치 주기 갱신(staleness 수용)이 현실적
   - 데이터가 가끔 바뀌면 → 이벤트 기반 동기화 비용 낮아 적합

**상태: 완료 ✅**

---

## 7. Ranking API 기간 파라미터 확장

### 결론

기존 `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1`에 `period` 파라미터 추가:

```
GET /api/v1/rankings?period=daily&date=20240414&size=20&page=1
GET /api/v1/rankings?period=weekly&date=20240414&size=20&page=1
GET /api/v1/rankings?period=monthly&date=20240414&size=20&page=1
```

**period별 데이터 소스:**
- `daily` → 기존 일간 데이터 소스 (Redis ZSET or DB)
- `weekly` → `date`로 주 시작/종료일 계산 → `mv_product_rank_weekly` 조회
- `monthly` → `date`로 연/월 추출 → `mv_product_rank_monthly` 조회

날짜 계산 로직(주 시작/종료일, 연월 추출)은 Service 또는 도메인 레이어에서 담당.

**상태: 완료 ✅**

---

## 8. 배치 운영 전략: 파라미터, 재실행, 모니터링

### 결론

**스케줄링:**
- `@Scheduled(cron = "0 0 3 * * *")` → 단순하고 추가 학습 곡선 없음
- 다중 인스턴스 환경에서는 중복 실행 문제 발생

**다중 인스턴스 대응:**
- 1차 방어선: Spring Batch JobRepository — 동일 JobParameters로 COMPLETED된 JobInstance 재실행 불가
- 2차 방어선: 분산 락 필요
  - `ShedLock`: DB 테이블 기반 분산 락 라이브러리 (직접 구현 시 race condition 관리 복잡)
  - `K8s CronJob`: 스케줄 시점에 파드 1개만 생성 → 중복 실행 자체 불가
  - `Quartz Clustering`: DB로 분산 조율

**실무 선택 기준:**
- 단순한 경우 → `commerce-batch`를 단일 인스턴스로 운영 (배치는 HA 불필요)
- 복잡한 환경 → K8s CronJob이 가장 깔끔

**상태: 완료 ✅**
