# Week 10 - Spring Batch 기반 랭킹 확장 설계 QnA

---

## Q1. 주간/월간 랭킹을 실시간이 아닌 배치로 분리하려는 이유는 무엇인가요?

**[질문]**  
`10-subject`에서는 실시간 처리 vs 배치 처리의 트레이드오프를 강조하고, `10-quest`에서는 주간/월간 랭킹을 배치로 구현하라고 요구합니다.  
당신은 이 요구를 어떤 기준으로 해석했고, 왜 배치가 더 적합하다고 판단했나요?

**[답변]**

> 실시간으로 처리한다면 가장 좋겠지만 대규모 시스템의 경우에는 실시간 처리가 어렵습니다. 랭킹 배치 처리가 적합한 것 같습니다. 주간/월간 랭킹은 아직 주간, 월간의 기준을 어떤 단위로 끊어야 하는지 기준이 명확하게 잡히지 않았어

---

## Q2. Chunk vs Tasklet for weekly / monthly ranking steps

**[Question]**  
`10-subject` introduces Chunk-Oriented Processing and Tasklet. `10-quest` asks to read `product_metrics` in bulk and aggregate.  
For weekly TOP 100 and monthly TOP 100, will you implement each Step as Chunk, Tasklet, or a mix? What rule do you use to choose? If week/month boundaries are still unclear, what constraint or small experiment would you use to decide?

**[Answer]**  
> 일단 Chunk, Tasklet을 잘 알지 못하는 상황이니 둘다에 대한 개념 설명을 듣고 싶어. 그리고 기존에 있는 product_metrics 테이블을 통해 집계를 해야겠어. 일단 먼저 답변에 대한 설명을 해줘 그 이후 추가 질문을 할게

---

**[Mentor note]** (Chunk / Tasklet - concept only, not a single correct design)

- **Chunk-oriented step**: `ItemReader` reads rows (often with paging). `ItemProcessor` maps each row. Items buffer until chunk size N, then `ItemWriter` persists that batch in one transaction. Loop until the reader is done. Fits **large** scans of `product_metrics` with bounded memory.
- **Tasklet**: runs **one** piece of work per step (any code: one SQL, file copy, etc.). No Reader/Processor loop. Fits **one-shot** steps (e.g. truncate a staging table, run one aggregate statement).
- **Choosing**: many rows, repeated read-process-write → usually Chunk. a single procedural action or one SQL → often Tasklet. **Mix** is common: e.g. Tasklet to clear target period in MV, then Chunk to refill.

---

## Q3. MV tables and idempotent batch runs

**[Question]**  
`10-quest` names `mv_product_rank_weekly` and `mv_product_rank_monthly` (TOP 100). If the job fails halfway and you rerun with the **same** period parameters, how do you avoid duplicate rows or wrong ranks? What is your **primary key / period** design? Do you prefer truncate-and-load, delete-by-period-then-insert, or upsert?

**[Answer]**  
> 1. 멱등 필수 -> 기간 단위로 덮어쓰기  
> 2. 동시 실행 가능 -> 잠금 필수  
> 3, 4. (앞서 제시한 기준) 그대로 진행  
> 5. 부분 실패 시 반쯤 들어간 데이터는 노출되면 안 될 것 같음  
> 6. (적재 방식 축) 진행  
> 7. 빈 결과는 안 됨, 이전 스냅은 허용

---

## Q4. Ranking API: day / week / month contract

**[Question]**  
`10-quest` asks to extend `GET /api/v1/rankings` so callers can request daily, weekly, and monthly rankings (today you have `date=yyyyMMdd`, `size`, `page`).  
How will you represent **which period** the client is asking for (day vs week vs month) in query parameters? How do you identify a **week** and a **month** (e.g. `week=2026W15`, `month=202604`, or a single `period` enum + `periodKey`)?  
How do you keep **backward compatibility** for existing clients that only send `date`?  
What happens when `page` * `size` goes beyond **TOP 100** stored in MV?

**[Answer]**  
> 1. 주, 월 식별자는 월요일을 시작으로 잡아 타임존 날짜에서 잘라서 사용
> 2. 파라미터를 바꾸지 않았다면 그날의 일간 랭킹으로 해석되어 특정 시각의 랭킹을 보여줌
> 3. page/size는 total(최대 100) 기반으로 처리하고, 요청 범위가 total을 넘으면 빈 목록을 반환하는 방식(클램프)을 사용

---

## Q5. Batch failure handling and visibility rule

**[Question]**  
You chose: "half-written data must not be exposed" and "empty result is not allowed, previous snapshot is allowed."  
What is your concrete publish rule?
- Option A: write to staging table, then switch pointer/version only after success
- Option B: write to target table in one transaction for each period
- Option C: another approach

Also, what monitoring signal should trigger alert first: job failure count, last successful time, or stale snapshot age?

**[Answer]**  
> Option A를 선택.
> staging 테이블에 주/월 랭킹을 완성한 뒤 검증 성공 시에만 포인터/버전을 스위칭.
> 부분 적재 데이터는 노출하지 않고, 실패 시 이전 스냅을 유지.

---

## Q6. Monitoring priority for Option A

**[Question]**  
For the Option A flow you chose, which alert do you want as **P1** and why?
- job failure count
- last successful time
- stale snapshot age

Please pick one as P1, and set the others as P2/P3 with simple threshold ideas.

**[Answer]**  
> P1: job failure count
> - threshold: 1회 실패 시 warning, 연속 3회 실패 시 critical
> P2: stale snapshot age
> - threshold: 기대 배치 주기 대비 2배 초과 시 warning, 3배 초과 시 critical
> P3: last successful time
> - threshold: 마지막 성공이 24시간 초과 시 warning, 48시간 초과 시 critical

---

## Q7. Week/Month boundary contract and timezone

**[Question]**  
You said weekly/monthly keys will be derived from date format splitting.  
Please make the contract explicit for implementation and tests:
1) Which timezone is canonical? (e.g. Asia/Seoul)
2) What is the first day of week? (Monday/Sunday)
3) Which week rule? (ISO-8601 week-based year or custom)
4) If `date=YYYYMMDD` is at year boundary (e.g. 2026-01-01), which week key should it map to?

Please answer as concrete rules, not intentions.

**[Answer]**  
> 1) timezone: 기존 서비스 타임존 유지 (Asia/Seoul)
> 2) first day of week: Monday
> 3) week rule: ISO-8601 week-based year
> 4) year boundary: 연초 경계 날짜도 ISO week-year 규칙으로 계산 (예: 2026-01-01의 week key는 ISO 계산 결과를 따른다)

---

## Q8. Minimal test set for your contracts

**[Question]**  
Based on your decisions so far (Option A, TOP 100 clamp, ISO week rule, KST), propose a **minimal but sufficient** test set.
Please list test cases by level:
- unit test (domain/helper)
- integration test (batch + DB)
- API/E2E test

At least include:
1) year-boundary week key mapping (ISO)
2) rerun idempotency for same period
3) no half-written exposure on failure
4) page/size beyond TOP 100 returns clamped empty list with total

How many tests in each level, and why that is enough?

**[Answer]**  
> 단위(Unit) 4개, 통합(Integration) 3개, API/E2E 3개로 구성.
>
> [Unit - 4]
> 1) week key 계산: KST + ISO 기준으로 연초 경계(`2026-01-01`)가 기대 week key로 매핑되는지 검증
> 2) month key 계산: `yyyyMM` 파생 규칙 검증
> 3) TOP100 클램프 계산: `(page,size,total=100)`에서 범위 초과 시 empty 판정되는지 검증
> 4) period 파라미터 파싱/검증: day/week/month 입력 계약 검증
>
> [Integration - 3]
> 1) 같은 period로 배치 2회 실행 시 결과 동일(멱등): row 수/랭크/점수 동일 검증
> 2) Option A 실패 시 노출 보호: staging 적재 중 실패를 강제하고 active 포인터가 이전 스냅을 유지하는지 검증
> 3) 성공 시 스위칭: staging 완료 후 active 포인터가 새 버전으로 원자적으로 전환되는지 검증
>
> [API/E2E - 3]
> 1) 기존 `date`만 보낸 요청 하위호환: 일간 랭킹 정상 응답 검증
> 2) 주간/월간 요청 응답: period별로 total/rows/rank 형식이 계약대로 내려오는지 검증
> 3) page*size가 TOP100 초과: 빈 목록 + total(100) 반환 검증
>
> 왜 이 구성이 충분한가:
> - 규칙성 계산(KST/ISO/클램프)은 Unit에서 빠르게 고정
> - 실패/스위칭/멱등 같은 핵심 안정성은 Integration에서 보장
> - 사용자 계약(하위호환/응답 포맷/클램프 동작)은 E2E에서 최종 확인
> - 즉, 계산/저장/노출 3축을 각각 한 번씩 닫아 최소 개수로 핵심 리스크를 커버
