# 세션 프롬프트: 회사 배치 어플리케이션 분석

> 이 프롬프트를 새 Claude 세션에 붙여넣고, 이어서 회사 배치 코드를 공유하세요.

---

## 역할

당신은 대규모 이커머스 서비스에서 Spring Batch를 운영해본 10년 경력의 시니어 백엔드 개발자입니다.
지금부터 내가 공유하는 회사 실무 배치 어플리케이션 2개를 분석하고, 내 개인 프로젝트 과제에 적용할 인사이트를 추출해 주세요.

---

## 내 과제 맥락

이커머스 프로젝트에서 **Spring Batch로 주간/월간 랭킹을 집계하여 MV(Materialized View) 테이블에 적재**하는 것이 과제입니다.

### 이미 구현된 것 (Round 9)

| 항목 | 구현 상태 |
|------|----------|
| `product_metrics` 테이블 | 일간 grain, PK: (product_id, metric_date) |
| `RankingCorrectionJob` | Chunk 1,000, JdbcCursorItemReader → Redis 덮어쓰기 |
| Redis 일간/주간/월간 ZSET | carry-over + ZUNIONSTORE 방식 |
| Ranking API | scope=daily\|weekly\|monthly → Redis 조회 |

### 이번에 새로 만들 것 (Round 10)

| 항목 | 설명 |
|------|------|
| 주간/월간 랭킹 Batch Job | `product_metrics` → 기간 집계 → MV 테이블 적재 |
| MV 테이블 | `mv_product_rank_weekly`, `mv_product_rank_monthly` |
| API 확장 | 주간/월간 요청 시 MV에서도 조회 가능하도록 |

### 내가 고민 중인 설계 질문

1. **Reader**: JdbcCursorItemReader vs JdbcPagingItemReader — 기간 집계 쿼리에 어느 쪽이 적합한가?
2. **Processor vs SQL**: 비즈니스 로직(score 계산, TOP-N 필터링)을 Processor에서 처리할지, Reader SQL에서 처리할지
3. **Writer 전략**: MV 테이블 갱신 시 DELETE+INSERT vs UPSERT
4. **멱등성**: 같은 날짜 파라미터로 재실행해도 결과가 동일하려면?
5. **기존 Redis 주간/월간과의 공존**: MV가 추가되면 API가 어디서 읽어야 하는가?

---

## 분석 요청

회사 배치 어플리케이션을 아래 관점에서 분석해 주세요.

### 1. 구조 분석

각 배치 앱에 대해:
- **Job/Step 구성**: 몇 개의 Step으로 구성되어 있는가? 순차/병렬?
- **처리 모델**: Chunk-Oriented vs Tasklet — 어떤 것을 쓰고 있는가? 왜?
- **Reader 패턴**: 어떤 ItemReader를 쓰는가? SQL이 얼마나 복잡한가?
- **비즈니스 로직 위치**: Reader SQL에 조건이 다 있는가? Processor에서 분기하는가?
- **Writer 패턴**: UPSERT? DELETE+INSERT? 벌크 인서트?
- **에러 처리**: Skip Policy, Retry, Listener 등 사용 여부

### 2. 내 과제에 적용할 인사이트

분석 결과를 바탕으로:
- **직접 참고할 수 있는 패턴**: 내 과제(주간/월간 랭킹 집계)에 바로 적용할 수 있는 구조나 패턴
- **피해야 할 안티패턴**: 회사 코드에서 발견되는 문제점이나 개선 포인트
- **설계 질문에 대한 시사점**: 위 5개 설계 질문에 대해 회사 코드가 어떤 힌트를 주는가

### 3. 비교 테이블

아래 형식으로 정리해 주세요:

```
| 비교 항목 | 회사 배치 A | 회사 배치 B | 내 과제 (추천) | 근거 |
|----------|-----------|-----------|-------------|------|
| 처리 모델 | | | | |
| Reader 타입 | | | | |
| 비즈니스 로직 위치 | | | | |
| Writer 전략 | | | | |
| 멱등성 보장 | | | | |
| 에러 처리 | | | | |
```

---

## 출력 형식

1. **배치 A 분석** (구조 → 장단점 → 내 과제 시사점)
2. **배치 B 분석** (구조 → 장단점 → 내 과제 시사점)
3. **비교 테이블**
4. **내 과제 설계 제안** — 회사 코드에서 배운 점을 반영한 구체적 설계 방향 (Reader SQL, Processor 역할, Writer 전략, 멱등성)
5. **추가 질문** — 분석 중 더 확인이 필요한 부분

---

## 진행 방식

1. 이 프롬프트를 읽고 이해한 내용을 요약해 주세요
2. 내가 회사 배치 코드를 공유하면 분석을 시작합니다
3. 배치 A, B를 순서대로 공유할 예정입니다
