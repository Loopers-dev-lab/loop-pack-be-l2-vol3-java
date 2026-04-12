

## 학습내용

> 우리는 이제, 유저가 우리 서비스에서 더 좋은 상품을 탐색할 수 있도록 상품 랭킹을 제공할 거예요.
>
>
> Round8 대기열에서 **ZSET** 을 순서 관리용으로 사용해봤다면, 이번에는 **점수 기반 실시간 랭킹**에 활용합니다.
> Round7 에서 구축한 **Kafka → commerce-collector 파이프라인**이 쌓아둔 이벤트 데이터를 기반으로, `ZSET` 에 랭킹 점수를 반영하고, API 는 이 데이터를 기반으로 TOP-N 및 개별 상품 순위 정보를 제공합니다.
>

<aside>
🎯

**Summary**

</aside>

**Round7** 에서 우리는 Kafka를 통해 유저 행동 이벤트(조회, 좋아요, 주문 등)를 수집하고, `commerce-collector`가 `product_metrics`에 집계하는 파이프라인을 구축했습니다. **Round8** 에서는 Redis Sorted Set을 활용해 대기열의 순서를 관리했죠.

이번 라운드에서는 이 두 가지를 결합합니다. **collector가 소비하는 이벤트를 기반으로 Redis ZSET에 랭킹 점수를 실시간 갱신**하고, **API는 ZSET을 조회해 랭킹 기능을 제공**하도록 설계합니다.

```mathematica
[commerce-api]
     → 유저 행동 이벤트 발행 (조회, 좋아요, 주문)
     → Kafka

[commerce-collector]
     → 이벤트 소비
     → product_metrics upsert (R7)
     → Redis ZSET 랭킹 점수 갱신 (R9) ← 여기가 이번 주차

[commerce-api]
     → GET /rankings/top (ZREVRANGE)
     → GET /products/{id}/rank (ZREVRANK)
```

<aside>
📌

**Keywords**

</aside>

- Redis Sorted Set (ZSET)
- ZINCRBY 기반 실시간 집계
- Top-N API
- 일별 Key 전략 & TTL
- 가중치 합산 (Weighted Sum)
- 콜드 스타트 문제

<aside>
🧠

**Learning**

</aside>

## 📊 Ranking System

<aside>
💡

**Ranking** 시스템의 특성은 뭐가 있을까요?

- 랭킹 정보가 많이 요청됨
    - **Top-N API :** 항상 유저에게 인기 있는 지면이자 다양한 큐레이션 요소로 활용

      > 홈 메인 - 인기 상품, 오늘의 Top 10, 인기순 정렬 등
    >
    - **개별 순위 조회 :** 특정 상품이 현재 몇 위인지 표기할 때 활용
- 주기적인 갱신이 필요함
    - 일간, 주간, 월간 단위로 새롭게 시작되는 랭킹 구조 필요 **(이번엔 일간만 진행)**
    - **콜드 스타트 문제**가 존재할 수 있음
- **RDB** 로 해결하면 안되는 걸까?
    - DB 쿼리 (`GROUP BY + ORDER BY`)  는 데이터가 쌓일수록 느려짐
    - 조회 빈도가 매우 높아 DB 과부하로 이어질 수 있음
</aside>

### 📈 Redis ZSET

> 이전에 캐시를 위해 **Strings 자료형**을 사용해 보았다면, 이번에는 **SortedSet 자료구조**를 활용해볼 예정입니다.
>
- **ZSET 구조**
    - `(member, score)` 쌍을 score 기준으로 정렬된 상태로 유지함
    - **삽입/수정** : O(logN)
    - **Top-N 조회** : O(N)
- **주요 연산**
    - `ZADD key score member` : score 와 함께 member 저장 (이미 있을 경우 갱신됨)
    - `ZREVRANGE key 0 N WITHSCORES` : score 기준 Top-N 조회
    - `ZREVRANK key member` : 특정 멤버의 순위 조회
    - `ZSCORE key member` : 특정 멤버의 스코어 조회
    - `ZCARD key` : 멤버 수 조회
- **Why ZSET ?**
    - 정렬 기능이 내장되어 있어 별도 인덱스 등에 대한 설정 불필요
    - 실시간으로 랭킹 반영 가능
    - 다양한 조회 지원 : Top-N, 특정 `member` 의 순위, score 범위 검색 등
- **다른 방식과 비교**


    | 방법 | 장점 | 단점 | 적합도 |
    | --- | --- | --- | --- |
    | DB ORDER BY | 정합성 ↑ | 느림, 부하 ↑ | 초기/소규모 |
    | 캐시(Map) + 정렬 | 간단 | 매 요청마다 정렬 필요 | 중간 |
    | Redis ZSET | 빠른 정렬 내장, 다양한 조회 지원 | 메모리 사용 ↑ | 대규모 트래픽 |

### 🔑 Key 설계 - 시간의 양자화

<aside>
💡

**Key 설계는 왜 중요할까요?**

랭킹은 언제부터 언제까지의 집계인지가 명확해야 의미가 있어요.

**타임 윈도우 별로 키를 분리**하면 랭킹 데이터에 대한 리셋, 만료, 보정 등 운영을 하기 좋습니다.

즉, 단순 누적만 할 경우 **랭킹의 의미가 퇴색**될 수 있어요.

</aside>

- **누적만 할 경우의 문제**
    - 오래 전 점수를 쌓은 상품이 계속 상위에 노출됨 → 신상품은 노출될 기회가 사라짐
    - 결국 **롱테일 (Long Tail) 현상** 이 나타나고, 소수 상품이 인기 상위권을 독식하게 됨
    - 특정 시간 단위 집계로 **공정성을 확보**하고 신선한 정보를 노출해야 함
- **시간의 양자화**
    - 집계를 일정 단위 (시간, 일, 주, 월 등) 으로 나누어서 관리
    - **일간 집계** - 하루 단위로 랭킹을 관리할 수 있어야 함 → 오늘 점수와 어제 점수를 분리
    - **TTL** - 메모리 관리를 위해 시간 윈도우의 1.5배~2배 정도로 잡으면 안정적
- 예시

    ```java
    rank:all:20250906 // 9월 6일 랭킹 집계
    rank:all:20250907 // 9월 7일 랭킹 집계
    ```


## ⚖️ 가중치 합산 (Weighted Sum)

<aside>
💡

**왜 가중치가 필요한가요?**
(1) 좋아요/구매/매출액은 스케일이 달라 단순 합산 시 특정 지표가 지배  
(2) 서비스 전략에 따라 어떤 지표를 더 중요하게 볼지 달라짐

</aside>

- **총점식**

    ```java
    Sum(p) = W(like)*Count(p.like) + W(order)*Count(p.order) + W(view)*Count(p.view)
    
    * W : Weight (가중치)
    * Count : 스코어를 구성하는 요소 수
    ```

- **기본 가중치 예시**
    - *총합 1 이 되도록 설계*
    - **Weight(view) = 0.1**
        - 조회 수는 가장 많을 것이므로 전체 스코어를 잡아먹을 수 있음
    - **Weight(like) = 0.2**
        - 좋아요 수는 주문 수보다는 **구매 결정** 관점에서 덜 중요한 지표이므로 조금 낮게 설정
    - **Weight(order)** = 0.7
        - 주문 수는 유저가 구매를 결정했으므로 가장 중요한 지표라고 보고 가중치를 높게 설정

### ❄️ 콜드 스타트 문**제**

<aside>
⚠️

**콜드 스타트 ?**

집계 윈도우가 시작되는 시점에는 아직 점수가 쌓이지 않았을 수 있기 때문에 랭킹정보가 존재하지 않거나 대상이 부족하게 됩니다.

즉, 랭킹 정보를 위해 ZSET 을 조회했지만 **상품 목록이 없는 문제** 가 생기게 되죠. 전날 인기 있었던 상품도, 새벽에 들어온 신상품도 동일한 조건으로 시작하는 문제가 발생해요.

혹은 랭킹에 진입하지 못한 상품들은 자연스레 **클릭도 구매도 발생하지 않게 되는 악순환**으로 이어질 수도 있습니다.

</aside>

**문제점**

- 윈도우가 변경되는 시점에는 대부분 상품 점수가 0이므로 **랭킹이 의미 없어짐**
- 사용자에게는 **어제 인기 있었던 상품** 또한 랭킹에서 보길 기대할 수 있음

### 🔄 **해결**

- **Score Carry-Over**
    - 새로운 키 생성 시 전날 점수의 일부를 적은 가중치를 곱해 미리 복사함
    - 이 떄, 가중치는 작은 값으로 가져가 오늘의 점수가 상위로 올라가지 못하는 문제를 방지

    ```java
    ZUNIONSTORE ranking:all:20250907 1 ranking:all:20250906 WEIGHTS 0.1 AGGREGATE SUM
    
    `ranking:all:20250907` 로 `ranking:all:20250906` 의 스코어들에 0.1 을 곱해서 복사
    ```


### 📊 Before / After Score

**Before (20250906)**

```
product:101 → 100
product:202 → 50
```

**After (20250907, carry-over 10%)**

```
product:101 → 10
product:202 → 5
```

---

### 10-13. 부분 실패(Partial Failure) 시 Redis 자가 복구(Self-Healing)를 위한 책임 분리 (리뷰 반영)

#### 고민 배경
`RankingAggregationService`의 핵심 파이프라인은 다음과 같은 3단계로 구성됩니다.
1. `filterAlreadyHandled`: `event_handled` 테이블을 조회하여 중복 메시지 필터링
2. `persistDeltas`: DB에 점수를 UPSERT 하고 `event_handled` 기록 (1개의 트랜잭션)
3. `publishScores`: 트랜잭션 커밋 이후 Redis ZADD 수행

이 때 **부분 실패(Partial Failure) 시나리오**를 고려해야 합니다. 만약 2번(DB 기록)까지 성공적으로 완료되었으나 3번(Redis 통신) 중 예외가 발생하면 어떻게 될까요?
- 예외가 던져지면 Consumer는 `ack.acknowledge()`를 호출하지 않으므로 Kafka는 해당 배치를 컨슈머에게 **재전달(Redelivery)** 합니다.
- **기존 로직의 문제점**: 재전달된 메시지들이 1번 단계(`filterAlreadyHandled`)를 거칠 때, 이미 DB에 `event_handled` 기록이 존재하므로 **전부 중복(Already Handled)으로 판정되어 무시됩니다.** 결과적으로 DB 업데이트 로직뿐만 아니라 **마지막의 Redis ZADD 로직까지 통째로 건너뛰게 되어**, Redis 데이터는 영원히 DB와 싱크가 맞지 않게 됩니다(Self-Healing 불가).

#### 해결 방안: DB 멱등성 필터링과 Redis 동기화 로직의 분리
이러한 "Eventual Consistency(최종적 일관성) 깨짐" 현상을 방지하기 위해, ZADD 연산의 특성(같은 값을 여러 번 일관되게 덮어써도 안전한 멱등성)을 십분 활용하여 **DB 업데이트 여부와 무관하게 무조건 최신 DB 스냅샷을 Redis에 덮어쓰도록** 책임을 분리했습니다.

```java
public void processCatalogBatch(List<ConsumerRecord<String, String>> records) {
    if (records == null || records.isEmpty()) return;

    // 1. 이번 배치가 다루는 모든 productId 추출 (성공/재발행 무관)
    Set<Long> allProductIdsInBatch = batchAggregator.extractProductIds(records);

    // 2. DB UPSERT는 중복을 걸러낸 "Fresh" 이벤트만 반영 (멱등성 보장)
    List<ConsumerRecord<String, String>> fresh = filterAlreadyHandled(records);
    if (!fresh.isEmpty()) {
        Map<Long, MetricDelta> perProduct = batchAggregator.aggregateCatalog(fresh);
        persistDeltas(perProduct); 
    }

    // 3. Redis 갱신은 "Fresh" 여부와 무관하게 항상 실행. 
    //    부분 실패에 의해 재배달된 메시지라도 이 단계를 통해 Redis 점수가 완벽히 자가 복구됨.
    Map<Long, Double> scoresToPublish = recalculateFromSnapshot(allProductIdsInBatch);
    publishScores(scoresToPublish);
}
```

이 변경을 통해, 일시적인 Redis 다운이나 타임아웃 오류가 발생하더라도 **Kafka의 재전달 매커니즘을 통해 DB 트랜잭션 롤백 없이 자동으로 최신 랭킹 점수 싱크를 맞출 수 있는 견고한 분산 환경 내결함성(Fault Tolerance)**을 확보했습니다.

---### 🌾 Summary

| **항목** | **설명** |
| --- | --- |
| **랭킹의 목적** | 유저에게 인기 상품을 효율적으로 노출 (Top-N, 개별 순위) |
| **핵심 기술** | Redis ZSET (정렬 내장 + O(logN) 삽입/수정) |
| **데이터 소스** | R7의 Kafka → collector 파이프라인이 수집한 유저 행동 이벤트 |
| **Key 설계** | 일별 키 분리 (rank:all:yyyyMMdd) + TTL로 메모리 관리 |
| **가중치 합산** | 시그널별 가중치를 곱해 단일 스코어로 합산 |
| **콜드 스타트** | Score Carry-Over (전일 점수 일부 복사)로 완화 |
| **R7/R8과의 관계** | R7 collector가 이벤트 → ZSET 갱신, R8 ZSET 경험을 랭킹에 재활용 |

---

<aside>
📚

**References**

</aside>

| 구분 | 링크 |
| --- | --- |
| 🔍 Redis Sorted Sets | [RedisGate - SORTED SETS](https://redisgate.kr/redis/command/zsets.php) |
| ⚙ Spring Data Redis | [Spring Data Redis - Redis Template](https://docs.spring.io/spring-data/redis/reference/redis/template.html) |
| 📖 Redis Sorted Set을 이용한 랭킹 관리 | [Medium - Redis Sorted Set을 이용한 랭킹 관리](https://medium.com/sjk5766/redis-sorted-set%EC%9D%84-%EC%9D%B4%EC%9A%A9%ED%95%9C-%EB%9E%AD%ED%82%B9-%EA%B4%80%EB%A6%AC-38d28712a8b9) |

<aside>
🌟

**Next Week Preview**

</aside>

> **일간을 넘어서 주간/월간 집계를 만들려면 어떻게 하는 게 좋을까?**
>
>
>
> 이번 주차에는 **Redis ZSET 을 기반으로 랭킹 시스템을 구축**할 방법에 대해 고민해 보았습니다. 유저에게 어떻게 랭킹 시스템을 제공할 수 있을까라는 고민을 통해 **인기 있는 상품을 효율적으로 노출**할 수 있게 되었어요.
>
> 이제는 일간 집계를 활용해 주간 집계 데이터를 만들고, 월간 집계 데이터를 만들기 위해선 어떻게 해야할까 고민해볼 거예요. 차주에는 점차 많아지는 데이터나 통계들을 주기적으로 생성해내는 기능을 만들어 봅니다.
>
> 
> 


---
## 구현과제
# 📝 Round 9 Quests

---

## 💻 Implementation Quest

> 이번에는 Redis ZSET 을 이용해 랭킹 시스템을 만들어 볼 거예요.
이전에 **Kafka Consumer** 를 통해 적재하던 집계정보를 기반으로 **실시간 랭킹 파이프라인을 구축**해봅니다.
>

<aside>
🎯

**Must-Have (이번 주에 무조건 가져가야 좋을 것-**무조건 ****하세요**)**

- Redis ZSET
- Realtime Ranking
- Ranking API

**Nice-To-Have (부가적으로 가져가면 좋을 것-**시간이 ****허락하면 ****꼭 ****해보세요**)**

- 초 실시간 (시간 단위) 랭킹 만들기
- 콜드 스타트 문제 해결
</aside>

### 📋 과제 정보

이전 주차에서 만들었던 `product_metrics` 테이블을 응용해 **카프카 컨슈머**에서 실시간 랭킹 집계를 시작합니다. 또한 이 랭킹 정보를 바탕으로 **오늘의 인기상품** API 를 만들어 봅니다.

- **(Nice to Have) 카프카 배치 리스너**
    - 메세지 단건 처리는 너무 많은 ZSET 연산, DB 연산을 동반할 수 있으므로 배치 리스너를 이용해 애플리케이션에서 정제하고, 스루풋을 높이기

![K-020.png](attachment:09de1aa1-d664-4e3e-82f0-dffa1db95716:K-020.png)

### (1) Kafka Consumer  → Redis ZSET 적재

- 조회/좋아요/주문 이벤트 등을 컨슘해 일간 키 (e.g. `ranking:all:{yyyyMMdd}`) ZSET 에 점수를 누적합니다.
- 각 이벤트에 따라 적절한 **Weight** 및 **Score** 를 고민해보고 이를 기반으로 랭킹을 반영합니다.

    ```java
    e.g.
    조회 : Weight = 0.1 , Score = 1
    좋아요 : Weight = 0.2 , Score = 1
    주문 : Weight = 0.6 , Score = price * amount (정규화 시에는 log 적용도 가능)
    ```

- **ZSET 스펙**
    - `TTL` : 2Day
    - `KEY` : ranking:all:{yyyyMMdd}

### (2) Ranking API 구현

- 랭킹 Page 조회
    - GET `/api/v1/rankings?date=yyyyMMdd&size=20&page=1`
- 상품 상세 조회 시 해당 상품의 랭킹 정보 추가

### (Additionals) 너무 쉽다구요?

- **실시간 Weight 조절**
    - 점수 계산에 사용되는 Weight 를 어떻게 수정할 수 있을지 고민해보기
- **실시간 랭킹**
    - 일간 랭킹이 아닌, 1시간 단위 랭킹을 만들어보기
- **콜드 스타트 완화를 위한 Scheduler 구현**
    - 23시 50분에 Score Carry-Over 를 통해 미리 랭킹판 생성하기

---

## ✅ Checklist

### 📈 Ranking Consumer

- [ ]  랭킹 ZSET 의 TTL, 키 전략을 적절하게 구성하였다
- [ ]  날짜별로 적재할 키를 계산하는 기능을 만들었다
- [ ]  이벤트가 발생한 후, ZSET 에 점수가 적절하게 반영된다

### ⚾ Ranking API

- [ ]  랭킹 Page 조회 시 정상적으로 랭킹 정보가 반환된다
- [ ]  랭킹 Page 조회 시 단순히 상품 ID 가 아닌 상품정보가 Aggregation 되어 제공된다
- [ ]  상품 상세 조회 시 해당 상품의 순위가 함께 반환된다 (순위에 없다면 null)

### 🧪 검증

- [ ]  이벤트 발행 → ZSET 점수 반영 → API 조회까지 E2E 흐름이 정상 동작하는지 확인
- [ ]  일자가 변경되어도 이전 날짜의 랭킹 조회가 정상적으로 동작하는지 확인
- [ ]  가중치 적용이 의도대로 랭킹 순서에 반영되는지 확인 (e.g. 주문 1건 > 좋아요 3건)

---

## 🧭 구현 전 고민 포인트

멘토링 내용 + 과제 요구사항 + 기존 R7/R8 코드(`MetricsEventService`, `product_metrics`, `event_handled`, ZSET 기반 큐)를 종합하여 구현 전에 결정해야 할 의사결정을 정리한다.

> ### ⚡ 아키텍처 전환 공지 (중요)
>
> 본 섹션 이하 모든 결정은 **두 차례의 재검토**를 거쳐 도달한 최종안이다.
> 중간 단계에서 폐기된 방향들은 각 섹션 내부에 "의사결정 이력" 으로 보존한다.
>
> **[초기안]** 이벤트 → hour bucket ZSET 실시간 ZADD → 일간 ZSET은 ZUNIONSTORE로 파생
>
> **[1차 전환 — 폐기됨]** ZUNIONSTORE 비선형 함정 발견
>
> hour bucket에 `log1p(count)` 기반 점수를 저장하고 `ZUNIONSTORE SUM` 으로 일간 점수를 만들면 `Σ log1p(x) ≠ log1p(Σx)` 이라 "꾸준한 활동 상품" 이 과대평가되는 편향이 생긴다. 이 함정을 피하려고 **"Consumer는 DB UPSERT만, API 요청 시점에 DB GROUP BY → Java 점수 계산 → ZSET 캐시"** 방향으로 한 번 전환했었다.
>
> **[2차 전환 — 채택]** 멘토링 원칙 재확인 → Consumer 배치 리스너 ZADD 엎어치기로 복귀
>
> 멘토링 원문을 재검토한 결과 네 가지 원칙을 확인했다.
> 1. ZINCRBY는 유실 복구가 어려우므로 **"매트릭 재조회 → 점수 계산 → ZADD 엎어치기"** 방식 권장
> 2. **Kafka 배치 리스너** 로 이벤트를 압축(합산) 하여 "N건 → 1회" DB/Redis 접근 감소
> 3. ZSET 자체에는 짧은 캐시 TTL **불필요** (Consumer가 상시 엎어치므로 stale 걱정 없음). 과제 명세 `TTL: 2Day` 는 **retention 성격** 으로 해석
> 4. 취소/환불 드리프트는 실시간 차감 금지, **주기적 보정 배치** 로 원장 기반 덮어쓰기
>
> 이 원칙들을 적용하면 **1차 전환의 동기였던 ZUNIONSTORE 비선형 함정이 애초에 발생하지 않는다**. hour bucket을 쪼개지 않고 **일간 단일 키 하나에 엎어치기** 만 하면 `log1p` 가 "일간 총합에 한 번만" 적용되므로 수학적으로 정확하다. 따라서 1차 전환의 "API 요청 시점 집계" 는 **철회** 되고, 초기안의 "Consumer 재계산 ZADD" 철학이 복원되되 트리거가 **Kafka 배치 리스너 poll() 주기** 로 명확히 지정된다.
>
> **최종 아키텍처:**
>
> ```
> [실시간 쓰기 경로 — commerce-streamer]
>   Kafka (catalog-events / order-events)
>     → CatalogEventConsumer / OrderEventConsumer  @KafkaListener (batch=true)
>         → poll() 마다 List<ConsumerRecord> 수신
>         → 상품별 그룹핑/합산 (view++, like±, orderAmount+=)
>         → MetricsEventService.processBatch(perProductDeltas) @Transactional
>             → event_handled INSERT (멱등성 방어)
>             → product_metrics Native UPSERT (복합키, GREATEST 가드)
>             → 매트릭 재조회 → RankingScoreCalculator.calculate()
>             → RankingWriter.upsertScore(dailyKey, productId, score)
>                 └── ZADD ranking:all:{yyyyMMdd} score pid
>                 └── EXPIRE ranking:all:{yyyyMMdd} 2d (최초 생성 시에만)
>         → ack.acknowledge()
>
> [읽기 경로 — commerce-api]
>   RankingV1Controller
>     → RankingFacade.getDailyRanking(date, pageable)
>         → RankingRepository.getTopN(key, page, size)  (단순 ZREVRANGE)
>         → ProductFacade.findVisibleByIds(productIds)  [#8-2]
>         → 응답 조립
>
> [콜드 스타트 완화 — commerce-streamer @Scheduled, 과제 Nice-To-Have 포함]
>   RankingCarryOverScheduler (구 #8-4 "캐시 스탬피드 스케줄러", 목적 재정의)
>     → 매일 23:50 실행
>     → 오늘 점수에 0.01 같은 낮은 가중치를 곱해 내일 키에 미리 시드
>     → 자정 직후 dailyRank 가 null 로 노출되는 구간 제거
>
> [주기적 보정 경로 — 설계만 보존, 이번 구현 스코프 제외]
>   RankingCorrectionBatch (구현 안 함, #6 에 설계만 남김)
>     → 취소/환불 드리프트 누적 시 원장 기반 재적재용. 운영 필요 시 후속 과제에서 구현
> ```
>
> **책임 분리 — read/write 자연 분리:**
>
> | 모듈 | 포함 컴포넌트 | 역할 |
> |------|---------------|------|
> | **commerce-streamer** | `MetricsEventService`, `ProductMetrics*`, `RankingScoreCalculator`, `RankingWeights`, `RankingWriter`, `RedisRankingWriter`, `RankingCarryOverScheduler` | 이벤트 소비 + 매트릭 적재 + 점수 계산 + ZADD 엎어치기 + Carry-Over |
> | **commerce-api** | `RankingRepository` (읽기 전용), `RankingFacade`, `RankingV1Controller`, `RankingEntry` | 단순 `ZREVRANGE` + 상품 상세 조립 |
> | 공유 | `RankingKey` 포맷 문자열 (`ranking:all:{yyyyMMdd}`) | 각 app에 복제 + 양쪽 테스트로 회귀 방지 |
>
> commerce-api는 **점수 공식을 알 필요가 없다** — streamer가 이미 계산해서 ZSET에 저장해뒀기 때문이다. 마찬가지로 streamer는 응답 조립 로직을 알 필요가 없다. 이 자연스러운 read/write 분리 덕분에 `RankingScoreCalculator` 같은 도메인 로직 중복이 발생하지 않는다.
>
> **이 아키텍처가 해소/단순화하는 것:**
>
> | 항목 | 결과 |
> |------|------|
> | 과제 명세 "Consumer → ZSET 적재" | **문자 그대로 충족** |
> | 체크리스트 "이벤트 발생 후 ZSET 반영" | **문자 그대로 충족** |
> | 과제 명세 `TTL: 2Day` | retention TTL로 자연 매핑 |
> | 과제 Nice-To-Have 카프카 배치 리스너 | **이번 구현에 함께 포함** (과제 분류는 그대로) |
> | 과제 Nice-To-Have "23:50 Score Carry-Over" | **이번 구현에 함께 포함** (과제 분류는 그대로) |
> | ZUNIONSTORE 비선형 함정 | **원천 발생 안 함** (단일 키 + log는 1회만) |
> | hour bucket 복잡 구조 | ZSET에는 불필요 — `product_metrics.bucket_hour` 는 DB 집계/복구 단위로만 존속 |
> | #8-4 캐시 스탬피드 스케줄러 | **목적 재정의** → 콜드 스타트 완화 + Carry-Over |
> | #5-7 "오늘 30초 / 과거 2일" TTL 분기 | **폐기** — 단일 2일 retention |
> | #8-3 `replaceScores` DEL→ZADD 공백 | **해소** — 증분 ZADD로 대체, 원자성 이슈 소멸 |
> | #8-2 빈 집계 결과 캐싱 이슈 | **원천 해소** — 캐시 개념 자체 소멸 |
> | #4-3 좋아요 취소 ZSET 드리프트 | 배치 리스너 경로에서 매트릭 재조회 시 자연 반영 (#7 상세) |
> | #6 주기적 보정 배치 | **설계만 보존, 구현 제외** (실시간 압축은 배치 리스너가 담당) |
>
> **의사결정 이력 보존:**
> 아래 각 섹션은 이 2차 전환을 반영한 최종안 기준으로 작성되었다. 초기 검토 과정(hour bucket ZUNIONSTORE 방식, API 요청 시점 집계, Spring Event 경로 등) 은 "의사결정 이력" 으로 해당 섹션 내에 보존한다.

### 1. 점수 반영 방식: ZINCRBY vs 재계산 ZADD

랭킹 ZSET에 점수를 어떻게 반영할 것인가에 대한 두 가지 접근.

#### (A) ZINCRBY — 이벤트마다 가중치 곱한 점수를 증분

```
이벤트 소비 → ZINCRBY ranking:all:20260407 0.6 product:123
```

| 장점 | 단점 |
|------|------|
| 구현 단순 (이벤트 1건 = Redis 명령 1개) | 메시지 유실 시 점수 유실, 복구 불가 |
| product_metrics 조회 불필요 → DB 부하 없음 | 중복 소비 시 점수 이중 반영 (at-least-once 환경에서 부풀려짐) |
| 실시간성 최고 | Redis 장애 복구 시 ZSET 재구축 별도 배치 필요 |
| | product_metrics와 ZSET 점수 간 정합성 보장 안 됨 (시간이 갈수록 drift) |

#### (B) product_metrics 업데이트 후 → 재계산 → ZADD 덮어쓰기 ✅ 채택

```
이벤트 소비 → product_metrics UPDATE → metrics 기반 점수 계산 → ZADD ranking:all:20260407 score product:123
```

| 장점 | 단점 |
|------|------|
| RDBMS가 SSOT — Redis가 날아가도 DB에서 재구축 가능 | 이벤트마다 metrics 값 필요 (트랜잭션 내에서 이미 보유 가능) |
| 멱등성 보장 — 같은 metrics → 같은 점수 → 덮어쓰기 안전 | 구현 복잡도 약간 증가 |
| product_metrics와 ZSET 항상 일치 | DB 부하가 이벤트 수에 비례 |
| 가중치 공식 변경 시 DB 기반 전체 재계산 가능 | |

#### 비교 요약

| 기준 | ZINCRBY | 재계산 ZADD |
|------|---------|-------------|
| 구현 난이도 | 쉬움 | 보통 |
| 실시간성 | 높음 | 높음 |
| 멱등성 | X | O |
| 유실 복원력 | 낮음 | 높음 |
| DB 부하 | 없음 | 이벤트당 SELECT 1회 (트랜잭션 내 metrics 재사용 시 0회) |
| Redis 장애 복구 | 별도 배치 필요 | DB에서 바로 재구축 |
| 가중치 변경 대응 | 기존 점수 보정 불가 | 전체 재계산 가능 |

#### 결정 — (B) 재계산 ZADD 채택, 트리거는 "Kafka 배치 리스너 poll() 주기"

**이유:**
1. 기존 `MetricsEventService` 가 이미 `product_metrics` 를 트랜잭션 안에서 업데이트하고 있어, DB 원장은 정확히 유지된다.
2. 기존 `event_handled` UNIQUE 제약 기반의 멱등성 방어와 정합성이 맞는다. ZINCRBY는 이 방어 바깥에서 동작하므로 중복 소비 시 점수가 부풀려질 수 있다.
3. 가중치 공식이 바뀌거나 Redis가 날아가도 RDBMS의 `product_metrics` 를 기준으로 ZSET을 언제든 재구축할 수 있다.
4. 멘토링에서도 "ZINCRBY는 메시지 유실 시 데이터가 틀어질 위험이 있으니, 매트릭을 먼저 업데이트하고 점수를 재계산해 ZSET에 덮어씌우는 방식이 복원력 측면에서 안전하다" 고 권장했다.
5. 멘토링의 또 다른 원칙 "이벤트를 모아 압축(합산) 한 뒤 반영하여 N건 → 1회로 줄이기" 는 **Kafka 배치 리스너** 로 달성된다. 단건 리스너보다 배치 리스너가 본 결정의 자연스러운 구현 도구가 된다.

**최종 실행 경로:**

```
Kafka (catalog-events / order-events)
  → @KafkaListener(batch = true)
  → poll() 마다 List<ConsumerRecord> 수신 (수~수백 건)
  → 상품별 그룹핑/합산 (view++, like±, orderAmount+=)
  → MetricsEventService.processBatch(perProductDeltas) @Transactional
      → event_handled INSERT (멱등성)
      → product_metrics Native UPSERT (복합키, GREATEST 가드) — #8-6
      → 매트릭 재조회 → RankingScoreCalculator.calculate()
      → RankingWriter.upsertScore(dailyKey, productId, score)
          └── ZADD ranking:all:{yyyyMMdd} score pid
          └── EXPIRE ranking:all:{yyyyMMdd} 2d (최초 생성 시에만)
  → ack.acknowledge()
```

이로써 **(B) 재계산 ZADD 의 철학(DB 원장 → 점수 계산 → ZSET 엎어치기) 은 유지** 하면서, 트리거는 "이벤트 단건" 이 아니라 "배치 리스너 poll() 주기" 로 정의되어 N→1 압축 효과까지 얻는다.

**의사결정 이력 — 1차 전환 (폐기됨):**

초기에는 "이벤트 소비 직후 재계산 → ZADD" 를 가정했다. 이후 hour bucket 분할 + ZUNIONSTORE 설계에서 `Σ log1p(x) ≠ log1p(Σx)` 수학적 문제를 발견해, **트리거를 "API 요청" 으로 옮기고 Consumer는 DB UPSERT 만** 수행하는 방향으로 한 번 전환했었다.

그러나 재검토에서 이 1차 전환이 다음 대가를 치르고 있었음을 확인했다.
- 과제 명세 "Consumer → ZSET 적재" 와 체크리스트 "이벤트 발생 후 ZSET 반영" 문자 해석과 충돌
- 멘토링의 "배치 리스너로 압축" 원칙 미활용
- API 요청 시점 DB GROUP BY 의 부하/스탬피드 우려

그리고 ZUNIONSTORE 문제 자체는 **"일간 단일 키에 log1p 를 1회만 적용"** 하는 구조에서 원천 발생하지 않는다는 점이 확인됨에 따라, 1차 전환의 근거가 소멸했다. 이에 본 결정의 트리거를 **"Kafka 배치 리스너 poll() 주기"** 로 최종 확정한다. Spring Event 리스너 경로(#2) 도 함께 불필요해져 폐기된다.

---

### 2. 점수 계산을 어디서 할 것인가

ZSET 갱신 로직을 어느 레이어/클래스에 둘 것인가. 기존 이벤트 소비 흐름은 다음과 같다.

```
CatalogEventConsumer / OrderEventConsumer  (commerce-streamer)
  → MetricsEventService.processProductXxx()
       → @Transactional 안에서 product_metrics upsert
       → event_handled INSERT (멱등성 방어)
```

여기에 ZSET 갱신을 어디에 끼워 넣을지 세 가지 옵션을 검토했다.

#### (A) MetricsEventService 내부에서 metrics 갱신 직후 ZSET 갱신

```java
@Transactional
public void processProductViewed(...) {
    eventHandledRepository.save(...);
    ProductMetrics metrics = metricsRepository.findById(...).orElse(...);
    metrics.incrementViewCount();
    rankingRepository.updateScore(productId, calculateScore(metrics));
}
```

| 장점 | 단점 |
|------|------|
| 가장 단순 | metrics 갱신과 ZSET 갱신이 한 클래스에 섞임 (SRP 위반) |
| metrics가 트랜잭션 안에 있어 추가 SELECT 없음 | DB 트랜잭션 안에서 Redis 호출 → Redis 장애 시 트랜잭션 롤백 |
| 두 동작의 시점 차이 없음 | 트랜잭션 밖으로 빼기 어려움 |

#### (B) 별도 RankingService를 만들고 Consumer에서 두 번 호출

```java
@KafkaListener(...)
public void consume(...) {
    metricsEventService.processProductViewed(event);  // 트랜잭션 1
    rankingService.updateScore(event.productId());    // 트랜잭션 2 or no-tx
    ack.acknowledge();
}
```

| 장점 | 단점 |
|------|------|
| 책임 분리 명확 | Consumer가 오케스트레이션 책임을 가짐 |
| Redis 장애가 DB 트랜잭션에 영향 없음 | metrics 트랜잭션 커밋 후 metrics를 다시 SELECT해야 함 |
| ranking 실패해도 metrics는 살아 있음 | 중복 소비 방어가 첫 호출에서 끝나 두 번째 호출은 보호 못 받음 (재계산 ZADD라 덮어쓰기 멱등이므로 실무상 OK) |

#### (C) Spring Event 발행 → RankingEventListener가 ZSET 갱신 (초기 채택 → 2차 전환으로 폐기)

```java
// MetricsEventService
metricsRepository.save(metrics);
applicationEventPublisher.publishEvent(new MetricsUpdatedEvent(productId, metrics.snapshot()));

// RankingEventListener
@TransactionalEventListener(phase = AFTER_COMMIT)
public void on(MetricsUpdatedEvent e) {
    rankingRepository.updateScore(e.productId(), calculateScore(e.snapshot()));
}
```

| 장점 | 단점 |
|------|------|
| 책임 분리 + 느슨한 결합 (metrics는 ranking을 모름) | Spring Event 흐름이 한 단계 추가되어 디버깅 복잡도 ↑ |
| metrics 트랜잭션 커밋 후 실행 → DB/Redis 격리 | 이벤트 객체에 metrics snapshot을 담아야 함 |
| 이벤트에 snapshot을 담으면 추가 DB 조회 불필요 | |
| 기존 codebase가 `KafkaEventPublishListener`, `LikeEventListener` 등에서 이미 `@TransactionalEventListener(AFTER_COMMIT)` 패턴을 적극 사용 중 — 일관성 ↑ | |
| Redis 장애 시 metrics는 안전, 로깅/DLQ로 별도 처리 가능 | |

#### 비교 요약

| 기준 | (A) Service 내부 | (B) Consumer 오케스트레이션 | (C) Spring Event |
|------|------------------|------------------------------|------------------|
| 책임 분리 | ✗ | ✓ | ✓✓ |
| DB/Redis 격리 | ✗ (TX 안에 Redis) | ✓ | ✓✓ |
| 추가 DB 조회 | 없음 | metrics 재조회 필요 | 없음 (snapshot 전달) |
| 구현 단순성 | ✓✓ | ✓ | ✗ |
| 기존 패턴 일관성 | 보통 | 보통 | ✓ (이미 다수 사용) |

> **참고**: (A)(B)(C) 모두 **이벤트 1건당 1회 ZSET 갱신** 이 일어나는 단건 처리 방식이다. 최종 아키텍처에서는 이 전제 자체가 깨진다 — Kafka 배치 리스너(#7) 로 **poll 당 집계 후 1회 ZADD** 로 전환되기 때문이다. 아래 "결정" 섹션 참조.

#### 결정 — (A) 변형: Kafka 배치 리스너 안에서 `MetricsEventService.processBatch()` 가 DB + ZADD 를 한 트랜잭션에서 수행

**트리거가 "이벤트 단건" 이 아니라 "배치 리스너 poll() 주기"(#1 참조) 로 확정되면서, 위 세 옵션의 선택 축 자체가 바뀌었다.** "이벤트 1건당 ZSET 갱신을 어디에 둘지" 에서 "poll 당 집계를 어디에 둘지" 로 질문이 달라졌기 때문이다. 배치 리스너 맥락에서 재평가한 결과 **(A) 변형** 이 가장 단순하고 자연스럽다.

**최종 구현 형태:**

```java
// CatalogEventConsumer
@KafkaListener(topics = "catalog-events", containerFactory = "batchListenerFactory")
public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
    Map<Long, MetricDelta> perProduct = aggregate(records);   // 상품별 압축
    metricsEventService.processBatch(perProduct);             // DB + ZADD 한 트랜잭션
    ack.acknowledge();
}

// MetricsEventService
@Transactional
public void processBatch(Map<Long, MetricDelta> perProduct) {
    for (var entry : perProduct.entrySet()) {
        Long productId = entry.getKey();
        MetricDelta delta = entry.getValue();

        eventHandledRepository.saveAllIgnoreDuplicates(delta.eventIds());           // 멱등성
        productMetricsRepository.upsertIncrements(productId, delta);                // Native UPSERT (#8-6)
        ProductMetricsSnapshot metrics = productMetricsRepository.snapshotToday(productId);
        double score = rankingScoreCalculator.calculate(metrics);
        rankingWriter.upsertScore(RankingKey.daily(LocalDate.now(KST)), productId, score);
    }
}
```

**이유:**
1. **배치 단위로 트랜잭션 범위가 자연 압축된다** — 수~수백 건 이벤트를 1 트랜잭션에서 처리하므로, 원래 (A) 의 단점이던 "매 이벤트마다 Redis 왕복으로 TX 롤백 위험 누적" 이 해소된다. poll 당 Redis 왕복은 상품 수(중복 제거된 수) 만큼만 발생.
2. **Spring Event `@TransactionalEventListener(AFTER_COMMIT)` 경로 불필요** — (C) 의 장점이던 "DB/Redis 격리" 는 배치 안에서는 오히려 과잉 복잡도다. 배치가 실패하면 Kafka 가 재전송하고 `event_handled` 멱등성이 재처리를 보호한다.
3. **"재조회 후 엎어치기" 철학 유지** — `processBatch` 내부에서 UPSERT 직후 같은 트랜잭션의 읽기 일관성으로 매트릭을 재조회해 점수를 산출하므로, (B) 의 "TX 커밋 후 재조회" 오버헤드도 없다.
4. **Redis 장애 시 거동이 명확** — Redis 호출이 실패하면 전체 배치가 롤백되고 Kafka 가 재전송한다. 장기 Redis 장애 시엔 Consumer 가 멈춰 "silent drift" 가 발생하지 않는다. 복구 후 재처리되며 `event_handled` 가 중복을 방어한다.
5. **commerce-streamer 단일 app 내부에서 완결** — `MetricsEventService` → `RankingScoreCalculator` → `RankingWriter` 호출이 한 트랜잭션 안에서 일어난다. commerce-api 와의 결합 없음 (아키텍처 전환 공지의 read/write 자연 분리 원칙과 정합).

**의사결정 이력:**

| 단계 | 결정 | 사유 |
|------|------|------|
| 초기안 | (C) Spring Event ✅ 채택 | 기존 코드베이스의 `@TransactionalEventListener(AFTER_COMMIT)` 관례 + DB/Redis 격리 |
| 1차 전환 | (C) **폐기**, "API 요청 시점 집계" 로 대체 | ZUNIONSTORE 비선형 함정 발견 → Consumer가 ZSET 을 건드리지 않는 방향으로 전환 |
| 2차 전환 (최종) | **(A) 변형** — 배치 리스너 `processBatch()` ✅ | 멘토링 원칙 재확인 → 배치 리스너 + ZADD 엎어치기로 복귀. (C) 의 격리 장점은 배치 단위에서 불필요, (A) 의 단건 단점도 배치 단위에서 자연 해소 |

(A)/(B)/(C) 세 옵션의 원 비교 내용은 위에 의사결정 이력으로 보존되어 있다. 최종 아키텍처에서는 "poll 당 집계를 배치 핸들러 안에서 수행한다" 는 것이 결론이다.



### 3. 집계 테이블 스키마 설계 — `ProductMetrics` 구조 변경

재계산 ZADD 방식(결정 #1)을 위해서는 **"오늘 얼마나 쌓였는가"의 값이 DB에 있어야** 한다. 그런데 현재 `ProductMetrics`는 `productId` 단일 PK에 전체 누적값만 들고 있어 일간/시간 단위 값을 뽑아낼 수 없다.

#### 현재 구조의 한계

```java
// 현재 (R7)
@Entity
public class ProductMetrics {
    @Id private Long productId;    // 상품당 1행, 전체 누적
    private long viewCount;
    private long likeCount;
    private long orderCount;
}
```

→ `viewCount = 1000`만 보여서 "오늘 몇 건"을 역산할 수 없음. 일간 랭킹 재구축이 불가능.

#### 사용처 조사 결과

전체 코드베이스에서 `ProductMetrics`를 사용하는 곳:

- **commerce-streamer**: 엔티티/레포지토리/`MetricsEventService`/테스트 (내부 소비만)
- **commerce-api**: 사용처 없음 (일부 파일은 주석에서만 언급)
- **commerce-batch**: 사용처 없음

결론: **ProductMetrics는 R9 랭킹 파이프라인만을 위한 내부 집계 테이블이며, 외부 소비자가 없다.** 스키마 변경의 외부 영향이 없어 자유롭게 재설계 가능.

#### 검토한 데이터 모델 3가지

| 모델 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **이벤트 로그** | 이벤트 1건 = 1 row INSERT (`product_event_log`) | 원본 보존, 어떤 윈도우든 재집계 가능, 어뷰징 분석 가능 | 스토리지 폭증, `GROUP BY` 부하, 이벤트당 DB 쓰기 비용 |
| **일별 집계** | `(productId, date)` 복합키 | 단순, 일간 전용 | 시간 단위 랭킹 불가, 확장성 낮음 |
| **시간 단위 집계** ✅ | `(productId, bucket_hour)` 복합키 | 일간은 hour 버킷 24개 합산으로 파생, Nice-To-Have "1시간 랭킹" 커버 | 하루 상품당 최대 24행, 일간 조회 시 합산 연산 필요 |

> 멘토링에서 말한 "RDBMS가 원장" 의미는 **집계 테이블 원장**이지 이벤트 로그 원장이 아니다. Kafka 자체가 이미 이벤트 로그 역할을 하므로 DB에 중복 저장할 이유가 약하다. 이벤트 로그 방식은 과제 스코프 초과.

#### 최종 결정 — 시간 단위 집계 + 기존 스키마 대체

```sql
CREATE TABLE product_metrics (
    product_id     BIGINT        NOT NULL,
    bucket_hour    DATETIME      NOT NULL,      -- 시간 단위로 절삭된 DATETIME (분/초 = 0)
    view_count     BIGINT        NOT NULL DEFAULT 0,
    like_count     BIGINT        NOT NULL DEFAULT 0,
    order_count    BIGINT        NOT NULL DEFAULT 0,
    order_amount   DECIMAL(19,2) NOT NULL DEFAULT 0,  -- 주문 금액 합계 (가중치 #4에서 결정)
    updated_at     DATETIME      NOT NULL,
    PRIMARY KEY (product_id, bucket_hour)
);
```

```java
@Entity
@Table(name = "product_metrics")
@IdClass(ProductMetricsId.class)
public class ProductMetrics {
    @Id @Column(name = "product_id") private Long productId;
    @Id @Column(name = "bucket_hour") private LocalDateTime bucketHour;  // HOUR 절삭
    // ...
}
```

`bucket_hour` 생성 규칙:

```java
LocalDateTime bucket = LocalDateTime.now(KST).truncatedTo(ChronoUnit.HOURS);
// 2026-04-09 14:37:22 → 2026-04-09 14:00:00
// 14:00~14:59 사이 모든 이벤트가 같은 row에 UPSERT
```

#### 왜 "날짜 + 시간"을 한 컬럼(`DATETIME`)으로?

- **시간 범위 쿼리가 자연스러움** — `WHERE bucket_hour >= NOW() - INTERVAL 3 HOUR`
- **자정 경계 처리가 자동** — `LocalDateTime`이 날짜 전환을 스스로 처리
- **인덱스 효율** — 단일 컬럼 범위 스캔
- **Java 타입 매핑 단순** — `LocalDateTime` 하나

#### 랭킹 조회 방식 (ZSET 키 설계) — 아키텍처 전환 반영

- **일간 랭킹**: `ranking:all:{yyyyMMdd}` — **API 요청 시점에 DB 집계 → Java 점수 계산 → ZADD로 생성**. 30초 TTL 캐시 역할.
- **시간 단위 랭킹** (Nice-To-Have): `ranking:1h:{yyyyMMddHH}` — 동일 경로로 생성. `bucket_hour`의 범위 조건만 하루 → 한 시간으로 좁힘.

```java
// 일간 랭킹 생성 (API 요청 시)
List<ProductDailyAggregate> aggregates = aggregateRepository.aggregateByDate(date);
// SELECT product_id, SUM(view_count), SUM(like_count), SUM(order_amount)
// FROM product_metrics
// WHERE bucket_hour >= date_start AND bucket_hour < date_end
// GROUP BY product_id

Map<Long, Double> scores = aggregates.stream()
    .collect(toMap(ProductDailyAggregate::productId, scoreCalculator::calculate));

rankingRepository.replaceScores("ranking:all:" + date, scores, Duration.ofSeconds(30));
```

> **⚠️ `ZUNIONSTORE`는 사용하지 않는다.** hour bucket에 미리 점수를 저장한 뒤 24개를 ZUNIONSTORE SUM 하는 방식은 `Σ log1p(x) ≠ log1p(Σx)` 때문에 "꾸준한 활동"을 과대평가하는 편향이 생긴다. 대신 DB에서 raw count를 먼저 합산하고, 그 위에 log 정규화를 한 번만 적용한다.

#### 결정 이유

1. **DB 범위 필터링용 키로서 `bucket_hour`가 최적** — 일간/시간 단위 조회 모두 `WHERE bucket_hour BETWEEN :start AND :end` 하나로 커버.
2. **과제 Nice-To-Have "1시간 랭킹"과 Must-Have "일간 랭킹"이 동일 경로** — `RankingService.populateRanking(key, start, end)` 한 메서드로 추상화 가능.
3. `ProductMetrics`의 외부 소비자가 없음이 확인되어 스키마 대체의 리스크가 commerce-streamer 내부로 한정된다.
4. 하루 상품당 최대 24행 → 상품 10만 × 2일 보관 ≈ 480만 row. MySQL `(bucket_hour, product_id)` 인덱스 범위 스캔으로 감당 가능.
5. R10(주간/월간 집계)에서도 `bucket_hour`를 상위 윈도우로 합산할 수 있어 구조가 재활용된다.
6. 이벤트 로그 방식 대비 스토리지/집계 비용이 훨씬 낮다. 멘토링의 "RDBMS 원장 = 집계 테이블" 해석과 정합.

#### 영향 범위

- `ProductMetrics` 엔티티: `bucket_hour` 필드 및 복합키 추가, `order_amount` 필드 추가
- `ProductMetricsId` (`@IdClass`) 신설
- `ProductMetricsJpaRepository` / `ProductMetricsRepositoryImpl`: `findById(ProductMetricsId)` 기반으로 수정
- `MetricsEventService.getOrCreateMetrics(productId)` → `getOrCreateMetrics(productId, bucket)` 로 변경
- 관련 테스트 3종(`ProductMetricsTest`, `MetricsEventServiceTest`, `MetricsEventServiceIntegrationTest`) 수정

#### 기존 R7 데이터 마이그레이션

`ProductMetrics` 는 commerce-streamer 내부 집계 테이블이며 외부 소비자가 없다(사용처 조사 결과). 따라서 **기존 R7 누적 데이터는 폐기** 하고 R9 스키마로 재생성한다. 구체적으로:

- 로컬/테스트 환경은 `ddl-auto=create-drop` 또는 Testcontainers 재생성으로 자연 처리
- 운영 배포 시에는 `DROP TABLE product_metrics; CREATE TABLE product_metrics (...);` 수동 DDL 1회. R7 누적값은 **버려도 무방** (원본 이벤트가 Kafka 에 남아 있으므로 필요 시 재처리 가능하지만 실무상 불필요)
- 이벤트 재처리 경로는 별도 운영 결정 사항이며, 본 문서 스코프 밖

과거 데이터 보존이 필요하다면 `ALTER TABLE` 기반 in-place 마이그레이션도 가능하나 복합키 전환이라 별도 재적재가 단순하다. 과제 스코프에서는 **drop & recreate** 로 확정.

---

### 4. 가중치 / Score 산정 방식

집계 테이블(#3)에 쌓인 `viewCount / likeCount / orderAmount`를 어떻게 단일 점수로 환산할 것인가. 네 가지 갈림길(A: 주문 점수 / B: 가중치 설정 / C: 좋아요 취소 / D: 정규화)을 각각 검토했다.

#### 4-1. 주문 점수 산정 — (A-3) log 금액 기반 채택

| 옵션 | 공식 | 핵심 특징 |
|------|------|-----------|
| (A-1) 건수 | `0.6 × orderCount` | 공정하지만 매출 기여 무시 |
| (A-2) 금액 원본 | `0.6 × orderAmount` | 스케일 폭주로 view/like 의미 상실 |
| **(A-3) log 금액** ✅ | `0.6 × log1p(orderAmount)` | 스케일 압축 + 매출 반영 + 가중치 의미 유지 |

**결정 이유:**
1. 과제 명세의 `price*amount` + "log 적용 가능" 힌트를 그대로 반영
2. 금액 원본은 주문 항목이 수만~수십만이 되어 view/like(0~수십)를 압도 → 가중치가 무의미해짐
3. log 압축은 실무 패턴(Hacker News, Reddit 등)이며 고가/저가 상품의 공정성 확보
4. `Math.log1p(0) = 0`이므로 경계값도 안전

#### 4-2. 가중치 설정 방식 — (B-2) `@ConfigurationProperties` + YAML 채택

| 옵션 | 설명 | 장단점 |
|------|------|--------|
| (B-1) 하드코딩 상수 | `static final double W_VIEW = 0.1` | 최고 단순하나 프로젝트 CLAUDE.md의 "환경 설정값 하드코딩 금지" 규칙과 충돌 |
| **(B-2) YAML + `@ConfigurationProperties`** ✅ | `ranking.weights.*` | 기존 Spring 패턴, 환경별 분리, 테스트 용이 |
| (B-3) DB 기반 동적 조정 | `ranking_weight` 테이블 | 런타임 변경 가능하나 과제 Nice-To-Have "실시간 Weight 조절"에 해당 |

```yaml
ranking:
  weights:
    view: 0.1
    like: 0.2
    order: 0.7
```

```java
@ConfigurationProperties(prefix = "ranking.weights")
public record RankingWeights(double view, double like, double order) {}
```

**결정 이유:**
1. 프로젝트 CLAUDE.md의 "환경 설정값 하드코딩 금지" 규칙 준수
2. 기존 Spring Boot 관례와 일치 — 코드베이스 일관성 유지
3. 재배포만으로 가중치 튜닝 가능 — 초기 운영/튜닝에 충분
4. `record` 기반이라 타입 안전성 + 불변성 + 테스트 용이성 확보
5. 환경별 분리 가능 (`application-dev.yml`, `application-prod.yml`)
6. 런타임 변경(B-3)은 과제 Nice-To-Have로 별도 분리 — 여력 있을 때 확장

#### 4-3. 좋아요 취소(및 부정 이벤트) 처리 — 배치 리스너에서 현재 bucket 차감 + 드리프트 수용

**멘토링 원칙:**
> 랭킹 시스템의 목표는 '현재 인기 있는 순서나 트렌드'를 보여주는 것이지, 데이터의 수치를 한 치의 오차도 없이 정확하게 보여주는 것이 아니다. 취소·환불·결제 실패 같은 부정적 이벤트에 실시간으로 마이너스 가중치를 두어 즉각 반영하기보다, 일부 유실을 허용하며 주기적 보정 배치로 원장(매트릭)을 재조회해 스코어를 새롭게 계산한 뒤 한 번에 덮어씌우는 방식이 합리적이다.

**선택지:**

| 옵션 | 내용 | 평가 |
|------|------|------|
| **(C-1) 현재 bucket 차감** ✅ | 배치 리스너가 현재 시각의 bucket 에서 `like_count -= 1`, Native UPSERT 의 `GREATEST` 가드로 음수 방지. 매트릭 재조회 후 점수 재계산 → ZADD 엎어치기 | 버킷 시점 불일치로 소량 드리프트 발생하나 멘토링 원칙(드리프트 허용)과 정합 |
| (C-2) 원래 bucket 차감 | 취소 이벤트에 원본 좋아요 시각을 실어 보내 해당 bucket 을 찾아가 차감 | 이벤트 스키마 확장 필요, 과제 스코프 초과 |
| (C-3) DB만 차감, ZSET 갱신 skip | 1차 전환안에서 채택 | Consumer 가 ZSET 을 안 건드리는 전제 위에 성립 — 2차 전환으로 전제 소멸 |
| (C-4) 완전 무시 | 이벤트 skip (DB 도 미갱신) | ProductMetrics 드리프트 확산, 원장 복원 어려움 |

**최종 결정 — (C-1)**

Consumer 배치 리스너가 좋아요/취소 이벤트를 한 번에 압축 처리한다. `liked=false` 인 경우 현재 bucket 의 `like_count` 에 `-1` 을 적용하며, `upsertIncrements` 의 `GREATEST(...)` 가드가 INSERT/UPDATE 양 분기에서 음수를 방지한다(#8-6). 매트릭 재조회 후 새 점수가 산출되어 ZADD 로 엎어치기 되므로, ZSET 은 현재 DB 상태를 즉시 반영한다.

**알려진 드리프트 — 버킷 시점 불일치:**

버킷 단위 집계 구조 때문에 다음 시나리오에서 일간 `SUM(like_count)` 이 실제 순증가량과 다를 수 있다.

```
14:10  userA 좋아요   → 14시 bucket.like_count = 1
15:30  userA 취소     → 15시 bucket.like_count = -1 → GREATEST → 0
SUM(today)           = 1 + 0 = 1    (실제 순증가는 0)
```

취소 이벤트가 **발생 시각의 bucket** 에서 차감되고 **원본 좋아요의 bucket** 은 그대로 남기 때문이다. 완전한 정확성을 원한다면 (C-2) 처럼 이벤트 스키마 확장이 필요하지만 과제 스코프 밖이다.

**드리프트 수용 근거:**

1. **멘토링 원칙과 정합** — "랭킹은 트렌드, 완벽한 정합성 불필요. 드리프트는 주기적 배치로 보정"
2. **영향 제한적** — like 가중치 0.2 + log1p 정규화로 단일 취소 누락의 점수 영향이 매우 작다
3. **장기 보정 경로 존재** — #6 `RankingCorrectionBatch` (설계만 보존) 가 DB 원장 기반 재계산으로 드리프트를 0 으로 되돌릴 수 있다
4. **자연 감쇠** — 일간 키는 자정에 초기화되므로 드리프트가 무한 누적되지 않는다
5. **배치 압축이 드리프트의 상당 부분을 흡수** — 같은 user 가 같은 poll 안에서 like → unlike 를 누르면 `likeDelta = 0` 으로 합산되어 드리프트 자체가 발생하지 않는다. 드리프트는 **poll 경계를 넘은** 취소에서만 나타나며 실무상 드물다

**주문 취소/환불도 동일 정책:**

`order-events` 에 취소/환불 이벤트가 추가되면 같은 원칙 — "현재 bucket 에서 차감, 드리프트 허용, 장기 보정은 #6 배치" — 를 적용한다. 본 아키텍처의 일관 정책이다.

**구현 윤곽 (MetricsEventService.processBatch 내부, 8-6 Native UPSERT 기반):**

```java
// 배치 리스너 aggregate 단계:
//   delta.likeDelta = (배치 내 liked=true 수) - (배치 내 liked=false 수)
// processBatch 는 이 delta 를 그대로 upsertIncrements 로 전달
// upsertIncrements 의 INSERT/UPDATE 양 분기 GREATEST 가드가 음수 저장 방지
```

#### 의사결정 이력 — 1차 전환안 (C-3) "DB 만 차감 + Spring Event 생략" 은 폐기됨

1차 전환에서는 Consumer 가 ZSET 을 전혀 쓰지 않았기 때문에 "좋아요 취소 시 DB 만 차감하고 ZSET 갱신은 API 요청 시점에 자동 보정" 이라는 경로가 성립했다. 2차 전환으로 Consumer 가 ZSET 에 직접 쓰게 되면서 이 경로는 더 이상 적용되지 않고, (C-1) 의 "현재 bucket 차감 + 드리프트 수용" 으로 교체된다.

#### 4-4. 점수 스케일 정규화 — 전체 log 정규화 채택

**모든 지표(view/like/order)에 `log1p` 적용**하여 스케일을 통일한다. **정규화는 "일간 총합을 한 번만" 적용한다**.

```java
score = w.view()  * log1p(totalView)      // SUM(view_count) over date
      + w.like()  * log1p(totalLike)      // SUM(like_count) over date
      + w.order() * log1p(totalOrderAmount) // SUM(order_amount) over date
```

**채택 배경 1 — 부분 log 정규화의 한계:**

처음에는 주문에만 log를 적용하는 방식을 고려했으나, 시뮬레이션 결과 **조회 수가 많은 상품이 오히려 유리해지는 편향**이 발견됐다.

시뮬레이션 예시 (view 5000 / like 100 / order 1만원 vs view 100 / like 5 / order 100만원):
- 부분 정규화(view는 원본, order만 log): 조회 많은 상품이 항상 1위
- 전체 정규화(모두 log): 매출 기여도 큰 상품이 1위 — 더 "인기 상품" 직관에 부합

**채택 배경 2 — ZUNIONSTORE 비선형 함정 발견 (아키텍처 전환의 계기):**

"hour bucket에 점수 저장 → ZUNIONSTORE SUM으로 일간 파생" 방식을 검토하던 중 수학적 문제를 발견했다.

```
hour1: view 10 → bucket_score = 0.1 * log1p(10)  ≈ 0.240
hour2: view 10 → bucket_score = 0.1 * log1p(10)  ≈ 0.240
ZUNIONSTORE SUM                                   ≈ 0.480

하지만 "올바른" 일간 점수:
0.1 * log1p(20)                                   ≈ 0.304
```

두 값이 다르다. 원인은 log의 비선형성: **`log1p(A) + log1p(B) ≠ log1p(A + B)`**.

### 의미: 두 가지 "일간 점수" 해석

| 방식 | 의미 | 편향 |
|------|------|------|
| `Σ log1p(hour counts)` (ZUNIONSTORE SUM) | "시간대별 활동의 로그 점수를 합산" | **꾸준한 활동 상품** 과대평가 (24시간에 10씩 vs 1시간에 240) |
| **`log1p(Σ hour counts)`** (DB 집계) | "하루 전체 활동량을 한 번에 로그" | **총량 기반 공정 평가** ⭐ |

후자가 과제 명세(`Score = price*amount`)의 "단일 window 계산" 의도와 정합한다. 이 사실이 **DB 집계 + Java 계산 아키텍처 전환의 결정적 근거**가 되었다.

**전체 정규화의 효과:**
- 모든 지표가 같은 스케일(한 자릿수 ~ 십 자릿수)로 압축
- 가중치 `0.1 / 0.2 / 0.7`이 의도대로 작동 (각 항의 기여도가 정확히 비율대로)
- view 부풀리기 어뷰징 내성 향상
- min-max 정규화(D-적용)가 가진 "전역 max 필요 → 재계산 비용 폭증" 문제 회피
- DB 집계 + Java 계산 경로에서 **단일 지점에서만 log1p 적용** → 수학적 정확성 보장

#### 4-5. 최종 점수 공식

입력은 **집계 결과 VO (`ProductDailyAggregate`)**. `ProductMetrics` 엔티티를 직접 받지 않음으로써 바운디드 컨텍스트 경계를 유지한다.

```java
// commerce-api/domain/ranking/ProductDailyAggregate.java
public record ProductDailyAggregate(
    Long productId,
    long totalView,
    long totalLike,
    BigDecimal totalOrderAmount
) {}

// commerce-api/domain/ranking/RankingScoreCalculator.java
public class RankingScoreCalculator {
    private final RankingWeights weights;

    public double calculate(ProductDailyAggregate agg) {
        double amount = agg.totalOrderAmount() == null
                ? 0.0
                : agg.totalOrderAmount().doubleValue();
        return weights.view()  * Math.log1p(agg.totalView())
             + weights.like()  * Math.log1p(agg.totalLike())
             + weights.order() * Math.log1p(amount);
    }
}

// null-safety: Native Query 의 `SUM(order_amount)` 는 매칭 row 가 없을 때 NULL 을 반환할 수 있다.
// `ProductDailyAggregate.totalOrderAmount` 는 `BigDecimal` 이므로 null 이 들어올 가능성이
// 이론적으로 존재하며, CLAUDE.md 의 "null-safety 강제" 규칙에 따라 0 으로 clamp 한다.
```

**가중치 초기값:** `view=0.1, like=0.2, order=0.7` (합계 1.0)

과제 힌트는 `view=0.1 / like=0.2 / order=0.6` (합계 0.9) 이나, 본 설계에서는 **합계를 정확히 1.0 으로 맞추기 위해 `order` 만 0.1 상향** 했다. 이유는 다음과 같다.

1. **정규화 해석의 편의** — log1p 정규화 이후 모든 지표가 유사 스케일에 놓이므로, 가중치 합이 1 이면 최종 점수가 "각 지표 기여도의 가중 평균" 으로 직접 해석된다. 0.9 라는 비정형 합계는 해석 편의성이 낮다.
2. **"주문 1건 > 좋아요 3건" 체크리스트 검증 친화** — `order` 에 약간 더 힘을 싣는 방향이 과제 Checklist 의 "가중치 적용이 의도대로 반영되는지 (주문 1건 > 좋아요 3건)" 검증과 정합한다.
3. **튜닝 가능성 보존** — 초기값일 뿐이며, `@ConfigurationProperties` (#4-2) 로 외부화되어 YAML 에서 손쉽게 과제 힌트 값(0.1/0.2/0.6) 으로 되돌리거나 다른 조합으로 바꿀 수 있다.

**튜닝 방향성:**
- 서비스 특성에 따라 YAML에서 조정 가능
- 신상품 노출이 중요해지면 view 비중 상향 (예: 0.2/0.3/0.5)
- 구매 전환이 핵심 KPI면 order 비중 상향 (예: 0.05/0.15/0.8)

---

### 5. Ranking API — 도메인 구조 · 데이터 흐름 · 응답 Aggregation

아키텍처 전환 이후 Ranking API는 **"읽기 경로가 곧 파이프라인의 핵심"** 이 된다. 쓰기는 Consumer가 DB에만 하므로 단순하고, 복잡도는 전부 읽기 쪽에 집중된다.

#### 5-1. 도메인 분리 — commerce-streamer(쓰기) / commerce-api(읽기) 자연 분리

2차 전환 이후 쓰기와 읽기가 **서로 다른 app 에서** 완전히 분리된다.

```
apps/commerce-streamer/          # 쓰기 전용
  └── domain/ranking/
       ├── RankingScoreCalculator.java   # log1p 정규화 + 가중치 (4-5)
       ├── RankingWeights.java           # @ConfigurationProperties (4-2)
       ├── RankingWriter.java            # 쓰기 인터페이스 (ZADD + EXPIRE)
       └── RankingKey.java               # 키 포맷 `ranking:all:{yyyyMMdd}`
  └── infrastructure/ranking/
       └── RedisRankingWriter.java       # masterRedisTemplate 기반 구현
  └── (기존) MetricsEventService         # processBatch(perProductDeltas) — Native UPSERT + 점수 계산 + RankingWriter 호출
  └── scheduler/
       └── RankingCarryOverScheduler.java  # 매일 23:50 Carry-Over (8-4)

apps/commerce-api/                # 읽기 전용
  └── domain/ranking/
       ├── RankingRepository.java        # 읽기 인터페이스 (ZREVRANGE/ZREVRANK)
       ├── RankingEntry.java             # VO — (productId, rank, score)
       └── RankingKey.java               # 포맷 동일, 양쪽 테스트로 회귀 방지
  └── infrastructure/ranking/
       └── RedisRankingRepository.java   # RedisTemplate 기반 구현
  └── application/ranking/
       ├── RankingFacade.java            # ZSET 조회 + 상품 정보 조합
       └── RankingItemInfo.java          # 응답 VO
  └── interfaces/api/ranking/
       ├── RankingV1Controller.java      # GET /api/v1/rankings
       └── dto/RankingV1Dto.java
```

**핵심 관찰:** commerce-api 는 점수 공식(`RankingScoreCalculator`) 을 알 필요가 없다. 이미 streamer 가 계산해서 ZSET 에 저장해뒀기 때문. 공식/가중치 설정 중복이 원천 발생하지 않는다. 유일한 "공유" 는 `RankingKey` 포맷 문자열 하나이며, 각 app 에 독립 상수로 두고 양쪽 테스트가 같은 리터럴을 고정한다.

> **의사결정 이력:** 1차 전환안에서는 "commerce-api 내부에 ranking 도메인 신설, API 요청 시점에 DB 집계 → 점수 계산 → ZSET 캐시 적재" 구조였다. 이로 인해 `RankingScoreCalculator` / `RankingWeights` / `ProductAggregateRepository` 가 전부 commerce-api 안에 존재했고, 바운디드 컨텍스트 경계를 넘기 위해 Native SQL + Projection (#5-2, #8-5) 이라는 별도 패턴이 필요했다. 2차 전환으로 쓰기 주체가 streamer 로 복귀하면서 이 전체 구조가 단순화되었다.

#### 5-2. 바운디드 컨텍스트 경계 — streamer 내부 완결

1차 전환에서는 commerce-api 가 `product_metrics` 테이블을 Native SQL 로 직접 집계 조회해야 했고, 이 때문에 "엔티티 import 없이 스키마만 공유" 하는 결합이 존재했다 (Native Query + Projection, 4 옵션 비교).

2차 전환 이후에는 **commerce-api 가 `product_metrics` 를 전혀 조회하지 않는다**. 점수 계산과 집계 모두 commerce-streamer 내부에서 일어나며, commerce-api 는 오직 `ranking:all:{yyyyMMdd}` ZSET 만 읽는다. 즉 **바운디드 컨텍스트 경계가 app 수준에서 자연 분리** 된다.

| 항목 | 1차 전환안 | 2차 전환 (최종) |
|------|-----------|-----------------|
| `product_metrics` 접근 주체 | commerce-api + commerce-streamer | **commerce-streamer 단독** |
| 스키마 문자열 결합 | commerce-api Native SQL | 없음 (streamer 내부 JPA Entity 사용) |
| 바운디드 컨텍스트 경계 | DB 스키마 공유로 결합 | **app 경계로 자연 분리** ⭐ |
| 별도 방어책 | Testcontainers 회귀 테스트 (#8-5) | 불필요 (streamer 내부 일관성으로 해결) |

streamer 내부에서는 기존 JPA Entity(`ProductMetrics` + `@IdClass ProductMetricsId`) 를 그대로 사용하거나, 배치 집계용으로 `ProductMetricsRepository.snapshotToday(productId)` 같은 전용 조회 메서드를 추가한다. Native SQL 은 8-6 의 `upsertIncrements` 에만 쓰이며, 그 외 조회는 JPA Entity/Repository 레벨에서 해결된다.

> **의사결정 이력:** 1차 전환안의 "commerce-api Native Query + Projection" 은 당시의 DB 집계 필요성에 의한 결정이었고, 이에 대한 보강(#8-5 회귀 방어 테스트) 도 문서에 남아 있다. 2차 전환으로 필요성 자체가 사라지면서 #8-5 의 논의도 소멸한다.

#### 5-3. RankingRepository (commerce-api, 읽기 전용)

2차 전환 이후 commerce-api 의 ranking 경로는 **단순 Redis 조회**만 한다. 재계산/캐시 관리 책임이 없으므로 `RankingService` 라는 도메인 서비스 레이어도 따로 둘 필요가 없다. `RankingFacade` 가 `RankingRepository` 를 직접 호출하는 구조로 단순화.

```java
// commerce-api/domain/ranking/RankingRepository.java
public interface RankingRepository {
    List<RankingEntry> getTopN(String key, int pageOneBased, int size);
    Long getRank(String key, Long productId);  // 1-based, null if absent
}

// commerce-api/infrastructure/ranking/RedisRankingRepository.java
@Repository
@RequiredArgsConstructor
public class RedisRankingRepository implements RankingRepository {
    private final StringRedisTemplate masterRedisTemplate;

    @Override
    public List<RankingEntry> getTopN(String key, int pageOneBased, int size) {
        long start = (long) (pageOneBased - 1) * size;
        long end   = start + size - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples =
            masterRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null || tuples.isEmpty()) return List.of();

        List<RankingEntry> result = new ArrayList<>(tuples.size());
        int index = 0;
        for (var t : tuples) {
            long rank = start + index + 1;     // 1-based
            result.add(new RankingEntry(Long.parseLong(t.getValue()), rank, t.getScore()));
            index++;
        }
        return result;
    }

    @Override
    public Long getRank(String key, Long productId) {
        Long zeroBased = masterRedisTemplate.opsForZSet().reverseRank(key, productId.toString());
        return zeroBased == null ? null : zeroBased + 1;  // 1-based, 없으면 null
    }
}
```

**포인트:**
- **`exists` / `replaceScores` 폐기** — 2차 전환 후 API 경로가 재계산 트리거를 갖지 않으므로 캐시 hit/miss 개념이 사라짐. 단순 `ZREVRANGE` / `ZREVRANK` 만 필요.
- **`masterRedisTemplate` 선택 근거** — 배치 리스너가 방금 ZADD 한 값을 즉시 ZREVRANGE 해야 하는 패턴. Replica 복제 지연 회피. (#8-7 의 기존 논의 유지)
- **1-based 변환 일관** — `reverseRank` 는 0-based 반환이라 `+1` 하고 null 보존. `getTopN` 의 rank 는 `(page-1)*size + index + 1` 공식으로 #8-8 과 정렬.
- **SingleFlight / stampede 방지 코드 없음** — Consumer 가 상시 ZADD 를 채우므로 miss → 동시 재계산 시나리오 자체가 없음.

#### 5-4. RankingWriter (commerce-streamer, 쓰기 전용)

쓰기 인터페이스는 읽기와 대칭으로 commerce-streamer 쪽에 둔다. `upsertScore` 단건 호출이 유일한 연산.

```java
// commerce-streamer/domain/ranking/RankingWriter.java
public interface RankingWriter {
    void upsertScore(String key, Long productId, double score);
}

// commerce-streamer/infrastructure/ranking/RedisRankingWriter.java
@Repository
@RequiredArgsConstructor
public class RedisRankingWriter implements RankingWriter {
    private final StringRedisTemplate masterRedisTemplate;
    private final RankingCacheProperties cacheProperties;

    @Override
    public void upsertScore(String key, Long productId, double score) {
        masterRedisTemplate.opsForZSet().add(key, productId.toString(), score);
        masterRedisTemplate.expire(key, cacheProperties.retention());  // 2일 retention (5-7)
    }
}
```

**포인트:**
- **`replaceScores` / `DEL` / `RENAME` 없음** — #8-3 결정대로 단건 ZADD 의 원자성으로 충분
- **매 호출에 `EXPIRE` 를 함께 설정** — Key 가 date-scoped 라 "오늘 키의 마지막 이벤트 + 2일" 에 자연 만료. NX 옵션이 없어도 의미 왜곡 없음
- **Carry-Over 스케줄러(#8-4) 와 공유** — Carry-Over 도 같은 `upsertScore` 를 호출

> **의사결정 이력:** 1차 전환안에서는 `RankingService` 가 commerce-api 안에 있으며 `ensureDailyRanking(date, key)` 내부에서 "exists 체크 → 캐시 miss 시 DB 집계 → 점수 계산 → replaceScores" 전체 파이프라인을 돌렸다. 이 서비스 레이어는 2차 전환으로 전부 사라지고, commerce-streamer 의 `MetricsEventService.processBatch()` 가 쓰기 책임을, commerce-api 의 `RankingFacade` 가 읽기 책임을 각각 담당한다.

#### 5-5. 응답 Aggregation — `RankingFacade` = ZREVRANGE + `findVisibleByIds`

ZSET 에서 얻은 `RankingEntry` 목록에 상품 정보를 붙여 응답을 조립한다. 2차 전환 이후에는 구조가 단순해져서 `RankingFacade` 가 곧 전체 흐름이다.

```java
// commerce-api/application/ranking/RankingFacade.java
@Component
@RequiredArgsConstructor
public class RankingFacade {
    private final RankingRepository rankingRepository;
    private final ProductFacade productFacade;

    public List<RankingItemInfo> getDailyRanking(LocalDate date, int pageOneBased, int size) {
        String key = RankingKey.daily(date);
        List<RankingEntry> entries = rankingRepository.getTopN(key, pageOneBased, size);
        if (entries.isEmpty()) return List.of();

        List<Long> productIds = entries.stream().map(RankingEntry::productId).toList();

        // 8-2: 캐시 우회, displayYn='Y' AND deletedAt IS NULL 필터링, Map 반환
        Map<Long, ProductInfo> products = productFacade.findVisibleByIds(productIds);

        return entries.stream()
            .map(e -> {
                ProductInfo p = products.get(e.productId());
                return p == null ? null : RankingItemInfo.of(e, p);
            })
            .filter(Objects::nonNull)  // 삭제/숨김 상품 제외 — 응답이 size 보다 작을 수 있음 (8-2)
            .toList();
    }

    public Long getDailyRank(Long productId) {
        return rankingRepository.getRank(RankingKey.daily(LocalDate.now(KST)), productId);  // null if absent
    }
}
```

**포인트:**
- **`RankingService` 호출 없음** — 2차 전환으로 해당 서비스 레이어가 사라졌다(5-3). `RankingRepository` 직접 호출.
- **`findVisibleByIds` 채택** — 8-2 결정대로 `ProductFacade.findVisibleByIds(List<Long>)` 가 `deletedAt IS NULL AND displayYn='Y'` 필터를 적용하고 캐시를 우회해 DB 직접 조회. Map 으로 반환되며, 존재하지 않는 상품은 Map 에 없음. `filter(Objects::nonNull)` 로 쳐내면서 over-fetch 없이 축소 응답 허용.
- **`getDailyRank`** — 상품 상세 경로(5-6, 8-10) 에서 호출되는 non-trigger 조회. Consumer 가 ZSET 을 상시 채우므로 단순 `ZREVRANK` 결과를 그대로 반환하면 되며, 순위권 밖이면 null.

> **의사결정 이력:** 1차 전환안에서는 `productFacade.findByIdsWithCache` (캐시 우선 + DB fallback) 가 A-3 옵션으로 채택되었으나, 8-2 에서 "랭킹 응답 규모가 작고 캐시 우회가 더 단순" 이라는 논리로 `findVisibleByIds` 로 대체되었다. 2차 전환은 이 8-2 결정을 그대로 유지한다.

#### 5-6. 상품 상세의 순위 조회 — Controller 레벨 합성

상품 상세 API(`GET /api/v1/products/{id}`) 응답에 현재 상품의 일간 순위를 포함해야 한다 (체크리스트).

**옵션:**
- (1) `ProductFacade` 가 `RankingFacade` 를 호출 → Product 도메인이 Ranking 에 의존
- (2) Controller 가 두 Facade 의 결과를 합성 ✅

**채택: (2) Controller 레벨 합성** — 도메인 간 단방향 의존이 깨지지 않도록.

```java
@GetMapping("/api/v1/products/{productId}")
public ApiResponse<ProductDetailResponse> detail(@PathVariable Long productId) {
    ProductDetailInfo detail = productFacade.getDetail(productId);
    Long dailyRank = rankingFacade.getDailyRank(productId);  // null if absent
    return ApiResponse.success(ProductDetailResponse.of(detail, dailyRank));
}
```

`dailyRank == null` 이면 "순위권 밖" 또는 "아직 해당 상품에 이벤트가 들어오지 않음" 을 의미. 응답 DTO 는 `Long dailyRank` nullable 로 둔다. 2차 전환으로 이 조회는 단순 `ZREVRANK` 한 번이며 어떤 부작용(캐시 재계산 등) 도 유발하지 않는다.

#### 5-7. TTL 정책 — retention 단일 (2일)

2차 전환 이후 TTL 정책은 **과제 명세 `TTL: 2Day` 를 retention 성격으로 해석하여 단일 값으로 단순화** 된다. 초기의 "오늘 30초 / 과거 2일" 날짜별 분기는 "API 요청 시점 집계" 전제에 딸린 설계였으며, 배치 리스너 + ZADD 엎어치기 구조에서는 더 이상 의미가 없다.

**핵심 관찰 — TTL 의 두 가지 성격 구분**

| 성격 | 목적 | 주기 | 멘토링 발언 | 과제 명세 |
|------|------|------|-------------|-----------|
| **캐시 TTL** | "stale 방지 → 만료되면 재집계 트리거" | 짧음 (수십 초) | "Consumer 가 상시 엎어치니 불필요" | 언급 없음 |
| **retention TTL** | "오래된 키 자동 청소 (housekeeping)" | 김 (수 일) | 언급 없음 | ✅ `TTL: 2Day` |

멘토링의 "ZSET 에 TTL 불필요" 는 **캐시 TTL** 에 대한 이야기이고, 과제 명세의 `TTL: 2Day` 는 **retention TTL** 이다. 두 성격은 서로 다른 목적이라 충돌하지 않으며, 본 아키텍처에서는 **retention TTL 만** 존재한다.

**최종 결정:**

| 항목 | 값 |
|------|-----|
| TTL 타입 | retention |
| 값 | **모든 날짜 2일 고정** |
| 설정 시점 | **최초 키 생성 시 1회만** |
| 이후 ZADD | TTL 을 건드리지 않음 (Redis ZADD 는 기존 TTL 보존) |
| 과거 날짜 조회 | 별도 정책 없음 — 이미 만료되었거나, 아직 살아 있으면 그대로 `ZREVRANGE` |

**동작 시나리오:**

```
D-day 00:00:00  이벤트 유입 시작
    → 첫 배치 처리 시 ZADD + EXPIRE 2d 설정 → 만료 예정 D+2 00:00:00

D-day 00:00:01 ~ D-day 23:59:59
    → 배치 리스너가 상시 ZADD 엎어치기
    → TTL 유지 (만료 시점은 D+2 00:00 고정)

D+1 (어제 랭킹 조회 시)
    → 키 살아있음 (만료 = D+2 00:00)
    → ZREVRANGE 정상 동작

D+2 00:00:00
    → 자동 만료
    → 이후 그 날짜 조회는 빈 배열
```

**최초 EXPIRE 설정 — 두 가지 옵션:**

| 옵션 | 방법 | 비고 |
|------|------|------|
| **(가) `EXPIRE key 2d NX`** | Redis 7.0+ 옵션. 기존 TTL 이 없을 때만 설정. 매 호출에 부가해도 idempotent. | 프로젝트 Redis 버전이 7.0 이상인지 확인 필요 |
| **(나) `exists(key)` 체크 후 EXPIRE** | false 면 ZADD 후 EXPIRE 수행, 이후 호출은 ZADD 만 | Redis 버전 무관 |

본 프로젝트 Redis 버전에 따라 택일. (가) 가 원자적이고 단순하므로 우선 고려. 구현 단계에서 Redis 버전 확인 후 확정.

**YAML 외부화:**

```yaml
ranking:
  cache:
    retention: P2D         # ISO-8601 Duration. 과제 명세 "TTL: 2Day"
```

```java
@ConfigurationProperties(prefix = "ranking.cache")
public record RankingCacheProperties(Duration retention) {}
```

**테스트 환경 고려:**
E2E 테스트에서 2일은 실질적으로 영원에 가깝다. `application-test.yml` 에서 짧게 오버라이드.

```yaml
# application-test.yml
ranking:
  cache:
    retention: PT10S       # 10초 (테스트 목적 단축)
```

#### 의사결정 이력 — 1차 전환안 (폐기됨)

초기에는 "오늘 30초 / 과거 2일" **날짜별 TTL 분기** 를 채택했었다. 당시 전제는 "API 요청 시점에 DB GROUP BY 로 재집계" 였고, 이 경우 "오늘 값이 자주 바뀌므로 짧은 캐시 TTL + 갱신" 이 합리적이었다.

| 방식 | 평가 | 운명 |
|------|------|------|
| A. 날짜별 분기 (오늘 30초 / 과거 2일) | 1차 전환안에서 채택 | ❌ 2차 전환으로 폐기 |
| B. 모든 날짜 짧은 TTL | 과제 명세 "2일" 위반 | 미채택 |
| C. 모든 날짜 긴 TTL | 오늘 랭킹 stale (1차 전환 전제 하에서) | 미채택 |
| D. 이중 키 구조 | 구현 복잡 | 미채택 |
| **E. retention 단일 (2일)** ✅ | 2차 전환 최종 — 배치 리스너가 상시 엎어치므로 "오늘 stale" 문제 자체가 소멸 | **최종** |

2차 전환 이후에는 "오늘 값이 stale" 이라는 문제 **자체가 사라졌다** — 배치 리스너가 poll 주기마다 최신 매트릭으로 ZADD 엎어치기를 하기 때문이다. 따라서 단일 2일 retention 으로 충분하다. 1차 전환안에서 만들어졌던 `RankingCacheProperties(Duration todayTtl, Duration pastTtl)` 는 `Duration retention` 하나만 가진 구조로 단순화된다.

#### 5-8. 전체 데이터 흐름 요약

```
[쓰기 경로 — commerce-streamer]
  Kafka (catalog-events / order-events)
    → @KafkaListener(batch = true)
    → poll() 마다 List<ConsumerRecord> 수신 → 상품별 합산
    → MetricsEventService.processBatch(perProductDeltas) @Transactional
        → event_handled INSERT (멱등성)
        → product_metrics Native UPSERT (#8-6)
        → 매트릭 재조회 → RankingScoreCalculator.calculate()
        → RankingWriter.upsertScore(dailyKey, pid, score)  [ZADD + EXPIRE 2d]
    → ack.acknowledge()

[읽기 경로 — commerce-api]
  GET /api/v1/rankings?date=20260409&page=1&size=20
    → RankingV1Controller
      → RankingFacade.getDailyRanking(date, pageable)
        → RankingRepository.getTopN(key, page, size)  [ZREVRANGE, 재계산 트리거 없음]
      → ProductFacade.findVisibleByIds(productIds)  [#8-2]
      → 응답 조립 → JSON
```

**이력:** 초기에는 API 경로에 "캐시 miss → DB GROUP BY → `replaceScores`" 재집계 트리거가 있었고 쓰기 경로는 "ZSET 쓰기 없음" 이었다. 2차 전환 이후 쓰기 책임이 Consumer 로 복귀하면서 읽기 경로는 단순 `ZREVRANGE` 만 남았다.

---

### 6. 주기적 보정 배치 — Nice-To-Have에서도 강등

아키텍처 전환 전에는 "이벤트 → ZSET 실시간 갱신 + 취소 드리프트를 배치로 보정"이 필요했으나, 전환 후에는 **매 API 요청이 DB 원장 기반 재계산**이기 때문에 배치의 필요성이 대부분 사라졌다.

#### 배치가 여전히 의미 있는 경우

| 시나리오 | 배치의 역할 |
|----------|------------|
| 가중치(`application.yml`) 변경 | 이미 캐시된 dailyKey들을 강제 무효화/재계산 |
| Redis 장애 복구 후 warm-up | 자주 조회되는 날짜의 dailyKey를 미리 채워 첫 요청 지연 방지 |
| 트래픽이 매우 낮아 "첫 요청"이 느린 경우 | 주기적 pre-warming |

**이 세 가지는 모두 "Nice-To-Have의 Nice-To-Have"** 수준이며, 과제 스코프에서 구현하지 않아도 Must-Have 체크리스트 완주에 문제없다.

#### 배제된 원래 용도

- ~~취소 이벤트로 인한 ZSET 드리프트 보정~~ → 매 API 요청이 자동 보정 (#4-3)
- ~~가중치 변경의 점진적 전파~~ → TTL 만료 후 자동 반영 (30초 내)
- ~~정합성 최후 보루~~ → DB가 원장이고 매 조회가 DB 기반이므로 드리프트가 누적되지 않음

#### 만약 구현한다면

배치 주기, 범위, 실행 위치 등의 설계 원칙은 초기 검토에서 정리된 바 있으나(15분 주기, 변경 감지, commerce-streamer `@Scheduled`), 실제 구현은 과제 여력이 있고 명확한 운영 니즈가 있을 때만 추가한다.

---

### 7. 카프카 배치 리스너 적용 여부 (Nice-To-Have, 예정)

과제 명세의 Nice-To-Have — 이벤트 단건 처리 대신 배치 리스너로 동일 상품 이벤트를 압축(합산)하여 DB 부하 감소.

**아키텍처 전환 후의 재평가:**
- Consumer는 이제 DB 쓰기만 수행 (ZSET 쓰기 없음)
- 동일 상품에 대한 연속 이벤트를 배치로 묶어 `product_metrics` UPSERT를 합산하면 **DB UPDATE 횟수가 크게 감소**
- Redis 부하는 원래부터 없으므로 이 부분의 이득은 없음
- 순수하게 "DB 쓰기 성능 최적화" 관점에서 유효한 Nice-To-Have

구현 시 기존 `modules/kafka/KafkaConfig`의 `BATCH_LISTENER_DEFAULT` 팩토리를 사용.

---

## 8. 설계 리뷰 후 확정 사항 (2026-04-09)

#1~#7은 초기 계획 작성 과정의 고민과 방향성을 정리한 기록이다. 이후 구현 착수 전 설계 리뷰를 진행하며 몇 가지 사각지대가 드러났고, 기존 결정을 교정하거나 새 결정을 추가했다. 이 섹션은 **각 항목에 대해 "무엇이 고민이었는지 → 어떤 선택지를 놓고 비교했는지 → 왜 그렇게 결정했는지"** 가 순서대로 드러나도록 정리한 "구현 정본" 이다. 위 섹션들과 충돌 시 이 섹션을 우선한다.

---

### 8-1. 상품 상세 API 가 랭킹 캐시 재생성을 트리거하는 사이드이펙트 (2차 전환으로 소멸)

#### 고민 배경 (1차 전환 당시)

1차 전환안의 #5-6 에서는 상품 상세 응답에 순위를 포함시키기 위해 `rankingFacade.getTodayRank(productId)` 를 호출했고, 이 메서드가 내부적으로 `ensureDailyRanking()` 을 통해 **캐시 miss 시 전체 일간 DB GROUP BY 재집계** 를 트리거했다. 상품 상세 트래픽이 랭킹 API 보다 훨씬 많으므로, 인기 없는 상품 1건 조회가 전체 랭킹 집계를 재실행하는 기형적 부하 구조가 있었다.

당시 해결책은 `getRankIfCached(key, productId)` 라는 별도 메서드를 만들어 `exists` 체크 후 미존재 시 null 을 반환하고 재계산을 절대 트리거하지 않도록 하는 것이었다.

#### 2차 전환 — 문제 자체 소멸

Consumer 배치 리스너가 ZSET 을 상시 엎어치는 구조로 바뀌면서 **"캐시 재생성 트리거" 라는 개념 자체가 사라졌다**. 상품 상세는 그저 `ZREVRANK` 를 한 번 호출해 현재 순위(있으면 1-based, 없으면 null) 를 받으면 된다. 별도 `getRankIfCached` 메서드도 불필요하며, `RankingRepository.getRank(key, productId)` 하나로 정리된다.

| 항목 | 1차 전환안 | 2차 전환 (최종) |
|------|-----------|-----------------|
| 상품 상세 호출 | `getRankIfCached` (재계산 트리거 금지 버전) | `getRank` 단순 호출 |
| 재계산 트리거 우려 | 실재 → 별도 메서드로 회피 | **개념 자체 없음** |
| RankingRepository 인터페이스 | `exists` + `replaceScores` + `getRankIfCached` 분리 | `getTopN` + `getRank` 두 개 |
| 콜드 스타트 보장 | 스케줄러(20초 warm-up) + `getRankIfCached` 이중 방어 | Consumer 가 상시 채움 + 23:50 Carry-Over(#8-4) 로 자정 직후 커버 |

과제 체크리스트의 "순위에 없다면 null" 조건은 2차 전환 후에는 **"해당 productId 가 오늘 이벤트를 받지 못했거나 ZREVRANK 결과가 null"** 이라는 자연스러운 의미로 정착된다.

---

### 8-2. 삭제/숨김 상품이 응답에 섞이는 문제와 페이지 사이즈 축소

#### 고민 배경

ZSET에는 `productId` 만 저장되므로, 응답 조립 단계에서 상품 정보를 덧붙여야 한다. 이때 다음 문제가 발생한다.

1. **상품이 soft-delete 되었지만 ZSET에는 남아 있는 경우**: `BaseEntity.deletedAt` 기반 soft delete 가 이 프로젝트에 실제로 구현되어 있다 (`ProductService.softDelete`, `softDeleteByBrandId`).
2. **상품이 숨김(`displayYn='N'`) 상태인 경우**: 관리자가 노출을 내린 상품이 집계 테이블에는 과거 데이터가 남아 ZSET 에 그대로 들어올 수 있다.

코드베이스 조사 결과 `ProductRepositoryImpl` 의 검색 쿼리는 `deletedAt IS NULL AND displayYn = 'Y'` 필터를 적용하는 반면, 단건 상세 조회(`findById`) 는 `deletedAt` 만 체크한다. 즉 **"목록 표현 맥락"과 "단건 URL 접근 맥락"에 대해 이미 다른 필터가 운영 중**이다. 랭킹 응답은 "목록 표현" 성격이므로 목록 쪽 필터 기준을 따라야 한다.

단순히 `filter(Objects::nonNull)` 로 숨겨진 상품을 쳐내면 요청 `size=20` 이어도 응답이 17개로 축소되는 사용자 경험 문제가 생긴다.

#### 검토한 선택지

**필터링 위치:**

| 옵션 | 설명 | 평가 |
|------|------|------|
| 집계 SQL 에 `JOIN product ON deleted_at IS NULL AND display_yn='Y'` | 집계 단계에서 원천 차단 | 근본적이지만 commerce-api가 `product_metrics` + `product` 두 테이블을 함께 읽어야 하므로 스키마 결합 ↑ |
| `ProductFacade.findVisibleByIds` 신설, 필터링 책임을 Product 도메인에 위임 | "노출 가능한 상품이 무엇인가" 는 Product 도메인의 규칙 | 책임 분리 명확, 기존 도메인 경계 유지 ⭐ |

**캐시 활용 여부 (findVisibleByIds):**

| 옵션 | 설명 | 평가 |
|------|------|------|
| 캐시 우선 + DB fallback (`ProductCacheStore` 확장) | multiGet 추가, 캐시 hit 필터링 + miss fill-back | 구현 복잡, 이득 미미 |
| 캐시 우회, DB 직접 | `findAllByIdInAndDeletedAtIsNullAndDisplayYn` 한 번 호출 | 단순, 랭킹 트래픽 규모에서 캐시 이득 제한적 ⭐ |

**Over-fetch 여부:**

| 옵션 | 설명 | 평가 |
|------|------|------|
| `size * 2` over-fetch 후 자르기 | 필터로 빠지는 비율 보상 | Redis 부하 증가, 삭제/숨김 비율이 낮으면 과잉 |
| size 그대로 조회, 축소 허용 | "요청 size 는 상한" 이라고 API 문서에 명시 | 단순, 삭제/숨김 비율이 낮다는 현실적 가정 ⭐ |

> **참고**: 1차 전환안의 "빈 집계 결과 캐싱" 논의(Sentinel / populated 마커 / no-op) 는 "API 요청 시점에 DB GROUP BY → replaceScores" 전제 위에 존재했다. 2차 전환(배치 리스너 + ZADD 엎어치기) 이후에는 DB GROUP BY 경로 자체가 사라져 "빈 집계" 개념이 무의미해졌다. Consumer 는 이벤트가 들어오는 날짜에만 키를 생성하고, 자정 직후 빈 구간은 8-4 Carry-Over 스케줄러가 미리 시드하여 해소한다. 아래 결정에서는 해당 항목이 빠진다.

#### 결정

1. **`ProductFacade.findVisibleByIds(List<Long>)` 신설**: `deletedAt IS NULL AND displayYn='Y'` 필터 적용, 캐시 우회하고 DB 직접 조회. 결과는 `Map<Long, ProductInfo>` 로 반환 — 존재하지 않거나 숨겨진 상품은 Map에 없다.
2. **Over-fetch 없음**: `size` 만큼만 ZSET에서 조회, 필터로 빠지면 응답이 축소되는 것을 허용한다. API 문서에 "요청 size 는 상한" 명시.
3. **상품 상세 경로는 기존 `findById` 그대로 유지**: displayYn='N' 상품도 URL 직접 접근은 허용되는 기존 정책을 건드리지 않음.

#### 이유

1. "노출 가능한 상품이 무엇인가" 는 Product 도메인의 규칙이지 Ranking 이 알아야 할 내용이 아니다. 집계 SQL에 필터를 박으면 향후 노출 정책 변경 시 양쪽을 수정해야 한다.
2. 랭킹 응답을 위한 상품 조회는 통상 `size <= 20` 수준으로 배치 크기가 작고 호출 빈도도 제한적이라, 캐시 우회의 DB 부하가 무시할 만하다. 반면 `ProductCacheStore` 를 multiGet 지원하도록 확장하면 인터페이스 오염이 크다.

---

### 8-3. ZSET 쓰기의 원자성 — 증분 ZADD 로 원천 해소

#### 고민 배경 (1차 전환 당시)

초기 계획은 `replaceScores(key, scores, ttl)` 를 "DEL → ZADD → EXPIRE" 3단계로 가정했고, 이 사이의 공백이 싱글 플라이트를 깨거나 부분 실패 시 캐시 전체 소실을 유발할 수 있다는 사각지대가 있었다. 당시 검토했던 대안은 MULTI/EXEC, 임시 키 + RENAME, Lua 스크립트였고 **임시 키 + RENAME** 을 채택했었다(RENAME 단건 커맨드가 원자적이고 부분 실패 시 기존 키 보존).

#### 2차 전환 — 문제 자체 소멸

배치 리스너가 상품 단위로 단건 `upsertScore()` 를 호출하는 구조로 바뀌면서 `replaceScores` 라는 개념 자체가 사라진다.

```java
public interface RankingWriter {
    void upsertScore(String key, Long productId, double score);  // 단일 ZADD 멤버 쓰기
}
```

**Redis `ZADD key score member` 는 이미 원자적이다:**
- 멤버가 없으면 INSERT, 있으면 score 만 UPDATE (단일 커맨드)
- `DEL` 없이 실행되어 기존 키의 다른 멤버 · TTL 에 영향 없음
- 부분 실패 시 해당 상품 1건만 누락 → 다음 배치 poll 에서 자연 재반영

따라서 초기 계획의 원자성 우려는 본 아키텍처에서 발생하지 않는다. TTL 설정은 5-7 의 "최초 생성 시 1회" 정책으로 별도 처리되며, "캐시 스탬피드" 도 API 경로에 재집계 트리거가 없으므로 원천 발생하지 않는다.

#### 의사결정 이력 보존

"임시 키 + RENAME" 결정은 "dailyKey 를 통째로 교체" 하는 1차 전환 전제 위에 성립했다. 2차 전환 이후 이 전제가 사라지면서 RENAME 경로는 구현되지 않는다. 관련 코드/테스트 케이스는 작성하지 않는다.

---

### 8-4. 콜드 스타트 완화 스케줄러 — 23:50 Carry-Over (구 "캐시 스탬피드 스케줄러" 목적 재정의)

#### 고민 배경

2차 전환 이후 Consumer 배치 리스너가 ZSET 을 상시 엎어치므로 "캐시 miss → DB GROUP BY 스탬피드" 라는 원래 문제는 사라졌다. 그러나 **자정 직후 새 일간 키가 비어 있는 시간** 이 한 가지 콜드 구간으로 남는다. 첫 이벤트 유입 전까지 `ranking:all:{오늘}` 키가 존재하지 않아 상품 상세의 `dailyRank` 가 null 로 노출되고, 몇 건만으로는 의미 있는 순위가 만들어지지 않는다.

과제 Nice-To-Have 가 같은 문제에 대해 "23:50 Score Carry-Over 를 통한 랭킹판 미리 생성" 을 언급한다. "오늘 점수 × 0.01 을 내일 키에 미리 시드" 하면 자정 직후에도 어제와 유사한 초기 순위가 존재하며, 오늘 이벤트가 쉽게 역전할 수 있는 낮은 초기값이라 왜곡도 적다.

#### 결정 — 매일 23:50, commerce-streamer 에서 1회 Carry-Over

**20초 주기 `fixedDelay` warm-up 이라는 1차 전환안은 폐기**. 대신 매일 23:50 cron 으로 1회 실행되는 Carry-Over 스케줄러로 목적을 재정의한다.

| 항목 | 결정 | 이유 |
|------|------|------|
| 실행 위치 | commerce-streamer `@Scheduled` | 점수 계산 로직(`RankingScoreCalculator`) 과 쓰기 도구(`RankingWriter`) 가 이미 streamer 에 있음. commerce-api 는 읽기 전용 |
| 실행 주기 | `@Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")` | 과제 Nice-To-Have 명세 그대로 |
| 동작 | 오늘 키의 (productId, score) 쌍을 읽어 score × 0.01 을 내일 키에 `upsertScore` | ZADD 덮어쓰기 — 자정 후 실제 이벤트가 자연스럽게 교체 |
| 과거 키 정리 | 별도 불필요 | 5-7 의 retention TTL 이 자동 만료 |
| 분산 환경 중복 실행 | 허용 | 결과 동일 (idempotent), 낭비만 있음. ShedLock 의존성 추가는 과제 스코프 초과 |
| 실패 처리 | try-catch + 로깅 | 다음날 Carry-Over 누락 시에도 첫 이벤트 유입 후 자연 복구 |

#### 구현 윤곽

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingCarryOverScheduler {
    private final RankingWriter rankingWriter;
    private final RankingReader rankingReader; // streamer 내부 읽기 유틸 (ZREVRANGE WITHSCORES)

    private static final double CARRY_OVER_FACTOR = 0.01;

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void carryOver() {
        LocalDate today = LocalDate.now(KST);
        String todayKey    = RankingKey.daily(today);
        String tomorrowKey = RankingKey.daily(today.plusDays(1));
        try {
            rankingReader.forEachWithScore(todayKey, (pid, score) ->
                rankingWriter.upsertScore(tomorrowKey, pid, score * CARRY_OVER_FACTOR));
        } catch (Exception e) {
            log.warn("ranking carry-over failed", e);
        }
    }
}
```

#### 의사결정 이력 — 20초 주기 warm-up (폐기됨)

1차 전환안에서는 스케줄러의 목적이 "API 경로의 캐시 스탬피드 방지 + dailyRank null 구간 방지" 였고, 20초 `fixedDelay` + `ApplicationReadyEvent` 즉시 warm-up + `refreshDailyRanking` 강제 호출로 설계되어 있었다(D-1 ~ D-7).

2차 전환으로 Consumer 가 ZSET 을 상시 엎어치게 되면서:
- "캐시 miss 감지 → DB GROUP BY" 경로 자체 소멸 → 스탬피드 개념 소멸
- 상품 상세 `dailyRank` 는 첫 이벤트 유입 후 즉시 채워짐 → 수십 초 ~ 수 분 수준의 자정 직후 콜드 구간만 남음
- 이 남은 문제는 1회성 23:50 Carry-Over 로 충분하며, 주기적 warm-up 은 과잉

이에 따라 D-1 ~ D-7 결정은 전부 폐기되고, 단일 cron 스케줄러로 대체된다. 과제 Nice-To-Have "23:50 Score Carry-Over" 가 이 스케줄러 하나로 달성된다.

---

### 8-5. 바운디드 컨텍스트 경계 (2차 전환으로 소멸)

#### 고민 배경 (1차 전환 당시)

1차 전환안에서는 commerce-api 가 `product_metrics` 테이블을 Native SQL + Projection 으로 직접 조회해 집계했다. "엔티티 import 는 안 하지만 테이블명/컬럼명 문자열을 직접 참조" 하는 구조라 **DB 스키마 레벨 결합** 이 존재했고, commerce-streamer 가 컬럼을 rename 하면 commerce-api 가 런타임에 깨지는 위험이 있었다. 이를 완화하기 위해 Testcontainers 기반 회귀 테스트(`ProductAggregateRepositoryImplIntegrationTest`) 를 추가하는 것이 원래 결정이었다.

#### 2차 전환 — 결합 자체가 사라짐

Consumer 배치 리스너가 commerce-streamer 내부에서 점수 계산과 ZSET 쓰기를 완료하게 되면서, **commerce-api 는 `product_metrics` 에 전혀 접근하지 않는다**. commerce-api 의 ranking 경로는 오직 `ranking:all:{yyyyMMdd}` ZSET 만 읽는다. 따라서:

| 항목 | 1차 전환안 | 2차 전환 (최종) |
|------|-----------|-----------------|
| commerce-api → product_metrics 참조 | Native SQL 직접 | **없음** |
| 스키마 결합 | 존재 | **없음** (app 경계로 자연 분리) |
| 회귀 방어 테스트 필요성 | 있음 | **불필요** |
| `ProductAggregateRepository` 위치 | commerce-api/infrastructure | 삭제. 집계는 streamer 내부 `ProductMetricsRepository.snapshotToday` 로 이동 |

`ProductMetricsRepository.snapshotToday(productId)` 같은 전용 조회 메서드는 commerce-streamer 안에서 JPA Entity 기반으로 타입 안전하게 구현되며, Native SQL 은 `upsertIncrements` (#8-6) 에만 국한된다. 결과적으로 commerce-api 쪽의 Native Query + Projection 기반 결합은 **전부 제거** 된다.

> **의사결정 이력:** 1차 전환안의 "표현 교정 + 회귀 방어 테스트 추가" 결정은 당시의 구조적 결합을 수용하기 위한 차선책이었다. 2차 전환으로 결합 자체가 사라지면서 본 섹션의 논의는 역사적 기록으로만 남는다.

---

### 8-6. Consumer 동시 UPSERT 경합 — Native UPSERT 도입 (#3 보강)

#### 고민 배경

R7 의 `MetricsEventService` 는 `findById → 필드 증감 → save` 패턴이다. 기존 엔티티 주석(`ProductMetrics.java`) 은 "같은 `productId` 는 같은 Kafka 파티션에서 순차 처리되므로 동시 INSERT race 는 사실상 없다" 고 기술하고 있다. R9 에서 `(productId, bucket_hour)` 복합키로 전환해도 같은 전제를 유지할 수 있을지 코드 기반으로 검증해야 한다.

#### 코드 조사 결과

**Kafka Producer 파티션 키 (`KafkaEventPublishListener`):**

- `catalog-events` (VIEWED, LIKED): **`productId`** ✅ — 동일 상품의 이벤트는 항상 같은 파티션으로 라우팅되어 단일 Consumer 가 순차 처리한다. 기존 주석의 전제가 성립.
- `order-events` (ORDER_PAID): **`orderId`** ⚠️ — 동일 상품이 서로 다른 주문에 포함되면 **다른 파티션으로 분산**된다.

즉 **기존 주석의 "순차 처리로 충분" 전제는 catalog-events 에만 해당** 하며, order-events 는 맹점이다. 동일 `(productId, bucket_hour)` row 에 여러 Consumer 스레드가 동시에 UPSERT 를 시도하면:

- JPA `findById + new + save` 패턴: 두 스레드가 동시에 "없음" 을 확인하고 둘 다 INSERT → `DuplicateKeyException` → 트랜잭션 롤백
- JPA `findById + 증감 + save` 패턴: Read-Modify-Write 레이스 → lost update (카운터 누락)

`event_handled` UNIQUE 제약은 "같은 eventId 중복 처리" 만 막지, "다른 eventId 인데 같은 상품을 동시 갱신" 은 막지 못한다. **R7 에도 사실상 이 버그가 잠재해 있었으나 통계값의 간헐적 부정확으로 묻혀 있었을 가능성이 크다.**

#### 검토한 선택지

| 옵션 | 대상 | 평가 |
|------|------|------|
| (a1) `order-events` 파티션 키를 `productId` 로 변경 | Producer 쪽 수정 | 근본적이나 한 주문이 여러 상품을 포함하면 이벤트 fan-out 구조 재설계가 필요 — 과제 스코프 초과 |
| (a2) 주문 상품별로 이벤트 분리 발행 | Producer 쪽 대규모 수정 | 멱등성 키도 `(eventId, productId)` 복합으로 재설계 필요 — 과제 스코프 초과 |
| (b) Native `ON DUPLICATE KEY UPDATE` | Repository 쪽만 수정 | commerce-streamer 내부에서 해결, 이벤트 구조 무영향 ⭐ |
| (c) SELECT ... FOR UPDATE | 비관 락 | 동시성 최악 |
| (d) 낙관 락 (`@Version`) | OptimisticLock 재시도 | 기존 주석이 명시적으로 거부한 방식 |

#### 결정 — (b) Native UPSERT

**이유:**
1. commerce-streamer 내부 변경만으로 해결되어 Producer 와 이벤트 스키마에 영향이 없다.
2. `INSERT ... ON DUPLICATE KEY UPDATE` 는 DB 레벨 원자 연산이라, Kafka 파티션 분산과 무관하게 안전하다.
3. R9 에서 `(productId, bucket_hour)` 복합키로 바뀌더라도 같은 패턴이 그대로 적용된다.
4. 기존 엔티티의 증감 메서드(`incrementViewCount`, `decrementLikeCount` 등) 는 조회/테스트 경로에서 여전히 유효하므로 제거할 필요 없다. **쓰기 경로만 Native UPSERT 로 전환** 한다.

**좋아요 취소의 음수 방지**: `GREATEST(like_count - :delta, 0)` 을 Native SQL 안에서 처리하여, 기존 `ProductMetrics.decrementLikeCount` 의 "0 미만으로 내려가지 않음" 불변식을 DB 레벨에서 재현한다.

**order-events 파티션 키 재설계 (a1/a2) 는 "추후 개선 사항" 으로 문서에만 기록**, 이번 과제 범위에서는 제외한다.

#### 구현 윤곽

```java
// ProductMetricsRepositoryImpl
public void upsertIncrements(
        Long productId, LocalDateTime bucketHour,
        long viewDelta, long likeDelta, long orderDelta, BigDecimal amountDelta) {
    entityManager.createNativeQuery("""
        INSERT INTO product_metrics
            (product_id, bucket_hour, view_count, like_count, order_count, order_amount, updated_at)
        VALUES
            (:pid, :bucket, :vd, GREATEST(:ld, 0), :od, :ad, NOW())
        ON DUPLICATE KEY UPDATE
            view_count   = view_count   + :vd,
            like_count   = GREATEST(like_count + :ld, 0),
            order_count  = order_count  + :od,
            order_amount = order_amount + :ad,
            updated_at   = NOW()
        """)
        .setParameter("pid", productId)
        .setParameter("bucket", bucketHour)
        .setParameter("vd", viewDelta)
        .setParameter("ld", likeDelta)
        .setParameter("od", orderDelta)
        .setParameter("ad", amountDelta)
        .executeUpdate();
}
```

`MetricsEventService` 는 `findById → 엔티티 조작 → save` 대신 이 메서드 한 번 호출로 단순화된다. 좋아요 취소는 `likeDelta = -1` 로 호출.

> **INSERT 분기의 `GREATEST(:ld, 0)` 가 필요한 이유:**
> `ON DUPLICATE KEY UPDATE` 의 `GREATEST(like_count + :ld, 0)` 은 **기존 행이 있을 때만** 동작한다. 해당 `(productId, bucket_hour)` row 가 아직 없는 상태에서 좋아요 취소 이벤트(`likeDelta = -1`) 가 들어오면 INSERT 분기가 실행되어 `like_count = -1` 이 그대로 적재되는 버그가 발생한다. INSERT VALUES 에서도 `GREATEST(:ld, 0)` 으로 0 이상 clamp 해야 "좋아요 수가 음수가 되지 않는다" 는 불변식이 DB 레벨에서 유지된다. `:vd` / `:od` / `:ad` 는 정상 이벤트에서 음수가 아니므로 별도 가드 불필요하며, 향후 주문 취소 지원 시 `amountDelta` 에도 동일 패턴을 적용한다.

---

### 8-7. Redis 템플릿 선택 근거 보강 (#5-4 보강)

#### 고민 배경

초기 계획 #5-4 는 `masterRedisTemplate` 을 사용한다고 명시했지만, 근거가 "방금 ZADD 한 값을 바로 ZREVRANGE 해야 하므로 Replica 지연 회피" 한 줄로 짧았다. 리뷰에서 "읽기 부하 편중" 우려가 제기되어 재검토가 필요했다.

#### 코드 조사 결과

`modules/redis/RedisConfig` 구성을 확인한 결과, 이 프로젝트의 Redis 템플릿은 다음 두 개뿐이다.

| 템플릿 | 읽기 전략 | 쓰기 대상 |
|--------|----------|----------|
| `defaultRedisTemplate` (`@Primary`) | `REPLICA_PREFERRED` | 마스터 (Lettuce 자동 라우팅) |
| `masterRedisTemplate` (`REDIS_TEMPLATE_MASTER`) | `MASTER` | 마스터 |

**핵심 사실:**
- `REDIS_TEMPLATE_REPLICA` 는 **존재하지 않는다** (grep 결과 0건).
- 쓰기는 어느 템플릿을 쓰든 항상 마스터로 간다 — `RedisStaticMasterReplicaConfiguration` 이 자동 라우팅.
- 두 템플릿의 실질적 차이는 오직 **"읽기를 Replica 로 보낼 것인가, 마스터로 강제할 것인가"**.

**기존 ZSET 사용 사례:**
- `QueueRedisRepository`, `QueueTokenRedisRepository` 등 **대기열 관련 ZSET 컴포넌트는 전부 `masterRedisTemplate` 사용**. 이유는 "enqueue 직후 rank 조회" 같은 쓰기 직후 읽기 패턴 때문이다.

#### 결정 — `masterRedisTemplate` 유지

**이유:**
1. **기존 관례와 일관**: 대기열 등 "시점 민감한 ZSET 데이터" 는 전부 `masterRedisTemplate` 을 쓰고 있다. 랭킹 ZSET 도 같은 성격이므로 관례를 따른다.
2. **`ensureDailyRanking` miss 경로에서 쓰기 직후 읽기**: 캐시 miss 시 `replaceScores → getTopN` 이 **같은 요청 스레드 안에서 순차 실행** 된다. Replica 복제 지연이 수 ms 만 되어도 방금 ZADD 한 멤버가 ZREVRANGE 결과에 누락될 수 있다.
3. **쓰기는 어차피 마스터** 로 가므로 "읽기 분산" 만이 Replica 를 선택할 유일한 이유인데, 이 프로젝트에 `replicaRedisTemplate` 이 없어서 읽기 분산을 쓰려면 Redis 모듈 설정 변경부터 필요하다 — 과제 스코프 초과.
4. 스케줄러(8-4) 가 있어도 **보조 수단일 뿐** 주 경로는 miss fallback 이므로, 쓰기 직후 읽기 경로는 여전히 살아 있다.

**문서 보강 문구:**
> `masterRedisTemplate` 사용. 대기열 등 기존 ZSET 컴포넌트의 관례와 일치하며, `ensureDailyRanking` miss 경로에서 `replaceScores` 직후 `getTopN` 이 같은 요청 스레드 내에서 실행되므로 Replica 복제 지연 회피가 필요하다. 트래픽 증가 시 읽기/쓰기 템플릿 분리를 재검토한다.

---

### 8-8. rank / page 체계 명시

#### 고민 배경

초기 계획 #5-4 에 `getRank` 가 "`zRevRank()` 결과를 1-based 로 변환" 한다고만 짧게 적혀 있었다. 그러나 rank/page 의 기준(0-based vs 1-based) 은 클라이언트 계약의 핵심이라 명시적으로 고정해야 한다.

#### 결정

| 항목 | 결정 | 근거 |
|------|------|------|
| **rank (순위 번호)** | **1-based**, nullable (`Long rank`) | 사용자 노출 관례 ("1등" = rank 1). 순위권 밖은 null — 과제 체크리스트 명시. |
| **page 파라미터** | **1-based** (사용자 입력 기준) | 과제 명세의 `?page=1&size=20` 예시가 "1페이지(첫 번째 페이지)" 를 의미한다고 해석. 기존 코드베이스(`ProductV1Controller` 등) 는 Spring `Pageable` 기본값(0-based) 을 쓰지만, 여기서는 과제 계약을 우선. Controller 에서 입력 `page` 를 받아 내부적으로 `max(page - 1, 0)` 으로 0-based 변환 후 ZREVRANGE 에 사용. |
| **페이지 내 rank 계산** | `(page - 1) * size + index + 1` (입력 1-based 기준) | page=1 인 경우 rank 는 1 부터 시작, page=2 는 size+1 부터 시작. |
| **응답 DTO 타입** | `Long rank` (박싱) | primitive `long` 은 null 표현 불가 — 금지 |

---

### 8-9. 1시간 단위 랭킹 (Nice-To-Have, 이번 구현 스코프 제외)

#### 고민 배경

과제 Nice-To-Have 에 "초 실시간(1시간 단위) 랭킹" 이 있다. 1차 전환안에서는 DB GROUP BY 범위를 1시간으로 좁혀 동일 경로에서 파생하도록 설계했다(별도 엔드포인트, TTL 이분화, `RankingCacheProperties` 4필드 확장 등).

#### 2차 전환 — 스코프 제외 결정

2차 전환 이후 1시간 단위 랭킹을 구현하려면 다음이 추가로 필요하다.

- `ranking:1h:{yyyyMMddHH}` ZSET 키 한 세트
- Consumer 배치 리스너에서 **일간 키 + 시간 키 두 곳에 동시 ZADD**
- `RankingKey.hourly()` 헬퍼, Facade/Controller 의 hourly 경로, DTO 확장
- `RankingCacheProperties` 에 시간 키 retention 필드 추가 (예: 25h)

구현 자체는 어렵지 않지만 쓰기 경로가 **두 배** 가 된다. 2차 전환에서 이미 포함한 Nice-To-Have 두 개(배치 리스너, Carry-Over 스케줄러) 가 있고, 과제 Must-Have 체크리스트는 일간 랭킹만 요구한다. 초점 유지를 위해 **1시간 단위 랭킹은 이번 구현 스코프에서 제외**한다.

배제했을 때 잃는 것:
- Nice-To-Have "초 실시간 랭킹" 미달성 (Must-Have 는 영향 없음)
- 상품 상세 응답의 `hourlyRank` 필드 (8-10 결정으로 어차피 없음)

남겨놓는 설계 메모(후속 구현 시 참고):

| 항목 | 방향 |
|------|------|
| 쓰기 경로 | `MetricsEventService.processBatch()` 가 `upsertScore(dailyKey, ...)` + `upsertScore(hourlyKey, ...)` 두 번 호출. 점수는 "일간 총합 기반" 이 아닌 "해당 시간 bucket 값 기반" 이어야 의미가 맞음 → `snapshotByBucket(productId, bucket)` 조회 메서드 별도 필요 |
| API 엔드포인트 | `GET /api/v1/rankings/hourly?datetime=yyyyMMddHH&page=1&size=20` (page 는 #8-8 의 1-based 규칙 준수). `datetime` 생략 시 현재 시간 |
| TTL | 단일 retention 25h~2d 중 선택. 5-7 의 `RankingCacheProperties.retention` 을 공유하거나 `hourlyRetention` 필드를 추가 |
| 시간 경계 | Consumer 가 상시 쓰므로 자정/매시 경계 특별 처리 불필요 |
| Carry-Over | 시간 키는 Carry-Over 불필요 (한 시간 주기로 신규 키 생성) |

> **의사결정 이력 — E-1/E-2/E-3 하위 결정 폐기:** 1차 전환안의 E-1(별도 엔드포인트), E-2(현재 30초 / 과거 5분 TTL), E-3(시간 경계 처리) 는 전부 "API 시점 집계" + "`fixedDelay` 20초 스케줄러" 전제 위에 있었고, 2차 전환으로 전제가 사라졌다. 향후 후속 구현 시에는 위의 "남겨놓는 설계 메모" 를 출발점으로 재설계한다.

---

### 8-10. 상품 상세 응답의 순위 필드 — 일간만

#### 고민 배경

1시간 단위 랭킹을 구현하기로 결정(8-9) 하면서, 상품 상세 응답의 "순위" 를 일간 기준으로 할지, 시간 단위도 포함할지 재검토가 필요해졌다. 과제 체크리스트는 "어느 랭킹 기준인지" 를 명시하지 않았다.

#### 검토한 선택지

| 옵션 | 응답 | 평가 |
|------|------|------|
| (i) 일간만 | `dailyRank` | 과제 체크리스트 최소 충족, 단순 |
| (ii) 시간 단위만 | `hourlyRank` | 체크리스트=일간 기준 해석과 불일치 |
| (iii) 둘 다 | `dailyRank` + `hourlyRank` | 정보량 최대, 응답 복잡도 약간 ↑ |

#### 결정 — (i) 일간만

**이유:**
1. 과제 Must-Have 는 일간 랭킹이므로 체크리스트의 "해당 상품의 순위" 는 일간 기준으로 해석하는 것이 자연스럽다.
2. 응답 DTO 를 불필요하게 넓히지 않는다.
3. 1시간 단위 랭킹은 전용 엔드포인트(`/rankings/hourly`) 를 통해 충분히 노출된다 — 상품 상세에 중복해 넣을 필요가 없다.

#### Controller 합성 (2차 전환 반영)

```java
@GetMapping("/api/v1/products/{productId}")
public ApiResponse<ProductDetailResponse> detail(
        @PathVariable Long productId,
        @LoginMember Member member) {
    ProductDetailInfo detail = productFacade.getProduct(productId, member.getId());
    Long dailyRank = rankingFacade.getDailyRank(productId);  // ZREVRANK, null if absent
    return ApiResponse.success(ProductDetailResponse.of(detail, dailyRank));
}
```

`rankingFacade.getDailyRank` 는 내부적으로 `rankingRepository.getRank(todayKey, productId)` 를 호출한다. Consumer 배치 리스너가 ZSET 을 상시 엎어치고 23:50 Carry-Over(#8-4) 가 자정 직후를 커버하므로, 실제 운영 중에는 이벤트가 한 건이라도 들어온 상품은 즉시 순위가 잡힌다. `null` 은 "해당 상품에 오늘 이벤트가 전혀 없음 또는 순위권 밖" 을 의미.

---

### 8-11. 확정 사항 요약 체크리스트 (2차 전환 최종본)

구현 착수 전 다음 항목이 모두 반영되어야 한다. 1차 전환안의 체크리스트는 아래 "의사결정 이력" 하위에 보존되어 있다.

**Kafka 배치 리스너 (commerce-streamer):**
- [ ] `KafkaConfig` 에 `batchListenerFactory` 등록 (없으면 신설). 기존 `BATCH_LISTENER_DEFAULT` 재사용 가능
- [ ] `CatalogEventConsumer` / `OrderEventConsumer` 를 `@KafkaListener(batch = true)` 로 전환, 파라미터를 `List<ConsumerRecord<String, String>>` 로 변경
- [ ] poll 단위에서 상품별 `MetricDelta` 로 합산하는 aggregate 유틸 작성

**매트릭 적재 & 점수 계산 (commerce-streamer):**
- [ ] `ProductMetrics` 스키마를 `(productId, bucket_hour)` 복합키로 전환 (#3)
- [ ] 기존 R7 `product_metrics` 데이터는 drop & recreate (#3 마이그레이션 노트)
- [ ] `ProductMetricsRepositoryImpl.upsertIncrements(...)` Native UPSERT 메서드 추가 (8-6). **INSERT VALUES 에도 `GREATEST(:ld, 0)` 가드** 적용
- [ ] `ProductMetricsRepository.snapshotToday(productId)` 조회 메서드 추가 — `processBatch` 내부에서 점수 계산용
- [ ] `RankingScoreCalculator` + `RankingWeights` (`@ConfigurationProperties` `ranking.weights.*`) 신설 (4-2, 4-5). null-safety 가드 포함 (#10)
- [ ] `RankingWriter` / `RedisRankingWriter` 신설 — `upsertScore(key, pid, score)` 단건 ZADD + `expire(key, retention)` (5-4, 5-7)
- [ ] `MetricsEventService.processBatch(perProductDeltas)` — `@Transactional` 안에서 `event_handled` 멱등 → `upsertIncrements` → `snapshotToday` → `calculate` → `upsertScore` 순 호출 (#1, #2)

**Carry-Over 스케줄러 (commerce-streamer):**
- [ ] `RankingCarryOverScheduler` — `@Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")` (8-4)
- [ ] 오늘 키 ZSET 을 `ZRANGE WITHSCORES` 로 읽어 `score × 0.01` 을 내일 키에 `upsertScore` 로 시드
- [ ] try-catch + 로깅. 분산 환경 중복 실행은 idempotent 이므로 허용

**Ranking 읽기 경로 (commerce-api):**
- [ ] `domain/ranking` 패키지 신설: `RankingRepository`, `RankingEntry`, `RankingKey` (5-1, 5-3)
- [ ] `RedisRankingRepository` — `getTopN(key, pageOneBased, size)` + `getRank(key, productId)` 두 메서드만 (5-3, 5-4)
- [ ] `masterRedisTemplate` 사용 (5-3)
- [ ] `exists` / `replaceScores` / `getRankIfCached` **폐기** — 2차 전환으로 필요 없음

**Application / Facade (commerce-api):**
- [ ] `ProductFacade.findVisibleByIds(List<Long>)` 신설 — `deletedAt IS NULL AND displayYn='Y'` 필터, 캐시 우회 DB 직접 조회 (8-2)
- [ ] `RankingFacade.getDailyRanking(date, page, size)` — `ZREVRANGE` + `findVisibleByIds` + 응답 조립 (5-5)
- [ ] `RankingFacade.getDailyRank(productId)` — 상품 상세용 `ZREVRANK` 단순 호출, null if absent (5-6, 8-10)

**Interfaces (commerce-api):**
- [ ] `GET /api/v1/rankings?date=yyyyMMdd&page=1&size=20` (page 는 **1-based**, 8-8)
- [ ] 상품 상세 응답에 `dailyRank` (nullable, 1-based `Long`) 필드 추가 (8-10)
- [ ] 페이지 내 rank 계산식: `(page - 1) * size + index + 1` (8-8)
- [ ] `RankingV1Controller` / DTO 신설

**설정 / 외부화:**
- [ ] `ranking.weights.view/like/order = 0.1/0.2/0.7` (4-2, 4-5)
- [ ] `ranking.cache.retention = P2D` (5-7, 단일 retention TTL)

**테스트:**
- [ ] `RankingScoreCalculator` 단위 테스트 (null-safety 포함)
- [ ] `MetricsEventService.processBatch` 통합 테스트 (Testcontainers) — 압축, 멱등, UPSERT, ZADD 까지 end-to-end
- [ ] `RedisRankingWriter` / `RedisRankingRepository` 통합 테스트
- [ ] `RankingKey` 포맷 회귀 테스트 (streamer + api 양쪽 동일 리터럴 고정)
- [ ] E2E — `/api/v1/rankings?date=...&page=1` 응답 검증, 주문 1건 > 좋아요 3건 가중치 검증, 자정 경계/과거 날짜 조회 검증 (과제 Checklist)

**스코프 제외 (설계만 보존):**
- [ ] #6 `RankingCorrectionBatch` — 설계 문서만 유지, 구현 안 함
- [ ] 1시간 단위 랭킹(#8-9) — 이번 스코프 제외. 설계 메모만 보존

---

#### 의사결정 이력 — 1차 전환안의 구 체크리스트 항목들 (폐기)

아래 항목들은 1차 전환안("API 요청 시점 집계 + 20초 warm-up 스케줄러 + commerce-api 에 ranking 도메인 집중") 전제에서 만들어졌고, 2차 전환으로 모두 폐기된다.

- ~~`domain/ranking` 패키지를 **commerce-api** 에 집중 신설~~ → streamer=쓰기, api=읽기 자연 분리 (5-1)
- ~~`RankingRepository.getRankIfCached` 추가~~ → 단순 `getRank` 로 통합 (8-1)
- ~~`RedisRankingRepository.replaceScores` 임시 키 + RENAME~~ → `upsertScore` 단건 ZADD 로 대체 (8-3)
- ~~`RankingCacheProperties` 시간 단위 TTL 필드 추가~~ → 단일 `retention` 필드로 축소 (5-7, 8-9)
- ~~`ProductAggregateRepositoryImpl` Native Query (일간/시간 범위)~~ → commerce-api 에서 DB 직접 조회 자체가 사라짐 (8-5)
- ~~`ProductAggregateRepositoryImplIntegrationTest` 회귀 방어 테스트~~ → 결합 자체 소멸로 불필요 (8-5)
- ~~`RankingFacade.getHourlyRanking(datetime, pageable)`~~ → 1시간 랭킹 스코프 제외 (8-9)
- ~~`RankingFacade.getDailyRankIfCached(productId)` non-trigger 경로~~ → `getDailyRank` 로 단순화 (5-6, 8-1, 8-10)
- ~~`GET /api/v1/rankings/hourly?datetime=...&page=0&size=20`~~ → 엔드포인트 미구현 + page 는 1-based (8-8, 8-9)
- ~~`RankingCacheScheduler` — `@Scheduled(fixedDelay = 20_000)`~~ → `RankingCarryOverScheduler` cron `0 50 23 * * *` 로 목적 재정의 (8-4)
- ~~`ApplicationReadyEvent` 즉시 warm-up~~ → 불필요. Consumer 가 첫 이벤트부터 ZSET 을 채우기 시작 (8-4)
- ~~#5-2 "경계가 진짜로 지켜짐" 교정~~ → 2차 전환으로 결합 자체 소멸 (5-2, 8-5)
- ~~#6 "Nice-To-Have 강등" 진술 철회~~ → 2차 전환 맥락에선 다시 "설계만 보존, 구현 제외" 로 정리됨

---

## 📖 의사결정 여정 (Decision Journey)

이 섹션은 최종 설계에 도달하기까지 거쳤던 고민과 전환의 흐름을 순서대로 기록한다. 블로그 Technical Writing Quest의 주 소재가 되며, "**무엇을 결정했는가**" 보다 "**왜 그렇게 결정했는가**" 가 드러나도록 정리한다.

### 1단계 — 점수 반영 방식의 첫 번째 선택: ZINCRBY vs 재계산 ZADD

처음에는 이벤트 소비 시점에 **ZINCRBY로 점수를 증분**하는 단순한 방식을 고려했다. 이벤트 하나 = Redis 명령 하나로 간결하지만, 다음 문제로 재계산 ZADD를 선택했다.

- 메시지 유실 시 점수도 유실 → 복구 수단 없음
- 중복 소비 시 점수가 이중 반영 → at-least-once 환경에서 부풀려짐
- `event_handled` UNIQUE 제약 기반의 기존 멱등성 방어와 부합하지 않음

**당시 결정**: **재계산 ZADD**. "DB를 원장으로 삼고 Redis는 파생"이라는 멘토링 원칙과도 일치했다.

### 2단계 — 점수 계산 위치 (A / B / C)

재계산 ZADD를 어느 클래스에서 수행할 것인가:
- (A) `MetricsEventService` 내부
- (B) 별도 `RankingService` + Consumer 오케스트레이션
- (C) Spring Event 발행 + `@TransactionalEventListener(AFTER_COMMIT)`

**당시 결정**: **(C) Spring Event**. 기존 codebase(`LikeEventListener` 등)가 같은 패턴을 이미 쓰고 있어 일관성 있고, DB 트랜잭션과 Redis 호출이 자연스럽게 분리되어 Redis 장애가 DB를 롤백시키지 않는다는 이점이 결정적이었다.

### 3단계 — 집계 테이블 스키마: 누적 vs 일별 vs 시간별

"일간 랭킹을 하려면 ProductMetrics의 어느 레벨에 데이터가 있어야 하는가" 라는 질문에서 출발했다. 현재 `ProductMetrics`는 `productId` 단일 PK에 **전체 누적값**만 저장하므로 "오늘 얼마나 쌓였는가"를 역산할 수 없다.

검토한 3가지 모델:
- **이벤트 로그**: `product_event_log` row per event → 과도한 스토리지, 과제 스코프 초과
- **일별 집계**: `(productId, date)` 복합키 → 단순하나 시간 단위 랭킹 불가
- **시간 단위 집계**: `(productId, bucket_hour)` 복합키 → Nice-To-Have "1시간 랭킹" 커버

**당시 결정**: **`(productId, bucket_hour)` 시간 단위 집계**. 일간은 hour 파생, Nice-To-Have까지 같은 스키마로 커버. 하루 상품당 최대 24행 × 2일 보관 = 약 480만 row로 MySQL 충분.

`ProductMetrics`의 외부 소비자 조사 결과 commerce-streamer 내부에서만 쓰이고 있음을 확인 → 스키마 대체(옵션 Y)가 안전.

"날짜+시간을 왜 한 컬럼(`DATETIME`)에?" 는 시간 범위 쿼리의 자연스러움, 자정 경계 자동 처리, `LocalDateTime` 단일 타입 매핑 때문.

### 4단계 — 가중치와 점수 공식 (A/B/C/D 갈림길)

**A 갈림길: 주문 점수 산정** — 건수 vs 금액 vs log 금액
- A-1 건수: 매출 기여 무시
- A-2 금액 원본: 스케일 폭주로 view/like 의미 상실 (`0.6 × 500,000 = 300,000` 같은 값이 view 10을 압도)
- **A-3 log 금액** ✅: `log1p(orderAmount)` — 스케일 압축 + 매출 반영 + 가중치 의미 유지

**B 갈림길: 가중치 설정 방식** — 하드코딩 vs YAML vs DB
- **B-2 YAML** ✅: `@ConfigurationProperties` record, 프로젝트 CLAUDE.md의 "환경설정 하드코딩 금지" 규칙 준수

**C 갈림길: 좋아요 취소 처리** — 가장 논쟁이 길었던 지점 (아래 별도 기록)

**D 갈림길: 정규화** — 부분 log vs 전체 log vs min-max
- 시뮬레이션 결과 **부분 정규화**(view는 원본, order만 log)는 조회 많은 상품 편향 발견
- **전체 log 정규화** ✅: 모든 지표에 `log1p` → 스케일 통일, 가중치가 의도대로 작동
- min-max 정규화는 전역 max 계산 비용이 과제 스코프 초과

### 5단계 — 좋아요 취소 처리의 미로 (C 갈림길 심층)

이 과정이 가장 길었고, 결과적으로 **아키텍처 전환의 씨앗**이 되었다.

**초기 옵션:**
- **C-1 현재 버킷 차감**: 15시에 취소되면 15시 bucket에서 `-1` → 음수 가드 필요 → 데이터 왜곡
- **C-2 원래 버킷 차감**: 이벤트에 `originalLikedAt` 추가 → R7 스키마 변경, TTL 만료 리스크
- **C-3 메트릭 차감 + ZSET 유지**: DB는 정확, ZSET은 자연 보정
- **C-4 완전 무시**: 이벤트 skip, 메트릭도 차감 안 함

**새로운 제안 (C-5) — "현재 순 좋아요 수" 사용**:
- view/order는 bucket 누적, like만 "현재 순 값"으로 사용
- 취소 처리 고민 자체가 사라짐
- 단점: bucket 기반과 현재값의 성격이 섞여 시간 단위 랭킹의 의미가 모호해짐
- 추가 고려: "현재 좋아요 수"를 어디서 가져올 것인가 (바운디드 컨텍스트 위반 위험)

**시간 감쇠 검토**:
- 이벤트가 오래될수록 영향력이 지수 감쇠
- 장점: 트렌딩, 취소 자연 소멸, 콜드 스타트 완화
- **검증 결과**: 
  - 스케일 차이(view 5000 vs order 100만원)는 여전히 해결 못 함
  - 구현 복잡도(이벤트 로그 or 수학적 트릭 or 주기 재계산) 매우 높음
  - 단기 취소 정확성은 여전히 떨어짐 (장기 완화만 됨)
- **배제** — 트렌딩 랭킹용 멋진 기법이지만 이 문제에는 과잉 대응

**"순수 카운트 + 시간 감쇠" 조합 검토**:
- 스케일 차이 미해결 (`log` 없이는 view/order 스케일 격차 여전)
- 시간 감쇠만으로는 스케일 정규화가 되지 않음
- **배제**

**멘토링 지침 수령**:
> "랭킹 시스템의 목표는 '현재 인기 있는 순서나 트렌드'를 보여주는 것이지, 데이터 수치를 한 치의 오차도 없이 정확하게 보여주는 것이 아니다."
>
> "취소·환불·결제 실패 같은 부정적 이벤트에 실시간으로 마이너스 가중치를 두지 말고, 일부 유실을 허용하며 주기적 보정 배치로 원장(매트릭)을 재조회해 스코어를 새롭게 계산한 뒤 한 번에 덮어씌우는 방식이 합리적이다."

이 지침이 길어진 논쟁을 정리했다.

**중간 합의**: C-3 (DB는 정확히 차감, ZSET은 skip) + 주기적 보정 배치(#6)

### 6단계 — 배치 주기 결정과 과제 명세 재독

C-3을 지원할 보정 배치의 주기를 결정하려 할 때, 과제 명세의 **"실시간 랭킹"** 표현을 두고 혼란이 생겼다.

- 초기 해석: "실시간이니 1분 배치 주기가 필요"
- **과제 재독**: Must-Have의 "Realtime Ranking"은 **실시간 파이프라인**(이벤트 기반 갱신)을 의미하고, Nice-To-Have의 "실시간 랭킹"은 **1시간 단위 윈도우**를 별도로 정의
- 따라서 Must-Have는 일간 랭킹이 핵심이고, 배치 주기는 실시간성보다 "드리프트 허용 범위"에 맞춰야 함

**수정**: 15분 배치 + 변경 감지 기반(`updated_at >= lastRunAt`) + commerce-streamer `@Scheduled`. 과제 스코프 최소화를 위해 Must-Have는 "자연 보정"(재계산 ZADD 덕분에 다음 긍정 이벤트가 드리프트 해소)만으로 가고, 배치는 Nice-To-Have로 분리.

### 7단계 — 아키텍처 전환의 트리거: ZUNIONSTORE 비선형 함정

"일간 랭킹을 hour bucket 24개의 ZUNIONSTORE로 파생한다"는 설계를 구체화하는 과정에서 **중대한 수학적 문제**를 발견했다.

hour bucket에 이미 점수(`0.1 × log1p(view_h) + ...`)를 저장하고 ZUNIONSTORE SUM으로 일간 점수를 만들면:

```
daily_score_ZUNIONSTORE = Σ_h [ 0.1·log1p(view_h) + 0.2·log1p(like_h) + 0.7·log1p(amount_h) ]
                        = 0.1·Σ_h log1p(view_h) + ...

"올바른" 일간 점수 = 0.1·log1p(Σ_h view_h) + 0.2·log1p(Σ_h like_h) + 0.7·log1p(Σ_h amount_h)
```

`log1p(A) + log1p(B) ≠ log1p(A + B)`. 구체 예시로 검증:
- 10시에 view 10, 11시에 view 10 → ZUNIONSTORE: `2 × log1p(10) ≈ 4.8`
- 올바른 계산: `log1p(20) ≈ 3.04`
- 차이 1.76, "꾸준히 활동한 상품"이 과대평가됨

이게 **의도된 동작이라면** OK지만, 과제 명세(`Score = price × amount`)의 "단일 window 점수"라는 의미와는 다르다.

### 8단계 — 전환의 결정: DB 집계 + Java 점수 계산

사용자 제안: *"ZUNIONSTORE를 하지 않고 DB 조회 결과를 가중치로 계산하는 비즈니스 로직을 별도로 만드는 건 어떨까?"*

**이 제안이 수학적으로 올바르다**는 것을 확인. 그리고 그 연장선에서 훨씬 큰 단순화가 가능하다는 것도 깨달았다.

**기존 설계**:
```
Consumer → product_metrics UPSERT + hour bucket ZSET ZADD (Spring Event 경로)
API 요청 → ZUNIONSTORE(24 hour keys) → ZREVRANGE
```

**전환된 설계**:
```
Consumer → product_metrics UPSERT만 (ZSET 쓰기 없음)
API 요청 → DB GROUP BY 집계 → Java 점수 계산 → ZADD 캐시 → ZREVRANGE
```

**이 전환이 해결하는 것들**:
1. **수학적 정확성 회복** — log 정규화가 "일간 총합"에 한 번만 적용됨
2. **점수 계산 로직 단일화** — `RankingScoreCalculator` 한 곳에만 존재
3. **Spring Event 경로 제거** — 디버깅 포인트 감소
4. **좋아요 취소 드리프트 자동 해소** — 매 API 요청이 DB 원장 기반 재계산이므로 최대 30초 TTL 내에 반영
5. **#6 보정 배치 거의 불필요** — API 경로 자체가 "매번 보정"
6. **멘토링 원칙("원장 재조회 + 재계산 + 덮어쓰기")을 가장 순수하게 구현**

### 9단계 — 도메인 분리와 바운디드 컨텍스트 경계

전환된 설계를 commerce-api의 어느 도메인에 둘 것인가:

- commerce-api에 `ranking` 도메인 신설 (기존 `product`, `queue` 패턴)
- commerce-streamer의 `ProductMetrics` 엔티티를 **직접 import하지 않음**
- 대신 `ProductAggregateRepository` 인터페이스와 `ProductDailyAggregate` VO를 자체 정의
- 인프라 레이어에서 **Native Query + Projection**으로 `product_metrics` 테이블을 읽음 → 바운디드 컨텍스트 경계 유지

상품 상세의 순위 조회도 고민 포인트였다:
- ProductFacade가 RankingFacade를 호출? → Product 도메인이 Ranking을 알게 됨
- **Controller 레벨에서 두 Facade 결과 합성** ✅ → 도메인 간 단방향 의존 보존

### 10단계 — TTL 정책의 두 얼굴

설계가 거의 마무리되고 구현 착수 직전, **TTL 값에 대한 혼동**이 드러났다. 제가 "30초"를 제안하고 있었는데, 과제 명세에는 **"TTL 2일"** 이 명시돼 있었던 것이다.

두 가지를 혼동하고 있었음이 확인됐다:
- **과제 명세의 "TTL 2일"** = 랭킹 데이터의 **보존 기간** (어제/그제 랭킹도 조회 가능해야 함)
- **30초 TTL** = 오늘 랭킹의 **재집계 주기** (캐시 성격, 실시간성 + 취소 자동 보정)

단일 TTL 값으로는 두 요구를 동시에 만족시킬 수 없다는 걸 깨달았다.
- 모든 날짜에 2일 → 오늘 랭킹이 stale, "DB 집계 + 30초 재계산" 이점 소멸
- 모든 날짜에 30초 → 과제 명세 위반, 어제 랭킹 조회 때마다 DB 재집계 (의미 없음)
- 이중 키 구조(원본 2일 + 캐시 30초) → 구현 복잡도 과다

**해결: 날짜별 TTL 분기** (방식 A)
- **오늘 날짜 → 30초 TTL**: 캐시 성격, 매 30초마다 DB 재집계 → 취소 반영, 새 이벤트 반영
- **어제 이전 → 2일 TTL**: 보존 성격, 한 번 생성 후 그대로 유지 → 과제 명세 부합, 과거 데이터는 변하지 않는다는 가정

**전제 검증**: "과거 데이터가 변하지 않는다"는 가정이 성립하는가?
- Kafka 지연이 수 시간을 넘지 않는다면 자정 후 어제 이벤트가 대량 유입되는 일은 드묾
- 극단적 edge case는 있으나, 랭킹의 본질(정확성 아닌 트렌드)과 정합
- 멘토링 원칙("비즈니스 효익 없는 정밀 처리 지양")과도 부합

**배운 것**: **"TTL"이라는 같은 단어가 두 가지 서로 다른 개념**을 가리킬 수 있다. 하나는 "데이터 수명", 하나는 "캐시 무효화 주기". 설계 초기에 이 구분을 명확히 하지 않으면 혼동이 생긴다.

### 11단계 — 최종 결정 목록

| # | 결정 | 핵심 |
|---|------|------|
| 1 | 점수 반영 방식 | 재계산 ZADD (트리거: API 요청) |
| 2 | 점수 계산 위치 | 🚫 Spring Event 폐기 → **`RankingService`** (API 요청 시점) |
| 3 | 집계 테이블 스키마 | `(productId, bucket_hour)` — 범위 필터링용 |
| 4-1 | 주문 점수 산정 | A-3 `log1p(orderAmount)` |
| 4-2 | 가중치 설정 | B-2 YAML `@ConfigurationProperties` |
| 4-3 | 좋아요 취소 | C-3 DB 차감, ZSET은 **매 API 요청이 자동 보정** |
| 4-4 | 정규화 | 전체 log 정규화 (`log1p(Σ)` 형태) |
| 4-5 | 점수 공식 | `0.1·log1p(view) + 0.2·log1p(like) + 0.7·log1p(amount)` |
| 5 | Ranking API | DB 집계 + Java 계산 + ZSET 캐시, Native Query + Projection |
| 5-7 | TTL 정책 | **날짜별 분기**: 오늘 30초 / 과거 2일 |
| 6 | 보정 배치 | Nice-To-Have에서도 강등 (거의 불필요) |
| 7 | 카프카 배치 리스너 | Nice-To-Have (DB 쓰기 최적화 용도) |

### 12단계 — 핵심 교훈

1. **처음 떠오른 "당연한" 설계를 수학적으로 검증하는 것의 중요성** — ZUNIONSTORE는 직관적이지만 log 정규화와 결합 시 왜곡을 낳는다.
2. **과제 명세의 단어("실시간")를 섣불리 해석하지 않기** — Must/Nice의 맥락이 다르다.
3. **같은 단어("TTL")가 다른 개념을 가리킬 수 있다** — "데이터 보존 기간"과 "캐시 무효화 주기"를 구분하지 않으면 혼동이 생긴다.
4. **복잡성은 잘못된 전제에서 온다** — ZSET 실시간 갱신 전제를 버리자 Spring Event, 배치 보정, 취소 처리가 모두 단순해졌다.
5. **멘토링 지침은 "원칙"을 제공** — 구체적 구현보다 판단 기준으로 활용해야 함.
6. **DB와 Redis의 역할을 "원장 vs 캐시"로 명확히 분리** — 드리프트, 정합성, 재구축 문제가 자연스럽게 해결됨.

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

- 누적 랭킹만 유지하면 왜 **롱테일 문제**가 발생할까?
- **시간의 양자화..** 처음 보는 너, 대체 왜 필요하니?
- **콜드스타트(0점에서 시작)** 문제를 어떻게 풀 수 있을까?
- 우리의 **랭킹 지표**는 이렇게 구성되요. 진짜 **인기있는 상품**은 이런 거예요.
- **실시간 랭킹?** 어려워 보이죠? 이렇게 풀면 쉽다!
- 상품이 10만 개일 때 ZSET 메모리는 얼마나 쓸까? 상위 N개만 유지하면?
- Top-N을 매번 ZREVRANGE로 조회하는 것과 주기적으로 캐싱하는 것의 트레이드오프는?

---

## 📋 구체적 구현 목록 (최종 의사결정 기반)

위에서 논의하고 확정한 **2차 전환 이후의 설계안**을 바탕으로, 목표 요구사항(Checklist)을 만족하기 위해 구현해야 할 Task들을 정리했습니다. 작업을 작게 나누어 PR 단위를 구성하거나 스텝 바이 스텝 구현 시 참고하세요.

### 1. 매트릭 적재 & DB 스키마 변경 (commerce-streamer)
- [ ] `ProductMetrics` 엔티티 리팩토링
  - `(productId, bucket_hour)` 복합키 구조로 변경 (`@IdClass ProductMetricsId` 추가)
  - `orderAmount` (주문 누적 금액, `BigDecimal`) 필드 추가
  - (옵션) 기존 R7 테스트/로컬의 `product_metrics` 데이터 초기화 
- [ ] `ProductMetricsRepositoryImpl.upsertIncrements(...)` Native UPSERT 구현
  - `INSERT INTO ... ON DUPLICATE KEY UPDATE` 사용
  - 좋아요 취소 대응을 위한 `GREATEST(like_count + :ld, 0)` 과 동일 레벨의 INSERT/UPDATE 분기 음수 방지 로직 보장
- [ ] 랭킹 점수 로직에서 필요한 `ProductMetricsRepository.snapshotToday(productId)` 조회 쿼리 구현 (지정된 날짜의 bucket 합산 리턴)

### 2. Kafka 배치 리스너 및 트랜잭션 개편 (commerce-streamer)
- [ ] `KafkaConfig` 에 `batchListenerFactory` 적용
- [ ] `CatalogEventConsumer` / `OrderEventConsumer` 에 `@KafkaListener(batch = true)` 적용
  - 메시지를 `List<ConsumerRecord<String, String>>` 형태로 한 번에 읽도록 수정
- [ ] Consumer 내부에서 동일 상품(`productId`)별로 이벤트를 그룹화하고 `MetricDelta` 객체로 합산 압축하는 aggregate 메서드 추가
- [ ] `MetricsEventService.processBatch(Map<Long, MetricDelta> perProduct)` 구조로 개편 및 `@Transactional` 적용
  - 중복 방지(`event_handled`), 복합키 기반 수정(`upsertIncrements`) 연계 확인

### 3. 도메인 랭킹 갱신 로직 (commerce-streamer)
- [ ] 가중치 설정 클래스 `RankingWeights` 생성 (뷰, 좋아요, 주문에 대한 값)
  - `@ConfigurationProperties(prefix = "ranking.weights")` 적용
  - `application.yml` 에 초기값 (`view: 0.1, like: 0.2, order: 0.7`) 세팅
- [ ] 점수 산정 도메인 `RankingScoreCalculator` 구현
  - 모든 지표에 `log1p` 정규화를 적용해 `(w_v*log1p(v)) + (w_l*log1p(l)) + (w_o*log1p(a))` 공식 구현
  - null-safety 고려 (`BigDecimal` 0 대체 등)
- [ ] Redis ZSET 반영 객체 `RankingWriter` (및 `RedisRankingWriter`) 구현
  - `masterRedisTemplate` 주입
  - `ranking.cache.retention` 설정 (2일 만료시간 등) 반영
  - `upsertScore(key, productId, score)` 동작 작성 (단건 원자적 `ZADD` 적용 후 만료시간 부여)
- [ ] `MetricsEventService.processBatch` 내부에 ZSET 갱신 흐름 통합
  - `upsertIncrements` 처리 직후 → `snapshotToday` 로 금일 집계정보 가져옴 → `RankingScoreCalculator` 점수화 → `RankingWriter.upsertScore` 저장

### 4. 콜드 스타트 방지 스케줄러 (commerce-streamer)
- [ ] `RankingCarryOverScheduler` 구현
  - `@Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")` 적용
  - 오늘 `dailyKey` 의 전체 점수를 읽어와 내일자 `dailyKey` 에 `* 0.01` (가중치 1%) 만큼 미리 ZADD 적재 (의존성 무관성 확인된 덮어쓰기 로직)

### 5. API 읽기 지원 (commerce-api)
- [ ] 도메인 패키지 `RankingRepository` (읽기 전용), `RankingKey`, 응답 정보 처리 구조 등 구성
- [ ] `RedisRankingRepository` 읽기 기능 구현
  - `getTopN`: `ZREVRANGE` 로 지정된 page 와 size 기반 조회 후, `RankingEntry`(상품Id, 순위(1-based), 점수) 목록 반환. (순위 식에 유의)
  - `getRank`: `ZREVRANK` 조회로 1-based 순위 보장, 없으면 `null` 리턴.
- [ ] 숨김/삭제 상품 거르기용 `ProductFacade.findVisibleByIds(List<Long>)` 추가
  - `deletedAt IS NULL AND displayYn='Y'` 필터가 적용된 DB 직접 조회 (캐시 우회) 구현
- [ ] `RankingFacade` 구현
  - `getDailyRanking`: `getTopN` 호출 후 취득한 Id 목록으로 `findVisibleByIds` 을 거쳐 보여질 RankingItem 목록을 Map 기반으로 최종 응답 조립 (유효하지 않은 Id 필터링)
  - `getDailyRank(productId)`: 상품 상세 화면에 쓰일 단건의 1-based 순위 조회 제공 (API 트리거 없는 단순 동작)

### 6. Controller 노출 및 문서화 (commerce-api)
- [ ] 랭킹 목록 API `GET /api/v1/rankings` 구현
  - `date(yyyyMMdd)`, `page`, `size` 파리미터 지원 (page 1-based 고려된 내부 0-based 변환 작업 처리)
- [ ] 특정 상품 상세 조회 (`GET /api/v1/products/{id}`) 로직에 순위 포함시키기
  - 앞서 구현한 `RankingFacade.getDailyRank`를 Controller 수준에서 합쳐 `dailyRank` 필드로 반환하도록 응답 DTO 변경

### 7. 통합 및 E2E 테스트 검증
- [ ] 통합 테스트 (Streamer 계층)
  - Kafka를 통해 발생한 이벤트 세트가 `processBatch`를 통해 누락 없이 Redis의 올바른 ZADD 까지 전파되는지
- [ ] E2E 검증 (과제 Checklist 충족 여부 확인)
  - 주문 1건 vs 좋아요 3건 반영 시 점수가 의도된 가중치대로 높은 랭킹을 차지하는가?
  - 일이 넘어가도 ZSET Retention 한도 안에서 이전 날짜의 랭킹 조회가 문제없이 되는가?
  - RankingPage 조회 시 상품 상세 내역도 함께 응답되며(`ProductFacade`), 상품 상세 조회 시 정상적으로 해당 랭크정보를 돌려주는가?

---

## 9. 구현 착수 직전 결정 — R7 파이프라인 비활성화 + R9 전용 테이블 신설

구현 착수 직전 리뷰에서 "기존 `ProductMetrics` 스키마를 복합키로 파괴적 변경" 방향이 재논의되었고, **기존 R7 파이프라인 전체를 주석 처리로 비활성화하고 R9 전용 테이블/컨슈머를 신설**하는 방향으로 최종 확정했다. 8-11 의 체크리스트 중 "스키마 drop & recreate / 기존 테스트 재작성" 에 관련된 항목을 대체한다.

### 배경

8-11 의 1차 확정안은 `ProductMetrics` 를 `(productId, bucket_hour)` 복합키로 변경하고 기존 R7 테스트 3종(`ProductMetricsTest`, `MetricsEventServiceTest`, `MetricsEventServiceIntegrationTest`) 을 재작성하는 것이었다. 그러나 다음 관찰이 있었다.

1. R7 `ProductMetrics` 는 **commerce-streamer 내부에서만 사용** 되고 있으며, 실제로 쓰는 외부 소비자가 없다(8-11 사용처 조사 결과). 즉 기존 테이블을 "계속 살려둘 필요" 도 "교체해서 재활용할 강한 이유" 도 없다.
2. 파괴적 변경 방향은 기존 테스트 3종을 전부 재작성해야 한다 — 변경 리스크가 있고, 기존 R7 동작 이력(git blame/테스트 커버리지) 을 잃는다.
3. R7 파이프라인을 파일 단위로 **주석 보존** 하면 "나중에 비교/부활" 경로가 열려 있고, 컴파일 대상에서 빠져서 빌드 부하도 없다.
4. R9 랭킹 파이프라인은 "시간 버킷 집계 + 주문 금액 + 배치 리스너" 라는 본질적으로 다른 데이터 모델이라, R7 테이블을 재활용하기보다 **독립 테이블이 의미 분리에 자연스럽다**.

### 확정 내용

| 항목 | 결정 |
|------|------|
| **R7 파이프라인** | `CatalogEventConsumer`, `OrderEventConsumer`, `MetricsEventService`, `ProductMetrics`, `ProductMetricsRepository(Impl)`, `ProductMetricsJpaRepository` 6개 파일을 파일 통째 블록 주석(`/* ... */`) 처리 — 상단에 "R9 랭킹 파이프라인으로 대체됨, 히스토리 보존용" 블록 주석 추가 |
| **R7 테스트** | `ProductMetricsTest`, `MetricsEventServiceTest`, `MetricsEventServiceIntegrationTest`, `CatalogEventConsumerTest`, `OrderEventConsumerTest` 5개 테스트 파일도 동일 방식으로 블록 주석 처리 |
| **`MetricsKafkaConfig.SINGLE_LISTENER` 빈** | 파일은 보존, `singleListenerContainerFactory()` 메서드만 주석 처리. 같은 파일에 `RANKING_BATCH_LISTENER` 빈 신설 |
| **R9 전용 엔티티** | `ProductMetricsHourly` (`@Entity`, `(productId, bucketHour)` 복합키 + `orderAmount` `BigDecimal` 추가). `@IdClass(ProductMetricsHourlyId.class)` |
| **R9 전용 테이블** | `product_metrics_hourly` (신설) |
| **R9 전용 Consumer** | `CatalogRankingConsumer`, `OrderRankingConsumer` 신설. 기존 Consumer 는 주석 처리되므로 클래스명 충돌 없음 |
| **Kafka groupId** | 기존 `metrics-group` **재활용**. 기존 Consumer 주석 처리 후 새 Consumer 가 같은 groupId 로 붙어 기존 오프셋을 이어서 소비. producer 측(commerce-api) 은 토픽/키 변경 없음 |
| **`event_handled` 테이블/엔티티** | **그대로 재활용**. 기존 R7 이 사용하던 동일 테이블에 R9 가 이어서 쓴다. 멱등성 보장 목적이 동일 |
| **Kafka 리스너 모드** | 기존 단건(`SINGLE_LISTENER`) → R9 배치(`RANKING_BATCH_LISTENER`) 전환. `ConcurrentKafkaListenerContainerFactory` 에 `setBatchListener(true)`, `MAX_POLL_RECORDS=3000`, `MANUAL` ACK, `ByteArrayJsonMessageConverter` |

### 이 결정이 8-11 에서 바꾸는 것

- ❌ "`ProductMetrics` 스키마를 `(productId, bucket_hour)` 복합키로 전환 (#3)" → **취소**. 기존 `ProductMetrics` 는 파일 주석으로 비활성화되어 건드리지 않는다.
- ❌ "기존 R7 `product_metrics` 데이터는 drop & recreate" → **취소**. 기존 테이블은 `ddl-auto` 정책에 따라 방치 또는 자연 정리된다. 운영 환경에선 수동 drop 이 필요할 수 있으나, 스키마 자체는 유지되어도 무방.
- ❌ "기존 테스트 3종 재작성" → **취소**. 5개 테스트 파일 모두 주석 처리로 보존.
- ✅ 점수 공식(`log1p` + 가중치 0.1/0.2/0.7), 배치 리스너 경로, RankingWriter/Repository 분리, Carry-Over 스케줄러, 읽기 경로(commerce-api), 5-7 단일 2일 retention TTL 등 **나머지 설계는 전부 그대로**. 바뀐 건 "원장 테이블이 기존 것을 재활용하느냐, 새로 만드느냐" 한 지점뿐.

### 네이밍 확정

- 엔티티: `ProductMetricsHourly` / `ProductMetricsHourlyId` (`@IdClass`)
- 테이블: `product_metrics_hourly`
- JPA Repository: `ProductMetricsHourlyJpaRepository`
- 도메인 Repository 인터페이스: `ProductMetricsHourlyRepository`
- 구현체: `ProductMetricsHourlyRepositoryImpl`
- Consumer: `CatalogRankingConsumer`, `OrderRankingConsumer`
- 서비스: `RankingAggregationService`
- Kafka 팩토리 상수: `MetricsKafkaConfig.RANKING_BATCH_LISTENER`
- 패키지: `com.loopers.domain.ranking`, `com.loopers.application.ranking`, `com.loopers.infrastructure.ranking`, `com.loopers.interfaces.consumer` (동일)


---

## 10. 리팩토링 / 개선 (코드 리뷰 반영)

R9 1차 구현 직후 시니어 리뷰에서 도출된 항목들을 P0 → P2 우선순위로 정리해 일괄 반영했다. 각 항목은 "왜 고쳤는가" 와 "어떻게 고쳤는가" 를 함께 남긴다. 모든 변경은 동일 PR 안에서 처리.

### 🚨 P0

#### 10-1. `OrderedProduct` 에 `unitPrice` 추가 — 주문 점수가 항상 0 이 되는 치명적 결함

**왜**
- 기존 `OrderPaidEvent.OrderedProduct` 는 `(productId, quantity)` 만 보유.
- Kafka payload 에도 `unitPrice` 가 직렬화되지 않음 → `BatchAggregator.aggregateOrder()` 가 매번 `decimalOrZero("unitPrice")` 로 0 을 받음 → `MetricDelta.amount = 0` → `product_metrics_hourly.order_amount = 0` → `RankingScoreCalculator` 의 `weights.order * log1p(amount)` 가 항상 0.
- 결과적으로 **점수 공식의 70% 가중치가 죽고**, 체크리스트 §검증 3 ("주문 1건 > 좋아요 3건") 이 절대 만족되지 않는 상태였다.

**어떻게**
- `OrderPaidEvent.OrderedProduct` 에 `int unitPrice` 필드 추가
- `OrderPaymentFacade.toOrderedProductEvents()` 가 `OrderItemInfo.price()` 를 단가로 채워 발행
- 영향 받은 테스트 3종(`KafkaEventPublishListenerTest`, `OrderEventListenerTest`, `UserActionEventListenerTest`) 의 생성자 호출에 단가 인자 추가
- streamer 측 `BatchAggregator` 주석을 "정상 publisher 는 R9 부터 항상 채워 보낸다" 로 정정 (과거 호환 fallback 만 유지)

### ⚠️ P1

#### 10-2. 상품 상세 3개 엔드포인트에 `dailyRank` 일관 적용

**왜**
- `getProductDetail` 에만 `dailyRank` 가 추가되어 있고, `[TEST]` 표시의 `local-cache` / `no-cache` 변형은 누락.
- 체크리스트 §3 "상품 상세 조회 시 해당 상품의 순위가 함께 반환된다" 는 엔드포인트 종류를 가리지 않음.

**어떻게**
- `ProductV1Controller` 의 `getProductWithLocalCache` / `getProductNoCache` 양쪽에서 `rankingFacade.getDailyRank(productId)` 를 호출하고 `ProductDetailResponse.from(info, dailyRank)` 로 응답 조립

#### 10-3. `RankingAggregationService` 트랜잭션 경계 정리 — Redis I/O 를 TX 밖으로

**왜**
- 기존 `@Transactional public void processCatalogBatch()` 안에서 DB UPSERT → snapshot → ZADD → `event_handled` 저장이 모두 한 트랜잭션 안에 묶여 있었다.
- 문제 1: **DB 커넥션을 Redis I/O 시간만큼 점유** (배치 3000건 × 동시성 3 환경에서 풀 압박).
- 문제 2: ZADD 가 TX 커밋 전에 발생 → `saveHandled` 실패 시 DB 는 롤백되지만 Redis 는 이미 갱신된 상태로 남아 짧은 일관성 윈도가 생김.

**어떻게**
- DB 로직(`upsertIncrements` + `snapshotByDate` + `saveHandled`) 을 별도 메서드 `persistDeltas` 로 분리
- Spring `@Transactional` 자기 호출 함정을 피하기 위해 `TransactionTemplate` 을 명시적으로 사용 (`RankingConfig` 에 빈 등록)
- `persistDeltas` 가 (productId → score) 맵을 반환하면, `publishScores` 가 **TX 커밋 이후** ZADD 를 수행
- 단위 테스트(`RankingAggregationServiceTest`) 는 no-op `TransactionTemplate` mock 으로 콜백 즉시 실행하도록 셋업
- 이제 DB 가 항상 원장이며, ZADD 실패는 ack 미전송 → 재배달 → 재계산 → ZADD 자연 복구

#### 10-4. `event_handled.saveAllNew` N+1 INSERT → 다중 row 단일 INSERT

**왜**
- 기존 구현은 `for (eventId : ids) { query.executeUpdate(); }` — 배치 3000건 처리 시 INSERT 도 3000회.
- 배치 압축으로 ZADD 를 1회로 줄이는 의미가 무색해짐 ("DB IO 감소" 라는 배치 도입 목적과 충돌).

**어떻게**
- `EventHandledRepositoryImpl.saveAllNew` 를 `INSERT IGNORE INTO event_handled (event_id, handled_at) VALUES (?, NOW()), (?, NOW()), ...` 다중 row 단일 SQL 로 변경
- 한 SQL 에 묶을 청크 상한 `MAX_BATCH=500` (MySQL `max_allowed_packet` 안전 마진)
- null/blank 필터링은 사전 단계에서 일괄 처리

#### 10-5. `product_metrics_hourly` 스키마 정의 확인

**왜**
- `apps/commerce-api/src/main/resources/schema.sql` 에는 `feature_flag`, `scheduler_lock` 같은 보조 테이블만 정의되어 있고 신규 `product_metrics_hourly` 는 없음.
- 누락 자체는 우려되지만, 기존 R7 의 `product_metrics`/`event_handled` 도 동일 패턴으로 Hibernate `ddl-auto: create` (test/local 프로파일) 에만 의존하고 있어 학습 프로젝트 컨벤션과 정합.

**어떻게**
- 코드 변경 없음. 운영 배포 시 별도 마이그레이션이 필요한 점을 PR 본문에 명시하기로 결정 (week6 ~ week8 의 다른 신규 테이블도 동일)

### 🟡 P2

#### 10-6. R7 레거시 클래스 "주석 보존" 7개 파일 삭제

**왜**
- `MetricsEventService`, `CatalogEventConsumer`, `OrderEventConsumer` 본문 + 4개 테스트 파일이 `package` 선언 + 블록 주석만 남은 빈 파일 형태로 보존되어 있었다.
- CLAUDE.md "removed comments for removed code 같은 backwards-compat hack 금지" 와 정면 충돌. git history 에 모두 남아 있으므로 보존 가치 없음.

**어떻게**
- main 3개 + test 4개, 총 7개 파일 삭제
- `CatalogRankingConsumer` / `OrderRankingConsumer` / `CouponIssueEventService` 의 doc 주석에 남아 있던 `MetricsEventService`, `CatalogEventConsumer`, `OrderEventConsumer` 참조도 정리

#### 10-7. `RankingCarryOverScheduler` idempotency 주석 정정

**왜**
- 기존 주석은 "분산 환경 중복 실행 허용" 만 있어 마치 완전히 idempotent 인 것처럼 보였다.
- 실제로는 두 인스턴스의 `forEachWithScore` 사이에 새 ZADD 가 끼면 시드 값이 미세하게 달라질 수 있다.

**어떻게**
- 주석에 "1% 가중 시드의 노이즈 수준 → 운영 허용", "정확한 분산 단일 실행이 필요하면 `scheduler_lock` 활용" 이라는 트레이드오프를 명시

#### 10-8. `RedisRankingWriter.upsertScore` EXPIRE 빈도 최적화 — 시도 후 원복

**왜 시도했나**
- 매 ZADD 호출마다 EXPIRE 가 동반되어, 배치당 N 회의 추가 Redis I/O 가 발생.
- key 가 date-scoped 라 retention 만료 전에는 EXPIRE 갱신이 불필요.

**어떻게 시도했나**
- 인스턴스 기준 `ConcurrentMap<String, Boolean> expireApplied` 에 키별 최초 1회만 EXPIRE 호출.

**왜 원복했나**
- 통합 테스트(`RankingAggregationServiceIntegrationTest > retentionSet`) 가 Redis flush 후 새로운 키에서 TTL 검증을 수행하는데, JVM-level 마커가 남아 있어 EXPIRE 가 스킵 → TTL 미설정으로 테스트 실패.
- 원래 EXPIRE 자체가 O(1) 이며 ZADD 와 RTT 도 같이 묶이므로 실측 이득이 미미한 반면 테스트 안정성과 운영 직관성을 해친다.
- 결정: 원복 후 매 호출 EXPIRE 유지. 향후 진짜 병목으로 드러나면 Redis 7+ `EXPIRE ... NX` 옵션을 raw command 로 사용하는 방향으로 재검토.

#### 10-9. `MetricDelta.isEmpty` 시멘틱 주석 명확화

**왜**
- like 가 +1/-1 로 상쇄되어 0 이 되면 isEmpty=true → upsert 가 스킵되지만 `event_handled` 에는 기록되는 동작이 코드만 봐서는 직관적이지 않았다.

**어떻게**
- `isEmpty()` 위에 "점수 변화가 없으므로 upsert 스킵, eventId 는 호출자가 `event_handled` 에 저장" 이라는 의도를 주석으로 명시

#### 10-10. `RankingV1Controller` 의 `total` 계산 시 `LocalDate.now()` 제거 → Clock 일원화

**왜**
- controller 에서 `date != null ? date : LocalDate.now()` 로 fallback 했는데, `LocalDate.now()` 는 시스템 기본 zone 사용. `RankingFacade` 는 KST `Clock` 사용 → 시스템 zone 이 KST 가 아닌 환경에서 controller 와 facade 가 다른 일자로 갈라질 가능성.

**어떻게**
- `RankingFacade` 에 `today()` 메서드 추가 (KST 기준 LocalDate 반환)
- controller 가 `effectiveDate = date != null ? date : rankingFacade.today()` 로 통일

#### 10-11. `RankingScoreCalculator` BigDecimal → double 정밀도 주석

**왜**
- `totalOrderAmount` 가 큰 금액일 때 `doubleValue()` 변환에서 정밀도 손실이 발생할 수 있다는 점이 코드만으로는 드러나지 않았다.

**어떻게**
- "log1p 의 비선형 변환 후라 점수 비교의 상대적 순서에는 영향 없음" 이라는 의도를 주석으로 명시

#### 10-12. `WebMvcConfig` 인터셉터 제외 패턴 주석 보강

**왜**
- `/api/v1/rankings` 가 토큰 인터셉터 제외 목록에 추가된 의도가 코드만으로는 불명확.

**어떻게**
- "비로그인 노출 허용 (대기열 토큰 검증 대상 아님)" 주석 추가

### ✅ 테스트 영향 정리

| 항목 | 테스트 영향 | 처리 |
|------|------------|------|
| 10-1 unitPrice | api 측 3개 단위 테스트(생성자 인자 추가) | ✅ 함께 수정 |
| 10-2 dailyRank 일관 | 기존 dailyRank E2E 는 standard endpoint 만 검증 — 영향 없음 | 변경 없음 |
| 10-3 TX 분리 | `RankingAggregationServiceTest` 가 6-arg 생성자 호출 → mock TransactionTemplate 추가 | ✅ 함께 수정 |
| 10-4 multi-row INSERT | EventHandledRepository 직접 단위 테스트 없음 — 통합 테스트가 간접 검증 | 변경 없음 |
| 10-6 R7 파일 삭제 | R7 테스트 4개 파일도 같은 상태(빈 주석 파일) → 함께 삭제 | ✅ 함께 삭제 |
| 10-5, 10-7~10-12 | 주석/내부/시그니처 호환 변경 — 기존 테스트에 영향 없음 | 변경 없음 |

### 영향 받은 파일 요약

```
api/    OrderPaidEvent.java                 (#10-1)
        OrderPaymentFacade.java             (#10-1)
        ProductV1Controller.java            (#10-2)
        WebMvcConfig.java                   (#10-12)
        RankingFacade.java                  (#10-10)
        RankingV1Controller.java            (#10-10)
        KafkaEventPublishListenerTest.java  (#10-1)
        OrderEventListenerTest.java         (#10-1)
        UserActionEventListenerTest.java    (#10-1)

streamer/ RankingAggregationService.java    (#10-3)
          RankingConfig.java                (#10-3)
          BatchAggregator.java              (#10-1 주석)
          MetricDelta.java                  (#10-9)
          RankingCarryOverScheduler.java    (#10-7)
          RankingScoreCalculator.java       (#10-11)
          RedisRankingWriter.java           (#10-8)
          EventHandledRepositoryImpl.java   (#10-4)
          CatalogRankingConsumer.java       (#10-6 주석)
          OrderRankingConsumer.java         (#10-6 주석)
          CouponIssueEventService.java      (#10-6 주석)
          RankingAggregationServiceTest.java(#10-3)

삭제: streamer/
        MetricsEventService.java
        CatalogEventConsumer.java
        OrderEventConsumer.java
        MetricsEventServiceTest.java
        MetricsEventServiceIntegrationTest.java
        CatalogEventConsumerTest.java
        OrderEventConsumerTest.java
```

---
