# Round 10 — 개념 공부 로드맵 & 과제 진도표

---

## Part A. 개념 공부 로드맵

> 초급 개발자 대상. 선수 지식부터 실무 적용까지 순서대로 구성.
> "이것을 모르면 다음 단계가 막힌다"는 기준으로 의존 순서를 잡았다.

### Step 1. 선수 지식 점검

과제를 시작하기 전에 확실해야 하는 기초 체력.

| 주제 | 왜 필요한가 | 확인 질문 |
|------|------------|----------|
| SQL 집계 함수 | Batch Reader가 읽는 쿼리를 이해해야 한다 | `GROUP BY`, `SUM`, `HAVING`, 서브쿼리로 "상품별 주간 매출 TOP 100"을 작성할 수 있는가? |
| 트랜잭션 기초 | Chunk 단위 커밋/롤백의 의미를 이해해야 한다 | `@Transactional`의 propagation, rollbackFor를 설명할 수 있는가? |
| Spring Bean 생명주기 | JobScope, StepScope가 왜 존재하는지 이해해야 한다 | `@Scope("step")`이 일반 singleton과 뭐가 다른지 설명할 수 있는가? |
| JDBC vs JPA 차이 | ItemReader 선택(JdbcCursorItemReader vs JpaPagingItemReader)에 영향 | 대량 조회에서 JPA N+1이 왜 위험한지 아는가? |

### Step 2. 배치 처리의 본질

코드를 쓰기 전에 "왜 배치인가"를 먼저 이해해야 한다.

**핵심 질문: "이 작업을 왜 API 서버에서 안 하는가?"**

| 개념 | 설명 | 연결 |
|------|------|------|
| 실시간 vs 배치 트레이드오프 | 실시간은 신속성, 배치는 정확성+효율성 | 우리 프로젝트: 일간 랭킹은 실시간(Redis), 주간/월간 집계는 배치(Spring Batch) |
| 멱등성(Idempotency) | 같은 Job을 두 번 돌려도 결과가 같아야 한다 | MV 테이블에 UPSERT or DELETE+INSERT 전략 |
| 대량 데이터와 메모리 | 10만 행을 한 번에 읽으면 OOM | Chunk 단위 처리로 메모리 제어 |

**반드시 읽어볼 것:**
- 과제 개념 문서의 "실시간 vs 배치 트레이드오프" 표
- 실무 배치 시나리오 4가지 (정산, 랭킹, 정리, DW 적재)

### Step 3. Spring Batch 아키텍처

Spring Batch의 계층 구조를 이해해야 코드가 읽힌다.

```
JobLauncher
  └── Job (실행 단위)
        └── Step (세부 단계, 1개 이상)
              ├── Chunk-Oriented: Reader → Processor → Writer
              └── Tasklet: 단발성 작업
```

**필수 개념:**

| 개념 | 설명 | 왜 중요한가 |
|------|------|------------|
| Job / Step | 배치의 실행 단위와 세부 단계 | Job 1개에 Step 여러 개 가능. 순서 제어, 조건 분기 |
| JobRepository | Job 실행 이력을 DB에 기록 (메타 테이블) | 재실행 판단, 실패 복구의 근거. `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION` 등 |
| JobParameters | Job 실행 시 전달하는 파라미터 | 같은 Job을 날짜별로 실행 (e.g. `targetDate=20260414`) |
| @JobScope / @StepScope | JobParameter를 주입받기 위한 지연 생성 | `@Value("#{jobParameters['targetDate']}")`가 동작하려면 필수 |
| Chunk | N건씩 읽고-가공하고-쓰는 반복 단위 | chunk(1000) = 1000건 읽고 쓴 후 커밋. 실패 시 해당 chunk만 롤백 |

**선수 관계:** Step 2(왜 배치인가) → Step 3(Spring Batch 구조)

### Step 4. Chunk-Oriented Processing 상세

대량 데이터 처리의 핵심 패턴.

```
while (hasMore) {
    List<I> items = reader.read(chunkSize);   // DB에서 N건 읽기
    List<O> outputs = new ArrayList<>();
    for (I item : items) {
        outputs.add(processor.process(item));  // 가공
    }
    writer.write(outputs);                     // 일괄 저장
    transaction.commit();                      // chunk 단위 커밋
}
```

**ItemReader 종류 비교 (시니어가 반드시 알아야 할 차이):**

| Reader | 동작 방식 | 장점 | 주의점 |
|--------|----------|------|--------|
| JdbcCursorItemReader | DB 커서를 열고 한 행씩 fetch | 메모리 효율적, 순서 보장 | 커넥션을 Step 동안 유지 → 커넥션 풀 점유 |
| JdbcPagingItemReader | LIMIT/OFFSET으로 페이지 단위 조회 | 커넥션 점유 짧음 | 정렬 기준 필수, 데이터 변경 시 누락/중복 가능 |
| JpaPagingItemReader | JPA로 페이지 조회 | 엔티티 매핑 편리 | N+1 위험, 대량에서 성능 저하 |

**우리 프로젝트의 선택:** 기존 RankingCorrectionJob이 `JdbcCursorItemReader` 사용 → 동일 패턴 재활용

**Chunk Size 결정 기준 (자주 놓치는 포인트):**

| chunk size | 효과 |
|-----------|------|
| 너무 작음 (10) | 커밋 횟수 ↑, DB I/O 오버헤드 ↑ |
| 너무 큼 (100,000) | 메모리 ↑, 실패 시 재처리 범위 ↑ |
| **적정 (500~5,000)** | 기존 Job이 1,000으로 설정. 벤치마크로 조정 |

### Step 5. Materialized View

**핵심: "미리 계산해둔 조회 전용 테이블"**

| 개념 | 설명 |
|------|------|
| MV란? | 복잡한 집계 쿼리 결과를 별도 테이블에 저장. 조회 시 집계 없이 SELECT만 |
| MySQL에서의 MV | 네이티브 MV 미지원 → 별도 테이블 + 배치 적재로 구현 |
| 갱신 전략 | 전체 교체(DELETE + INSERT) vs 증분(UPSERT). 데이터 크기와 빈도에 따라 선택 |
| 원본과의 관계 | `product_metrics`(원본) → Spring Batch → `mv_product_rank_weekly/monthly`(MV) |

**MV vs 실시간 집계:**

| 기준 | 실시간 집계 (매 요청) | MV (사전 집계) |
|------|---------------------|---------------|
| 조회 속도 | 느림 (GROUP BY + ORDER BY) | 빠름 (단순 SELECT) |
| 데이터 신선도 | 항상 최신 | 배치 주기만큼 stale |
| DB 부하 | 높음 (매번 집계) | 낮음 (배치 때만) |
| 적합 | 소규모, 실시간 필수 | 대규모, 주기적 갱신 허용 |

### Step 6. 우리 프로젝트 맥락 (기존 구현과의 연결)

Round 9에서 이미 구축한 것과 Round 10의 관계를 이해해야 설계 판단이 가능하다.

**현재 아키텍처 (Round 9 완성):**

```
[실시간 경로 — Speed Layer]
  Kafka → MetricsConsumer → Redis Hash/ZSET (일간)
  → 23:50 스케줄러: carry-over + ZUNIONSTORE (주간/월간 Redis ZSET)

[배치 보정 — Batch Layer]
  product_metrics(DB) → RankingCorrectionJob → Redis Hash/ZSET 덮어쓰기

[API]
  GET /api/v1/rankings?scope=daily|weekly|monthly → Redis ZSET 조회
```

**Round 10이 추가하는 것:**

```
[배치 집계 — Materialized View Layer]
  product_metrics(DB) → Spring Batch Job → mv_product_rank_weekly/monthly(DB)

[API 확장]
  주간/월간 요청 → MV 테이블 조회 (Redis 대신 or 함께)
```

**핵심 설계 질문: Redis ZSET 주간/월간과 MV 테이블은 어떻게 공존하는가?**

| 관점 | Redis ZSET (기존) | MV 테이블 (신규) |
|------|-------------------|-----------------|
| 데이터 소스 | Redis carry-over 기반 | DB product_metrics 기반 |
| 신선도 | 일 1회 갱신 (23:50) | 배치 주기 (일 1회) |
| 정확도 | carry-over 누적 근사치 | DB 원장 기반 정확값 |
| 조회 속도 | O(log N + M) ~0.01ms | DB SELECT ~수ms |
| 장애 시 | Redis 장애 → 조회 불가 | DB만 살아있으면 조회 가능 |

→ 이 트레이드오프를 설계 문서에서 분석하고 판단을 내려야 한다.

### Step 7. 운영 관점 (시니어가 강조하는 포인트)

코드가 동작하는 것과 운영 가능한 것은 다르다.

| 주제 | 질문 | 왜 중요한가 |
|------|------|------------|
| 멱등성 | 같은 날짜로 Job을 두 번 돌리면? | MV 데이터가 2배가 되면 안 된다 |
| 실패 복구 | Step 2에서 실패하면 Step 1부터 다시? | Spring Batch의 재시작 메커니즘 이해 |
| 모니터링 | Job이 성공했는지 어떻게 아는가? | 처리 건수, 소요 시간, 실패 알림 |
| 스케줄링 | 언제 돌리는가? 다른 Job과 충돌은? | 기존 23:50 스케줄러, RankingCorrectionJob과의 시간 배치 |
| 데이터 정합성 | MV와 Redis 랭킹이 다르면? | 어느 쪽이 source of truth인지 정해야 한다 |

---

## Part B. 과제 수행 진도표

### 기존 구현 현황 (Round 9)

| 항목 | 상태 | 비고 |
|------|------|------|
| commerce-batch 모듈 (Spring Batch) | ✅ 완료 | 6개 Job 운영 중 |
| product_metrics 테이블 (daily grain) | ✅ 완료 | PK: (product_id, metric_date) |
| RankingCorrectionJob (배치 보정) | ✅ 완료 | Chunk 1,000, JdbcCursorItemReader |
| Redis 일간/주간/월간 ZSET | ✅ 완료 | 23:50 carry-over + ZUNIONSTORE |
| Ranking API (scope 파라미터) | ✅ 완료 | daily/weekly/monthly → Redis 조회 |
| MV 테이블 | ❌ 미구현 | Round 10 핵심 과제 |

---

### Phase 0. 설계

> 코드를 쓰기 전에 결정해야 할 것들.

- [ ] **0-1. 아키텍처 결정: Redis ZSET vs MV 테이블 역할 분담**
  - Redis 주간/월간과 MV 테이블의 공존 전략 (대체? 보완? fallback?)
  - API가 어느 소스에서 읽는가 (scope별 분기)
  - 설계 문서에 판단 근거 기록

- [ ] **0-2. MV 테이블 스키마 설계**
  - `mv_product_rank_weekly` / `mv_product_rank_monthly` DDL
  - PK 구성 (product_id + 기간키? 별도 id?)
  - 저장 항목: rank, score, 개별 메트릭(view/like/order), 기간 식별자
  - TOP 100만 저장하는 전략 (Writer에서 제한? 쿼리에서 제한?)

- [ ] **0-3. Spring Batch Job 설계**
  - Job 이름, Step 구성 (단일 Step? 다중 Step?)
  - Reader: product_metrics에서 기간별 집계 쿼리
  - Processor: score 계산 (기존 calculateScore 재활용)
  - Writer: MV 테이블 적재 (UPSERT vs DELETE+INSERT)
  - JobParameter: targetDate, scope(weekly/monthly)
  - 멱등성 보장 전략

- [ ] **0-4. 스케줄링/실행 전략**
  - 실행 시점 (기존 23:50 carry-over, RankingCorrectionJob과의 관계)
  - 주간 Job 실행 주기 (매일? 월요일만?)
  - 월간 Job 실행 주기 (매일? 월초만?)

- [ ] **0-5. 설계 문서 작성**
  - `docs/design/10-batch-ranking-system.md` 생성
  - 기존 09 설계와의 연결 명시

---

### Phase 1. 구현

- [ ] **1-1. MV 엔티티/리포지토리**
  - Entity 클래스 (commerce-batch 또는 공통 모듈)
  - Repository (JPA or JDBC)

- [ ] **1-2. 주간 랭킹 Batch Job**
  - JobConfig 클래스
  - ItemReader: product_metrics → 최근 7일 집계
  - ItemProcessor: score 계산 + 순위 산정
  - ItemWriter: mv_product_rank_weekly 적재
  - JobParameter 처리 (targetDate)

- [ ] **1-3. 월간 랭킹 Batch Job**
  - 주간과 동일 구조, 집계 기간만 30일
  - mv_product_rank_monthly 적재

- [ ] **1-4. API 확장 (필요 시)**
  - 주간/월간 요청 시 MV 테이블에서 조회하도록 분기
  - 기존 Redis 조회 경로와의 공존 or 전환

- [ ] **1-5. 스케줄링/실행 설정**
  - application.yml Job 설정
  - 실행 방법 문서화 (커맨드라인, 스케줄러)

---

### Phase 2. 테스트

- [ ] **2-1. 단위 테스트**
  - Score 계산 로직 (기존 calculateScore와 일치 검증)
  - Processor 변환 로직

- [ ] **2-2. 통합 테스트**
  - Job 전체 실행 (Testcontainers + @SpringBatchTest)
  - product_metrics에 시드 데이터 → Job 실행 → MV 결과 검증
  - 멱등성 검증 (같은 날짜로 2회 실행 → 결과 동일)

- [ ] **2-3. 엣지 케이스**
  - 데이터 없는 날짜로 실행
  - 7일/30일 미만 데이터로 실행 (서비스 초기)
  - 대량 데이터 성능 테스트 (상품 10만건 기준)

---

### Phase 3. 시나리오 기반 모니터링

- [ ] **3-1. 시나리오 정의**
  - 시나리오 1: 정상 실행 — 시드 데이터 기반 주간/월간 Job 실행
  - 시나리오 2: MV vs Redis 결과 비교 — 같은 기간 랭킹 TOP 20 대조
  - 시나리오 3: 재실행 — 동일 파라미터로 2회 실행, 멱등성 확인

- [ ] **3-2. 모니터링 지표**
  - Job 실행 시간, 처리 건수 (Spring Batch 메타 테이블)
  - MV 적재 건수
  - Grafana 대시보드 (선택)

- [ ] **3-3. 결과 기록**
  - 스크린샷 또는 로그 기반 실행 결과 정리
  - 성능 수치 (소요 시간, 처리량)

---

### Phase 4. 테크니컬 라이팅

- [ ] **4-1. 블로그 글 구성안 작성**
  - 핵심 메시지 1줄
  - 목차 + 섹션별 핵심 메시지

- [ ] **4-2. 초안 작성**
  - 설계 판단 중심 (왜 MV인가, 왜 이 구조인가)
  - 기존 Redis 랭킹과의 관계
  - 코드는 핵심 판단을 보여주는 최소한만

- [ ] **4-3. Retrospective (10주 회고)**
  - 1~10주 전체 여정 요약
  - 가장 큰 전환점
  - Trade-off 판단 1~2개
  - 실전 연결 포인트

---

### Phase 5. PR & 리뷰 포인트

- [ ] **5-1. PR 작성**
  - 변경 사항 요약
  - 설계 판단 근거
  - 테스트 계획

- [ ] **5-2. 리뷰 포인트 작성 (2~3개)**
  - 설계 고민이 드러나는 열린 질문
  - "배경 → 대안 비교 → 선택 근거 → 질문" 구조

---

### 일정 가이드 (참고용)

| 일차 | 단계 | 핵심 산출물 |
|------|------|-----------|
| Day 1 | 개념 공부 (Step 1~4) + Phase 0 설계 | 설계 문서 초안 |
| Day 2 | 개념 공부 (Step 5~7) + Phase 0 완료 | 설계 문서 확정 |
| Day 3 | Phase 1 구현 (Job + MV) | 주간/월간 Job 동작 |
| Day 4 | Phase 1 완료 + Phase 2 테스트 | 테스트 통과 |
| Day 5 | Phase 3 모니터링 | 시나리오 검증 결과 |
| Day 6 | Phase 4 테크니컬 라이팅 | 블로그 초안 |
| Day 7 | Phase 5 PR + 리뷰 포인트 | PR 제출 |
