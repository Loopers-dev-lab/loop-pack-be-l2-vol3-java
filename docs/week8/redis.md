# Round 8 학습 정리 - 대기열 시스템과 Redis

---

## 1. 대기열, 왜 필요한가?

### 1-1. 문제의 본질: 유입 속도 > 처리 속도

시스템에는 처리할 수 있는 한계가 있다. DB 커넥션 풀, 스레드 풀, 외부 PG사 API 호출 제한 등 모든 자원은 유한하다.

평소에는 문제가 없다. 초당 100건 들어오고, 시스템이 초당 250건 처리할 수 있으면 여유가 있다. 하지만 블랙 프라이데이처럼 초당 10,000건이 몰리는 순간, 시스템은 자신이 처리할 수 있는 양의 40배를 동시에 받게 된다.

```
평소:    유입 100 TPS  <  처리 250 TPS  →  여유 있음
행사:    유입 10,000 TPS  >>  처리 250 TPS  →  시스템 붕괴
```

### 1-2. 대기열이 없으면 벌어지는 일

대기열 없이 10,000건의 요청이 동시에 주문 API에 도달하면 다음과 같은 연쇄 장애가 발생한다.

**1단계: 자원 고갈**
```
10,000 요청 → POST /orders
  → DB 커넥션 풀(50개) 즉시 소진
  → 나머지 9,950 요청은 커넥션 대기
  → HikariCP 커넥션 타임아웃(30초) 동안 스레드가 블로킹
  → 톰캣 스레드 풀(200개)도 순식간에 고갈
```

**2단계: 응답 지연 → 타임아웃**
```
커넥션을 잡은 50개 요청도 DB 부하로 인해 느려짐
  → 평소 200ms 걸리던 쿼리가 5초 이상
  → 클라이언트 타임아웃 발생
  → 유저에게는 빈 화면, 로딩 스피너만 돌아감
```

**3단계: 재시도 폭풍 (Retry Storm)**
```
유저: "왜 안 되지?" → 새로고침
  → 이미 처리 중인 요청 + 새 요청이 중첩
  → 트래픽이 10,000 → 20,000 → 30,000으로 증폭
  → 시스템이 완전히 멈춤
```

**4단계: 전체 서비스 장애 (Cascading Failure)**
```
주문 API가 점유한 커넥션이 반환되지 않음
  → 같은 DB를 쓰는 상품 조회, 회원 API도 커넥션 대기
  → 주문뿐 아니라 전체 서비스가 멈춤
  → 장바구니도 안 열리고, 로그인도 안 됨
```

핵심 문제는 **공정성의 부재**다. 10,000명 중 누가 성공하는지는 순전히 운이다. 먼저 요청한 유저가 성공하는 것이 아니라, 운 좋게 커넥션을 잡은 유저만 성공한다.

### 1-3. 스케일업/스케일아웃으로 해결할 수 없는 이유

"서버를 늘리면 되지 않나?"라는 질문이 자연스럽다. 하지만 한계가 있다.

| 전략 | 한계 |
|------|------|
| 서버 스케일아웃 (10대 → 100대) | DB와 PG는 서버 수에 비례해서 확장되지 않는다. 커넥션 풀의 총량은 DB가 감당할 수 있는 최대 연결 수에 묶여 있다 |
| DB 스케일업 (더 큰 장비) | 비용 대비 효과가 급격히 떨어진다. 2배 비싼 장비가 2배 성능을 내지 않는다 |
| 오토스케일링 | 트래픽 피크가 극단적으로 짧고 높은 경우(행사 시작 직후 10초), 오토스케일링이 반응하기 전에 이미 터진다. EC2 인스턴스 기동에 수십 초~수 분 소요 |
| Read Replica 추가 | 주문은 **쓰기** 작업이다. Read Replica는 읽기 부하만 분산한다 |

결론: 서버를 아무리 늘려도 **DB와 PG라는 병목**은 남는다. 유입량 자체를 조절하는 수밖에 없다.

### 1-4. 대기열의 역할: Back-pressure 구현

대기열은 **유입 속도와 처리 속도 사이의 완충 장치**다.

```
[대기열 없이]
  10,000 요청 → 주문 API → DB (250 TPS) → 💥 붕괴

[대기열 있으면]
  10,000 요청 → 대기열(Redis) → 스케줄러(175 TPS로 조절) → 주문 API → DB → ✅ 안정
```

이 개념을 **Back-pressure(배압)**라고 한다. 하류 시스템(DB, PG)이 감당할 수 있는 속도만큼만 상류(유저 요청)를 흘려보내는 것이다.

물리학에서 배관의 압력을 제어하는 것과 같다. 수도꼭지(유저)에서 물이 쏟아지더라도, 밸브(스케줄러)가 파이프(DB)가 감당할 수 있는 양만 흘려보낸다.

대기열이 제공하는 가치:

| 가치 | 설명 |
|------|------|
| 시스템 보호 | 처리량 이상의 요청이 DB에 도달하지 못하게 차단 |
| 공정성 보장 | 먼저 요청한 유저가 먼저 처리됨 (FIFO) |
| 유저 경험 | "512번째입니다, 약 3분 대기" → 유저가 기다릴 수 있음 |
| 이탈 방지 | 순번이 보이면 새로고침하지 않음 → 재시도 폭풍 차단 |
| 피크 평탄화 | 10초 동안 10,000건 → 57초 동안 175건/초로 분산 |

---

## 2. 거부할 것인가, 기다리게 할 것인가 — Rate Limiting vs Queuing

트래픽이 한계를 넘을 때, 선택지는 크게 두 가지다.

### 2-1. Rate Limiting — 초과 요청을 거부

시스템이 처리할 수 있는 한도를 정해두고, 초과하는 요청은 **거부(429 Too Many Requests)**한다.

```
[유저] → POST /orders
       → Rate Limiter: "초당 250건 초과"
       → 429 Too Many Requests
       → "잠시 후 다시 시도해주세요"
```

**주요 알고리즘**

| 알고리즘 | 원리 | 특징 |
|----------|------|------|
| **Fixed Window** | 고정 시간 창(1초) 내 요청 수 제한 | 단순하지만 창 경계에서 2배 트래픽 허용 가능 |
| **Sliding Window** | 시간 창이 연속적으로 이동 | Fixed Window의 경계 문제 해결, 메모리 더 사용 |
| **Token Bucket** | 일정 속도로 토큰이 충전, 요청마다 토큰 소비 | 버스트 허용 가능, AWS API Gateway 등에서 사용 |
| **Leaky Bucket** | 요청을 버킷에 넣고 일정 속도로 흘려보냄 | 출력이 항상 일정, 버스트 불허 |

**적합한 상황**
- API 보호: 특정 유저/IP가 과도하게 호출하는 것 방지
- 봇 차단: 비정상적인 요청 패턴 차단
- 일상적 부하 제어: 평소 트래픽의 점진적 증가에 대응
- DDoS 방어: 악의적 대량 요청 차단

**문제점: 블랙 프라이데이에서는 부적합**

블랙 프라이데이에 "나중에 다시 시도하세요"를 반환하면:
- 유저는 떠나거나 더 세게 새로고침한다
- 재시도 폭풍이 발생한다
- "운 좋은 사람만 성공"하므로 공정성이 없다
- 매출 손실이 발생한다 (기다릴 의사가 있는 유저를 내보내는 것)

### 2-2. Queuing — 초과 요청을 보관

초과 요청을 거부하지 않고 **대기열에 보관**한다. 유저에게는 순번을 알려주고, 시스템이 감당할 수 있는 속도로 순차 처리한다.

```
[유저] → POST /queue/enter
       → 대기열 진입: "현재 512번째, 약 3분 대기"
       → 순번 조회 반복 (polling)
       → 내 차례 → 토큰 발급
       → POST /orders (토큰으로 진입)
```

**적합한 상황**
- 행사 트래픽: 블랙 프라이데이, 한정판 세일 등
- 유저가 기다릴 의사가 있는 경우: 콘서트 티켓, 한정 상품 등
- 공정성이 중요한 경우: 먼저 온 유저가 먼저 처리되어야 할 때

### 2-3. 비교 정리

| 구분 | Rate Limiting | Queuing (대기열) |
|------|---------------|-----------------|
| 초과 요청 처리 | **거부** (429) | **보관** (대기열에 적재) |
| 유저 경험 | "나중에 다시 시도하세요" | "512번째입니다, 약 3분 대기" |
| 유저 반응 | 새로고침 → 재시도 폭풍 | 기다림 → 순서대로 처리 |
| 공정성 | 없음 (운 좋은 사람이 성공) | 있음 (먼저 온 사람이 먼저) |
| 구현 난이도 | 낮음 | 높음 (순번 관리, 토큰, 스케줄러 필요) |
| 인프라 비용 | 낮음 | Redis 등 추가 인프라 필요 |

**양자택일이 아니다**

실무에서는 두 전략을 조합한다:
1. **1차 방어: Rate Limiting** — 봇, 비정상 요청을 먼저 걸러냄
2. **2차 방어: Queuing** — 정상 유저를 대기열에 진입시킴
3. **3차 방어: 대기열 최대 인원 제한** — 대기열 자체에도 상한을 둠 (예: 최대 50,000명)

```
[요청] → Rate Limiter (봇/비정상 차단)
       → 대기열 인원 체크 (50,000명 초과 시 거부)
       → 대기열 진입
       → 스케줄러가 순차 처리
```

판단 기준: **"유저가 이 결과를 기다릴 의사가 있는가?"**
- 있다 → Queuing (콘서트 티켓, 한정판 상품)
- 없다 → Rate Limiting (일반 API 호출, 검색)

---

## 3. 대기열을 구현하는 기술 선택지

Redis만이 대기열을 구현할 수 있는 것은 아니다. 각 기술의 특성과 트레이드오프를 이해하면, 왜 Redis Sorted Set이 이 시나리오에 적합한지가 명확해진다.

### 3-1. RDBMS (MySQL) 기반 대기열

DB 테이블에 대기열을 구현하는 방식이다.

```sql
CREATE TABLE waiting_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    entered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status ENUM('WAITING', 'TOKEN_ISSUED') DEFAULT 'WAITING',
    INDEX idx_status_entered (status, entered_at)
);

-- 대기열 진입
INSERT INTO waiting_queue (user_id) VALUES (123);

-- 내 순번 조회
SELECT COUNT(*) FROM waiting_queue
WHERE status = 'WAITING' AND entered_at < (
    SELECT entered_at FROM waiting_queue WHERE user_id = 123
);

-- 앞에서 N명 꺼내기
SELECT * FROM waiting_queue
WHERE status = 'WAITING'
ORDER BY entered_at ASC
LIMIT 18
FOR UPDATE;
```

| 장점 | 단점 |
|------|------|
| 별도 인프라 불필요 (기존 DB 사용) | 순번 조회마다 COUNT 쿼리 → 대기 인원이 많으면 느려짐 |
| 트랜잭션 보장 | Polling 부하가 DB에 직접 전달됨 |
| 영속성 보장 (서버 재시작해도 유지) | 락 경합: FOR UPDATE로 꺼낼 때 동시성 문제 |
| | 초당 5,000건 순번 조회를 DB가 감당하기 어려움 |

**결론**: 대기열의 가장 큰 부하인 "순번 조회"가 DB에 직접 가해진다. 대기열은 시스템을 보호하기 위한 것인데, 대기열 자체가 DB 부하를 유발하면 본말전도다.

### 3-2. Kafka 기반 대기열

Kafka의 토픽을 대기열로 활용하는 방식이다. Round 7에서 쿠폰 발급에 사용한 방식과 유사하다.

```
[유저] → Producer: send("order-queue", userId)
       → Consumer: poll() → 순차 처리
```

| 장점 | 단점 |
|------|------|
| 메시지 유실 방지 (디스크 영속화) | **순번 조회 불가**: Kafka는 "내가 몇 번째인지" 조회하는 기능이 없다 |
| 높은 처리량 (초당 수십만 건) | 실시간 피드백 불가: fire & forget 방식 |
| 이미 인프라가 있음 (Round 7) | 파티션 내에서만 순서 보장 → 전체 순서 보장이 어려움 |
| Consumer 수로 처리량 조절 가능 | 대기열 이탈(취소) 처리가 어려움 |

**결론**: Kafka는 "요청을 넣고 나중에 결과를 확인"하는 fire & forget에 적합하다. Round 7의 쿠폰 발급이 정확히 이 패턴이다. 하지만 주문 대기열처럼 "내 순번을 실시간으로 확인"해야 하는 경우에는 부적합하다.

**Kafka 버퍼링(R7 쿠폰)과 대기열 시스템(R8 주문)의 차이**

| 구분 | Kafka 버퍼링 (R7 쿠폰) | 대기열 시스템 (R8 주문) |
|------|------------------------|----------------------|
| 유저 경험 | 요청 후 나중에 결과 확인 | 화면에서 순번을 보며 대기 |
| 결과 전달 | 비동기 (polling으로 결과 조회) | 입장 토큰 발급 → 즉시 주문 가능 |
| 핵심 관심사 | 메시지 유실 방지, 멱등 처리 | 공정한 순서, 실시간 피드백, 토큰 만료 |
| 유저 인지 | "신청 완료, 결과는 나중에" | "현재 512번째, 예상 대기 3분" |

### 3-3. 인메모리 큐 (Java ConcurrentLinkedQueue 등)

애플리케이션 메모리에 큐를 두는 방식이다.

```java
private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

// 진입
queue.offer(userId);

// 순번 (비효율적)
int position = 0;
for (String id : queue) {
    if (id.equals(userId)) break;
    position++;
}
```

| 장점 | 단점 |
|------|------|
| 추가 인프라 불필요 | 서버 재시작 시 대기열 소실 |
| 지연 시간 최소 (네트워크 없음) | **멀티 인스턴스 환경에서 공유 불가**: 서버 A의 큐와 서버 B의 큐가 별개 |
| 구현 단순 | 순번 조회가 O(N) — 10,000명이면 매번 10,000개 순회 |
| | 메모리 제한: 대기 인원이 많으면 OOM 위험 |

**결론**: 단일 서버 + 소규모 트래픽에서만 사용 가능. 프로덕션 환경(멀티 인스턴스)에서는 사용 불가.

### 3-4. Redis Sorted Set 기반 대기열

Redis의 Sorted Set을 활용하는 방식이다.

```
ZADD  waiting-queue  {timestamp}  {userId}   -- 대기열 진입
ZRANK waiting-queue  {userId}                 -- 내 순번 조회 (O(log N))
ZCARD waiting-queue                           -- 전체 대기 인원 (O(1))
ZPOPMIN waiting-queue {N}                     -- 앞에서 N명 꺼내기 (atomic)
```

| 장점 | 단점 |
|------|------|
| 인메모리 → 읽기/쓰기 μs 단위 | 영속성 제한: Redis 장애 시 대기열 소실 가능 (RDB/AOF로 완화) |
| 순서 보장: score(timestamp) 기반 자동 정렬 | 추가 인프라 필요 (하지만 우리 프로젝트는 이미 있음) |
| 원자적 연산: ZADD, ZPOPMIN 등이 atomic | 메모리 제한: 대기 인원에 비례해 메모리 사용 |
| 중복 방지: Set 특성으로 동일 userId 자동 거부 | |
| 순번 조회 O(log N): 10,000명이어도 ~14번 비교 | |
| TTL 지원: 입장 토큰 만료를 자연스럽게 처리 | |
| 멀티 인스턴스 공유: 중앙 Redis에 모든 서버가 접근 | |

### 3-5. 기술 선택 비교 요약

| 요구사항 | MySQL | Kafka | 인메모리 큐 | Redis Sorted Set |
|----------|-------|-------|------------|-----------------|
| 순번 조회 성능 | X (COUNT 쿼리) | X (불가) | X (O(N)) | O (O(log N)) |
| 순서 보장 | O | 부분적 | O | O |
| 중복 진입 방지 | O (UNIQUE) | X (별도 처리) | X (별도 처리) | O (Set 특성) |
| 멀티 인스턴스 공유 | O | O | X | O |
| 원자적 연산 | 락 필요 | - | CAS 필요 | O (내장) |
| 실시간 피드백 | 느림 | 불가 | 빠름 | 빠름 |
| 인프라 추가 | 불필요 | 이미 있음 | 불필요 | 이미 있음 |

**왜 Redis Sorted Set인가?**

대기열의 가장 빈번한 연산은 **순번 조회**다. 대기 중인 유저가 2초마다 "내가 몇 번째인지" 물어본다. 10,000명이 대기 중이면 초당 5,000건의 순번 조회가 발생한다. 이 연산이:
- MySQL에서는 COUNT 쿼리 → 인덱스 스캔 → ms 단위
- Redis Sorted Set에서는 ZRANK → O(log N) → μs 단위

대기열은 시스템을 보호하기 위한 것이므로, 대기열 자체가 병목이 되면 안 된다. Redis Sorted Set은 이 요구사항을 가장 잘 충족한다.

---

## 4. Redis Sorted Set의 score

### 4-1. score란 무엇인가

Sorted Set의 각 원소는 `member + score` 쌍으로 구성된다. score는 **각 원소의 정렬 기준이 되는 실수(double) 값**이다.

```
ZADD waiting-queue  1711800000001  "user:100"
                    ^^^^^^^^^^^^^   ^^^^^^^^^
                    score           member
                    (정렬 기준)      (실제 데이터)
```

- member는 **고유**하다 (Set 특성, 같은 member를 다시 넣으면 score만 업데이트)
- score는 **중복 가능**하다 (같은 score를 가진 member가 여러 개일 수 있음)
- score 기준 **오름차순**으로 자동 정렬됨

### 4-2. score의 데이터 타입

score는 **IEEE 754 double-precision floating-point** (64비트 부동소수점)이다.

```
정수:     ZADD key 1 "a"
소수:     ZADD key 3.14 "b"
음수:     ZADD key -100 "c"
특수값:   ZADD key +inf "d"     -- 양의 무한대
          ZADD key -inf "e"     -- 음의 무한대
```

- 표현 범위: 약 -(2^53) ~ +(2^53) 범위 내에서 정수를 정확하게 표현
- 그 이상의 정수는 부동소수점 정밀도 한계로 오차 발생 가능
- `+inf`와 `-inf`는 ZRANGEBYSCORE에서 범위 지정에 유용: `ZRANGEBYSCORE key -inf +inf` (전체 조회)

### 4-3. score가 같으면? — 사전순(lexicographic) 정렬

score가 동일한 member가 여러 개이면, **member 문자열의 사전순**으로 정렬된다.

```
> ZADD queue 1000 "user:B"
> ZADD queue 1000 "user:A"
> ZADD queue 1000 "user:C"

> ZRANGE queue 0 -1 WITHSCORES
1) "user:A"     ← score 같으면 문자열 사전순
2) "1000"
3) "user:B"
4) "1000"
5) "user:C"
6) "1000"
```

### 4-4. 대기열에서 score를 어떻게 쓰는가

**score = 진입 시각(timestamp)**

```
ZADD waiting-queue 1711800000001 "user:100"   -- 첫 번째 진입
ZADD waiting-queue 1711800000002 "user:200"   -- 두 번째 진입
ZADD waiting-queue 1711800000003 "user:300"   -- 세 번째 진입
```

- timestamp가 작을수록 = 먼저 진입 = 앞 순번
- 오름차순 정렬이므로 먼저 온 유저가 자동으로 앞에 위치
- ZPOPMIN이 score가 가장 낮은 원소부터 꺼내므로, 먼저 온 유저부터 처리

**왜 밀리초 timestamp를 쓰는가?**

초 단위 timestamp를 사용하면 같은 초에 진입한 유저들의 score가 동일해진다.

```
// 초 단위 — 같은 초에 진입하면 score 동일
ZADD queue 1711800000 "user:A"
ZADD queue 1711800000 "user:B"
// -> score 같으면 member 사전순 -> "user:A"가 먼저 -> 공정하지 않음
```

밀리초 단위를 사용하면 동일 score가 발생할 확률이 크게 줄어든다.

```
// 밀리초 단위 — 거의 항상 다른 score
ZADD queue 1711800000001 "user:A"
ZADD queue 1711800000002 "user:B"
// -> score가 다르므로 진입 순서 정확히 보장
```

**그래도 score가 같아질 수 있다면?**

극단적으로 동시에 요청이 들어와 밀리초까지 동일할 수 있다. 이 경우 member 사전순으로 정렬되는데, 밀리초 단위 동시 진입은 실무에서 "같은 순번"으로 간주해도 문제가 없는 수준이다.

더 엄밀하게 하려면 score에 **밀리초 + 시퀀스 번호**를 조합할 수 있다.

```
// Redis INCR로 시퀀스 생성 후 score에 반영
score = timestamp_ms * 1000 + sequence % 1000
```

이렇게 하면 같은 밀리초에 진입하더라도 시퀀스로 순서가 결정되어 완벽한 FIFO가 보장된다.

### 4-5. score 관련 명령어

```
ZSCORE key member             -- 특정 member의 score 조회 (O(1))
ZINCRBY key increment member  -- score를 increment만큼 증가 (O(log N))
ZRANGEBYSCORE key min max     -- score 범위로 member 조회
ZCOUNT key min max            -- score 범위 내 member 수
```

- `ZSCORE`: 대기열에서 유저의 진입 시각을 확인할 때 사용. 토큰 발급 시각과의 차이로 **실제 대기 시간** 계산 가능
- `ZINCRBY`: 대기열에서는 잘 쓰이지 않지만, 랭킹/점수 시스템에서 점수 증감에 활용
- `ZRANGEBYSCORE`: 특정 시간대에 진입한 유저 목록 조회 (모니터링/디버깅용)
- `ZCOUNT`: 특정 시간대의 대기 인원 수 확인 (예: 최근 1분간 진입한 유저 수)

---

## 5. Redis Sorted Set 심화

### 5-1. Sorted Set의 내부 구조

Redis Sorted Set은 내부적으로 두 가지 자료구조를 조합하여 사용한다.

**원소 수가 적을 때 (ziplist/listpack)**
- 원소 수가 128개 이하이고, 각 원소의 크기가 64바이트 이하일 때
- 연속 메모리 블록에 저장 → 메모리 효율적
- 순차 탐색이지만, 크기가 작으므로 캐시 히트율이 높아 빠름

**원소 수가 많을 때 (skiplist + hashtable)**
- skiplist: score 기반 정렬 유지, 범위 검색과 순위 조회에 사용
- hashtable: member → score 매핑, O(1) 조회에 사용
- ZRANK가 O(log N)인 이유: skiplist의 각 레벨을 건너뛰며 순위를 계산

```
skiplist 구조 (레벨 예시):

Level 3: HEAD ──────────────────────────────────── 500 ──── NIL
Level 2: HEAD ────────── 200 ──────────────────── 500 ──── NIL
Level 1: HEAD ── 100 ── 200 ── 300 ── 400 ──── 500 ──── NIL
Level 0: HEAD ── 100 ── 200 ── 300 ── 400 ── 500 ── 600  NIL
           (score: timestamp 기준 오름차순)
```

### 5-2. 핵심 명령어 상세

**ZADD — 대기열 진입**

```
ZADD waiting-queue {score} {member}
ZADD waiting-queue 1711800000000 "user:123"
```

- score: 진입 시각의 timestamp (밀리초 단위)
- member: userId를 문자열로 표현
- 이미 존재하는 member를 다시 ZADD하면 score만 업데이트됨
- NX 옵션: `ZADD waiting-queue NX {score} {member}` → 이미 존재하면 무시 (중복 진입 방지 강화)
- 시간 복잡도: O(log N)
- 반환값: 새로 추가된 원소의 수 (이미 존재하면 0)

```
> ZADD waiting-queue NX 1711800000001 "user:100"
(integer) 1    ← 새로 추가됨

> ZADD waiting-queue NX 1711800000002 "user:100"
(integer) 0    ← 이미 존재하므로 무시됨 (중복 방지)
```

**ZRANK — 순번 조회**

```
ZRANK waiting-queue "user:123"
```

- 0-based 순위 반환 (score 오름차순)
- 0이면 가장 앞, null이면 대기열에 없음
- 시간 복잡도: O(log N) — skiplist에서 해당 노드까지의 경로를 따라가며 누적 순위 계산
- 10,000명 대기 시: log2(10,000) ≈ 14번 비교만으로 순위 확인

```
> ZADD waiting-queue 1000 "user:A" 2000 "user:B" 3000 "user:C"
> ZRANK waiting-queue "user:A"
(integer) 0     ← 가장 먼저 진입 (score 가장 낮음)
> ZRANK waiting-queue "user:C"
(integer) 2     ← 3번째
> ZRANK waiting-queue "user:X"
(nil)           ← 대기열에 없음
```

**ZCARD — 전체 대기 인원**

```
ZCARD waiting-queue
```

- 시간 복잡도: O(1) — Redis가 원소 수를 메타데이터로 관리
- 예상 대기 시간 계산에 활용: `ZCARD / 초당 처리량`

**ZPOPMIN — 앞에서 N명 꺼내기**

```
ZPOPMIN waiting-queue 18
```

- score가 가장 낮은(가장 먼저 진입한) N개 원소를 **꺼내고 제거**
- atomic 연산: 여러 스케줄러가 동시에 실행해도 같은 유저를 두 번 꺼내지 않음
- 시간 복잡도: O(M * log N) — M은 꺼내는 수, N은 전체 원소 수
- 반환값: [member, score, member, score, ...] 쌍

```
> ZPOPMIN waiting-queue 2
1) "user:A"
2) "1000"
3) "user:B"
4) "2000"
```

**ZSCORE — 특정 유저의 진입 시각 확인**

```
ZSCORE waiting-queue "user:123"
```

- 시간 복잡도: O(1) — hashtable에서 직접 조회
- 진입 시각(score)을 확인하여 대기 시간 계산에 활용

**ZREM — 대기열 이탈**

```
ZREM waiting-queue "user:123"
```

- 유저가 직접 대기를 취소할 때 사용
- 시간 복잡도: O(log N)

### 5-3. 입장 토큰 (Entry Token) 개념

입장 토큰은 대기열에서 자기 차례가 온 유저에게 발급되는 **"주문 API 진입 허가증"**이다.

**왜 필요한가**

대기열만으로는 부족하다. 대기열에서 빠진 유저가 실제로 주문 API에 진입할 권한이 있는지 검증할 수단이 필요하다.

```
[토큰 없이 대기열만 운영하면]
대기열에서 빠진 유저     -> 주문 API 호출    ← OK
대기열을 거치지 않은 유저 -> 주문 API 직접 호출 ← 이걸 막을 수 없음

[토큰이 있으면]
대기열에서 빠진 유저     -> 토큰 발급 -> 토큰으로 주문 API 호출 -> 검증 통과 ✅
대기열을 거치지 않은 유저 -> 토큰 없음 -> 주문 API 거부 ❌
```

**놀이공원 비유**

놀이공원 인기 놀이기구를 생각하면 이해하기 쉽다.

| 놀이공원 | 대기열 시스템 |
|---------|-------------|
| 줄을 서서 대기 | Redis Sorted Set에 진입 |
| 직원이 "다음 5명!" 안내 | 스케줄러가 N명 꺼냄 |
| 팔찌(입장권) 받음 | 입장 토큰 발급 |
| 팔찌 보여주고 탑승 | 토큰으로 주문 API 호출 |
| 팔찌는 30분 유효 | 토큰 TTL 5분 |
| 탑승 후 팔찌 회수 | 주문 완료 후 토큰 삭제 |
| 팔찌 없으면 탑승 불가 | 토큰 없으면 주문 API 거부 |

**토큰이 해결하는 문제들**

| 문제 | 토큰이 해결하는 방식 |
|------|-------------------|
| 대기열 우회 | 토큰이 없으면 주문 API 진입 자체가 불가 |
| 중복 주문 | 주문 완료 후 토큰 삭제 -> 같은 토큰으로 재사용 불가 |
| 자리 차지 | TTL(5분)이 지나면 자동 만료 -> 다음 유저에게 기회 |
| 토큰 위조 | 서버(Redis)에 저장된 값과 대조하므로 위조 불가 |

**생명주기**

```
[발급]  스케줄러가 ZPOPMIN으로 유저를 꺼냄
        -> SET entry-token:user:100  abc-token  EX 300  (5분 TTL)

[전달]  유저가 순번 조회(polling) 시 토큰이 응답에 포함됨
        -> { "position": 0, "token": "abc-token" }

[사용]  유저가 주문 API 호출 시 헤더에 토큰 포함
        -> POST /orders  (Header: X-Entry-Token: abc-token)

[검증]  서버가 Redis에서 토큰 확인
        -> GET entry-token:user:100  -> "abc-token" -> 일치

[삭제]  주문 완료 후 즉시 삭제
        -> DEL entry-token:user:100

[만료]  5분 내 미사용 시 Redis TTL에 의해 자동 삭제
        -> 다음 유저에게 기회가 돌아감
```

요약하면, 입장 토큰은 **"대기열을 정당하게 통과했다"는 증명서**이자 **처리량 제어의 핵심 장치**다. 토큰이 있어야만 주문할 수 있고, TTL로 자동 만료되어 자리를 차지하는 유저를 방지한다.

### 5-4. Redis String + TTL — 입장 토큰 구현

입장 토큰은 Redis의 String 자료구조 + TTL로 구현한다.

**토큰 발급**

```
SET entry-token:{userId} {token} EX 300
```

- key: `entry-token:user:123` (유저별 고유 키)
- value: UUID 기반 토큰 문자열
- EX 300: 300초(5분) 후 자동 삭제
- 유저당 1개의 토큰만 존재 (같은 key에 SET하면 덮어씀)

**토큰 검증**

```
GET entry-token:{userId}
```

- 값이 존재하고, 요청의 토큰과 일치하면 유효
- null이면: 토큰 미발급 또는 TTL 만료

**토큰 삭제 (사용 완료)**

```
DEL entry-token:{userId}
```

- 주문 완료 후 즉시 삭제 → 동일 토큰으로 중복 주문 방지

**TTL 설계 고려사항**
- 너무 짧으면 (1분): 유저가 결제 정보 입력 중에 만료 → 불만
- 너무 길면 (30분): 토큰만 받고 주문 안 하는 유저가 자리 차지 → 뒤의 유저가 불이익
- 적정 범위: 3~5분 (주문 페이지 진입 → 결제 정보 확인 → 주문 완료까지의 합리적 시간)
- 비즈니스 판단 영역: 서비스 특성에 따라 조정 필요

---

## 6. Spring Data Redis API

### 6-1. ZSetOperations — Sorted Set 조작

우리 프로젝트의 `RedisConfig`에서 이미 `RedisTemplate<String, String>` 빈이 등록되어 있다. 이 템플릿에서 `opsForZSet()`으로 ZSetOperations를 얻을 수 있다.

```java
@Component
public class WaitingQueueRedisRepository {

    private final ZSetOperations<String, String> zSetOps;

    public WaitingQueueRedisRepository(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        this.zSetOps = redisTemplate.opsForZSet();
    }

    // 대기열 진입
    // ZADD waiting-queue NX {timestamp} {userId}
    // 반환: true(새로 추가), false(이미 존재)
    public boolean enter(String userId, double timestamp) {
        return Boolean.TRUE.equals(
            zSetOps.addIfAbsent("waiting-queue", userId, timestamp)
        );
    }

    // 순번 조회 (0-based, null이면 대기열에 없음)
    // ZRANK waiting-queue {userId}
    public Long getPosition(String userId) {
        return zSetOps.rank("waiting-queue", userId);
    }

    // 전체 대기 인원
    // ZCARD waiting-queue
    public long getTotalCount() {
        Long size = zSetOps.size("waiting-queue");
        return size != null ? size : 0;
    }

    // 앞에서 N명 꺼내기
    // ZPOPMIN waiting-queue {count}
    public Set<ZSetOperations.TypedTuple<String>> popFront(long count) {
        return zSetOps.popMin("waiting-queue", count);
    }

    // 대기열 이탈
    // ZREM waiting-queue {userId}
    public void remove(String userId) {
        zSetOps.remove("waiting-queue", userId);
    }
}
```

**주의: RedisTemplate 선택**
- `redisTemplateMaster`: Master 노드에 직접 쓰기/읽기 → ZADD, ZPOPMIN 등 쓰기 연산에 사용
- 기본 `redisTemplate`: REPLICA_PREFERRED → 읽기 연산에 사용 가능하지만, Replica 복제 지연(lag)이 있을 수 있음
- 대기열은 실시간성이 중요하므로, 읽기도 Master에서 수행하는 것이 안전한 선택

### 6-2. ValueOperations — 입장 토큰

```java
@Component
public class EntryTokenRedisRepository {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    private final ValueOperations<String, String> valueOps;
    private final RedisTemplate<String, String> redisTemplate;

    public EntryTokenRedisRepository(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        this.valueOps = redisTemplate.opsForValue();
        this.redisTemplate = redisTemplate;
    }

    // 토큰 발급 (5분 TTL)
    // SET entry-token:{userId} {token} EX 300
    public String issue(String userId) {
        String token = UUID.randomUUID().toString();
        String key = "entry-token:" + userId;
        valueOps.set(key, token, TOKEN_TTL);
        return token;
    }

    // 토큰 검증
    // GET entry-token:{userId}
    public boolean validate(String userId, String token) {
        String key = "entry-token:" + userId;
        String stored = valueOps.get(key);
        return token.equals(stored);
    }

    // 토큰 삭제 (사용 완료)
    // DEL entry-token:{userId}
    public void delete(String userId) {
        redisTemplate.delete("entry-token:" + userId);
    }

    // 토큰 존재 여부 확인
    public boolean exists(String userId) {
        String key = "entry-token:" + userId;
        return valueOps.get(key) != null;
    }
}
```

### 6-3. 기존 프로젝트의 Redis 설정 구조

우리 프로젝트의 `RedisConfig`는 Master-Replica 구조로 설정되어 있다.

```
[쓰기 요청] → redisTemplateMaster → Redis Master (:6379)
[읽기 요청] → redisTemplate (기본) → Redis Replica (:6380) (REPLICA_PREFERRED)
```

- `RedisProperties`: database, master(host, port), replicas(host, port 목록)를 관리하는 record
- `RedisNodeInfo`: 개별 노드의 host, port를 담는 record
- Lettuce 커넥션 팩토리: `LettuceClientConfiguration`에서 readFrom 전략 설정
- 두 개의 RedisTemplate 빈:
  - 기본: `REPLICA_PREFERRED` (읽기는 Replica 우선, 불가 시 Master)
  - Master 전용: `@Qualifier("redisTemplateMaster")` (항상 Master)

대기열과 입장 토큰은 **쓰기+읽기가 빈번하고 실시간성이 중요**하므로, Master 전용 템플릿을 사용하는 것이 안전하다.

---

## 7. Spring 스케줄러

### 7-1. @Scheduled 기본

Spring의 `@Scheduled`는 메서드를 주기적으로 실행하는 가장 단순한 방법이다.

**활성화**

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

`@EnableScheduling`을 선언해야 `@Scheduled`가 동작한다. 우리 프로젝트에서는 이미 OutboxScheduler가 있으므로, 이 설정이 존재할 수 있다.

**fixedRate vs fixedDelay**

이 차이를 정확히 이해하는 것이 중요하다.

```
fixedRate = 100ms:
  |--작업(30ms)--|           |--작업(30ms)--|           |--작업(30ms)--|
  0ms           30ms         100ms         130ms        200ms
  ^                          ^                          ^
  이전 실행 시작으로부터 100ms 후 다시 실행

fixedDelay = 100ms:
  |--작업(30ms)--|                 |--작업(30ms)--|                 |--작업(30ms)--|
  0ms           30ms              130ms         160ms              260ms
  ^                    ^          ^                    ^
  이전 실행 종료 후 100ms 대기하고 실행
```

| 속성 | 기준 시점 | 특징 | 적합한 경우 |
|------|----------|------|------------|
| `fixedRate` | 이전 실행 **시작** | 일정한 주기 보장, 실행이 오래 걸리면 겹칠 수 있음 | 대기열 스케줄러 — **일정한 처리량 유지** |
| `fixedDelay` | 이전 실행 **종료** | 겹침 없음, 주기가 변동될 수 있음 | Outbox 스케줄러 — **처리 완료 후 다음 폴링** |

대기열 스케줄러에 `fixedRate`가 적합한 이유:
- 목표: 초당 175명의 일정한 처리량 유지
- `fixedRate = 100`이면 100ms마다 실행 → 초당 10번 × 18명 = 180명
- 처리 시간이 약간 변동해도 주기는 일정하게 유지됨

기존 OutboxScheduler에 `fixedDelay`가 사용된 이유:
- Kafka 발행이 실패하면 재시도까지 시간이 걸릴 수 있음
- 이전 배치 처리가 완료된 후 다음 폴링을 시작하는 것이 안전

**initialDelay**

```java
@Scheduled(fixedRate = 100, initialDelay = 5000)
```

- 애플리케이션 시작 후 5초 뒤에 첫 실행
- 스프링 컨텍스트가 완전히 초기화된 후 실행되도록 보장
- Redis 연결이 안정화될 시간을 줌

### 7-2. 배치 크기 산정 — 처리량 역산

스케줄러가 한 번에 몇 명을 꺼낼지는 시스템의 처리 한계로부터 역산한다.

**계산 과정**

```
1. DB 커넥션 풀 크기 확인
   → HikariCP maximum-pool-size: 50

2. 주문 1건 평균 처리 시간 측정
   → 재고 확인 + 차감 + 결제 + 저장 = 약 200ms

3. 이론적 최대 TPS 계산
   → 50개 커넥션 / 0.2초 = 250 TPS
   → 50개 커넥션이 동시에 200ms씩 처리하면, 1초에 250건 처리 가능

4. 안전 마진 적용 (70%)
   → 250 * 0.7 = 175 TPS
   → 주문 외에도 상품 조회, 회원 조회 등이 커넥션을 사용하므로 30% 여유

5. Thundering Herd 완화를 위한 분할
   → 1초에 175명을 한 번에 발급 (X) → 동시 부하 발생
   → 100ms마다 18명씩 발급 (O) → 부하 10배 평탄화
   → 175 / 10 = 17.5 → 반올림 18명
```

**왜 70% 안전 마진인가?**
- 주문 API만 DB를 쓰는 것이 아님
- 상품 조회, 회원 정보 조회, 장바구니 등도 같은 커넥션 풀을 사용
- 피크 시간에 주문 외 트래픽도 증가할 수 있음
- 커넥션 풀이 100% 차면 대기 시간이 급증하고, 타임아웃 연쇄 발생

**HikariCP 설정과의 관계**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      connection-timeout: 30000   # 커넥션 대기 최대 30초
      idle-timeout: 600000        # 유휴 커넥션 10분 후 해제
```

- maximum-pool-size가 스케줄러 배치 크기의 **상한을 결정**
- connection-timeout이 너무 짧으면 대기열이 있어도 타임아웃 발생

### 7-3. 스케줄러 안정성

**단일 스레드 문제**

Spring의 기본 TaskScheduler는 **단일 스레드**로 동작한다. OutboxScheduler와 QueueScheduler가 같은 스레드에서 실행되면, 하나가 지연될 때 다른 것도 밀린다.

```
[단일 스레드 — 문제 상황]
시간 →
|--Outbox(3초 지연)--|--Queue--|   |--Outbox--|--Queue--|
0s                   3s       3.1s
                     ↑ Queue가 3초나 늦게 실행됨 → 대기열이 3초간 멈춤
```

해결: 스케줄러 전용 스레드 풀 설정

```java
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}
```

**멀티 인스턴스 환경에서의 중복 실행**

서버가 2대 이상이면, 각 서버에서 스케줄러가 동시에 실행된다.

```
[서버 A 스케줄러] → ZPOPMIN waiting-queue 18 → user:1 ~ user:18
[서버 B 스케줄러] → ZPOPMIN waiting-queue 18 → user:19 ~ user:36
```

ZPOPMIN이 atomic이므로 같은 유저를 두 번 꺼내는 일은 없다. 하지만 두 서버가 동시에 18명씩 꺼내면 순간적으로 36명에게 토큰이 발급되어 처리량 제어가 무너질 수 있다.

해결 방법:
- Redis 분산 락 (SETNX): 스케줄러 실행 전 락 획득, 완료 후 해제
- ShedLock 라이브러리: `@SchedulerLock` 어노테이션으로 간편하게 분산 락 적용
- 리더 선출: 하나의 인스턴스만 스케줄러를 실행하도록 지정

**스케줄러 장애 감지**

스케줄러가 멈추면 대기열에서 아무도 빠지지 못한다. 유저는 순번이 줄어들지 않는 것을 보고 이탈한다.

모니터링 방법:
- 스케줄러 실행 시마다 마지막 실행 시각을 Redis에 기록
- 별도 모니터링에서 마지막 실행 시각이 1분 이상 지나면 알림 발송
- Micrometer의 `@Timed` 어노테이션으로 스케줄러 실행 시간/횟수 추적

---

## 8. Thundering Herd 완화 전략 상세

### 8-1. 문제 재확인

대기열을 만들었으니 끝인 것 같지만, 토큰을 발급받은 유저들이 **동시에** 주문 API를 호출하는 새로운 문제가 생긨다.

```
[스케줄러] 1초마다 175명 토큰 발급
         → 175명이 거의 동시에 POST /orders
         → DB 커넥션 175개 동시 점유
         → 순간 부하 스파이크
```

대기열은 "피크를 평탄화"하는 것이지, 부하를 없애는 것이 아니다.

### 8-2. 전략 1 — 발급 간격 분산

가장 직접적인 방법. 한 번에 많이 발급하지 않고, 짧은 주기로 나누어 발급한다.

```
AS-IS: 매 1초 → 175명 동시 발급
TO-BE: 매 100ms → 18명씩 발급 → 부하가 10배 평탄화
```

구현은 @Scheduled의 fixedRate를 100ms로 설정하고 배치 크기를 18로 줄이는 것으로 충분하다.

```java
@Scheduled(fixedRate = 100)
public void issueTokens() {
    Set<TypedTuple<String>> users = waitingQueueRepository.popFront(BATCH_SIZE);
    for (TypedTuple<String> user : users) {
        entryTokenRepository.issue(user.getValue());
    }
}
```

### 8-3. 전략 2 — 토큰에 Jitter 부여

토큰을 발급하되, 활성화 시점에 랜덤 딜레이를 포함한다.

```
토큰 발급 시:
  user:100 → 토큰 발급 (activateAfter: 0ms)
  user:101 → 토큰 발급 (activateAfter: 500ms)
  user:102 → 토큰 발급 (activateAfter: 1200ms)
  user:103 → 토큰 발급 (activateAfter: 800ms)
```

유저마다 주문 API 진입 시점이 0~2초 사이에 랜덤하게 분산된다. 순번 조회 응답에 `activateAfterMs` 필드를 포함하여 클라이언트가 해당 시간만큼 기다린 후 주문 API를 호출하도록 유도한다.

### 8-4. 전략 3 — 주문 API 자체 Rate Limit

토큰이 있어도 초당 N건까지만 주문 API가 처리하도록 제한한다. 대기열이 뚫리거나 버그가 있어도 DB를 보호하는 **최종 안전장치**다.

```java
// Bucket4j, Resilience4j RateLimiter 등으로 구현
@RateLimiter(name = "orderApi", fallbackMethod = "orderRateLimited")
public OrderResponse createOrder(...) { ... }
```

이 전략은 대기열과 독립적으로 동작하므로, 대기열 시스템 전체가 장애가 나더라도 DB를 보호할 수 있다.

---

## 9. Graceful Degradation 상세

### 9-1. Redis 장애 시나리오

대기열의 핵심 인프라인 Redis가 죽으면 어떻게 되는가?

| 장애 유형 | 영향 |
|----------|------|
| Redis Master 다운 | 대기열 진입/토큰 발급 불가, 기존 토큰 검증도 불가 |
| Redis Replica 다운 | Master만 남아 동작 가능, 읽기 부하가 Master에 집중 |
| 네트워크 단절 | 일시적이면 재연결, 지속되면 Master 다운과 동일 |
| Redis 메모리 부족 | maxmemory 도달 시 ZADD 실패, 대기열 진입 불가 |

### 9-2. 대응 전략별 트레이드오프

**전략 1: 전면 차단**

```java
public QueueResponse enter(String userId) {
    try {
        return queueService.enter(userId);
    } catch (RedisConnectionException e) {
        throw new CoreException(ErrorType.SERVICE_UNAVAILABLE,
            "현재 주문이 일시적으로 불가합니다. 잠시 후 다시 시도해주세요.");
    }
}
```

- 선택 기준: 공정성이 최우선일 때 (대기열 없이 진입하면 순서가 깨짐)
- 장점: 시스템 안전, 공정성 유지
- 단점: 매출 손실, 유저 이탈

**전략 2: 대기열 우회 (bypass)**

```java
public QueueResponse enter(String userId) {
    try {
        return queueService.enter(userId);
    } catch (RedisConnectionException e) {
        log.warn("Redis 장애 감지, 대기열 우회 모드 활성화");
        return QueueResponse.bypass(); // 토큰 없이 주문 허용
    }
}
```

- 선택 기준: 서비스 가용성이 최우선일 때
- 장점: 서비스 유지
- 단점: 대기열의 보호 기능이 사라져 과부하 위험, 공정성 깨짐

**전략 3: Fallback 큐**

```java
public QueueResponse enter(String userId) {
    try {
        return queueService.enter(userId);
    } catch (RedisConnectionException e) {
        log.warn("Redis 장애 감지, Fallback 큐로 전환");
        return fallbackQueueService.enter(userId); // 로컬 메모리 큐 또는 Kafka
    }
}
```

- 선택 기준: 부분적 기능 유지가 목표일 때
- 장점: 서비스 유지 + 일정 수준의 순서 보장
- 단점: 멀티 인스턴스 간 큐가 분리됨 (로컬 메모리 사용 시), 순번 정확성 저하

### 9-3. 우리 프로젝트의 참고 패턴

기존 `ProductRedisCacheStore`에서 이미 Redis 장애 시 Graceful Degradation 패턴을 사용하고 있다.

```java
// 기존 패턴 (ProductRedisCacheStore)
public Optional<ProductDetailInfo> findCachedDetail(Long productId) {
    try {
        String json = valueOps.get(key);
        // ... Redis에서 캐시 조회
    } catch (Exception e) {
        log.warn("Redis 캐시 조회 실패, DB fallback: {}", e.getMessage());
        return Optional.empty(); // → 상위에서 DB 직접 조회
    }
}
```

대기열에도 동일한 try-catch 패턴을 적용할 수 있지만, 캐시와 대기열은 성격이 다르다:
- 캐시 실패 → DB에서 조회하면 됨 (기능적으로 동일)
- 대기열 실패 → 대체할 수 있는 동등한 시스템이 없음 (전략적 판단 필요)

핵심은 **"장애가 발생한 뒤에 판단하면 늦다"**는 것이다. 어떤 전략을 선택할지를 사전에 정의하고, 코드에 미리 구현해두어야 한다.

---

## 10. Redis 트랜잭션과 Lua 스크립트

### 10-1. Redis는 싱글 스레드다

Redis의 모든 동작을 이해하는 출발점은 **싱글 스레드**다.

Redis는 명령어를 하나의 스레드에서 순차적으로 처리한다. `ZADD`가 실행되는 동안 다른 클라이언트의 `ZPOPMIN`이 끼어들 수 없다. 이것이 개별 명령어가 atomic인 이유다.

```
시간 →
[클라이언트 A: ZADD]  [클라이언트 B: ZPOPMIN]  [클라이언트 C: GET]
      ↑ 하나씩 순서대로 실행, 동시 실행 없음
```

하지만 **여러 명령어를 연속으로 실행**해야 할 때 문제가 생긴다. 명령어 사이에 다른 클라이언트의 명령어가 끼어들 수 있다.

```
[클라이언트 A]                    [클라이언트 B]
  ZPOPMIN waiting-queue 1
    → "user:100" 꺼냄
                                    ZRANK waiting-queue "user:100"
                                      → (nil)  ← 이미 꺼내진 상태
  SET entry-token:user:100 ...
```

클라이언트 A의 ZPOPMIN과 SET 사이에 클라이언트 B의 ZRANK가 끼어들었다. 단일 명령어는 atomic이지만, **명령어 묶음은 atomic이 아니다**. 이 문제를 해결하는 두 가지 방법이 MULTI/EXEC과 Lua 스크립트다.

### 10-2. MULTI/EXEC — Redis의 트랜잭션

MULTI로 시작하고 EXEC로 끝나는 명령어 묶음이다. MULTI 이후의 명령어는 즉시 실행되지 않고 **큐에 쌓였다가, EXEC 시점에 한꺼번에 실행**된다.

```
> MULTI
OK
> ZPOPMIN waiting-queue 1
QUEUED                         ← 실행되지 않고 큐에 쌓임
> SET entry-token:user:100 abc-token EX 300
QUEUED                         ← 실행되지 않고 큐에 쌓임
> EXEC
1) 1) "user:100"               ← 이 시점에 한꺼번에 실행
   2) "1711800000000"
2) OK
```

EXEC가 호출되면 큐에 쌓인 명령어들이 **연속으로** 실행된다. 중간에 다른 클라이언트의 명령어가 끼어들지 못한다.

**DISCARD — 트랜잭션 취소**

EXEC 전에 DISCARD를 호출하면, 큐에 쌓인 명령어를 모두 버리고 트랜잭션을 취소한다.

```
> MULTI
OK
> SET key1 value1
QUEUED
> DISCARD          ← 큐를 비우고 트랜잭션 취소
OK                 ← SET key1은 실행되지 않음
```

주의: DISCARD는 **EXEC 전**에만 사용할 수 있다. EXEC 후에는 이미 실행이 완료되었으므로 되돌릴 수 없다.

**WATCH — 낙관적 락 (Optimistic Locking)**

WATCH는 특정 key를 감시한다. WATCH한 key가 EXEC 전에 다른 클라이언트에 의해 변경되면, EXEC가 실패한다 (null 반환).

```
[클라이언트 A]                      [클라이언트 B]
> WATCH waiting-queue
OK
> MULTI
OK
> ZPOPMIN waiting-queue 1
QUEUED
                                    > ZADD waiting-queue 999 "user:999"
                                    (integer) 1
                                    ← waiting-queue가 변경됨!
> EXEC
(nil)                               ← WATCH한 key가 변경되어 트랜잭션 실패
```

WATCH + MULTI/EXEC = **CAS(Compare-And-Swap)** 패턴이다. 실패하면 처음부터 다시 시도한다.

```java
// Spring Data Redis에서의 WATCH 사용
redisTemplate.execute(new SessionCallback<>() {
    @Override
    public Object execute(RedisOperations operations) {
        operations.watch("waiting-queue");

        // 현재 상태 읽기
        Long currentSize = operations.opsForZSet().size("waiting-queue");

        operations.multi();
        // 명령어 큐에 쌓기
        operations.opsForZSet().popMin("waiting-queue", batchSize);
        // EXEC — WATCH한 key가 변경되었으면 null 반환
        return operations.exec();
    }
});
```

**MULTI/EXEC의 핵심 한계: 명령어 간 결과 참조 불가**

MULTI 안의 명령어는 큐에 쌓이기만 할 뿐, 실행되지 않는다. 따라서 앞 명령어의 결과를 뒤 명령어의 입력으로 사용할 수 없다.

```
> MULTI
> ZPOPMIN waiting-queue 1       ← 결과: "user:100" (하지만 아직 모름)
> SET entry-token:??? abc EX 300  ← ??? 에 "user:100"을 넣고 싶지만 불가능
> EXEC
```

ZPOPMIN이 누구를 꺼낼지는 EXEC 시점에야 알 수 있다. 그런데 SET의 key는 MULTI 시점에 이미 결정되어야 한다. 이 모순 때문에 "꺼내기 + 토큰 발급"을 MULTI/EXEC만으로는 완전하게 묶을 수 없다.

### 10-3. Lua 스크립트 — MULTI/EXEC의 상위 호환

Lua 스크립트는 MULTI/EXEC의 한계를 모두 해결한다. 스크립트 전체가 **atomic으로 실행**되면서, **중간 결과를 참조하고 조건 분기도 가능**하다.

**기본 문법**

```lua
-- redis.call('명령어', key, arg1, arg2, ...)
-- KEYS[n]: 스크립트에 전달된 key 목록 (1-based 인덱스)
-- ARGV[n]: 스크립트에 전달된 인자 목록 (1-based 인덱스)

local value = redis.call('GET', KEYS[1])
if value then
    redis.call('DEL', KEYS[1])
end
return value
```

**대기열 적용: 꺼내기 + 토큰 발급을 하나의 atomic 연산으로**

```lua
-- KEYS[1] = "waiting-queue"
-- ARGV[1] = 배치 크기 (e.g. "18")
-- ARGV[2] = 토큰 TTL 초 (e.g. "300")

-- 1. 대기열에서 N명 꺼내기
local popped = redis.call('ZPOPMIN', KEYS[1], tonumber(ARGV[1]))
local results = {}

-- 2. 꺼낸 유저 각각에게 토큰 발급
for i = 1, #popped, 2 do
    local userId = popped[i]       -- ZPOPMIN 결과를 바로 참조 가능!
    local score = popped[i + 1]

    -- UUID 대신 간단한 토큰 생성 (실무에서는 더 안전한 방식 사용)
    local token = tostring(math.random(1000000, 9999999))
    local tokenKey = 'entry-token:' .. userId

    redis.call('SET', tokenKey, token, 'EX', tonumber(ARGV[2]))

    table.insert(results, userId)
    table.insert(results, token)
end

-- 3. 결과 반환: [userId1, token1, userId2, token2, ...]
return results
```

이 스크립트가 실행되는 동안:
- 다른 클라이언트의 명령어가 끼어들지 못함 (atomic)
- ZPOPMIN의 결과(userId)를 SET의 key로 바로 사용 (결과 참조)
- popped가 비어있으면 for 루프가 실행되지 않음 (조건 분기)

**Spring Data Redis에서 Lua 스크립트 실행**

```java
@Component
public class QueueTokenScript {

    private final RedisScript<List> issueTokenScript;
    private final RedisTemplate<String, String> redisTemplate;

    public QueueTokenScript(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        // 스크립트를 리소스에서 로드하거나 문자열로 직접 작성
        this.issueTokenScript = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    public List<String> popAndIssueTokens(int batchSize, int ttlSeconds) {
        return redisTemplate.execute(
            issueTokenScript,
            List.of("waiting-queue"),                          // KEYS
            String.valueOf(batchSize), String.valueOf(ttlSeconds) // ARGV
        );
    }
}
```

**redis.call vs redis.pcall**

```lua
-- redis.call: 에러 발생 시 스크립트 즉시 중단
redis.call('ZADD', 'key', 'not-a-number', 'member')  -- 에러 → 스크립트 중단

-- redis.pcall: 에러를 캐치하여 반환, 스크립트는 계속 실행
local ok, err = pcall(redis.call, 'ZADD', 'key', 'not-a-number', 'member')
if not ok then
    -- 에러 처리
end
```

대기열에서는 `redis.call`을 사용하는 것이 안전하다. 중간에 실패하면 더 이상 진행하지 않는 것이 맞기 때문이다.

### 10-4. RDBMS 트랜잭션과의 차이

Redis의 MULTI/EXEC과 Lua 스크립트를 "트랜잭션"이라 부르지만, RDBMS의 트랜잭션과는 근본적으로 다르다.

**RDBMS 트랜잭션 (MySQL)**
```sql
BEGIN;
UPDATE stock SET quantity = quantity - 1 WHERE product_id = 1;
INSERT INTO orders (product_id, member_id) VALUES (1, 100);
-- 여기서 실패하면?
ROLLBACK;  -- UPDATE도 원래 상태로 되돌림
```

**Redis Lua 스크립트**
```lua
redis.call('ZPOPMIN', 'waiting-queue', 1)          -- 실행됨 ✅
redis.call('SET', 'invalid-command-usage')          -- 실패 ❌ → 스크립트 중단
redis.call('SET', 'entry-token:user:100', 'abc')   -- 실행 안 됨
-- → ZPOPMIN은 이미 실행된 상태로 남아있음 (롤백 안 됨)
```

| 특성 | RDBMS (MySQL) | MULTI/EXEC | Lua 스크립트 |
|------|--------------|------------|-------------|
| 다른 연산 끼어들기 차단 | O (격리 수준에 따라) | O | O |
| 중간 결과 참조 | O | **X** | O |
| 조건 분기 | O | **X** | O |
| 실패 시 롤백 | **O** | **X** | **X** |
| 네트워크 왕복 | 명령어 수만큼 | MULTI + 명령어들 + EXEC | 1번 (스크립트 전송) |

**왜 Redis는 롤백을 지원하지 않는가?**

Redis 공식 문서의 입장은 명확하다:

1. **Redis 명령어는 문법이 틀리거나, 잘못된 타입에 실행할 때만 실패한다** — 예: String에 ZADD를 실행하면 실패. 이런 에러는 프로그래밍 실수이며, 프로덕션에서는 발생하지 않아야 한다.
2. **롤백을 지원하면 Redis의 단순성과 성능이 훼손된다** — WAL(Write-Ahead Log)이나 undo log를 유지해야 하므로, 인메모리 DB의 핵심 이점이 사라진다.
3. **Redis의 설계 철학은 "단순하고 빠르게"** — 복잡한 트랜잭션 관리는 애플리케이션의 책임이다.

### 10-5. 대기열에서의 실전 적용 가이드

**언제 무엇을 사용하는가**

| 상황 | 적합한 방식 | 이유 |
|------|------------|------|
| 대기열 진입 (ZADD NX) | 단일 명령어 | 하나의 명령어로 충분, 자체로 atomic |
| 순번 조회 (ZRANK) | 단일 명령어 | 읽기 전용, 원자성 문제 없음 |
| 전체 대기 인원 (ZCARD) | 단일 명령어 | 읽기 전용 |
| 꺼내기 + 토큰 발급 | **Lua 스크립트** | ZPOPMIN 결과를 SET key에 사용해야 함 |
| 토큰 검증 + 삭제 | MULTI/EXEC 또는 Lua | 검증과 삭제를 atomic으로 묶어야 함 |
| 순번 조회 + 토큰 존재 여부 확인 | MULTI/EXEC | 두 읽기를 한 번의 왕복으로 묶기 (파이프라이닝 효과) |

**토큰 검증 + 삭제를 Lua로 묶는 예시**

주문 완료 시 토큰을 검증하고 삭제하는 과정을 atomic으로 처리해야 한다. 검증과 삭제 사이에 다른 요청이 같은 토큰으로 중복 주문할 수 있기 때문이다.

```lua
-- KEYS[1] = "entry-token:{userId}"
-- ARGV[1] = 클라이언트가 보낸 토큰

local stored = redis.call('GET', KEYS[1])

if stored == false then
    return 0    -- 토큰 없음 (만료 또는 미발급)
end

if stored ~= ARGV[1] then
    return -1   -- 토큰 불일치
end

redis.call('DEL', KEYS[1])
return 1        -- 검증 성공 + 삭제 완료
```

이 스크립트가 atomic이므로, 같은 토큰으로 두 번 주문해도 하나만 성공하고 나머지는 "토큰 없음"을 받는다.

```java
// Spring Data Redis에서 사용
DefaultRedisScript<Long> validateScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

Long result = redisTemplate.execute(
    validateScript,
    List.of("entry-token:" + userId),
    requestToken
);

// result: 1 = 성공, 0 = 만료/미발급, -1 = 불일치
```

**Lua 스크립트 사용 시 주의사항**

1. **실행 시간을 짧게 유지**: 스크립트 실행 중 다른 모든 명령어가 대기한다. 긴 스크립트는 Redis 전체를 블로킹한다. 기본 타임아웃은 5초이며, 초과하면 `BUSY` 에러가 발생한다.
2. **KEYS는 반드시 파라미터로 전달**: key를 스크립트 안에 하드코딩하면 Redis Cluster에서 동작하지 않는다. Cluster는 KEYS 파라미터를 기반으로 어느 노드에서 실행할지 결정한다.
3. **스크립트 캐싱**: Redis는 스크립트를 SHA1 해시로 캐싱한다. 동일 스크립트를 반복 실행하면 전송 비용이 줄어든다. `EVALSHA` 명령어를 사용하면 해시만 전송한다. Spring Data Redis의 `DefaultRedisScript`가 이를 자동으로 처리한다.
4. **비결정적 명령어 제한**: `TIME`, `RANDOMKEY` 같은 비결정적 명령어는 Replica 복제 시 결과가 달라질 수 있어 쓰기 명령어 뒤에 사용할 수 없다 (Redis 7.0 이전 기준).

---

## 11. Lua 스크립트 vs Redis Functions

Redis Functions는 Redis 7.0에서 도입된 기능으로, Lua 스크립트의 한계를 보완한다. 둘 다 Redis 서버 안에서 Lua 코드를 atomic으로 실행하지만, 코드의 위치와 관리 방식이 근본적으로 다르다.

### 11-1. 동작 방식의 차이

**Lua 스크립트 (EVAL) — 클라이언트가 코드를 보내는 방식**

```
[클라이언트] → EVAL "redis.call('ZPOPMIN', KEYS[1], 1) ..." 1 waiting-queue
               ↑ 스크립트 전체를 매번 전송 (또는 EVALSHA로 해시만 전송)
```

- 스크립트가 **클라이언트 측에 존재**한다 (Java 코드, 리소스 파일 등)
- 실행할 때마다 Redis에 전달한다
- Redis에 저장되지 않는다 (SHA1 해시로 캐시는 되지만, 재시작하면 사라짐)

**Redis Functions (FCALL) — 서버에 등록하는 방식**

```
-- 1. 함수를 서버에 등록 (한 번만)
FUNCTION LOAD "#!lua name=queue_lib
redis.register_function('pop_and_issue', function(keys, args)
    local popped = redis.call('ZPOPMIN', keys[1], tonumber(args[1]))
    local results = {}
    for i = 1, #popped, 2 do
        local userId = popped[i]
        local token = tostring(math.random(1000000, 9999999))
        redis.call('SET', 'entry-token:' .. userId, token, 'EX', tonumber(args[2]))
        table.insert(results, userId)
        table.insert(results, token)
    end
    return results
end)
"

-- 2. 호출 (이름만으로)
FCALL pop_and_issue 1 waiting-queue 18 300
```

- 함수가 **Redis 서버 안에 영구 저장**된다
- 호출할 때는 함수 이름만 전달한다
- Redis 재시작해도 유지된다 (RDB/AOF에 포함)

### 11-2. 비교 정리

| 구분 | Lua 스크립트 (EVAL) | Redis Functions (FCALL) |
|------|-------------------|----------------------|
| 도입 시점 | Redis 2.6+ | Redis 7.0+ |
| 코드 위치 | 클라이언트 (Java 리소스 등) | Redis 서버에 영구 저장 |
| 등록 | 불필요 (매번 전송) | `FUNCTION LOAD`로 한 번 등록 |
| 호출 | `EVAL script ...` 또는 `EVALSHA hash ...` | `FCALL name ...` |
| 재시작 후 | 캐시 사라짐, 재전송 필요 | 유지됨 (RDB/AOF에 포함) |
| 코드 관리 | 애플리케이션 배포에 포함 | Redis 서버 배포에 포함 |
| 라이브러리 | 불가 (단일 스크립트) | 라이브러리 단위로 묶을 수 있음 |
| 업그레이드 | 애플리케이션 재배포 | `FUNCTION LOAD REPLACE`로 서버에서 교체 |
| Flags 지원 | 없음 | `no-writes`, `allow-oom` 등 선언 가능 |

### 11-3. Functions의 핵심 차별점 — 라이브러리

여러 함수를 하나의 라이브러리로 묶어 공통 로직을 재사용할 수 있다.

**Lua 스크립트: 각 스크립트가 독립적**

```
EVAL "대기열 진입 스크립트..." ...
EVAL "토큰 발급 스크립트..." ...
EVAL "토큰 검증 스크립트..." ...
→ 3개의 독립적인 스크립트, 공통 로직 재사용 불가
```

**Redis Functions: 하나의 라이브러리에 여러 함수**

```lua
FUNCTION LOAD "#!lua name=queue_lib

-- 공통 유틸리티 (라이브러리 내에서 재사용)
local function generate_token()
    return tostring(math.random(1000000, 9999999))
end

local function token_key(userId)
    return 'entry-token:' .. userId
end

-- 함수 1: 대기열 진입
redis.register_function('queue_enter', function(keys, args)
    return redis.call('ZADD', keys[1], 'NX', tonumber(args[1]), args[2])
end)

-- 함수 2: 꺼내기 + 토큰 발급
redis.register_function('queue_pop_and_issue', function(keys, args)
    local popped = redis.call('ZPOPMIN', keys[1], tonumber(args[1]))
    local results = {}
    for i = 1, #popped, 2 do
        local token = generate_token()           -- 공통 함수 재사용
        redis.call('SET', token_key(popped[i]), token, 'EX', tonumber(args[2]))
        table.insert(results, popped[i])
        table.insert(results, token)
    end
    return results
end)

-- 함수 3: 토큰 검증 + 삭제
redis.register_function('queue_validate_token', function(keys, args)
    local stored = redis.call('GET', keys[1])
    if stored == false then return 0 end
    if stored ~= args[1] then return -1 end
    redis.call('DEL', keys[1])
    return 1
end)
"
```

`generate_token()`과 `token_key()` 같은 공통 로직을 라이브러리 내에서 공유할 수 있다. Lua 스크립트에서는 각 스크립트가 완전히 독립적이라 이런 재사용이 불가능하다.

### 11-4. Functions의 Flags

각 함수의 특성을 flag로 선언하면, Redis가 함수의 동작을 미리 파악할 수 있다.

```lua
redis.register_function{
    function_name = 'queue_get_position',
    callback = function(keys, args)
        return redis.call('ZRANK', keys[1], args[1])
    end,
    flags = { 'no-writes' }     -- 읽기 전용 선언
}
```

| Flag | 의미 |
|------|------|
| `no-writes` | 읽기 전용 → Replica에서도 실행 가능 |
| `allow-oom` | 메모리 부족(OOM)해도 실행 허용 |
| `allow-stale` | Replica가 Master와 동기화 안 된 상태에서도 실행 허용 |

Lua 스크립트는 이런 선언이 없어서, Redis가 스크립트의 특성을 알 수 없다. 쓰기가 포함될 수 있다고 가정하고 Replica에서의 실행을 제한한다. Functions의 `no-writes` flag를 사용하면 순번 조회 같은 읽기 전용 함수를 Replica에서 실행하여 Master의 부하를 줄일 수 있다.

### 11-5. 관리 명령어

```
FUNCTION LOAD "#!lua name=queue_lib ..."    -- 라이브러리 등록
FUNCTION LOAD REPLACE "#!lua name=queue_lib ..."  -- 기존 라이브러리 교체
FUNCTION LIST                                -- 등록된 라이브러리 목록
FUNCTION LIST LIBRARYNAME queue_lib          -- 특정 라이브러리 상세
FUNCTION DELETE queue_lib                    -- 라이브러리 삭제
FUNCTION DUMP                                -- 모든 함수를 직렬화 (백업)
FUNCTION RESTORE <dump>                      -- 직렬화된 함수 복원
```

### 11-6. 언제 무엇을 사용하는가

| 상황 | 선택 | 이유 |
|------|------|------|
| Redis 7.0 미만 | Lua 스크립트 | Functions 사용 불가 |
| 단순한 atomic 연산 1~2개 | Lua 스크립트 | 등록 없이 바로 실행, 간편 |
| 여러 관련 연산을 묶어 관리 | Functions | 라이브러리로 묶어 공통 로직 재사용 |
| 코드와 Redis 배포를 분리하고 싶을 때 | Functions | 서버에 저장되므로 앱 배포와 독립적 |
| 읽기 전용 함수를 Replica에서 실행하고 싶을 때 | Functions | `no-writes` flag로 Replica 실행 가능 |
| Spring Data Redis 생태계 활용 | Lua 스크립트 | `DefaultRedisScript`가 잘 지원됨 |

### 11-7. 우리 프로젝트에서의 선택

우리 프로젝트의 Redis 버전은 7.0이므로 둘 다 사용 가능하다. 하지만 현실적으로는 **Lua 스크립트가 더 적합**하다.

1. **Spring Data Redis의 지원**: `DefaultRedisScript` + `RedisTemplate.execute()`로 Lua 스크립트를 자연스럽게 사용할 수 있다. Functions용 API는 아직 `RedisTemplate`에 직접 통합되어 있지 않아 저수준 커넥션(`RedisConnection.execute()`)을 직접 다뤄야 한다.
2. **코드와 함께 관리**: 스크립트가 Java 프로젝트 안에 있으면 버전 관리, 코드 리뷰, 테스트가 자연스럽다. Functions는 Redis 서버에 별도로 배포해야 하므로 배포 파이프라인이 복잡해진다.
3. **대기열 스크립트 수가 적다**: 2~3개 스크립트면 충분하므로, 라이브러리로 묶을 필요성이 크지 않다.

Functions는 Redis를 공유 로직 플랫폼처럼 쓰는 대규모 환경(여러 애플리케이션이 같은 Redis의 동일한 비즈니스 로직을 호출)에서 진가를 발휘한다. 우리처럼 애플리케이션과 Redis가 1:1로 밀접한 구조에서는 Lua 스크립트로 충분하다.

---

## 12. Redis 명령어 레퍼런스

### 12-1. 기본 명령어

| 명령어 | 설명 | 예시 |
|--------|------|------|
| `PING` | 연결 확인 | `PING` -> `PONG` |
| `SELECT {db}` | 데이터베이스 선택 (0~15) | `SELECT 0` |
| `DBSIZE` | 현재 DB의 키 개수 | `DBSIZE` -> `(integer) 42` |
| `EXISTS {key}` | 키 존재 여부 | `EXISTS entry-token:user:100` -> `1` 또는 `0` |
| `DEL {key}` | 키 삭제 (동기) | `DEL entry-token:user:100` |
| `UNLINK {key}` | 키 삭제 (비동기, 큰 키에 적합) | `UNLINK bigkey` |
| `TYPE {key}` | 키의 자료구조 타입 확인 | `TYPE waiting-queue` -> `zset` |
| `TTL {key}` | 남은 만료 시간 (초) | `TTL entry-token:user:100` -> `287` |
| `PTTL {key}` | 남은 만료 시간 (밀리초) | `PTTL entry-token:user:100` -> `287432` |
| `EXPIRE {key} {초}` | 만료 시간 설정 | `EXPIRE mykey 300` |
| `PERSIST {key}` | 만료 시간 제거 (영구 보관) | `PERSIST mykey` |
| `RENAME {old} {new}` | 키 이름 변경 | `RENAME oldkey newkey` |
| `KEYS {pattern}` | 패턴에 맞는 키 목록 (프로덕션 사용 금지) | `KEYS entry-token:*` |
| `SCAN {cursor}` | 키를 점진적으로 순회 (KEYS 대안) | `SCAN 0 MATCH entry-token:* COUNT 100` |

### 12-2. String (문자열)

가장 기본적인 자료구조. 입장 토큰에 사용.

| 명령어 | 설명 | 시간 복잡도 |
|--------|------|-----------|
| `SET {key} {value}` | 값 저장 | O(1) |
| `SET {key} {value} EX {초}` | 값 저장 + TTL (초) | O(1) |
| `SET {key} {value} PX {밀리초}` | 값 저장 + TTL (밀리초) | O(1) |
| `SET {key} {value} NX` | 키가 없을 때만 저장 (분산 락에 활용) | O(1) |
| `SET {key} {value} XX` | 키가 있을 때만 덮어쓰기 | O(1) |
| `GET {key}` | 값 조회 | O(1) |
| `MSET {k1} {v1} {k2} {v2}` | 여러 키 한번에 저장 | O(N) |
| `MGET {k1} {k2}` | 여러 키 한번에 조회 | O(N) |
| `INCR {key}` | 값을 1 증가 (정수) | O(1) |
| `INCRBY {key} {n}` | 값을 n 증가 | O(1) |
| `DECR {key}` | 값을 1 감소 | O(1) |
| `SETNX {key} {value}` | 키가 없을 때만 저장 (SET NX와 동일) | O(1) |
| `GETDEL {key}` | 값 조회 후 삭제 (atomic) | O(1) |

**대기열에서의 활용**

```
-- 토큰 발급 (5분 TTL)
SET entry-token:user:100 abc-token EX 300

-- 토큰 검증
GET entry-token:user:100
-> "abc-token"

-- 토큰 삭제 (주문 완료 후)
DEL entry-token:user:100

-- 시퀀스 번호 생성 (score용)
INCR queue:sequence
-> (integer) 1

-- 분산 락 획득 (스케줄러 중복 실행 방지)
SET scheduler-lock "server-1" NX EX 1
-> OK      ← 락 획득 성공
-> (nil)   ← 다른 서버가 이미 보유
```

### 12-3. Sorted Set (정렬 집합)

대기열의 핵심 자료구조. 각 원소에 score가 부여되어 자동 정렬.

**추가/수정**

| 명령어 | 설명 | 시간 복잡도 |
|--------|------|-----------|
| `ZADD {key} {score} {member}` | 원소 추가 | O(log N) |
| `ZADD {key} NX {score} {member}` | 없을 때만 추가 (중복 방지) | O(log N) |
| `ZADD {key} XX {score} {member}` | 있을 때만 score 업데이트 | O(log N) |
| `ZADD {key} GT {score} {member}` | 새 score가 더 클 때만 업데이트 | O(log N) |
| `ZADD {key} LT {score} {member}` | 새 score가 더 작을 때만 업데이트 | O(log N) |
| `ZINCRBY {key} {increment} {member}` | score 증감 | O(log N) |

**조회**

| 명령어 | 설명 | 시간 복잡도 |
|--------|------|-----------|
| `ZSCORE {key} {member}` | 특정 member의 score 조회 | O(1) |
| `ZMSCORE {key} {m1} {m2}` | 여러 member의 score 한번에 조회 | O(N) |
| `ZRANK {key} {member}` | 순위 조회 (오름차순, 0-based) | O(log N) |
| `ZREVRANK {key} {member}` | 역순위 조회 (내림차순) | O(log N) |
| `ZCARD {key}` | 전체 원소 수 | O(1) |
| `ZCOUNT {key} {min} {max}` | score 범위 내 원소 수 | O(log N) |
| `ZRANGE {key} {start} {stop}` | 인덱스 범위 조회 (오름차순) | O(log N + M) |
| `ZRANGE {key} {start} {stop} WITHSCORES` | 인덱스 범위 + score 함께 조회 | O(log N + M) |
| `ZREVRANGE {key} {start} {stop}` | 인덱스 범위 조회 (내림차순) | O(log N + M) |
| `ZRANGEBYSCORE {key} {min} {max}` | score 범위로 조회 | O(log N + M) |
| `ZRANGEBYSCORE {key} {min} {max} LIMIT {offset} {count}` | score 범위 + 페이징 | O(log N + M) |

**삭제/꺼내기**

| 명령어 | 설명 | 시간 복잡도 |
|--------|------|-----------|
| `ZREM {key} {member}` | 특정 원소 제거 | O(log N) |
| `ZPOPMIN {key} {count}` | score 가장 낮은 N개 꺼내기 (제거) | O(M log N) |
| `ZPOPMAX {key} {count}` | score 가장 높은 N개 꺼내기 (제거) | O(M log N) |
| `ZREMRANGEBYSCORE {key} {min} {max}` | score 범위로 삭제 | O(log N + M) |
| `ZREMRANGEBYRANK {key} {start} {stop}` | 인덱스 범위로 삭제 | O(log N + M) |

**대기열에서의 활용**

```
-- 대기열 진입 (중복 방지)
ZADD waiting-queue NX 1711800000001 "user:100"
-> (integer) 1    ← 새로 추가됨
ZADD waiting-queue NX 1711800000002 "user:100"
-> (integer) 0    ← 이미 존재, 무시됨

-- 내 순번 조회
ZRANK waiting-queue "user:100"
-> (integer) 0    ← 가장 앞

-- 전체 대기 인원
ZCARD waiting-queue
-> (integer) 512

-- 스케줄러: 앞에서 18명 꺼내기
ZPOPMIN waiting-queue 18

-- 특정 유저 대기 취소
ZREM waiting-queue "user:100"

-- 전체 대기열 확인 (디버깅용)
ZRANGE waiting-queue 0 -1 WITHSCORES

-- 특정 시간 이전에 진입한 유저 수
ZCOUNT waiting-queue -inf 1711800000000
```

### 12-4. Hash (해시)

필드-값 쌍의 맵. 유저 세션 정보나 토큰 메타정보 저장에 활용 가능.

| 명령어 | 설명 | 시간 복잡도 |
|--------|------|-----------|
| `HSET {key} {field} {value}` | 필드 설정 | O(1) |
| `HGET {key} {field}` | 필드 조회 | O(1) |
| `HMSET {key} {f1} {v1} {f2} {v2}` | 여러 필드 한번에 설정 | O(N) |
| `HMGET {key} {f1} {f2}` | 여러 필드 한번에 조회 | O(N) |
| `HGETALL {key}` | 모든 필드-값 조회 | O(N) |
| `HDEL {key} {field}` | 필드 삭제 | O(1) |
| `HEXISTS {key} {field}` | 필드 존재 여부 | O(1) |
| `HINCRBY {key} {field} {n}` | 필드 값 증가 | O(1) |
| `HLEN {key}` | 필드 수 | O(1) |
| `HKEYS {key}` | 모든 필드명 목록 | O(N) |
| `HVALS {key}` | 모든 값 목록 | O(N) |

```
-- 토큰 메타정보를 Hash로 저장하는 방식 (String 대신)
HSET entry-token:user:100 token "abc-token" issuedAt "1711800000"
HGET entry-token:user:100 token
EXPIRE entry-token:user:100 300
```

### 12-5. Set (집합)

중복 없는 원소의 모음. 순서가 없다.

| 명령어 | 설명 | 시간 복잡도 |
|--------|------|-----------|
| `SADD {key} {member}` | 원소 추가 | O(1) |
| `SREM {key} {member}` | 원소 제거 | O(1) |
| `SISMEMBER {key} {member}` | 원소 존재 여부 | O(1) |
| `SMEMBERS {key}` | 모든 원소 조회 | O(N) |
| `SCARD {key}` | 원소 수 | O(1) |
| `SPOP {key}` | 랜덤 원소 꺼내기 | O(1) |
| `SRANDMEMBER {key}` | 랜덤 원소 조회 (제거 안 함) | O(1) |
| `SUNION {k1} {k2}` | 합집합 | O(N) |
| `SINTER {k1} {k2}` | 교집합 | O(N*M) |
| `SDIFF {k1} {k2}` | 차집합 | O(N) |

```
-- 토큰 발급된 유저 추적 (모니터링용)
SADD issued-users "user:100" "user:200"
SISMEMBER issued-users "user:100"
-> (integer) 1
SCARD issued-users
-> (integer) 2
```

### 12-6. List (리스트)

순서가 있는 원소의 목록. 큐/스택으로 활용 가능.

| 명령어 | 설명 | 시간 복잡도 |
|--------|------|-----------|
| `LPUSH {key} {value}` | 왼쪽(앞)에 추가 | O(1) |
| `RPUSH {key} {value}` | 오른쪽(뒤)에 추가 | O(1) |
| `LPOP {key}` | 왼쪽에서 꺼내기 | O(1) |
| `RPOP {key}` | 오른쪽에서 꺼내기 | O(1) |
| `LRANGE {key} {start} {stop}` | 범위 조회 | O(S+N) |
| `LLEN {key}` | 길이 | O(1) |
| `LINDEX {key} {index}` | 인덱스로 조회 | O(N) |
| `LREM {key} {count} {value}` | 특정 값 제거 | O(N) |
| `BLPOP {key} {timeout}` | 블로킹 왼쪽 꺼내기 (큐가 비면 대기) | O(1) |
| `BRPOP {key} {timeout}` | 블로킹 오른쪽 꺼내기 | O(1) |

List는 대기열로 쓸 수 있지만, **순번 조회(내가 몇 번째인지)가 O(N)**이라 Sorted Set보다 부적합하다.

```
-- List로 대기열을 만든다면 (비추천)
RPUSH queue "user:100"     -- 뒤에 추가
LPOP queue                 -- 앞에서 꺼내기
LLEN queue                 -- 대기 인원
-- 내 순번 조회? -> O(N) 전체 순회 필요
```

### 12-7. 프로덕션에서 주의할 명령어

| 명령어 | 위험성 | 대안 |
|--------|--------|------|
| `KEYS *` | O(N), 키가 많으면 Redis 전체 블로킹 | `SCAN` 사용 |
| `FLUSHDB` / `FLUSHALL` | 데이터 전체 삭제 | 프로덕션에서 절대 사용 금지 |
| `SMEMBERS` / `HGETALL` | 원소가 많으면 느림 | `SSCAN` / `HSCAN` 사용 |
| `DEL` (큰 키) | 원소가 많은 Set/Hash/Sorted Set 삭제 시 블로킹 | `UNLINK` 사용 (비동기 삭제) |
| `ZRANGE 0 -1` | 전체 조회, 원소가 많으면 느림 | 디버깅 전용, 프로덕션에서는 범위 제한 |

- `KEYS`는 개발 환경에서만 사용. 프로덕션에서는 반드시 `SCAN`으로 대체
- 큰 키를 삭제할 때 `DEL` 대신 `UNLINK`를 쓰면 Redis가 백그라운드에서 비동기로 삭제하므로 블로킹이 발생하지 않음
- Redis는 싱글 스레드이므로, 오래 걸리는 명령어 하나가 전체 Redis를 멈추게 할 수 있음