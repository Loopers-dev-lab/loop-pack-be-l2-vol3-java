# Round 10 랭킹 — 동작·시나리오 (day/week/month + MV)

> **이 문서가 하는 일**: Round 10에서 추가된 주간/월간 MV 조회를 포함해, 랭킹 API가 정상/예외에서 어떤 결과를 내는지 정리한다.  
> **근거 문서**: [10-batch-ranking-mv-design.md](./10-batch-ranking-mv-design.md), [10-batch-ranking-implementation-roadmap.md](../Implementation/10-batch-ranking-implementation-roadmap.md), [10-qna.md](../qna/10-qna.md), [09-ranking-user-scenarios.md](./09-ranking-user-scenarios.md), [09-ranking-risk-discovery.md](./09-ranking-risk-discovery.md).

---

## 0. Round 10에서 바뀐 핵심


| 항목       | Round 9                 | Round 10                        |
| -------- | ----------------------- | ------------------------------- |
| 조회 기간    | day(일간) 중심              | day + week + month              |
| 데이터 원천   | Redis ZSET 중심           | day=Redis, week/month=MV        |
| 주/월 안정성  | 해당 없음                   | 요청 시작 시 `MAX(version)` 1회 고정 조회 |
| 페이지 정책   | 범위 초과 시 빈 목록 + total 유지 | 동일(주/월은 TOP100 클램프 포함)          |
| 배치 실패 노출 | 해당 없음                   | Option A로 half-written 비노출      |


근거: 로드맵 3.5/3.6/3.7, Done 기준의 `half-written 비노출`, `TOP100`, `모니터링`.

---

## 1. 정상 시나리오 (Happy Path)


| ID  | 트리거                                                              | 호출/처리                                 | 관찰 가능한 결과                                                       |
| --- | ---------------------------------------------------------------- | ------------------------------------- | --------------------------------------------------------------- |
| H1  | `GET /api/v1/rankings?date=yyyyMMdd&page&size`                   | 일간 Redis 키 조회                         | HTTP 200, `dataSource=REDIS`(또는 스냅샷/fallback 정책 경로), 기존 하위호환 유지 |
| H2  | `GET /api/v1/rankings?period=WEEKLY&periodKey=2026W15&page&size` | 주간 MV에서 `MAX(version)` 결정 후 해당 버전만 조회 | HTTP 200, `dataSource=MV_WEEKLY`, `mvPublishVersion` 포함         |
| H3  | `GET /api/v1/rankings?period=MONTHLY&periodKey=202604&page&size` | 월간 MV 동일 규칙                           | HTTP 200, `dataSource=MV_MONTHLY`, `mvPublishVersion` 포함        |
| H4  | 주/월 조회 page 범위 내                                                 | `total=min(실데이터,100)` 기준 슬라이스         | content 정상, `totalElements<=100`                                |
| H5  | 주/월 조회 page 범위 초과                                                | total 대비 오프셋 초과                       | HTTP 200, `content=[]`, `totalElements` 유지                      |
| H6  | 배치 성공 (`rankingProductMvJob`)                                    | staging 검증 성공 후 publish               | 다음 조회부터 새 snapshot 반영                                           |
| H7  | 배치 실패                                                            | Option A에서 switch 미수행                 | 이전 정상 snapshot 계속 노출(반쯤 쓴 데이터 비노출)                              |


---

## 2. 예외·경계 시나리오

### 2.1 입력/계약 검증


| ID  | 조건                                                    | 결과              |
| --- | ----------------------------------------------------- | --------------- |
| E1  | `period`만 있고 `periodKey` 없음                           | 400 BAD_REQUEST |
| E2  | `periodKey`만 있고 `period` 없음                           | 400 BAD_REQUEST |
| E3  | `date`와 `period/periodKey` 동시 지정                      | 400 BAD_REQUEST |
| E4  | WEEKLY key 포맷 오류 (`20260406`) 또는 주차 범위 이탈 (`2026W00`) | 400 BAD_REQUEST |
| E5  | MONTHLY key 포맷 오류 (`2026-04`) 또는 월 범위 이탈 (`202613`)   | 400 BAD_REQUEST |


### 2.2 MV 조회 안정성


| ID  | 조건                                       | 결과                                  |
| --- | ---------------------------------------- | ----------------------------------- |
| E6  | periodKey에 MV row 없음 (`MAX(version)` 없음) | 200 + 빈 목록, `mvPublishVersion=null` |
| E7  | 동일 periodKey에 버전 혼재(v1, v2)              | 요청 시작 시 선택된 `MAX(version)`만 응답      |
| E8  | 조회 중 배치가 새 버전 publish                    | 이미 시작된 요청은 기존 선택 버전 기준으로 일관 응답      |


### 2.3 배치/운영 경계


| ID  | 조건                           | 결과                                                                           |
| --- | ---------------------------- | ---------------------------------------------------------------------------- |
| E9  | 동일 period 락 선점 상태에서 후행 실행    | period lock step에서 실패(중복 실행 방지)                                              |
| E10 | staging 검증 실패(rank 불연속/중복 등) | publish 실패, 기존 MV 유지                                                         |
| E11 | 배치 실패 반복                     | `batch.rank.job.failure.count` 증가(알람 연계 대상)                                  |
| E12 | 성공 배치 장시간 없음                 | `batch.rank.snapshot.stale.seconds`, `batch.rank.job.last.success.epoch`로 탐지 |


---

## 3. 사용자 관점 시나리오


| ID  | 사용자 행동                    | 기대 화면/응답                     |
| --- | ------------------------- | ---------------------------- |
| U1  | 기존 앱이 `date`만 보내 일간 랭킹 요청 | 이전과 동일하게 동작(회귀 없음)           |
| U2  | 주간 탭 진입 후 페이지 이동          | TOP100 내 정상 이동, 초과 페이지는 빈 목록 |
| U3  | 월간 탭에서 마지막 페이지 이후 요청      | 오류 대신 빈 목록 + total 유지        |
| U4  | 배치 실패 직후 랭킹 재조회           | 이전 정상 랭킹 유지(깨진 순위 노출 없음)     |
| U5  | 배치 재실행(동일 period) 후 재조회   | 결과 동일(멱등), 사용자 체감 변동 없음      |


---

## 4. 리스크 연결 (Round 9 → Round 10)

Round 9 리스크 중 Round 10에서도 중요한 항목을 재정리한다.


| Risk                   | Round 10 상태                 | 확인 포인트                            |
| ---------------------- | --------------------------- | --------------------------------- |
| R3 (Redis/DB 불일치)      | day 경로에서는 여전히 유효            | day 응답의 total/rows 불일치 가능성 문서화 유지 |
| R7 (오프셋 시프트)           | day 라이브 조회에 여전히 유효          | 주/월 MV는 요청 버전 고정으로 완화             |
| R1 (장애/빈 목록 구분)        | 주/월은 MV 조회로 상대적 완화, day는 유지 | dataSource/메트릭으로 운영 구분            |
| 신규 R10-1 (publish 원자성) | Option A로 대응                | 실패 시 active 유지 테스트 필수             |
| 신규 R10-2 (버전 혼재 조회)    | 요청당 `MAX(version)` 고정으로 대응  | 혼합 버전 E2E 필수                      |


---

## 5. 테스트 매핑 체크리스트 (로드맵 3.8 기준)


| 구분          | 시나리오                                                      |
| ----------- | --------------------------------------------------------- |
| Unit        | ISO 연초 week key, month key, TOP100 클램프, period 파라미터 검증    |
| Integration | 동일 period 재실행 멱등, 실패 시 active 유지, 성공 시 publish/switch     |
| E2E         | `date` 하위호환, week/month 응답 계약, TOP100 초과 페이지 빈 목록 + total |
| 추가 권장       | period lock 경합, 요청 단위 active version 고정                   |


---

## 6. 운영 확인 항목


| 항목  | 기준 (QnA)                                                    |
| --- | ----------------------------------------------------------- |
| P1  | `batch.rank.job.failure.count` — 1회 warning, 연속 3회 critical |
| P2  | `batch.rank.snapshot.stale.seconds` — 주기 2배/3배 임계           |
| P3  | `batch.rank.job.last.success.epoch` — 24h/48h 임계            |


---

## 7. 관련 구현/문서 링크

- 설계: [10-batch-ranking-mv-design.md](./10-batch-ranking-mv-design.md)
- 구현 순서: [10-batch-ranking-implementation-roadmap.md](../Implementation/10-batch-ranking-implementation-roadmap.md)
- 결정 로그: [10-qna.md](../qna/10-qna.md)
- 선행 시나리오: [09-ranking-user-scenarios.md](./09-ranking-user-scenarios.md)
- 선행 리스크: [09-ranking-risk-discovery.md](./09-ranking-risk-discovery.md)

