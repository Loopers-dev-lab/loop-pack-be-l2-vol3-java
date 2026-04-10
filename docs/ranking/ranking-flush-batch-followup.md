# Ranking Flush Batch Follow-up

`ranking:all:{yyyyMMdd}` ZSET에 대해 이벤트마다 바로 `ZINCRBY`를 치는 구조를
`Kafka batch -> 메모리 집계 -> Redis flush` 구조로 바꾸기 위한 후속 설계 메모다.

핵심 목표는 두 가지다.

- Redis write 횟수를 줄여 hot key 부담과 RTT를 낮춘다.
- `flush -> ack` 원칙을 지키되, `EventHandled` 때문에 ranking delta가 유실되지 않게 한다.

## 지금까지 합의한 내용

### 1. ack 시점

ack 순서는 **반드시 `flush -> ack`** 로 둔다.

이유:

- `ack -> flush` 이면 ack 직후 Kafka 입장에서는 처리가 끝난 메시지다.
- 그 뒤 Redis flush가 실패하면 랭킹 점수는 유실된다.
- `flush -> ack` 이면 flush 성공 전 장애가 나더라도 Kafka 재전달로 복구를 시도할 수 있다.

즉, 이 구조는 기본적으로 **유실보다 중복 가능성을 감수하는 방향**이다.

처리 순서는 아래를 기준으로 잡는다.

```text
1. Kafka batch 수신
2. 각 메시지의 도메인 처리 수행
3. ranking delta를 batch 단위로 집계
4. Redis flush
5. flush 성공 후 ack
6. flush 실패 시 no ack
```

### 2. 메모리 집계 키

메모리 집계 키는 **`(rankingDate, productId)`** 로 둔다.

권장 구조:

```java
Map<LocalDate, Map<Long, Double>> deltaByDateAndProduct;
```

이유:

- Redis key가 날짜별(`ranking:all:yyyyMMdd`)로 나뉜다.
- 같은 `productId` 라도 날짜가 다르면 다른 ZSET에 들어간다.
- 하나의 Kafka batch 안에 날짜 경계를 넘는 이벤트가 섞일 수 있다.

예시:

```java
LocalDate rankingDate = occurredAt.atZone(zoneId).toLocalDate();
double delta = scoreOf(message);

deltaByDateAndProduct
    .computeIfAbsent(rankingDate, ignored -> new HashMap<>())
    .merge(productId, delta, Double::sum);
```

이 구조를 쓰면 같은 batch 안에서 동일 상품에 대한 여러 이벤트를 합산한 뒤
Redis에는 상품별 1회만 반영할 수 있다.

## 이번에 결정할 설계

### 3. flush 트리거 전략

이번 구현의 1차 선택은 **Kafka poll batch 경계에서만 flush** 다.

즉:

- `@KafkaListener(batch = true)` 로 받은 `messages` 1묶음을 처리한다.
- 그 묶음에서 ranking delta를 집계한다.
- 집계가 끝나면 바로 Redis flush 한다.
- flush 성공 시에만 `ack` 한다.

이번 단계에서 **시간 기반 flush / 별도 scheduler flush / 백그라운드 flush thread** 는 넣지 않는다.

선택 이유:

- 이미 Kafka batch listener가 자연스러운 flush 경계를 제공한다.
- `flush -> ack` 와 가장 잘 맞는다.
- 배치 경계가 명확해서 장애 복구 흐름이 단순하다.
- 현재 `MAX_POLL_RECORDS = 3000`, `FETCH_MAX_WAIT_MS = 5000` 이라 랭킹 API 특성상 지연 허용 범위 안에 있다.

정리하면 이번 버전의 기준은 아래다.

- **기본 flush 단위**: Kafka poll batch
- **목표**: 구현 복잡도 최소화 + write 감축 효과 확보
- **보류**: 시간 기반 flush, 전용 flush 워커, 다단계 버퍼링

추가 가드로는 나중에 아래 정도만 검토하면 된다.

- unique `(date, productId)` 수가 임계치를 넘으면 내부 chunk flush
- flush latency가 `max.poll.interval.ms` 에 근접하면 배치 크기 재조정

하지만 1차 구현에서는 과하다. 먼저 poll batch flush로 시작하는 게 맞다.

### 4. Redis flush 구현 방식

Redis flush는 **날짜별 key로 묶은 뒤 pipeline으로 `ZINCRBY` + `EXPIRE`** 한다.

구현 원칙:

- 메모리 집계 결과를 `Map<LocalDate, Map<Long, Double>>` 형태로 받는다.
- 날짜별로 Redis key를 계산한다.
- 각 상품 delta에 대해 `ZINCRBY` 1번씩 보낸다.
- `EXPIRE` 는 상품마다 걸지 않고, **flush에서 touched key마다 1번만** 건다.
- flush 실패 시 예외를 삼키지 않고 consumer까지 전파한다.

예시 흐름:

```text
date=2026-04-10
  product 1 -> +0.4
  product 2 -> +0.6

pipeline:
  ZINCRBY ranking:all:20260410 0.4 1
  ZINCRBY ranking:all:20260410 0.6 2
  EXPIRE   ranking:all:20260410 172800
```

권장 repository 방향:

```java
public interface RankingRepository {
    void flush(Map<LocalDate, Map<Long, Double>> deltaByDateAndProduct);
}
```

`incrementScore()` 를 유지하면 호출자에서 배치 최적화를 할 수 없으니,
flush 배치 관점의 메서드로 인터페이스를 올리는 편이 맞다.

### 5. 가장 중요한 경계: `EventHandled` 와 flush 배치

여기가 이번 재설계의 핵심이다.

현재 구조는 메시지 처리 트랜잭션 안에서:

1. metrics DB 반영
2. Redis ranking 반영
3. `EventHandled` 저장

혹은 배치화 시도 시:

1. metrics DB 반영
2. `EventHandled` 저장
3. 나중에 Redis flush

가 되기 쉽다.

문제는 2번 후 3번이 실패하면, **메시지는 이미 handled인데 ranking delta는 사라진다** 는 점이다.

그래서 ranking flush를 배치화하려면, ranking 반영도 **재시도 가능한 영속 상태** 로 남겨야 한다.

#### 채택 방향: `RankingDeltaPending` 를 둔다

메시지 1건 처리 트랜잭션 안에서 아래를 함께 커밋한다.

- 도메인 metrics 반영
- `EventHandled(eventId)` 저장
- `RankingDeltaPending(eventId, rankingDate, productId, delta, status=PENDING)` 저장

즉, 메시지 처리의 결과를 "Redis에 바로 반영" 하지 말고,
"나중에 flush해야 할 ranking delta" 로 DB에 남긴다.

그 다음 consumer batch 흐름은 아래처럼 간다.

```text
1. Kafka batch 수신
2. 각 메시지를 개별 처리
   - metrics 반영
   - EventHandled 저장
   - RankingDeltaPending 저장
3. 이번 batch의 eventId들에 대한 PENDING delta 조회
4. (date, productId) 단위로 메모리 집계
5. Redis flush
6. flush 성공 시 pending -> flushed 처리
7. ack
```

이렇게 하면:

- flush 전에 장애가 나도 pending delta가 남아 있다.
- 재전달된 메시지는 `EventHandled` 로 중복 도메인 반영을 막는다.
- 대신 pending delta는 다시 모아서 flush할 수 있다.

#### 정확성 모델

이 구조도 Redis까지 **완전한 exactly-once** 는 아니다.

남는 창구:

- Redis flush 성공
- 그런데 `pending -> flushed` DB 업데이트 전에 장애 발생

이 경우 재시도 시 중복 flush 가능성이 남는다.

하지만 이 구조는 아래를 만족한다.

- ranking delta 유실을 막는다.
- 중복 가능성은 특정 장애 창구로 한정된다.
- 현재 시스템 철학인 "유실보다 중복" 과 맞다.

즉 이번 단계의 명시적 선택은:

- **메시지 단위 정확성**: `EventHandled` 로 중복 방지
- **ranking flush 정확성**: pending delta 기반 at-least-once
- **우선순위**: 유실 방지 > 중복 0 보장

### 6. Consumer 예외 처리 원칙

현재 `ProductMetricsConsumer` 는 메시지별 예외를 잡고 로그만 남긴 뒤 마지막에 무조건 ack 한다.

flush 배치 구조에서는 이 방식이 맞지 않는다.

바꿔야 할 원칙:

- 메시지 처리 중 복구 불가능 예외가 나면 batch 전체를 실패로 본다.
- flush 실패 시도 당연히 batch 실패다.
- 실패 batch는 ack 하지 않는다.
- 예외는 container error handler가 재시도/재전달하게 둔다.

즉, consumer는 아래처럼 가야 한다.

```text
for batch:
  process messages
  aggregate pending delta
  flush redis
  mark flushed
  ack

if any step fails:
  throw
  no ack
```

`DataIntegrityViolationException` 만 따로 삼키는 현재 방식도 재검토 대상이다.
중복 이벤트 여부는 `EventHandled` 와 unique key로 판단하되,
consumer는 "실패를 숨기지 않는 방향" 이 더 맞다.

### 7. 운영 관점에서 볼 지표

필수 모니터링 지표는 아래 정도면 충분하다.

- Kafka consumer lag
- batch 처리 시간
- batch당 메시지 수
- batch당 unique `(date, productId)` 수
- Redis flush latency
- Redis pipeline size
- `RankingDeltaPending` 적체 건수
- flush 실패 횟수

알람 기준은 특히 두 군데가 중요하다.

- `batch 처리 시간 > max.poll.interval.ms` 근접
- `RankingDeltaPending` backlog 증가

### 8. 1차 구현안

이번 재설계의 1차 구현 범위는 아래로 자른다.

1. `ProductMetricsService` 에서 Redis 직접 쓰기를 제거한다.
2. 메시지 처리 트랜잭션에서 `RankingDeltaPending` 를 저장한다.
3. `ProductMetricsConsumer` 에서 batch eventId 기준 pending delta를 조회한다.
4. `(date, productId)` 로 메모리 집계한다.
5. `RankingRepository.flush(...)` 로 Redis pipeline flush 한다.
6. 성공 시 pending row를 flushed 처리하고 ack 한다.
7. 실패 시 예외를 올리고 no ack 한다.

이 정도면 hot key 완화 목적과 장애 복구 목적을 동시에 만족한다.

## 다음 시작 문장

다음 논의를 이어갈 때는 아래 문장으로 시작하면 된다.

```text
ranking-flush-batch-followup.md 기준으로 1차 구현안 클래스 분리부터 보자
```
