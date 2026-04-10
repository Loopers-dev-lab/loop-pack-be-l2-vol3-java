# Round 9 — Show Me The Ranking

## 개요

Round7의 Kafka -> commerce-collector 파이프라인이 수집한 유저 행동 이벤트를 기반으로,
Redis ZSET에 랭킹 점수를 실시간 갱신하고, API가 ZSET을 조회해 랭킹 기능을 제공한다.

```
[commerce-api]
  -> 유저 행동 이벤트 발행 (조회, 좋아요, 주문)
  -> Kafka

[commerce-collector]
  -> 이벤트 소비
  -> product_metrics upsert (R7)
  -> Redis ZSET 랭킹 점수 갱신 (R9)  <-- 이번 주차

[commerce-api]
  -> GET /rankings/top     (ZREVRANGE)
  -> GET /products/{id}/rank (ZREVRANK)
```

---

## 키워드

- Redis Sorted Set (ZSET)
- ZINCRBY 기반 실시간 집계
- Top-N API
- 일별 Key 전략 & TTL
- 가중치 합산 (Weighted Sum)
- 콜드 스타트 문제

---

## 학습 내용

### 1. Ranking 시스템 특성

- **Top-N API**: 홈 메인 인기 상품, 오늘의 Top 10, 인기순 정렬 등 — 항상 높은 조회 빈도
- **개별 순위 조회**: 특정 상품이 현재 몇 위인지 표기
- **주기적 갱신**: 일간/주간/월간 단위로 리셋 (이번 라운드는 일간만)
- **콜드 스타트 문제** 존재
- **RDB로 해결하기 어려운 이유**: `GROUP BY + ORDER BY`는 데이터가 쌓일수록 느려지고, 높은 조회 빈도에 DB 과부하

### 2. Redis ZSET

- **(member, score)** 쌍을 score 기준 정렬 상태로 유지
- 삽입/수정: O(log N), Top-N 조회: O(N)
- 주요 연산:
  - `ZADD key score member` — score와 함께 member 저장 (이미 있으면 갱신)
  - `ZREVRANGE key 0 N WITHSCORES` — score 기준 Top-N 조회
  - `ZREVRANK key member` — 특정 멤버의 순위 조회
  - `ZSCORE key member` — 특정 멤버의 스코어 조회
  - `ZCARD key` — 멤버 수 조회

**다른 방식과 비교:**

| 방법 | 장점 | 단점 | 적합도 |
|------|------|------|--------|
| DB ORDER BY | 정합성 높음 | 느림, 부하 높음 | 초기/소규모 |
| 캐시(Map) + 정렬 | 간단 | 매 요청마다 정렬 필요 | 중간 |
| Redis ZSET | 빠른 정렬 내장, 다양한 조회 | 메모리 사용 높음 | 대규모 트래픽 |

### 3. Key 설계 — 시간의 양자화

**누적만 할 경우의 문제:**
- 오래 전 점수를 쌓은 상품이 계속 상위 노출 -> 신상품 노출 기회 상실
- 롱테일(Long Tail) 현상 — 소수 상품이 상위권 독식
- 시간 단위 집계로 공정성 확보 + 신선한 정보 노출 필요

**일별 키 분리:**
```
rank:all:20250906   // 9월 6일 랭킹 집계
rank:all:20250907   // 9월 7일 랭킹 집계
```

**TTL:** 시간 윈도우의 1.5배~2배

### 4. 가중치 합산 (Weighted Sum)

**필요한 이유:**
- 좋아요/구매/매출액은 스케일이 달라 단순 합산 시 특정 지표가 지배
- 서비스 전략에 따라 중요 지표가 달라짐

**총점식:**
```
Sum(p) = W(like) * Count(p.like) + W(order) * Count(p.order) + W(view) * Count(p.view)
```

**기본 가중치 (총합 = 1.0):**

| 지표 | 가중치 | 근거 |
|------|--------|------|
| view | 0.1 | 조회 수가 가장 많아 전체 스코어를 지배할 수 있으므로 낮게 |
| like | 0.2 | 구매 결정 관점에서 주문보다 덜 중요 |
| order | 0.7 | 유저가 구매를 결정한 가장 중요한 지표 |

### 5. 콜드 스타트 문제

**문제:**
- 집계 윈도우 시작 시점에 점수가 없어 랭킹 정보 부재
- 전날 인기 상품도 0점에서 시작
- 랭킹 미진입 상품 -> 클릭/구매 발생 안 함 -> 악순환

**해결 — Score Carry-Over:**
- 새 키 생성 시 전날 점수의 일부를 작은 가중치로 복사
- 가중치를 작게 잡아 오늘의 점수가 빠르게 역전 가능하도록 함

```
ZUNIONSTORE ranking:all:20250907 1 ranking:all:20250906 WEIGHTS 0.1 AGGREGATE SUM
```

Before (20250906): `product:101 -> 100`, `product:202 -> 50`
After (20250907, carry-over 10%): `product:101 -> 10`, `product:202 -> 5`

---

## 요약

| 항목 | 설명 |
|------|------|
| 랭킹의 목적 | 유저에게 인기 상품을 효율적으로 노출 (Top-N, 개별 순위) |
| 핵심 기술 | Redis ZSET (정렬 내장 + O(logN) 삽입/수정) |
| 데이터 소스 | R7의 Kafka -> collector 파이프라인이 수집한 유저 행동 이벤트 |
| Key 설계 | 일별 키 분리 (rank:all:yyyyMMdd) + TTL로 메모리 관리 |
| 가중치 합산 | 시그널별 가중치를 곱해 단일 스코어로 합산 |
| 콜드 스타트 | Score Carry-Over (전일 점수 일부 복사)로 완화 |
| R7/R8과의 관계 | R7 collector가 이벤트 -> ZSET 갱신, R8 ZSET 경험을 랭킹에 재활용 |

---

## 참고 자료 & 레퍼런스 분석

### 과제 제공 레퍼런스

- Redis Sorted Sets: https://redisgate.kr/redis/command/zsets.php
- Spring Data Redis Template: https://docs.spring.io/spring-data/redis/reference/redis/template.html
- 올리브영 랭킹 시스템 개편기: https://oliveyoung.tech/2023-11-07/ranking-system/

### 추가 조사 레퍼런스

| 글 | 핵심 내용 | 우리 과제 적용 포인트 |
|----|----------|---------------------|
| [Redis 공식 Leaderboard Tutorial](https://redis.io/tutorials/howtos/leaderboard/) | Java 코드 예시, 일별 키, TTL, 페이지네이션, 배치 Pipeline | ZINCRBY + ZREVRANGE 구현, 페이지네이션 공식 |
| [Capped Leaderboard (daily.dev)](https://daily.dev/blog/creating-a-capped-leaderboard-with-redis-sorted-set-secondary-index-and-lua) | Lua 스크립트로 상위 N개만 유지, 보조 인덱스 패턴 | 메모리 관리, ZSET + Hash 분리 설계 |
| [Redis Sorted Sets Leaderboards (OneUptime)](https://oneuptime.com/blog/post/2026-01-25-redis-sorted-sets-leaderboards/view) | 시간 윈도우별 키 패턴, TTL 전략, 복합 점수 동점 처리 | 키 네이밍 확장, 시간 단위 랭킹 설계 |

### 올리브영 기술 블로그 분석

올리브영은 Oracle 프로시저 기반 랭킹을 **AWS Glue + Athena + Step Function** 배치 ETL로 개편했다.
우리 과제와는 접근 방식이 근본적으로 다르다 (배치 ETL vs 실시간 ZSET).

**공통점**: 기존 DB 프로시저/쿼리 방식의 한계 인식 — 반복 집계, 확장성 부족, 산출 근거 파악 어려움
**차이점**: 올리브영은 AWS 매니지드 서비스 기반 배치, 우리는 Kafka + Redis ZSET 기반 실시간

### 레퍼런스에서 얻은 설계 인사이트

**1) 페이지네이션 — ZREVRANGE 오프셋 계산**

```
start = (page - 1) * size
end = start + size - 1
ZREVRANGE ranking:all:{date} start end WITHSCORES
```

과제 API `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1`에 직접 적용 가능.

**2) 상품 정보 Aggregation — ZSET + DB 조합**

ZSET에는 productId만 저장하고, API 응답 시 DB에서 상품 정보를 조회하여 합쳐 반환한다.
Redis 공식 가이드의 Hash + ZSET 패턴과 동일한 개념이나,
우리는 상품 정보가 MySQL에 있으므로 ZSET 조회 -> productId 목록 -> DB IN 쿼리로 처리.

**3) 동점 처리 — 타임스탬프 인코딩**

```
compositeScore = baseScore + (1 - timestamp / 10_000_000_000)
```

같은 점수면 먼저 달성한 상품이 상위. 현재 과제 요구사항에는 명시되지 않았으나,
ZINCRBY 방식에서는 동점 시 ZSET의 기본 동작(사전식 순서)에 의존하게 된다.

**4) 메모리 관리 — Capped ZSET**

상품 수가 많아질 경우 `ZREMRANGEBYRANK ranking:all:{date} 0 -(N+1)`로 하위 항목을 주기적으로 제거.
항목당 ~50 bytes 기준, 상품 10만 개 = ~5MB. 상위 1,000개만 유지하면 ~50KB.

**5) 배치 Pipeline — Redis 왕복 최소화**

현재 MetricsConsumer가 3,000건 배치로 Kafka를 소비하므로,
Redis Pipeline으로 여러 ZINCRBY를 한 번에 전송하면 네트워크 왕복을 줄일 수 있다.

**6) 시간 윈도우 키 확장 패턴**

```
ranking:all:daily:{yyyyMMdd}        TTL: 2일
ranking:all:hourly:{yyyyMMddHH}     TTL: 3시간
```

Nice-to-Have "시간 단위 랭킹"을 키 네이밍만 확장하여 자연스럽게 구현 가능.

**7) 콜드 스타트 — 레퍼런스 공백**

조사한 3개 레퍼런스 모두 콜드 스타트를 다루지 않는다.
과제 문서의 ZUNIONSTORE carry-over 방식이 현재 유일한 레퍼런스.
실무에서 이 방식이 표준적인지, 다른 접근이 있는지는 확인 필요.

---

## 다음 주차 예고

일간 집계를 넘어 주간/월간 집계를 만드는 방법. 점차 많아지는 데이터/통계를 주기적으로 생성하는 기능.

---

## 구현 과제 (Implementation Quest)

### Must-Have

#### (1) Kafka Consumer -> Redis ZSET 적재

- 조회/좋아요/주문 이벤트를 컨슘하여 일간 키(`ranking:all:{yyyyMMdd}`)의 ZSET에 점수 누적
- 이벤트별 Weight & Score:

| 이벤트 | Weight | Score | 비고 |
|--------|--------|-------|------|
| 조회 | 0.1 | 1 | |
| 좋아요 | 0.2 | 1 | |
| 주문 | 0.6 | price * amount | 정규화 시 log 적용 가능 |

- ZSET 스펙:
  - **TTL**: 2일
  - **KEY**: `ranking:all:{yyyyMMdd}`

#### (2) Ranking API 구현

- **랭킹 Page 조회**: `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1`
- **상품 상세 조회 시 해당 상품의 랭킹 정보 추가**

### Nice-to-Have

- 시간 단위(초 실시간) 랭킹 만들기
- 콜드 스타트 문제 해결 (Score Carry-Over)
- 카프카 배치 리스너 (단건 처리 대신 배치로 ZSET/DB 연산 최적화, 스루풋 향상)

### Additionals

- 실시간 Weight 조절 — 점수 계산 가중치를 동적으로 수정하는 방법
- 1시간 단위 랭킹 — 일간이 아닌 시간 윈도우 랭킹
- 콜드 스타트 Scheduler — 23:50에 Score Carry-Over로 다음 날 랭킹판 사전 생성

---

## 체크리스트

### Ranking Consumer

- [ ] 랭킹 ZSET의 TTL, 키 전략을 적절하게 구성
- [ ] 날짜별 적재 키를 계산하는 기능
- [ ] 이벤트 발생 후 ZSET에 점수가 적절하게 반영

### Ranking API

- [ ] 랭킹 Page 조회 시 정상적으로 랭킹 정보 반환
- [ ] 랭킹 Page 조회 시 상품 ID가 아닌 상품 정보가 Aggregation되어 제공
- [ ] 상품 상세 조회 시 해당 상품의 순위가 함께 반환 (순위에 없으면 null)

### 검증

- [ ] 이벤트 발행 -> ZSET 점수 반영 -> API 조회 E2E 흐름 정상 동작
- [ ] 일자 변경 후에도 이전 날짜의 랭킹 조회 정상 동작
- [ ] 가중치 적용이 의도대로 랭킹 순서에 반영 (e.g. 주문 1건 > 좋아요 3건)

---

## Technical Writing Quest

### 작성 기준

| 항목 | 설명 |
|------|------|
| 형식 | 블로그 |
| 길이 | 제한 없음, 단 1줄 요약(TL;DR) 포함 |
| 포인트 | "무엇을 했다"보다 "왜 그렇게 판단했는가" 중심 |
| 예시 | 코드 비교, 흐름도, 리팩토링 전후 등 자유 |
| 톤 | 실력은 보이지만 자만하지 않고, 고민이 읽히는 글 |

### 글감 제안

- 누적 랭킹만 유지하면 왜 롱테일 문제가 발생할까?
- 시간의 양자화 — 왜 필요한가?
- 콜드 스타트(0점에서 시작) 문제를 어떻게 풀 수 있을까?
- 우리의 랭킹 지표 구성 — 진짜 인기 있는 상품이란?
- 실시간 랭킹, 이렇게 풀면 쉽다
- 상품 10만 개일 때 ZSET 메모리는? 상위 N개만 유지하면?
- Top-N을 매번 ZREVRANGE로 조회 vs 주기적 캐싱의 트레이드오프

---

## 구현 상세

### Ranking Consumer (commerce-streamer)

**구현 파일:**
- `application/ranking/MetricsDelta.java` — MetricsConsumer 내부 클래스에서 추출
- `application/ranking/RankingProperties.java` — 가중치 외부화 (`@ConfigurationProperties`)
- `application/ranking/RankingScoreUpdater.java` — HINCRBY→ZADD 파이프라인

**MetricsDelta 패키지 이동:**
원래 `MetricsConsumer`의 private inner class였던 `MetricsDelta`를 `application.ranking` 패키지로 추출.
- 이유: `RankingScoreUpdater`(application 레이어)가 `MetricsDelta`를 매개변수로 받는데,
  `interfaces.consumer` 패키지에 두면 application → interfaces 역방향 의존이 발생한다.
- `application.ranking`에 두면 `MetricsConsumer`(interfaces) → `MetricsDelta`(application) 순방향 의존이 유지된다.

**MetricsConsumer 확장 (Phase 3):**
기존 Phase 1(멱등성 체크 + deltaMap 집계) → Phase 2(DB UPSERT) 이후에
Phase 3(Redis 랭킹 갱신)을 추가.
- **동일 deltaMap 재사용**: Phase 1에서 productId별로 집계한 `Map<Long, MetricsDelta>`를 Phase 2(DB UPSERT)와 Phase 3(Redis ZSET)이 공유. 이벤트를 두 번 파싱하지 않는다.
- **격리**: Phase 3은 Phase 2의 `transactionTemplate` 밖에서 별도 try-catch로 실행. Redis 장애가 DB 커밋에 영향 주지 않음.

**이중 집계 구조 — 데이터 정합성 전략:**
같은 이벤트를 product_metrics(DB, 전체 누적 원장)와 ranking:*(Redis, 일간 집계 실시간 뷰) 두 저장소에 동시 적재한다.
- Phase 2(DB)와 Phase 3(Redis)는 실행 순서는 있지만 트랜잭션을 공유하지 않는다
- Phase 2 성공 + Phase 3 실패 → DB 정확, Redis 일시 부정확 → 다음 배치에서 자연 복구
- Phase 2 실패 → 트랜잭션 롤백, Phase 3도 스킵 → Kafka 오프셋 미커밋 → 재처리
- **원장 기반 재집계 미구현 결정**: product_metrics는 전체 누적이라 일간 delta 추출 불가. 일간 랭킹은 TTL 2일의 휘발성 데이터이므로, Redis 장애 복구 후 이벤트 유입으로 자연 재생성하는 것이 적합
- 향후 주간/월간 배치 집계(Round 10)에서 product_metrics 원장이 활용될 예정

**HINCRBY→ZADD 방식 선택 (vs ZINCRBY):**
- ZINCRBY는 delta score를 증분하므로, 가중치 변경 시 과거 적재분을 보정할 수 없다
- HINCRBY로 Hash에 원본 메트릭(viewCount, likeCount, salesCount, salesAmount)을 일간 누적 → 리턴값으로 composite score 재계산 → ZADD로 덮어쓰기
- 부동소수점 누적 오차 방지, 가중치 변경 시 재계산 용이

**Redis 키 전략:**

| 키 | 패턴 | 예시 | 용도 |
|----|------|------|------|
| ZSET | `ranking:all:{yyyyMMdd}` | `ranking:all:20260410` | 일간 composite score |
| Hash | `ranking:metrics:{yyyyMMdd}:{productId}` | `ranking:metrics:20260410:101` | 일간 원본 메트릭 4필드 |

- TTL: 172,800초 (2일). Pipeline 내 EXPIRE로 매 배치 설정 (O(1), 별도 EXISTS 불필요)
- 시간대: KST (`ZoneId.of("Asia/Seoul")`) — 자정 기준 일간 윈도우
- 날짜 포맷: `DateTimeFormatter.BASIC_ISO_DATE` → `20260410`
- 키 생성은 `LocalDate`를 매개변수로 받는 순수 함수로 구현 → 테스트 가능
- 공개 상수: `RANKING_ZSET_PREFIX`, `RANKING_METRICS_PREFIX`, `RANKING_TTL_SECONDS`
- Hourly 키 확장(`ranking:all:hourly:{yyyyMMddHH}`)은 Nice-to-Have로 이번 구현에서 제외

**Score 계산 공식:**
```
score = 0.1 × log₁₀(viewCount + 1)
      + 0.2 × log₁₀(likeCount + 1)
      + 0.7 × log₁₀(salesAmount + 1)
      + productId × 1e-10
```
- 모든 메트릭에 log₁₀ 정규화: 조회 수(수만)와 좋아요(수십)의 절대값 스케일 차이 완화
- `+1`은 log₁₀(0) = -∞ 방지, `max(0, value)` 적용으로 음수(취소 초과) 방어
- 가중치 합 = 1.0 (view 0.1 + like 0.2 + order 0.7)
- 가중치는 `@ConfigurationProperties`로 외부화 → yml 변경으로 런타임 조정 가능

**음수 메트릭 방어:**
- HINCRBY 리턴값이 음수가 될 수 있음 (취소가 생성보다 먼저 도착한 경우)
- `max(value, 0)` 적용 후 log 계산
- 음수 감지 시 WARN 로그 (productId 포함)

**Master 전용 쓰기:**
- `@Qualifier("redisTemplateMaster")`로 Master 노드에만 쓰기 수행
- modules/redis가 제공하는 `defaultRedisTemplate`은 `ReadFrom.REPLICA_PREFERRED`이므로 쓰기에 부적합
- 향후 Ranking API(읽기)에서는 `defaultRedisTemplate`(Replica 우선)을 사용할 예정

**Pipeline 구현 상세:**
- `SessionCallback` + `executePipelined`로 원자적 파이프라인 실행
- **Pipeline 1 (HINCRBY)**: productId당 4 HINCRBY + 1 EXPIRE
  - Hash 필드: `viewCount`, `likeCount`, `salesCount`, `salesAmount`
  - HINCRBY 리턴값 = 누적치. 리턴 순서에 의존하여 파싱 (`base = i × 5`)
- **Pipeline 2 (ZADD)**: 누적치로 score 재계산 → productId별 ZADD + ZSET 키 1회 EXPIRE
- 3,000건 배치에서 인기 상품 ~100개에 집중 시: Pipeline 1(HINCRBY 400 + EXPIRE 100) + Pipeline 2(ZADD 100 + EXPIRE 1)
- Redis 왕복 2회로 최소화

**단위 테스트 (`RankingScoreUpdaterTest`) — 26개 전체 PASS:**

| 카테고리 | 테스트 수 | 검증 내용 |
|----------|-----------|-----------|
| 기본 score 계산 | 4 | 모든 메트릭 0 → score 0.0, 단일 지표별 정확한 수치 (e.g. view=99 → 0.1×log₁₀(100)=0.2) |
| 가중치 순서 | 3 | 주문 1건(10000원) > 좋아요 3건, like > view (같은 count), 복합 score 정확도 |
| log₁₀ 정규화 | 2 | view 10배 차이 → score 1.5배 미만, salesAmount 100배 차이 → score 2배 미만 |
| 음수 방어 | 5 | 개별/전체 음수 → 0 클램핑, 음수 항이 양수 항의 score를 침범하지 않음 |
| 커스텀 가중치 | 1 | view 가중치 0.7로 변경 시 view score > order score 확인 |
| 키 생성 | 7 | ZSET/Hash 키 포맷, 날짜 변경 시 다른 키, productId별 분리, prefix 상수 일치, TTL=172800초 |
| 타이브레이커 | 4 | 동점 시 신상품 우선, 주 score 역전 불가, ε 안전성, ε 상수 검증 |

### 콜드 스타트 Carry-Over 스케줄러 (commerce-streamer)

**구현 파일:**
- `application/ranking/RankingCarryOverScheduler.java` — 23:50 KST 스케줄 실행

**문제:**
일간 키 전환(자정) 시 새 ZSET이 비어있어 00:00~01:00 사이 랭킹 정보가 없다.
이벤트가 쌓이기 전까지 사용자에게 빈 랭킹이 노출되는 콜드 스타트 문제.

**해결 — Score Carry-Over:**
- 23:50 KST에 `ZUNIONSTORE ranking:all:{tomorrow} 1 ranking:all:{today} WEIGHTS 0.1` 실행
- 오늘 ZSET의 모든 score를 10%로 축소하여 내일 ZSET에 시드
- 내일 실제 이벤트가 들어오면 `RankingScoreUpdater`의 HINCRBY→ZADD가 carry-over score를 덮어쓰므로, carry-over는 자연스럽게 퇴장한다

**Hash는 복사하지 않는 이유:**
- Hash는 HINCRBY 원본 메트릭 저장소이다. carry-over 대상이 아님
- carry-over된 상품에 새 이벤트가 없으면 Hash 없이 ZSET score(10%)만 남아 순위에 표시
- 새 이벤트가 들어오면 Hash가 0부터 시작하여 HINCRBY→ZADD로 실제 score가 덮어씀

**실행 시점 — 23:50인 이유:**
- 자정(00:00)에 실행하면 이미 콜드 스타트 발생 후
- 23:50이면 10분의 여유로 carry-over 완료 후 자정을 맞이
- ZUNIONSTORE는 atomic이므로 23:50 시점의 스냅샷이 복사됨 (마지막 10분 이벤트 누락은 허용)

**설정 외부화:**
- `ranking.carry-over-rate: 0.1` — yml에서 비율 조정 가능
- `RankingProperties` record에 `carryOverRate` 필드 추가
- `@EnableScheduling`을 `CommerceStreamerApplication`에 추가

**ZUNIONSTORE 구현:**
- Spring Data Redis `opsForZSet().unionAndStore(todayKey, emptyList, tomorrowKey, Aggregate.SUM, Weights.of(rate))`
- otherKeys는 빈 리스트 (소스 키 1개만 사용)
- EXPIRE로 내일 키에도 TTL 172,800초 설정

**장애 대응:**
- try-catch로 감싸 Redis 장애 시 ERROR 로그만 기록
- carry-over 실패해도 비즈니스에 치명적이지 않음 — 자정 이후 실제 이벤트가 쌓이면 랭킹 복구

**테스트 설계:**
- `carryOver(LocalDate today)` 메서드 분리로 `@Scheduled`에 의존하지 않고 임의 날짜 테스트 가능

**단위 테스트 (`RankingCarryOverSchedulerTest`) — 4개 전체 PASS:**

| 테스트 | 검증 내용 |
|--------|-----------|
| ZUNIONSTORE 파라미터 | todayKey, emptyList, tomorrowKey, Aggregate.SUM, Weights.of(0.1) |
| TTL 설정 | 내일 키에 172,800초 EXPIRE |
| 장애 격리 | Redis 예외 시 예외를 삼키고 전파하지 않음 |
| 연말 키 전환 | 2026-12-31 → 2027-01-01 키 생성 정확 |

### 동점 처리 — productId 기반 타이브레이커

**동점 발생 조건:**
동일한 메트릭 조합을 가진 상품이 존재하면 주 score가 동점이 된다.
초기/carry-over 직후에 발생 가능성이 높고, 일과 시간에는 이벤트 누적으로 자연 해소.

**ZSET 동점 기본 동작:**
score 동일 시 member의 사전식(lexicographic) 순서로 정렬.
productId가 숫자이므로 사전식 순서는 비즈니스 의미 없음 (e.g. "99" > "202" > "101").

**대안 비교:**

| 대안 | 장점 | 단점 |
|------|------|------|
| 아무것도 안 함 (ZSET 기본) | 단순 | 동점 시 순서가 자의적 (사전식) |
| 타임스탬프 인코딩 | 먼저 달성한 상품 우선 | score에 두 가지 의미 혼합, 디버깅 어려움 |
| salesCount 인코딩 | 비즈니스 의미 있음 | salesAmount가 이미 주 score에 반영 → 같은 시그널의 이중 반영 |
| **productId 인코딩** | 신상품에 노출 기회 부여, 주 score와 다른 차원 | productId가 auto-increment가 아닌 경우 무의미 |

**결정: productId × ε(1e-10)를 score에 인코딩하여 ZSET 레벨에서 동점 해소.**

근거:
- salesCount는 이미 salesAmount를 통해 주 score에 반영 → 타이브레이커에 다시 쓰면 이중 반영
- 동점인 상품 중 높은 productId(=최근 등록 신상품)가 상위 → 미시적 콜드 스타트 완화
- productId는 auto-increment이므로 높을수록 최근 등록. Phase 3에서 이미 보유하여 추가 조회 불필요
- 주 score(조회/좋아요/매출)와 완전히 다른 차원의 보정이라 정보가 중복되지 않음

**ε(엡실론) 산정:**
- 주 score 최소 유의미 차이: view 0→1 = `0.1 × log₁₀(2) = 0.0301`
- productId 현실적 상한: 10,000,000 (천만)
- `ε = 1e-10` → productId 천만일 때 보정값 0.001 → 주 score 차이(0.0301)의 3.3%
- Redis double(64bit IEEE 754) 유효 자릿수 15~16자리에서 충분히 표현 가능

**최종 수식:**
```
score = 0.1 × log₁₀(viewCount + 1)
      + 0.2 × log₁₀(likeCount + 1)
      + 0.7 × log₁₀(salesAmount + 1)
      + productId × 1e-10
```

**구현 변경:**
- `RankingScoreUpdater.TIEBREAKER_EPSILON = 1e-10` 상수 추가
- `calculateScore(viewCount, likeCount, salesAmount, productId)` — productId 매개변수 추가
- `pipelineZadd()`에서 entry.getKey()(productId)를 calculateScore에 전달

**단위 테스트 (타이브레이커) — 4개 전체 PASS:**

| 테스트 | 검증 내용 |
|--------|-----------|
| 동점 시 신상품 우선 | 동일 메트릭 + productId 101 vs 505 → 505(신상품)가 상위 |
| 주 score 역전 불가 | view=2/pid=101 vs view=1/pid=999999 → 주 score가 높은 쪽이 상위 |
| ε 안전성 | productId 1000만이어도 주 score 최소 차이의 5% 미만 |
| ε 상수 | `TIEBREAKER_EPSILON == 1e-10` |

### 장애 시나리오 분석

**장애 포인트 분류:**
```
쓰기 경로:
  Kafka → MetricsConsumer → [Phase 1: DB 멱등성] → [Phase 2: DB upsert] → [Phase 3: Redis 적재]
                                                                              ↑ 장애 포인트
읽기 경로:
  유저 → RankingController → RankingFacade → [RankingRedisRepository → Redis Replica]
                                                        ↑ 장애 포인트
```

**쓰기 경로 — Phase 3 Redis 장애:**
- Phase 3을 try-catch로 격리 (이미 구현). DB 커밋과 ack.acknowledge()는 Phase 3 성공 여부와 무관
- 재시도 불필요: 다음 배치의 HINCRBY가 누적 delta를 반영하고, score 재계산이 Hash 전체 상태 기반이므로 정합성 유지
- Consumer 재시작/리밸런싱: event_handled INSERT IGNORE 멱등성으로 중복 처리 방지

**Redis 복구 후 데이터 정합성:**

| 시나리오 | 결과 | 복구 방법 |
|---------|------|----------|
| Hash 유실 + ZSET 유실 | 빈 랭킹 | 이벤트 유입으로 Hash/ZSET 자연 재생성 (수 분~수 시간) |
| Hash 유실 + ZSET 잔존 | ZSET score가 오래된 값 | 새 이벤트의 HINCRBY→score 재계산→ZADD로 갱신. 단, 장애 전 누적분 유실 |
| Hash 잔존 + ZSET 유실 | 랭킹 목록 없음 | 새 이벤트의 score 재계산→ZADD로 ZSET 재생성 |

모든 경우 "새 이벤트가 들어오면 자연 복구"된다. Hash가 SSOT이므로, Hash만 있으면 score를 언제든 재계산 가능.
단, Hash까지 유실된 경우 장애 전 일간 누적 메트릭 복원 불가 — Redis를 랭킹 유일 저장소로 쓰는 한 불가피한 트레이드오프.

**읽기 경로 — Redis Replica 장애 시 대응 결정:**

| 대안 | 적합성 | 선택 여부 |
|------|--------|----------|
| 빈 응답 반환 | UX 저하 | 미채택 |
| **503 에러 응답** | 클라이언트가 재시도 판단 가능 | **채택** |
| DB fallback (product_metrics ORDER BY) | 일간이 아닌 전체 누적 → 데이터 의미 불일치 | 미채택 |
| 로컬 캐시 fallback | 현재 요구사항 대비 과도한 복잡도 | 미채택 |

근거: 랭킹은 핵심 비즈니스(주문/결제)가 아니므로 일시적 503 허용 가능. DB fallback은 "일간 랭킹"과 "전체 누적"이라는 데이터 의미가 달라 오히려 혼란.

**상품 상세의 랭킹 정보 — 부분 장애 허용:**
- 상품 상세 API에서 랭킹 조회 실패 시, 상품 정보는 정상 반환하고 ranking=null로 처리
- 상품 상세는 핵심 기능이므로 부가 정보(랭킹) 실패가 전체 응답을 실패시키면 안 됨

**장애 대응 요약:**

| 장애 | 경로 | 영향 | 대응 | 복구 |
|------|------|------|------|------|
| Redis Master 장애 | 쓰기 | 랭킹 갱신 중단 | Phase 3 try-catch 격리, DB 정상 | 복구 후 자연 재생성 |
| Redis Replica 장애 | 읽기 | 랭킹 API 503 | 에러 응답 | Replica 복구 시 즉시 정상화 |
| Consumer 재시작 | 쓰기 | 수 초 지연 | 멱등성으로 중복 방지 | 자동 |
| Hash/ZSET 유실 | 양쪽 | 일간 데이터 유실 | 이벤트 유입으로 점진 재생성 | 수 분~수 시간 |
| 상품 상세 랭킹 조회 실패 | 읽기 | 랭킹 필드 null | try-catch, 상품 정보 정상 반환 | 자동 |

### Ranking Read Path (commerce-api)

**구현 파일:**
- `infrastructure/ranking/RankingRedisRepository.java` — Redis ZSET 읽기 전용 어댑터
- `interfaces/api/ranking/RankingDto.java` — 랭킹 API 응답 DTO
- `application/ranking/RankingFacade.java` — 랭킹 유스케이스 조율
- `interfaces/api/ranking/RankingController.java` — `GET /api/v1/rankings`
- `interfaces/api/product/ProductDto.java` — `ranking` 필드 추가 (nullable)
- `application/product/ProductFacade.java` — `lookupRanking()` 추가

**RankingRedisRepository — Replica 읽기:**
- `defaultRedisTemplate`(Replica 우선) 주입 — 쓰기(commerce-streamer)와 분리된 읽기 경로
- 3개 메서드: `getTopN(date, start, end)` → ZREVRANGE WITHSCORES, `getRankAndScore(date, productId)` → ZREVRANK + ZSCORE, `getTotalCount(date)` → ZCARD
- `getTopN`은 `List<RankingEntry>` record로 반환 — Facade가 Spring Data Redis의 `TypedTuple`에 의존하지 않도록 변환
- `getRankAndScore`는 0-based reverseRank에 +1하여 1-based rank 반환
- ZREVRANK null → 랭킹 미진입 → null 반환

**RankingFacade — ZSET→DB 2단계 조회:**
1. date null → 오늘(KST) 기본값 적용
2. Redis ZREVRANGE로 페이지 범위의 `List<RankingEntry>` 조회
3. productId 목록으로 DB `findAllByIds` IN 쿼리 (Product + Brand JOIN)
4. ZSET 순서를 유지하면서 상품 정보 merge → RankingResponse 리스트 반환

**Top 100 제한:**
- `MAX_RANKING_SIZE = 100` 상수로 총 항목 수를 cap
- ZSET에 수천 상품이 있어도 API는 상위 100개만 노출
- 페이지 요청이 100 넘으면 빈 페이지 반환

**장애 처리 — 503 에러:**
- Redis 조회를 try-catch로 감싸 `CoreException(INTERNAL_ERROR)` 발생
- 랭킹은 핵심 비즈니스가 아니므로 일시적 503 허용 (장애 시나리오 섹션 결정 사항)
- DB 조회는 Redis 성공 후 실행되므로 Redis 장애 시 DB 부하 없음

**ProductRepository.findAllByIds — 랭킹용 IN 쿼리:**
- `ProductJpaRepository`에 `@Query` 추가: `SELECT p, b.name FROM Product p LEFT JOIN Brand b ...  WHERE p.id IN :ids`
- `ProductRepositoryImpl`에서 빈 리스트 방어 후 `toProductWithBrand()` 재사용
- `FakeProductRepository`에도 동일 시그니처 구현 (테스트 호환)

**ProductDto.ProductResponse — ranking 필드 추가:**
- `RankingDto.RankingInfo ranking` 필드 (nullable)
- `from()` 팩토리 메서드들은 `null` 전달 (일반 목록 조회 시 랭킹 불필요)
- `withRanking(RankingInfo)` 메서드로 캐시된 응답에 랭킹 정보 부착

**RankingDto.RankingInfo:**
- `rank, score, date` 3필드 — 상품 상세 응답에 "이 랭킹이 어느 날짜 기준인지" 포함
- 설계 문서 섹션 7.2 응답 구조 준수

**ProductFacade.lookupRanking — 부분 장애 허용:**
- `getProductDetailCached()`에서 상품 정보 조회 후 `lookupRanking()` 호출
- 오늘 날짜(KST)로 ZREVRANK + ZSCORE 조회, `RankingInfo`에 date도 함께 전달
- Redis 장애 시 catch하여 WARN 로그 + `ranking=null` 반환 → 상품 상세는 정상 응답
- `RankingRedisRepository`가 null 주입(테스트)이어도 NPE를 catch하여 안전

**RankingController:**
- `GET /api/v1/rankings?date=yyyyMMdd&page=0&size=20`
- `date` 파라미터 optional — 생략 시 RankingFacade에서 오늘(KST) 기본값 적용
- 기존 `ApiResponse<T>` 래퍼 사용, `ProductController` 패턴 준수
- page 기본값 0, size 기본값 20

**Rank 계산:**
- 1-based rank = `page * size + 1`부터 시작
- ZREVRANGE가 반환하는 순서(score 내림차순)를 그대로 유지
- 삭제된 상품은 DB 조회 결과에 없으므로 응답에서 자동 제외 (rank 번호는 순차 증가)

**테스트 호환성 수정 (4개 파일):**
- `CaffeineProductCacheAdapterTest` — `ProductResponse` 생성자에 `null`(ranking) 추가
- `MultiLayerProductCacheAdapterTest` — `detailResponse()` 헬퍼에 `null`(ranking) 추가
- `ProductFacadeTest` — `ProductFacade` 생성자에 `null`(RankingRedisRepository) 추가
- `FakeProductRepository` — `findAllByIds()` 구현 추가

### product_metrics 스키마 재설계 (commerce-streamer)

**변경 동기:**
기존 `product_metrics`는 `product_id`를 PK로 전체 기간 누적만 저장했다. 세 가지 문제가 있었다:
1. **시간 축 부재** — 일별 트렌드 분석 불가, Redis 장애 시 일간 재집계 불가
2. **취소 이력 소실** — `sales_count += -3` 방식은 원래 몇 건이었는지 복원 불가
3. **데이터 정리 불가** — 행이 하나뿐이라 오래된 데이터 purge 불가

**변경 파일:**
- `application/ranking/MetricsDelta.java` — 7개 additive DB 필드 + Redis net delta 파생 getter
- `interfaces/consumer/MetricsConsumer.java` — Phase 1 이벤트 매핑 + Phase 2 UPSERT SQL
- `application/ranking/RankingScoreUpdater.java` — pipelineHincrby에서 net getter 사용
- `domain/metrics/ProductMetrics.java` — JPA 엔티티 (DDL 생성용)
- `domain/metrics/ProductMetricsId.java` — 복합 PK용 `@IdClass` (신규)

**스키마 변경 (AS-IS → TO-BE):**
```
AS-IS: PK = (product_id), 4 컬럼 (like_count, view_count, sales_count, sales_amount)
TO-BE: PK = (product_id, metric_date), 7 컬럼 + idx_metric_date 인덱스
```
- 그레인(Grain) = `daily × product` — 한 행이 "특정 상품의 특정 날짜 메트릭"을 의미
- 모든 컬럼이 Additive(양수 누적)이므로 어떤 차원으로든 `SUM` 가능

**MetricsDelta 재설계 — DB 표현 기반 + Redis 파생:**
핵심 결정: MetricsDelta가 DB의 7개 additive 컬럼을 기본 필드로 저장하고, Redis用 net delta는 파생 getter로 제공한다.

```
DB (Phase 2) 필드:           Redis (Phase 3) 파생 getter:
  viewDelta         ──→       getViewDelta()          (그대로)
  likeDelta         ──→       getNetLikeDelta()       = likeDelta - unlikeDelta
  unlikeDelta       ──→       (DB 전용)
  salesCountDelta   ──→       getNetSalesCountDelta() = salesCountDelta - cancelCountDelta
  salesAmountDelta  ──→       getNetSalesAmountDelta()= salesAmountDelta - cancelAmountDelta
  cancelCountDelta  ──→       (DB 전용)
  cancelAmountDelta ──→       (DB 전용)
```

이 설계의 장점:
- 하나의 deltaMap으로 Phase 2와 Phase 3이 각자 필요한 getter만 호출
- merge() 시 모든 필드가 단순 덧셈으로 합산되어 정합성 보장
- Redis의 net 의미론(`like - unlike`)이 DB 필드에서 자연스럽게 유도됨

**이벤트별 factory 메서드 매핑:**

| 이벤트 | factory 메서드 | DB 필드 | Redis net delta |
|--------|---------------|---------|----------------|
| LIKE_CREATED | `ofLike()` | likeDelta=1 | netLike=+1 |
| LIKE_REMOVED | `ofUnlike()` | unlikeDelta=1 | netLike=-1 |
| PRODUCT_VIEWED | `ofView()` | viewDelta=1 | view=+1 |
| ORDER_CREATED | `ofSales(count, amount)` | salesCount/Amount | netSales=+count/amount |
| ORDER_CANCELLED | `ofCancel(count, amount)` | cancelCount/Amount | netSales=-count/amount |

**MetricsConsumer Phase 1 변경:**
- `LIKE_CREATED`: `ofLike(1)` → `ofLike()` (인자 제거)
- `LIKE_REMOVED`: `ofLike(-1)` → `ofUnlike()` (별도 factory)
- `ORDER_CANCELLED`: `ofSales(-count, -amount)` → `ofCancel(count, amount)` (양수 전달)

**MetricsConsumer Phase 2 변경:**
```sql
-- AS-IS: PK = product_id, 4 컬럼
INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount)
VALUES (?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE like_count = like_count + VALUES(like_count), ...

-- TO-BE: PK = (product_id, metric_date), 7 컬럼
INSERT INTO product_metrics
  (product_id, metric_date, view_count, like_count, unlike_count,
   sales_count, sales_amount, cancel_count, cancel_amount)
VALUES (?, CURDATE(), ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
  view_count = view_count + VALUES(view_count), ...
```
- `CURDATE()`로 일별 파티셔닝 — 같은 상품이라도 날짜가 다르면 다른 행
- INSERT의 9개 파라미터: productId + 7개 DB delta 값

**RankingScoreUpdater 변경 (Phase 3):**
- `getLikeDelta()` → `getNetLikeDelta()` (Redis HINCRBY에는 net값 전달)
- `getSalesCountDelta()` → `getNetSalesCountDelta()`
- `getSalesAmountDelta()` → `getNetSalesAmountDelta()`
- `getViewDelta()`는 변경 없음 (view에는 취소 개념 없음)

**ProductMetrics JPA 엔티티:**
- `@IdClass(ProductMetricsId.class)` 복합 PK: `(productId, metricDate)`
- 7개 메트릭 컬럼 + `@Index(name = "idx_metric_date")`
- `updatedAt` 컬럼 제거 (새 스키마에서는 metric_date가 시간 축 역할)
- 이 엔티티는 DDL 자동 생성(`ddl-auto: create`)용이며, MetricsConsumer는 JdbcTemplate으로 직접 SQL 실행

**follow-up 필요:**
- `MetricsReconcileTasklet`(commerce-batch)의 네이티브 SQL도 새 스키마에 맞게 수정 필요 — 이번 라운드에서는 설계 문서 범위(섹션 11.1) 외이므로 별도 작업으로 기록

### Late-Arriving Fact 이중 기록 (commerce-streamer, commerce-api)

**설계 근거:**
ORDER_CANCELLED는 주문일과 다른 날짜에 발생한다 (예: 4/1 주문 → 4/5 취소).
인식일(CURDATE) 기준으로만 기록하면 4/1의 순매출을 계산할 때 취소분이 4/5 행에만 존재하여 정합성이 깨진다.
설계 문서 섹션 2.5 "설계 원칙 2" — 인식일 + 발생일 이중 기록으로 해결.

**product_metrics 컬럼 변경:**
```
AS-IS:
  cancel_count       INT NOT NULL DEFAULT 0
  cancel_amount      BIGINT NOT NULL DEFAULT 0

TO-BE:
  cancel_count_by_event_date    INT NOT NULL DEFAULT 0    -- 인식일 기준
  cancel_amount_by_event_date   BIGINT NOT NULL DEFAULT 0
  cancel_count_by_order_date    INT NOT NULL DEFAULT 0    -- 발생일(원주문일) 기준
  cancel_amount_by_order_date   BIGINT NOT NULL DEFAULT 0
```

**변경 파일:**
- `domain/metrics/ProductMetrics.java` — 엔티티 컬럼 rename + 2개 추가
- `application/order/OrderFacade.java` (commerce-api) — ORDER_CANCELLED 이벤트에 `originalOrderDate` 필드 추가
- `interfaces/consumer/MetricsConsumer.java` — Phase 2 이중 UPSERT 구현

**OrderFacade 변경:**
- `cancelOrder()`에서 `order.getCreatedAt().toLocalDate().toString()`으로 원주문일 추출
- 이벤트 payload Map에 `"originalOrderDate"` 필드 추가

**MetricsConsumer 이중 UPSERT — 방법 B 채택:**

방법 비교:
| 방법 | 설명 | 장점 | 단점 |
|------|------|------|------|
| A | MetricsDelta에 originalOrderDate 필드 추가 | 단일 구조 | deltaMap.merge에서 날짜 충돌 |
| **B** | **별도 LateArrivingCancel 리스트** | **deltaMap 구조 불변, 관심사 분리** | **추가 리스트 관리** |
| C | Phase 2에서 원본 records 재순회 | 코드 변경 최소 | Phase 2에서 JSON 재파싱 필요 |

**방법 B 채택 근거:**
- MetricsDelta는 Semantic Definition(의미적 정의)이다. 필드명 `cancelCountDelta`는 "취소 delta"라는 의미이지, DB 컬럼명(`cancel_count_by_event_date`)과 1:1 대응이 아니다
- Phase 2 UPSERT가 MetricsDelta의 의미를 DB 컬럼에 매핑하는 책임을 갖는다
- MetricsDelta를 변경하지 않으므로 기존 Phase 3(Redis)에 영향 없음

**구현 상세:**
```
Phase 1: processRecord() 내부
  ORDER_CANCELLED 수신 시:
    1. deltaMap.merge(productId, ofCancel(count, amount))  ← 기존과 동일
    2. lateArrivingCancels.add(LateArrivingCancel(productId, orderDate, count, amount))  ← 추가

Phase 2: transactionTemplate 내부
  1. 인식일 UPSERT (기존 로직, 컬럼명만 변경):
     INSERT INTO product_metrics (..., cancel_count_by_event_date, cancel_amount_by_event_date)
     VALUES (?, CURDATE(), ...) ON DUPLICATE KEY UPDATE ...
  2. 발생일 UPSERT (신규):
     INSERT INTO product_metrics (product_id, metric_date, cancel_count_by_order_date, cancel_amount_by_order_date)
     VALUES (?, ?, ?, ?)  -- metric_date = originalOrderDate
     ON DUPLICATE KEY UPDATE cancel_count_by_order_date += ..., cancel_amount_by_order_date += ...
```

**하위 호환성:**
- `originalOrderDate` 미포함 이벤트(구버전)는 인식일 UPSERT만 실행, 발생일 UPSERT 스킵
- 파싱 실패 시 warn 로그 + 인식일 UPSERT는 정상 실행 (장애 격리)

**정합성 검증 SQL:**
```sql
-- 충분히 긴 기간으로 합산하면 두 기준의 합계가 같아야 함
SELECT SUM(cancel_count_by_order_date) AS by_order,
       SUM(cancel_count_by_event_date) AS by_event
FROM product_metrics WHERE product_id = ?;
```

**테스트 (`MetricsConsumerTest`) — 6개 전체 PASS:**

| 테스트 | 검증 내용 |
|--------|-----------|
| cancelledEvent_dualUpsert | ORDER_CANCELLED 이벤트에 인식일+발생일 이중 UPSERT 실행 |
| cancelledEvent_noOriginalOrderDate_singleUpsert | originalOrderDate 없으면 인식일만 실행 |
| crossDateCancel_twoDistinctUpserts | 인식일 SQL은 CURDATE() 사용, 발생일 SQL은 파라미터 전달 |
| invalidOriginalOrderDate_eventDateUpsertStillWorks | 파싱 실패 시 인식일 UPSERT 정상 실행 |
| orderCreated_noByOrderDateUpsert | ORDER_CREATED는 발생일 UPSERT 미실행 |
| productViewed_upsertContainsViewCount | PRODUCT_VIEWED는 view_count UPSERT 정상 실행 |

### Lambda Architecture 배치 보정 잡 (commerce-batch)

**설계 근거:**
실시간 경로(Kafka → Redis)는 이벤트 유실, 처리 순서, 부동소수점 누적 오차 등으로 DB 원장과 드리프트가 발생할 수 있다.
설계 문서 섹션 2.5 "설계 원칙 4" + 섹션 11.3 — Lambda Architecture의 배치 레이어가 1시간 주기로 DB 원장 기준 Redis를 덮어쓴다.

**구현 파일:**
- `batch/job/rankingcorrection/RankingCorrectionJobConfig.java` — chunk-oriented 배치 잡
- `batch/job/rankingcorrection/RankingCorrectionProperties.java` — 가중치 설정 레코드
- `application.yml` — `ranking.weights.*` 추가

**chunk-oriented 처리 선택 이유:**
기존 배치 잡은 모두 Tasklet 패턴이지만, 이 잡은 "DB 읽기 → Score 계산 → Redis 쓰기" 흐름이므로 chunk-oriented가 적합:
- Reader: JdbcCursorItemReader — `idx_metric_date` 인덱스 활용, 메모리 효율적
- Writer: Redis Pipeline으로 chunk(1,000건) 단위 일괄 적재
- 상품 수가 증가해도 메모리 사용량이 chunk 크기에 비례하여 안정적

**DB 원장 조회 SQL:**
```sql
SELECT product_id, view_count,
  (like_count - unlike_count) AS net_like,
  sales_count,
  (sales_amount - cancel_amount_by_event_date) AS net_sales_amount
FROM product_metrics
WHERE metric_date = CURDATE()
```
- `net_like = like_count - unlike_count` → DB에서 net 계산
- `net_sales_amount = sales_amount - cancel_amount_by_event_date` → 인식일 기준 취소 반영

**Redis 덮어쓰기 (Pipeline):**
```
chunk 단위 Pipeline:
  productId마다:
    DEL ranking:metrics:{date}:{pid}          -- 기존 Hash 삭제 (stale 필드 방지)
    HSET ranking:metrics:{date}:{pid} viewCount ... likeCount ... salesCount ... salesAmount ...
    ZADD ranking:all:{date} {score} {pid}     -- score 덮어쓰기
    EXPIRE ranking:metrics:{date}:{pid} 172800
  마지막:
    EXPIRE ranking:all:{date} 172800
```

**Score 수식 — RankingScoreUpdater와 동일 (Semantic Definition):**
```
score = W(view) × log₁₀(viewCount + 1)
      + W(like) × log₁₀(netLike + 1)
      + W(order) × log₁₀(netSalesAmount + 1)
      + productId × 1e-10
```
- 가중치: `ranking.weights.*` yml 설정에서 읽음 (streamer와 동일 값)
- 키 prefix, TTL, date format: RankingScoreUpdater의 상수와 동일 값을 배치 잡에서 재정의
- `max(0, value)` 음수 클램핑 동일 적용

**실행 방식:**
```bash
java -jar commerce-batch.jar --spring.batch.job.name=rankingCorrectionJob
```
- 외부 스케줄러(Kubernetes CronJob 등)로 1시간 주기 실행
- `@ConditionalOnProperty` 패턴으로 기존 배치 잡과 동일한 구조

**Race Condition 안전성:**
- 배치 실행 중 실시간 이벤트가 Redis에 HINCRBY→ZADD로 기록될 수 있음
- 배치의 ZADD는 DB 원장 기준 score를 "덮어쓰기"하므로, 실시간 이벤트의 미세한 delta가 유실될 수 있음
- 허용 범위: 최대 1 chunk(1,000건) 처리 시간 동안의 이벤트 delta. 다음 실시간 이벤트에서 HINCRBY→ZADD로 복구됨

**테스트 (`RankingCorrectionScoreTest`) — 7개 전체 PASS:**

| 카테고리 | 테스트 수 | 검증 내용 |
|----------|-----------|-----------|
| Score 수식 일치 | 5 | 모든 메트릭 0, view/like/order 단독, 복합 score — RankingScoreUpdater와 동일 결과 |
| 음수 방어 | 2 | netLike/netSalesAmount 음수 → 0 클램핑 |
| 타이브레이커 | 1 | 동점 시 높은 productId 상위 |
