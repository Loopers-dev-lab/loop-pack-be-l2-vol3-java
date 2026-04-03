# Week 8 구현 계획: Redis 기반 대기열 시스템

> **목표** — 주문 API 앞단에 Redis Sorted Set 대기열 + 입장 토큰 + 스케줄러 기반 순차 입장 시스템을 구현한다.
> 기존 Week 7 파이프라인(Outbox, Kafka, Metrics)은 그대로 유지하고, 대기열은 주문 API의 **관문(gate)** 역할만 한다.

---

## 전체 아키텍처 요약

```
[Client] → POST /api/v1/queue/enter → Redis Sorted Set (ZADD NX)
              │
              ├── GET /api/v1/queue/position
              │      → ZRANK 조회
              │      → 토큰 존재 시: ZREM + tokenIssued=true 반환 (큐에서 제거)
              │
[Scheduler] ──── fixedDelay 100ms ────→ ZRANGE(조회만) + SET entry-token:{userId} NX (멱등성)
              │
[Client] → POST /api/v1/orders → preHandle: 토큰 존재 확인만
              │                → 주문 생성
              │                → OrderCreatedEvent → QueueOrderEventListener: 토큰 삭제
              │
              └── (기존 Week 7 흐름) → OrderCreatedEvent → Outbox → Kafka → Metrics
```

> **핵심 변경**: 스케줄러는 ZRANGE로 조회만 하고 큐에서 제거하지 않는다.
> 유저가 폴링 API에서 토큰을 확인하는 시점에 ZREM으로 큐에서 제거한다.
> 이렇게 하면 스케줄러 크래시 시 유저가 큐에서 소실되지 않고, Lua 스크립트 없이 애플리케이션 코드로만 멱등성을 확보할 수 있다.

---

## 설계 결정 요약 (design-notes.md 기준)

| 결정 포인트 | 선택 | 핵심 근거 |
|------------|------|----------|
| 토큰 방식 | UUID + Redis SET | 단일 사용 보장에 어차피 Redis 필요 → 가장 단순한 방식 |
| 토큰 키 구조 | `entry-token:{userId}` | 중복 슬롯 점유 구조적 차단 + AuthInterceptor와 자연스러운 연결 |
| TTL 초기값 | 5분 (300초) | 보수적 출발, 모니터링으로 조정 |
| 스케줄러 방식 | fixedDelay | 누적 없이 배치 크기 항상 예측 가능 |
| 배치 크기 | 14명/회 | HikariCP 40 / 0.2s avg × 70% = 140 TPS ÷ 10회/초. TTL 기반 최악값(300s × N)은 "전원 동시 주문" 가정으로 비현실적 → TPS 지속 부하 기준 채택. 버스트 방어는 별도 rate limiter(Phase 6 후보) |
| 원자성 | 애플리케이션 멱등성 | ZRANGE(조회) + SET NX(발급). Lua 불필요 — 스케줄러 크래시 시 유저 큐 잔류로 자동 복구 |
| 활성/이탈 구분 | 구분하지 않음 | TTL로 정책화, Heartbeat 복잡도 불필요 |

---

## 패키지 구조 설계

```
apps/commerce-api/src/main/java/com/loopers/
├── interfaces/api/queue/
│   ├── QueueV1ApiSpec.java              # Swagger 인터페이스
│   └── QueueV1Controller.java           # 대기열 진입, 순번 조회 API
├── application/queue/
│   ├── QueueFacade.java                 # 대기열 진입/순번 조회 유스케이스
│   ├── EntryTokenScheduler.java         # 스케줄러: ZRANGE → SET NX 토큰 발급
│   └── QueueOrderEventListener.java     # OrderCreatedEvent 수신 → 토큰 삭제
├── domain/queue/
│   ├── WaitingQueueRepository.java      # 대기열 Port (enqueue/getPosition/peekBatch/remove)
│   ├── EntryTokenRepository.java        # 토큰 Port (issueIfAbsent/existsByUserId/delete)
│   └── QueuePosition.java              # 순번 조회 결과 VO
├── infrastructure/queue/
│   ├── RedisWaitingQueueRepository.java # Sorted Set 구현체
│   └── RedisEntryTokenRepository.java   # Redis SET 구현체 (SET NX EX)
└── interfaces/interceptor/
    └── EntryTokenInterceptor.java       # POST /orders 진입 시 토큰 존재 검증만 (삭제 안 함)
```

---

## 구현 Phase 구성

### Phase 1: 대기열 진입 & 순번 조회 (Step 1)

### Phase 2: 입장 토큰 & 스케줄러 (Step 2)

### Phase 3: 토큰 검증 & 주문 연동 (Step 2 연장)

### Phase 4: 실시간 순번 조회 고도화 (Step 3)

### Phase 5: 동시성 & 통합 검증

---

## Phase 1: 대기열 진입 & 순번 조회

> 목표: Redis Sorted Set 기반으로 유저가 대기열에 진입하고, 순번을 조회할 수 있다.

### 1-1. domain 레이어 — Port & VO 정의

**파일**: `domain/queue/WaitingQueueRepository.java`

```java
public interface WaitingQueueRepository {
    boolean enqueue(Long userId, double score);   // ZADD NX → true: 신규, false: 이미 존재
    Long getPosition(Long userId);                // ZRANK → null이면 큐에 없음
    long getTotalCount();                         // ZCARD
}
```

**파일**: `domain/queue/QueuePosition.java`

```java
public record QueuePosition(
    long position,           // 0-based rank + 1 = 1-based 순번
    long totalWaiting,       // 전체 대기 인원
    long estimatedWaitSeconds // 예상 대기 시간 (초)
) {
    public static QueuePosition of(long rank, long totalCount, int batchSize, int intervalMs) {
        long position = rank + 1;
        // 예상 대기 시간 = (내 순번 / 배치 크기) × 스케줄러 간격
        long estimatedWaitSeconds = (position / batchSize) * intervalMs / 1000;
        return new QueuePosition(position, totalCount, estimatedWaitSeconds);
    }
}
```

### 1-2. infrastructure 레이어 — Redis 구현

**파일**: `infrastructure/queue/RedisWaitingQueueRepository.java`

```java
@Repository
@RequiredArgsConstructor
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String QUEUE_KEY = "waiting-queue:order";

    private final RedisTemplate<String, String> redisTemplateMaster;

    @Override
    public boolean enqueue(Long userId, double score) {
        // ZADD NX — 이미 존재하면 false 반환
        return Boolean.TRUE.equals(
            redisTemplateMaster.opsForZSet().addIfAbsent(QUEUE_KEY, String.valueOf(userId), score)
        );
    }

    @Override
    public Long getPosition(Long userId) {
        // ZRANK — 0-based, null이면 큐에 없음
        return redisTemplateMaster.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
    }

    @Override
    public long getTotalCount() {
        Long count = redisTemplateMaster.opsForZSet().zCard(QUEUE_KEY);
        return count != null ? count : 0;
    }
}
```

> **설계 포인트**: 순번 조회(`getPosition`)도 Master에서 읽는다.
> Replica 복제 지연으로 순번이 뒤로 밀려 보이는 것보다, 정확한 순번을 보여주는 게 UX에 유리하다.
> 폴링 부하가 문제되면 Phase 4에서 Replica 전환 + 허용 오차 문서화.

### 1-3. application 레이어 — QueueFacade

**파일**: `application/queue/QueueFacade.java`

```java
@Service
@RequiredArgsConstructor
public class QueueFacade {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Value("${queue.batch-size:18}")
    private int batchSize;

    @Value("${queue.scheduler-interval-ms:100}")
    private int schedulerIntervalMs;

    public QueuePosition enterQueue(Long userId) {
        double score = System.currentTimeMillis();
        boolean isNew = waitingQueueRepository.enqueue(userId, score);
        // 신규든 기존이든 현재 순번을 반환
        return getPosition(userId);
    }

    public QueuePosition getPosition(Long userId) {
        // 1. 이미 토큰을 발급받았는지 확인
        if (entryTokenRepository.existsByUserId(userId)) {
            return QueuePosition.tokenIssued();
        }

        // 2. 대기열에서 순번 조회
        Long rank = waitingQueueRepository.getPosition(userId);
        if (rank == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 존재하지 않는 유저입니다.");
        }

        long totalCount = waitingQueueRepository.getTotalCount();
        return QueuePosition.of(rank, totalCount, batchSize, schedulerIntervalMs);
    }
}
```

### 1-4. interfaces 레이어 — Controller & ApiSpec

**파일**: `interfaces/api/queue/QueueV1Controller.java`

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    public ApiResponse<QueuePositionResponse> enter(@LoginUser UserInfo userInfo) {
        QueuePosition position = queueFacade.enterQueue(userInfo.getId());
        return ApiResponse.success(QueuePositionResponse.from(position));
    }

    @GetMapping("/position")
    public ApiResponse<QueuePositionResponse> position(@LoginUser UserInfo userInfo) {
        QueuePosition position = queueFacade.getPosition(userInfo.getId());
        return ApiResponse.success(QueuePositionResponse.from(position));
    }
}
```

**응답 DTO**: `QueuePositionResponse`

```java
public record QueuePositionResponse(
    long position,
    long totalWaiting,
    long estimatedWaitSeconds,
    boolean tokenIssued        // true면 클라이언트가 주문 API로 전환
) {
    public static QueuePositionResponse from(QueuePosition qp) {
        return new QueuePositionResponse(
            qp.position(), qp.totalWaiting(), qp.estimatedWaitSeconds(), qp.tokenIssued()
        );
    }
}
```

### 1-5. 테스트 계획 (TDD Red Phase)

| 테스트 | 검증 내용 | 유형 |
|--------|----------|------|
| `대기열_진입_성공` | ZADD NX → 순번 반환 | Facade 통합 |
| `중복_진입_시_기존_순번_반환` | 동일 userId 재진입 → score 불변, 기존 순번 반환 | Facade 통합 |
| `순번_조회_대기열에_없는_유저` | NOT_FOUND 예외 | Facade 통합 |
| `전체_대기_인원_조회` | N명 진입 → totalWaiting = N | Facade 통합 |
| `예상_대기_시간_계산` | 순번 36, 배치 18, 간격 100ms → 예상 0초(즉시~) | 단위 테스트 |
| `100명_동시_진입_순서_보장` | ExecutorService + CountDownLatch → ZCARD = 100, 중복 없음 | 동시성 테스트 |

---

## Phase 2: 입장 토큰 & 스케줄러

> 목표: 스케줄러가 ZRANGE로 대기열 상위 N명을 조회하고, 토큰이 없는 유저에게만 UUID 토큰을 발급한다.
> 큐에서의 실제 제거(ZREM)는 유저가 폴링 API에서 토큰을 확인하는 시점에 처리한다.

### 핵심 설계: Lua 없이 애플리케이션 멱등성으로 원자성 대체

**왜 Lua를 쓰지 않는가?**

구버전(ZPOPMIN + Lua) 문제:
```
스케줄러: ZPOPMIN으로 userId 제거 → SET entry-token:{userId} 발급
          ↑ 크래시 발생 시
          userId는 큐에서 이미 사라졌고, 토큰도 없음 → 유저 소실
```

새 접근(ZRANGE + SET NX):
```
스케줄러: ZRANGE로 userId 조회 (큐에서 제거 안 함)
        → entry-token:{userId} 없으면 SET NX로 토큰 발급
        → 크래시 발생해도 userId는 큐에 남아 있음 → 다음 스케줄러 실행 시 재발급
폴링 API: 토큰 확인 → 있으면 ZREM으로 큐에서 제거 → tokenIssued=true 반환
```

**멱등성 보장 방법**: `entry-token:{userId}` 키가 이미 존재하면 `SET NX`는 아무것도 하지 않는다.
같은 유저에게 스케줄러가 여러 번 실행되어도 토큰이 중복 발급되지 않는다.

---

### 2-1. domain 레이어 — 이미 Phase 1에서 정의 완료

`EntryTokenRepository.java`는 Phase 1에서 이미 올바른 인터페이스로 정의되어 있다.

```java
public interface EntryTokenRepository {
    void issueIfAbsent(Long userId, String token, long ttlSeconds); // SET NX EX
    boolean existsByUserId(Long userId);                             // EXISTS
    void delete(Long userId);                                        // DEL (주문 완료 후)
}
```

### 2-2. infrastructure 레이어 — 이미 Phase 1에서 구현 완료

`RedisEntryTokenRepository.java`도 Phase 1에서 구현됨.
- `issueIfAbsent()`: `opsForValue().setIfAbsent()` — Redis `SET NX EX`
- `existsByUserId()`: `hasKey()`
- `delete()`: `delete()`

### 2-3. application 레이어 — EntryTokenScheduler

**파일**: `application/queue/EntryTokenScheduler.java`

```
[스케줄러 동작 흐름]

100ms마다 실행:
  1. ZRANGE waiting-queue:order 0 17  → 상위 18명 userId 목록 조회 (큐에서 제거 안 함)
  2. 각 userId에 대해:
       GET entry-token:{userId}  → 이미 토큰 있음? → 건너뜀 (멱등성)
                                 → 토큰 없음?     → SET NX EX 300 entry-token:{userId} {uuid}
  3. 발급된 유저 수 로깅
```

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryTokenScheduler {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Value("${queue.batch-size:18}")
    private int batchSize;

    @Value("${queue.token-ttl-seconds:300}")
    private long tokenTtlSeconds;

    @Scheduled(fixedDelayString = "${queue.scheduler-interval-ms:100}")
    public void issueTokens() {
        try {
            List<Long> candidates = waitingQueueRepository.peekBatch(batchSize); // ZRANGE
            if (candidates.isEmpty()) {
                return;
            }

            int issued = 0;
            for (Long userId : candidates) {
                // SET NX: 이미 토큰이 있으면 건너뜀 (멱등성)
                String token = UUID.randomUUID().toString();
                entryTokenRepository.issueIfAbsent(userId, token, tokenTtlSeconds);
                issued++;
            }

            log.info("입장 토큰 발급 완료: {}명 (후보 {}명)", issued, candidates.size());
        } catch (Exception e) {
            // 최외곽 try/catch: @Scheduled 스레드 사망 방지 (design-notes.md 참조)
            log.error("토큰 발급 스케줄러 오류", e);
        }
    }
}
```

> **설계 포인트 정리**:
> - `fixedDelay` 사용 — 이전 실행이 끝난 후 100ms 대기. `fixedRate`는 실행이 누적되어 배치 크기 통제 불가.
> - 최외곽 `try/catch` — Spring `@Scheduled` 단일 스레드에서 uncaught exception 발생 시 스케줄러 영구 정지.
> - `peekBatch`(ZRANGE)가 빈 결과 반환 시 → 즉시 return, 불필요한 반복 없음.
> - `issueIfAbsent`(SET NX) — 동일 userId 재조회 시 토큰 덮어쓰기 없음. TTL 리셋 없음.

### 2-4. QueueFacade.getPosition() — 폴링 시 ZREM 처리 (이미 Phase 1에서 구현)

```
[폴링 API 흐름 — 토큰 발급 후 첫 조회 시]

1. ZRANK waiting-queue:order {userId}
   → rank == null (스케줄러가 이미 토큰을 발급했지만 큐는 아직 안 지워짐)

2. EXISTS entry-token:{userId}
   → true

3. ZREM waiting-queue:order {userId}  ← 여기서 큐에서 제거
   → QueuePosition.ofTokenIssued() 반환

클라이언트: tokenIssued=true → 주문 페이지로 이동
```

이 흐름은 `QueueFacade.getPosition()`에 이미 구현되어 있다.

### 2-5. 테스트 계획 (TDD Red Phase)

| 테스트 | 검증 내용 | 유형 |
|--------|----------|------|
| `스케줄러_실행_시_N명_토큰_발급` | 큐에 20명 → 스케줄러 1회 → 18명 토큰 발급 | Facade 통합 |
| `스케줄러_실행_후_큐에서_제거_안됨` | 스케줄러 실행 후 ZCARD = 20 유지 (ZRANGE 사용) | Facade 통합 |
| `빈_큐에서_스케줄러_실행` | 에러 없이 종료 | Facade 통합 |
| `토큰_TTL_만료_테스트` | TTL 1초 발급 → 1.5초 후 existsByUserId = false | 통합 |
| `폴링_시_토큰_있으면_큐에서_제거` | 토큰 발급 → getPosition() → ZRANK = null, tokenIssued = true | Facade 통합 |
| `스케줄러_멱등성` | 동일 유저 토큰 발급 후 스케줄러 재실행 → 토큰 덮어쓰기 없음 (TTL 유지) | Facade 통합 |

---

## Phase 3: 토큰 검증 & 주문 연동

> 목표: 토큰을 가진 유저만 주문 API에 진입할 수 있고, 주문 성공 후 이벤트 리스너가 토큰을 삭제한다.

### 핵심 설계: Interceptor는 검증만, 삭제는 이벤트 리스너가

**왜 Interceptor에서 삭제하면 안 되나? (implementation-plan-review.md Issue #1)**

```
[구버전 — 잘못된 설계]
preHandle(): validateAndDelete(userId)  ← 토큰 삭제
    ↓
OrderFacade.create(): 재고 부족으로 실패
    ↓
토큰은 이미 없음 → 유저 재주문 불가 + 대기열도 이탈 상태
```

```
[새 설계]
preHandle(): existsByUserId(userId) 검증만  ← 삭제 안 함
    ↓
OrderFacade.create(): 주문 성공
    ↓
orderEventPublisher.publish(OrderCreatedEvent)
    ↓
QueueOrderEventListener.handleOrderCreated(): delete(userId)  ← 여기서 삭제
  (AFTER_COMMIT — 트랜잭션 커밋이 확정된 후 실행, 주문 실패 시 실행되지 않음)
```

---

### 3-1. 토큰 검증 Interceptor

**파일**: `interfaces/interceptor/EntryTokenInterceptor.java`

```
[preHandle 동작]
1. HTTP 메서드가 POST가 아니면 통과 (GET 주문 조회는 토큰 불필요)
2. AuthInterceptor가 저장한 UserInfo에서 userId 추출
   → request.getAttribute(LoginUserArgumentResolver.ATTR_LOGIN_USER)
3. entry-token:{userId} 존재 여부만 확인 (삭제 안 함)
4. 없으면 BAD_REQUEST 예외
```

```java
@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private final EntryTokenRepository entryTokenRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Issue #3: Spring Interceptor는 HTTP 메서드 구분 불가 → 직접 체크
        if (!"POST".equals(request.getMethod())) {
            return true;
        }

        // Issue #2: AuthInterceptor가 저장한 UserInfo 꺼내기 (ATTR_LOGIN_USER 상수 재사용)
        UserInfo userInfo = (UserInfo) request.getAttribute(LoginUserArgumentResolver.ATTR_LOGIN_USER);
        Long userId = userInfo.id();

        // Issue #1: 검증만. 삭제는 주문 성공 후 QueueOrderEventListener가 담당
        if (!entryTokenRepository.existsByUserId(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효한 입장 토큰이 없습니다.");
        }

        return true;
    }
}
```

### 3-2. WebMvcConfig 인터셉터 등록

```java
// AuthInterceptor 이후에 등록 — AuthInterceptor가 먼저 UserInfo를 request에 저장해야 함
registry.addInterceptor(entryTokenInterceptor)
    .addPathPatterns("/api/v1/orders");
// HTTP 메서드 구분은 preHandle 내부에서 처리
```

### 3-3. 주문 완료 후 토큰 삭제 — QueueOrderEventListener

**파일**: `application/queue/QueueOrderEventListener.java`

```
[이벤트 흐름]
OrderFacade → orderEventPublisher.publish(OrderCreatedEvent) → QueueOrderEventListener
                                                              ↑
                                            @TransactionalEventListener(AFTER_COMMIT)
                                            트랜잭션 커밋 확정 후 실행
                                            주문 실패 시 이 리스너는 실행되지 않음
```

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueOrderEventListener {

    private final EntryTokenRepository entryTokenRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 주문 트랜잭션 커밋 확정 후 토큰 삭제
        // OrderFacade, OrderService 변경 없음 — 관심사 분리
        entryTokenRepository.delete(event.userId());
        log.debug("입장 토큰 삭제 완료: userId={}", event.userId());
    }
}
```

> **설계 포인트**:
> - `OrderFacade`는 건드리지 않는다 — 대기열은 주문의 "관문"이지 주문 로직의 일부가 아니다.
> - `@Async` + `AFTER_COMMIT` — 핵심 트랜잭션과 분리, 응답 지연 없음. 기존 `OrderEventListener`와 동일한 패턴.
> - 기존 `OrderEventListener`에 추가하지 않고 별도 클래스로 분리 — Open-Closed 원칙.

### 3-4. 테스트 계획 (TDD Red Phase)

| 테스트 | 검증 내용 | 유형 |
|--------|----------|------|
| `토큰_있는_유저_주문_성공` | 토큰 발급 → POST /orders → 201 성공 | E2E |
| `주문_성공_후_토큰_삭제됨` | 주문 성공 → existsByUserId = false | Facade 통합 |
| `토큰_없는_유저_주문_거부` | POST /orders → 400 "유효한 입장 토큰 없음" | E2E |
| `토큰_만료_후_주문_거부` | TTL 1초 토큰 → 1.5초 후 POST /orders → 400 | E2E |
| `주문_실패_시_토큰_유지` | 재고 없는 상품 주문 → 실패 → 토큰 여전히 존재 | Facade 통합 |
| `GET_주문조회는_토큰_불필요` | GET /orders → 200 (토큰 검증 없음) | E2E |

---

## Phase 4: 실시간 순번 조회 고도화

> **✅ Phase 1 구현 시 완료** — `QueuePosition` VO에 모든 필드와 팩토리 메서드를 미리 포함했기 때문에 별도 작업 불필요.

### 완료된 항목

| 항목 | 구현 위치 |
|------|----------|
| `recommendedPollingIntervalMs` 동적 폴링 주기 | `QueuePosition.of()` — 순번 100/1000 기준 1초/3초/5초 |
| `tokenIssued`, `estimatedWaitSeconds` 필드 | `QueuePosition` record |
| `ofTokenIssued()` 팩토리 메서드 | `QueuePosition.ofTokenIssued()` |
| `ofExpired()` 팩토리 메서드 | `QueuePosition.ofExpired()` |
| `QueuePositionResponse` DTO | `QueueV1Dto.QueuePositionResponse` |

---

## Phase 5: 동시성 & End-to-End 통합 검증

> 목표: 전체 흐름을 통합 테스트하고, 동시성 시나리오를 검증한다.

### 5-1. End-to-End 전체 흐름 테스트

```
1. 유저 50명 회원가입
2. 50명 동시 대기열 진입 → ZCARD = 50
3. 스케줄러 3회 실행 → 18 + 18 + 14 = 50명 전원 토큰 발급 (큐 ZCARD는 아직 50)
4. 50명 폴링 API 호출 → tokenIssued=true + ZREM → 대기열 ZCARD = 0
5. 토큰 보유 유저 50명 동시 주문 → 전원 성공
6. 모든 토큰 삭제 확인 (QueueOrderEventListener)
```

### 5-2. 동시성 테스트 (별도 파일)

**파일**: `QueueConcurrencyTest.java`

| 테스트 | 조건 | 기대 결과 |
|--------|------|----------|
| `100명_동시_대기열_진입` | 100 threads × POST /queue/enter | ZCARD = 100, 중복 없음 |
| `동일_유저_10회_동시_진입` | 10 threads × 같은 userId | ZCARD = 1 (중복 없음) |
| `스케줄러와_진입_동시_실행` | 진입 + ZRANGE/SET NX 동시 | 데이터 유실 없음 (큐 유지) |
| `토큰_동시_사용_시도` | 2 threads × 같은 토큰으로 주문 | 1명 성공, 1명 실패 |

### 5-3. 처리량 초과 시나리오

```
1. 대기열에 1000명 진입
2. 스케줄러 배치 크기 = 18
3. 검증: 18명/100ms씩 순차 처리, 나머지는 순번이 자동 감소
4. 검증: 주문 API 동시 부하가 배치 크기를 넘지 않음
```

---

## TDD 실행 순서 요약

```
Phase 1 ─── Red ──→ Green ──→ Refactor
  │  1-1. QueuePosition 단위 테스트 (예상 대기 시간 계산)
  │  1-2. WaitingQueueRepository + Redis 통합 테스트
  │  1-3. QueueFacade 통합 테스트 (진입/조회)
  │  1-4. QueueV1Controller E2E 테스트
  │
Phase 2 ─── Red ──→ Green ──→ Refactor
  │  2-1. EntryTokenRepository 통합 테스트 (SET NX 발급/멱등성/TTL)
  │  2-2. EntryTokenScheduler 통합 테스트 (ZRANGE → 발급, 큐 ZCARD 유지 확인)
  │
Phase 3 ─── Red ──→ Green ──→ Refactor
  │  3-1. EntryTokenInterceptor E2E (토큰 유/무/만료, GET 요청 통과)
  │  3-2. QueueOrderEventListener 통합 테스트 (주문 성공 후 토큰 삭제, 실패 시 유지)
  │  3-3. 주문 전체 흐름 E2E (대기열 → 토큰 → 주문 → 토큰 삭제)
  │
Phase 4 ─── ✅ 완료 (Phase 1에서 QueuePosition에 모든 필드 포함)
  │
Phase 5 ─── 동시성 + 통합
     5-1. QueueConcurrencyTest (동시 진입, 동시 토큰 사용)
     5-2. End-to-End 전체 흐름 테스트
```

---

## 설정값 & 산정 근거

### 배치 크기 산정

```
[전제]
- HikariCP maximum-pool-size: 50
- 주문 1건 평균 처리 시간: 200ms (비관적 락 + 쿠폰 검증 + 이벤트 발행)
- commerce-api 인스턴스: 1대 (로컬)

[계산]
- 이론적 최대 TPS = 50 커넥션 / 0.2초 = 250 TPS
- 안전 마진 70% = 175 TPS
- 스케줄러 간격 100ms = 초당 ~10회 실행
- 배치 크기 = 175 / 10 = 17.5 → 18명/회

[검증]
- 18명 × 10회/초 = 180 TPS < 250 TPS (안전 범위 내)
- 다중 인스턴스 시: 배치 크기를 인스턴스 수로 나누거나, 분산 락 적용
```

### TTL 설정 근거

```
[기준]
- 유저가 주문 정보를 입력하는 시간 = 알 수 없음 (초기 서비스)
- 개발자 직접 테스트: ~1~2분
- 실제 유저 예상: 2~3배 → 3~6분

[결론]
- 초기값: 5분 (300초) — 보수적 출발
- 조정 기준:
  - Token Expiry Rate > 30% → TTL 증가
  - Token Conversion Rate < 50% → TTL 감소 또는 UX 개선
```

---

## 기존 코드 참조 맵

| 구현 항목 | 참조할 기존 코드 | 재사용 포인트 |
|----------|----------------|--------------|
| Redis Repository | `RedisCouponStockRepository` | Master 전용 Template, 키 네이밍 패턴 |
| 스케줄러 | `OutboxRelayScheduler` | fixedDelay, 최외곽 try/catch, 연속 실패 중단 |
| Interceptor | `AuthInterceptor` | preHandle 패턴, request attribute 활용 |
| WebMvcConfig | `WebMvcConfig` | 인터셉터 등록 순서, 경로 패턴 |
| 통합 테스트 | `CouponFacadeIntegrationTest` | Redis Testcontainer, RedisCleanUp |
| 동시성 테스트 | `CouponIssueConcurrencyTest` | ExecutorService + CountDownLatch |
| ApiSpec | `OrderV1ApiSpec` | Swagger 인터페이스 패턴 |

---

## 미결 사항 & Phase 3+ 고려사항

| 항목 | 상태 | 비고 |
|------|------|------|
| 대기열 활성화/비활성화 시점 | 미결 | Circuit Breaker 연동, 트래픽 기반 자동 전환 |
| 다중 인스턴스 스케줄러 중복 실행 | 인지 | 로컬 1대 전제, 프로덕션 시 분산 락 또는 commerce-batch 분리 |
| SSE 기반 실시간 Push | Nice-to-Have | Phase 4 이후 검토 |
| Thundering Herd 완화 | Nice-to-Have | 발급 간격 분산 (10ms마다 ~2명) |
| Graceful Degradation | Nice-to-Have | Redis 장애 시 전면 차단 or bypass 정책 결정 필요 |
| 대기열 최대 인원 제한 | 선택 | `ZCARD` 확인 → 초과 시 503 반환 |
