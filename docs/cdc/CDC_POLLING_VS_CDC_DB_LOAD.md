# Polling vs CDC DB 부하 비교 (실측)

## 실험 배경 (왜 이 실험을 했는가)

- 현재 프로젝트는 메인 경로로 Polling 기반 Outbox 릴레이를 사용하고, CDC(Connect+Debezium)는 확장 경로로 검토하고 있다.
- 설계 논의에서 "Polling이 실제로 DB에 얼마나 더 부담을 주는지"를 정량 수치로 확인해야, 운영 선택(메인 경로 유지 vs CDC 확대)에 근거를 제시할 수 있다.
- (“Polling이 더 무겁다”)이 아니라 **측정값 기반 의사결정**이 필요해 동일 조건 반복 실험을 수행했다.

## 결론

- 동일 30초 구간에서 **Polling 방식이 CDC 방식보다 DB 질의 부하가 훨씬 큼**.
- 10회 반복 측정 평균 기준 `Questions`는 약 **49.4배**, `Com_select`는 약 **145.7배** Polling이 높았다.

## 측정 조건

- 환경:
  - MySQL: `docker/cdc/docker-compose.connect.yml`의 `mysql-cdc`
  - Kafka Connect: Debezium MySQL Connector (`loopers-mysql-cdc`)
- 공통:
  - DB: `loopers`
  - 테이블: `outbox_event`(1,000건 INIT 적재), `product_metrics`
  - 지표: MySQL `SHOW GLOBAL STATUS`의 `Questions`, `Com_select`
  - 측정 시간: 각 시나리오 30초

## 동일 조건 검증

- 두 방식 모두 **같은 MySQL 인스턴스(`mysql-cdc`)**, **같은 DB/테이블**, **같은 관측 지표**, **같은 측정 시간(30초)**, **같은 반복 횟수(10회)**로 측정했다.
- 차이는 오직 처리 방식이다.
  - Polling: 0.1초 주기 조회를 의도적으로 발생시킴
  - CDC: Connector RUNNING 상태에서 Polling 조회 없이 대기
- 즉, 결과 차이는 환경 차이가 아니라 **조회 방식 자체의 차이**에서 발생한 값이다.

## 시나리오

1. **Polling**
   - 0.1초 주기로 아래 조회를 반복 실행
   - `SELECT id,topic,event_type FROM loopers.outbox_event WHERE status='INIT' ORDER BY id LIMIT 100;`
2. **CDC idle**
   - Debezium Connector RUNNING 상태에서 애플리케이션 Polling 없이 30초 대기

## 실측 결과 (10회 반복)

| 시나리오 | Questions delta 평균 (최소~최대) | Com_select delta 평균 (최소~최대) |
|---|---:|---:|
| Polling | 445.0 (344~500) | 291.4 (224~328) |
| CDC idle | 9.0 (9~9) | 2.0 (2~2) |

- 비교:
  - `Questions`: `445.0 / 9.0 = 49.4x`
  - `Com_select`: `291.4 / 2.0 = 145.7x`

## 시각화

### 1) 평균 비교 표 (정규화)

| 지표 | Polling 평균 | CDC 평균 | Polling/CDC 배수 |
|---|---:|---:|---:|
| Questions delta | 445.0 | 9.0 | 49.4x |
| Com_select delta | 291.4 | 2.0 | 145.7x |

### 1-1) 1~10회 측정 Raw 결과

| 회차 | Polling Questions | Polling Com_select | CDC Questions | CDC Com_select |
|---:|---:|---:|---:|---:|
| 1 | 362 | 236 | 9 | 2 |
| 2 | 449 | 294 | 9 | 2 |
| 3 | 484 | 318 | 9 | 2 |
| 4 | 488 | 320 | 9 | 2 |
| 5 | 482 | 316 | 9 | 2 |
| 6 | 488 | 320 | 9 | 2 |
| 7 | 500 | 328 | 9 | 2 |
| 8 | 440 | 288 | 9 | 2 |
| 9 | 344 | 224 | 9 | 2 |
| 10 | 413 | 270 | 9 | 2 |

### 2) 막대 그래프 (텍스트)

단위: `█` 1개 = 대략 10 delta

`Questions`

- Polling: `████████████████████████████████████████████` (445)
- CDC    : `█` (9)

`Com_select`

- Polling: `█████████████████████████████` (291.4)
- CDC    : `█` (2)

## 근거 명령 (실행 로그 기준)

- 10회 raw 결과(tsv):
  - `/tmp/cdc_polling_10runs_result.tsv`
- 요약 출력:
  - `poll_q: avg=445.00, min=344, max=500`
  - `poll_s: avg=291.40, min=224, max=328`
  - `cdc_q: avg=9.00, min=9, max=9`
  - `cdc_s: avg=2.00, min=2, max=2`
  - `ratio_questions_avg=49.44`
  - `ratio_com_select_avg=145.70`

## 해석

- Polling은 데이터 유무와 무관하게 주기 조회가 발생하므로 DB에 지속 질의 부하를 만든다.
- CDC(Connect+Debezium)는 binlog 기반 추적이므로 유휴 구간의 DB 질의가 매우 작다.
- 따라서 **유휴/저변화 구간이 길수록 CDC가 DB 친화적**이다.

## 원인 분석 (왜 이런 결과가 나왔는가)

- **핵심 원인 1 - Pull vs Event-driven**
  - Polling은 일정 주기마다 DB를 직접 조회(SELECT)하므로 이벤트가 없어도 질의가 누적된다.
  - CDC는 DB binlog를 읽어 변경분만 전달하므로, 유휴 구간에서는 추가 SELECT가 거의 없다.
- **핵심 원인 2 - 조회 빈도**
  - Polling 시나리오는 0.1초 주기(초당 약 10회)라 30초 동안 반복 호출이 누적되어 `Questions`/`Com_select`를 크게 올린다.
  - 근거: Polling 평균 `Questions=445.0`, `Com_select=291.4`.
- **핵심 원인 3 - 측정 지표 특성**
  - `Com_select`는 SELECT 실행량을 직접 반영하므로 Polling 영향이 크게 나타난다.
  - CDC idle에서는 SELECT가 거의 없어 `Com_select=2.0` 수준으로 수렴했다.

## 주의사항

- 본 수치는 로컬 단일 노드/단일 실행 기준이다.
- 프로덕션 적용 전에는 동일 방법으로
  - Polling 주기(`outbox relay fixed-delay`)
  - 처리 배치 크기
  - 실제 이벤트 유입률
  을 반영해 재측정하는 것을 권장한다.
