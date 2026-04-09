# Ranking Flush Batch Follow-up

랭킹 flush 배치 설계를 이어서 논의하기 위한 메모.

## 지금까지 합의한 내용

### 1. ack 시점

배치 컨슈머의 ack 시점은 **반드시 `flush -> ack`** 순서가 맞다.

이유:

- `ack -> flush` 이면 ack 직후 장애 시 Kafka 는 이미 처리 완료로 간주하고, Redis 반영은 유실될 수 있다.
- `flush -> ack` 이면 flush 성공 후 ack 전에 장애가 나더라도 Kafka 재전달로 복구 가능하다.
- 현재 구조는 outbox + Kafka + 중복 방지(`EventHandled`)를 전제로 하므로, **유실보다 중복 가능성을 택하는 구조**가 맞다.

정리:

```text
1. Kafka batch 수신
2. 메시지별 점수 계산
3. 메모리 delta 집계
4. Redis flush
5. flush 성공 후 ack
6. flush 실패 시 no ack / 예외 / 재처리
```

### 2. 메모리 집계 자료구조

메모리 집계 키는 **`(rankingDate, productId)`** 로 잡는다.

권장 자료구조:

```java
Map<LocalDate, Map<Long, Double>> deltaByDateAndProduct;
```

이유:

- Redis 랭킹 key 가 날짜별(`ranking:all:yyyyMMdd`)로 분리되어 있음
- 같은 productId 라도 날짜가 다르면 다른 ZSET 으로 가야 함
- 한 Kafka batch 안에도 자정 경계 이벤트가 섞일 수 있음

집계 예시:

```java
LocalDate rankingDate = occurredAt.atZone(zoneId).toLocalDate();
double delta = scoreOf(message);

deltaByDateAndProduct
    .computeIfAbsent(rankingDate, ignored -> new HashMap<>())
    .merge(productId, delta, Double::sum);
```

의미:

- 이벤트 수만큼 Redis 에 쓰지 않고
- batch 내 `unique product 수`만큼만 flush 하게 된다

## 다음에 이어서 볼 주제

### 3. flush 트리거 전략

비교할 대상:

- Kafka batch 끝 flush
- 시간 기반 flush
- 개수 기반 flush
- 혼합형 flush

보고 싶은 포인트:

- 현재 프로젝트에서 가장 자연스러운 기본값은 무엇인가
- 실시간성(latency) 과 처리량(throughput) 사이에서 어느 지점을 택할 것인가
- batch listener 구조와 가장 충돌이 적은 방식은 무엇인가

### 4. Redis flush 구현 방식

논의할 내용:

- `ZINCRBY` 다건을 Spring Data Redis 에서 어떻게 pipeline 으로 보낼지
- 날짜별 key 별로 flush 를 어떻게 쪼갤지
- `expire` 를 어디서 같이 처리할지
- flush 실패 시 예외 전파와 로그 기준을 어떻게 둘지

### 5. 정합성 경계

다음에 더 명확히 볼 것:

- `EventHandled` 와 flush 배치의 책임 경계
- 중복 방지는 메시지 단위인지 batch 단위인지
- Redis 는 파생 저장소이므로 어디까지 정확성을 요구할지

### 6. 운영 포인트

추가로 볼 것:

- batch 크기와 unique product 수의 관계
- 메모리 사용량 추정
- flush latency 관측 지표
- Redis RTT, pipeline size, consumer lag 모니터링 포인트

## 다음 시작 문장

다음에 이어서 시작할 때는 아래 문장으로 열면 된다.

```text
ranking-flush-batch-followup.md 이어서 3번 flush 트리거 전략부터 보자
```
