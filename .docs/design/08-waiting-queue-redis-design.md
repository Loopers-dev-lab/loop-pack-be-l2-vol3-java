# Redis 기반 주문 대기열 설계 (Round 8)

> 목표: 블랙 프라이데이 등 **피크 트래픽**에서 하류(DB·PG)를 보호하면서, **진입 순서가 보장되는 공정한 대기 경험**과 **실시간 순번·예상 대기 시간**을 제공한다.  
> 범위: (1) Redis **Sorted Set** 기반 대기열, (2) 스케줄러 기반 **입장 토큰** 발급·TTL, (3) **Polling** 기반 순번 조회, (4) 주문 API(`POST /api/v1/orders`) 앞단 **관문**으로 토큰 검증.  
> 주문 확정 이후 이벤트·Kafka 파이프라인은 `**.docs/design/07-event-driven-architecture-and-kafka-pipeline.md`** 와 동일하게 유지한다.

**관련 문서**: [00-ubiquitous-language.md](./00-ubiquitous-language.md), [05-transaction-query.md](./05-transaction-query.md), [06-payment-implementation-plan.md](./06-payment-implementation-plan.md), [07-event-driven-architecture-and-kafka-pipeline.md](./07-event-driven-architecture-and-kafka-pipeline.md)  
**로드맵·시퀀스**: [08-waiting-queue-implementation-roadmap.md](../Implementation/08-waiting-queue-implementation-roadmap.md)  
**리스크·테스트 매핑**: [08-waiting-queue-risk-test-cases.md](./08-waiting-queue-risk-test-cases.md)

**구현 정본(`commerce-api`)**: `QueueFacade`의 `eventId`는 기본값 `default`(`queue.scheduler.event-id`). 예상 대기 TPS는 **`queue.position.throughput-tps`**(YAML, Redis 메타 키 아님). 정원은 **`queue.join.max-waiting`** + Lua(`addIfAbsentWithinCapacity`)로 **409(CONFLICT)**. 순번 API는 **`QueuePositionRateLimitFilter`** + `queue:ratelimit:position:{loginId}`. 입장 토큰은 **`OrderFacade.placeOrder` 초입**에서 `consumeIfTokenMatches`(Lua)로 **주문 로직 전에 일회 소비**(성공·실패와 무관). 스케줄러 토큰 저장 직전 지터는 코드상 **0~300ms**(`ThreadLocalRandom`, 상한 301ms 미포함).

---

### 설계의 목적: 왜 Redis 기반 대기열인가?

- **하류 보호**: 피크 시 주문 요청이 DB 커넥션·스레드 풀·PG를 압도하면 타임아웃·전면 장애로 이어진다. 대기열은 요청을 상류에서 **보관**하고 하류가 감당 가능한 속도로만 **방출**해 **Back-pressure**를 만든다.
- **공정성·가시성**: 단순 Rate Limit(429)만 쓰면 재시도 폭풍·이탈·순서 공정성 부재가 생긴다. **Sorted Set**으로 **진입 순서(FIFO)** 를 보장하고, **순번·예상 대기 시간**을 Polling으로 제공한다.
- **Kafka만 쓰는 패턴과의 차이**: 버퍼만 두면 사용자 화면에서 **순번·입장 시점**을 알기 어렵다. 본 설계는 Redis ZSET + 입장 토큰으로 **실시간 UX** 를 목표로 한다.
- **Rate Limit과의 관계**: 비정상 요청은 먼저 거르고, 정상 유저만 대기열에 진입시키는 **상호 보완**을 전제로 한다.

### 주요 아키텍처: 데이터 구조와 흐름


| 구분        | 내용                                                                                                                                                                                   |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **데이터**   | `queue:waiting:{eventId}` **ZSET** — score=진입 시각(epoch ms), member=`userId`. `queue:entry:{userId}` **문자열** — 입장 토큰 + TTL. 선택: `queue:ratelimit:position:{loginId}`(순번 API RPS). TPS·정원은 **애플리케이션 설정**(`queue.position.throughput-tps`, `queue.join.max-waiting`). |
| **클라이언트** | `POST /api/v1/queue/enter` → `GET /api/v1/queue/position`(또는 **`GET /api/v1/queue/position/stream`** SSE) → 토큰 수신 후 `POST /api/v1/orders` + `X-Entry-Token`. |
| **백그라운드** | 스케줄러가 `ZPOPMIN`(`popOldest`)으로 N명/틱 방출 후 토큰 `SET`+`EX`(pop과 SET은 **별 연산**). 멀티 인스턴스는 **분산 락**(`queue.scheduler.lock-key`). |
| **레이어**   | interfaces → `QueueFacade` → domain 규칙 + `RedisWaitingQueueRepository`. 주문 트랜잭션과 분리하되, 주문 직전 **토큰 검증**만 수행.                                                                          |


### 핵심 로직: 진입·상태·토큰


| 프로세스         | 정의                                                                                                                                                       |
| ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **진입**       | 로그인 유저(`X-Loopers-LoginId` → `userId`). ZSET에 **최초 진입만** 유효하면 `ZADD … NX` 또는 Lua로 score 고정. 이미 대기 중이면 기존 순번 유지 등 **정책 명시** 필요.                           |
| **순번·입장 가능** | `ZRANK`로 순번; 0이면 입장 가능. 응답에 예상 대기 초·입장 시 **토큰** 포함.                                                                                                      |
| **토큰 발급·만료** | 스케줄러가 방출 시 `SET queue:entry:{userId} {token} EX {ttl}`. 발급 루프에서 **0~300ms** 지연으로 Redis 쓰기 스파이크를 완화(문서의 0~2s “활성화 지연”과는 별개·축소 구현). TTL 만료 시 재진입 정책은 §9 참고. |
| **주문 관문**    | `queue.order.require-entry-token=true`일 때 `OrderEntryTokenGate` → **`consumeIfTokenMatches`(Lua: 일치 시 DEL)** 를 **`placeOrder` 본문 전**에 수행. 주문 성공 후 별도 DEL 없음. |
| **용량 초과**    | `queue.join.max-waiting` 초과 시 신규 진입 **409(CONFLICT)** — `queue:max-size` Redis 키는 사용하지 않음. |


### 성능·동시성 핵심 포인트

- **스케줄러**: 틱당 N을 작게·틱 주기를 짧게 해 **Thundering Herd** 완화; 필요 시 토큰 활성화 Jitter는 Nice-to-have.
- **분산 환경**: 스케줄러 **중복 실행 방지** — Redis `SETNX`+TTL 등 **분산 락**.
- **처리량 상한**: DB 풀·평균 주문 지연으로 이론 TPS 산정 후 안전 계수(예: 70%) — 로드맵 표 참고.
- **Redis 장애 시**: **Fallback 큐(Kafka) 기본 전략**을 사용하고, Kill switch로 전면 차단/우회 전환만 허용한다.

---

## 0. 문제 정의


| 현상                           | 결과                            |
| ---------------------------- | ----------------------------- |
| 초당 주문 요청이 DB 커넥션·스레드 풀 한계 초과 | 타임아웃·전면 장애                    |
| 단순 Rate Limit(429)만 적용       | 재시도 폭풍, 이탈, 공정성 부재            |
| Kafka 버퍼만 사용 (R7 쿠폰 패턴)      | 유저가 화면에서 **순번·입장 시점**을 알기 어려움 |


**대기열(Queuing)** 은 상류 요청을 **보관**하고, 하류가 감당 가능한 속도로만 **방출**하여 **Back-pressure**를 구현한다. Rate Limiting과 **상호 보완** 가능(예: 비정상 요청은 먼저 거르고, 정상 유저만 대기열 진입).

---

## 1. 아키텍처 원칙 (본 프로젝트 규칙 준수)

### 1.1 레이어 배치


| 레이어                | 책임                                                                                          | 예시                                                       |
| ------------------ | ------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| **interfaces**     | `QueueV1Controller`, DTO, `QueuePositionRateLimitFilter`(순번 경로 RPS) | `POST /api/v1/queue/enter`, `GET /api/v1/queue/position`, `GET /api/v1/queue/position/stream` |
| **application**    | `QueueFacade` — 진입·순번 조회 유스케이스 조율, 트랜잭션 경계는 **주문과 분리** (대기열은 주로 Redis IO)                   |                                                          |
| **domain**         | `WaitingQueueService`(또는 동등) — 진입 정책, 순번·예상시간 계산에 필요한 **순수 규칙**; Repository 인터페이스(포트)       | Redis 구현 세부는 두지 않음                                       |
| **infrastructure** | `RedisWaitingQueueRepository` — Spring Data Redis / `StringRedisTemplate` 등으로 ZSET·문자열 키 조작 | `modules/redis` 설정 재사용                                   |


- Controller는 Facade만 호출한다.  
- Facade에 **주문 도메인 비즈니스 규칙**(재고·쿠폰 등)을 넣지 않는다.  
- **주문 생성** 트랜잭션(`OrderFacade.placeOrder`)과 대기열은 별개이나, **주문 진입 전**에 토큰 검증을 한 번 수행한다.

### 1.2 인증·식별

- 대기열 진입·순번 조회는 **로그인 유저** 기준으로 설계한다 (`X-Loopers-LoginId` → `UserFacade`로 `userId` 조회).  
- **member = 내부 `userId`(Long 문자열)** 또는 프로젝트에서 합의한 단일 식별자로 통일한다. `loginId` 문자열을 ZSET member로 쓸지, `userId`로 쓸지 **한 가지로 고정**하고 ubiquitous language와 맞춘다.
- 동일 유저의 재진입은 **에러 대신 멱등 처리**(기존 순번 반환)로 고정한다.
- 비로그인 트래픽을 향후 열 경우, 대기열 진입 전단에서 `ip + ua + deviceId` 기반 rate limiting/challenge를 별도로 둔다(현 라운드 구현 범위는 로그인 유저만).

### 1.3 주문 API와의 연결

- 기존: `POST /api/v1/orders` + `X-Loopers-LoginId`.  
- 추가: **입장 토큰** — 예) 헤더 `X-Entry-Token: {token}` (또는 동일 의미의 스펙).  
- 흐름: 스케줄러가 방출 후 `position` 응답에 **토큰 포함**(또는 별도 조회) → 클라이언트가 `POST /api/v1/orders` 시 동일 토큰 전달 → **`placeOrder` 초입**에서 Lua로 **일치 시 삭제(일회용)**.  
- 토큰 검증 실패 시: `400` 또는 `403`/`401` — 프로젝트 `ErrorType`과 HTTP 매핑에 맞춰 정의.

---

## 2. Redis 데이터 모델

### 2.1 Sorted Set — 대기열


| 키(예시)                     | 타입   | score           | member   | 용도                   |
| ------------------------- | ---- | --------------- | -------- | -------------------- |
| `queue:waiting:{eventId}` | ZSET | 진입 시각(epoch ms) | `userId` | 선입선출 순서, `ZRANK`로 순번 |


- **중복 진입 방지**: 동일 member에 대해 `ZADD`는 덮어쓰기가 아니라 score 갱신 여부를 정책으로 정한다. 일반적으로 **최초 진입만 유효**면 `NX` 옵션(또는 Lua)으로 첫 진입 시각 고정.  
- **행사 ID**: 단일 글로벌 대기열이면 `eventId` 생략 가능; 행사별로 분리할 경우 키에 포함.

### 2.2 입장 토큰 — 문자열 + TTL


| 키(예시)                  | 값                 | TTL                       |
| ---------------------- | ----------------- | ------------------------- |
| `queue:entry:{userId}` | 난수 토큰(또는 서명된 문자열) | 예: 300초(5분) — **운영에서 조정** |


- 스케줄러가 대기열에서 꺼낸 유저에게 `SET` + `EX`.  
- 주문 시도 시 **검증과 동시에 DEL**(Lua). 주문 본문 실패 시에도 토큰은 이미 소비된 상태(재발급은 재대기·정책 과제 — §9).  
- TTL 만료 시 재진입은 클라이언트가 `enter`를 다시 호출(정원·멱등 정책은 `addIfAbsentWithinCapacity`와 동일).
- 틱마다 `expiredSinceLastTick` 등으로 배치를 보정하는 로직은 **현재 구현에 없음**(고정 `max-batch-size`만 사용).

### 2.3 보조 키·설정

- **처리량 파라미터**: **`queue.position.throughput-tps`** (YAML) — `QueuePositionEstimator`·예상 대기 초 계산. Redis `queue:throughput:tps` 키는 사용하지 않음.  
- **대기열 최대 길이**: **`queue.join.max-waiting`** — Lua로 `ZCARD`와 비교, 초과 시 신규 진입 거절(**409**).

---

## 3. API 설계 (고객)


| 메서드    | 경로                       | 설명                                                   |
| ------ | ------------------------ | ---------------------------------------------------- |
| `POST` | `/api/v1/queue/enter`    | 대기열 진입. 이미 대기 중이면 기존 순번 유지 또는 정책대로                   |
| `GET`  | `/api/v1/queue/position` | 현재 순번(0이면 입장 가능), 전체 대기 인원, **예상 대기 초**, 입장 시 **토큰** |


- 응답 래퍼는 기존 `ApiResponse` 유지.  
- **예상 대기 시간**: `ceil(position / max(throughputTps, ε)) + 1` (Jitter 평균 1초 반영) — 상세 공식은 로드맵 문서.  
- Polling 힌트는 `**suggestedPollIntervalMs` + `Retry-After` 둘 다 제공**으로 고정한다.
- 동적 폴링 간격(기본값):
  - `position <= 100` → `1000ms`
  - `101 ~ 1000` → `3000ms`
  - `1001 ~ 10000` → `5000ms`
  - `> 10000` → `10000ms`
- SSE 루프는 `suggestedPollIntervalMs`만큼 sleep(최소 1초)한다.

---

## 4. 스케줄러

- **역할**: `ZPOPMIN`(`popOldest`)으로 **N명/틱** 방출, 이후 애플리케이션에서 토큰 `SET`+`EX`.  
- **주기·배치**: `queue.scheduler.tick-ms`(기본 100ms), `max-batch-size`(기본 18) — **산정은 로드맵 §3·`application.yml`**.  
- **Thundering Herd**: 짧은 틱 + 소배치 + 토큰 저장 전 **0~300ms** 지연.  
- **중복 실행 방지**: **`queue.scheduler.lock-key`** 에 `SET NX EX`(분산 락). 락 실패 시 해당 틱은 방출 생략.
- **Heartbeat**: **`queue.scheduler.heartbeat-key`** 를 틱 성공 시 갱신(TTL `heartbeat-ttl-seconds`). 문서상 “30초 미갱신·락 10회 연속 알림”은 **운영 알림 연동은 §9에 남음**(코드는 키 갱신만).
- 배치 보정(`effectiveBatch`)은 **미구현**; `max-batch-size` 고정.

---

## 5. 비기능·운영


| 항목       | 방향                                                                        |
| -------- | ------------------------------------------------------------------------- |
| Redis 장애 | **대기열 진입**만 `queue.fallback.enabled` 시 Kafka 비동기 접수(`commerce-api` 프로듀서·컨슈머). 순번/토큰 조회는 fallback 없음(저장소 오류 시 500). |
| 모니터링     | Micrometer: `loopers.queue.*` 카운터 등(백엔드 실패, Kafka 발행/복구/DLT 등). §9의 운영 알림·대시보드는 별도. |
| 테스트      | `QueueQuestVerificationIntegrationTest`, E2E·인프라 테스트 참고 — [08-waiting-queue-risk-test-cases.md](./08-waiting-queue-risk-test-cases.md) |


### 5.1 Kafka — 대기열 **진입(join) fallback** (구현 기준)

`POST /api/v1/queue/enter` 가 Redis에 쓰지 못할 때만 사용한다. **주문(Place order)용 별도 토픽·`commerce-streamer` 소비**는 본 라운드 범위가 아니다.

- **애플리케이션**: `commerce-api`
- **토픽**: `queue.fallback.topic-name` → 기본 **`queue-join-fallback`**
- **컨슈머 그룹**: `queue.fallback.consumer-group` → 기본 **`loopers-queue-join-fallback-consumer`**
- **재시도**: `@RetryableTopic`(백오프 5s·최대 지연 30s 등) + `@DltHandler`
- **복구**: 메시지 수신 후 `WaitingQueueService.joinQueueFromRecovery` → `addIfAbsentWithinCapacity` + 순번 조회
- **프로파일**: `local`/`test` 에서는 보통 `queue.fallback.enabled: false` — [application.yml](../../apps/commerce-api/src/main/resources/application.yml) 참고

---

## 6. 구현 순서 (레포 반영 완료)

로드맵 [§5](../Implementation/08-waiting-queue-implementation-roadmap.md#5-구현-파이프라인-단계) 권장 순서(1→3→4→2→5)에 따라 `commerce-api`에 반영됨.

---

## 7. 체크리스트


| 과제 Step          | 상태 | 본 설계 섹션                     |
| ---------------- | --- | --------------------------- |
| Step 1 — 대기열     | 완료 | §2.1, §3 `enter`/`position` |
| Step 2 — 토큰·스케줄러 | 완료 | §2.2, §4, §1.3 주문 연동        |
| Step 3 — 실시간 순번  | 완료 | §3, SSE, Rate limit, 예상 대기   |


**검증**: [08-waiting-queue-risk-test-cases.md](./08-waiting-queue-risk-test-cases.md) 및 `QueueQuestVerificationIntegrationTest` 등.

---

## 8. 구현 상세 참조

### 8.1 Redis 자료구조·키·명령어


| 목적       | 키(예)                       | 타입        | 주요 명령                                                                             | 비고                    |
| -------- | -------------------------- | --------- | --------------------------------------------------------------------------------- | --------------------- |
| 대기열 순서   | `queue:waiting:{eventId}`  | ZSET      | 진입: Lua `addIfAbsentWithinCapacity`(정원·기존 멤버 처리), `ZRANK`, `ZCARD`, 방출: `ZPOPMIN` | 정원은 설정 `queue.join.max-waiting` |
| 입장 토큰    | `queue:entry:{userId}`     | String    | 스케줄러 `SET EX`, 주문: Lua `consumeIfTokenMatches`                                       | TTL `queue.scheduler.token-ttl-seconds` |
| 순번 API RL | `queue:ratelimit:position:{loginId}` | String | Lua `INCR`+`EXPIRE`                                                                | `queue.position.rate-limit.*` |
| 처리량·정원 | —                          | —         | **YAML** `queue.position.throughput-tps`, `queue.join.max-waiting`                      | Redis 메타 키 미사용     |
| 스케줄러 락·HB | `queue.scheduler.lock-key`, `heartbeat-key` | String | `SET NX EX`, `SET EX`                                                         | `application.yml` 기본값 참고 |


Redis 명령 조합·경합은 구현체(`RedisWaitingQueueRepository`, `RedisEntryTokenRepository`) 주석 및 테스트를 정본으로 한다.

### 8.2 비즈니스·예외 상황


| 상황              | 문서상 방향               | 구현 시 유의                                                                          |
| --------------- | -------------------- | -------------------------------------------------------------------------------- |
| 토큰 없음·불일치·이미 소비 | §1.3, `ErrorType` 매핑 | **`BAD_REQUEST`(400)** — `OrderEntryTokenService`; 주문 **전** Lua 소비로 **일회용** |
| TTL 만료 후 주문 시도  | §2.2                 | 재대기 또는 명시적 에러; 클라이언트는 position 재조회 유도                                            |
| 토큰 미활성(지연 구간)   | §2.2, §3             | 별도 “미활성” 플래그 없음; 만료·불일치는 400. 폴링 힌트는 `Retry-After`/`suggestedPollIntervalMs` |
| **토큰 탈취**       | 문서에 명시 없음            | 토큰을 **해당 userId 전용 키**에만 저장하고, 주문 시 **로그인 userId와 키 일치**로 이차 검증(동일 세션 가정 시에도 방어) |
| 토큰 미사용          | §2.2                 | TTL 만료만 Redis가 처리. 틱별 만료 보정 배치는 **미구현**.                                        |
| 대기열 최대 길이 초과    | §2.3                 | **409(CONFLICT)** — “대기열 정원이 찼습니다.” (`queue.join.max-waiting`)                        |
| Redis 다운        | §5                   | 전면 차단 vs 우회 — **정책 확정** 전 구현 보류 가능                                               |
| 동일 유저 중복 진입     | §2.1                 | `NX`/Lua로 최초 시각 고정 vs 갱신 — **한 가지로 고정**                                          |
| 스케줄러 중복 실행      | §4                   | 분산 락; 락 실패 시 스킵 또는 다음 틱                                                          |


### 8.3 검증(Validation) 단계 요약


| 단계    | 대상     | 검증 내용                                                                   |
| ----- | ------ | ----------------------------------------------------------------------- |
| 진입 전  | 인증     | `X-Loopers-LoginId` 등 — **로그인 유저** (§1.2)                               |
| 진입 시  | 식별자·중복 | `userId` 조회 성공; ZSET 정책(최초 진입만/갱신 금지)                                   |
| 진입 시  | 용량     | Lua로 `ZCARD`와 `queue.join.max-waiting` 비교 (0이면 검사 생략)                |
| 순번 조회 | 세션     | 동일 `userId`의 대기 행사·키 일관성                                                |
| 순번 조회 | 폴링 힌트  | `suggestedPollIntervalMs` + `Retry-After` 동시 제공, position 구간별 간격 적용     |
| 주문    | 입장권    | `X-Entry-Token` 존재; Redis `queue:entry:{userId}` 와 값 일치; 필요 시 TTL 잔여 확인 |
| 주문 후  | 토큰     | 이미 관문에서 소비됨(추가 DEL 없음). 실패 시에도 재사용 불가(재입장 필요).                        |


### 8.4 인터페이스: 파라미터·반환값

**대기열 API (고객)**


| API                          | 요청                                     | 응답 `data`에 포함할 항목(예)                                                                                                            |
| ---------------------------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `POST /api/v1/queue/enter`   | 헤더: 로그인 식별. Body는 행사별 확장 시 `eventId` 등 | 진입 결과, **현재 순번** 또는 재진입 안내                                                                                                      |
| `GET /api/v1/queue/position` | 동일 헤더                                  | **position**(또는 rank), 전체 대기 인원, **estimatedWaitSeconds**, 입장 가능 시 **entryToken**, `suggestedPollIntervalMs` + `Retry-After` 힌트 |


**주문 API 연동**


| 항목   | 내용                                                           |
| ---- | ------------------------------------------------------------ |
| 헤더   | 기존 `X-Loopers-LoginId` + `**X-Entry-Token: {token}`** (§1.3) |
| 실패 시 | 토큰 검증 실패 → 합의된 HTTP 상태·`ErrorType`                           |


**내부·타 서비스**

- `UserFacade`(또는 동등): `loginId` → `userId`.
- 스케줄러: Redis ZSET 방출 + `SET` 토큰; 설정에서 `throughputTps`·틱 주기·N/틱 읽기.

---

## 9. 남은 보완 항목 

아래는 **현재 코드/운영에 아직 반영되지 않았거나**, 문서·제품에서 **결정이 남은 항목**만 기술한다.  
(구현된 것: §3·§4에 따른 **폴링 힌트·`Retry-After`**, **SSE·동시 연결 상한**, **순번 API Rate Limit**, **스케줄러 분산 락·틱당 배치·지터(0~300ms)**, **ZRANK+ZCARD Lua**, **진입 Lua(`addIfAbsentWithinCapacity`)**, **입장 토큰 Lua 소비**, **join Redis 실패 시 Kafka fallback·일부 메트릭**, **heartbeat 키 갱신** 등 — 이 절에서는 제외.)

### 9.1 성능·병목


| 구간           | 남은 이슈                           | 방향                                               |
| ------------ | ------------------------------- | ------------------------------------------------ |
| **단일 ZSET**  | 피크 시 한 키에 쓰기·읽기 집중              | 행사/트래픽별 **샤딩 키** 검토, **프로파일링** 후 필요 시 분할         |
| **스케줄러**     | 락·배치·지터로 완화된 상태에서의 **추가 수평 분산** | **인스턴스별 파티션(키 분할)** 은 복잡도 대비 필요 시에만              |
| **Polling**  | 폴링 힌트·Rate limit은 적용됨           | **CDN/엣지 캐시**는 미도입(실시간 순번과 상충 — 필요 시 별도 합의)      |
| **토큰·순번 조회** | `position`·주문 검증 경로의 Redis QPS  | **파이프라인**, 검증 경로 축소, (선택) **짧은 로컬 캐시**는 일관성 검토 후 |


### 9.2 일관성·정합성


| 주제              | 남은 이슈                                       | 방향                                                                                |
| --------------- | ------------------------------------------- | --------------------------------------------------------------------------------- |
| **동일 시각 진입**    | score가 동일 ms면 Redis는 member **사전순**으로 부가 정렬 | 제품이 요구하면 **tie-break**(예: 서버 시퀀스·Lua) **명시·구현**                                   |
| **방출 ↔ 토큰**     | 현재는 `pop` 후 애플리케이션에서 `SET`; **한 원자 연산 아님**  | 프로세스 크래시 시 **pop만 되고 토큰 없음** 가능 — `**ZPOPMIN`(또는 범위)+`SET`을 Lua/MULTI로 묶는 패턴** 검토 |
| **토큰 vs 주문 결과** | 구현은 **주문 처리 전 토큰 소비**                       | 주문 **실패 시 재입장·재발급**을 허용할지 **비즈니스 규칙으로 확정**                                        |
| **멀티 AZ·복제**    | 읽기 복제본·페일오버 시 **지연**                        | Primary 기준 정책·모니터링을 **운영 아키텍처**와 맞출 것                                             |


### 9.3 운영·관측

- **알림**: `ZCARD` 임계치, **락 미획득 연속** 횟수, Redis **지연·에러율**(문서 §4 heartbeat만으로는 부족한 항목).
- **로깅**: 진입·방출·토큰 검증 실패 **원인 코드** 통일, `userId`는 해시·샘플링 등 **개인정보** 고려.
- **재시도**: 클라이언트·서버 재시도 시 `enter`/`position` **멱등 키** 또는 정책과의 정합성.
- **DR·백업**: 스냅샷/페일오버 시 대기열·토큰 **복구 범위** 합의.
- **부하·SLO**: 로드맵 시나리오와 **합의된 SLO**(예: P99 대기 표시 지연).

위는 신규 요구사항이 아니라 **추가 착수 시 검토 목록**으로 둔다.