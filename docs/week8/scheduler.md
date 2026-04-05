# Round 8 학습 정리 - Spring @Scheduled

---

## 1. 기본 개념

`@Scheduled`는 Spring에서 메서드를 **주기적으로 자동 실행**하는 어노테이션이다. 별도의 Quartz 같은 외부 라이브러리 없이 Spring Boot만으로 스케줄링이 가능하다.

### 1-1. 활성화

`@EnableScheduling`을 선언해야 `@Scheduled`가 동작한다.

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

또는 Application 클래스에 직접 선언:

```java
@SpringBootApplication
@EnableScheduling
public class CommerceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommerceApiApplication.class, args);
    }
}
```

### 1-2. 기본 규칙

- `@Scheduled`를 사용하는 클래스는 **반드시 Spring Bean**이어야 한다 (`@Component`, `@Service` 등)
- 반환 타입은 **void**가 원칙 (반환값이 있어도 무시됨)
- **파라미터를 받을 수 없다** (Spring이 자동으로 호출하므로 인자를 넣을 방법이 없음)

```java
// OK -- void, 파라미터 없음
@Scheduled(fixedRate = 100)
public void process() { }

// 컴파일은 되지만, 반환값은 무시됨
@Scheduled(fixedRate = 100)
public String process() { return "ignored"; }

// ❌ 파라미터가 있으면 안 됨
@Scheduled(fixedRate = 100)
public void process(String param) { }  // 에러
```

---

## 2. 주요 속성

### 2-1. fixedRate

이전 실행 **시작** 시점 기준으로 일정 간격마다 실행.

```java
@Scheduled(fixedRate = 1000)  // 1초마다
public void process() {
    // 이 메서드의 실행 시간과 관계없이 1초 간격으로 실행
}
```

```
시간 ->
|--작업(30ms)--|           |--작업(30ms)--|           |--작업(30ms)--|
0ms           30ms         1000ms        1030ms       2000ms
^                          ^                          ^
시작 기준 1000ms 간격
```

**작업이 주기보다 오래 걸리면?**

```
fixedRate = 1000ms인데 작업이 1500ms 걸리면:

|------작업(1500ms)------|--작업(1500ms)------|--작업(1500ms)------|
0ms                     1500ms              3000ms              4500ms
                        ↑ 1000ms 시점에 실행해야 하지만, 앞 작업이 안 끝남
                          -> 끝나자마자 즉시 실행 (밀림)
```

기본 단일 스레드에서는 겹치지 않고 밀린다. 하지만 의도한 주기가 보장되지 않으므로, **작업 시간이 주기보다 짧도록 설계해야 한다.**

대기열 스케줄러에 적합한 이유: **일정한 처리량(TPS)을 유지**해야 하므로.

### 2-2. fixedDelay

이전 실행 **종료** 시점 기준으로 일정 간격 후 실행.

```java
@Scheduled(fixedDelay = 1000)  // 이전 종료 후 1초 뒤
public void process() {
    // 이 메서드가 끝난 후 1초를 기다리고 다시 실행
}
```

```
시간 ->
|--작업(30ms)--|                    |--작업(30ms)--|                    |--작업(30ms)--|
0ms           30ms                 1030ms        1060ms                2060ms
              ^----1000ms 대기---->^              ^----1000ms 대기---->^
              종료 기준
```

**작업이 오래 걸리면?**

```
fixedDelay = 1000ms, 작업이 1500ms:

|------작업(1500ms)------|                    |------작업(1500ms)------|
0ms                     1500ms               2500ms                  4000ms
                        ^----1000ms 대기---->^
                        -> 전체 주기가 2500ms로 늘어남 (안전하지만 느려짐)
```

겹칠 위험이 없다. 우리 프로젝트의 OutboxScheduler가 이 방식을 사용한다.

### 2-3. fixedRate vs fixedDelay 비교

| 구분 | fixedRate | fixedDelay |
|------|-----------|------------|
| 기준 시점 | 이전 실행 **시작** | 이전 실행 **종료** |
| 주기 보장 | 일정한 주기 보장 (작업이 짧을 때) | 주기가 변동 가능 (작업 시간에 따라) |
| 겹침 위험 | 작업 > 주기이면 밀림 발생 | 없음 (항상 순차) |
| 적합한 경우 | **대기열 스케줄러** (일정 TPS 유지) | **Outbox 스케줄러** (처리 완료 보장) |

### 2-4. initialDelay

앱 시작 후 첫 실행까지 대기 시간.

```java
@Scheduled(fixedRate = 100, initialDelay = 5000)
// 앱 시작 5초 후 첫 실행, 이후 100ms마다
```

- Spring 컨텍스트 초기화 완료 후 실행되도록 보장
- Redis 연결 안정화 시간 확보
- 다른 빈들의 초기화가 완료된 후 스케줄러 시작

### 2-5. cron

Unix cron 표현식으로 특정 시각에 실행.

```java
@Scheduled(cron = "0 0 2 * * *")           // 매일 새벽 2시
@Scheduled(cron = "0 */5 * * * *")         // 5분마다
@Scheduled(cron = "0 0 9-18 * * MON-FRI")  // 평일 9~18시 매 정각
```

**cron 표현식 구조**

```
초  분  시  일  월  요일
0   0   2   *   *   *
│   │   │   │   │   └─ 요일 (0-7, MON-SUN)
│   │   │   │   └──── 월 (1-12)
│   │   │   └─────── 일 (1-31)
│   │   └────────── 시 (0-23)
│   └───────────── 분 (0-59)
└──────────────── 초 (0-59)
```

**특수 문자**

| 문자 | 의미 | 예시 |
|------|------|------|
| `*` | 매번 | `* * * * * *` (매초) |
| `*/N` | N마다 | `*/5 * * * * *` (5초마다) |
| `-` | 범위 | `0-30 * * * * *` (0~30초) |
| `,` | 나열 | `0 0 1,15 * * *` (1일, 15일 자정) |
| `?` | 미지정 (일/요일에만) | `0 0 0 ? * MON` (매주 월요일) |
| `L` | 마지막 (일/요일에만) | `0 0 0 L * *` (매월 마지막 날) |

**대기열 스케줄러에는 cron보다 fixedRate가 적합하다.** cron은 "특정 시각"에 실행하는 것이고, fixedRate는 "일정 간격"으로 실행하는 것이다. 밀리초 단위 주기가 필요한 대기열에는 fixedRate가 맞다.

cron이 적합한 경우:
- 만료된 대기열 데이터 정리 배치 (매일 새벽 3시)
- 대기열 통계 집계 (매시간 정각)

### 2-6. zone

cron의 타임존 지정.

```java
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
```

지정하지 않으면 서버의 기본 타임존 사용. 우리 프로젝트는 테스트 설정에서 `Asia/Seoul`을 사용한다.

---

## 3. 단일 스레드 문제와 해결

### 3-1. 기본 동작: 단일 스레드

Spring의 기본 TaskScheduler는 **스레드 1개**로 모든 `@Scheduled` 메서드를 실행한다. 여러 스케줄러가 있으면 하나가 지연될 때 나머지도 밀린다.

```
[단일 스레드 -- 문제 상황]
OutboxScheduler(10초 주기) + QueueScheduler(100ms 주기)가 같은 스레드:

시간 ->
|---Outbox(3초 소요)---|--Queue--|--Queue--|...
0s                    3s       3.1s     3.2s
                      ↑ Queue가 3초간 실행 못 함 -> 대기열이 3초간 멈춤
```

### 3-2. 해결: 스레드 풀 설정

**방법 1: SchedulingConfigurer 구현**

```java
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);               // 스레드 4개
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setErrorHandler(t ->
            log.error("스케줄러 실행 중 에러 발생", t)
        );
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}
```

**방법 2: application.yml 설정**

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 4
      thread-name-prefix: scheduler-
```

이렇게 하면 OutboxScheduler와 QueueScheduler가 **별도 스레드**에서 실행되어 서로 영향을 주지 않는다.

```
[스레드 풀(4개) -- 정상 상황]
Thread-1: |---Outbox(3초)---|                |---Outbox---|
Thread-2: |--Q--|--Q--|--Q--|--Q--|--Q--|--Q--|--Q--|--Q--|
           ↑ 서로 독립적으로 실행
```

### 3-3. 스레드 풀 크기 결정

- 스케줄러 수 + 1~2개 여유가 일반적
- OutboxScheduler(1개) + QueueScheduler(1개) = 최소 2개, 여유 포함 4개
- 너무 많이 잡으면 불필요한 리소스 소비

---

## 4. 예외 처리

### 4-1. 기본 동작

`@Scheduled` 메서드에서 예외가 발생하면 **해당 실행만 실패하고, 다음 주기에 정상 실행**된다. 스케줄러 자체가 멈추지는 않는다.

```java
@Scheduled(fixedRate = 100)
public void issueTokens() {
    try {
        Set<TypedTuple<String>> users = waitingQueueRepository.popFront(BATCH_SIZE);
        for (TypedTuple<String> user : users) {
            entryTokenRepository.issue(user.getValue());
        }
    } catch (Exception e) {
        log.error("토큰 발급 실패", e);
        // 예외를 삼켜서 스케줄러가 계속 동작하도록 보장
        // 다음 주기에 다시 실행됨
    }
}
```

### 4-2. 주의: 잡히지 않는 예외

try-catch 없이 예외가 던져지면 로그에 찍히고 다음 주기에 실행되긴 하지만, 에러 핸들링을 명시적으로 하는 것이 안전하다.

**글로벌 에러 핸들러 설정**

```java
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setErrorHandler(t -> {
            log.error("스케줄러 에러: {}", t.getMessage(), t);
            // 알림 전송 등 추가 처리
        });
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}
```

### 4-3. 매번 실패하는 경우

매 주기마다 예외가 발생하면 **로그만 쌓이고 대기열이 안 움직이는 상태**가 된다. 유저는 순번이 줄어들지 않는 것을 보고 이탈한다.

모니터링이 필수:
- 스케줄러 실행 시마다 마지막 실행 시각을 기록
- 일정 기간 미실행 시 알림 발송
- Micrometer `@Timed`로 실행 횟수/성공률 추적

---

## 5. 설정값 외부화

하드코딩 대신 application.yml에서 값을 주입받을 수 있다. **개발 규칙의 "환경 설정값을 코드에 하드코딩하지 말 것"을 준수.**

### 5-1. application.yml

```yaml
queue:
  scheduler:
    rate: 100           # ms
    initial-delay: 5000 # ms
    batch-size: 18
  token:
    ttl-seconds: 300
```

### 5-2. @Value로 주입

```java
@Component
public class QueueScheduler {

    private final int batchSize;
    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    public QueueScheduler(
            @Value("${queue.scheduler.batch-size}") int batchSize,
            WaitingQueueRepository waitingQueueRepository,
            EntryTokenRepository entryTokenRepository
    ) {
        this.batchSize = batchSize;
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
    }

    @Scheduled(
        fixedRateString = "${queue.scheduler.rate}",
        initialDelayString = "${queue.scheduler.initial-delay}"
    )
    public void issueTokens() {
        // batchSize 사용
    }
}
```

- `fixedRateString`, `fixedDelayString`, `initialDelayString`: 문자열로 받아서 프로퍼티 치환
- 환경별(로컬/dev/prod)로 다른 값 설정 가능
- 로컬에서는 배치 크기를 작게, 프로덕션에서는 크게 등 유연한 조정

### 5-3. @ConfigurationProperties로 주입

설정값이 많아지면 `@ConfigurationProperties`가 더 깔끔하다.

```java
@ConfigurationProperties(prefix = "queue.scheduler")
public record QueueSchedulerProperties(
    int rate,
    int initialDelay,
    int batchSize
) {}
```

```java
@Component
@EnableConfigurationProperties(QueueSchedulerProperties.class)
public class QueueScheduler {

    private final QueueSchedulerProperties properties;

    public QueueScheduler(QueueSchedulerProperties properties, ...) {
        this.properties = properties;
    }

    @Scheduled(
        fixedRateString = "${queue.scheduler.rate}",
        initialDelayString = "${queue.scheduler.initial-delay}"
    )
    public void issueTokens() {
        // properties.batchSize() 사용
    }
}
```

---

## 6. @Async와의 조합

`@Scheduled`는 기본적으로 동기 실행이다. `@Async`와 함께 쓰면 비동기로 실행할 수 있지만, **대기열 스케줄러에서는 비추천**이다.

```java
// 비추천 -- 동시에 여러 번 실행될 수 있음
@Async
@Scheduled(fixedRate = 100)
public void issueTokens() { ... }
```

`@Async`를 붙이면 매 주기마다 새 스레드에서 실행되므로, 이전 실행이 안 끝났어도 다음 실행이 시작된다. ZPOPMIN의 원자성 덕분에 중복 pop은 없지만, **토큰 발급량이 의도보다 많아질 수 있다.**

```
[fixedRate=100ms + @Async -- 위험]
Thread-1: |------작업(200ms)------|
Thread-2:           |------작업(200ms)------|
Thread-3:                     |------작업(200ms)------|
→ 100ms마다 새 스레드가 생성, 동시에 3개 작업이 실행
→ 배치 18명 x 3 = 54명에게 동시 토큰 발급 → 처리량 설계 무너짐
```

---

## 7. 멀티 인스턴스 환경

서버가 2대 이상이면 각 서버에서 스케줄러가 동시에 실행된다.

### 7-1. 문제

```
[서버 A] @Scheduled -> ZPOPMIN 18명 -> 토큰 발급
[서버 B] @Scheduled -> ZPOPMIN 18명 -> 토큰 발급
-> 동시에 36명에게 토큰 발급 -> 처리량 설계(175 TPS) 무너질 수 있음
```

ZPOPMIN이 atomic이므로 같은 유저를 두 번 꺼내는 일은 없다. 하지만 배치 크기 x 인스턴스 수만큼 동시에 발급된다.

### 7-2. 해결 방법

**방법 1: 분산 락 (Redis SETNX)**

```java
@Scheduled(fixedRateString = "${queue.scheduler.rate}")
public void issueTokens() {
    // 락 획득 시도
    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent("scheduler-lock", "server-1", Duration.ofMillis(200));

    if (Boolean.TRUE.equals(acquired)) {
        try {
            // 토큰 발급 로직
        } finally {
            redisTemplate.delete("scheduler-lock");
        }
    }
    // 락 획득 실패 -> 이번 주기는 건너뜀
}
```

**방법 2: ShedLock 라이브러리**

```java
@Scheduled(fixedRateString = "${queue.scheduler.rate}")
@SchedulerLock(name = "issueTokens", lockAtMostFor = "200ms")
public void issueTokens() {
    // 한 인스턴스에서만 실행됨
}
```

**방법 3: 배치 크기를 인스턴스 수로 나누기**

```yaml
# 2대 운영 시
queue:
  scheduler:
    batch-size: 9   # 18 / 2 = 9
```

단순하지만, 인스턴스 수가 동적으로 변하면 설정도 바꿔야 하는 단점이 있다.

### 7-3. 방법별 비교

| 방법 | 장점 | 단점 |
|------|------|------|
| 분산 락 (SETNX) | 정확한 제어, 추가 라이브러리 불필요 | 락 관리 코드 필요, 락 서버 장애 시 전체 멈춤 |
| ShedLock | 어노테이션 하나로 간편 | 라이브러리 의존성 추가 |
| 배치 크기 분할 | 구현 단순 | 인스턴스 수 변경 시 설정 변경 필요 |

---

## 8. 우리 프로젝트의 기존 사용 패턴

OutboxScheduler에서 이미 `@Scheduled`를 사용하고 있다.

```java
// OutboxScheduler.java
@Scheduled(fixedDelay = 10000)  // 발행 완료 후 10초 대기
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxEventRepository.findUnpublishedBefore(BATCH_LIMIT);
    for (OutboxEvent event : events) {
        try {
            eventPublisher.publish(event);
            event.markPublished();
        } catch (Exception e) {
            log.error("발행 실패: {}", event.getEventId(), e);
        }
    }
}
```

**OutboxScheduler vs QueueScheduler 비교**

| 구분 | OutboxScheduler | QueueScheduler (예상) |
|------|----------------|---------------------|
| 속성 | `fixedDelay = 10000` | `fixedRate = 100` |
| 이유 | 발행 완료 보장 후 다음 폴링 | 일정한 TPS 유지 |
| 배치 크기 | 100건 | 18명 |
| 데이터 소스 | DB (OutboxEvent 테이블) | Redis (Sorted Set) |
| 실패 처리 | 다음 주기에 재시도 (published=false 유지) | 다음 주기에 새 유저 꺼냄 (ZPOPMIN은 되돌릴 수 없음) |
| 트랜잭션 | JPA dirty checking으로 상태 변경 | Redis 원자적 연산 |

---

## 9. 대기열 스케줄러 구현 예시

위 내용을 종합한 대기열 스케줄러의 예상 구조:

```java
@Slf4j
@Component
public class QueueScheduler {

    private final int batchSize;
    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    public QueueScheduler(
            @Value("${queue.scheduler.batch-size}") int batchSize,
            WaitingQueueRepository waitingQueueRepository,
            EntryTokenRepository entryTokenRepository
    ) {
        this.batchSize = batchSize;
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
    }

    @Scheduled(
        fixedRateString = "${queue.scheduler.rate}",
        initialDelayString = "${queue.scheduler.initial-delay}"
    )
    public void issueTokens() {
        try {
            Set<ZSetOperations.TypedTuple<String>> users =
                waitingQueueRepository.popFront(batchSize);

            if (users.isEmpty()) {
                return;  // 대기열이 비어있으면 아무것도 안 함
            }

            for (ZSetOperations.TypedTuple<String> user : users) {
                String userId = user.getValue();
                entryTokenRepository.issue(userId);
                log.debug("토큰 발급: userId={}", userId);
            }

            log.info("토큰 발급 완료: {}명", users.size());
        } catch (Exception e) {
            log.error("토큰 발급 실패", e);
            // 다음 주기에 다시 실행됨
            // ZPOPMIN으로 이미 꺼낸 유저는 토큰을 못 받은 상태
            // -> 보상 로직 필요 여부 검토
        }
    }
}