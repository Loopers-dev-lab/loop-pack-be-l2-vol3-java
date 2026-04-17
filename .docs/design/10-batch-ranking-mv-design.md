# Round 10 - Spring Batch 기반 주간/월간 랭킹 설계

> **TL;DR**: Round 10의 핵심은 `product_metrics`(일간 원장)를 기준으로 주간/월간 TOP 100을 **배치로 사전 집계(Materialized View)** 하고, API에서 일/주/월을 같은 계약으로 조회하는 것이다.  
> QnA에서 확정한 운영 원칙은 **Option A(staging 완성 후 switch)**, **ISO 주차 + KST**, **TOP 100 클램프(page/size 초과 시 빈 목록 + total 유지)** 이다.

**관련 문서**

- 요구/학습 원문: [../qna/10-subject.md](../qna/10-subject.md), [../qna/10-quest.md](../qna/10-quest.md)
- 설계 QnA(결정 기록): [../qna/10-qna.md](../qna/10-qna.md)
- 구현 순서: [../Implementation/10-batch-ranking-implementation-roadmap.md](../Implementation/10-batch-ranking-implementation-roadmap.md)
- 선행 레퍼런스: [09-ranking-redis-zset-design.md](./09-ranking-redis-zset-design.md)

---

## 1. Scope와 결정사항


| 구분     | 이번 라운드 고정                                                        |
| ------ | ---------------------------------------------------------------- |
| 입력 원장  | `product_metrics`                                                |
| 집계 출력  | `mv_product_rank_weekly`, `mv_product_rank_monthly`              |
| 배치 모델  | Chunk 중심 + 필요 시 Tasklet 혼합                                       |
| 공개 방식  | Option A: staging 완성 후 active switch                             |
| 시간 규칙  | `Asia/Seoul`, 주 시작 월요일, ISO-8601 week-based-year                 |
| API 확장 | 기존 `/api/v1/rankings`를 day/week/month로 확장, 기존 `date` 요청 하위 호환 유지 |
| TOP 제한 | MV는 TOP 100, API는 total=100 기준 클램프(범위 초과 시 빈 목록)                 |


---

## 2. 왜 배치 + MV인가

- `10-subject`가 말한 대로 주/월 랭킹은 즉시성보다 정확성과 비용 효율이 중요하다.
- 매 요청마다 주/월 집계를 계산하면 DB 부하가 커져 API 응답 안정성을 해친다.
- 따라서 조회 전용 MV에 사전 집계하고 API는 읽기 전용 경로로 단순화한다.

---

## 3. 데이터 모델 설계

## 3.1 MV 테이블

- `mv_product_rank_weekly`
- `mv_product_rank_monthly`

권장 컬럼:

- `period_key` (예: `2026W15`, `202604`)
- `product_id`
- `rank` (1~100)
- `score`
- `updated_at`
- `version` (Option A switch용)

권장 제약:

- `UNIQUE(period_key, product_id)`
- 조회 인덱스: `(period_key, rank)`

근거:

- QnA Q3에서 같은 period 재실행 멱등성과 중복 방지 요구를 확정했다.

## 3.2 기간 키 규칙

- timezone: `Asia/Seoul`
- week: ISO-8601 week-based-year + Monday
- month: `yyyyMM`
- 연초 경계(`2026-01-01` 등): ISO 계산 결과를 그대로 week key로 사용

---

## 4. 배치 공개 전략 (Option A)

QnA Q5 결정:

- staging 테이블/버전에 먼저 완성
- 검증 성공 시에만 active 포인터/버전 switch
- 실패 시 active는 이전 스냅 유지

효과:

- 반쯤 적재된 데이터 비노출
- 빈 결과 방지(이전 스냅 허용)
- 같은 period 재실행 멱등성 강화

---

## 4.1 멱등성 보장 규칙

멱등성 정의:

- 같은 `period_type + period_key`로 배치를 여러 번 실행해도 최종 공개 결과가 동일해야 한다.

적용 규칙:

1. 입력 고정
  - 집계 입력은 `product_metrics` 원장만 사용한다.
  - 같은 period 실행 시 동일한 기간 규칙(KST/ISO)으로 동일 입력 집합을 구성한다.
2. 계산 고정
  - Top 100 선정/정렬/rank 부여 규칙을 실행마다 동일하게 적용한다.
  - 배치 내 증분 누적(INCR) 대신 "원장 재계산 결과"를 기준으로 반영한다.
3. 쓰기 고정 (Option A)
  - staging에 해당 period 결과를 완성한 후 검증 성공 시에만 active switch한다.
  - 실패 실행은 active에 반영하지 않으며 이전 스냅을 유지한다.
4. 제약 고정
  - `UNIQUE(period_key, product_id)`를 유지하여 같은 period 중복 행 생성을 차단한다.

검증 규칙:

- 동일 period 재실행 통합 테스트에서 `row 수`, `rank`, `score`가 실행 간 동일해야 한다.
- 중간 실패 후 재실행 테스트에서 실패 실행 결과가 active에 노출되지 않아야 한다.

---

## 5. API 계약

대상: `GET /api/v1/rankings`

계약 방향:

- 기존 `date=yyyyMMdd&page&size`는 그대로 지원(일간 해석)
- 기간 확장 파라미터 추가(예: `period` + `periodKey`)
- 응답은 공통 포맷 유지, `total` 포함

TOP 100 클램프:

- `total = min(집계건수, 100)`
- 대고객 랭킹 API: 요청 범위가 `total` 초과면 응답은 **빈 목록 + total 유지**, 서버 로그/메트릭에서는 "page 범위 초과"로 기록
- 내부/관리용 API(필요 시 별도 경로): 동일 조건에서 **400 BAD_REQUEST**로 명시적인 계약 위반 처리

---

## 6. Spring Batch 모델 선택

- 본 집계 Step: Chunk (`Reader -> Processor -> Writer`)
- 전/후처리(정리, 검증 마킹, 포인터 갱신): Tasklet 가능

혼합 권장 시나리오:

1. Tasklet: 대상 period staging 정리
2. Chunk: 집계 결과 적재
3. Tasklet: 검증 후 active switch

근거:

- QnA Q2에서 Chunk/Tasklet 혼합 사용 가능성을 확인했고,
- `10-quest`가 대량 `product_metrics` 처리와 MV 적재를 요구한다.

---

## 7. 모니터링/알림 우선순위

QnA Q6 확정:

- P1: job failure count
  - warning: 1회 실패
  - critical: 연속 3회 실패
- P2: stale snapshot age
  - warning: 주기 2배 초과
  - critical: 주기 3배 초과
- P3: last successful time
  - warning: 24시간 초과
  - critical: 48시간 초과

---

## 8. 테스트 전략(최소 세트)

QnA Q8 확정:

- Unit 4
- Integration 3
- E2E 3

필수 커버:

1. ISO 연초 경계 week key
2. 동일 period 재실행 멱등
3. 실패 시 half-written 비노출
4. TOP100 초과 page/size 클램프

---

## 9. 리스크와 완화


| 리스크                                | 완화                                                                                      |
| ---------------------------------- | --------------------------------------------------------------------------------------- |
| 주차 계산 불일치                          | ISO/KST 규칙을 도메인 유틸 + 단위테스트로 고정                                                          |
| Option A 스위칭 레이스(조회가 구/신 버전 혼합 조회) | active 포인터 스위칭을 원자적으로 처리하고, 조회는 active 버전만 읽도록 강제                                       |
| 동일 period 동시 실행 충돌                 | period 단위 배치 락(분산락/DB락) 적용, 중복 실행 시 후행 잡 중단                                             |
| 재실행 시 중복/왜곡                        | `멱등성 보장 규칙` 적용(원장 재계산, `UNIQUE(period_key, product_id)`, Option A switch, 재실행 통합테스트 고정) |
| TOP100 경계 오프바이원(100위 누락/중복)        | `(page,size,total=100)` 경계 단위테스트 + E2E 클램프 테스트 고정                                       |
| 집계 실패 노출                           | active 이전 스냅 유지                                                                         |
| API 계약 파손                          | 기존 `date` 하위호환 유지 + 점진 전환(새 파라미터 optional/feature flag) + 하위호환 E2E 고정                   |
| 모니터링 왜곡(실패/노후 상태 탐지 누락)            | P1/P2/P3 메트릭 분리 수집 및 임계치 알림 고정                                                          |
| 점수 규칙 변경 이력 부재로 설명 불가              | MV 버전/스코어 규칙 버전 필드 관리, 배포 노트에 변경 이력 기록                                                  |


### 9.1 동일 period 동시 실행 충돌 완화 규칙

- 락 단위: `period_type + period_key` (예: `WEEKLY:2026W15`)
- 실행 시작 시 period 락을 먼저 획득하고, 락 획득 실패 실행은 즉시 `SKIP/중단` 처리
- `active` 포인터 스위칭은 락 보유 실행만 수행 가능
- 실행 로그/메트릭에 `run_id`, `period_key`, `lock_owner`를 남겨 사후 추적 가능하게 유지
- 알림은 "동일 period 중복 실행 감지" 이벤트를 warning 이상으로 분리 발행

