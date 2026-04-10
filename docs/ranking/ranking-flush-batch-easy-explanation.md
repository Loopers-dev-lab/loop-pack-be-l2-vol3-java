# Ranking Flush Batch 쉬운 설명

## 1. 지금 왜 바꾸려는가

지금은 Kafka 메시지 1개가 오면 Redis에 바로 점수를 올린다.

```text
메시지 1개 처리
-> DB 반영
-> Redis 반영
-> EventHandled 저장
```

이 방식은 단순하지만, 이벤트가 많아지면 Redis에 너무 자주 쓰게 된다.

예시:

```text
상품 1 조회   +0.1
상품 1 조회   +0.1
상품 1 좋아요 +0.2
상품 2 구매   +0.6
```

현재 방식:

- Redis 4번 쓰기

우리가 원하는 방식:

- 상품 1 점수는 `0.1 + 0.1 + 0.2 = 0.4` 로 합친다
- 상품 2 점수는 `0.6` 으로 둔다
- Redis에는 상품별로 한 번씩만 반영한다

즉:

- Redis 2번 쓰기

## 2. 바꾸고 싶은 목표

핵심은 이거다.

```text
메시지 여러 개를 한 번에 받음
-> 메모리에서 상품별 점수를 합침
-> Redis에 한 번에 반영
```

그림으로 보면:

```text
[현재]
Kafka batch
  |- 상품1 조회 +0.1 -> Redis 1번
  |- 상품1 조회 +0.1 -> Redis 1번
  |- 상품1 좋아요 +0.2 -> Redis 1번
  |- 상품2 구매 +0.6 -> Redis 1번

결과: Redis 4번 쓰기
```

```text
[개선]
Kafka batch
  |- 상품1 조회 +0.1
  |- 상품1 조회 +0.1
  |- 상품1 좋아요 +0.2
  |- 상품2 구매 +0.6

메모리 집계
  |- 상품1 = +0.4
  |- 상품2 = +0.6

Redis flush
  |- 상품1 +0.4
  |- 상품2 +0.6

결과: Redis 2번 쓰기
```

## 3. 그런데 왜 그냥 바꾸면 안 되는가

문제는 `EventHandled` 다.

`EventHandled` 는 쉽게 말해:

- "이 메시지는 이미 처리했다"

라는 기록이다.

만약 우리가 이렇게 처리하면:

```text
1. DB 처리 성공
2. EventHandled 저장 성공
3. Redis flush 실패
```

시스템은 이미:

- "이 메시지는 처리 끝" 이라고 기억한다

그래서 Kafka가 같은 메시지를 다시 보내도:

- 중복 메시지라고 보고 건너뛴다

그런데 실제로는:

- Redis 점수 반영이 실패했다

즉, 랭킹 점수가 사라진다.

이게 "delta 유실" 이다.

## 4. 문제 상황 그림

```text
[잘못된 흐름]

Kafka 메시지
   |
   v
도메인 DB 반영
   |
   v
EventHandled 저장
   |
   v
Redis flush 실패
   |
   v
ack 안 하거나 재전달 발생
   |
   v
다시 읽었더니 EventHandled 가 이미 있음
   |
   v
"이미 처리된 메시지네" 하고 스킵
   |
   v
결과: Redis 랭킹 점수는 빠짐
```

## 5. 그래서 중간 저장소가 하나 더 필요하다

그래서 `RankingDeltaPending` 같은 중간 테이블을 두자는 것이다.

이건 쉽게 말해:

- "이 메시지의 랭킹 점수는 아직 Redis에 반영 대기 중"

이라는 기록이다.

역할을 분리하면:

- `EventHandled` = 메시지를 읽고 도메인 처리는 끝남
- `RankingDeltaPending` = 랭킹 점수는 아직 Redis 반영 전일 수 있음

## 6. 개선 구조 그림

```text
Kafka batch
   |
   v
메시지 1개씩 처리
   |
   +--> 도메인 DB 반영
   |
   +--> EventHandled 저장
   |
   +--> RankingDeltaPending 저장
   |
   v
batch 안의 pending 점수 모으기
   |
   v
(날짜, 상품) 기준으로 합치기
   |
   v
Redis flush
   |
   +--> 성공: pending 완료 처리
   |
   +--> 실패: pending 그대로 남김
   |
   v
flush 성공했을 때만 ack
```

## 7. 이 구조의 장점

장점은 단순하다.

- Redis flush 전에 죽어도 점수 정보가 DB에 남아 있다
- 나중에 다시 flush 시도할 수 있다
- 랭킹 점수 유실을 막을 수 있다
- Redis write 횟수도 줄일 수 있다

## 8. 한 줄 요약

배치 flush를 안전하게 하려면:

- "메시지를 처리했다"
- "Redis에 랭킹을 반영했다"

이 둘을 같은 의미로 보면 안 된다.

둘 사이에 `RankingDeltaPending` 같은 대기 저장소를 둬야 안전하다.
