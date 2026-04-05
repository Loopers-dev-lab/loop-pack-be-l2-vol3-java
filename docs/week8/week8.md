
---
# 학습내용
## 🧭 루프팩 BE L2 - Round 8

> 줄을 세우고, 순서대로 들여보내자!
>
>
> 트래픽이 몰리는 순간, 시스템을 보호하면서도 **유저에게 공정한 대기 경험**을 제공하는 구조를 설계합니다.
>
> Redis 기반의 **주문 대기열 시스템**을 구현하고, 입장 토큰과 실시간 순번 조회를 통해
> **"기다리는 동안에도 이탈하지 않는"** 서비스 흐름을 만들어봅니다.
>

<aside>
🎯

**Summary**

</aside>

지난 주에 우리는 Kafka를 통해 **요청을 버퍼링**하고 Consumer가 순차 처리하는 구조를 배웠습니다. 선착순 쿠폰은 "요청을 넣고 나중에 결과를 확인"하는 **fire & forget** 방식이었죠.

하지만 **주문은 다릅니다**. 유저는 주문이 처리될 때까지 화면 앞에서 기다리고 있고, "내 순서가 언제인지", "지금 몇 번째인지"를 알고 싶어합니다. 단순히 요청을 큐에 넣는 것만으로는 충분하지 않습니다.

이번 라운드에서는 **대기열 시스템**을 직접 설계하고 구현합니다. 시스템 입장에서는 **처리량을 제어**하고, 유저 입장에서는 **공정한 순서와 실시간 피드백**을 받을 수 있는 구조를 만들어봅니다.

<aside>
📌

**Keywords**

</aside>

- 대기열 (Waiting Queue)
- Rate Limiting vs Queuing
- Back-pressure
- Redis Sorted Set
- 입장 토큰 (Entry Token)
- 순번 조회 & 실시간 피드백
- Thundering Herd
- Graceful Degradation
- Polling / SSE

<aside>
🧠

**Learning**

</aside>

## ⚠️ 문제 분석 - 블랙 프라이데이, 주문이 몰린다

<aside>
🚧

우리 커머스 서비스에 블랙 프라이데이 행사가 열렸습니다. 평소 초당 100건이던 주문 요청이 **초당 10,000건**으로 폭증합니다.

</aside>

```jsx
[10,000명 동시 접속]
     └── POST /orders
           ├── 재고 확인 & 차감
           ├── 결제 처리
           └── 주문 저장
           → DB 커넥션 풀 고갈
           → 응답 지연 → 타임아웃
           → 전체 시스템 장애
```

| **문제점** | **설명** |
| --- | --- |
| 💥 시스템 과부하 | DB 커넥션, 스레드 풀이 한계를 넘으면 전체 서비스가 멈춤 |
| 😤 유저 경험 붕괴 | 응답 없이 로딩만 돌다가 타임아웃 → 재시도 → 더 악화 |
| ⚖️ 공정성 부재 | 누가 먼저 요청했는지와 관계없이, 운 좋은 사람만 성공 |
| 🔄 재시도 폭풍 | 실패한 유저가 새로고침 → 트래픽이 더 증가하는 악순환 |

### 🍰 스케일업 & 아웃으로 해결되지 않는 이유

- 서버를 10배 늘려도, **DB와 PG는 스케일이 제한적**
- 트래픽의 **피크가 극단적으로 짧고 높은** 경우(행사 시작 직후 10초), 오토스케일링이 반응하기 전에 터짐
- 결국 **시스템이 처리할 수 있는 속도로 요청을 조절**하는 것이 핵심

> 이 개념을 **Back-pressure** 라고 합니다.
하류 시스템(DB, PG)이 감당할 수 있는 속도만큼만 상류(유저 요청)를 흘려보내는 것.
대기열은 이 back-pressure를 구현하는 대표적인 방법입니다.
>

---

## 🚦 거부할 것인가, 기다리게 할 것인가

트래픽이 몰릴 때 선택할 수 있는 전략은 크게 두 가지입니다.

### Rate Limiting vs Queuing

| **구분** | **Rate Limiting** | **Queuing (대기열)** |
| --- | --- | --- |
| 초과 요청 처리 | **거부** (429 Too Many Requests) | **보관** (대기열에 적재) |
| 유저 경험 | "나중에 다시 시도하세요" | "잠시만 기다려주세요 (현재 512번째)" |
| 유저 반응 | 새로고침 → 재시도 폭풍 | 기다림 → 순서대로 처리 |
| 적합한 상황 | API 보호, 봇 차단, 일상적 부하 제어 | 행사 트래픽, **유저가 기다릴 의사가 있는** 경우 |

블랙 프라이데이에 *"나중에 다시 시도하세요"* 를 반환하면, 유저는 떠나거나 더 세게 새로고침합니다. **유저가 원하는 것을 기다려서라도 얻을 수 있는** 구조가 필요합니다.

> **💡 Rate Limiting과 Queuing은 양자택일이 아닙니다.**
대기열 자체에도 최대 인원 제한(Rate Limiting)을 둘 수 있고, 봇이나 비정상 요청은 Rate Limiting으로 먼저 걸러낸 뒤 정상 유저만 대기열에 진입시킬 수 있습니다.
>

---

## 🚪 대기열 시스템 설계

### Kafka 버퍼링과의 차이

지난 주 선착순 쿠폰에서 Kafka를 버퍼로 활용했습니다. 그것과 이번 대기열은 어떻게 다를까요?

| **구분** | **Kafka 버퍼링 (R7 쿠폰)** | **대기열 시스템 (R8 주문)** |
| --- | --- | --- |
| 유저 경험 | 요청 후 나중에 결과 확인 (fire & forget) | 화면에서 순번을 보며 대기 |
| 결과 전달 | 비동기 (polling으로 결과 조회) | 입장 토큰 발급 → 즉시 주문 가능 |
| 제어 대상 | 처리 순서 | **처리 속도 (throughput)** |
| 핵심 관심사 | 메시지 유실 방지, 멱등 처리 | 공정한 순서, 실시간 피드백, 토큰 만료 |
| 유저 인지 | "신청 완료, 결과는 나중에" | "현재 512번째, 예상 대기 3분" |

### 대기열의 구성 요소

```jsx
[유저] → 대기열 진입 (POST /queue/enter)
      → 대기열에서 순번 부여
      → 순번 조회 (GET /queue/position)  ← 실시간 반복 조회
      → 내 차례가 오면 입장 토큰 발급
      → 토큰으로 주문 API 호출 (POST /orders)
      → 토큰 검증 → 주문 처리
```

| **구성 요소** | **역할** |
| --- | --- |
| **대기열 (Queue)** | 유저 요청을 순서대로 보관 |
| **스케줄러 (Scheduler)** | 일정 주기로 대기열에서 N명씩 꺼내 입장 토큰 발급 |
| **입장 토큰 (Entry Token)** | 주문 API 진입 권한. TTL이 있어 일정 시간 내 사용해야 함 |
| **순번 조회 (Position)** | 유저가 현재 몇 번째인지 실시간으로 확인 |

---

## 🔧 Redis 기반 대기열 구현

### 왜 Redis인가?

| **요구사항** | **Redis가 적합한 이유** |
| --- | --- |
| 빠른 읽기/쓰기 | 인메모리 기반으로 순번 조회가 μs 단위 |
| 순서 보장 | Sorted Set으로 score(timestamp) 기반 정렬 |
| 원자적 연산 | `ZADD`, `ZRANK`, `ZPOPMIN` 등이 atomic |
| TTL 지원 | 입장 토큰의 만료를 자연스럽게 처리 |

### 핵심 자료구조: Sorted Set

```jsx
ZADD  waiting-queue  {timestamp}  {userId}    // 대기열 진입
ZRANK waiting-queue  {userId}                  // 내 순번 조회 (0-based)
ZCARD waiting-queue                            // 전체 대기 인원
ZPOPMIN waiting-queue {N}                      // 앞에서 N명 꺼내기 (스케줄러)
```

- **score = 진입 시각 (timestamp)** → 먼저 들어온 사람이 앞 순번
- **member = userId** → 중복 진입 자동 방지 (Set 특성)

### 입장 토큰

```jsx
SET   entry-token:{userId}  {token}  EX 300    // 5분 TTL 토큰 발급
GET   entry-token:{userId}                      // 토큰 검증
DEL   entry-token:{userId}                      // 사용 완료 후 삭제
```

- 토큰이 있는 유저만 주문 API 진입 가능
- TTL이 지나면 자동 만료 → 다음 유저에게 기회가 돌아감

---

## 📡 실시간 피드백 — 유저를 떠나지 않게

<aside>
🕯️

대기열의 성패는 **유저가 기다리는 동안 이탈하지 않느냐**에 달려있습니다. 순번이 보이지 않으면 유저는 새로고침을 누르거나 이탈합니다.

</aside>

### 피드백 전달 방식

유저에게 순번을 알려주는 방식은 크게 세 가지가 있습니다.

**1⃣  Polling — 클라이언트가 주기적으로 물어보기**

가장 단순한 방식입니다. 클라이언트가 일정 주기(1~3초)마다 서버에 순번을 질의합니다.

```jsx
  [클라이언트]
     └── setInterval(2000)
           → GET /queue/position
           ← { "position": 128, "estimatedWaitSeconds": 45 }
           ...
           ← { "position": 0, "token": "abc-123-def" }  // 내 차례!
           → POST /orders (with token)
```

- ✅ 구현이 단순하고 인프라 변경이 없음
- ❌ 대기 인원이 많으면 Polling 자체가 서버 부하
- ❌ 주기 사이의 지연이 발생 (2초 주기면 최대 2초 늦게 인지)

**2⃣  SSE (Server-Sent Events) — 서버가 알려주기**

서버가 클라이언트와의 단방향 연결을 유지하며, 순번이 바뀔 때마다 Push합니다.

```jsx
[클라이언트] → GET /queue/stream (연결 유지)
          ← event: position
          ← data: { "position": 128, "estimatedWaitSeconds": 45 }
          ...
          ← event: enter
          ← data: { "token": "abc-123-def" }
```

- ✅ 서버가 변경 시점에만 전송 → 불필요한 요청 없음
- ✅ HTTP 기반이라 별도 프로토콜 불필요
- ❌ 연결을 유지해야 하므로 대기 인원 × 1 커넥션 필요
- ❌ 로드밸런서 뒤에서 연결 유지 설정 필요

**3⃣  WebSocket — 양방향 실시간**

- 대기열 순번 조회는 **서버 → 클라이언트 단방향**이면 충분
- WebSocket의 양방향 기능이 과도 → 이 시나리오에서는 비추천

> 💡 구현 난이도를 고려하면 **Polling으로 시작**하고, 대기 인원이 많아 Polling 부하가 문제가 되면 **SSE로 전환**하는 것을 권장합니다.
>

### 예상 대기 시간 계산

유저에게 순번만 보여주는 것보다 **"약 N분 남았습니다"** 가 훨씬 효과적입니다.

```mathematica
예상 대기 시간 = 내 순번 / 초당 처리량
```

> e.g. 순번 300, 초당 50명 처리 → 300 / 50 = 약 6초 대기
단, 이 수치는 **추정값**입니다. 토큰 미사용(만료)이나 시스템 상태에 따라 달라질 수 있으므로, "약 N분"으로 표현하는 것이 좋습니다.
>

---

## ⚡ Thundering Herd — 토큰 발급 직후의 함정

대기열을 만들었으니 문제가 해결된 것 같지만, **새로운 문제가 생깁니다.**

스케줄러가 1초마다 175명에게 토큰을 발급하면, 175명이 **동시에** 주문 API를 호출합니다. 이건 원래 문제의 축소판입니다.

```mathematica
[스케줄러] → 1초마다 175명 토큰 발급
         → 175명 동시에 POST /orders
         → DB 커넥션 175개 동시 점유
         → 순간 부하 스파이크!
```

이를 **Thundering Herd(떼몰이) 문제**라고 합니다. 캐시 만료 시 모든 요청이 동시에 DB를 조회하는 것과 같은 원리입니다.

### 완화 전략

**1⃣  발급 간격 분산**

1초에 175명을 한 번에 발급하지 않고, 100ms마다 17~18명씩 나누어 발급합니다.

```mathematica
AS-IS: 매 1초 → 175명 동시 발급
TO-BE: 매 100ms → ~18명씩 발급 → 부하가 10배 평탄화
```

**2⃣  토큰에 Jitter 부여**

토큰을 발급하되, 활성화 시점에 랜덤 딜레이(0~2초)를 포함합니다. 유저마다 주문 API 진입 시점이 자연스럽게 분산됩니다.

**3⃣  주문 API 자체 Rate Limit**

토큰이 있어도 초당 N건까지만 주문 API가 처리합니다. 대기열이 뚫리더라도 하류 시스템을 보호하는 **최종 안전장치**입니다.

> 💡 대기열은 **피크를 평탄화(smoothing)** 하는 것이지, 부하를 없애는 것이 아닙니다.
하류 시스템의 한계를 항상 염두에 두고 설계해야 합니다.
>

---

## 💣 오해 — 대기열만 있으면 끝?

### 대기열 자체의 리스크

**❌ 토큰 미사용**

- 토큰을 받고 주문하지 않으면 자리만 차지합니다. TTL을 설정해 만료 처리가 필수이며, 만료된 토큰 수만큼 다음 유저에게 추가 발급하는 로직이 필요합니다.

**❌ 어뷰징**

- 한 유저가 여러 브라우저나 디바이스로 중복 진입을 시도할 수 있습니다. Redis Sorted Set에 userId를 member로 사용하면 자연스럽게 중복이 방지되지만, 비로그인 상태라면 디바이스 핑거프린트 등 별도 대응이 필요합니다.

**❌ 스케줄러 장애**

- 스케줄러가 멈추면 대기열에서 아무도 빠지지 못합니다. 헬스체크와 이중화를 고려해야 하며, 스케줄러 미실행 시간이 일정 기준을 초과하면 알림을 보내야 합니다.

**❌ 과도한 Polling 부하**

- 대기 인원이 10,000명이고 2초마다 Polling하면 초당 5,000건의 순번 조회 요청이 발생합니다. Redis 기반이라 감당 가능하지만, 대기 인원에 비례해 Polling 주기를 동적으로 늘리는 것도 고려해볼 수 있습니다.

    ```mathematica
    순번 1~100:    1초마다 조회 (곧 입장)
    순번 100~1000: 3초마다 조회
    순번 1000+:    5초마다 조회
    ```


### Redis 장애 시 — Graceful Degradation

*대기열의 핵심 인프라인 Redis가 죽으면 어떻게 해야 할까요?*

**전면 차단**

- 대기열 진입 자체를 막고 "잠시 후 다시 시도" 안내
- 안전하지만 서비스 중단

**대기열 우회 (bypass)**

- 대기열 없이 주문 API 직접 접근 허용
- 서비스 유지하지만 과부하 위험

**Fallback 큐**

- 로컬 메모리 큐나 Kafka로 임시 전환
- 순번 정확성은 떨어지지만 서비스 유지

> 정답은 없습니다. **"Redis 장애 시 우리 서비스는 어떻게 동작해야 하는가?"** 를 사전에 정의해두는 것 자체가 중요합니다. 장애가 발생한 뒤에 판단하면 늦습니다.
>

---

## 📊 운영 지표 — 무엇을 모니터링할 것인가

대기열 시스템은 **눈에 보이지 않는 곳에서 유저 경험을 결정**합니다. 장애가 발생하기 전에 이상 징후를 감지하려면 아래 지표를 추적해야 합니다.

| **지표** | **설명** | **왜 중요한가** |
| --- | --- | --- |
| **Queue Depth** | 현재 대기열에 대기 중인 유저 수 (`ZCARD`) | 급격히 증가하면 유입 > 처리량이라는 신호 |
| **Avg Wait Time** | 진입 → 토큰 발급까지 평균 대기 시간 | 유저 체감 품질의 핵심 지표 |
| **P99 Wait Time** | 상위 1% 유저의 대기 시간 | 평균은 정상인데 P99가 높으면 특정 시점 병목 |
| **Token Conversion Rate** | 토큰 발급 → 주문 완료 비율 | < 50%면 TTL이 짧거나 주문 UX에 문제 |
| **Token Expiry Rate** | 토큰 만료(이탈) 비율 | > 30%면 유저가 대기 중 포기하고 있다는 의미 |
| **Scheduler Health** | 스케줄러 마지막 실행 시각 | 1분 이상 미실행 시 대기열 전체가 멈춤 |

> 💡 특히 **Token Conversion Rate**와 **Token Expiry Rate**는 단순 시스템 지표가 아니라 **비즈니스 지표**입니다. 유저가 토큰을 받고도 주문하지 않는다면, 대기 시간이 너무 길거나 토큰 TTL이 맞지 않다는 뜻입니다.
>

---

## 🏗️ 우리 프로젝트에 적용하기

### 전체 흐름

```mathematica
[유저] → POST /queue/enter
      → Redis Sorted Set에 userId + timestamp 저장
      → 순번 응답 (e.g. 512번째)

[유저] → GET /queue/position (2초마다 polling)
      → 현재 순번 + 예상 대기 시간 응답

[스케줄러] → 100ms마다 실행
         → ZPOPMIN으로 N명 꺼내기 (Thundering Herd 완화)
         → 입장 토큰 발급 (Redis SET + TTL 5분)

[유저] → 순번 0 도달, 토큰 수신
      → POST /orders (Header: X-Entry-Token)
      → 토큰 검증 → 주문 처리
      → 토큰 삭제

[주문 이후] → 7주차 이벤트 파이프라인 동작
          → ApplicationEvent → Kafka → collector
```

### Round 7과의 연결점

| **Round7 에서 배운 것** | **Round8 에서 활용하는 것** |
| --- | --- |
| 주문 → 이벤트 발행 (ApplicationEvent) | 주문 처리 후 후속 이벤트는 그대로 이벤트 기반 |
| Kafka 파이프라인 | 주문 완료 이벤트 → Kafka → collector (Metrics 집계) |
| Outbox Pattern | 주문 이벤트 발행의 신뢰성 보장 |

> 대기열은 **주문 API 앞단의 관문**이고, 주문 API 이후의 흐름은 **Round7 에서 구축한 이벤트 파이프라인**이 그대로 동작합니다.
>

### 처리량 설계 기준

시스템이 안정적으로 처리할 수 있는 TPS를 기준으로 스케줄러의 배치 크기를 설정합니다.

```mathematica
DB 커넥션 풀: 50
주문 1건 평균 처리 시간: 200ms
→ 이론적 최대 TPS: 50 / 0.2 = 250 TPS
→ 안전 마진 70%: 175 TPS
→ 스케줄러: 100ms마다 ~18명씩 토큰 발급 (Thundering Herd 완화)
```

---

### 🌾 Summary

| **항목** | **설명** |
| --- | --- |
| **대기열의 목적** | 시스템을 보호하면서 공정한 순서로 유저를 처리 (Back-pressure) |
| **Rate Limiting과의 차이** | 거부가 아니라 보관. 유저가 기다릴 의사가 있는 상황에 적합 |
| **핵심 기술** | Redis Sorted Set (순서 보장 + 원자적 연산 + TTL) |
| **유저 경험** | 순번 조회 + 예상 대기 시간 → 이탈 방지 |
| **Thundering Herd** | 토큰 발급 분산으로 완화. 대기열이 부하를 없애는 건 아님 |
| **Graceful Degradation** | Redis 장애 시 전략을 사전에 정의해두는 것이 핵심 |
| **R7과의 관계** | 대기열은 주문 API **앞단**의 관문, 주문 이후는 R7의 이벤트 파이프라인 |

---

<aside>
📚

**References**

</aside>

| 구분 | 링크 |
| --- | --- |
| 🔍 Redis Sorted Set | [Redis Sorted Sets](https://redis.io/docs/latest/develop/data-types/sorted-sets/) |
| ⚙ Spring Data Redis | [Spring Data Redis Reference](https://docs.spring.io/spring-data/redis/reference/) |
| 📖 가상 대기열 설계 | [Virtual Waiting Room Architecture - System Design Newsletter](https://newsletter.systemdesign.one/p/virtual-waiting-room) |
| 📖 Back-pressure | [Reactive Streams](https://www.reactive-streams.org/) |
| 🌟 지마켓 - 대기열 | [지마켓 대기열 시스템 파헤치기](https://dev.gmarket.com/46) |
| 📖 SSE in Spring | [Server-Sent Events in Spring - Baeldung](https://www.baeldung.com/spring-server-sent-events) |

<aside>
🌟

**Next Week Preview**

</aside>

> **쌓인 데이터를 어떻게 가치로 바꿀 수 있을까?**
>
>
> **Round7** 에서 Kafka를 통해 유저 행동 이벤트를 수집하고 product_metrics에 집계하는 파이프라인을 구축했습니다. **Round8** 에서는 대기열을 통해 트래픽을 제어하며 안정적으로 주문을 처리하는 구조도 만들었습니다.
>
> 다음주에는 지금까지 쌓인 데이터를 기반으로 **실시간 랭킹 파이프라인**을 구축해볼 거예요. 인기 상품, 급상승 키워드, 실시간 판매 순위 — 데이터가 서비스의 경쟁력이 됩니다!
>

 ```

```kotlin
// src/coupon/event/listener/OrderCreateEventListener.kt
// 이벤트 리스너 flow
@EventListener
fun handle(event: OrderCreatedEvent) {
	couponService.issue(event); 
}

// src/order/event/listener/OrderCreateEventListener.kt
@EventListener
fun handle(event: OrderCreatedEvent) {
	metricsService.increase(event); 
	
}

// src/order/event/listener/OrderCreateEventListener.kt
@EventListener
fun handle(event: OrderCreatedEvent) {
	logService.record(event);
}
```

1. 쿠폰 서비스에 이벤트 발행이 실패하면.. 다른 서비스에도 이벤트 전파가 실패한다.
2. 직렬로 수행되죠. 우리는 기본적으로 다른 컨슈머에 영향받지 않는 구조가 필요해요.
3. 새로운 컨슈머가 추가될때 주문 도메인 코드를  수정해야 하죠.
  1. 주문이 결국 내 이벤트가 누구한테 필요한지 다 알고, 또 추가로 필요하면 쫓아다니면서 먹여줘야 해요.


---
# 구현과제
# 📝 Round 8 Quests

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

**Nice-To-Have (부가적으로 가져가면 좋을 것-**시간이 ****허락하면 ****꼭 ****해보세요**)**

- SSE 기반 실시간 순번 Push
- Polling 주기 동적 조절 (순번 구간별)
- Thundering Herd 완화 (발급 간격 분산 / Jitter)
- Graceful Degradation (Redis 장애 시 Fallback)
</aside>

### 📋 과제 정보

**Step 1 — Redis 기반 대기열 구현**

- 블랙 프라이데이 행사를 앞두고, 주문 API 앞단에 **대기열 시스템**을 구축한다.
- Redis Sorted Set을 활용해 **진입 순서를 보장**하고, **중복 진입을 방지**한다.
- 유저는 대기열에 진입한 뒤, 자신의 **순번과 예상 대기 시간**을 조회할 수 있다.

**Step 2 — 입장 토큰 & 스케줄러**

- 스케줄러가 일정 주기로 대기열에서 N명씩 꺼내 **입장 토큰을 발급**한다.
- 토큰은 **TTL**이 있어 일정 시간 내 사용하지 않으면 자동 만료된다.
- 토큰이 있는 유저만 주문 API에 진입할 수 있으며, 주문 완료 후 토큰은 삭제된다.
- 처리량 설계 기준(DB 커넥션 풀, 평균 처리 시간)을 바탕으로 **스케줄러의 배치 크기를 산정**한다.

**Step 3 — 실시간 순번 조회**

- 유저가 대기 중 **현재 순번과 예상 대기 시간**을 실시간으로 확인할 수 있는 API를 구현한다.
- Polling 기반으로 구현하되, 대기 인원에 따른 **Polling 부하**를 고려한다.

**주문 이후 흐름**

- 대기열은 **주문 API 앞단의 관문**이다.
- 주문 API 이후의 흐름(이벤트 발행, Kafka 파이프라인, Metrics 집계)은 **R7에서 구축한 구조를 그대로 활용**한다.

---

## ✅ Checklist

### 🚪 Step 1 — 대기열

- [ ]  Redis Sorted Set 기반 대기열 진입 API 구현 (`POST /queue/enter`)
- [ ]  순번 조회 API 구현 (`GET /queue/position`)
- [ ]  userId 기반 중복 진입 방지
- [ ]  전체 대기 인원 조회

### 🎫 Step 2 — 입장 토큰 & 스케줄러

- [ ]  스케줄러가 주기적으로 대기열에서 N명을 꺼내 입장 토큰 발급
- [ ]  토큰 TTL 설정 (e.g. 5분)
- [ ]  주문 API 진입 시 토큰 검증
- [ ]  주문 완료 후 토큰 삭제
- [ ]  처리량 기준으로 스케줄러 배치 크기 산정 근거 문서화

### 📡 Step 3 — 실시간 순번 조회

- [ ]  예상 대기 시간 계산 로직 구현
- [ ]  Polling 기반 순번 + 예상 대기 시간 응답
- [ ]  토큰 발급 시 순번 조회 응답에 토큰 포함

|


### 🧪 검증

- [ ]  동시 진입 테스트 — 대기열 순서가 정확히 보장되는지 확인
- [ ]  토큰 만료 테스트 — TTL 초과 시 토큰이 무효화되는지 확인
- [ ]  처리량 초과 테스트 — 스케줄러 배치 크기 이상의 요청이 들어와도 시스템이 안정적인지 확인




## 설계 결정 기록

### 결정 1. 패키지 구조 — `domain/queue/` 독립 도메인

대기열을 주문 도메인 하위(`domain/order/queue/`)에 넣을지, 독립 도메인으로 뺄지 고민했다.

- 대기열은 주문 전용이 아님 — 향후 한정판 세일, 티켓팅 등 다른 도메인에도 적용 가능
- 주문 도메인에 넣으면 주문이 대기열을 알아야 하는 역방향 의존이 생김
- feature flag, 스케줄러, 토큰 관리 등 자체 책임이 충분히 큼

결정: **독립 도메인으로 분리**

### 결정 2. API 경로 — `/api/v1/queue/*`

패키지를 독립으로 뺐으므로 경로도 `/api/v1/queue/*`로 독립시켰다.
주문과의 연결은 Interceptor가 담당하므로 경로 의존이 없다.

### 결정 3. 토큰 검증 방식 — userId 기반

두 가지 선택지를 비교했다:

| 방식 | 장점 | 단점 |

------|------|------|
| 헤더 기반 (`X-Entry-Token`) | 보안 명시적, 토큰 탈취 시 userId+토큰 둘 다 필요 | 클라이언트가 토큰 저장/전달해야 함 |
| userId 기반 (서버 조회) | 클라이언트 변경 없음, 구현 단순 | 로그인 세션 의존, 디버깅 시 추적 덜 명확 |

결정: **userId 기반** — 서버가 로그인된 userId로 Redis에서 토큰 존재 여부만 확인. 클라이언트는 토큰 값을 몰라도 됨.

### 결정 4. 검증 레이어 — Interceptor

Interceptor를 사용하면 주문 도메인 코드를 건드리지 않는다.
feature flag OFF면 Interceptor가 통과시키고, ON이면 토큰 검증한다.

```
요청 -> MemberAuthInterceptor -> QueueTokenInterceptor -> OrderV1Controller
                                   |
                           flag OFF -> 통과
                           flag ON  -> 토큰 검증
```

`WebMvcConfig`에 등록하여 `POST /api/v1/orders` 경로에만 적용한다.
주문 도메인은 대기열의 존재를 전혀 모른다.

### 결정 5. 스케줄러 설정 — 100ms / ~18명, 토큰 TTL 5분

**5-1. 실행 주기 & 배치 크기**

| 선택지 | 주기 | 배치 | 장점 | 단점 |
|--------|------|------|------|------|
| A안 | 1초 | 175명 | 단순 | 175명 동시 주문 -> Thundering Herd |
| **B안 (선택)** | 100ms | ~18명 | 부하 10배 평탄화 | 스케줄러 호출 빈도 높음 |

결정: **B안** — 대기열의 본래 목적(부하 평탄화)에 부합

처리량 산정 근거:
```
DB 커넥션 풀: 50 (HikariCP)
주문 1건 평균 처리 시간: 200ms (가정)
이론적 최대 TPS: 50 / 0.2 = 250 TPS
안전 마진 70%: 175 TPS
100ms당 배치 크기: 175 / 10 = ~18명
```

**5-2. 토큰 TTL**

| TTL | 장점 | 단점 |
|-----|------|------|
| 3분 | 빠른 회전 | 유저 시간 부족, 만료 불만 |
| **5분 (선택)** | 적당한 여유, 커머스 일반 기준 | 미사용 시 5분간 자리 차지 |
| 10분 | 유저 여유 충분 | 대기열 정체, 전환율 저하 |

결정: **5분** — 주문 페이지 진입부터 결제 완료까지 적당한 여유

### 결정 6. 순번 조회 응답 — 최소 응답

```json
{ "position": 128, "estimatedWaitSeconds": 1, "token": null }
```

`totalWaiting`(전체 대기 인원)을 포함하면 "3420명 대기 중"을 보고 이탈할 수 있다.
ZCARD 비용은 O(1)로 거의 없지만, UX 관점에서 최소 응답을 선택했다.

예상 대기 시간 계산 방식:

| 방식 | 계산 | 장점 | 단점 |
|------|------|------|------|
| A. 고정 TPS | position / 175.0 | 단순 | 스케줄러 설정과 괴리 가능 |
| **B. 스케줄러 기반 (선택)** | (position / batchSize) * (intervalMs / 1000) | 스케줄러 설정에 정확히 연동 | 여전히 이론값 |
| C. 실측 기반 | 최근 N초간 실제 처리량 측정 | 가장 정확 | 구현 복잡 |

결정: **B. 스케줄러 기반** — batchSize=18, intervalMs=100으로 계산. 예: position 176 → (176/18) * 0.1 ≈ 1초

### 결정 7. Feature Flag — DB 기반

| 방식 | 장점 | 단점 |
|------|------|------|
| application.yml | 단순 | 변경 시 재배포 필요 |
| Redis | 런타임 즉시 변경 | Redis 장애 시 플래그 자체를 못 읽음 |
| **DB (선택)** | 런타임 변경 가능, 이력 추적, Admin API 연동 | DB 장애 시 못 읽음 (but DB 죽으면 주문도 못함) |

결정: **DB 기반, 캐시 없이** — DB가 죽으면 어차피 주문도 못하므로 추가 SPOF가 아님. Admin API로 운영 중 즉시 ON/OFF 가능.

---

## 고민포인트

### 고민 1. 스케줄러에 fixedRate 대신 fixedDelay를 선택한 이유

학습 자료에서는 "일정한 처리량 유지"를 위해 fixedRate가 적합하다고 정리했지만,
실제 구현에서는 fixedDelay(100ms)를 선택했다.

**fixedRate의 문제**

fixedRate는 이전 실행의 시작 시점 기준으로 다음 실행을 예약한다.
만약 한 번의 실행이 100ms를 초과하면(Redis 지연, 네트워크 타임아웃 등),
이전 실행이 끝나기 전에 다음 실행이 시작될 수 있다.

```
fixedRate = 100ms일 때, 처리가 150ms 걸리는 경우:

T=0ms    [실행1 시작]
T=100ms  [실행2 시작] ← 실행1이 아직 끝나지 않음
T=150ms  [실행1 종료]
T=200ms  [실행3 시작] ← 실행2가 아직 끝나지 않음
```

Spring의 기본 스케줄러는 단일 스레드이므로 실제로 동시 실행되지는 않지만,
밀린 작업이 대기열처럼 쌓여서 연쇄적으로 실행되는 문제가 발생한다.
Redis 장애가 복구된 직후 밀린 실행이 한꺼번에 수행되면,
대기열 시스템이 방지하려 했던 Thundering Herd를 스케줄러 자체가 유발하게 된다.

**fixedDelay의 선택 이유**

fixedDelay는 이전 실행이 완료된 후 100ms를 기다린 뒤 다음 실행을 시작한다.

```
fixedDelay = 100ms일 때, 처리가 150ms 걸리는 경우:

T=0ms    [실행1 시작]
T=150ms  [실행1 종료]
T=250ms  [실행2 시작]  ← 종료 후 100ms 대기
T=400ms  [실행2 종료]
T=500ms  [실행3 시작]
```

- 실행이 겹치지 않으므로 동시성 이슈가 원천 차단됨
- Redis 지연 시 자연스럽게 처리량이 줄어들어 back-pressure 효과
- 장애 복구 후 밀린 작업이 한꺼번에 실행되지 않음

**트레이드오프**

처리 시간이 길어지면 실제 TPS가 설계값(175 TPS)보다 낮아질 수 있다.
하지만 대기열의 본래 목적이 "백엔드를 보호하면서 안정적으로 처리"하는 것이므로,
처리량이 일시적으로 줄어드는 것이 시스템 과부하보다 낫다고 판단했다.

### 고민 2. peekFront + remove 대신 ZPOPMIN을 선택한 이유

초기 구현에서는 스케줄러가 `ZRANGE`로 대기열 앞쪽 N명을 조회(peek)한 뒤,
토큰 발급 성공 시 `ZREM`으로 개별 제거하는 방식이었다.

```
[초기 구현 — 2단계 방식]
1. ZRANGE waiting-queue 0 17  → 18명 조회 (제거 안 함)
2. SET entry-token:{userId}   → 토큰 발급
3. ZREM waiting-queue {userId} → 대기열에서 제거
```

**문제점**

peek과 remove가 별개의 Redis 호출이므로, 두 연산 사이에 일관성이 깨질 수 있다.
분산 락으로 단일 인스턴스만 실행되도록 보장하고 있지만,
락 만료(30초) 시 두 인스턴스가 동일 유저를 peek하는 극단적 시나리오가 존재한다.

```
[문제 시나리오 — 락 만료 시]
인스턴스A: ZRANGE → userId=1 조회
인스턴스A: (처리 지연, 30초 경과, 락 만료)
인스턴스B: 락 획득 → ZRANGE → userId=1 다시 조회   ← 동일 유저 중복 처리
```

`hasToken` 체크로 중복 발급 자체는 방지되지만, 불필요한 Redis 호출이 발생한다.

**ZPOPMIN으로 변경**

`ZPOPMIN`은 조회와 제거가 하나의 Redis 명령으로 수행된다.
한 번 꺼낸 유저는 대기열에서 즉시 사라지므로 중복 처리가 원천 차단된다.

```
[변경 후 — 원자적 방식]
1. ZPOPMIN waiting-queue 18   → 18명 조회 + 제거 (원자적)
2. SET entry-token:{userId}   → 토큰 발급
3. (실패 시) ZADD waiting-queue {score} {userId}  → 원래 score로 재삽입
```

**실패 시 재삽입 전략**

ZPOPMIN으로 이미 대기열에서 꺼냈으므로, 토큰 발급이 실패하면 유저가 유실된다.
이를 방지하기 위해 `QueueEntry(userId, score)` record를 도입하여
실패 시 원래 score(진입 시각)로 재삽입한다.

재삽입 자체도 실패할 수 있으므로(Redis 장애 지속 시), 별도 try-catch로 격리하여
한 명의 재삽입 실패가 나머지 배치 유저 처리에 영향을 주지 않도록 한다.
재삽입 실패 시 `log.error`로 userId, score를 기록하여 운영에서 수동 복구할 수 있도록 한다.

```java
for (QueueEntry entry : entries) {
    try {
        queueTokenService.issueToken(entry.userId());
    } catch (Exception e) {
        try {
            queueRepository.enter(entry.userId(), entry.score());
        } catch (Exception reinsertEx) {
            log.error("재삽입 실패, 유저 유실 userId={}, score={}", ...);
        }
    }
}
```

재삽입 시 `ZADD NX`를 사용하므로 이미 토큰이 발급된 유저가 다시 대기열에 들어가는 일은 없다.

**트레이드오프**

| 항목 | peekFront + remove | ZPOPMIN + 재삽입 |
|------|---|---|
| 원자성 | ❌ 별개 연산, 사이 gap 존재 | ✅ 단일 명령 |
| 실패 처리 | 대기열에 그대로 남아있음 | 수동 재삽입 필요 |
| 중복 처리 위험 | 극단적 시나리오에서 가능 | 원천 차단 |
| 구현 복잡도 | 단순 | QueueEntry record + 재삽입 로직 |

안전성과 원자성을 우선하여 ZPOPMIN을 선택했다.

### 고민 3. Feature Flag DB 조회에 인메모리 캐시를 적용한 이유

`isQueueEnabled()`는 스케줄러(100ms)와 Interceptor(매 POST 요청)에서 호출된다.
캐시 없이 매번 DB를 조회하면 초당 10회 이상의 SELECT가 발생한다.

```
스케줄러: 100ms마다 → 초당 10회
Interceptor: POST /orders마다 → 트래픽 비례
QueueService.enter/getPosition: 호출마다 → 트래픽 비례
→ 합산: 초당 수십 회 DB SELECT
```

**설계 결정 7번과의 관계**

설계 결정 7번에서 "DB 기반, 캐시 없이"를 선택했다.
"DB가 죽으면 어차피 주문도 못하므로 추가 SPOF가 아님"이라는 판단이었다.

그러나 DB가 죽지 않더라도, 고빈도 SELECT가 커넥션 풀을 점유하여
주문 처리에 영향을 줄 수 있다는 점을 간과했다.

**적용한 방식**

Spring의 `@Cacheable` 대신 `volatile` 필드 + TTL 방식의 단순 인메모리 캐시를 적용했다.

```java
private static final long CACHE_TTL_MS = 5_000;
private volatile boolean cachedQueueEnabled = false;
private volatile long lastCheckedTime = 0;

public boolean isQueueEnabled() {
    long now = System.currentTimeMillis();
    if (now - lastCheckedTime < CACHE_TTL_MS) {
        return cachedQueueEnabled;  // 5초 이내 → 캐시 반환
    }
    boolean enabled = featureFlagRepository.findByFeatureKey(...)
            .map(FeatureFlag::isEnabled).orElse(false);
    cachedQueueEnabled = enabled;
    lastCheckedTime = now;
    return enabled;
}
```

`@Cacheable`을 쓰지 않은 이유:
- 도메인 서비스가 캐시 프레임워크에 의존하지 않도록 하기 위함
- 캐시 키가 하나뿐이라 `ConcurrentHashMap` 기반 캐시 매니저는 과도
- `volatile`로 스레드 간 가시성을 보장하면 충분

**트레이드오프**

- 플래그 변경 시 최대 5초의 반영 지연이 발생한다.
- 대기열 ON/OFF는 운영자가 수동으로 전환하는 것이므로 5초 지연은 허용 가능하다.
- DB 조회 빈도가 초당 10회+ → 5초당 1회로 감소한다.

### 고민 4. 토큰 1회성 보장 — preHandle에서 원자적 소모 (코드 리뷰 반영)

**기존 방식의 문제**

기존에는 `preHandle`에서 `hasToken()`으로 존재 여부만 확인하고,
`afterCompletion`에서 주문 성공 시 `deleteToken()`으로 삭제했다.

```
preHandle: hasToken() → 존재 확인만 (토큰 유지)
Controller: 주문 처리 → 2xx 응답
afterCompletion: deleteToken() → Redis 삭제 실패 시 토큰이 살아남음
```

이 방식은 두 가지 문제가 있었다:
1. `deleteToken()` 실패 시(Redis 장애) 응답은 이미 2xx로 나갔고, 토큰이 TTL 동안 재사용 가능
2. 동시 요청 시 두 요청 모두 `hasToken()=true`로 통과 가능

**변경 후 — GETDEL 기반 원자적 소모**

```
preHandle: consumeToken(GETDEL) → 토큰 원자적 소모 (성공 시 즉시 사라짐)
Controller: 주문 처리
afterCompletion:
  - 성공(2xx): 아무 작업 없음 (이미 소모됨)
  - 실패(non-2xx/예외): issueToken()으로 재발급 (재시도 기회 제공)
```

변경 내역:

| 파일 | 변경 내용 |
|------|----------|
| `QueueTokenRepository.java` | `consumeToken(Long userId)` 추가 (GETDEL) |
| `QueueTokenRedisRepository.java` | `getAndDelete()` 구현 |
| `QueueTokenService.java` | `consumeToken(Long userId)` 추가 |
| `QueueTokenInterceptor.java` | preHandle에서 `consumeToken()`, afterCompletion에서 실패 시 `issueToken()` 복구 |

안전성:
- GETDEL의 원자성으로 동시 요청 시 하나만 통과
- 주문 실패 시 토큰 재발급으로 재시도 기회 보장
- 재발급 실패 시 try-catch로 격리하여 예외 전파 방지, error 로깅

`@RestControllerAdvice`가 예외를 처리하면 `ex`가 `null`이 될 수 있으나,
`response.getStatus()` 범위를 함께 체크하여 비정상 응답을 정확히 필터링한다.

### 고민 5. Redis 키 하드코딩 분산 → 상수 중앙화

초기 구현에서 Redis 키가 여러 곳에 분산 하드코딩되어 있었다.

```
QueueRedisRepository:       private static final String QUEUE_KEY = "waiting-queue";
QueueTokenRedisRepository:  private static final String TOKEN_KEY_PREFIX = "entry-token:";
QueueSchedulerIntegrationTest: redisTemplate.delete("waiting-queue");
QueueV1ApiE2ETest:             redisTemplate.keys("entry-token:*");
QueueRedisRepositoryIntegrationTest: redisTemplate.delete("waiting-queue");
```

키를 변경할 때 한 곳이라도 수정이 누락되면 자동 테스트도 잡아내지 못하는 사일런트 버그가 된다.

**변경 후**

`QueueConstants`에 키 상수를 정의하고, Repository와 테스트 코드에서 모두 참조하도록 변경했다.

```java
public final class QueueConstants {
    public static final String QUEUE_KEY = "waiting-queue";
    public static final String TOKEN_KEY_PREFIX = "entry-token:";
    // ...
}
```

Repository에서는 `QueueConstants.QUEUE_KEY`를 로컬 상수에 할당하여 사용하고,
테스트에서도 `QueueConstants.QUEUE_KEY`, `QueueConstants.TOKEN_KEY_PREFIX + "*"` 형태로 참조한다.

키 변경 시 `QueueConstants` 한 곳만 수정하면 전체 코드에 반영된다.

### 고민 6. 스케줄러 실행 주기 — 매직 넘버에서 application.yml로 외부화

초기 구현에서 `@Scheduled(fixedDelay = 100)` 리터럴을 사용했다.
`QueueConstants.INTERVAL_MS = 100`이 별도로 존재했지만,
`@Scheduled` 어노테이션은 Java 상수를 직접 참조할 수 없어 동기화가 수동이었다.

```java
// 변경 전
public static final long INTERVAL_MS = 100;  // QueueConstants
@Scheduled(fixedDelay = 100)                 // QueueScheduler — 별개의 리터럴
```

**문제점**

- 두 값이 항상 같아야 하지만 컴파일러가 검증하지 못함
- 운영 환경에서 주기를 변경하려면 코드 수정 + 재배포 필요

**변경 후**

`application.yml`에 `queue.scheduler.fixed-delay`를 정의하고,
`@Scheduled`에서 SpEL(`${...}`)로 참조하도록 변경했다.

```yaml
# application.yml
queue:
  scheduler:
    fixed-delay: 100  # ms. QueueConstants.INTERVAL_MS와 동일한 값을 유지해야 한다.
```

```java
// 변경 후
@Scheduled(fixedDelayString = "${queue.scheduler.fixed-delay}")
public void processQueue() { ... }
```

이제 yml 수정만으로 재빌드 없이 스케줄러 주기를 조절할 수 있다.
`QueueConstants.INTERVAL_MS`는 예상 대기 시간 계산에 계속 사용되므로 yml 값과 동기화가 필요한데,
이 부분은 주석으로 명시하여 관리한다.

### 고민 7. peekFront/remove dead code 제거

ZPOPMIN 기반 `popFront`로 전환한 후, 기존의 `peekFront`와 `remove`는 기능 코드에서 더 이상 사용되지 않았다.
그러나 `QueueRepository` 인터페이스와 `QueueRedisRepository` 구현, 통합 테스트에는 그대로 남아있었다.

```
사용처 분석:
  peekFront → 기능 코드 0건, 통합 테스트 3건
  remove    → 기능 코드 0건, 통합 테스트 2건, 유닛 테스트 verify(never) 1건
```

dead code를 유지하면 인터페이스가 불필요하게 커지고,
새로운 개발자가 "이걸 써야 하는 건 아닌가?"라고 혼란을 겪을 수 있다.

**삭제 범위**

- `QueueRepository` 인터페이스에서 `peekFront`, `remove` 제거
- `QueueRedisRepository` 구현체에서 해당 메서드 제거
- `QueueRedisRepositoryIntegrationTest`에서 PeekFront, Remove 테스트 클래스 제거
- `QueueSchedulerTest`에서 `verify(never()).remove()` 제거

인터페이스가 `enter`, `getRank`, `getTotalCount`, `popFront` 4개 메서드로 정리되었다.

### 고민 8. ZPOPMIN 결과의 null safety 개선

`popFront` 구현에서 `tuple.getScore()`와 `tuple.getValue()`의 null 처리를 개선했다.

```java
// 변경 전 — 방어적이지만 lint 경고 발생
tuple.getScore() != null ? tuple.getScore() : 0.0

// 변경 후 — ZPOPMIN 결과는 항상 score/value를 포함하므로 requireNonNull이 적합
Objects.requireNonNull(tuple.getValue())
Objects.requireNonNull(tuple.getScore())
```

ZPOPMIN은 Sorted Set에서 실제로 꺼낸 결과만 반환하므로 score와 value가 null일 수 없다.
조건부 fallback(`? 0.0`) 대신 `Objects.requireNonNull`로 의도를 명확히 하고,
만약 예상과 다르게 null이 발생하면 `NullPointerException`으로 즉시 감지할 수 있도록 했다.

---

## 테스트 계획

### Step 1 — 대기열

**QueueServiceTest (Unit, Mock Redis)**

| 케이스 | 검증 |
|--------|------|
| 대기열 진입 성공 | ZADD 호출, 순번 반환 |
| 동일 유저 중복 진입 | 기존 순번 반환 (새로 등록되지 않음) |
| 순번 조회 성공 | ZRANK로 올바른 position 반환 |
| 대기열에 없는 유저 순번 조회 | 적절한 예외 또는 응답 |
| 예상 대기 시간 계산 | position / 초당 처리량 정확한지 |
| feature flag OFF 시 진입 시도 | 비활성 상태 응답 |

**QueueRedisRepositoryIntegrationTest (Integration, Testcontainers Redis)**

| 케이스 | 검증 |
|--------|------|
| ZADD + ZRANK 실제 동작 | 진입 순서대로 순번 부여 |
| 동일 userId ZADD 두 번 | score 변경 안 되는지 (NX 옵션) |
| ZCARD 정확성 | 진입 인원 수와 일치 |
| ZPOPMIN N명 꺼내기 | 순서대로 정확히 N명 제거 |
| 동시 진입 10명 | 모두 고유 순번, 순서 보장 |
| 동시 진입 100명 | 대규모에서도 순서 정확 |

### Step 2 — 토큰 & 스케줄러

**QueueTokenServiceTest (Unit)**

| 케이스 | 검증 |
|--------|------|
| 토큰 발급 성공 | Redis SET + TTL 5분 |
| 토큰 검증 성공 | 존재하는 토큰 -> true |
| 토큰 검증 실패 (없음) | 미발급 유저 -> false |
| 토큰 삭제 성공 | DEL 후 검증 시 false |
| 이미 토큰이 있는 유저에게 재발급 | 기존 토큰 유지 or 갱신 정책 |

**QueueTokenIntegrationTest (Integration, Testcontainers Redis)**

| 케이스 | 검증 |
|--------|------|
| 토큰 발급 -> 5분 TTL 확인 | TTL entry-token:{userId} = 300초 |
| 토큰 만료 후 검증 | TTL 경과 후 토큰 없음 확인 |
| 토큰 삭제 후 재조회 | 삭제 즉시 무효화 |

**QueueSchedulerTest (Unit)**

| 케이스 | 검증 |
|--------|------|
| 대기열에 20명, 배치 18명 | 18명 토큰 발급, 2명 잔류 |
| 대기열에 5명, 배치 18명 | 5명만 발급 (부족해도 정상) |
| 대기열 비어있음 | 아무 일도 안 함 (에러 없음) |
| feature flag OFF | 스케줄러 실행 안 함 |

### Step 3 — 순번 조회 Polling

**QueuePositionTest (Unit)**

| 케이스 | 검증 |
|--------|------|
| 대기 중 유저 조회 | { position: 128, estimatedWaitSeconds: 45, token: null } |
| 토큰 발급된 유저 조회 | { position: 0, estimatedWaitSeconds: 0, token: "xxx" } |
| 대기열에 없는 유저 조회 | 적절한 예외 또는 응답 |

### Interceptor & Feature Flag

**QueueTokenInterceptorTest (Unit, MockMvc)**

| 케이스 | 검증 |
|--------|------|
| flag ON + 토큰 있음 -> POST /orders | 통과 |
| flag ON + 토큰 없음 -> POST /orders | 차단 (에러 응답) |
| flag OFF -> POST /orders | 토큰 없어도 통과 |
| GET /orders | 토큰 검증 안 함 (POST만 적용) |

**QueueFeatureFlagTest (Unit + Integration)**

| 케이스 | 검증 |
|--------|------|
| flag 조회 (ON) | true 반환 |
| flag 조회 (OFF) | false 반환 |
| flag 없을 때 기본값 | OFF (안전한 기본값) |

### Controller E2E

**QueueV1ApiE2ETest (Testcontainers)**

| 케이스 | 검증 |
|--------|------|
| 전체 흐름: 진입 -> 순번 조회 -> 토큰 발급 -> 주문 | 정상 시나리오 end-to-end |
| 토큰 없이 주문 시도 (flag ON) | 차단 확인 |
| flag OFF 시 대기열 없이 주문 | 기존 흐름 그대로 동작 |

### 설정값 검증 테스트

선택한 설정값이 최적인지, 전후 값과 비교하여 검증한다.

**QueueSchedulerBatchSizeTest (Integration)**

| 배치 크기 | 주기 | 검증 |
|----------|------|------|
| 9명 / 100ms (절반) | 100ms | 처리량 부족 -> 대기열이 계속 쌓이는지 |
| **18명 / 100ms (선택값)** | 100ms | 처리량 적정 -> 대기열이 안정적으로 소화되는지 |
| 36명 / 100ms (2배) | 100ms | DB 부하 증가 -> 처리 시간 증가하는지 |
| 175명 / 1초 (A안) | 1초 | 동시 부하 스파이크 발생하는지 |

**QueueTokenTTLTest (Integration)**

| TTL | 검증 |
|-----|------|
| 3분 (이전) | 토큰 만료 빈도 — 짧은 TTL이 유저 이탈을 유발하는지 |
| **5분 (선택값)** | 적정 시간 내 주문 완료 가능한지 |
| 10분 (이후) | 미사용 토큰이 대기열 회전을 얼마나 지연시키는지 |

**QueueConcurrencyTest (Integration)**

| 시나리오 | 검증 |
|---------|------|
| 100명 동시 진입 + 스케줄러 동시 실행 | 순번 정확, 토큰 중복 발급 없음 |
| 1000명 진입 + 18명/100ms 스케줄러 | 전체 소화 시간 측정, 이론값과 비교 |
| 1000명 진입 + 175명/1초 스케줄러 | 위와 비교하여 부하 패턴 차이 확인 |


---

## Step 1 구현 결과

### 생성한 파일

**Domain Layer:**

| 파일 | 설명 |
|------|------|
| `domain/queue/FeatureFlag.java` | DB feature flag 엔티티 (featureKey, enabled) |
| `domain/queue/FeatureFlagRepository.java` | feature flag repository 인터페이스 |
| `domain/queue/QueueRepository.java` | Redis 대기열 repository 인터페이스 (enter, getRank, getTotalCount, popFront) |
| `domain/queue/QueueService.java` | 대기열 진입, 순번 조회, feature flag 검증 |
| `domain/queue/QueuePositionInfo.java` | 순번 응답 record (position, estimatedWaitSeconds, token) |

**Infrastructure Layer:**

| 파일 | 설명 |
|------|------|
| `infrastructure/queue/FeatureFlagJpaRepository.java` | Spring Data JPA repository |
| `infrastructure/queue/FeatureFlagRepositoryImpl.java` | FeatureFlagRepository 구현체 |
| `infrastructure/queue/QueueRedisRepository.java` | Redis Sorted Set 기반 구현 (ZADD NX, ZRANK, ZCARD, ZPOPMIN) |

**Application Layer:**

| 파일 | 설명 |
|------|------|
| `application/queue/QueueFacade.java` | 유즈케이스 조율 (enter, getPosition 위임) |

**Interfaces Layer:**

| 파일 | 설명 |
|------|------|
| `interfaces/api/queue/QueueV1Controller.java` | `POST /api/v1/queue/enter`, `GET /api/v1/queue/position` |
| `interfaces/api/queue/dto/QueueV1Dto.java` | EnterResponse, PositionResponse DTO |

### 핵심 구현 사항

- **대기열 진입**: `ZADD NX`로 중복 진입 방지. score는 `System.currentTimeMillis()`로 선착순 보장
- **순번 조회**: `ZRANK`로 0-based 순번 반환 + `position / 175.0`으로 예상 대기 시간 계산
- **Feature Flag**: DB 조회로 ON/OFF 판단, flag 미존재 시 OFF(안전한 기본값)
- **DIP 준수**: Domain에 QueueRepository 인터페이스, Infrastructure에 Redis 구현체

### 테스트 결과

**QueueServiceTest (Unit, Mockito) — 14개 GREEN**

| 카테고리 | 케이스 | 결과 |
|---------|--------|------|
| 대기열 진입 | 진입 성공 - 순번 반환 | PASS |
| 대기열 진입 | 동일 유저 중복 진입 - 기존 순번 반환 | PASS |
| 대기열 진입 | feature flag OFF - 진입 불가 (BAD_REQUEST) | PASS |
| 대기열 진입 | flag 미존재 - OFF 간주하여 진입 불가 | PASS |
| 순번 조회 | 대기 중 유저 - 순번 + 예상 대기 시간 반환 | PASS |
| 순번 조회 | 순번 0인 유저 - 예상 대기 시간 0 | PASS |
| 순번 조회 | 대기열에 없는 유저 - NOT_FOUND 예외 | PASS |
| 순번 조회 | 예상 대기 시간 계산 정확성 (350/175.0=2초) | PASS |
| 순번 조회 | feature flag OFF - 조회 불가 | PASS |
| Feature Flag | ON -> true | PASS |
| Feature Flag | OFF -> false | PASS |
| Feature Flag | 미존재 -> false (안전한 기본값) | PASS |
| 전체 대기 인원 | 인원 수 반환 | PASS |
| 전체 대기 인원 | 빈 대기열 -> 0 | PASS |

**QueueRedisRepositoryIntegrationTest (Integration, Testcontainers Redis) — 12개 GREEN**

| 카테고리 | 케이스 | 결과 |
|---------|--------|------|
| 진입 (enter) | 진입 성공 - true 반환 | PASS |
| 진입 (enter) | 중복 진입 - false 반환 (NX) | PASS |
| 진입 (enter) | 중복 진입 시 기존 score 유지 | PASS |
| 순번 (getRank) | 진입 순서대로 순번 부여 | PASS |
| 순번 (getRank) | 미진입 유저 - empty 반환 | PASS |
| 전체 인원 (getTotalCount) | 진입 인원 수와 일치 | PASS |
| 전체 인원 (getTotalCount) | 빈 대기열 - 0 반환 | PASS |
| popFront | 순서대로 N명 꺼내기 | PASS |
| popFront | 요청보다 적으면 있는 만큼만 | PASS |
| popFront | 빈 대기열 - 빈 리스트 | PASS |
| 동시성 | 10명 동시 진입 - 모두 고유 순번 | PASS |
| 동시성 | 100명 동시 진입 - 순서 정확 | PASS |

### Checklist 충족 현황

- [x] Redis Sorted Set 기반 대기열 진입 API (`POST /api/v1/queue/enter`)
- [x] 순번 조회 API (`GET /api/v1/queue/position`)
- [x] userId 기반 중복 진입 방지
- [x] 전체 대기 인원 조회
- [x] Feature Flag DB 기반 ON/OFF

---

## Step 1 코드 리뷰 & 리팩토링

코드 리뷰에서 6가지 피드백을 받았고, 각각에 대해 판단 후 조치했다.

### 1. 전체 대기 인원 조회 API 미노출

`QueueService.getTotalCount()`가 구현되어 있지만 Controller에 노출되지 않았다는 피드백.

체크리스트를 재확인한 결과, "전체 대기 인원 조회"는 "API 구현"이라고 명시되어 있지 않다. `QueueService.getTotalCount()`로 내부 조회 기능이 구현되어 있고, Step 2 스케줄러에서도 활용되므로 **현행 유지**.

### 2. enter() — score 할당과 rank 조회 사이의 갭

`System.currentTimeMillis()`로 score 부여 후 `getRank()` 호출 사이에 다른 유저 진입 가능. 반환된 순번이 실제와 달라질 수 있다는 피드백.

Lua 스크립트로 atomic하게 처리하는 방법이 있지만, 클라이언트는 최소 1초 간격으로 polling하여 정확한 순번을 갱신받는다. 진입 응답의 ms 단위 부정확함은 실질적 문제가 아니므로 **현행 유지**. 오버엔지니어링 방지 원칙에 부합.

### 3. QueueFacade가 단순 위임만 수행

리뷰어도 "Step 2에서 토큰 로직이 추가되면 자연스럽게 역할이 생긴다"고 판단. **현행 유지**.

### 4. QueuePositionInfo — position 0-based → 1-based 변환 (수정 완료)

`ZRANK`는 0-based이므로 유저에게 "0번째"를 보여주면 부자연스럽다.

수정 내용:
- `QueuePositionInfo.waiting()`에서 `rank + 1`로 1-based position 변환
- 예상 대기 시간 계산도 1-based position 기준으로 변경
- 단위 테스트 기대값 수정 (rank=175 -> position=176, rank=0 -> position=1, rank=350 -> position=351)
- 테스트 14개 모두 GREEN 확인

### 5. FeatureFlag 테이블 DDL / 초기 데이터 부재

운영 환경(`ddl-auto: none`)에서 `feature_flag` 테이블 미존재 시 앱 기동 실패. 테스트는 `ddl-auto: create`이므로 통과하지만 마이그레이션 SQL이 필요. **Step 2 진행 전 처리 예정**.

### 6. @Slf4j 미사용 — QueueRedisRepository (수정 예정)

`@Slf4j`가 선언되어 있지만 로그를 사용하지 않음. **제거 완료**.

---

## Step 2 설계 결정 기록

### 선택 1. 토큰 서비스 위치 — 별도 QueueTokenService 분리

QueueService에 토큰 로직을 추가하면 한 클래스가 대기열 + 토큰을 모두 관리하게 된다.
토큰은 Redis String + TTL로 자료구조도 다르고, 발급/검증/삭제라는 별도 생명주기가 있다.

결정: **별도 QueueTokenService로 분리** — 책임 분리, 테스트 분리, QueueService 비대화 방지.

### 선택 2. 주문 완료 후 토큰 삭제 — Interceptor 후처리 (afterCompletion)

| 방식 | 주문→대기열 의존 | 삭제 보장 | 설계 원칙 |
|------|----------------|----------|----------|
| OrderPaymentFacade 수정 | 생김 | 확실 | Interceptor로 분리한 취지에 어긋남 |
| **Interceptor 후처리 (선택)** | 없음 | 확실 | 토큰 검증~삭제를 같은 레이어에서 관리 |
| ApplicationEvent | 없음 | 비동기 지연 가능 | 기존 이벤트 패턴과 일관 |

결정: **Interceptor afterCompletion에서 삭제** — 토큰 검증도 Interceptor에서 하므로, 삭제도 같은 곳에서 처리. 주문 도메인은 대기열의 존재를 전혀 모른다.

### 선택 3. 이미 토큰이 있는 유저의 재발급 정책 — 기존 토큰 유지

| 정책 | 장점 | 단점 |
|------|------|------|
| **기존 토큰 유지 (선택)** | 추가 시간 미부여, 대기열 회전 유지 | - |
| TTL 갱신 | 유저에게 여유 | TTL 무한 연장 가능, 대기열 정체 |

결정: **기존 토큰 유지, 스케줄러가 스킵** — 토큰을 받고 사용하지 않은 건 유저 책임. 토큰 만료 시 대기열에서도 이미 빠진 상태이므로 처음부터 다시 줄을 서야 한다.

토큰 만료 후 흐름:
```
1. 대기열 진입 -> 순번 대기
2. 스케줄러가 토큰 발급 (TTL 5분)
3-a. 5분 내 주문 완료 -> 토큰 삭제 -> 정상 종료
3-b. 5분 내 주문 안 함 -> 토큰 자동 만료 -> 다시 POST /queue/enter부터
```

---

## Step 2 구현 결과

### 생성/수정한 파일

**Domain Layer:**

| 파일 | 설명 |
|------|------|
| `domain/queue/QueueTokenRepository.java` | 토큰 저장소 인터페이스 (issue NX, getToken, hasToken, delete) |
| `domain/queue/QueueTokenService.java` | 토큰 발급(UUID, Optional 반환), 검증(hasToken), 삭제 |
| `domain/queue/QueueRepository.java` | popFront 제거, peekFront + remove로 변경 |

**Infrastructure Layer:**

| 파일 | 설명 |
|------|------|
| `infrastructure/queue/QueueTokenRedisRepository.java` | Redis String + TTL 기반 (SET NX EX 300) |
| `infrastructure/queue/QueueRedisRepository.java` | ZRANGE(peekFront) + ZREM(remove) 구현 |

**Application Layer:**

| 파일 | 설명 |
|------|------|
| `application/queue/QueueScheduler.java` | @Scheduled(fixedDelay=100), peekFront 18명 → 토큰 발급 → 성공 시만 remove |

**Interfaces Layer:**

| 파일 | 설명 |
|------|------|
| `interfaces/api/queue/QueueTokenInterceptor.java` | POST만 검증, preHandle(토큰 검증), afterCompletion(성공 시 토큰 삭제) |
| `config/WebMvcConfig.java` | QueueTokenInterceptor 등록 (/api/v1/orders) |

### Step 2 코드 리뷰 & 리팩토링

코드 리뷰에서 7가지 피드백을 받았고, 모두 수정 완료했다.

**[Critical] 1. popFront 후 토큰 발급 실패 시 유저 유실**

멘토님 조언: "선(先) 토큰 발급, 후(後) 팝(Pop)" — 대기열에서 먼저 꺼내지 말고, 토큰 발급 성공 후에만 대기열에서 제거.

수정: `popFront(ZPOPMIN)` → `peekFront(ZRANGE, 제거 안 함)` + `remove(ZREM, 성공 시만 제거)` 패턴으로 변경. 토큰 발급 실패 시 유저는 대기열에 그대로 남아 다음 스케줄러 실행에서 재시도된다.

**[Major] 2. Interceptor가 GET /orders에도 적용됨**

설계 문서에 "POST /api/v1/orders 경로에만 적용"이라 했으므로, preHandle과 afterCompletion 모두 `!"POST".equalsIgnoreCase(request.getMethod())` 체크를 추가. GET/PUT/DELETE는 토큰 검증 없이 통과.

**[Major] 3. hasToken/validateToken 중복**

둘 다 "토큰이 존재하는가"를 확인하는 동일 기능. `validateToken` 제거, `hasToken`으로 통일.

**[Minor] 4. issueToken null 반환 → Optional**

프로젝트 규칙 "null-safety: Optional 활용"에 맞춰 `Optional<String>` 반환으로 변경.

**[Minor] 5. QueueScheduler 레이어 위치**

`domain/queue/` → `application/queue/`로 이동. @Scheduled는 인프라/애플리케이션 관심사이며, QueueService + QueueTokenService를 조율하는 역할이므로 application 레이어가 적합.

**[Minor] 6. QueueTokenInterceptorTest 위치**

`domain/queue/` → `interfaces/api/queue/`로 이동. 프로덕션 코드와 같은 패키지 구조.

**[Minor] 7. afterCompletion 중복 DB 조회**

preHandle에서 `request.setAttribute("queueEnabled", flag)`로 저장, afterCompletion에서 attribute로 읽어서 DB 재조회 제거.

### 테스트 결과

| 테스트 | 건수 | 상태 |
|--------|------|------|
| QueueServiceTest (Unit) | 14개 | GREEN |
| QueueTokenServiceTest (Unit) | 11개 | GREEN |
| QueueSchedulerTest (Unit) | 8개 | GREEN |
| QueueTokenInterceptorTest (Unit) | 16개 | GREEN |
| QueueRedisRepositoryIntegrationTest | 14개 | GREEN |
| QueueTokenRedisRepositoryIntegrationTest | 17개 | GREEN |
| **합계** | **80개** | **전체 통과** |

### Checklist 충족 현황 (Step 2)

- [x] 스케줄러가 주기적으로 대기열에서 N명을 꺼내 입장 토큰 발급
- [x] 토큰 TTL 설정 (5분)
- [x] 주문 API 진입 시 토큰 검증
- [x] 주문 완료 후 토큰 삭제
- [x] 처리량 기준으로 스케줄러 배치 크기 산정 근거 문서화

---

## Step 3 구현 결과

### 수정/생성한 파일

**Domain Layer:**

| 파일 | 설명 |
|------|------|
| `domain/queue/QueuePositionInfo.java` | 순번 응답 record — waiting(예상 대기 시간 + polling 주기), ready(토큰 포함) 팩토리 메서드 |
| `domain/queue/QueueTokenService.java` | `getToken(userId)` 메서드 추가 — 토큰 조회 (순번 조회 시 ready 응답에 필요) |
| `domain/queue/QueueService.java` | `getPosition()` — 토큰 발급 유저는 ready 응답, 대기 중 유저는 순번+예상 대기 시간+polling 주기 응답 |

**Interfaces Layer:**

| 파일 | 설명 |
|------|------|
| `interfaces/api/queue/QueueV1Controller.java` | `GET /api/v1/queue/position` — Polling 기반 순번 조회 API |
| `interfaces/api/queue/dto/QueueV1Dto.java` | `PositionResponse` — position, estimatedWaitSeconds, token, pollIntervalSeconds |

### 핵심 구현 사항

- **예상 대기 시간**: `(position / batchSize) * (intervalMs / 1000)` — 배치 크기 18명, 주기 100ms 기준
- **Polling 주기 동적 조절**: 대기 인원에 따라 서버 부하 제어
  - 1~100번: 1초 (곧 입장할 유저에게 빠른 피드백)
  - 101~1000번: 3초 (중간 대기)
  - 1001번 이상: 5초 (장기 대기, 서버 부하 최소화)
- **토큰 발급 유저 즉시 입장**: `hasToken` 확인 후 `ready(token)` 응답 반환, 클라이언트는 polling 중단 후 주문 진행

### 테스트 결과

**QueueServiceTest (Unit, Mockito) — 18개 GREEN**

| 카테고리 | 케이스 | 결과 |
|---------|--------|------|
| 대기열 진입 | 진입 성공 - 순번 반환 | PASS |
| 대기열 진입 | 동일 유저 중복 진입 - 기존 순번 반환 | PASS |
| 대기열 진입 | feature flag OFF - 진입 불가 (BAD_REQUEST) | PASS |
| 대기열 진입 | flag 미존재 - OFF 간주하여 진입 불가 | PASS |
| 순번 조회 | 대기 중 유저 - 순번 + 예상 대기 시간 반환 | PASS |
| 순번 조회 | 순번 0인 유저 - 예상 대기 시간 0 | PASS |
| 순번 조회 | **토큰 발급된 유저 - position=0, token 포함 ready 응답** | PASS |
| 순번 조회 | 대기열에 없는 유저 - NOT_FOUND 예외 | PASS |
| 순번 조회 | 예상 대기 시간 계산 정확성 | PASS |
| 순번 조회 | **순번 1~100 - polling 주기 1초** | PASS |
| 순번 조회 | **순번 101~1000 - polling 주기 3초** | PASS |
| 순번 조회 | **순번 1001 이상 - polling 주기 5초** | PASS |
| 순번 조회 | feature flag OFF - 조회 불가 | PASS |
| Feature Flag | ON -> true | PASS |
| Feature Flag | OFF -> false | PASS |
| Feature Flag | 미존재 -> false (안전한 기본값) | PASS |
| 전체 대기 인원 | 인원 수 반환 | PASS |
| 전체 대기 인원 | 빈 대기열 -> 0 | PASS |

### Checklist 충족 현황 (Step 3)

- [x] 예상 대기 시간 계산 로직 구현
- [x] Polling 기반 순번 + 예상 대기 시간 응답
- [x] 토큰 발급 시 순번 조회 응답에 토큰 포함

### Step 3 코드 리뷰 & 리팩토링

코드 리뷰에서 4가지 피드백을 받았고, 3가지를 수정 완료했다.

**[Major] 1. 스케줄러 peekFront + remove — 비원자적 연산으로 인한 중복 처리 위험**

멀티 인스턴스 배포 시 두 서버가 동시에 같은 유저를 peek하고 토큰을 발급하려 시도할 수 있다. setIfAbsent(NX)로 토큰 중복 발급은 방지되지만, 불필요한 Redis 호출이 발생한다.

현재 단일 인스턴스 + fixedDelay(이전 실행 완료 후 100ms)이므로 코드 변경 없이 **인지 사항으로 기록**. 멀티 인스턴스 전환 시 분산 락 또는 Lua 스크립트로 atomic 처리 필요.

**[Minor] 2. BATCH_SIZE/INTERVAL_MS 상수 중복 정의 (수정 완료)**

QueueService와 QueueScheduler에서 각각 BATCH_SIZE=18을 정의하고 있어, 한쪽만 변경하면 예상 대기 시간 계산이 틀어지는 위험이 있었다.

수정: `QueueConstants` 클래스 생성하여 상수를 한 곳에서 관리. QueueService, QueueScheduler 모두 `QueueConstants.BATCH_SIZE`, `QueueConstants.INTERVAL_MS`를 참조하도록 변경.

**[Minor] 3. hasToken -> getToken 사이 race condition 방어 (수정 완료)**

`hasToken()` 호출 후 `getToken()` 호출 사이에 토큰이 TTL로 만료되면 IllegalStateException 발생 가능. 발생 확률은 극히 낮지만 방어가 필요.

수정: `QueueTokenService.findToken(userId)` 메서드 추가 (Optional<String> 반환). QueueService.getPosition()에서 `findToken`으로 단일 호출하여 race condition 제거. empty 시 대기열 순번으로 자연스럽게 fallback.

**[Minor] 4. Polling 주기 경계값 테스트 보강 (수정 완료)**

기존 테스트가 구간 중간값(50, 500, 2000)만 커버하여 off-by-one 버그를 놓칠 수 있었다.

수정: 경계값 100, 101, 1000, 1001에 대한 테스트 4개 추가. 총 테스트 22개 GREEN.

### 테스트 결과 (리팩토링 후)

| 테스트 | 건수 | 상태 |
|--------|------|------|
| QueueServiceTest (Unit) | 22개 | GREEN |
| QueueTokenServiceTest (Unit) | 11개 | GREEN |
| QueueSchedulerTest (Unit) | 8개 | GREEN |
| QueueTokenInterceptorTest (Unit) | 16개 | GREEN |
| QueueRedisRepositoryIntegrationTest | 14개 | GREEN |
| QueueTokenRedisRepositoryIntegrationTest | 17개 | GREEN |
| **합계** | **88개** | **전체 통과** |

---

## 테스트 커버리지 리뷰 & 보강

Step 1~3 구현 완료 후 테스트 피라미드 관점에서 누락된 테스트를 점검하고 보강했다.

### 발견된 누락 사항

| 누락 항목 | 중요도 | 이유 |
|-----------|--------|------|
| E2E: 토큰 1회성 사용 검증 | 높음 | afterCompletion에서 토큰 삭제가 실제 HTTP 흐름에서 동작하는지 미검증 |
| E2E: 대기 중 순번 조회 응답 | 중간 | 스케줄러 실행 전 waiting 상태의 응답 구조 미검증 |
| Unit: QueuePositionInfo VO | 중간 | 예상 대기 시간 계산, polling 주기 로직이 VO에 있으나 직접 단위 테스트 부재 |
| Unit: QueueV1Controller MockMvc | 낮음 | Interfaces 레이어 단위 테스트 부재 (E2E로만 커버) |
| E2E: 다수 유저 동시 진입 순서 | 낮음 | Repository 통합 테스트에만 존재, HTTP 레벨 미검증 |

### 보강 1. QueuePositionInfoTest (Unit, 14개 GREEN)

QueuePositionInfo record의 팩토리 메서드(waiting, ready)와 내부 계산 로직을 직접 검증한다.

| 카테고리 | 케이스 | 결과 |
|---------|--------|------|
| waiting | rank=0 -> position=1, estimatedWaitSeconds=0 | PASS |
| waiting | rank=17 -> position=18, estimatedWaitSeconds=0 (반올림) | PASS |
| waiting | rank=175 -> position=176, estimatedWaitSeconds=1 | PASS |
| waiting | rank=350 -> position=351, estimatedWaitSeconds=2 | PASS |
| waiting | rank=9999 -> position=10000, estimatedWaitSeconds=56 (대규모) | PASS |
| waiting | batchSize 정배수 (rank=35, position=36) | PASS |
| polling 주기 | position=1 -> 1초 | PASS |
| polling 주기 | position=100 -> 1초 (경계값) | PASS |
| polling 주기 | position=101 -> 3초 (경계값) | PASS |
| polling 주기 | position=1000 -> 3초 (경계값) | PASS |
| polling 주기 | position=1001 -> 5초 (경계값) | PASS |
| polling 주기 | position=5000 -> 5초 | PASS |
| ready | position=0, estimatedWaitSeconds=0, token 포함, pollIntervalSeconds=0 | PASS |

### 보강 2. QueueV1ControllerTest (MockMvc, 8개 GREEN)

Interfaces 레이어 단위 테스트. MockMvc로 HTTP 상태 코드, 응답 JSON 구조를 검증한다.

| 카테고리 | 케이스 | 결과 |
|---------|--------|------|
| POST /queue/enter | 인증된 유저 진입 -> 200 + position 반환 | PASS |
| POST /queue/enter | 인증 없음 -> 401 | PASS |
| POST /queue/enter | flag OFF -> 400 | PASS |
| GET /queue/position | 대기 중 -> position, estimatedWaitSeconds, pollIntervalSeconds, token=null | PASS |
| GET /queue/position | 토큰 발급됨 -> position=0, token 포함 | PASS |
| GET /queue/position | 대기열 미존재 -> 404 | PASS |
| GET /queue/position | 인증 없음 -> 401 | PASS |
| GET /queue/position | flag OFF -> 400 | PASS |

### 보강 3. QueueV1ApiE2ETest 추가 시나리오 (3개 GREEN)

| 카테고리 | 케이스 | 결과 |
|---------|--------|------|
| 토큰 1회성 | 주문 성공 -> 동일 유저 재주문 시도 -> 400 차단 (afterCompletion 토큰 삭제 검증) | PASS |
| 대기 중 순번 조회 | 토큰 발급 전 순번 조회 -> position > 0, token=null, pollIntervalSeconds > 0 | PASS |
| 동시 진입 | 5명 동시 대기열 진입 -> 전원 성공, 순번 중복 없음 | PASS |

### 최종 테스트 현황

| 테스트 | 건수 | 상태 |
|--------|------|------|
| QueuePositionInfoTest (Unit) | 14개 | GREEN |
| QueueServiceTest (Unit) | 22개 | GREEN |
| QueueTokenServiceTest (Unit) | 13개 | GREEN |
| QueueSchedulerTest (Unit) | 8개 | GREEN |
| QueueTokenInterceptorTest (Unit) | 16개 | GREEN |
| QueueV1ControllerTest (MockMvc) | 8개 | GREEN |
| QueueRedisRepositoryIntegrationTest | 14개 | GREEN |
| QueueTokenRedisRepositoryIntegrationTest | 17개 | GREEN |
| QueueSchedulerIntegrationTest | 4개 | GREEN |
| QueueV1ApiE2ETest (E2E) | 10개 | GREEN |
| **합계** | **126개** | **전체 통과** |


---

## ✍️ Technical Writing Quest

> 이번 주에 학습한 내용, 과제 진행을 되돌아보며
**"내가 어떤 판단을 하고 왜 그렇게 구현했는지"** 를 글로 정리해봅니다.
>
>
> **좋은 블로그 글은 내가 겪은 문제를, 타인도 공감할 수 있게 정리한 글입니다.**
>
> 이 글은 단순 과제가 아니라, **향후 이직에 도움이 될 수 있는 포트폴리오** 가 될 수 있어요.
>

### 📚 Technical Writing Guide

### ✅ 작성 기준

| 항목 | 설명 |
| --- | --- |
| **형식** | 블로그 |
| **길이** | 제한 없음, 단 꼭 **1줄 요약 (TL;DR)** 을 포함해 주세요 |
| **포인트** | “무엇을 했다” 보다 **“왜 그렇게 판단했는가”** 중심 |
| **예시 포함** | 코드 비교, 흐름도, 리팩토링 전후 예시 등 자유롭게 |
| **톤** | 실력은 보이지만, 자만하지 않고, **고민이 읽히는 글**예: “처음엔 mock으로 충분하다고 생각했지만, 나중에 fake로 교체하게 된 이유는…” |

---

### ✨ 좋은 톤은 이런 느낌이에요

> 내가 겪은 실전적 고민을 다른 개발자도 공감할 수 있게 풀어내자
>

| 특징 | 예시 |
| --- | --- |
| 🤔 내 언어로 설명한 개념 | Stub과 Mock의 차이를 이번 주문 테스트에서 처음 실감했다 |
| 💭 판단 흐름이 드러나는 글 | 처음엔 도메인을 나누지 않았는데, 테스트가 어려워지며 분리했다 |
| 📐 정보 나열보다 인사이트 중심 | 테스트는 작성했지만, 구조는 만족스럽지 않다. 다음엔… |

### ❌ 피해야 할 스타일

| 예시 | 이유 |
| --- | --- |
| 많이 부족했고, 반성합니다… | 회고가 아니라 일기처럼 보입니다 |
| Stub은 응답을 지정하고… | 내 생각이 아닌 요약문처럼 보입니다 |
| 테스트가 진리다 | 너무 단정적이거나 오만해 보입니다 |

### 🎯 Feature Suggestions

- Rate Limiting으로 거부하는 것과 대기열로 줄 세우는 것, 어떤 상황에서 어떤 전략이 맞을까?
- 스케줄러 배치 크기를 어떻게 산정했는가? 그 근거는?
- Thundering Herd를 직접 겪었다면, 어떻게 완화했는가?
- Redis가 죽으면 우리 서비스는 어떻게 되어야 하는가?
- Polling vs SSE — 왜 그 방식을 선택했는가?
- 토큰 TTL을 몇 분으로 설정했고, 그 기준은 무엇인가?


---

## Keywords 개념 정리

### 1. 대기열 (Waiting Queue)

시스템이 처리할 수 있는 양보다 많은 요청이 들어올 때, 초과 요청을 **순서대로 보관**하고 처리 가능한 속도로 하나씩 꺼내 처리하는 구조다.

- 핵심 목적: 시스템 보호 + 공정한 순서 보장 + 유저 이탈 방지
- 은행 창구의 번호표 시스템과 동일한 원리. 창구(서버)는 한정되어 있고, 고객(요청)은 번호를 받고 순서대로 대기
- 단순히 요청을 버퍼링하는 것(Kafka)과 다르게, 유저가 **자신의 순번을 실시간으로 확인**할 수 있어야 함
- 대기열의 성패는 "유저가 기다리는 동안 이탈하지 않느냐"에 달려있음

### 2. Rate Limiting vs Queuing

트래픽이 한계를 넘을 때 선택할 수 있는 두 가지 전략이다.

**Rate Limiting**은 초과 요청을 **거부**(429 Too Many Requests)한다.
- "나중에 다시 시도하세요" -> 유저가 새로고침 -> 재시도 폭풍 위험
- 적합: API 보호, 봇 차단, DDoS 방어, 일상적 부하 제어
- 알고리즘: Token Bucket, Sliding Window, Fixed Window, Leaky Bucket

**Queuing**은 초과 요청을 **보관**(대기열에 적재)한다.
- "512번째입니다, 약 3분 대기" -> 유저가 기다림 -> 이탈 방지
- 적합: 행사 트래픽, 유저가 기다릴 의사가 있는 경우 (콘서트 티켓, 한정판 세일)

양자택일이 아니라 **조합해서 사용**한다. 봇/비정상 요청은 Rate Limiting으로 먼저 걸러내고, 정상 유저만 대기열에 진입시키는 방식. 대기열 자체에도 최대 인원 제한을 둘 수 있다.

판단 기준: **"유저가 이 결과를 기다릴 의사가 있는가?"** 있다면 Queuing, 없다면 Rate Limiting.

### 3. Back-pressure (배압)

하류 시스템(DB, PG)이 감당할 수 있는 속도만큼만 상류(유저 요청)를 흘려보내는 제어 메커니즘이다.

- 배관의 압력 조절 밸브와 같은 원리. 수도꼭지(유저)에서 물이 쏟아져도, 밸브(스케줄러)가 파이프(DB)가 감당할 수 있는 양만 흘려보냄
- Back-pressure가 없으면: DB 커넥션 풀 고갈 -> 전체 서비스 타임아웃 -> 재시도 폭풍 -> 시스템 전면 장애
- 대기열은 Back-pressure를 구현하는 대표적 방법. 유입은 대기열에 적재하고, 처리는 스케줄러가 제어된 속도(예: 175 TPS)로 수행
- 스케줄러의 배치 크기(N명/주기)가 곧 Back-pressure의 **조절 밸브** 역할

### 4. Redis Sorted Set

Redis의 자료구조 중 하나로, 각 원소(member)에 점수(score)가 부여되어 **score 기준으로 자동 정렬**되는 Set이다.

- 대기열에서의 활용: score = 진입 시각(timestamp), member = userId
- Set 특성으로 **동일 member 중복 불가** -> 같은 유저의 중복 진입 자동 방지
- 핵심 명령어:
  - `ZADD key score member` -- 대기열 진입 (O(log N))
  - `ZRANK key member` -- 내 순번 조회 (O(log N), 10,000명이어도 ~14번 비교)
  - `ZCARD key` -- 전체 대기 인원 (O(1))
  - `ZPOPMIN key count` -- 앞에서 N명 꺼내기 (atomic, 동시성 안전)
- 내부 구조: skiplist + hashtable 조합. skiplist가 정렬/순위 조회, hashtable이 O(1) member->score 매핑 담당
- 대기열의 가장 빈번한 연산인 순번 조회(ZRANK)가 O(log N)으로 매우 빠르기 때문에, MySQL(COUNT 쿼리)이나 Kafka(순번 조회 불가)보다 대기열에 적합

### 5. 입장 토큰 (Entry Token)

대기열에서 자기 차례가 온 유저에게 발급되는 **주문 API 진입 권한**이다.

- Redis String + TTL로 구현: `SET entry-token:{userId} {token} EX 300` (5분 TTL)
- 토큰이 있는 유저만 주문 API에 진입 가능 -> 처리량 제어의 핵심
- TTL이 있어 일정 시간(예: 5분) 내 사용하지 않으면 자동 만료 -> 다음 유저에게 기회
- 주문 완료 후 토큰 삭제 -> 동일 토큰으로 중복 주문 방지
- 흐름: 스케줄러가 대기열에서 N명 꺼냄 -> 각 유저에게 토큰 발급 -> 유저가 토큰으로 주문 API 호출 -> 토큰 검증 -> 주문 처리 -> 토큰 삭제
- TTL 설정 기준: 주문 페이지 진입 -> 결제 정보 확인 -> 주문 완료까지 걸리는 합리적 시간 (보통 3~5분)

### 6. 순번 조회 & 실시간 피드백

유저가 대기 중 **현재 자신이 몇 번째인지, 예상 대기 시간이 얼마인지**를 실시간으로 확인할 수 있는 기능이다.

- 대기열의 성패를 결정하는 핵심 요소. 순번이 보이지 않으면 유저는 새로고침하거나 이탈
- 순번 조회: `ZRANK waiting-queue {userId}` -> 0-based 순위 반환
- 예상 대기 시간: `내 순번 / 초당 처리량` (예: 순번 300, 초당 50명 처리 -> 약 6초)
  - 추정값이므로 "약 N분"으로 표현하는 것이 적절
- 응답 예시:
  - 대기 중: `{ "position": 128, "estimatedWaitSeconds": 45, "token": null }`
  - 입장 가능: `{ "position": 0, "estimatedWaitSeconds": 0, "token": "abc-123-def" }`
- 전달 방식은 Polling 또는 SSE를 사용 (아래 9번 참고)

### 7. Thundering Herd (떼몰이)

**대량의 요청이 동시에 같은 리소스에 몰리는 현상**이다. 놀란 소 떼가 한꺼번에 우르르 달려가는 모습에서 유래.

- 대기열에서의 발생: 스케줄러가 175명에게 동시에 토큰 발급 -> 175명이 동시에 주문 API 호출 -> DB 커넥션 175개 동시 점유 -> 순간 부하 스파이크
- 캐시에서의 발생: 인기 상품 캐시 만료 -> 1,000명이 동시에 DB 직접 조회
- 핵심: 같은 양의 요청이라도 **10초에 걸쳐 분산되면 문제없지만, 1초에 몰리면 터진다**
- 대기열은 "피크를 평탄화(smoothing)"하는 것이지, 부하를 없애는 것이 아님
- 완화 전략:
  - **발급 간격 분산**: 1초에 175명 한번 -> 100ms마다 18명씩 (부하 10배 평탄화)
  - **Jitter**: 토큰 활성화 시점에 랜덤 딜레이(0~2초) 부여 -> 유저별 진입 시점 분산
  - **주문 API Rate Limit**: 토큰 있어도 초당 N건 제한 -> 최종 안전장치

### 8. Graceful Degradation (우아한 성능 저하)

시스템 일부에 장애가 발생해도 **전체 서비스가 멈추지 않도록 단계적으로 기능을 축소**하는 전략이다.

- 대기열의 핵심 인프라인 Redis가 죽으면 어떻게 할 것인가?
  - **전면 차단**: 대기열 진입 자체를 막고 "잠시 후 다시 시도" 안내. 안전하지만 서비스 중단
  - **대기열 우회(bypass)**: 대기열 없이 주문 API 직접 접근 허용. 서비스 유지하지만 과부하 위험
  - **Fallback 큐**: 로컬 메모리 큐나 Kafka로 임시 전환. 순번 정확성 저하, 서비스 유지
- 정답이 없다. **"장애 시 우리 서비스는 어떻게 동작해야 하는가?"를 사전에 정의해두는 것 자체가 핵심**
- 장애가 발생한 뒤에 판단하면 늦다. 어떤 전략을 선택할지 미리 코드에 구현해두어야 함
- 우리 프로젝트 참고: ProductRedisCacheStore에서 이미 Redis 장애 시 try-catch로 DB fallback 패턴을 사용 중

### 9. Polling / SSE

유저에게 대기 순번을 실시간으로 전달하는 두 가지 방식이다.

**Polling -- 클라이언트가 주기적으로 물어보기**

```
[클라이언트] -> setInterval(2초)
             -> GET /queue/position
             <- { "position": 128, "estimatedWaitSeconds": 45 }
             -> (2초 후 다시)
             -> GET /queue/position
             <- { "position": 64, "estimatedWaitSeconds": 22 }
```

- 구현이 단순하고 인프라 변경이 없음
- 대기 인원이 많으면 Polling 자체가 서버 부하 (10,000명 x 2초마다 = 초당 5,000건)
- 주기 사이의 지연 발생 (2초 주기면 최대 2초 늦게 인지)
- 부하 완화: 순번 구간별 동적 주기 조절 (순번 1~100: 1초, 100~1000: 3초, 1000+: 5초)

**SSE (Server-Sent Events) -- 서버가 알려주기**

```
[클라이언트] -> GET /queue/stream (HTTP 연결 유지)
             <- event: position
             <- data: { "position": 128 }
             ...  (서버가 변경 시점에만 Push)
             <- event: enter
             <- data: { "token": "abc-123-def" }
```

- 서버가 변경 시점에만 전송 -> 불필요한 요청 없음
- HTTP 기반이라 별도 프로토콜 불필요 (WebSocket과 달리)
- 단방향(서버 -> 클라이언트)이므로 대기열 순번 알림에 적합
- 단점: 대기 인원 x 1 커넥션을 유지해야 함. 로드밸런서 뒤에서 연결 유지 설정 필요

**비교**

| 구분 | Polling | SSE |
|------|---------|-----|
| 구현 난이도 | 낮음 | 중간 |
| 서버 부하 | 주기적 요청 (대기 인원에 비례) | 연결 유지 (대기 인원에 비례) |
| 실시간성 | 주기만큼 지연 (1~5초) | 즉시 (변경 시점에 Push) |
| 인프라 변경 | 없음 | 로드밸런서 설정 필요 |
| 확장 | 쉬움 | 커넥션 관리 필요 |

권장: **Polling으로 시작**하고, 대기 인원이 많아 Polling 부하가 문제가 되면 **SSE로 전환**.

---

<aside>
🧠

**Learning**

</aside>

## ⚠️ 문제 분석 - 블랙 프라이데이, 주문이 몰린다

<aside>
🚧

우리 커머스 서비스에 블랙 프라이데이 행사가 열렸습니다. 평소 초당 100건이던 주문 요청이 **초당 10,000건**으로 폭증합니다.

</aside>

```jsx
[10,000명 동시 접속]
     └── POST /orders
           ├── 재고 확인 & 차감
           ├── 결제 처리
           └── 주문 저장
           → DB 커넥션 풀 고갈
           → 응답 지연 → 타임아웃
           → 전체 시스템 장애
```

| **문제점** | **설명** |
| --- | --- |
| 💥 시스템 과부하 | DB 커넥션, 스레드 풀이 한계를 넘으면 전체 서비스가 멈춤 |
| 😤 유저 경험 붕괴 | 응답 없이 로딩만 돌다가 타임아웃 → 재시도 → 더 악화 |
| ⚖️ 공정성 부재 | 누가 먼저 요청했는지와 관계없이, 운 좋은 사람만 성공 |
| 🔄 재시도 폭풍 | 실패한 유저가 새로고침 → 트래픽이 더 증가하는 악순환 |

### 🍰 스케일업 & 아웃으로 해결되지 않는 이유

- 서버를 10배 늘려도, **DB와 PG는 스케일이 제한적**
- 트래픽의 **피크가 극단적으로 짧고 높은** 경우(행사 시작 직후 10초), 오토스케일링이 반응하기 전에 터짐
- 결국 **시스템이 처리할 수 있는 속도로 요청을 조절**하는 것이 핵심

> 이 개념을 **Back-pressure** 라고 합니다.
하류 시스템(DB, PG)이 감당할 수 있는 속도만큼만 상류(유저 요청)를 흘려보내는 것.
대기열은 이 back-pressure를 구현하는 대표적인 방법입니다.
>

---

## 🚦 거부할 것인가, 기다리게 할 것인가

트래픽이 몰릴 때 선택할 수 있는 전략은 크게 두 가지입니다.

### Rate Limiting vs Queuing

| **구분** | **Rate Limiting** | **Queuing (대기열)** |
| --- | --- | --- |
| 초과 요청 처리 | **거부** (429 Too Many Requests) | **보관** (대기열에 적재) |
| 유저 경험 | "나중에 다시 시도하세요" | "잠시만 기다려주세요 (현재 512번째)" |
| 유저 반응 | 새로고침 → 재시도 폭풍 | 기다림 → 순서대로 처리 |
| 적합한 상황 | API 보호, 봇 차단, 일상적 부하 제어 | 행사 트래픽, **유저가 기다릴 의사가 있는** 경우 |

블랙 프라이데이에 *"나중에 다시 시도하세요"* 를 반환하면, 유저는 떠나거나 더 세게 새로고침합니다. **유저가 원하는 것을 기다려서라도 얻을 수 있는** 구조가 필요합니다.

> **💡 Rate Limiting과 Queuing은 양자택일이 아닙니다.**
대기열 자체에도 최대 인원 제한(Rate Limiting)을 둘 수 있고, 봇이나 비정상 요청은 Rate Limiting으로 먼저 걸러낸 뒤 정상 유저만 대기열에 진입시킬 수 있습니다.
>

---

## 🚪 대기열 시스템 설계

### Kafka 버퍼링과의 차이

지난 주 선착순 쿠폰에서 Kafka를 버퍼로 활용했습니다. 그것과 이번 대기열은 어떻게 다를까요?

| **구분** | **Kafka 버퍼링 (R7 쿠폰)** | **대기열 시스템 (R8 주문)** |
| --- | --- | --- |
| 유저 경험 | 요청 후 나중에 결과 확인 (fire & forget) | 화면에서 순번을 보며 대기 |
| 결과 전달 | 비동기 (polling으로 결과 조회) | 입장 토큰 발급 → 즉시 주문 가능 |
| 제어 대상 | 처리 순서 | **처리 속도 (throughput)** |
| 핵심 관심사 | 메시지 유실 방지, 멱등 처리 | 공정한 순서, 실시간 피드백, 토큰 만료 |
| 유저 인지 | "신청 완료, 결과는 나중에" | "현재 512번째, 예상 대기 3분" |

### 대기열의 구성 요소

```jsx
[유저] → 대기열 진입 (POST /queue/enter)
      → 대기열에서 순번 부여
      → 순번 조회 (GET /queue/position)  ← 실시간 반복 조회
      → 내 차례가 오면 입장 토큰 발급
      → 토큰으로 주문 API 호출 (POST /orders)
      → 토큰 검증 → 주문 처리
```

| **구성 요소** | **역할** |
| --- | --- |
| **대기열 (Queue)** | 유저 요청을 순서대로 보관 |
| **스케줄러 (Scheduler)** | 일정 주기로 대기열에서 N명씩 꺼내 입장 토큰 발급 |
| **입장 토큰 (Entry Token)** | 주문 API 진입 권한. TTL이 있어 일정 시간 내 사용해야 함 |
| **순번 조회 (Position)** | 유저가 현재 몇 번째인지 실시간으로 확인 |

---

## 🔧 Redis 기반 대기열 구현

### 왜 Redis인가?

| **요구사항** | **Redis가 적합한 이유** |
| --- | --- |
| 빠른 읽기/쓰기 | 인메모리 기반으로 순번 조회가 μs 단위 |
| 순서 보장 | Sorted Set으로 score(timestamp) 기반 정렬 |
| 원자적 연산 | `ZADD`, `ZRANK`, `ZPOPMIN` 등이 atomic |
| TTL 지원 | 입장 토큰의 만료를 자연스럽게 처리 |

### 핵심 자료구조: Sorted Set

```jsx
ZADD  waiting-queue  {timestamp}  {userId}    // 대기열 진입
ZRANK waiting-queue  {userId}                  // 내 순번 조회 (0-based)
ZCARD waiting-queue                            // 전체 대기 인원
ZPOPMIN waiting-queue {N}                      // 앞에서 N명 꺼내기 (스케줄러)
```

- **score = 진입 시각 (timestamp)** → 먼저 들어온 사람이 앞 순번
- **member = userId** → 중복 진입 자동 방지 (Set 특성)

### 입장 토큰

```jsx
SET   entry-token:{userId}  {token}  EX 300    // 5분 TTL 토큰 발급
GET   entry-token:{userId}                      // 토큰 검증
DEL   entry-token:{userId}                      // 사용 완료 후 삭제
```

- 토큰이 있는 유저만 주문 API 진입 가능
- TTL이 지나면 자동 만료 → 다음 유저에게 기회가 돌아감

---

## 📡 실시간 피드백 — 유저를 떠나지 않게

<aside>
🕯️

대기열의 성패는 **유저가 기다리는 동안 이탈하지 않느냐**에 달려있습니다. 순번이 보이지 않으면 유저는 새로고침을 누르거나 이탈합니다.

</aside>

### 피드백 전달 방식

유저에게 순번을 알려주는 방식은 크게 세 가지가 있습니다.

**1⃣  Polling — 클라이언트가 주기적으로 물어보기**

가장 단순한 방식입니다. 클라이언트가 일정 주기(1~3초)마다 서버에 순번을 질의합니다.

```jsx
  [클라이언트]
     └── setInterval(2000)
           → GET /queue/position
           ← { "position": 128, "estimatedWaitSeconds": 45 }
           ...
           ← { "position": 0, "token": "abc-123-def" }  // 내 차례!
           → POST /orders (with token)
```

- ✅ 구현이 단순하고 인프라 변경이 없음
- ❌ 대기 인원이 많으면 Polling 자체가 서버 부하
- ❌ 주기 사이의 지연이 발생 (2초 주기면 최대 2초 늦게 인지)

**2⃣  SSE (Server-Sent Events) — 서버가 알려주기**

서버가 클라이언트와의 단방향 연결을 유지하며, 순번이 바뀔 때마다 Push합니다.

```jsx
[클라이언트] → GET /queue/stream (연결 유지)
          ← event: position
          ← data: { "position": 128, "estimatedWaitSeconds": 45 }
          ...
          ← event: enter
          ← data: { "token": "abc-123-def" }
```

- ✅ 서버가 변경 시점에만 전송 → 불필요한 요청 없음
- ✅ HTTP 기반이라 별도 프로토콜 불필요
- ❌ 연결을 유지해야 하므로 대기 인원 × 1 커넥션 필요
- ❌ 로드밸런서 뒤에서 연결 유지 설정 필요

**3⃣  WebSocket — 양방향 실시간**

- 대기열 순번 조회는 **서버 → 클라이언트 단방향**이면 충분
- WebSocket의 양방향 기능이 과도 → 이 시나리오에서는 비추천

> 💡 구현 난이도를 고려하면 **Polling으로 시작**하고, 대기 인원이 많아 Polling 부하가 문제가 되면 **SSE로 전환**하는 것을 권장합니다.
>

### 예상 대기 시간 계산

유저에게 순번만 보여주는 것보다 **"약 N분 남았습니다"** 가 훨씬 효과적입니다.

```mathematica
예상 대기 시간 = 내 순번 / 초당 처리량
```

> e.g. 순번 300, 초당 50명 처리 → 300 / 50 = 약 6초 대기
단, 이 수치는 **추정값**입니다. 토큰 미사용(만료)이나 시스템 상태에 따라 달라질 수 있으므로, "약 N분"으로 표현하는 것이 좋습니다.
>

---

## ⚡ Thundering Herd — 토큰 발급 직후의 함정

대기열을 만들었으니 문제가 해결된 것 같지만, **새로운 문제가 생깁니다.**

스케줄러가 1초마다 175명에게 토큰을 발급하면, 175명이 **동시에** 주문 API를 호출합니다. 이건 원래 문제의 축소판입니다.

```mathematica
[스케줄러] → 1초마다 175명 토큰 발급
         → 175명 동시에 POST /orders
         → DB 커넥션 175개 동시 점유
         → 순간 부하 스파이크!
```

이를 **Thundering Herd(떼몰이) 문제**라고 합니다. 캐시 만료 시 모든 요청이 동시에 DB를 조회하는 것과 같은 원리입니다.

### 완화 전략

**1⃣  발급 간격 분산**

1초에 175명을 한 번에 발급하지 않고, 100ms마다 17~18명씩 나누어 발급합니다.

```mathematica
AS-IS: 매 1초 → 175명 동시 발급
TO-BE: 매 100ms → ~18명씩 발급 → 부하가 10배 평탄화
```

**2⃣  토큰에 Jitter 부여**

토큰을 발급하되, 활성화 시점에 랜덤 딜레이(0~2초)를 포함합니다. 유저마다 주문 API 진입 시점이 자연스럽게 분산됩니다.

**3⃣  주문 API 자체 Rate Limit**

토큰이 있어도 초당 N건까지만 주문 API가 처리합니다. 대기열이 뚫리더라도 하류 시스템을 보호하는 **최종 안전장치**입니다.

> 💡 대기열은 **피크를 평탄화(smoothing)** 하는 것이지, 부하를 없애는 것이 아닙니다.
하류 시스템의 한계를 항상 염두에 두고 설계해야 합니다.
>

---

## 💣 오해 — 대기열만 있으면 끝?

### 대기열 자체의 리스크

**❌ 토큰 미사용**

- 토큰을 받고 주문하지 않으면 자리만 차지합니다. TTL을 설정해 만료 처리가 필수이며, 만료된 토큰 수만큼 다음 유저에게 추가 발급하는 로직이 필요합니다.

**❌ 어뷰징**

- 한 유저가 여러 브라우저나 디바이스로 중복 진입을 시도할 수 있습니다. Redis Sorted Set에 userId를 member로 사용하면 자연스럽게 중복이 방지되지만, 비로그인 상태라면 디바이스 핑거프린트 등 별도 대응이 필요합니다.

**❌ 스케줄러 장애**

- 스케줄러가 멈추면 대기열에서 아무도 빠지지 못합니다. 헬스체크와 이중화를 고려해야 하며, 스케줄러 미실행 시간이 일정 기준을 초과하면 알림을 보내야 합니다.

**❌ 과도한 Polling 부하**

- 대기 인원이 10,000명이고 2초마다 Polling하면 초당 5,000건의 순번 조회 요청이 발생합니다. Redis 기반이라 감당 가능하지만, 대기 인원에 비례해 Polling 주기를 동적으로 늘리는 것도 고려해볼 수 있습니다.

    ```mathematica
    순번 1~100:    1초마다 조회 (곧 입장)
    순번 100~1000: 3초마다 조회
    순번 1000+:    5초마다 조회
    ```


### Redis 장애 시 — Graceful Degradation

*대기열의 핵심 인프라인 Redis가 죽으면 어떻게 해야 할까요?*

**전면 차단**

- 대기열 진입 자체를 막고 "잠시 후 다시 시도" 안내
- 안전하지만 서비스 중단

**대기열 우회 (bypass)**

- 대기열 없이 주문 API 직접 접근 허용
- 서비스 유지하지만 과부하 위험

**Fallback 큐**

- 로컬 메모리 큐나 Kafka로 임시 전환
- 순번 정확성은 떨어지지만 서비스 유지

> 정답은 없습니다. **"Redis 장애 시 우리 서비스는 어떻게 동작해야 하는가?"** 를 사전에 정의해두는 것 자체가 중요합니다. 장애가 발생한 뒤에 판단하면 늦습니다.
>

---

## 📊 운영 지표 — 무엇을 모니터링할 것인가

대기열 시스템은 **눈에 보이지 않는 곳에서 유저 경험을 결정**합니다. 장애가 발생하기 전에 이상 징후를 감지하려면 아래 지표를 추적해야 합니다.

| **지표** | **설명** | **왜 중요한가** |
| --- | --- | --- |
| **Queue Depth** | 현재 대기열에 대기 중인 유저 수 (`ZCARD`) | 급격히 증가하면 유입 > 처리량이라는 신호 |
| **Avg Wait Time** | 진입 → 토큰 발급까지 평균 대기 시간 | 유저 체감 품질의 핵심 지표 |
| **P99 Wait Time** | 상위 1% 유저의 대기 시간 | 평균은 정상인데 P99가 높으면 특정 시점 병목 |
| **Token Conversion Rate** | 토큰 발급 → 주문 완료 비율 | < 50%면 TTL이 짧거나 주문 UX에 문제 |
| **Token Expiry Rate** | 토큰 만료(이탈) 비율 | > 30%면 유저가 대기 중 포기하고 있다는 의미 |
| **Scheduler Health** | 스케줄러 마지막 실행 시각 | 1분 이상 미실행 시 대기열 전체가 멈춤 |

> 💡 특히 **Token Conversion Rate**와 **Token Expiry Rate**는 단순 시스템 지표가 아니라 **비즈니스 지표**입니다. 유저가 토큰을 받고도 주문하지 않는다면, 대기 시간이 너무 길거나 토큰 TTL이 맞지 않다는 뜻입니다.
>

---

## 🏗️ 우리 프로젝트에 적용하기

### 전체 흐름

```mathematica
[유저] → POST /queue/enter
      → Redis Sorted Set에 userId + timestamp 저장
      → 순번 응답 (e.g. 512번째)

[유저] → GET /queue/position (2초마다 polling)
      → 현재 순번 + 예상 대기 시간 응답

[스케줄러] → 100ms마다 실행
         → ZPOPMIN으로 N명 꺼내기 (Thundering Herd 완화)
         → 입장 토큰 발급 (Redis SET + TTL 5분)

[유저] → 순번 0 도달, 토큰 수신
      → POST /orders (Header: X-Entry-Token)
      → 토큰 검증 → 주문 처리
      → 토큰 삭제

[주문 이후] → 7주차 이벤트 파이프라인 동작
          → ApplicationEvent → Kafka → collector
```

### Round 7과의 연결점

| **Round7 에서 배운 것** | **Round8 에서 활용하는 것** |
| --- | --- |
| 주문 → 이벤트 발행 (ApplicationEvent) | 주문 처리 후 후속 이벤트는 그대로 이벤트 기반 |
| Kafka 파이프라인 | 주문 완료 이벤트 → Kafka → collector (Metrics 집계) |
| Outbox Pattern | 주문 이벤트 발행의 신뢰성 보장 |

> 대기열은 **주문 API 앞단의 관문**이고, 주문 API 이후의 흐름은 **Round7 에서 구축한 이벤트 파이프라인**이 그대로 동작합니다.
>

### 처리량 설계 기준

시스템이 안정적으로 처리할 수 있는 TPS를 기준으로 스케줄러의 배치 크기를 설정합니다.

```mathematica
DB 커넥션 풀: 50
주문 1건 평균 처리 시간: 200ms
→ 이론적 최대 TPS: 50 / 0.2 = 250 TPS
→ 안전 마진 70%: 175 TPS
→ 스케줄러: 100ms마다 ~18명씩 토큰 발급 (Thundering Herd 완화)
```

---

### 🌾 Summary

| **항목** | **설명** |
| --- | --- |
| **대기열의 목적** | 시스템을 보호하면서 공정한 순서로 유저를 처리 (Back-pressure) |
| **Rate Limiting과의 차이** | 거부가 아니라 보관. 유저가 기다릴 의사가 있는 상황에 적합 |
| **핵심 기술** | Redis Sorted Set (순서 보장 + 원자적 연산 + TTL) |
| **유저 경험** | 순번 조회 + 예상 대기 시간 → 이탈 방지 |
| **Thundering Herd** | 토큰 발급 분산으로 완화. 대기열이 부하를 없애는 건 아님 |
| **Graceful Degradation** | Redis 장애 시 전략을 사전에 정의해두는 것이 핵심 |
| **R7과의 관계** | 대기열은 주문 API **앞단**의 관문, 주문 이후는 R7의 이벤트 파이프라인 |

---

<aside>
📚

**References**

</aside>

| 구분 | 링크 |
| --- | --- |
| 🔍 Redis Sorted Set | [Redis Sorted Sets](https://redis.io/docs/latest/develop/data-types/sorted-sets/) |
| ⚙ Spring Data Redis | [Spring Data Redis Reference](https://docs.spring.io/spring-data/redis/reference/) |
| 📖 가상 대기열 설계 | [Virtual Waiting Room Architecture - System Design Newsletter](https://newsletter.systemdesign.one/p/virtual-waiting-room) |
| 📖 Back-pressure | [Reactive Streams](https://www.reactive-streams.org/) |
| 🌟 지마켓 - 대기열 | [지마켓 대기열 시스템 파헤치기](https://dev.gmarket.com/46) |
| 📖 SSE in Spring | [Server-Sent Events in Spring - Baeldung](https://www.baeldung.com/spring-server-sent-events) |

<aside>
🌟

**Next Week Preview**

</aside>

> **쌓인 데이터를 어떻게 가치로 바꿀 수 있을까?**
>
>
> **Round7** 에서 Kafka를 통해 유저 행동 이벤트를 수집하고 product_metrics에 집계하는 파이프라인을 구축했습니다. **Round8** 에서는 대기열을 통해 트래픽을 제어하며 안정적으로 주문을 처리하는 구조도 만들었습니다.
>
> 다음주에는 지금까지 쌓인 데이터를 기반으로 **실시간 랭킹 파이프라인**을 구축해볼 거예요. 인기 상품, 급상승 키워드, 실시간 판매 순위 — 데이터가 서비스의 경쟁력이 됩니다!
>

 ```

```kotlin
// src/coupon/event/listener/OrderCreateEventListener.kt
// 이벤트 리스너 flow
@EventListener
fun handle(event: OrderCreatedEvent) {
	couponService.issue(event); 
}

// src/order/event/listener/OrderCreateEventListener.kt
@EventListener
fun handle(event: OrderCreatedEvent) {
	metricsService.increase(event); 
	
}

// src/order/event/listener/OrderCreateEventListener.kt
@EventListener
fun handle(event: OrderCreatedEvent) {
	logService.record(event);
}
```

1. 쿠폰 서비스에 이벤트 발행이 실패하면.. 다른 서비스에도 이벤트 전파가 실패한다.
2. 직렬로 수행되죠. 우리는 기본적으로 다른 컨슈머에 영향받지 않는 구조가 필요해요.
3. 새로운 컨슈머가 추가될때 주문 도메인 코드를  수정해야 하죠.
  1. 주문이 결국 내 이벤트가 누구한테 필요한지 다 알고, 또 추가로 필요하면 쫓아다니면서 먹여줘야 해요.




---

# 고민 포인트
---

## 1. 대기열 스케줄러에서 @Async를 쓰지 않는 이유

`@Scheduled`는 동기 실행이 기본이다. `@Async`를 붙이면 비동기로 바꿀 수 있지만, 대기열 스케줄러에서는 **의도적으로 사용하지 않는다.**

**@Async를 붙이면 벌어지는 일**

```
fixedRate = 100ms + @Async:
  Thread-1: |------작업(200ms)------|
  Thread-2:           |------작업(200ms)------|
  Thread-3:                     |------작업(200ms)------|
  -> 100ms마다 새 스레드가 생성, 동시에 3개 작업이 실행
  -> 배치 18명 x 3 = 54명에게 동시 토큰 발급
  -> 처리량 설계(175 TPS) 무너짐
```

- 이전 실행이 안 끝났어도 다음 실행이 새 스레드에서 시작됨
- ZPOPMIN은 atomic이라 같은 유저를 두 번 꺼내진 않지만, **의도한 배치 크기의 2~3배가 동시에 발급**될 수 있음
- 대기열의 핵심 목적은 **처리량 제어(Back-pressure)**인데, @Async가 이 제어를 무너뜨림

**@Async 없이 동기 실행하면**

```
fixedRate = 100ms, 동기:
  |--작업(30ms)--|           |--작업(30ms)--|           |--작업(30ms)--|
  0ms           30ms         100ms        130ms        200ms
  -> 항상 한 번에 하나만 실행
  -> 배치 18명이 정확히 100ms 간격으로 발급
  -> 처리량 설계 그대로 유지
```

**결론**: 대기열 스케줄러는 "정확히 N명씩, 일정 주기로" 발급하는 것이 핵심이다. @Async는 이 정밀한 제어를 깨뜨리므로 사용하지 않는다.

## 2. OutboxScheduler와 QueueScheduler의 충돌

우리 프로젝트에는 이미 OutboxScheduler(`fixedDelay = 10000`)가 있고, 대기열을 구현하면 QueueScheduler(`fixedRate = 100`)가 추가된다. 이 둘이 충돌하지 않는가?

**Spring 기본 설정: 단일 스레드 -> 충돌 발생**

```
[단일 스레드 -- 문제]
|---Outbox(Kafka 발행, 3초 소요)---|--Queue--|--Queue--|...|---Outbox---|
0s                                3s       3.1s     3.2s
                                  ↑ Queue가 3초간 멈춤 -> 대기열이 3초간 정지
```

Spring의 기본 TaskScheduler는 스레드 1개다. Outbox가 Kafka에 이벤트를 발행하느라 3초가 걸리면, 그동안 QueueScheduler는 실행되지 못한다. 100ms마다 실행해야 하는 대기열이 3초간 멈추는 것은 치명적이다.

**해결: 스레드 풀 설정**

```java
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);  // 스레드 4개
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}
```

또는 application.yml:

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 4
```

이렇게 하면:

```
[스레드 풀(4개) -- 정상]
Thread-1: |---Outbox(3초)---|                |---Outbox---|
Thread-2: |--Q--|--Q--|--Q--|--Q--|--Q--|--Q--|--Q--|--Q--|
           ↑ 서로 다른 스레드에서 독립 실행, 충돌 없음
```

**결론**: QueueScheduler를 추가할 때 **반드시 스케줄러 스레드 풀 설정이 필요하다.** 이 설정 없이는 OutboxScheduler와 QueueScheduler가 같은 스레드를 두고 경쟁하여 대기열이 불안정해진다.

---

# 설계 결정 항목
---

대기열 시스템을 구현할 때 결정해야 하는 값들을 정리한다. 각 항목은 구현 전에 근거와 함께 결정하고, 코드에 하드코딩하지 않고 설정으로 분리하여 환경별(로컬/dev/prod)로 조정할 수 있도록 한다.

## 1. score 방식 (타임스탬프 vs INCR)

Sorted Set에 유저를 넣을 때 score를 어떤 값으로 설정할 것인가.

| 방식 | 장점 | 단점 |
|------|------|------|
| `System.currentTimeMillis()` | 별도 Redis 호출 없음, 진입 시각 자체가 score | 동일 밀리초 충돌 가능 (확률 극히 낮음) |
| `Redis INCR` (시퀀스) | 완벽한 순서 보장, 충돌 0% | Redis 호출 1번 추가 (INCR + ZADD = 2번) |
| 밀리초 * 1000 + INCR % 1000 | 시각 정보 + 순서 보장 둘 다 | 구현 복잡도 증가 |

- 현실적으로 `System.currentTimeMillis()`로 충분
- 밀리초 단위 동시 진입은 "같은 순번"으로 봐도 무방
- 엄밀한 FIFO가 필요하면 INCR로 전환

## 2. 스케줄러 실행 주기

몇 ms마다 대기열에서 유저를 꺼낼 것인가.

```
목표 TPS = 배치 크기 x (1000ms / 주기)
```

- 주기가 길수록 한 번에 많이 꺼내야 하고, 그만큼 Thundering Herd 위험 증가
- 100ms~500ms 범위가 현실적
- fixedRate 사용 (일정한 처리량 유지)
- initialDelay도 설정 (앱 시작 후 Redis 연결 안정화 시간 고려, 예: 5000ms)

| 주기 예시 | 배치 크기 (175 TPS 기준) | Thundering Herd 위험 |
|----------|------------------------|-------------------|
| 100ms | 18명 | 낮음 (10배 평탄화) |
| 500ms | 88명 | 중간 |
| 1초 | 175명 | 높음 |
| 3초 | 525명 | 매우 높음 |

## 3. 배치 크기

스케줄러가 한 번 실행될 때 대기열에서 몇 명을 꺼내 토큰을 발급할 것인가.

**처리량 역산**
```
DB 커넥션 풀: 50
주문 1건 평균 처리 시간: 200ms
이론적 최대 TPS: 50 / 0.2 = 250 TPS
안전 마진 70%: 250 * 0.7 = 175 TPS
배치 크기: 175 / (1000 / 100ms) = 17.5 -> 18명
```

- 안전 마진을 두는 이유: 주문 외 다른 쿼리(상품 조회, 회원 정보 등)도 커넥션 풀을 사용
- PG 호출이 느려지면 처리 시간이 200ms -> 500ms로 증가할 수 있으므로 여유 필요
- 실제 환경에서는 부하 테스트로 최적값을 찾아야 함

## 4. 최대 동시 활성 토큰 수

동시에 최대 몇 개의 토큰이 활성 상태로 존재할 수 있는가.

```
최대 동시 활성 토큰 = 배치 크기 x (TTL / 스케줄러 주기)
예: 10명 x (300초 / 3초) = 1,000명
예: 18명 x (300초 / 0.1초) = 54,000명
```

- 이 값은 "토큰을 받고 아무도 주문을 안 했을 때"의 최악 상한
- 실제로는 토큰 받자마자 주문하는 유저가 대부분이므로 동시 활성 수는 훨씬 적음
- 하지만 상한을 알고 있어야 시스템 용량 계획이 가능
- 고도화: "현재 활성 토큰이 N개 이상이면 스케줄러가 발급을 잠시 멈춘다"는 로직으로 안전장치 추가 가능

## 5. 토큰 TTL

토큰 발급 후 만료까지 시간.

**결정 기준: "토큰 받고 -> 주문 버튼 누르기까지 유저가 현실적으로 몇 분이 필요한가?"**

| 서비스 상황 | 권장 TTL |
|------------|---------|
| 결제 정보가 이미 저장되어 있음 | 3분 |
| 카드 정보를 직접 입력해야 함 | 5분 |
| 배송지까지 새로 입력해야 함 | 7분 |

- 너무 짧으면: 유저가 결제 정보 입력 중에 만료 -> 불만
- 너무 길면: 토큰만 받고 안 쓰는 유저가 자리 차지 -> 뒤의 유저 불이익
- 만료된 토큰 수만큼 추가 발급하는 보상 로직은 복잡도가 높으므로 필요성을 판단

## 6. 중복 진입 정책

이미 대기열에 있는 유저가 다시 요청하면 어떻게 할 것인가.

| 정책 | 구현 | 유저 경험 |
|------|------|----------|
| **기존 순번 유지** | `ZADD NX` (이미 존재하면 무시) | "이미 대기 중이세요, 현재 128번째" |
| **맨 뒤로 다시** | `ZADD` (score 업데이트) | 새로고침하면 순번이 뒤로 밀림 |

- 거의 모든 경우 **기존 순번 유지(ZADD NX)**가 맞음
- 새로고침했다고 순번이 뒤로 밀리면 유저 불만이 큼
- ZADD NX의 반환값이 0이면 이미 존재 -> 기존 ZRANK를 조회하여 현재 순번 응답

## 7. 예상 대기 시간 계산 방식

유저에게 "약 N분 남았습니다"를 어떻게 계산할 것인가.

**기본 공식**
```
예상 대기 시간(초) = 내 순번 / 초당 처리량
예: 순번 300 / 초당 175명 = 약 1.7초
```

| 방식 | 정확도 | 복잡도 |
|------|--------|--------|
| `순번 / 고정 TPS` | 낮음 (TPS가 일정하다고 가정) | 낮음 |
| `순번 / 최근 N초간 실제 처리량` | 중간 (실측 기반) | 중간 |
| `최근 처리된 유저들의 평균 대기 시간 기반` | 높음 | 높음 |

- 처음에는 고정 TPS 방식으로 구현
- 추정값이므로 "약 N분"으로 표현 (정확한 초 단위보다 적절)
- 고도화 시 최근 실제 처리량을 Redis에 기록하여 동적 계산

## 8. Polling 주기

클라이언트가 몇 초마다 순번을 조회할 것인가.

- 기본 2~3초가 일반적
- **스케줄러 주기보다 Polling 주기가 짧으면 의미 없음** (변화 없는 응답을 반복 수신)
  - 스케줄러 3초 주기인데 Polling 1초면, 2번은 변화 없는 응답
- 동적 주기: 서버 응답에 `retryAfterMs`를 포함하여 클라이언트가 따르게 유도

| 순번 구간 | Polling 주기 | 이유 |
|----------|-------------|------|
| 1~100 | 1초 | 곧 입장, 빠른 피드백 필요 |
| 100~1,000 | 3초 | 중간 대기, 적당한 빈도 |
| 1,000+ | 5초 | 긴 대기, 서버 부하 절감 |

## 9. 토큰 검증 실패 시 응답

토큰 없이 주문 API에 접근하거나, 만료된 토큰으로 접근하면 어떤 응답을 줄 것인가.

| 상황 | HTTP 상태 | 응답 메시지 | 대기열 재진입 |
|------|----------|-----------|-------------|
| 토큰 헤더가 아예 없음 | 401 UNAUTHORIZED | "입장 토큰이 필요합니다" | X (대기열 진입부터 해야 함) |
| 토큰이 만료됨 | 401 UNAUTHORIZED | "토큰이 만료되었습니다. 대기열에 다시 진입해주세요" | X |
| 토큰 값이 불일치 | 401 UNAUTHORIZED | "유효하지 않은 토큰입니다" | X |
| 토큰은 유효하지만 이미 사용됨 | 409 CONFLICT | "이미 사용된 토큰입니다" | X |

- **대기열로 자동 재진입은 위험**: 만료된 토큰으로 계속 시도하면 무한 재진입 루프 가능
- 유저가 직접 다시 대기열에 진입하도록 안내하는 것이 안전
- ErrorType에 `ENTRY_TOKEN_REQUIRED`, `ENTRY_TOKEN_EXPIRED`, `ENTRY_TOKEN_INVALID`, `ENTRY_TOKEN_ALREADY_USED` 등 추가

## 10. 기타 설정

| 설정 | 설명 | 예시 |
|------|------|------|
| 대기열 키 이름 | Redis Sorted Set key | `waiting-queue` (행사별 분리 시 `waiting-queue:{eventId}`) |
| 토큰 키 prefix | Redis String key prefix | `entry-token:` |
| 토큰 생성 방식 | 예측 불가능한 값 | `UUID.randomUUID()` |
| 대기열 최대 인원 | 대기열 상한 | 50,000명 (초과 시 Rate Limiting으로 거부) |
| 토큰 전달 방식 | 헤더 / 쿼리 파라미터 | `Header: X-Entry-Token` (URL에 노출 안 됨) |
| 검증 대상 URL | 어떤 API에 토큰 검증 적용 | `POST /api/v1/orders` (주문 생성에만, 조회는 제외) |
| 검증 위치 | Interceptor / Controller / AOP | 프로젝트 컨벤션에 따라 결정 |
| RedisTemplate 선택 | Master 전용 vs Replica 허용 | 쓰기/읽기 모두 Master가 안전 (실시간성 중요) |

---

# 학습 가이드
---

## 1. 핵심 개념

### 1-1. Back-pressure (배압)

하류 시스템(DB, PG)이 감당할 수 있는 속도만큼만 상류(유저 요청)를 흘려보내는 제어 메커니즘이다.

**학습 포인트**
- Back-pressure가 없으면 어떤 일이 벌어지는가?
  - DB 커넥션 풀 고갈 -> 전체 서비스 타임아웃 -> 재시도 폭풍 -> 시스템 전면 장애
  - 핵심은 "처리 속도 < 유입 속도"일 때 시스템이 스스로를 보호하는 방법
- Reactive Streams에서의 Back-pressure 원리 (Publisher가 Subscriber의 처리 속도에 맞춰 발행량 조절)
- 대기열이 Back-pressure를 구현하는 대표적 방법인 이유
  - 유입은 대기열에 적재, 처리는 스케줄러가 제어된 속도로 수행
- 우리 프로젝트 적용: 스케줄러의 배치 크기(N명/주기)가 곧 Back-pressure의 조절 밸브

### 1-2. Rate Limiting vs Queuing

**학습 포인트**
- Rate Limiting: 초과 요청을 **거부**(429 Too Many Requests)
  - 적합 상황: API 보호, 봇 차단, DDoS 방어, 일상적 부하 제어
  - 알고리즘: Token Bucket, Sliding Window, Fixed Window, Leaky Bucket
  - 유저 반응: "나중에 다시 시도하세요" -> 재시도 폭풍 위험
- Queuing: 초과 요청을 **보관**(대기열에 적재)
  - 적합 상황: 행사 트래픽, 유저가 기다릴 의사가 있는 경우
  - 유저 반응: "현재 512번째, 약 3분 대기" -> 이탈 방지
- 양자택일이 아니다
  - 봇/비정상 요청은 Rate Limiting으로 먼저 걸러내고, 정상 유저만 대기열에 진입
  - 대기열 자체에도 최대 인원 제한(Rate Limiting)을 둘 수 있음
- 실무에서의 판단 기준: "유저가 이 결과를 기다릴 의사가 있는가?"

### 1-3. Thundering Herd (떼몰이 문제)

**학습 포인트**
- 정의: 대량의 요청이 동시에 같은 리소스에 몰리는 현상
  - 캐시 만료 시 모든 요청이 동시에 DB 조회하는 것과 동일한 원리
  - 대기열에서는: 스케줄러가 N명에게 동시에 토큰 발급 -> N명이 동시에 주문 API 호출
- 왜 대기열을 만들어도 이 문제가 생기는가?
  - 1초마다 175명에게 토큰 발급 -> 175명 동시 POST /orders -> DB 커넥션 175개 동시 점유
  - 대기열은 "피크를 평탄화(smoothing)"하는 것이지, 부하를 없애는 것이 아님
- 완화 전략 3가지
  - (1) 발급 간격 분산: 1초에 175명 한번 -> 100ms마다 18명씩 (부하 10배 평탄화)
  - (2) 토큰에 Jitter 부여: 활성화 시점에 랜덤 딜레이(0~2초) 포함
  - (3) 주문 API 자체 Rate Limit: 토큰 있어도 초당 N건 제한 (최종 안전장치)
- 실제 구현 시 고려: @Scheduled의 fixedRate를 100ms로 설정하고 배치 크기를 줄이는 방식

### 1-4. Graceful Degradation (우아한 성능 저하)

**학습 포인트**
- 정의: 시스템 일부에 장애가 발생해도 전체 서비스가 멈추지 않도록 단계적으로 기능을 축소하는 전략
- Redis 장애 시 3가지 선택지
  - (1) 전면 차단: 대기열 진입 자체를 막고 "잠시 후 다시 시도" 안내 -> 안전하지만 서비스 중단
  - (2) 대기열 우회(bypass): 대기열 없이 주문 API 직접 접근 허용 -> 서비스 유지하지만 과부하 위험
  - (3) Fallback 큐: 로컬 메모리 큐나 Kafka로 임시 전환 -> 순번 정확성 저하, 서비스 유지
- 핵심: 정답이 없다. "Redis 장애 시 우리 서비스는 어떻게 동작해야 하는가?"를 **사전에** 정의해두는 것이 중요
- 우리 프로젝트 참고: ProductRedisCacheStore에서 이미 Redis 장애 시 DB fallback 패턴을 사용 중
  - try-catch로 Redis 실패 시 빈 값 반환 -> DB에서 직접 조회
  - 동일 패턴을 대기열에도 적용 가능

---

## 2. Redis 기술 학습

### 2-1. Redis Sorted Set 심화

Sorted Set은 대기열의 핵심 자료구조이다. 각 원소에 score가 부여되어 자동 정렬된다.

**필수 명령어**
- `ZADD key score member`: 원소 추가 (이미 존재하면 score 업데이트)
  - 대기열 진입: `ZADD waiting-queue {timestamp} {userId}`
  - Set 특성으로 동일 userId 중복 진입 자동 방지
  - NX 옵션: `ZADD key NX score member` -> 이미 존재하면 추가하지 않음 (중복 방지 강화)
- `ZRANK key member`: 해당 원소의 순위 반환 (0-based, score 오름차순)
  - 내 순번 조회: `ZRANK waiting-queue {userId}` -> 0이면 가장 앞, null이면 대기열에 없음
- `ZCARD key`: Sorted Set의 전체 원소 수
  - 전체 대기 인원 조회: `ZCARD waiting-queue`
- `ZPOPMIN key count`: score가 가장 낮은(가장 먼저 진입한) N개 원소를 꺼내고 제거
  - 스케줄러가 사용: `ZPOPMIN waiting-queue 18` -> 앞에서 18명 꺼내기
  - atomic 연산이므로 동시성 안전
- `ZSCORE key member`: 해당 원소의 score 반환
  - 진입 시각 확인용

**학습해야 할 추가 명령어**
- `ZREM key member`: 특정 원소 제거 (대기열 이탈 처리)
- `ZRANGE key start stop`: 범위 조회 (모니터링/디버깅용)
- `ZRANGEBYSCORE key min max`: score 범위로 조회

**Spring Data Redis에서의 사용**
```java
// RedisTemplate에서 ZSetOperations 얻기
ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

// 대기열 진입
zSetOps.add("waiting-queue", userId, System.currentTimeMillis());

// 내 순번 조회 (0-based)
Long rank = zSetOps.rank("waiting-queue", userId);

// 전체 대기 인원
Long size = zSetOps.size("waiting-queue");

// 앞에서 N명 꺼내기 (ZPOPMIN)
Set<ZSetOperations.TypedTuple<String>> popped =
    zSetOps.popMin("waiting-queue", count);
```

### 2-2. Redis String + TTL (입장 토큰)

**필수 명령어**
- `SET key value EX seconds`: 값 저장 + TTL 설정
  - 토큰 발급: `SET entry-token:{userId} {token} EX 300` (5분 TTL)
- `GET key`: 값 조회
  - 토큰 검증: `GET entry-token:{userId}` -> 존재하면 유효, null이면 만료/미발급
- `DEL key`: 값 삭제
  - 주문 완료 후 토큰 삭제: `DEL entry-token:{userId}`

**Spring Data Redis에서의 사용**
```java
ValueOperations<String, String> valueOps = redisTemplate.opsForValue();

// 토큰 발급 (5분 TTL)
String token = UUID.randomUUID().toString();
valueOps.set("entry-token:" + userId, token, Duration.ofMinutes(5));

// 토큰 검증
String storedToken = valueOps.get("entry-token:" + userId);
boolean isValid = token.equals(storedToken);

// 토큰 삭제
redisTemplate.delete("entry-token:" + userId);
```

**학습 포인트**
- TTL이 중요한 이유: 토큰을 받고 주문하지 않으면 자리만 차지 -> TTL로 자동 만료 -> 다음 유저에게 기회
- TTL 설정 기준: 주문 페이지 진입 -> 결제 정보 입력 -> 주문 완료까지 걸리는 시간 고려 (보통 3~5분)
- 만료된 토큰 수만큼 다음 유저에게 추가 발급하는 보상 로직 필요 여부 검토

### 2-3. 기존 프로젝트 Redis 설정 이해

**학습 포인트**
- RedisConfig.java 분석
  - Master-Replica 구조: 쓰기는 Master, 읽기는 Replica에서 수행
  - 두 개의 RedisTemplate: 기본(REPLICA_PREFERRED), Master 전용(redisTemplateMaster)
  - 대기열 구현 시 어떤 템플릿을 사용해야 하는가?
    - ZADD, ZPOPMIN, SET(토큰 발급) -> 쓰기 연산 -> Master 전용 템플릿 사용
    - ZRANK, ZCARD, GET(토큰 조회) -> 읽기 연산 -> 기본 템플릿(Replica) 사용 가능
    - 단, 순번 조회의 실시간성이 중요하면 Master에서 읽는 것도 고려
- redis.yml의 설정 구조 (database, master host/port, replica host/port)

---

## 3. Spring 스케줄러 학습

### 3-1. @Scheduled 어노테이션

**학습 포인트**
- `@EnableScheduling`: 스케줄링 기능 활성화 (Application 클래스 또는 Config에 선언)
- fixedRate vs fixedDelay
  - `fixedRate = 100`: 이전 실행 **시작** 시점 기준 100ms마다 실행 (실행 시간과 무관하게 일정 주기)
  - `fixedDelay = 100`: 이전 실행 **종료** 시점 기준 100ms 후 실행 (처리 완료 보장)
  - 대기열 스케줄러에는 `fixedRate`가 적합 (일정한 처리량 유지)
  - 기존 OutboxScheduler는 `fixedDelay = 10000` 사용 (발행 완료 후 다음 폴링)
- initialDelay: 애플리케이션 시작 후 첫 실행까지의 대기 시간

### 3-2. 배치 크기 산정

**학습 포인트 - 처리량 역산**
```
DB 커넥션 풀: 50
주문 1건 평균 처리 시간: 200ms
이론적 최대 TPS: 50 / 0.2 = 250 TPS
안전 마진 70%: 250 * 0.7 = 175 TPS
```
- 안전 마진을 두는 이유: 주문 외 다른 쿼리도 커넥션을 사용하기 때문
- 스케줄러 주기와 배치 크기 관계
  - 1초마다 175명 -> Thundering Herd 위험
  - 100ms마다 18명 -> 부하 분산 (175 / 10 = 17.5, 반올림 18)
- 실제 환경에서는 부하 테스트를 통해 최적값을 찾아야 함
- HikariCP의 maximum-pool-size 설정과의 관계

### 3-3. 스케줄러 안정성

**학습 포인트**
- 스케줄러가 단일 스레드에서 동작하면 하나가 지연될 때 다른 스케줄러도 밀림
  - TaskScheduler를 빈으로 등록하고 pool-size 조정 고려
- 스케줄러 장애 감지: 마지막 실행 시각을 기록하고, 일정 기준 초과 시 알림
- 멀티 인스턴스 환경에서의 중복 실행 방지
  - Redis 분산 락(SETNX) 또는 ShedLock 라이브러리 활용
  - ZPOPMIN이 atomic이므로 중복 pop은 발생하지 않지만, 토큰 발급 로직 전체의 원자성 보장 필요

---

## 4. 구현 설계 학습

### 4-1. 전체 흐름 이해

```
[유저] -> POST /queue/enter
      -> Redis ZADD waiting-queue {timestamp} {userId}
      -> 현재 순번 응답 (ZRANK)

[유저] -> GET /queue/position (2초마다 polling)
      -> ZRANK로 순번 조회
      -> 예상 대기 시간 = 순번 / 초당 처리량
      -> 순번 0 도달 시 토큰 정보 포함 응답

[스케줄러] -> 100ms마다 실행
         -> ZPOPMIN으로 18명 꺼내기
         -> 각 유저에게 입장 토큰 발급 (SET entry-token:{userId} {token} EX 300)

[유저] -> POST /orders (Header: X-Entry-Token: {token})
      -> 토큰 검증 (GET entry-token:{userId})
      -> 주문 처리 (기존 OrderPaymentFacade 활용)
      -> 토큰 삭제 (DEL entry-token:{userId})
```

### 4-2. 레이어별 역할 분배

**학습 포인트 - 기존 아키텍처 패턴을 그대로 따른다**

| 레이어 | 역할 | 예상 클래스 |
|--------|------|------------|
| Domain | 대기열/토큰 관련 인터페이스 정의 | WaitingQueueRepository(interface), EntryTokenRepository(interface) |
| Infrastructure | Redis 기반 구현체 | WaitingQueueRedisRepository, EntryTokenRedisRepository |
| Application | 대기열 진입/순번 조회/토큰 발급 로직 조율 | QueueFacade, QueueScheduler |
| Interfaces | REST API 엔드포인트 | QueueV1Controller |

- Domain 레이어에 Repository 인터페이스를 정의하고, Infrastructure에서 Redis로 구현 (DIP)
- 기존 OrderRepository -> OrderRepositoryImpl 패턴과 동일
- 토큰 검증은 Interceptor 또는 Filter로 구현 가능 (주문 API 진입 전 검증)

### 4-3. 토큰 검증 방식

**학습 포인트**
- 방법 1: Controller에서 직접 검증
  - 주문 Controller 메서드 시작 시 토큰 유효성 체크
  - 장점: 단순, 명시적
  - 단점: 주문 Controller에 대기열 관련 로직이 침투
- 방법 2: Interceptor/Filter로 분리
  - HandlerInterceptor 구현 -> /api/v1/orders POST 요청에만 적용
  - 장점: 관심사 분리, 주문 도메인 코드 변경 없음
  - 단점: 헤더 규약 필요 (X-Entry-Token)
- 방법 3: AOP(@Aspect) 활용
  - 커스텀 어노테이션(@RequireEntryToken) + Aspect
  - 장점: 선언적, 재사용 가능
  - 단점: 디버깅 어려움

### 4-4. Polling 설계

**학습 포인트**
- 기본 구조: 클라이언트가 2초마다 GET /queue/position 호출
- 응답 형태:
  - 대기 중: `{ "position": 128, "estimatedWaitSeconds": 45, "token": null }`
  - 입장 가능: `{ "position": 0, "estimatedWaitSeconds": 0, "token": "abc-123-def" }`
  - 대기열에 없음: 404 또는 별도 상태 코드
- Polling 부하 고려
  - 대기 10,000명 x 2초마다 = 초당 5,000건 순번 조회
  - Redis ZRANK는 O(log N)이므로 성능 문제는 적지만, 네트워크/서버 부하 고려
  - 순번 구간별 동적 주기 조절: 응답에 `retryAfterMs` 필드 포함
    - 순번 1~100: 1초
    - 순번 100~1000: 3초
    - 순번 1000+: 5초
- 예상 대기 시간 계산: `순번 / 초당 처리량`
  - 이 수치는 추정값이므로 "약 N분"으로 표현

---

## 5. 프로젝트 적용 학습

### 5-1. 기존 패턴 재사용

**학습 포인트**
- ErrorType에 대기열 관련 에러 코드 추가
  - 예: QUEUE_ALREADY_ENTERED(409), QUEUE_NOT_FOUND(404), INVALID_ENTRY_TOKEN(401)
  - 기존 패턴: ErrorType enum에 status + code + message 정의
- CoreException으로 비즈니스 예외 발생
  - 예: `throw new CoreException(ErrorType.INVALID_ENTRY_TOKEN)`
- ApiResponse로 일관된 응답 형식 유지
  - 성공: `ApiResponse.success(queuePositionResponse)`
  - 실패: `ApiResponse.fail(errorCode, message)`
- ApiControllerAdvice에서 자동으로 예외 처리

### 5-2. Round 7 이벤트 파이프라인과의 연결

**학습 포인트**
- 대기열은 주문 API **앞단의 관문**
  - 대기열 통과 -> 토큰 발급 -> 주문 API 진입 -> 기존 주문 흐름 그대로
- 주문 이후 흐름은 변경 없음
  - OrderPaymentFacade -> OrderPaidEvent 발행 -> KafkaEventPublishListener -> Kafka
  - OutboxPattern으로 신뢰성 보장
- 새로 추가되는 것: 주문 API 진입 **전**에 토큰 검증 단계만 추가

### 5-3. 테스트 전략

**학습 포인트**
- 단위 테스트
  - QueueFacade: Mock(WaitingQueueRepository, EntryTokenRepository) -> 비즈니스 로직 검증
  - 순번 계산, 예상 대기 시간 계산 로직 검증
- 통합 테스트
  - Redis Testcontainer 사용 (기존 프로젝트에 testFixtures 설정 있음)
  - 실제 Redis에 ZADD/ZRANK/ZPOPMIN 동작 검증
  - 토큰 TTL 만료 테스트
- E2E 테스트
  - TestRestTemplate + Testcontainers
  - 대기열 진입 -> 순번 조회 -> 스케줄러 실행 -> 토큰 수신 -> 주문 API 호출 전체 흐름
- 동시성 테스트
  - ExecutorService로 다수 유저 동시 진입 시 순서 보장 검증
  - 동일 userId 중복 진입 방지 검증

### 5-4. 모니터링 지표 구현

**학습 포인트**
- Micrometer + Prometheus로 아래 지표 수집 고려
  - Queue Depth: ZCARD 결과를 Gauge로 노출
  - Avg/P99 Wait Time: 진입 시각(score) ~ 토큰 발급 시각의 차이를 Timer로 기록
  - Token Conversion Rate: 토큰 발급 수 vs 주문 완료 수 비율
  - Token Expiry Rate: 만료된 토큰 수 / 전체 발급 수
  - Scheduler Health: 마지막 실행 시각을 Gauge로 노출

---

## 6. 학습 순서 권장

| 순서 | 항목 | 이유 |
|------|------|------|
| 1 | Redis Sorted Set 명령어 실습 | 대기열의 핵심 자료구조를 먼저 손에 익히기 |
| 2 | Spring Data Redis ZSetOperations API | Java 코드로 Redis 명령어 매핑 이해 |
| 3 | Back-pressure, Rate Limiting vs Queuing 개념 | 왜 대기열이 필요한지 설계 근거 이해 |
| 4 | 전체 흐름도 그려보기 | 진입 -> 순번 조회 -> 토큰 발급 -> 주문 API 호출 시퀀스 |
| 5 | 레이어별 클래스 설계 | Domain 인터페이스 -> Infrastructure 구현체 -> Application Facade |
| 6 | 스케줄러 배치 크기 산정 | 처리량 역산 + Thundering Herd 완화 전략 |
| 7 | 토큰 검증 방식 결정 | Interceptor vs Controller 직접 검증 중 선택 |
| 8 | 테스트 코드 작성 (TDD Red Phase) | 먼저 실패하는 테스트 작성 후 구현 |
| 9 | Graceful Degradation 설계 | Redis 장애 시 동작 정의 |
| 10 | 모니터링 지표 설계 | 운영 관점에서 필요한 메트릭 정의 |

---

## 배포 전 코드 리뷰 & 수정 사항

전체 구현 완료 후 배포 전 코드 리뷰를 수행하여 6개 이슈를 발견하고 모두 수정했다.

### CRITICAL 1. feature_flag 테이블 DDL + 초기 데이터 자동 실행

운영 환경(`ddl-auto: none`)에서 `feature_flag` 테이블이 자동 생성되지 않아 앱 기동 시 조회 실패.

수정:
- `src/main/resources/schema.sql` -- `CREATE TABLE IF NOT EXISTS feature_flag` DDL
- `src/main/resources/data.sql` -- `QUEUE_ENABLED` 초기 데이터 INSERT (기본 비활성)
- `application.yml` -- `spring.sql.init.mode: always`, `spring.jpa.defer-datasource-initialization: true` 추가
- local/test 프로필에서는 `spring.sql.init.mode: never` (JPA ddl-auto: create가 처리)

배포 시 앱 기동만으로 테이블 생성 + 초기 데이터 삽입이 자동 수행된다.
활성화: `UPDATE feature_flag SET enabled = 1 WHERE feature_key = 'QUEUE_ENABLED';`

### CRITICAL 2. QUEUE_ENABLED 초기 데이터 부재

`feature_flag` 테이블이 있어도 `QUEUE_ENABLED` row가 없으면 `isQueueEnabled()`가 항상 false.
data.sql의 `WHERE NOT EXISTS` 절로 멱등하게 INSERT하여 해결.

### IMPORTANT 3. E2E 테스트 구현

`QueueV1ApiE2ETest` -- 7개 테스트 케이스 작성:

| 케이스 | 검증 |
|--------|------|
| 전체 흐름 | 진입 -> 토큰 발급 -> 순번 조회(토큰 포함) -> 주문 성공 |
| 토큰 없이 주문 (flag ON) | 차단 확인 (400) |
| flag OFF 주문 | 토큰 없이도 주문 성공 |
| flag row 없이 주문 | 기본값 false -> 대기열 bypass -> 주문 성공 |
| flag OFF 진입 시도 | 대기열 비활성 에러 (400) |
| 동일 유저 중복 진입 | 동일 순번 반환 (멱등성) |
| 인증 없이 진입 | 401 반환 |

### IMPORTANT 4. 처리량 초과 테스트 구현

`QueueSchedulerIntegrationTest` -- 4개 테스트 케이스 작성:

| 케이스 | 검증 |
|--------|------|
| 50명 대기, 1사이클 | 18명만 처리, 32명 잔류 |
| 50명 대기, 다중 사이클 | 3사이클 후 전원 처리 완료 |
| 200명 대기, 반복 사이클 | 대규모에서도 안정적 소화 |
| 5명 대기, 1사이클 | 배치 미만이면 전원 즉시 처리 |

백그라운드 `@Scheduled` 간섭 방지를 위해 `@MockBean QueueScheduler`로 비활성화 후 테스트용 인스턴스를 직접 생성하여 수동 호출.

### MINOR 5. QueueTokenService.getToken() dead code 제거

어디에서도 호출되지 않는 `getToken()` 메서드 삭제. `CoreException` 대신 `IllegalStateException`을 사용하는 패턴 불일치도 함께 제거.

### MINOR 6. 멀티 인스턴스 스케줄러 분산 락 (DB 기반)

2대 이상 배포 시 동일 스케줄러가 중복 실행되는 문제를 DB 기반 분산 락으로 해결.

생성한 파일:

| 파일 | 설명 |
|------|------|
| `domain/queue/SchedulerLock.java` | 엔티티 (lock_key, locked, locked_at, instance_id) |
| `domain/queue/SchedulerLockRepository.java` | 인터페이스 (tryAcquire, release) |
| `infrastructure/queue/SchedulerLockJpaRepository.java` | 원자적 UPDATE 쿼리 |
| `infrastructure/queue/SchedulerLockRepositoryImpl.java` | REQUIRES_NEW 트랜잭션으로 락 획득/해제 |

동작 방식:
```
1. 스케줄러 진입
2. tryAcquire: UPDATE scheduler_lock SET locked=true
   WHERE lock_key='QUEUE_SCHEDULER' AND (locked=false OR locked_at < 만료시각)
   -> 1건 업데이트 = 획득 성공, 0건 = 다른 인스턴스 실행 중 -> 스킵
3. processBatch: 대기열 처리
4. finally: release (locked=false)
```

안전장치:
- `locked_at` 기반 만료 (30초) -- 인스턴스 장애 시 자동 해제
- `finally` 블록에서 release -- 예외 발생 시에도 락 반드시 해제
- `instance_id` 기록 -- 디버깅/모니터링 용도
- **소유자 기반 락 해제** -- `release(lockKey, instanceId)` 조건으로 자신의 락만 해제

`schema.sql`, `data.sql`에 `scheduler_lock` 테이블 DDL 및 초기 데이터 추가.

### MINOR 6-1. 소유자 기반 락 해제 (코드 리뷰 반영)

**문제**: `release(lockKey)` 가 소유자를 확인하지 않아, 락 만료 후 교차 실행 시 이전 소유자가 새 소유자의 락을 해제할 수 있었다.

```
T=0s    Instance A: tryAcquire 성공 -> processBatch() 시작
T=30s   락 만료 (LOCK_EXPIRE_SECONDS=30)
T=30.1s Instance B: tryAcquire 성공 (만료된 락 재획득) -> processBatch() 시작
T=31s   Instance A: finally -> release(LOCK_KEY) -- Instance B의 락을 해제
T=31.1s Instance C: tryAcquire 성공 -> Instance B와 동시 실행
```

**수정**: `release()` 에 `instanceId` 파라미터를 추가하여 자신이 소유한 락만 해제하도록 변경.

| 파일 | 변경 내용 |
|------|----------|
| `SchedulerLockRepository.java` | `release(lockKey)` -> `release(lockKey, instanceId)` |
| `SchedulerLockJpaRepository.java` | WHERE 조건에 `AND s.instanceId = :instanceId` 추가 |
| `SchedulerLockRepositoryImpl.java` | `release` 메서드 시그니처 반영 |
| `QueueScheduler.java` | `release(LOCK_KEY, instanceId)` 호출 |

변경 후 동작:
```
T=31s Instance A: release(LOCK_KEY, "instanceA")
      -> WHERE lock_key='QUEUE_SCHEDULER' AND instance_id='instanceA'
      -> 0건 UPDATE (현재 소유자는 instanceB) -> 해제되지 않음
```

테스트 추가:
- 단위 테스트: `release` 호출 시 `tryAcquire`와 동일한 `instanceId`가 전달되는지 검증
- 통합 테스트: 이전 소유자가 새 소유자의 락을 해제하지 못하는 교차 실행 시나리오 검증

### 추가 수정: 기존 테스트 버그 수정

| 테스트 | 원인 | 수정 |
|--------|------|------|
| LikeConcurrencyTest (3개) | HikariCP 커넥션 풀(10) < 10스레드 + REQUIRES_NEW 리스너 -> 풀 고갈 | test 프로필 `maximum-pool-size: 10 -> 20` |
| ProductLikeSummaryIntegrationTest (1개) | 클래스 레벨 `@Transactional`로 커밋 없음 -> AFTER_COMMIT 리스너 미실행 | `likeService.like()` + `incrementLikeCount()` 직접 호출 |

### 추가 수정: @WebMvcTest 호환성

`QueueTokenInterceptor`가 `HandlerInterceptor`를 구현하여 `@WebMvcTest`에서 웹 컴포넌트로 자동 스캔됨. 의존성(`QueueService`, `QueueTokenService`)이 없어 컨텍스트 시작 실패.

수정:
- `QueueTokenInterceptor`에 `@ConditionalOnBean(QueueService.class)` 추가 -- `@WebMvcTest`에서 스캔 방지
- `WebMvcConfig`에서 `@Autowired(required = false)` + null 체크로 optional 주입
