# 📝 Round 8 Quests

---

## 💻 Implementation Quest

> 트래픽이 폭증하는 순간에도 시스템을 보호하면서, **유저에게 공정한 대기 경험**을 제공하는 구조를 설계하고 구현합니다.
Redis 기반 대기열로 **처리량을 제어**하고, 입장 토큰과 실시간 순번 조회를 통해
**"기다리는 동안에도 이탈하지 않는"** 주문 흐름을 만들어봅니다.
> 

<aside>
🎯

**Must-Have (이번 주에 무조건 가져가야 좋을 것-**무조건 ****하세요**)**

- Redis Sorted Set 기반 대기열
- 입장 토큰 발급 & 검증 (TTL)
- 스케줄러 기반 순차 입장 처리
- Polling 기반 순번 조회 API

**Nice-To-Have (원래 과제 문구 — 현 레포에서는 아래 일부가 이미 구현됨)**

- SSE 기반 실시간 순번 Push → **`GET /api/v1/queue/position/stream`** + 동시 연결 상한
- Polling 주기 동적 조절 → **`QueuePollHintPolicy`** + `Retry-After`
- Thundering Herd 완화 → 스케줄러 소배치·짧은 틱·토큰 저장 전 **0~300ms** 지연
- Graceful Degradation → Redis 장애 시 **진입** Kafka fallback (`queue.fallback.*`)
</aside>

### 📋 과제 정보

**Step 1 — Redis 기반 대기열 구현**

- 블랙 프라이데이 행사를 앞두고, 주문 API 앞단에 **대기열 시스템**을 구축한다.
- Redis Sorted Set을 활용해 **진입 순서를 보장**하고, **중복 진입을 방지**한다.
- 유저는 대기열에 진입한 뒤, 자신의 **순번과 예상 대기 시간**을 조회할 수 있다.

**Step 2 — 입장 토큰 & 스케줄러**

- 스케줄러가 일정 주기로 대기열에서 N명씩 꺼내 **입장 토큰을 발급**한다.
- 토큰은 **TTL**이 있어 일정 시간 내 사용하지 않으면 자동 만료된다.
- 토큰이 있는 유저만 주문 API에 진입할 수 있으며, 구현은 **`placeOrder` 호출 직전**에 토큰을 **Lua로 일회 소비**(주문 성공 여부와 무관).
- 처리량 설계 기준(DB 커넥션 풀, 평균 처리 시간)을 바탕으로 **스케줄러의 배치 크기를 산정**한다.

**Step 3 — 실시간 순번 조회**

- 유저가 대기 중 **현재 순번과 예상 대기 시간**을 실시간으로 확인할 수 있는 API를 구현한다.
- Polling 기반으로 구현하되, 대기 인원에 따른 **Polling 부하**를 고려한다.

**주문 이후 흐름**

- 대기열은 **주문 API 앞단의 관문**이다.
- 주문 API 이후의 흐름(이벤트 발행, Kafka 파이프라인, Metrics 집계)은 **R7에서 구축한 구조를 그대로 활용**한다.

---

## ✅ Checklist

> 아래는 **현재 레포 기준** 반영이다. API 경로는 실제로 `POST /api/v1/queue/enter`, `GET /api/v1/queue/position` 이다.

### 🚪 Step 1 — 대기열

- [x]  Redis Sorted Set 기반 대기열 진입 API 구현 (`POST /api/v1/queue/enter`)
- [x]  순번 조회 API 구현 (`GET /api/v1/queue/position`, SSE `.../position/stream`)
- [x]  userId 기반 중복 진입 방지
- [x]  전체 대기 인원 조회

### 🎫 Step 2 — 입장 토큰 & 스케줄러

- [x]  스케줄러가 주기적으로 대기열에서 N명을 꺼내 입장 토큰 발급
- [x]  토큰 TTL 설정 (e.g. 5분)
- [x]  주문 API 진입 시 토큰 검증·일회 소비 (`OrderEntryTokenGate`, `require-entry-token`)
- [x]  토큰은 주문 본문 **전** 소비(Lua); 완료 후 별도 DEL 없음
- [x]  처리량 기준으로 스케줄러 배치 크기 산정 근거 문서화

### 📡 Step 3 — 실시간 순번 조회

- [x]  예상 대기 시간 계산 로직 구현
- [x]  Polling 기반 순번 + 예상 대기 시간 응답
- [x]  토큰 발급 시 순번 조회 응답에 토큰 포함

### 🧪 검증

- [x]  동시 진입 테스트 — 대기열 순서가 정확히 보장되는지 확인 (`QueueQuestVerificationIntegrationTest#concurrentJoin_shouldPreservePopOrderByScore`)
- [x]  토큰 만료 테스트 — TTL 초과 후 무효화 (`QueueQuestVerificationIntegrationTest#entryToken_afterTtlExpires_shouldNotBeFoundOrConsumable`)
- [x]  처리량 초과 테스트 — 스케줄러 배치 이상 부하 시 안정성 (`QueueQuestVerificationIntegrationTest#scheduler_shouldDrainQueueLargerThanBatchAcrossMultipleTicks` — 배치 18명·대기 50명·`JitterDelay`/`SchedulerLockRepository` 목 처리)

---

### 🎯 Feature Suggestions

- Rate Limiting으로 거부하는 것과 대기열로 줄 세우는 것, 어떤 상황에서 어떤 전략이 맞을까?
- 스케줄러 배치 크기를 어떻게 산정했는가? 그 근거는?
- Thundering Herd를 직접 겪었다면, 어떻게 완화했는가?
- Redis가 죽으면 우리 서비스는 어떻게 되어야 하는가?
- Polling vs SSE — 왜 그 방식을 선택했는가?
- 토큰 TTL을 몇 분으로 설정했고, 그 기준은 무엇인가?