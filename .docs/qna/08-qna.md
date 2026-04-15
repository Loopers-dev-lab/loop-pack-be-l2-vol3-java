# Week 8 — Waiting Queue 설계 QnA

> 세션 재시작: 이전 QnA 기록은 사용하지 않고 처음부터 진행한다.  
> 참조 문서: `.docs/qna/08-subject.md`, `.docs/qna/08-quest.md`

---

## Q1. Redis가 죽었을 때, 우리 서비스는 어떻게 동작해야 하나?

**[질문]**
`08-subject.md`와 `08-quest.md`에는 Redis 장애 시 **Graceful Degradation**(전면 차단 vs 우회 vs Fallback 등)을 **사전에 정의**하라는 취지가 나와 있어요.

지금 프로젝트 기준으로, Redis가 사용 불능일 때 아래 각각은 **어떤 HTTP 응답·메시지·사용자 안내**가 되는 게 맞다고 보시나요?

- `POST /api/v1/queue/enter`
- `GET /api/v1/queue/position`
- `POST /orders` (입장 토큰이 필요한 경우)

그리고 그 선택이 **하류(DB/PG) 보호**와 **유저 경험(이탈/불만)** 중 무엇을 더 우선하는지, 본인 말로 한 단락만 적어주세요.

**[답변]**

공통: 저장소(Redis·DB) 예외 HTTP 매핑

`ApiControllerAdvice`에서 `DataAccessException`, `RedisConnectionFailureException`, `RedisSystemException`을 한곳에서 처리한다.

- **HTTP**: 500, `ErrorType.INTERNAL_ERROR`
- **본문 메시지**: `일시적으로 저장소에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.`
- **관측**: `loopers.queue.backend.failures`(tag `layer=api`) 카운터 증가

도메인에서 이미 `CoreException`으로 감싼 경우는 위보다 앞선 `handle(CoreException)` 경로로 간다.

---

### `POST /api/v1/queue/enter` (대기열 진입)

1. **Redis 복구 가능 예외**(`WaitingQueueService#isRecoverableQueueBackendFailure` — cause 체인에 `DataAccessException` / Redis 연결·시스템 예외):

- `queue.fallback.enabled: true` 이면 `QueueJoinFallbackPublisher`로 Kafka에 접수 이벤트 발행 후 **200**, `data`에 `asyncFallbackPending=true`, `fallbackRequestId` (비동기 접수). 복구 컨슈머가 `joinQueueFromRecovery`로 ZSET 반영, 실패 시 Kafka 재시도·DLT.
- **fallback 비활성**(로컬·테스트 등): `CoreException(INTERNAL_ERROR, "대기열을 일시적으로 사용할 수 없습니다.", cause)` → **500**, 위 도메인 문구.
- fallback 켠 상태인데 publisher 빈: `CoreException(INTERNAL_ERROR, "대기열 fallback publisher가 구성되지 않았습니다.")` → **500**.

1. **저장소 예외가 위 “복구 가능” 분기에 안 잡히면** 상위로 전파되어, 유형에 따라 공통 핸들러 또는 `handle(Throwable)`에 걸릴 수 있다.

---

### `GET /api/v1/queue/position` (순번 조회)

`joinQueue`와 달리 **Kafka fallback 분기는 없다.** Redis 조회(`findPositionSnapshot` 등) 중 저장소 예외가 나면 `ApiControllerAdvice`의 저장소 핸들러로 매핑된다 → **500** + 공통 메시지(위) + 메트릭. 즉 “빈 응답”이 아니라 **명시적 실패 응답**이다.

---

### `POST /orders` (입장 토큰 필요: `queue.order.require-entry-token: true`)

- **비즈니스 실패**(헤더 없음·불일치·만료로 스크립트가 미소비): `OrderEntryTokenService` → `CoreException(BAD_REQUEST)` — **400**, “X-Entry-Token 헤더가 필요합니다.” / “입장 토큰이 없거나 유효하지 않습니다.”
- **Redis 장애**로 Lua/`consumeIfTokenMatches`가 예외를 던지면: **BAD_REQUEST가 아니라** 저장소 예외로 `ApiControllerAdvice` 공통 경로 → **500** + 공통 메시지. 토큰 부재와 **HTTP·코드로 구분**된다.

`require-entry-token: false`이면 입장 토큰 단계는 생략되고, 주문 본문에서 나는 다른 Redis/DB 예외는 동일한 공통 매핑을 따를 수 있다.

---

### 그 외

- **스케줄러**(입장 배치·분산 락·하트비트): Redis 실패 시 틱 단위 예외·로그(클라이언트 HTTP와 별도). 별도 graceful 분기는 두지 않은 것이 현재 코드 기준이다.
- **앱 기동**: Spring Data Redis는 보통 **기동 직후가 아니라 명령 실행 시점**에 연결 실패가 드러난다.

---

### 하류 보호 vs 유저 경험 (한 단락)

**진입(`POST /api/v1/queue/enter`)만** fallback이 켜진 환경에서 Kafka 비동기 접수로 “접수 자체는 남기는” UX를 주고, **순번·토큰·주문**은 Redis 일관성 없이 성공으로 위장하지 않는다. 저장소 다운 시 **400으로 위장하지 않고 500 + 동일 안내 문구**로 막아, 토큰 없음과 인프라 장애를 구분한다. 우선순위는 **잘못된 허용·오판 방지(하류·정합성)** 에 가깝고, **진입 한정**으로만 **이탈 완화(접수 유지)** 를 택한 형태다.

---

## Q2. 스케줄러 “한 번에 N명” — N은 무엇을 기준으로 정할 건가요?

**[질문]**

`08-quest.md`에는 스케줄러가 주기적으로 대기열에서 **N명**을 꺼내 입장 토큰을 발급하고, **처리량 설계 기준**(DB 커넥션 풀, 평균 처리 시간 등)으로 **배치 크기를 산정**하라고 나와 있어요.

지금 떠올리는 **N(또는 틱당 입장 인원)** 은 어느 정도이고, 그 숫자를 **어떤 식으로** DB·PG·앱 스레드와 연결해서 설명할 수 있나요? (공식 하나를 쓰지 않아도 됩니다. “이 값을 넘기면 어떤 자원이 먼저 빨간불이 켜질지”만 짚어 주세요.)

**[답변]**

> (아래는 **문서·YAML에 맞춰 둔 설계 예시**를 정리한 것이다. **실측 운영값으로 C/L을 잡은 것은 아니다.**)

**[멘토 보충]**

- **문서 기준**: `08-quest.md`는 N의 구체 숫자를 정하지 않고, DB 커넥션 풀·평균 처리 시간 등으로 **배치 크기를 산정·근거화**하라고만 한다. 수치 예시는 `08-waiting-queue-implementation-roadmap.md` §3, `08-waiting-queue-redis-design.md` §4, `08-subject.md`에 있다.
- **왜 “예시에 맞춘 수치”로만 적었나**: 이 레포에는 **스테이징/프로덕션에서 재측정한 C·L·p95** 같은 **루퍼스 전용 운영 기준이 없고**, `application.yml`의 `queue.scheduler.`·`throughput-tps`가 **로드맵 §3 예시(50 / 0.2s / 70% / 175 / 100ms / 18)** 와 **동일하게** 맞춰져 있다. 그래서 QnA 답은 **“우리 서비스 실측”이 아니라 “문서·설정에 적힌 설계 예시”** 를 그대로 설명한 것이다. 실제 배포 전에는 **운영 DB 풀·주문 지연·PG·재고 락**을 보고 값을 치환하고, 로드맵에도 “실제 값은 운영 설정으로 치환”이라고 되어 있다.
- **예시 산정(로드맵 §3)**: 전제 `C=50`, `L=0.2s` → 이론 `TPS ≈ C/L = 250` → 안전 계수 `α=0.7` → 목표 **175 TPS**; 틱 `Δ=100ms`(초당 10틱) → **n = ceil(175/10) = 18명/틱**. 전제는 **“실제 값은 운영 DB·앱 설정으로 치환”**이며, “PG·재고 락이면 실제 병목이 더 낮을 수 있음 — **병목 재측정**”이 전제로 붙어 있다.
- **DB·PG·스레드와의 연결**: 스케줄러 틱 자체는 주로 **Redis**(락·pop·토큰 SET)이고, **N은 곧바로 DB 커넥션 N개를 쓰는 것이 아니다.** 다만 토큰을 받은 유저가 `POST /orders`로 몰리면 **동시 주문 요청**이 늘어나, 그때 **앱 스레드 → DB 커넥션 풀 대기·고갈 → PG/외부 타임아웃** 순으로 한계가 드러나기 쉽다(실제 순서는 부하 패턴에 따라 다름).
- **설정 반영**: `application.yml` — `queue.scheduler.tick-ms: 100`, `max-batch-size: 18`, `queue.position.throughput-tps: 175`로 **로드맵 예시와 맞춤**.

**[후속 질문: 병목을 한 가지로만 고른다면]**

위 보충에서 “실제 순서는 부하 패턴에 따라 다르다”고 했어요. **우리 서비스(또는 이 프로젝트 가정)** 에서 N을 너무 크게 잡았을 때, **가장 먼저** 빨간불이 켜질 자원을 하나만 꼽는다면 무엇인가요? (DB 커넥션 / PG / Redis / 앱 스레드 등 중에서, 한 줄 근거만.)

**[답변]**

> - **DB 커넥션 풀** — 이 프로젝트에서 주문(`OrderFacade` 등)은 **트랜잭션·재고·쿠폰·영속화**로 DB 점유가 길어지기 쉽고, N을 키우면 **동시에 `POST /orders`가 풀 슬롯을 빠르게 소모**해 대기·타임아웃이 먼저 두드러지기 때문이다. (입장 토큰용 Redis 조회·스케줄러용 Redis는 상대적으로 가볍고, PG는 결제 단계에서 별도로 두드러질 수 있어 “N만 키웠을 때 첫 빨간불”로는 풀이 앞선다고 보는 편이 자연스럽다.)

---

## Q3. 순번 조회는 Polling인데, 부하는 어떻게 감당할 건가요?

**[질문]**

`08-quest.md`는 순번·예상 대기 시간 API를 **Polling**으로 구현하고, 대기 인원에 따른 **Polling 부하**를 고려하라고 해요. `08-subject.md`에는 **SSE**도 Nice-To-Have로 나옵니다.

**클라이언트 입장**에서 “몇 초마다 한 번씩” 호출할지, 서버 입장에서 **초당 요청 수**를 어떻게 상한할지(또는 상한을 안 둘지)를 **지금 설계안**으로 말로만 짧게 적어 주세요. SSE로 바꾼다면 **무엇이** 달라지나요? (장단 한 줄씩이면 됩니다.)

**[답변]** (구현 기준)

> - **클라이언트(권장 폴링 간격)**
>   - `GET /api/v1/queue/position` 응답의 `suggestedPollIntervalMs` 와 헤더 `Retry-After`(초)는 `QueuePollHintPolicy`와 동일: 순번 **≤100 → 1초**, **101~1000 → 3초**, **1001~10000 → 5초**, **그 초과 → 10초**.
>   - 예상 대기 초는 `queue.position.throughput-tps`와 `QueuePositionEstimator`로 계산한다.
> - **서버(초당 요청 상한)**
>   - `queue.position.rate-limit`(중첩 설정)이 켜진 프로필에서는 `RedisQueuePositionRateLimiter` + `QueuePositionRateLimitFilter`가 `**X-Loopers-LoginId` 기준** 초당 N회(기본 5, 윈도 1초)를 넘기면 **429** + `Retry-After: 1` + `ApiResponse` `TOO_MANY_REQUESTS`. **local/test 프로필에서는 비활성화해 로컬·기존 테스트를 깨지 않게 둔다.
>   - Nginx 등 **프록시 IP 기준 제한**은 `docker/nginx-api-gateway.example.conf` 예시와 별개 레이어.
> - **SSE**
>   - `GET /api/v1/queue/position/stream` — `QueuePositionStreamService`가 `SseEmitter`(기본 5분)로 `getQueuePosition` 스냅샷을 반복 전송하고, 간격은 `max(1000ms, suggestedPollIntervalMs)`. 대기열 이탈 시 `not-in-queue` 후 종료.

**[후속 질문: 429를 받은 클라이언트]**

폴링 쪽에서 **429 + Retry-After** 를 내려줬다고 칠게요. 그다음 클라이언트가 **Retry-After보다 짧은 간격으로** 다시 `GET /api/v1/queue/position`을 치면, 서버 입장에서 그 요청을 **어떻게** 처리하는 게 설계와 일치할까요? (같은 429를 또 줄지, 다른 코드를 줄지, 한 줄로만.)

**[답변]**

> - **같은 429를 다시 주는 것이 설계와 맞다** — 레이트 리밋은 “권장 간격”을 어기면 **카운트가 쌓이거나 윈도 내 상한을 넘어** 계속 거부하는 쪽이고, `QueuePositionRateLimitFilter`는 초과 시 항상 **429 + `Retry-After: 1`** 로 동일하게 응답한다. **다른 HTTP 코드로 완화해 주지 않는다**(클라이언트는 `Retry-After`만큼 기다린 뒤 재시도하는 계약).

---

## Q4. 입장 토큰 TTL은 왜 그 길이인가요?

**[질문]**

`08-quest.md`는 입장 토큰에 **TTL**(예: 5분)을 두고, 시간 안에 쓰지 않으면 만료되게 하라고 해요.

**몇 분(또는 몇 초)** 으로 두었는지, 그리고 **그보다 짧게/길게** 잡았을 때 각각 어떤 **운영·UX 리스크**가 생길지 본인 말로 짧게 적어 주세요.

**[답변]**

> - **설정 값**: `queue.scheduler.token-ttl-seconds: 300` → **5분**(스케줄러가 Redis에 `SET`할 때의 입장 자격 TTL, `RedisEntryTokenRepository`).
> - **짧게 잡을 때(예: 1분)**
>   - **UX**: 결제·폼 입력이 길면 토큰이 만료되어 **다시 대기열 맨 뒤**(정책에 따라)로 가는 불만·이탈이 늘 수 있다.
>   - **운영**: 미사용 슬롯 회전은 빨라지지만, **재진입·재호출**이 늘어 대기열·API 부하가 잠깐 튈 수 있다.
> - **길게 잡을 때(예: 30분)**
>   - **UX**: 한 번 입장한 뒤 **느슨하게** 주문까지 이어갈 시간은 넉넉해진다.
>   - **운영·보안**: 발급됐지만 쓰이지 않은 “유효 토큰”이 길게 남아 **동시에 주문 플로우에 들어올 수 있는 세션(슬롯)** 이 오래 붙잡히는 효과가 있어, 피크 시 **하류 보호(의도한 동시 주문 상한)** 와 어긋나기 쉽다. 토큰 유출 시 **유효 창**도 길어진다.
> - **로드맵/설계 문구**: `08-waiting-queue-redis-design` 등에서는 **예: 300초(5분) — 운영에서 조정**으로 두어, **문서 예시와 YAML이 맞춰진 상태**이며 실제 행사에서는 트래픽·평균 결제 소요에 맞춰 조정하는 전제다.

---

## Q5. 스케줄러 틱·토큰 발급이 한꺼번에 몰리면 (Thundering Herd)

**[질문]**

`08-quest.md` Nice-To-Have에는 **Thundering Herd 완화**(발급 간격 분산 / Jitter)가 나와요.

스케줄러가 **고정 주기**(예: 100ms마다)로 돌고, 그때마다 **N명**에게 토큰을 발급한다면, **다음 단계**(클라이언트가 동시에 주문 API를 호출하는 것)에서 **Thundering Herd**가 생길 수 있다고 보시나요? 그렇다면 **코드/설정/운영** 중 어디에서 무엇을 조정하면 완화된다고 보시는지, 한 단락만 적어 주세요.

**[답변]**

> **생길 수 있다**고 본다 — 한 틱에서 최대 N명이 거의 동시에 입장 자격을 갖게 되면, 폴링 주기에 따라 `**POST /orders` 요청이 짧은 시간에 몰릴 수 있어 하류(DB·PG) 입장에서는 Thundering Herd에 가까운 스파이크가 된다.
>
> 완화는 **설정**으로 `queue.scheduler.tick-ms`를 짧게·`max-batch-size`를 작게 해 **방출을 더 잘게 쪼개는 것**, **코드**로는 현재 `EntrySchedulerService`가 유저마다 **0~300ms Jitter**를 두고 토큰을 Redis에 써서(주석: 동일 틱 Redis 스파이크 완화) **토큰이 실제로 “보이는” 시점을 틀어** 주문 호출 시각을 조금 흩뜨리는 것, **클라이언트/운영**으로는 `suggestedPollIntervalMs`·`Retry-After` 준수·순번 API **429 레이트 리밋**·필요 시 게이트웨이에서 **주문 API RPS·동시 연결 제한**으로 최종 상한을 거는 것이 맞다(로드맵에도 틱 세분화·Jitter·주문 API 상한을 함께 언급).

---

## Q6. Rate Limiting vs 대기열 — 언제 무엇을 쓰나요?

**[질문]**

`08-subject.md`와 `08-quest.md` Feature Suggestions에는 **Rate Limiting으로 거부**하는 것과 **대기열로 줄 세우는** 것을 어떤 상황에 쓸지 묻는 항목이 있어요.

**봇·비정상 트래픽**과 **행사 몰림(정상 유저)** 을 각각 어떤 전략으로 나눠 처리하는 게 낫다고 보시는지, 본인 말로 **두 문장**만 적어 주세요.

**[답변]**

**봇·비정상 트래픽**에는 IP·디바이스·페이지별 **Rate Limiting과 WAF·챌린지**로 앞단에서 끊거나 429를 주는 편이 낫다 — 대기열 슬롯을 쓰지 않게 해 **정상 유저 몫을 지키고** 하류까지 악성 트래픽을 보내지 않기 위해서다. **행사 몰림(정상 유저)** 은 **대기열(ZSET + 입장 토큰)** 로 “거부” 대신 **순서와 가시성**을 주고 하류 속도에 맞춰 방출하는 편이 낫다 — 기다릴 의사가 있는 수요를 보존하면서 **Back-pressure**로 DB·PG를 보호하기 위해서다.

---

## Q7. (리스크 디스커버리) 빈틈·누락·한계·리스크를 어떻게 더 찾을 것인가?

> 이 절은 `.cursor/skills/risk-discovery-test-cases/SKILL.md` 워크플로를 **QnA 형태로 이어가기** 위한 것이다.  
> 이미 도출·구현된 TC 목록: `.cursor/skills/risk-discovery-test-cases/reference.md`, 설계 매핑: `.docs/design/08-waiting-queue-risk-test-cases.md`.

### 진행 방식

- 스킬의 **1차 질문·꼬리질문**은 대화에서 **한 번에 하나** 짚고, 확정한 문장만 아래 **[답변]**에 옮겨 적는다.
- 위 **리스크 레지스터(RD1~8)** 는 시점 스냅샷이다. 답을 쓰다 보면 RD를 고치거나 **RD9** 이후를 추가해도 된다.

### 범위 (한 문장)

`**commerce-api`의 Redis ZSET 대기열·입장 토큰·스케줄러·순번/SSE API·Kafka join fallback**이 HTTP·Redis·Kafka와 맞물리는 구간에서, 요구사항 대비 **빈틈·운영 한계·알려진 리스크를 점검한다.

### 1차 질문 (스킬 카테고리별)

아래를 **코드·설정·운영** 관점에서 각각 한 줄씩만 적어 보면, 빈틈이 드러나기 쉽다.


| 영역        | 질문                                                                                                                   |
| --------- | -------------------------------------------------------------------------------------------------------------------- |
| **경계**    | `queue.position.throughput-tps`가 0·음수·비정상적으로 크면 어떻게 되는가? 설정 검증이 있는가?                                                 |
| **동시성**   | 동일 ms에 다수가 `enter`하면 score 동점 시 **ZSET 멤버 lex 순**이 제품 의도와 맞는가? 멀티 인스턴스에서 **락 미획득 틱**이 연속되면 방출 지연을 **메트릭으로** 볼 수 있는가? |
| **실패·복구** | `join`은 Kafka fallback이 있으나 `**position` 조회는 없다** — 의도된 비대칭인가? DLT에 쌓인 메시지 **재처리 절차가 문서·런북으로 고정돼 있는가?                |
| **보안**    | 순번·SSE는 로그인 유저 기준인데, **헤더 위조**는 인터셉터·DB 실존 유저 검증으로 충분한가?                                                             |
| **시간**    | 토큰은 주문 **생성 시점**에 소비된다 — “주문 완료 후”와 다를 때 **CS·정합성** 이슈가 없는가?                                                         |
| **관측성**   | 스케줄러 틱 실패·락 스킵·방출 0이 **대시보드/알람**에 연결돼 있는가?                                                                           |
| **운영**    | `eventId`가 코드상 `**default` 고정**인데, 행사가 여러 개면 API·설계 확장이 필요하지 않은가? 대기열 **최대 길이·진입 거절 정책이 있는가?                         |


### 꼬리질문 (답이 없으면 그대로 리스크로 적는다)

1. SSE 연결 **수·스레드**(유저당 `SingleThreadExecutor`)가 피크 시 **서버 한계**를 만드는가? 상한을 둘 것인가?
2. `require-entry-token`이 **test/local에서는 false**인데, CI에서 **prd-like 스모크**를 돌릴 것인가?
3. Redis를 **읽기 복제**만 바라보는 구성은 없는가? 있다면 순번 **stale**을 허용하는가?

### 리스크 레지스터 (현재 코드·문서 기준 1차 정리)


| ID  | Risk / 한계                                                               | Trigger              | 영향                              | 완화·메모 (테스트 / 코드 / 운영)                         |
| --- | ----------------------------------------------------------------------- | -------------------- | ------------------------------- | --------------------------------------------- |
| RD1 | **API·Facade의 `eventId` 고정** (`default`)                                | 다행사·다 큐 요구           | 단일 이벤트만 표현                      | 설계 확장 시 도메인 VO·API 파라미터·문서 동시 갱신              |
| RD2 | `**position`에 Kafka fallback 없음                                         | Redis만 장애            | 진입은 접수됐으나 순번 조회는 500            | Q1과 정합; “순번 불가” UX·문구 정책 명시                   |
| RD3 | **동점 score → lex 순** (userId 문자열)                                       | 동일 epoch ms 다건 enter | 숫자 순서와 다른 FIFO                  | `TC-R1-1`·설계 문서화; 필요 시 score에 서브 시퀀스(승인 후 코드) |
| RD4 | 스케줄러 **락 스킵**은 `loopers.queue.scheduler.lock.skipped` 등으로 계측            | 락 경쟁·TTL             | 알람·대시보드는 **운영 규칙**으로 연결         | 틱 완료·invocation 메트릭과 함께 알람 규칙 정의              |
| RD5 | `throughput-tps`는 **유한·(0, 상한]** 검증(`QueuePositionProperties`)          | 극단 오설정               | 기동 시 `IllegalArgumentException` | `TC-R2-1` 등 회귀 유지                             |
| RD6 | **토큰 소비 시점 = 주문 생성**                                                    | 결제 실패·롤백             | “입장권”과 “결제 완료” 불일치 인식           | 제품 문구·정책 명시; 필요 시 상태머신 별도 설계                  |
| RD7 | Kafka DLT 재처리 **런북** `.docs/runbooks/queue-join-fallback-dlt-replay.md` | 소비 실패 누적             | 수동 재발행 절차는 문서화됨                 | 멱등·검증은 `TC-R4-1` 계열과 운영 합의                    |
| RD8 | **SSE 동시 연결 상한·백프레셔** 미기재                                               | 피크 시 연결 폭증           | 스레드·소켓 고갈                       | 게이트웨이 제한·문서화·부하 테스트                           |


### 테스트 매핑 (이미 있는 것 / 보강 후보)

- RD3·RD5 등은 `.cursor/skills/risk-discovery-test-cases/reference.md`의 **TC-R1-1, TC-R2-1** 등과 연결된다.
- RD8(SSE 상한) 등은 **운영·부하 테스트** 영역 — 필요 시 `TC-RD8-1` 형식으로 번호를 붙이면 된다.

### 리팩토링 힌트 (승인 후에만 구현)

- `eventId`·score 생성을 **도메인 서비스/VO**로 모아 인프라(ZSET 규칙)를 한곳에서 설명 가능하게 할지 검토.
- SSE **동시 연결 상한**(애플리케이션 코드) 여부를 검토.

**[답변]** (대화 누적 — 아래에만 덧붙인다)

**Q7-1 · 경계 (`throughput-tps`)**

- 코드 참고: `QueuePositionProperties` 컴팩트 생성자에서 `throughputTps`가 **유한하고 (0, `MAX_THROUGHPUT_TPS`]** 가 아니면 `IllegalArgumentException`으로 기동 단계에서 실패한다. `QueuePositionEstimator`는 유효한 TPS로만 호출된다.
- **구현 기준 답**: **0·음수·비유한·상한 초과**는 프로퍼티 바인딩 시 막힌다. `TC-R2-1` 등으로 회귀를 유지하는 전제다.

**Q7-2 · 동시성**

- 코드 참고: 동점 score 시 ZSET은 멤버 **문자열 lex** 순(`TC-R1-1`). 연속 틱 락 스킵은 `QueueRedisInfrastructureIntegrationTest` 등에서 검증. 락 미획득 시 `loopers.queue.scheduler.lock.skipped` 카운터 증가(`QueueInfrastructureMetrics`).
- **구현 기준 답**: **동점은 userId 문자열 lex**로 정해져 있고, 제품 FIFO와 다르면 score에 서브 시퀀스를 넣는 식으로만 바꿀 수 있다. **락 미획득**은 Micrometer·통합 테스트로 확인 가능하다.

**Q7-3 · 실패·복구**

- 코드 참고: `join`만 Kafka fallback, `position`은 없음(Q1·RD2). DLT 재처리는 `.docs/runbooks/queue-join-fallback-dlt-replay.md`에 절차가 있다(RD7).
- **구현 기준 답**: **의도된 비대칭**으로, 진입만 Kafka로 접수·복구하고 순번은 Redis 일관 시에만 200이다. DLT는 **런북 기준 수동 재발행** 전제다.

**Q7-4 · 보안**

- 코드 참고: 고객 API는 `CustomerAuthInterceptor`로 `X-Loopers-LoginId` 공백·**DB 실존 유저** 검증 후 컨트롤러 진입.
- **구현 기준 답**: `/api/v1/queue/`** 도 인터셉터 대상이라 **위조 loginId는 DB에 없으면 401**이다. **비밀번호·세션 없이 헤더만**이므로 네트워크 탈취·공유 단말 리스크는 **제품 스코프 밖**으로 남는다.

**Q7-5 · 시간**

- 코드 참고: 입장 토큰 소비는 `OrderFacade` 초입 `OrderEntryTokenGate`(주문 **생성** 시점).
- **구현 기준 답**: **주문 생성 시점에 토큰을 소비**하므로 “결제 완료 후 삭제” 문구와는 다르다. 결제 실패 시에도 토큰은 이미 없어 **재주문은 재입장** 흐름이 되며, CS·문구는 그에 맞춰 안내해야 한다.

**Q7-6 · 관측성**

- 코드 참고: API 측 `loopers.queue.backend.failures` 등. 스케줄러는 `loopers.queue.scheduler.invocations`, `loopers.queue.scheduler.lock.skipped`, `loopers.queue.scheduler.tick.completed`(tag `released_empty`) 등 Micrometer 카운터가 있다.
- **구현 기준 답**: 스케줄러 관련 **지표는 노출**되나, **대시보드·알람에 자동 연결**은 아니고 운영에서 임계·대시보드를 붙여야 한다.

**Q7-7 · 운영**

- 코드 참고: `QueueFacade` 등에서 `eventId`는 `default` 문자열 고정. 대기열 **최대 인원**은 `queue.join.max-waiting`(`QueueJoinProperties`)·`WaitingQueueCapacityPolicy`로 적용되고, 정원 초과 시 `WaitingQueueService`가 `CoreException(CONFLICT)` → **409** (“대기열 정원이 찼습니다.”). Redis 쪽은 `RedisWaitingQueueRepository` Lua로 원자적 정원 검사.
- **구현 기준 답**: **단일 행사(`default`)·단일 정원 상한**까지는 코드에 있다. **다 행사·행사별 정원**은 API·설계 확장이 필요하다.

**Q7-T1 · 꼬리 (SSE 연결·스레드 상한)**

- **구현 기준 답**: `QueuePositionStreamService`는 **유저당 `SingleThreadExecutor` + 5분 SSE**이며 **동시 연결·스레드 상한 코드는 없다**. 피크 시 **Tomcat·스레드 풀** 한계가 먼저 올 수 있어 게이트웨이·운영 상한이 별도다.

**Q7-T2 · 꼬리 (`require-entry-token`·CI prd-like)**

- **구현 기준 답**: **local/test 프로필**에서 `queue.order.require-entry-token: false`로 **주문 E2E가 토큰 없이** 돈다. **CI에서 prd-like 스모크**는 **별도 프로필·잡**으로 `true`를 켜야 하며, 레포 기본 파이프라인에 **고정돼 있지는 않다**.

**Q7-T3 · 꼬리 (Redis 읽기 복제·stale)**

- **구현 기준 답**: 대기열·토큰·스케줄러 락은 `RedisWaitingQueueRepository` 등이 `**redisTemplateMaster`** 를 쓰므로 **읽기 복제만으로 순번이 stale** 해지는 경로는 **현재 구현에 없다**. 복제 지연이 문제가 되려면 **템플릿을 replica로 바꾼 뒤**의 설계다.

**한 줄 요약 (우선순위·감수 범위)**

- **감수**: 단일 `default` 큐·토큰=주문 시점 소비·position 비대칭. **우선 보강 후보**: 다 행사·행사별 정원·SSE/연결 상한 코드·토큰 prd-like CI. (throughput-tps는 `QueuePositionProperties`에서 유한·(0, 상한] 검증, 스케줄 락 스킵은 `loopers.queue.scheduler.lock.skipped`, DLT 런북은 `.docs/runbooks/queue-join-fallback-dlt-replay.md`.)

---

