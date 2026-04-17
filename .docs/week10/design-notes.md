# Week 10 설계 결정 노트 — Spring Batch 주간/월간 랭킹

## 집계 기간 정의 — Rolling Window vs 캘린더 고정

주간/월간 랭킹의 집계 기간을 어떻게 정의할지. UX("이번 주/달" 기대), 최신성, Spring Batch 멱등성 학습 가치가 각각 다르게 충돌했다.

**검토한 접근들:**

**접근 A: 캘린더 고정 (지난 주/달)**
매주 월요일·매월 1일에 직전 캘린더 기간 1회 집계. 소스가 완전히 불변이라 Batch 멱등성/재시작 개념 학습엔 이상적이지만, 주·월 초반엔 표시할 데이터가 없고 "지난 주 TOP"이라는 UX 타협이 필요.

**접근 B: Rolling Window (최근 7일/30일)**
매일 `[today-7, today-1]` / `[today-30, today-1]` 덮어쓰기. 데이터가 항상 꽉 차고 사용자가 느끼는 "이번 주/달"에 더 가깝지만, 의미상 "최근 N일"에 더 가까움.

**접근 C: 하이브리드 / D: 일간 Carry-Over 확장**
둘 다 기각. C 는 배치 2종 + MV 4개로 과투자, D 는 첫 주 weekly ≈ monthly 중복 + 점수 의미 혼탁(과거 관성 + 현재 활동).

**결론**: 초안 A → 실무 e-commerce 사례 조사 후 **B 로 반전 확정**. `[today-7, today-1]` 로 잡으면 소스가 실행 시점과 무관하게 불변이라 멱등성 리스크도 해소됨. 이 반전이 스키마(기간 식별자), JobParameters, API 설계 전반에 연쇄적으로 영향.

---

## 소스 테이블 전략 — hourly 직접 집계 vs daily 중간 테이블 SOT

`ranking_metrics`(hourly) 에서 주/월을 각자 집계할지, `ranking_metrics_daily` 중간 테이블을 신설해 계층 집계할지.

**검토한 접근들:**

**접근 A: hourly 직접 집계**
주/월 Job 이 각자 `ranking_metrics` 를 GROUP BY. 상품 1개 기준 주간 스캔량 168행(7×24), 월간 720행(30×24). 단순하고 Spring Batch Chunk 개념 학습에 집중하기 좋지만 규모 증가 시 부담.

**접근 B: daily 중간 테이블 SOT (hourly → daily → weekly/monthly)**
일간 Job 이 hourly→daily 적재, 주/월은 daily 를 읽음. 스캔량 주간 7행 / 월간 30행 (**24배 감소**). 파이프라인 1단계 추가 + 시각 분산(일간 00:30 → 주/월 01:00 이후) 필요.

**결론**: **1단계 A 채택 → 측정 후 2단계 B 비교 실험**. 학습 초점이 Chunk-Oriented Processing 본질이고, 현재 학습 데이터 규모에선 A 로 충분. "같은 문제를 다른 데이터 모델로 풀 때 무엇이 바뀌는가"를 실측으로 체감하는 학습 가치 우선.

---

## weekly → monthly 체이닝 기각 — 계층 집계의 최소 공통 단위

"주간 랭킹을 모으면 월간 랭킹 아닌가?" 직관으로 weekly MV 를 monthly 의 소스로 재사용할 수 있을지 검토.

**검토한 접근들:**

**접근 A: weekly → monthly 체이닝**
weekly MV 에서 score 합산해 monthly 산출. 중복 계산 제거 이점.

**접근 B: 각자 일간 단위에서 독립 집계**
주/월 Job 이 각자 day 단위 소스를 GROUP BY.

**결론**: **A 불가, B 확정**. ISO 주 경계가 캘린더 월 경계와 일치하지 않아 "4월 랭킹"에 3월 말/5월 초 데이터가 섞이거나(주-월 편입 규칙), 경계 주를 빼면 최대 9일치 누락. **계층 집계의 최소 공통 단위는 반드시 일(day)** 이라는 원칙 확정 — 주제 2 의 B안도 이 원칙에 기반.

---

## MV 스키마: 저장 범위 + rank 컬럼 — TOP 100 + rank 저장

활동 있는 상품 전체 저장 vs TOP 100 만, 그리고 rank 를 컬럼에 저장할지 조회 시 계산할지.

**검토한 접근들:**

**접근 A: 전체 저장 + rank 없이 `ORDER BY score LIMIT 100` 계산**
미래 파생 요구(상품 상세의 "지난 주 N위") 대응 옵션 가치. 가중치 변경 시 rank 갱신 부담 회피.

**접근 B: TOP 100 만 저장 + rank 컬럼 저장**
과제 요구에 충실, 저장량 최소.

**결론**: **B 확정**. Rolling Window 전환(주제 1)으로 저장 범위 확장 유인이 약화됨. 결정적 논리는: **TOP 100 만 저장 시 가중치가 바뀌면 101위 이하 데이터가 없어 score UPDATE 만으로 새 TOP 100 식별 불가 → 원천 재집계 필수 → 재실행 과정에서 rank 도 자연스럽게 재계산 → rank 저장의 추가 비용 사실상 0.** 가중치 변경 유연성을 훼손하지 않는다.

---

## MV 기간 식별자 + PK — snapshot_date + (snapshot_date, product_id)

MV 의 기간을 무엇으로 식별할지, PK 를 어떻게 잡을지.

**검토한 접근들:**

**접근 A: `year + week_of_year` 복합 식별자, PK=(year, week, rank)**
의미론적으로 명시적. 하지만 MySQL `YEAR()` ≠ `YEAROFWEEK()` 의 ISO week-year 함정, 연도 경계 범위 쿼리 복잡.

**접근 B: `week_start_date DATE` 단일**
단순하지만 Rolling Window 전환으로 "주 시작일" 의미가 흐려짐.

**접근 C: `snapshot_date DATE` + PK=(snapshot_date, product_id)**
Rolling Window 에 정합. "그날 찍힌 스냅샷의 이 상품 기록"이 유일해야 한다는 의미론.

**결론**: **C 확정**. 사용자가 "rank 는 정렬 결과이고 identity 는 (snapshot_date, product_id) 가 맞다"고 지적해 PK 초안 `(snapshot_date, rank)` 에서 교정. rank 는 의미론적으로 정렬 부산물이지 identity 가 아님.

---

## Composite PK JPA 매핑 — Surrogate id + UNIQUE

`(snapshot_date, product_id)` 복합 identity 를 JPA 에 어떻게 매핑할지.

**검토한 접근들:**

**접근 A: `@EmbeddedId` 또는 `@IdClass`**
도메인 의미론적으로 명시적이지만, 기존 프로젝트는 전부 surrogate `Long id` 관행.

**접근 B: Surrogate `@Id Long id` + `UNIQUE(snapshot_date, product_id)`**
DB 제약으로 identity 강제 + 코드 관성 유지.

**결론**: **B 확정**. `@EmbeddedId` 가 필수는 아니며, 프로젝트 전체가 surrogate id 관행인 상황에서 MV 만 다른 스타일을 쓰는 건 득보다 실(일관성 훼손)이 큼.

---

## 엔티티/모듈 배치 — 두 app 에 중복 선언 + Repository 분리

MV 테이블을 commerce-batch(쓰기)와 commerce-api(읽기) 양쪽에서 접근해야 함. 기존 구조는 엔티티가 각 app 에 귀속됨(`modules/jpa` 는 설정/BaseEntity 전용).

**검토한 접근들:**

**접근 A: 공유 모듈 (`modules/ranking-mv`) 신설**
중복 제거 이점. 하지만 현재까진 다른 도메인 공유 사례 없음 → 관행 깨기.

**접근 B: 두 app 에 엔티티 중복 선언 + Repository 각자 분리**
기존 철학(app 독립성) 존중. batch 는 `MvProductRankWeekly` + `upsertAll`, api 는 `WeeklyRank` + `findBySnapshot...` 로 관점도 이름도 다름.

**결론**: **B 확정**. 같은 테이블을 "다른 역할로 본다"는 점이 이름부터 드러남. Repository 분리는 선호가 아니라 **구조 강제** — Spring Data JPA Repository 가 엔티티 타입으로 파라미터화되므로 엔티티 분리 결정이 Repository 분리를 자동으로 강제. ISP(쓰기·읽기 메서드 무관) + 트랜잭션 경계(batch=chunk tx, api=readOnly+replica) 자연 분리 부수효과.

---

## Job/Step 구조 — 독립 Job 2개 + 단일 Chunk Step

주/월을 한 Job 의 Step 으로 묶을지, 각자 독립 Job 으로 할지. Chunk vs Tasklet.

**검토한 접근들:**

**접근 A: 단일 Job + 주·월 Step 2개**
한 번 실행으로 양쪽 완료. 단 한쪽 실패 시 전체 재실행/복구 복잡.

**접근 B: 독립 Job 2개 + 각자 단일 Chunk Step**
실패·재실행·스케줄 개별 가능. JobRepository 이력 깔끔. Tasklet 대신 Chunk 채택 — TOP 100 규모에선 스트리밍 이점 약하지만 학습 초점이 Chunk-Oriented Processing.

**결론**: **B 확정**. Conditional Flow(`.on().to().from()`) 는 현재 Step 1개라 과함 → 추후 daily 중간 테이블 도입 시 검증 Step + fallback Step 등으로 도입 가치 있음(미래 메모).

---

## Reader 방식 — JdbcCursorItemReader + JDBC, Chunk 50

TOP 100 집계를 ItemReader 로 어떻게 흘려줄지, JDBC 와 JPA 중 어느 층을 쓸지, Chunk 크기는.

**검토한 접근들:**

**접근 A: JdbcPagingItemReader / Keyset**
대규모 데이터 커넥션 대여·반납 패턴으로 안전. 하지만 score 가 GROUP BY 이후 파생값이라 Keyset 키 사용 불가 + LIMIT 100 규모엔 OFFSET 비용 무의미.

**접근 B: JdbcCursorItemReader + Chunk 50 + JDBC**
100행을 2청크로 순환시켜 Chunk 동작을 직접 체감 가능. Reader 결과가 엔티티가 아닌 집계 DTO, Writer 가 UPSERT(JPA 약점) — 양쪽 모두 JPA 매핑 이점 없음.

**결론**: **B 확정**. Chunk 50 은 학습 목적(2청크 순환) 의도적 선택. MySQL Connector/J 의 스트리밍 함정(`setFetchSize(Integer.MIN_VALUE)` 필요)은 LIMIT 100 규모엔 해당 없음 — 2단계 확장 시 재점검 메모.

---

## Writer 구현 방식 — 커스텀 ItemWriter + Repository.upsertAll 위임

JDBC 기반 Writer 로 확정된 상태에서 구체 구현 방식 선택.

**검토한 접근들:**

**접근 A: `JpaItemWriter` (merge 모드)**
merge() 가 내부적으로 SELECT 발사 → 청크 50건에 SELECT 50회 추가 왕복. `addBatch()` 로 못 묶여 `hibernate.jdbc.batch_size` 최적화도 무효.

**접근 B: `JpaItemWriter` (persist 모드)**
INSERT 전용이라 재실행 시 UNIQUE 위반으로 `PersistenceException`. MV 재집계 시나리오와 맞지 않음.

**접근 C: `JdbcBatchItemWriter` 직접 사용**
동작은 올바르나 SQL 이 Writer Bean 에 박혀 주제 4 의 Repository 분리 결정과 어긋남.

**접근 D: 커스텀 `ItemWriter` + `Repository.upsertAll` 위임**
주제 4 시그니처(`MvProductRankWeeklyRepository.upsertAll`) 정합. `commerce-streamer` 의 `RankingMetricsJpaRepository.upsertViewCount` 관행과 일관.

**결론**: **D 확정**. 핵심 근거 2가지: (1) 주제 4 의 Repository 분리 결정이 이미 `upsertAll(List)` 시그니처를 낳음 — Writer 는 그걸 호출하는 얇은 어댑터. (2) **SQL 이 infrastructure 레이어에 모이는** 프로젝트 관행 유지. `created_at` 은 `ON DUPLICATE KEY UPDATE` 절에서 제외해 최초 INSERT 시각 보존.

---

## 멱등성 — Rolling Window + run.id + trigger 하이브리드 JobParameter

Rolling Window `[today-7, today-1]` 전환으로 소스 불변성은 확보됨. 그 위에서 재실행 2종(같은 날 복구 / 가중치 변경 재집계)을 어떻게 구분해 처리할지.

**검토한 접근들:**

**접근 A: `run.id` 만 추가**
재실행마다 값 다르면 새 JobInstance 생성. 단순하지만 "왜 재실행했는지"가 JobRepository 이력에 안 남음.

**접근 B: `run.id` + `trigger` enum 하이브리드**
`trigger ∈ {WEIGHT_CHANGE, DATA_FIX, MANUAL_RERUN}` 를 `identifying=true` 로 부착. JobInstance 유일성은 `run.id` 로 이미 확보되지만, `trigger` 는 **자기 기록성** + 운영 쿼리용으로 존재.

**결론**: **B 확정**. `SCHEDULED` 값은 미정의 — 정상 스케줄러는 `trigger` 자체를 생략해 "파라미터 비어있음 = 자동 실행" 해석. 2단계 멱등성(JobInstance + SQL UPSERT)이 독립 작동해 분산 락 역할까지 겸함 — `ShedLock` 불필요(다중 인스턴스 시 재검토 메모).

---

## API 엔드포인트 설계 — 경로 분리 (`/weekly`, `/monthly`)

기존 `/rankings` + `/rankings/hourly` 구조에 주/월을 어떻게 추가할지.

**검토한 접근들:**

**접근 A: `/rankings?period=weekly|monthly` 파라미터 통합**
URL 하나, 내부 분기로 처리.

**접근 B: `/rankings/weekly`, `/rankings/monthly` 경로 분리**
기존 `/hourly` 분리 선례 일관. 각 엔드포인트 독립 시맨틱.

**결론**: **B 확정**. 내부적으로 저장소가 분기되어야 함(Redis ZSET vs DB MV)이 필연이라 "얕은 통합"은 실익 없음 — 경로 분리가 외부 계약과 내부 구현 양쪽에 자연스러움. 기존 선례 존중.

---

## 캐시 설계 — 두 캐시의 역할 분리

`/rankings/weekly` 응답 캐시를 어떻게 구성할지. Rolling Window 의 "snapshot_date 내부 데이터 불변성" 을 어떻게 활용할지.

**검토한 접근들:**

**접근 A: 단일 캐시 `rankings:weekly:{page}:{size}` + TTL 갱신**
구조 단순. 새 snapshot 배치 후 수동 invalidate 필요 or 짧은 TTL → 스탬피드 위험.

**접근 B: 두 캐시 분리 — 메인(Immutable) + 메타(Mutable)**
메인: `rankings:weekly:{snapshot_date}:{page}:{size}` (24h TTL). 키에 snapshot_date 박혀있어 같은 키는 절대 값이 안 바뀜 → 수동 invalidate 불필요, 스탬피드 구조적 회피.
메타: `rankings:weekly:latest_date` (25h TTL). `date` 파라미터 미지정 요청 시 최신 snapshot 식별용.

**결론**: **B 확정**. "date 명시 요청"은 메타 캐시 건너뛰고 메인 조회, "date 생략 요청"만 메타→메인 2단. 새 배치 완료 → 새 snapshot_date → 자동으로 새 메인 키 생성 → 구 키는 TTL 만료로 자연 삭제. 메인 캐시 Cold Start(배치 완료 직후 첫 요청) 스탬피드는 학습 프로젝트 수준에선 수용 — pre-warming 은 기술부채.

---

## latest_date 캐시 동기화 — JobExecutionListener 기반 put + 25h TTL

`latest_date` 메타 캐시를 언제·누가 갱신할지. UX 에 최신성이 중요.

**검토한 접근들:**

**접근 A: 짧은 TTL (5분) + DB `MAX(snapshot_date)` 폴백**
캐시 miss 시 DB 폴백, 복잡한 이벤트 없음. 하지만 TTL 만료 순간 다수 요청이 동시에 DB 조회 → **스탬피드**. 5분 지연도 UX 저해.

**접근 B: 캐시 제거하고 매 요청 DB `MAX()` 조회**
단순. 작은 쿼리지만 "date 생략 요청" 트래픽이 크면 부담.

**접근 C: `JobExecutionListener.afterJob` 에서 put + 긴 TTL(25h)**
배치 성공 시 Listener 가 Redis 에 새 snapshot_date 덮어쓰기. API 측 MISS 는 cold start(리스너 한 번도 안 돈 상태) 에서만 발생 → DB `findLatestSnapshotDate()` 폴백 후 재 put. TTL 25h 는 다음 배치 이전 자연 만료 방지 안전망.

**결론**: **C 확정**. 배치 FAILED/STOPPED 시 put 생략 — 실패한 Job 의 snapshotDate 를 캐시에 박으면 "존재하지 않는 snapshot 가리킴" 위험. 이전 성공값 유지가 안전. `RankingLatestDateCacheListener` 를 기존 `JobListener`/`StepMonitorListener` 와 **별도 클래스**로 분리(SRP) — 캐시 푸시 실패가 로깅까지 중단시킬 위험 방지.

---

## 랭킹 가중치 관리 — DB SOT 동적 조회 vs 하드코딩

배치 실행 시 점수 계산에 사용되는 가중치(VIEW/LIKE/ORDER별 배율)를 어디서 어떻게 가져올지.

**검토한 접근들:**

**접근 A: 코드 내 하드코딩**
`view * 0.1 + like * 0.2 + order * 0.00001` 상수 고정. 가중치 변경 시 코드 수정 + 재배포 필요. 단순하지만 가중치 변경 이력이 코드 이외 어디에도 남지 않음.

**접근 B: 환경변수/설정 파일**
`application.yml` 에 가중치 속성 정의. 배포 없이 설정값 변경 가능. 그러나 여러 인스턴스 간 동기화 문제, 이력 관리 부재.

**접근 C: Redis 캐시에서 조회**
빠른 조회. 단 캐시 TTL 불일치로 오래된 가중치가 그대로 사용될 위험 — 특히 `trigger=WEIGHT_CHANGE` 재실행 시나리오에서 캐시 갱신 누락이 발생할 수 있음.

**접근 D: DB SOT (`ranking_weight` 테이블) 에서 조회**
`BatchRankingWeightRepository.findWeightByEventType("VIEW")` 로 실행 시점에 직접 조회. DB가 단일 SOT가 되어 이력 관리가 가능하고, 캐시 불일치 문제가 없음.

**결론**: **D 확정**. 핵심 근거: 배치는 매일 1회만 실행되므로 **신속성(캐시 조회 속도)보다 정확성(SOT 일치)이 우선**이다. Redis 캐시는 TTL 기간 동안 오래된 값이 남아있을 수 있어, `WEIGHT_CHANGE` trigger로 재집계 시 여전히 구 가중치가 사용될 위험이 있다. DB 직접 조회 1회 비용(밀리초 단위)은 전체 배치 실행 시간(수십 초) 대비 무시 가능. 가중치 변경 로그가 `ranking_weight` 테이블에 자연스럽게 남아 운영 이력 추적도 가능.

**점수 공식 추가 결정**: 단순 선형 합산 대신 `LOG(1 + x)` 변환 적용. 매출이 극단적으로 큰 상품이 랭킹을 독점하는 롱테일 왜곡을 완화하고, 다양한 상품이 경쟁 가능한 분포를 만들기 위함. `+1`은 값이 0일 때 `LOG(0) = -∞` 방지.

---

## 배치 실행 주체 — @Scheduled vs 외부 크론

주/월 Job 을 어디서 트리거할지.

**검토한 접근들:**

**접근 A: 외부 크론 (K8s CronJob, AWS EventBridge, Argo Workflow)**
실무 정답. 실행 주기·실패·스케일링이 배포 리소스로 명시됨. 단 인프라 셋업 부가 비용.

**접근 B: 애플리케이션 내부 `@Scheduled`**
commerce-batch 가 항시 떠 있다고 가정하면 `@EnableScheduling` + cron 표현식으로 충분. 단일 인스턴스 가정 깨면 ShedLock 또는 외부 크론으로 전환 필요.

**결론**: **B 확정 (단일 인스턴스 가정)**. 실행 시각은 weekly 01:00 KST / monthly 01:30 KST — Rolling Window `[today-7/30, today-1]` 이라 자정 후면 아무 때나 가능하나 30분 간격 분산해 DB/로그 관찰성 확보. `spring.batch.job.enabled=false` 로 부팅 시 Job 자동 실행 막아야 (스케줄러만 돌도록).

---

## 실패 알림 — 이번 주차 기술부채로 명시 (미결)

배치 실패 시 운영 알림 경로.

**검토한 접근들:**

**접근 A: Slack Appender 이번 주차 포함**
`supports/logging` 에 이미 `maricn/logback-slack-appender` 포함 — `logback-spring.xml` 에 레벨 세팅만 추가하면 `log.error()` 가 자동 Slack 전파. Webhook URL 환경변수 관리 필요.

**접근 B: 이번 주차엔 `log.error()` + `JobExecution.status='FAILED'` 로 관찰성만 확보, Slack 은 후속**
학습 우선순위(스케줄링·캐시 동기화) 집중. Webhook URL 관리 + 테스트 부담 회피. 현재도 가시성은 0이 아님 — `BATCH_JOB_EXECUTION` 테이블에 실패 이력 남음.

**결론 / 미결**: **이번 주차 B (기술부채), 후속 세션에서 A 로 전환 예정**. 명시적 TODO: `logback-spring.xml` 에 `<appender-ref ref="SLACK"/>` 을 `ERROR` 레벨로 부착.
