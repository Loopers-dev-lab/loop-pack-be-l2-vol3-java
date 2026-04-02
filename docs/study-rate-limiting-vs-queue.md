# Rate Limiting vs 대기열 (Queue) 전략 비교

> 이 문서는 Black Friday 트래픽 폭증 시나리오에서 두 전략의 차이를 이해하기 위한 학습 자료입니다.
> 관련 시뮬레이터: `apps/commerce-api/src/test/java/com/loopers/simulation/`

---

## 1. 왜 이 문제가 생기는가

주문 API에 트래픽이 집중되면 HikariCP 커넥션 풀이 고갈되어 DB가 다운된다.
단순한 대응으로 Rate Limiting(초과 시 즉시 거절)을 걸면 오히려 더 큰 문제가 생긴다.

```
[Rate Limiting의 역설]

사용자 200명 동시 요청
    → 80명 성공, 120명 429 수신
    → 120명 즉시 재시도
    → 80명 완료되기 전에 120명이 다시 몰림
    → 또 120명 거절 → 즉시 재시도
    → ... 반복
```

이것이 **Thundering Herd** 문제다. 거절할수록 요청이 줄지 않고 오히려 늘어난다.

---

## 2. 두 전략 비교

### 판단 기준: 거절된 요청이 즉시 재시도되는가?

| | Rate Limiting | 대기열 (Queue) |
|---|---|---|
| 초과 시 응답 | 즉시 거절 (429) | 번호표 발급 후 대기 |
| 클라이언트 행동 | 즉시 재시도 → 요청 폭증 | 서버 안내 간격으로 폴링 |
| 서버 부하 | 거절할수록 증가 (Thundering Herd) | 제어된 유입량 유지 |
| 처리 완료 시간 | 빠름 (경쟁 방식) | 느림 (순서 방식) |
| 사용자 경험 | 그냥 실패 | "150번째 대기 중" |

### 각 전략이 맞는 상황

**Rate Limiting이 적합한 경우:**
- 악성 봇, 크롤러 차단
- API 남용 방지 (1분에 100회 이상 호출하는 클라이언트)
- 외부 파트너 API 할당량 관리
- 거절당한 주체가 즉시 재시도하지 않는 경우

**대기열이 적합한 경우:**
- 블랙프라이데이 주문, 티켓팅, 한정 쿠폰 발급
- 사용자가 "왜 안 돼?" 하고 계속 누르는 상황
- 정상 사용자 트래픽이지만 동시에 너무 몰리는 경우

**실무 패턴:** 두 전략을 함께 사용한다.
대기열 앞단에 Rate Limiting을 걸어 봇은 즉시 차단하고, 정상 사용자는 대기열로 흡수.

---

## 3. 시뮬레이션 결과

가상 틱(tick) 기반 결정론적 시뮬레이션. 조건: 사용자 200명, 서버 용량 80명 동시 처리.

```
╔══════════════════════════════════════════════════════════════════╗
║            트래픽 전략 비교 시뮬레이션 결과                          ║
╠══════════════════════════════════╦═══════════════╦═══════════════╣
║ 지표                              ║ Rate Limiting ║         Queue ║
╠══════════════════════════════════╬═══════════════╬═══════════════╣
║ 총 발생 요청 수 (재시도 포함)     ║           680 ║           207 ║
║ 성공한 요청 수                    ║           200 ║           200 ║
║ 최대 동시 요청 수                 ║            80 ║            80 ║
║ 재시도 횟수                       ║           480 ║             7 ║
║ 처리 완료 시간 (가상 ms)          ║          1000 ║          1300 ║
╚══════════════════════════════════╩═══════════════╩═══════════════╝
```

**읽는 법:**
- 총 요청 수 680 vs 207: Rate Limiting은 거절당한 120명이 즉시 재시도를 반복해 요청이 3배 이상 뻥튀기
- 재시도 횟수 480 vs 7: Queue는 배치 3회 안에 전원 처리되어 폴링 기회 자체가 거의 없음
- 처리 완료 시간: Queue가 30% 더 길다. 배치 단위로 순서대로 처리하므로 마지막 사람이 더 늦게 완료됨

**핵심 트레이드오프:** Queue는 서버 부하를 안정적으로 유지하는 대신 전체 처리 완료까지 더 오래 걸린다.

---

## 4. 이 프로젝트의 대기열 구현 (volume-8)

### 3단계 파이프라인

```
[사용자가 "주문하기" 버튼 클릭]
        │
        ▼ ① POST /api/v1/queue/enter
[QueueV1Controller → QueueFacade]
        │  ZADD queue:waiting NX {currentTimeMillis} {userId}   ← 중복 방지
        │  ZRANK → 내 순번 / ZCARD → 전체 대기 인원
        ▼
    응답: { position: 45, totalCount: 200 }

    ── 5초마다 백그라운드 (TokenScheduler) ──────────────────────
        Redisson 분산 락 획득
        Lua 스크립트 원자적 실행:
            ZRANGE 상위 80명 조회
            EXISTS token:{userId} == 0 인 사람만 선별
            ZREM queue:waiting {userId}
        SET token:{userId} {UUID} EX 300
    ────────────────────────────────────────────────────────────

        │ ② GET /api/v1/queue/position (Adaptive Polling)
        ▼
[QueueFacade.getPosition()]
        │  순번 ≤ 10 → 5초 후 다시 조회
        │  순번 ≤ 50 → 15초 후 다시 조회
        │  순번 > 50 → 30초 후 다시 조회
        ▼
    응답: { position: 3, estimatedWaitSeconds: 1,
            nextPollAfterSeconds: 5, token: "uuid-..." }

        │ ③ POST /api/v1/orders (X-Queue-Token 헤더 포함)
        ▼
[QueueTokenInterceptor.preHandle()]
        │  GET token:{userId} → 값 불일치 or 없음 → 403
        │  일치 → 통과 → 주문 처리 → DEL token:{userId}
        ▼
    응답: 200 OK
```

### 주요 설계 결정

| 결정 | 채택 | 이유 |
|------|------|------|
| 중복 진입 방지 | `ZADD NX` | ZSCORE → ZADD 분리 시 TOCTOU 문제 |
| 토큰 설계 | UUID + Redis String TTL 300초 | userId만 알면 위조 가능한 단순 값 대신 UUID |
| 토큰 검증 위치 | `HandlerInterceptor` | Filter는 ControllerAdvice 연동 불가 |
| 배치 팝 원자성 | Lua 스크립트 | ZRANGE→EXISTS→ZREM 분리 시 유령 상태 발생 |
| 스케줄러 중복 실행 방지 | Redisson 분산 락 | 멀티 인스턴스 환경에서 토큰 중복 발급 방지 |
| 폴링 주기 안내 | Adaptive (`nextPollAfterSeconds`) | 고정 주기 폴링은 순번 먼 사람도 불필요한 요청 반복 |

---

## 5. 시뮬레이터 실행 방법

```bash
# IntelliJ에서 테스트 클래스 옆 초록 버튼 클릭 (Run 탭에서 결과표 확인 가능)

# 또는 터미널에서
./gradlew :apps:commerce-api:test \
  --tests "com.loopers.simulation.RateLimitingVsQueueTest" \
  --rerun-tasks
```

시뮬레이터 파라미터 변경은 `TrafficSimulator.Config.defaults()`에서 조정:
- `rateLimitRetryTicks`: 1 → 더 크게 하면 Thundering Herd 완화 효과 확인 가능
- `batchSize`: 80 → 더 작게 하면 Queue 처리 시간 증가 확인 가능
- `totalUsers`: 200 → 더 크게 하면 두 전략의 차이가 더 극명하게 드러남
