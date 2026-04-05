## Redis Sorted Set

### 왜 Sorted Set인가

대기열에서 순서를 보장하려면 **원자적으로 순서를 결정할 수 있는 자료구조**가 필요하다.

| 자료구조 | 순서 | 중복 |
|---|---|---|
| List | 있음 | 허용 |
| Set | 없음 | 불허 |
| Sorted Set | 있음 | 불허 |

Sorted Set은 각 member에 **score**라는 숫자값을 붙여서 저장하고, score 기준으로 자동 정렬된 상태를 유지한다.

```
member      score
-------     -----
userA       1
userB       2
userC       3
```

대기열에 적합한 이유:
- member = userId (누가 대기 중인지)
- score = 진입 순서 (먼저 진입할수록 낮은 값)
- score가 낮을수록 먼저 진입한 사람 → 선착순 자연스럽게 보장

---

### score 설계

score로 **Unix timestamp**를 사용하면 진입 시각 기준 선착순이 된다.

다만 동시에 수천 명이 진입하면 같은 timestamp가 나올 수 있다. 이때 Redis는 score가 같으면 member를 사전순으로 정렬한다. 엄밀한 선착순이 깨진다.

**해결 방법 비교**

| 방법 | 방식 | 트레이드오프 |
|---|---|---|
| 나노초 timestamp | 겹칠 확률을 극히 낮춤 | 완전히 없애진 못함, 그 수준의 오차는 허용 가능 |
| Redis INCR 시퀀스 | 원자적으로 1씩 증가, 절대 겹치지 않음 | 별도 키 관리 필요, 복잡도 증가 |

선택 기준은 **완벽한 선착순이 비즈니스적으로 필요한가**다.
- 티켓팅, 한정판처럼 1등과 2등의 차이가 명확한 서비스 → INCR
- 일반 주문 대기열처럼 수백 명 중 몇 명의 순서가 뒤바뀌어도 무방한 서비스 → 나노초 timestamp로 충분

---

### 핵심 명령어

**ZADD — 대기열 진입**

```
ZADD waiting-queue <score> <member>
ZADD waiting-queue 1743385200000 "userA"
```

이미 있는 member를 다시 ZADD하면 score만 업데이트된다. 중복 진입이 자연스럽게 방지된다.

**ZRANK — 내 순번 조회**

```
ZRANK waiting-queue "userA"
→ 0  (0부터 시작, +1 하면 몇 번째인지)
```

**ZCARD — 전체 대기 인원**

```
ZCARD waiting-queue
→ 1500
```

순번과 조합하면 예상 대기 시간을 계산할 수 있다.

```
예상 대기 시간 = (ZRANK + 1) / 처리 TPS
```

**ZPOPMIN — 앞에서부터 꺼내기**

```
ZPOPMIN waiting-queue 18
→ score가 가장 낮은 18명을 꺼내서 반환 + 대기열에서 제거
```

스케줄러가 100ms마다 호출해서 토큰을 발급한다.

---

### 왜 순서가 보장되는가

두 가지가 맞물려서 보장된다.

**1. score 기반 정렬**

Sorted Set은 ZADD할 때마다 score 기준 올바른 위치에 삽입되어 항상 정렬된 상태를 유지한다.
ZPOPMIN은 항상 score가 가장 낮은 것부터 꺼내므로, 먼저 진입한 사람이 먼저 나온다.

**2. Redis 단일 스레드 모델**

Redis는 명령어를 단일 스레드로 순차 처리한다. 여러 서버가 동시에 ZADD를 보내도 Redis 안에서는 하나씩 처리된다.

```
서버 A의 ZADD ──┐
서버 B의 ZADD ──┤→ Redis가 하나씩 순차 처리 → 순서 꼬임 없음
서버 C의 ZADD ──┘
```

동시에 요청이 와도 Redis 안에서는 동시성 문제가 없다. 이것이 "원자적으로 순서를 결정"한다는 의미다.

---

### 중복 방지

별도 처리 없이 Sorted Set 구조 자체로 보장된다.

Sorted Set은 member가 unique하다. 같은 userId로 ZADD를 두 번 하면 추가가 아니라 score 업데이트가 된다.

```
ZADD waiting-queue 1 "userA"  → 진입 (1번째)
ZADD waiting-queue 5 "userA"  → score만 5로 업데이트 (줄 뒤로 밀림)
```

재진입하면 순번이 뒤로 밀리는 부작용이 있으므로, 애플리케이션에서 이미 대기 중인지 체크 후 ZADD하는 것이 안전하다.
