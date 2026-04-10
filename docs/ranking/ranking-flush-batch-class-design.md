# Ranking Flush Batch 클래스 분리

## 한 줄 구조

구조를 한 줄로 줄이면 이렇다.

```text
Consumer
-> 메시지 처리 서비스
-> Pending 조회
-> 배치 집계
-> Redis flush
-> Pending 완료 처리
-> ack
```

## 역할 분리

### 1. `ProductMetricsConsumer`

역할:

- Kafka batch를 받는다
- 메시지를 하나씩 서비스에 넘긴다
- 이번 batch의 `eventId` 목록을 모은다
- pending delta를 조회한다
- 집계 후 Redis flush 한다
- flush 성공 시 `FLUSHED` 처리한다
- 마지막에 ack 한다

즉, consumer는 **배치 orchestration 책임**만 가진다.

그림:

```text
Kafka batch 수신
   |
   v
message loop
   |
   v
processedEventIds 수집
   |
   v
pending 조회
   |
   v
집계
   |
   v
Redis flush
   |
   v
pending 완료 처리
   |
   v
ack
```

### 2. `ProductMetricsService`

역할:

- 메시지 1건의 도메인 처리
- 중복 이벤트인지 확인
- 상품 metrics DB 반영
- `RankingDeltaPending` 저장
- `EventHandled` 저장

즉, service는 **메시지 1건의 트랜잭션 경계**를 가진다.

중요:

- 여기서는 Redis를 직접 건드리지 않는다
- Redis는 consumer batch 단계에서만 반영한다

그림:

```text
메시지 1건
   |
   +--> 중복 확인
   |
   +--> metrics 반영
   |
   +--> RankingDeltaPending 저장
   |
   +--> EventHandled 저장
```

### 3. `RankingDeltaPending`

역할:

- "이 메시지의 ranking 점수는 아직 Redis에 반영 대기 중" 이라는 상태 저장

필드:

- `eventId`
- `rankingDate`
- `productId`
- `delta`
- `status`

상태:

- `PENDING`
- `FLUSHED`

즉, 이 테이블은 Redis flush와 Kafka 재시도 사이를 연결하는 **안전장치**다.

### 4. `RankingDeltaPendingRepository`

역할:

- pending row 저장
- 특정 eventId 목록의 pending row 조회
- flush 성공 후 상태 변경

필요 메서드:

```java
save(...)
findPendingByEventIds(...)
markAsFlushed(...)
```

### 5. `RankingRepository`

역할:

- Redis ZSET에 실제 반영

중요:

- 더 이상 `incrementScore()` 같은 "메시지 1건 단위 API" 가 아니라
- `flush(Map<LocalDate, Map<Long, Double>>)` 같은 "배치 반영 API" 여야 한다

즉, repository는 "점수 계산" 이 아니라
**이미 계산된 집계 결과를 Redis에 쓰는 책임**만 가진다.

### 6. `RankingRepositoryImpl`

역할:

- 날짜별 key 계산
- pipeline `ZINCRBY`
- touched key에 `EXPIRE`

그림:

```text
(date, productId) -> delta
   |
   v
date별 key 생성
   |
   v
pipeline:
  ZINCRBY
  ZINCRBY
  ...
  EXPIRE
```

## 책임 경계 요약

쉽게 말하면:

- `Consumer` = 배치 진행자
- `Service` = 메시지 1건 처리자
- `Pending` = 아직 Redis에 안 쓴 점수 보관소
- `RankingRepository` = Redis writer

이렇게 보면 된다.

## 왜 이 분리가 좋은가

장점:

- 책임이 섞이지 않는다
- Redis flush 실패 시 어디서 멈춰야 하는지 명확하다
- Kafka ack 시점이 분명해진다
- 테스트를 나눠서 쓰기 쉽다

테스트도 자연스럽게 나뉜다.

- `ProductMetricsService` 테스트
  - 중복 이벤트면 pending이 추가되지 않는지
  - event type별 delta가 맞는지
- `RankingRepositoryImpl` 테스트
  - flush 시 Redis 점수가 누적되는지
  - TTL이 걸리는지
- `ProductMetricsConsumer` 테스트
  - batch 집계 후 flush 되는지
  - flush 후 pending이 `FLUSHED` 로 바뀌는지

## 다음 리팩터링 포인트

현재 구조에서 다음으로 손볼 만한 부분은 두 가지다.

### 1. 집계 로직을 consumer 밖으로 빼기

지금 consumer가 직접:

- pending 조회
- `(date, productId)` 집계
- flush
- mark flushed

를 다 하고 있다.

이건 동작은 맞지만 consumer가 조금 무거워진다.

그래서 나중에는 아래 같은 배치 서비스로 빼는 게 더 깔끔하다.

```java
RankingFlushBatchService.flushProcessedEvents(List<String> eventIds)
```

그러면 consumer는 이렇게 줄어든다.

```text
메시지 처리
-> flush batch service 호출
-> ack
```

### 2. `ProductMetricsService.handle()` 결과를 더 명확히 만들기

지금은 `void` 라서 consumer가
"이 메시지가 실제 처리됐는지, 중복이라 스킵됐는지" 를 바로 알기 어렵다.

나중에는 이런 식도 가능하다.

```java
public record HandleResult(String eventId, boolean processed) {}
```

그러면 consumer는 `processed=true` 인 eventId만 모을 수 있다.

다만 1차 구현에서는 꼭 필요한 변경은 아니다.

## 최종 그림

```text
ProductMetricsConsumer
   |
   +--> ProductMetricsService.handle(message)
   |       |
   |       +--> metrics DB 반영
   |       +--> RankingDeltaPending 저장
   |       +--> EventHandled 저장
   |
   +--> RankingDeltaPendingRepository.findPendingByEventIds(...)
   |
   +--> batch 집계
   |
   +--> RankingRepository.flush(...)
   |
   +--> RankingDeltaPendingRepository.markAsFlushed(...)
   |
   +--> ack
```

이 구조로 보면 설계가 어렵지 않다.

- 메시지 처리와
- Redis 반영을

중간의 `Pending` 으로 분리한 것이다.
