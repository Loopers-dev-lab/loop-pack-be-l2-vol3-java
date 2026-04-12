# 9주차 학습 정리 - Redis ZSET 기반 실시간 랭킹 시스템

## TL;DR
R7 Kafka 파이프라인 + R8 ZSET 경험을 결합하여, 유저 행동 이벤트 기반의 실시간 상품 랭킹 시스템을 구축한다.

---

## 0. 랭킹이란?

### 개념
랭킹이란 특정 기준(점수, 빈도, 금액 등)에 따라 대상을 순서대로 나열하는 것이다. 단순한 정렬이 아니라, "무엇이 더 인기 있는가", "무엇이 더 가치 있는가"를 사용자에게 전달하는 정보 큐레이션 수단이다.

### 어디에 쓰이는가?
- **홈 화면**: 오늘의 인기 상품 Top 10, 실시간 베스트
- **검색/목록**: 인기순 정렬 옵션
- **상품 상세**: "현재 N위 상품" 표기로 구매 전환율 향상
- **마케팅/운영**: 프로모션 대상 선정, 트렌드 분석

### 랭킹 시스템에 필요한 것들
| 요소 | 설명 |
|------|------|
| **집계 대상** | 어떤 이벤트를 점수에 반영할 것인가 (조회, 좋아요, 주문 등) |
| **점수 산정 방식** | 이벤트별 가중치를 어떻게 부여할 것인가 (Weighted Sum) |
| **시간 범위** | 어느 기간의 데이터를 집계할 것인가 (일간, 주간, 월간) |
| **정렬/조회 성능** | 높은 조회 빈도를 감당할 수 있는 저장소 (Redis ZSET) |
| **갱신 전략** | 실시간 반영 vs 배치 집계, 콜드 스타트 대응 |
| **비정상 접근 필터링** | 어뷰징, 봇 트래픽 등을 집계에서 제외하는 전략 |

### 비정상 접근 필터링
랭킹 점수를 조작하려는 비정상 트래픽(봇, 반복 클릭, 어뷰징 등)을 걸러내야 랭킹의 신뢰성이 유지된다.

**1) 수집 단계 - 사전 차단**
- Rate Limiting: 동일 유저가 같은 상품을 단시간 반복 조회 시 이벤트 발행 자체를 차단
- 인증 기반 필터: 비로그인 트래픽, 봇 User-Agent 등을 이벤트 발행 대상에서 제외

**2) 소비 단계 - Consumer에서 중복 제거**
- Redis Set으로 중복 판별 -> 이미 존재하면 스킵
- 임계치 기반: 특정 유저의 이벤트가 일정 횟수 이상이면 해당 유저 이벤트 전체 무시
- 비로그인 유저 식별: memberId가 없으므로 대체 식별자가 필요

| 식별 방식 | 장점 | 단점 |
|-----------|------|------|
| IP | 서버에서 바로 추출 가능 | 공유 IP(회사, 카페)에서 여러 유저가 동일 IP |
| Device Fingerprint | IP보다 정밀한 식별 | 구현 복잡, 브라우저 정책 제한 |
| Anonymous Cookie/Token | 비로그인에도 세션 단위 식별 가능 | 쿠키 삭제/시크릿 모드에서 무력화 |

- 현실적 조합: **IP + Anonymous Cookie** (쿠키 우선, 없으면 IP fallback)
```
dedup key 예시:
로그인 유저   -> dedup:{date}:{productId}:{memberId}
비로그인 유저 -> dedup:{date}:{productId}:{cookieId 또는 IP}
```
- 비로그인은 좋아요/주문 불가하고 조회 가중치(0.1)가 가장 낮으므로, 적당한 수준의 Rate Limiting으로 충분한 경우가 많다

**3) 점수 반영 단계 - 보정**
- 유저당 기여도 상한(Cap): 한 유저가 같은 상품에 줄 수 있는 점수를 제한 (조회는 하루 1회, 좋아요는 1회 등)
- 로그 스케일 정규화: 비정상적으로 큰 값의 영향을 `log()`로 완화

> 현실적으로 가장 효과적인 조합은 **Rate Limiting(사전) + 유저당 Cap(점수 반영)** 이다.

---

## 1. 학습해야 할 핵심 개념

### 1-1. Redis Sorted Set (ZSET)
- `(member, score)` 쌍을 score 기준 정렬 상태로 유지하는 자료구조
- 삽입/수정: O(logN), Top-N 조회: O(N)
- 주요 명령어
  - `ZADD key score member` : 멤버 추가/갱신
  - `ZINCRBY key increment member` : 점수 증분
  - `ZREVRANGE key 0 N WITHSCORES` : Top-N 조회 (내림차순)
  - `ZREVRANK key member` : 특정 멤버 순위 조회
  - `ZSCORE key member` : 특정 멤버 점수 조회
  - `ZUNIONSTORE` : 키 합산 (콜드 스타트 해결용)
- RDB `GROUP BY + ORDER BY` 대비 장점: 정렬 내장, 실시간 반영, 높은 조회 빈도 감당

### 1-2. Key 설계 - 시간의 양자화
- 누적만 하면 롱테일 현상 발생 (오래된 상품이 상위 독식)
- 일별 키 분리: `ranking:all:{yyyyMMdd}`
- TTL: 시간 윈도우의 1.5~2배 (일간이면 2일)

### 1-3. 가중치 합산 (Weighted Sum)
- 이벤트 종류별 스케일이 다르므로 단순 합산 불가
- 가중치 설계 예시 (합계 = 1)
  - view: W=0.1, Score=1
  - like: W=0.2, Score=1
  - order: W=0.6, Score=price*amount (또는 log 정규화)

### 1-4. 콜드 스타트 문제 및 해결
- 문제: 새 윈도우 시작 시 모든 상품 점수가 0 -> 랭킹 무의미
- 해결: Score Carry-Over
  - `ZUNIONSTORE ranking:all:20250907 1 ranking:all:20250906 WEIGHTS 0.1 AGGREGATE SUM`
  - 전날 점수의 10%를 새 키에 미리 복사

---

## 2. 구현 과제 체크리스트

### Must-Have

#### (1) Kafka Consumer -> Redis ZSET 적재
- [ ] 랭킹 ZSET의 키 전략 구성: `ranking:all:{yyyyMMdd}`
- [ ] TTL 2일 설정
- [ ] 날짜별 키 계산 기능 구현
- [ ] 조회/좋아요/주문 이벤트 소비 시 가중치 적용하여 ZINCRBY로 점수 누적

#### (2) Ranking API 구현
- [ ] `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1` - 랭킹 Page 조회
- [ ] 랭킹 조회 시 상품 ID뿐 아니라 상품 정보도 Aggregation하여 반환
- [ ] 상품 상세 조회 시 해당 상품의 순위 정보 추가 반환 (순위 없으면 null)

### Nice-To-Have
- [ ] 카프카 배치 리스너로 스루풋 향상
- [ ] 1시간 단위 초실시간 랭킹
- [ ] 23시 50분 Scheduler로 Score Carry-Over 실행 (콜드 스타트 완화)
- [ ] Weight 실시간 조절 방안 설계

---

## 3. 검증 항목
- [ ] 이벤트 발행 -> ZSET 점수 반영 -> API 조회 E2E 흐름 확인
- [ ] 일자 변경 후 이전 날짜 랭킹 조회 정상 동작 확인
- [ ] 가중치 반영 순서 확인 (e.g. 주문 1건 > 좋아요 3건)

---

## 4. 전체 아키텍처 흐름

```
[commerce-api]
  -> 유저 행동 이벤트 발행 (조회, 좋아요, 주문)
  -> Kafka

[commerce-collector]
  -> 이벤트 소비
  -> product_metrics upsert (R7 기존)
  -> Redis ZSET 랭킹 점수 갱신 (R9 신규)

[commerce-api]
  -> GET /api/v1/rankings (ZREVRANGE)
  -> 상품 상세에 순위 포함 (ZREVRANK)
```

---

## 5. 실서비스 분석 - 무신사 랭킹 시스템

### 5-1. 랭킹 구조
무신사는 단일 랭킹이 아닌 **3가지 축으로 분리** 운영한다.

| 랭킹 유형 | 설명 |
|-----------|------|
| 상품 랭킹 | 개별 상품 순위 |
| 브랜드 랭킹 | 브랜드 단위 순위 |
| 검색어 랭킹 | 인기 검색어 순위 |

### 5-2. 필터 체계
성별 + 연령대 + 카테고리 조합으로 **세그먼트별 독립 랭킹**을 제공한다.

- **성별**: 전체 / 남성 / 여성
- **연령대**: 전체 / 20대 등
- **카테고리**: 2단계 계층 (상의 > 반팔 티셔츠, 아우터 > 패딩 등)
- **스토어**: 무신사 / 킥스 / 스포츠 / 뷰티 / 부티크 / 아울렛 등 8개 스토어별 독립 랭킹

### 5-3. 랭킹 산출 시그널 (추정)
- **구매 기반 집계**: 배너에 "8만명이 선택한 필수 데님 팬츠" 같은 실매출 수치 노출
- **기간별 집계**: 랭킹 아카이브를 월별(`/ranking/archive?date=YYYYMM`)로 보관
- **클릭 이벤트 수집**: GA4 + Amplitude 이중 트래킹으로 클릭 패턴도 피드백 가능성

### 5-4. 상품별 표시 정보
- 순위 번호 + 순위 변동(상승/하락/신규)
- 브랜드명, 상품명, 정가, 판매가, 할인율
- 소셜 프루프 배너: "N명이 선택한" 형태의 카피

### 5-5. 주목할 UX 패턴

| 패턴 | 설명 |
|------|------|
| **소셜 프루프** | "N명이 선택한" 배너로 구매 전환 유도 |
| **세그먼트 개인화** | 성별+연령 조합으로 "나와 비슷한 사람들의 선택" 맥락 제공 |
| **랭킹 아카이브** | 과거 월별 랭킹 보관으로 트렌드 회고 가능 |
| **멀티 스토어** | 스토어별 독립 랭킹 생태계로 버티컬 탐색 지원 |

### 5-6. 우리 과제에 적용할 수 있는 인사이트
- 무신사는 **구매 데이터 중심**으로 랭킹을 산출 -> 우리 과제에서도 주문 가중치를 가장 높게(0.7) 설정한 것과 방향이 같음
- **기간별 키 분리**(일간/월간)로 운영 -> 우리의 `ranking:all:{yyyyMMdd}` 전략과 유사
- 단순 순위 나열이 아닌 **순위 변동 정보**도 함께 제공 -> Nice-To-Have로 고려해볼 수 있음

---

# 실시간 인기 상품 스코어링 — Weight & Time Decay

주문·조회·좋아요·리뷰 등 유저 시그널에 가중치를 부여하고, 시간 감쇠를 적용해 실시간 인기 상품 랭킹을 계산하는 방법을 다룹니다.

---

## Part 1. 시그널 가중치 (Signal Weight)

"주문 1건과 조회 1건이 같은 가치인가?"라는 질문에서 출발합니다. **전환(Conversion)에 가까운 행동일수록 높은 가중치**를 부여하는 것이 기본 원칙입니다.

### 시그널 가치 순서

| 시그널 | 설명 | 가중치 예시 |
|--------|------|-------------|
| 💳 구매 (Purchase) | 실제 전환이 발생한 최고 가치 시그널 | ×10 |
| 🛒 장바구니 담기 (Add to Cart) | 구매 의도가 명확한 행동 | ×5 |
| ❤️ 좋아요·찜 (Like / Wish) | 관심 표현, 재방문 가능성 | ×3 |
| ✏️ 리뷰 작성 (Review) | 높은 참여도, 사회적 증거 생성 | ×3 |
| 👁️ 상세 조회 (Detail View) | 기본적인 관심 시그널 | ×1 |
| 🔍 검색 노출 (Search Impression) | 패시브 시그널, 최저 가치 | ×0.5 |

> 위 수치는 **휴리스틱(경험 기반) 초기값**의 예시입니다. 실제 서비스에서는 데이터를 보면서 조정합니다.

---

## Part 2. 가중치를 정하는 방법

### 방법 1 — 휴리스틱 (경험 기반) `초기 단계`

구매=10, 장바구니=5, 좋아요=3, 조회=1처럼 직관적으로 설정 후 A/B 테스트로 조정합니다. 데이터가 적은 초기 서비스에서 가장 흔하게 사용하는 방식입니다.

### 방법 2 — 전환율 역산 `추천`

조회 100건 중 구매 평균 2건이면, 조회 대비 구매 가치가 50배라는 논리입니다. 실제 데이터의 전환 비율(funnel ratio)로 가중치를 도출합니다.

### 방법 3 — 데이터 기반 학습 (ML) `고도화`

로지스틱 회귀 등으로 "어떤 시그널 조합이 이후 구매를 가장 잘 예측하는가"를 학습하면 모델의 계수(coefficient)가 곧 가중치가 됩니다.

> **💡 실무 패턴:** 1번으로 시작 → 데이터 축적 → 2번으로 검증 → 트래픽이 충분하면 3번으로 고도화

---

## Part 3. 시간 감쇠 (Time Decay)

"3일 전 주문 100건"과 "오늘 주문 30건" 중 어느 쪽이 **지금** 더 인기 있는가를 반영하기 위한 장치입니다.

### 3-1. 지수 감쇠 (Exponential Decay) — 가장 널리 사용

반감기(Half-life) 개념을 적용하여 시간이 지날수록 점수가 자연스럽게 감소합니다.

**공식:**

```
decayed_score = raw_score × e^(-λt)

λ = ln(2) / half_life
t = 경과 시간
half_life = 24h → 24시간마다 가치가 절반
```

**Python 구현:**

```python
import math

half_life = 24  # 시간 단위
lambda_ = math.log(2) / half_life

score = raw_score * math.exp(-lambda_ * hours_elapsed)
```

### 3-2. 슬라이딩 윈도우 (Sliding Window) — 가장 단순

최근 N시간 내의 시그널만 합산하고, 이전 데이터는 완전히 버립니다. 구현이 쉽지만 **윈도우 경계에서 점수가 급변**하는 단점이 있습니다.

```sql
SELECT product_id, SUM(signal_value) AS score
FROM events
WHERE event_time >= NOW() - INTERVAL '24 hours'
GROUP BY product_id
ORDER BY score DESC;
```

### 3-3. 중력 모델 (Gravity Model) — 커뮤니티 피드용

Hacker News, Reddit 등에서 사용하는 방식입니다. 시간이 지나면 분모가 커지면서 자연스럽게 점수가 하락합니다.

```
score = (votes - 1) / (age_hours + 2) ^ gravity

gravity: 보통 1.5 ~ 1.8
age_hours: 게시 후 경과 시간
```

### 감쇠 방식 비교

| 방식 | 장점 | 단점 | 적합한 곳 |
|------|------|------|-----------|
| 지수 감쇠 | 자연스러운 곡선, 튜닝 용이 | 실시간 재계산 비용 | 이커머스 |
| 슬라이딩 윈도우 | 구현 단순, 직관적 | 경계에서 급변 | 실시간 트렌딩 |
| 중력 모델 | 시간 경과에 따른 자연스러운 하락 | 세밀한 제어 어려움 | 뉴스·커뮤니티 피드 |

---

## Part 4. 실무 통합 구현

시그널별 가중치와 시간 감쇠를 동시에 적용하는 코드입니다.

```python
import math
from datetime import datetime

def calc_popularity(product, now: datetime) -> float:
    signals = [
        (product.orders,    10, product.last_order_at),
        (product.cart_adds,  5, product.last_cart_at),
        (product.likes,      3, product.last_like_at),
        (product.views,      1, product.last_view_at),
    ]

    half_life = 24  # hours
    lambda_ = math.log(2) / half_life
    score = 0.0

    for count, weight, last_at in signals:
        hours = (now - last_at).total_seconds() / 3600
        score += count * weight * math.exp(-lambda_ * hours)

    return score
```

### Redis ZSET에 반영

위 함수로 계산한 결과를 Redis에 주기적으로 넣어서 랭킹을 서빙합니다.

```python
import redis

r = redis.Redis()

for product in products:
    score = calc_popularity(product, now)
    r.zadd("ranking:popular", {product.id: score})

# 상위 50개 조회
top_50 = r.zrevrange("ranking:popular", 0, 49, withscores=True)
```

> **⚠️ 주의:** Redis score는 IEEE 754 double(64비트)입니다. 점수가 매우 크거나 타임스탬프 정밀도가 높아야 하면 정밀도 손실에 주의해야 합니다.

---

## 참고 자료

- Reddit Hot Ranking Algorithm
- Hacker News Ranking Formula
- Wilson Score Interval
- Exponential Decay (Half-life) 개념

---
## 7. 참고 자료
- [RedisGate - SORTED SETS](https://redisgate.kr/redis/command/zsets.php)
- [Spring Data Redis - Redis Template](https://docs.spring.io/spring-data/redis/reference/redis/template.html)
- [Medium - Redis Sorted Set을 이용한 랭킹 관리](https://medium.com/sjk5766/redis-sorted-set%EC%9D%84-%EC%9D%B4%EC%9A%A9%ED%95%9C-%EB%9E%AD%ED%82%B9-%EA%B4%80%EB%A6%AC-38d28712a8b9)


https://oliveyoung.tech/2023-11-07/ranking-system/