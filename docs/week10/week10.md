# 학습내용
## 🧭 루프팩 BE L2 - Round 10

> 서비스에서 다양한 가치를 창출하기 위해 대량의 데이터를 모으고, 쌓고, 압착해야 합니다. 데이터의 규모가 커지면, 점점 이런 작업들을 웹 애플리케이션 내에서 처리하는 것에 대한 부하가 가파르게 높아집니다.

그래서 우리는 마지막으로 `spring-batch` 애플리케이션을 만들어 볼 거예요. 이를 기반으로 일간 랭킹 뿐 아닌 주간, 월간 랭킹 또한 집계를 활용해 만들어 봅시다.
>

<aside>
🎯

**Summary**

</aside>

지난 라운드에서 Kafka Consumer 와 Redis ZSET 을 활용해 메세지를 압착해 처리량을 높이는 테크닉, 특정 점수 기준의 정렬 SET 활용 방법을 학습하고 실시간으로 갱신되는 일단위 랭킹을 만들어보았습니다.

이번 라운드에서는 Spring Batch 를 이용해 주간, 월간 랭킹을 구현합니다. **Batch** 는 일간 집계를 기반으로 주간, 월간 집계를 만들어내고 **API** 는 일간 랭킹 뿐 아니라 주간, 월간 랭킹도 제공합니다.

<aside>
📌

**Keywords**

</aside>

- Spring Batch (Job / Step / Chunk / Tasklet)
- ItemReader / ItemProcessor / ItemWriter
- Materialized View (사전 집계)
- 실시간 처리 vs 배치 처리

<aside>
🧠

**Learning**

</aside>

## 🧮 Bacth System

<aside>
💡

**Batch Processing** 이 왜 필요할까요? 한번 예

- **대규모 집계**
    - 수억 건 데이터에 대한 합산, 평균, 통계는 실시간으로 처리하기엔 비용이 너무 크다.
    - e.g. "지난 한 달간 상품별 매출 TOP 100" → 매 요청마다 계산하면 DB/Redis 부하로 서비스 전체가 흔들림
- **운영 리포트/통계**
    - 경영진 보고용, BI 툴, 월간 정산 등은 수 초 단위의 실시간성이 필요하지 않음
    - 정확성과 대량처리가 더 중요 → 하루 한 번 배치로 계산해도 충분
- **데이터 정제 및 적재**
    - 로그 수집 → 정제 → DW 적재 같은 과정은 실시간보다는 일정 주기 단위로 몰아서 처리하는 게 효율적
</aside>

### 🎞️ 실무에서 자주 보는 배치 시나리오

- **주문 정산**
    - 주문/결제/환불 데이터를 모아 매일 새벽 3시 정산 테이블 생성.
    - PG사 매출/정산 금액 검증도 함께.
- **랭킹/통계 적재**
    - 일간/주간/월간 인기 상품 집계
    - 카테고리별 판매량 통계
- **데이터 정리/청소**
    - 만료된 쿠폰 삭제, 오래된 로그 제거, 캐시 초기화
- **데이터 웨어하우스(DW) 적재**
    - 서비스 DB → DW(BigQuery, Redshift 등) 로 적재 후 분석

### ⚖️ 실시간 vs 배치 트레이드오프

| 항목 | 실시간 처리 | 배치 처리 |
| --- | --- | --- |
| 장점 | 즉각 반영 → UX 좋음 | 대규모 집계, 비용 효율적 |
| 단점 | 인프라 복잡, 멱등성 관리 필요 | 지연 발생, 실시간성 부족 |
| 적합 | 좋아요 수, 실시간 랭킹 | 월간 리포트, 대시보드, BI |
| 초점 | **신속성** | **정확성 & 효율성** |

---
# 구현과제
## 🏗️ Spring Batch

### 💧 **기본 구성 요소**

- **Job** : 배치 실행 단위 (예: “일간 주문 통계 Job”)
- **Step** : Job 을 구성하는 세부 단계

### 📌 배치 처리 모델

**Chunk-Oriented Processing**

- 데이터 읽기 (Reader) → 가공 (Processor) → 저장 (Writer)
- 청크 단위로 트랜잭션이 관리됨 → 안정적 대량 처리

```java
@Bean
public Step orderStatsStep(
  JobRepository jobRepository,
  PlatformTransactionManager txManager,
  ItemReader<Order> reader, 
  ItemProcessor<Order, OrderStat> processor,
  ItemWriter<OrderStat> writer
) {
    return new StepBuilder("orderStatsStep", jobRepository)
        .<Order, OrderStat>chunk(1000, txManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
}
```

**장점**

- 대규모 집계/정산/데이터 변환에 적합
- 트랜잭션 단위 조절 가능

---

**Tasklet**

- Step = 하나의 작업(Task) 실행
- 반복 구조 없음, 단발성 작업에 적합

```java
@Bean
public Step cleanupStep(
	JobRepository jobRepository,
	PlatformTransactionManager txManager
) {
    return new StepBuilder("cleanupStep", jobRepository)
      .tasklet((contribution, chunkContext) -> {
          orderRepository.deleteOldOrders(); // 만료 주문 삭제
          return RepeatStatus.FINISHED;
      }, txManager)
      .build();
}
```

**장점**

- 간단한 SQL 실행, 파일 이동, 캐시 초기화 등에 적합
- Reader/Processor/Writer 필요 없는 작업에 깔끔

> *일반적으로 **구현의 용이성** 등을 이유로 Tasklet 내에서 로직 상으로 Chunk Oriented Processing 을 구현하기도 합니다.*
>

---

### 🗼 Materialized View

<aside>
💡

**다시 돌아왔다, Materialized View**

이전에 **Join 한계를 극복하기 위한 조회 전용 구조**로서 `Materialized View` 에 대해 언급되었던 적이 있었습니다.

이번엔 **복잡한 집계 쿼리를 극복하기 위한 조회 전용 구조**로서 `Materialized View` 를 만나볼 거예요.

</aside>

- **복잡한 집계 쿼리를 미리 계산해둔 조회 전용 구조**
- MySQL 은 MV 기능이 별도로 없으므로 보통 **별도 테이블 + 배치 적재** 방식 사용
- 주기적으로 대규모 데이터 (각 상품의 일별 일간 집계) 를 주기적으로 집계해 활용

```sql
CREATE TABLE product_metrics_weekly ( // 주간 상품 이벤트 집계
  product_id BIGINT PRIMARY KEY,
  like_count INT,
  order_count INT,
  view_count INT,
  yearMonthWeek VARCHAR, // 예시입니다.
  updated_at DATETIME
);

CREATE TABLE product_metrics_monthly ( // 주간 상품 이벤트 집계
  product_id BIGINT PRIMARY KEY,
  like_count INT,
  order_count INT,
  view_count INT,
  yearMonth VARCHAR, // 예시입니다.
  updated_at DATETIME
);
```

---

### 🎯 운영 관점에서의 배치 전략

- **스케줄링** : Spring Scheduler, Quartz 혹은 인프라 (Cron + K8s)
- **재실행 전략** : 실패 시 부분 롤백 vs 전체 재실행
- **병렬 Step** : 여러 Step 을 동시에 실행해 성능 향상
- **모니터링** : 실행 로그, 실패 알림, 처리 건수 추적

---

<aside>
📚

**References**

</aside>

| 구분 | 링크 |
| --- | --- |
| 🔍 Spring Batch | [Spring Docs - Spring Batch](https://docs.spring.io/spring-batch/reference/) |
| ⚙ Spring Boot with Spring Batch | [Baeldung - Spring Boot with Spring Batch](https://www.baeldung.com/spring-boot-spring-batch) |
| 📖 Materialized View | [AWS - What is Materialized View](https://aws.amazon.com/ko/what-is/materialized-view/) |

<aside>
🌟

**Mentor’s Message**

</aside>

이번 10주 동안 우리는 **단순한 CRUD를 넘어서, 실제 서비스에서 마주치는 문제들을 단계적으로 풀어왔습니다**. 현업에서 여러분들이 활약하기 위해 어떤 것들을 알면 좋을지, 문제를 접근하고 해석하는 방법, 문제에 맞는 적절한 해답을 도출하는 방법 등을 전달하려고 노력했어요.

- **1~3주차** : 도메인 모델링, 계층 분리, 객체 협력 설계
- **4~6주차** : 트랜잭션과 동시성, 읽기 최적화, 외부 시스템(결제 PG) 연동과 회복 탄력성
- **7주차** : 이벤트 와 Kafka, 유량제어
- **8주차** : 대기열 큐
- **9주차** : 실시간 집계, 랭킹 시스템 구축
- **10주차** : 배치와 Materialized View를 통한 대규모 집계와 조회 최적화

즉, **이커머스라는 시나리오를 통해 → 설계 → 동시성 → 성능 → 회복력 → 이벤트 → 확장성 → 데이터 파이프라인 → 집계** 까지, 실무에서 다루는 거의 모든 챕터를 작은 스케일로 경험해 본 셈입니다.

하지만 여기서 끝이 아닙니다.

- 실제 서비스는 **더 많은 데이터와 트래픽, 더 복잡한 요구사항** 속에서 움직입니다.
- 새로운 기능을 추가할 때마다, 이번 과정에서 배운 **Trade-off와 선택의 기준**이 반복해서 필요합니다.
- 이직, 프로젝트, 사이드 개발 등 어떤 길을 가더라도, 지금 경험한 **문제 정의 → 분석 → 해결** 과정은 계속해서 쓰이게 될 것이고 힘이 되어줄 겁니다.



이제는 여러분이 스스로 문제를 정의하고, 배운 도구와 방법을 적용하며, 더 깊은 학습으로 나아갈 차례입니다.

루프팩 BE L2는 끝났지만, **여러분의 성장 여정은 여기서부터가 시작**입니다.

---
# 📝 Round 10 Quests

---

## 💻 Implementation Quest

> 이번에는 Spring Batch 를 활용해 주간, 월간 랭킹을 제공해 볼 거예요.
이전에 적재했던 `product_metrics` 와 같은 일간 집계정보를 기반으로 **주간, 월간 랭킹 시스템을 구축**해봅니다.
>

<aside>
🎯

**Must-Have (이번 주에 무조건 가져가야 좋을 것-**무조건 ****하세요**)**

- Spring Batch
- Batch Processing
- Materialized View (Statistics)
</aside>

### 📋 과제 정보

이번 주는 대규모 데이터 집계 및 조회 전용 구조에 대한 설계를 진행해 봅니다.

### (1) Spring Batch Job 구현

- 하루치 메트릭 테이블을 읽어 데이터를 집계하고 처리해봅니다.
    - 대상 테이블 : `product_metrics`
    - Chunk-Oriented 방식을 통해 대량의 데이터를 읽고 처리할 수 있도록 구성해 보세요.

### (2) Materialized View 설계

- 집계 결과를 조회 전용 테이블 (MV) 로 저장합니다.
    - `mv_product_rank_weekly` : 주간 TOP 100 랭킹
    - `mv_product_rank_monthly` : 월간 TOP 100 랭킹

### (3) Ranking API 확장

- 기존 Ranking 을 제공하는 GET `/api/v1/rankings?date=yyyyMMdd&size=20&page=1` 에서 기간 정보를 전달받아 API 로 일간, 주간, 월간 랭킹을 제공할 수 있도록 개선합니다.

---

## ✅ Checklist

### 🧱 Spring Batch

- [ ]  Spring Batch Job 을 작성하고, 파라미터 기반으로 동작시킬 수 있다.
- [ ]  Chunk Oriented Processing (Reader/Processor/Writer or Tasklet) 기반의 배치 처리를 구현했다.
- [ ]  집계 결과를 저장할 Materialized View 의 구조를 설계하고 올바르게 적재했다.

### 🧩 Ranking API

- [ ]  API 가 일간, 주간, 월간 랭킹을 제공하며 조회해야 하는 형태에 따라 적절한 데이터를 기반으로 랭킹을 제공한다.

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

### 🎯 Retrospective

- 단순히 “무엇을 했다”가 아니라, **10주 동안 어떻게 성장했는지**를 돌아본다.
- “기능 구현” 중심이 아니라, **사고방식/문제 해결/설계 선택 과정** 중심으로 기록한다.
- 이 글은 **개인 포트폴리오**이자, 앞으로 학습 방향을 스스로 점검하는 기준점이 된다.

### 담으면 좋은 내용

1. **전체 여정 요약**
    - 1~10주차 동안 다뤘던 주요 테마 및 문제점들을 간단히 돌아보기
    - 단순 나열이 아니라, **흐름이 어떻게 연결되었는지** 를 강조
2. **가장 큰 전환점**
    - **내 기존의 사고방식이 바뀌었다** 싶은 순간
    - *예: 4주차 트랜잭션/락을 통해 단순 @Transactional 이상의 고민을 알게 된 점, 7주차 이벤트 분리를 통해 ‘확장성’에 눈을 뜬 경험*
3. **나의 Trade-off 판단**
    - 실습 중 내가 내린 중요한 선택 1~2개
    - 왜 그 선택을 했고, 대안은 뭐였는지, 지금 다시 한다면 어떻게 할 건지
4. **실전과의 연결**
    - “이건 실제 회사/서비스에서 써먹을 수 있겠다” 싶은 포인트
    - *예: 캐시 무효화 전략, Kafka 기반 집계, Resilience4j 설정 등*





---

---

# 🧠 구현 전 알아야 할 개념 정리

## 1. 현재 코드베이스 구조 파악

### 데이터 소스: `product_metrics_hourly`

배치의 읽기 대상 테이블. `commerce-streamer`의 `ProductMetricsHourly` 엔티티가 매핑되어 있다.

```
product_metrics_hourly
├── product_id     BIGINT        (복합PK)
├── bucket_hour    DATETIME      (복합PK — 시간 단위로 절삭된 LocalDateTime)
├── view_count     BIGINT
├── like_count     BIGINT
├── order_count    BIGINT
├── order_amount   DECIMAL(19,2)
└── updated_at     DATETIME
```

- 하루치 = `bucket_hour` 기준 `2024-01-01 00:00` ~ `2024-01-01 23:00` (24개 row)
- 주간 집계 = 7일치 bucketHour 범위 필터
- 월간 집계 = 해당 월 전체 bucketHour 범위 필터

### 기존 일간 랭킹 흐름

```
commerce-streamer → product_metrics_hourly (DB 적재)
                  → Redis ZSET (ranking:all:yyyyMMdd) 실시간 갱신

commerce-api → Redis ZSET 조회 → GET /api/v1/rankings?date=yyyyMMdd
```

### 주간/월간 랭킹 흐름 (이번에 구현)

```
commerce-batch → product_metrics_hourly 읽기
              → 집계 계산
              → mv_product_rank_weekly / mv_product_rank_monthly 저장

commerce-api → MV 테이블 조회 → GET /api/v1/rankings?date=yyyyMMdd&period=weekly
```

---

## 2. Spring Batch ItemReader 종류

| ItemReader | 특징 | 적합한 상황 |
|-----------|------|-----------|
| `JpaPagingItemReader` | JPA JPQL, 페이지 단위 조회 | 엔티티 매핑이 되어있고 페이징이 자연스러울 때 |
| `JdbcCursorItemReader` | JDBC 커서 기반, 스트리밍 | 단순 SQL, 대용량, 메모리 효율 중요할 때 |
| `JdbcPagingItemReader` | JDBC + 페이징 | JDBC이지만 페이지 단위 처리 원할 때 |
| `RepositoryItemReader` | Spring Data Repository 활용 | 이미 Repository가 있고 재사용하고 싶을 때 |

---

## 3. 집계 점수 계산 방식

일간 랭킹은 `commerce-streamer`에서 `viewCount`, `likeCount`, `orderCount`, `orderAmount`에 가중치를 적용해 ZSET score를 산출한다.

주간/월간 집계 시에도 동일한 가중치 공식을 적용하거나, 단순 합산 후 정렬하는 방식 중 선택해야 한다.

---

## 4. MV 테이블 갱신 전략

| 전략 | 방식 | 장단점 |
|------|------|--------|
| TRUNCATE + INSERT | 전체 삭제 후 재적재 | 구현 단순, 갱신 중 조회 공백 발생 가능 |
| UPSERT | INSERT ON DUPLICATE KEY UPDATE | 공백 없음, 구현 복잡도 있음 |
| DELETE + INSERT (트랜잭션) | 트랜잭션 안에서 삭제 후 삽입 | 공백 없음, 트랜잭션 범위 주의 |

---

## 5. 배치 모듈에서 엔티티 공유 문제

`ProductMetricsHourly`는 `commerce-streamer` 모듈에 정의되어 있다.
`commerce-batch`는 이 모듈에 의존하지 않으므로, 다음 중 하나를 선택해야 한다.

- **방법 A**: `commerce-batch`에 동일 테이블을 가리키는 별도 읽기 전용 엔티티 정의
- **방법 B**: JPA 대신 JDBC(`JdbcCursorItemReader`)로 직접 SQL 쿼리

---

# 🤔 구현 시 선택해야 할 사항

## 선택 1. Chunk vs Tasklet 내 Chunk 구현 ✅ 결정

| | Spring Batch Chunk | Tasklet 내 직접 구현 |
|--|-------------------|---------------------|
| 재시작 지원 | chunk 단위 재시작 가능 | 처음부터 재시작 |
| Skip/Retry | 프레임워크 지원 | 직접 구현 필요 |
| 구현 난이도 | Reader/Processor/Writer 분리 필요 | 자유롭게 구현 가능 |
| 대용량 적합성 | 높음 | 낮음 (전체 로드 위험) |

### 고민한 내용

Tasklet 내에서 직접 Chunk 처리를 구현하는 방식도 고려했다.
구현이 복잡할수록 Reader/Processor/Writer로 강제 분리하는 것보다 Tasklet 내에서 자유롭게 처리하는 방식이 더 자연스러울 수 있기 때문이다.

그러나 이번 구현의 핵심 처리 흐름은 다음과 같다:

```
product_metrics_hourly에서 기간 범위 읽기
→ product_id 별 집계 (SUM)
→ 점수 계산 후 TOP 100 정렬
→ mv_product_rank_weekly / mv_product_rank_monthly 저장
```

Spring Batch Chunk의 전제는 `1건 읽기 → 1건 가공 → N건 쓰기`인데,
집계(GROUP BY)가 포함되면 여러 시간 버킷 row → 1개 product_id 집계값이 되어 Chunk 구조와 어색해진다.

이 문제는 **SQL에서 GROUP BY 집계까지 처리**하면 해결된다.

```sql
SELECT product_id,
       SUM(view_count), SUM(like_count), SUM(order_count), SUM(order_amount)
FROM product_metrics_hourly
WHERE bucket_hour BETWEEN :start AND :end
GROUP BY product_id
```

Reader가 이미 집계된 결과(product_id 1건 = 1row)를 읽으므로 Chunk 구조에 자연스럽게 맞아떨어진다.
결과적으로 Spring Batch가 제공하는 페이징, 재시작각각을 , 건수 추적 혜택을 그대로 받을 수 있다.

**결정: SQL 집계 + Spring Batch Chunk 방식**

---

## 선택 2. ItemReader 방식 ✅ 결정

### 선택지 비교

**A. JdbcCursorItemReader** — 커서 기반 스트리밍

DB 커넥션을 유지하면서 결과를 한 줄씩 읽어온다.

- **적합한 사례**: 수백만 건 로그 정제, 순서 보장이 중요한 이벤트 처리, 단발성 배치
- **단점**: Step 실행 동안 DB 커넥션 1개를 점유 — 배치 시간이 길면 커넥션 풀 압박

**B. JdbcPagingItemReader** — 페이지 단위 조회

chunk 처리마다 새 쿼리로 페이지를 가져오고 커넥션을 반납한다.

- **적합한 사례**: 실패 시 마지막 페이지부터 재시작이 필요한 정산 배치, 배치 실행 시간이 긴 경우, 병렬 Step으로 커넥션 경합이 생기는 환경
- **단점**: GROUP BY + ORDER BY 복잡한 쿼리에서 페이징 정렬 키 설정이 까다로움

### 고민한 내용

선택 1에서 SQL GROUP BY 집계 결과를 Reader가 읽는 방식으로 확정했으므로 JDBC 기반 Reader가 자연스럽다.

이번 구현의 특성을 따져보면:
- 읽는 데이터는 product_id별 집계 결과로 볼륨이 크지 않음 (최대 상품 수만큼)
- 1일 1회 실행, 실행 시간이 짧아 재시작 필요성이 낮음
- GROUP BY 쿼리에 페이징 키를 추가하면 쿼리가 복잡해짐

**결정: JdbcCursorItemReader**

재시작 중요도가 낮고 GROUP BY 쿼리 복잡도를 피할 수 있어 `JdbcCursorItemReader`가 더 적합하다.

---

## 선택 3. 주간/월간 기간 기준 ✅ 결정

### 선택지 비교

| 기준 | 설명 | 예시 (기준일: 2024-01-10) |
|------|------|--------------------------|
| 슬라이딩 윈도우 | 기준일 기준 직전 7일/30일 | 주간: 01-04 ~ 01-10 / 월간: 12-11 ~ 01-10 |
| ISO 주차 / 역월 고정 | 해당 주 월~일 / 해당 월 1일~말일 | 주간: 01-08 ~ 01-14 / 월간: 01-01 ~ 01-31 |

### 트레이드오프

| | 슬라이딩 윈도우 | ISO 주차/역월 |
|--|--------------|--------------|
| 랭킹 변경 시점 | 매일 (배치 실행마다) | 주/월이 바뀔 때 |
| 데이터 신선도 | 높음 — 항상 최근 N일 기준 | 낮음 — 주/월 전환 시점에만 갱신 |
| 과거 랭킹 조회 | 어렵다 (날마다 결과가 다름) | 쉽다 (2024년 1월 = 고정값) |
| MV 테이블 설계 | 날짜 키 필요, 데이터 누적 관리 필요 | 주차/월 키로 단순 설계 가능 |
| API 구현 | date-base_date 매핑 및 fallback 필요 | 날짜로 주차/월 계산 후 바로 조회 |
| 배치 주기 | 매일 실행 필요 | 주 1회 / 월 1회로 충분 |

### 고민한 내용

"주간 베스트"라는 이름이 반드시 ISO 주차를 의미하지는 않는다. 슬라이딩 윈도우로도 "주간 베스트"를 구현할 수 있으며, 오히려 사용자가 어느 요일에 접속해도 항상 최근 7일 기준의 생생한 랭킹을 볼 수 있다는 장점이 있다.

커머스 서비스에서 실시간성 높은 랭킹을 제공하려면 슬라이딩 윈도우가 더 적합하다. ISO 방식은 과거 스냅샷 조회나 리포트 용도에 강점이 있지만, 이번 과제의 목적은 사용자에게 현시점 기준의 주간/월간 인기 상품을 보여주는 것이다.

MV 테이블에 날짜 키가 필요하고 갱신 전략이 다소 복잡해지는 트레이드오프가 있지만 구현 가능한 수준이다.

**결정: 슬라이딩 윈도우 방식**

`jobParameter`로 `targetDate`를 받아 배치 실행 시점에 기간을 계산한다.

### 부가 결정: 슬라이딩 윈도우 범위 ✅

| | 선택 A | 선택 B |
|--|--------|--------|
| 주간 | 오늘 포함 직전 7일 | **어제 기준 직전 7일** |
| 월간 | 오늘 포함 직전 30일 | **어제 기준 직전 30일** |

배치는 새벽에 실행되므로 오늘 데이터는 당일 0시~배치 실행 시각까지만 쌓인 불완전한 상태다.
어제 기준으로 잡으면 전날까지 완전히 쌓인 데이터만 집계하므로 항상 안정적인 랭킹을 제공할 수 있다.

**결정: 선택 B — 어제(targetDate - 1일) 기준 직전 7일/30일**

```
주간: targetDate - 7일  ~  targetDate - 1일
월간: targetDate - 30일 ~  targetDate - 1일
```

---

## 선택 4. MV 테이블 갱신 전략 ✅ 결정

### 선택지 비교

슬라이딩 윈도우 방식에서 TRUNCATE는 다른 날짜 데이터까지 삭제하므로 제외.
DELETE + INSERT vs UPSERT 중 선택.

**DELETE + INSERT (트랜잭션)**

```sql
BEGIN;
DELETE FROM mv_product_rank_weekly WHERE base_date = :baseDate;
INSERT INTO mv_product_rank_weekly VALUES (...);
COMMIT;
```

| 장점 | 단점 |
|------|------|
| 구현 단순, 직관적 | 트랜잭션 크기에 따라 락 경합 가능 |
| 전체 교체로 데이터 정합성 항상 보장 | 트랜잭션 범위가 길수록 조회 대기 발생 가능 |
| 탈락 상품 자동 제거 | - |
| 배치 실패 시 롤백으로 이전 데이터 유지 | - |

**UPSERT (INSERT ON DUPLICATE KEY UPDATE)**

```sql
INSERT INTO mv_product_rank_weekly (product_id, base_date, rank, score)
VALUES (:productId, :baseDate, :rank, :score)
ON DUPLICATE KEY UPDATE rank = VALUES(rank), score = VALUES(score);
```

| 장점 | 단점 |
|------|------|
| 트랜잭션 범위 작아 락 경합 낮음 | 배치 재실행 시 같은 base_date의 이전 레코드가 잔류할 수 있음 |
| 원자적 연산 | 재실행 안전성을 위해 결국 DELETE를 앞에 붙여야 함 |
| - | DELETE를 붙이면 DELETE + INSERT와 구조가 동일해져 UPSERT의 장점이 사라짐 |

### 고민한 내용

UPSERT는 같은 `base_date` 내에서 배치 재실행 시 이전 레코드가 잔류하는 문제가 있다. 예를 들어 1차 실행 도중 실패해 상품 A가 부분 적재된 상태에서, 2차 재실행 시 상품 A가 TOP 100 밖으로 밀리면 1차 레코드가 그대로 남는다. 이를 해결하려면 재실행 전 해당 `base_date` 데이터를 먼저 DELETE해야 하는데, 결국 DELETE + INSERT와 동일한 구조가 된다.

이번 구현은 매일 직전 7일/30일치를 새로 계산해 TOP 100을 완전히 교체하는 방식이므로, **전체 교체가 자연스럽고 구현이 단순한 DELETE + INSERT가 더 적합**하다.

**결정: DELETE + INSERT (트랜잭션으로 묶어 원자적 처리)**

---

## 선택 5. API 파라미터 확장 방식 ✅ 결정

### 선택지 비교

**A. period 파라미터 추가**
```
GET /api/v1/rankings?date=20240110&period=daily    (기존 Redis)
GET /api/v1/rankings?date=20240110&period=weekly   (MV 테이블)
GET /api/v1/rankings?date=20240110&period=monthly  (MV 테이블)
```

| 장점 | 단점 |
|------|------|
| 기존 엔드포인트 변경 범위 최소 | period에 따라 데이터 소스가 달라 컨트롤러/파사드에 분기 발생 |
| 하나의 URI로 통일감 | 내부 복잡도가 높아져 유지보수 어려움 |

**B. 별도 엔드포인트**
```
GET /api/v1/rankings/daily?date=20240110
GET /api/v1/rankings/weekly?date=20240110
GET /api/v1/rankings/monthly?date=20240110
```

| 장점 | 단점 |
|------|------|
| 각 엔드포인트가 단일 책임 | 컨트롤러/파사드 코드가 늘어남 |
| 데이터 소스가 달라도 독립적으로 구현 가능 | - |
| 각 기간별 독립적인 확장 가능 | - |
| RESTful 관점에서 리소스 성격이 명확 | - |

### 고민한 내용

`daily`는 Redis, `weekly`/`monthly`는 MV 테이블로 데이터 소스가 근본적으로 다르다. A 방식으로 구현하면 하나의 메서드 안에서 `period` 값에 따라 분기가 생기고, 나중에 각 기간별로 다른 요구사항이 생길 때 점점 복잡해진다.

단일 책임 원칙과 RESTful 설계 관점에서 **조회하는 리소스의 성격이 다르면 URI로 구분하는 것이 명확**하다.

**결정: B — 별도 엔드포인트로 분리**

---

## 선택 6. 스케줄링 방식 ✅ 결정

### 핵심 원칙: 외부 오케스트레이션 도구 사용

Job 간 의존 관계나 스케줄링을 **코드 내부(Job Chaining 등)에서 제어하지 않는다.**
코드 내부에서 흐름을 묶으면 장애 발생 시 영향 범위가 커지고, 중간 중단이 어려우며 모니터링도 불리하다.
각 배치는 독립적인 실행 단위로 만들고, 제어는 외부 스케줄러에 맡기는 것이 현업의 추세다.

### 도구별 비교

| 도구 | 적합한 상황 | 특징 |
|------|-----------|------|
| `@Scheduled` | 단순 단일 인스턴스, 프로토타입 | 분산 환경 중복 실행 위험, 모니터링 없음 |
| Quartz | 분산 환경 + 코드 내 스케줄링 | DB 기반 락, 운영 복잡도 있음 |
| Jenkins / Cron | 일반적인 현업 환경 | 운영 비용 낮음, 심플, 가장 많이 사용 |
| K8s CronJob | 쿠버네티스 기반 인프라 | 인프라 레벨 스케줄링, 배치 앱 단순하게 유지 |
| Airflow | 고도화된 데이터 파이프라인 환경 | DAG 기반 의존 관계 관리, 운영 난이도 높음 |
| Argo Workflows | 이미 Argo를 CI/CD로 사용 중인 대규모 환경 | 쿠버네티스 네이티브, 운영 비용 높음 |

### 규모별 선택 기준

- **기본 수준** : Jenkins 또는 K8s CronJob
  - 운영 비용이 낮고 심플하여 별도의 빅데이터 인프라가 없는 환경에 적합
- **고도화된 대규모 환경** : Airflow 또는 Argo Workflows
  - 이미 회사에서 해당 도구를 CI/CD나 데이터 마트 관리 목적으로 쓰고 있을 때 함께 활용

**결정: 현재 프로젝트 규모에서는 K8s CronJob (외부 스케줄링) 수준으로 설계**

배치 앱은 `--job.name` 파라미터를 받아 독립적으로 실행되는 단위로 유지하고, 스케줄링은 외부에 위임하는 구조로 구현한다.

---

# 실시간 집계 vs 배치 + MV 조회

## 실시간 집계 방식

```sql
-- 랭킹 조회 요청마다 실행
SELECT product_id,
       LN(1 + SUM(view_count))    * ? +
       LN(1 + SUM(like_count))    * ? +
       LN(1 + SUM(order_amount))  * ? AS score
FROM product_metrics_hourly
WHERE bucket_hour BETWEEN :start AND :end
GROUP BY product_id
ORDER BY score DESC
LIMIT 100;
```

- 매 요청마다 전체 기간 데이터를 Full Scan + GROUP BY + Sort
- 주간 = 7일 × 24시간 = 168개 row/상품, 월간 = 720개 row/상품
- 상품 수 × row 수만큼 매번 집계 → **요청이 많아질수록 DB 부하가 선형으로 증가**

## 배치 + MV 조회 방식

```sql
-- 배치가 새벽에 1회 실행해 결과를 MV 테이블에 저장
-- 랭킹 조회 요청은 단순 PK 조회
SELECT * FROM mv_product_rank_weekly WHERE base_date = ?;
```

- 집계는 배치가 새벽에 1회만 수행
- 조회 시점에는 이미 계산된 결과를 단순 SELECT → **요청 수와 무관하게 응답 시간 일정**

## 왜 배치 + MV가 더 나은가

| 항목 | 실시간 집계 | 배치 + MV |
|------|-----------|----------|
| 조회 쿼리 비용 | Full Scan + GROUP BY + Sort | PK 단순 조회 |
| 동시 요청 증가 시 | DB 부하 선형 증가 | 부하 없음 (이미 계산됨) |
| 응답 시간 | 데이터 양에 비례 | 항상 일정 |
| 집계 실행 횟수 | 요청마다 | 하루 1회 |
| 데이터 신선도 | 실시간 | 배치 실행 주기만큼 지연 |

## 트레이드오프

배치 + MV 방식의 단점은 **데이터 신선도**다.
배치가 새벽 2시에 실행된다면 오전 10시에 조회해도 어제 기준 랭킹을 보게 된다.

하지만 주간/월간 랭킹은 **하루 단위로 변해도 사용자가 체감하는 차이가 크지 않다.**
실시간 반영이 중요한 일간 랭킹은 Redis ZSET으로 처리하고,
주간/월간처럼 집계 비용이 크고 신선도 요구가 낮은 경우에는 배치 + MV가 적합하다.

```
일간 랭킹  → Redis ZSET  (실시간, 메모리 기반)
주간 랭킹  → MV 테이블   (배치 집계, 하루 1회 갱신)
월간 랭킹  → MV 테이블   (배치 집계, 하루 1회 갱신)
```

---

# Spring Batch Listener 구현

Spring Batch의 Job/Step/Chunk 실행 흐름에 부가 로직을 끼워넣기 위해 3개의 Listener를 구현했다.
실행 이력(성공/실패 상태, 시작/종료 시각)은 Spring Batch가 `BATCH_*` 테이블에 자동으로 기록하므로,
Listener는 그 외 **모니터링, 로깅, 알림** 등 부가 작업에 집중한다.

## JobListener

- `@BeforeJob`: Job 시작 시 Job 이름 로깅 + 시작 시각을 `ExecutionContext`에 저장
- `@AfterJob`: Job 종료 시 시작/종료 시각과 총 소요 시간(시간/분/초)을 로깅

```java
@BeforeJob
void beforeJob(JobExecution jobExecution) {
    log.info("Job '{}' 시작", jobExecution.getJobInstance().getJobName());
    jobExecution.getExecutionContext().putLong("startTime", System.currentTimeMillis());
}

@AfterJob
void afterJob(JobExecution jobExecution) {
    // ExecutionContext에서 시작 시각 복원 후 소요 시간 계산 및 로깅
}
```

## StepMonitorListener

- `@BeforeStep`: Step 시작 시 Step 이름 로깅
- `@AfterStep`: Step 종료 시 실패 예외가 있으면 jobName + 예외 메시지 로깅
  - Slack 등 외부 알림 채널 연동 포인트 (현재는 주석 처리)
  - 실패 시 `ExitStatus.FAILED` 반환으로 Job 상태에 반영

```java
@Override
public ExitStatus afterStep(StepExecution stepExecution) {
    if (!stepExecution.getFailureExceptions().isEmpty()) {
        // error 발생 시 slack 등 다른 채널로 모니터 전송
        return ExitStatus.FAILED;
    }
    return ExitStatus.COMPLETED;
}
```

## ChunkListener

- `@AfterChunk`: 청크 처리 완료 후 readCount, writeCount 로깅

```java
@AfterChunk
void afterChunk(ChunkContext chunkContext) {
    log.info("청크 종료: readCount: {}, writeCount: {}",
        chunkContext.getStepContext().getStepExecution().getReadCount(),
        chunkContext.getStepContext().getStepExecution().getWriteCount()
    );
}
```

---

# 코드 리뷰 반영

## 1. rank 할당의 Multi-Chunk 취약점 수정

`WeeklyRankingItemWriter` / `MonthlyRankingItemWriter` 에서 rank 를 `int rank = 1` 부터 순서대로 할당하는 방식은
`write()` 가 여러 번 호출되는 다중 Chunk 환경에서 매 호출마다 rank 가 1 부터 재시작되는 버그가 발생한다.

현재는 `LIMIT 100` + `chunk(100)` 으로 단일 Chunk 가 보장되지만, 둘 중 하나만 변경되면 조용히 깨진다.

**수정 내용**

- `WeeklyRankingJobConfig`, `MonthlyRankingJobConfig` 에 `public static final int TOP_N = 100` 상수 추가
- SQL `LIMIT 100` → `LIMIT %d".formatted(TOP_N)` 으로 상수 참조
- `chunk(100, ...)` → `chunk(TOP_N, ...)` 으로 통일
- Writer 에 단일 Chunk 전제 경고 주석 추가

---

## 2. Controller → Facade 내부 메서드 노출 수정

Controller 가 `resolveBaseDate()`, `getWeeklyTotal()` 등 Facade 내부 메서드를 직접 호출하고 있었다.
동일한 `date` 에 대해 `resolveBaseDate` 가 최대 3회 중복 실행되는 문제도 있었다.

**수정 내용**

`RankingPageResult(effectiveDate, total, items)` record 를 신규 도입하여
Facade 가 items + total + effectiveDate 를 하나로 묶어 반환하도록 변경했다.
Controller 는 Facade 를 단일 호출하고 결과를 그대로 사용한다.

일간(`RankingFacade`) / 주간(`WeeklyRankingFacade`) / 월간(`MonthlyRankingFacade`) 모두 동일하게 적용하여
Facade 간 일관성을 유지했다.

- `getDailyTotal`, `today()`, `getWeeklyTotal`, `getMonthlyTotal`, `resolveBaseDate` (public) 제거
- 3개 Controller 단순화, 3개 Facade 테스트 반환 타입 수정

---

## 3. DELETE + INSERT 트랜잭션 미명시 수정

`JdbcMvProductRankRepository` 의 `replaceWeeklyRanking` / `replaceMonthlyRanking` 은
DELETE + INSERT 를 원자적으로 처리해야 하지만 `@Transactional` 이 없었다.

현재는 Spring Batch Chunk 트랜잭션이 암묵적으로 보장하고 있으나,
Repository 가 자신의 원자성을 호출자에게 위임하면 Batch 외부 호출 시 데이터 소실 위험이 있다.

**수정 내용**

- `replaceWeeklyRanking`, `replaceMonthlyRanking` 에 `@Transactional` 추가
- Spring 기본 전파 속성(`REQUIRED`) 으로 Batch 트랜잭션 안에서는 합류, 외부 호출 시 자체 트랜잭션 생성
- 클래스 Javadoc 에 REQUIRED 전파 동작 명시

---

## 4. order_amount 미사용 버그 수정

배치 SQL 이 `SUM(order_count) * weight` 를 사용하고 있었다.
일간 랭킹(`RankingScoreCalculator`) 은 week9 에서 다음 근거로 `log1p(order_amount)` 를 채택했다.

- `order_count` (건수) — 매출 기여 무시 (기각)
- `order_amount` 원본 — 스케일 폭주로 view/like 압도 (기각)
- `log1p(order_amount)` — 스케일 압축 + 매출 반영 + 가중치 의미 유지 (채택)

주간/월간 배치가 이 결정을 반영하지 않아 일간과 점수 계산 공식이 불일치했다.

**수정 내용**

배치 AGGREGATION_SQL 을 일간과 동일한 공식으로 수정했다.

```sql
-- 수정 전
SUM(view_count) * ? + SUM(like_count) * ? + SUM(order_count) * ?

-- 수정 후
LN(1 + SUM(view_count))    * ?
+ LN(1 + SUM(like_count))  * ?
+ LN(1 + SUM(order_amount)) * ?
```

배치 E2E 테스트 데이터도 `order_count` 기반에서 `order_amount` 기반으로 교체했다.

---

## 5. 테스트 given/when/then 주석 누락 수정

`WeeklyRankingJobE2ETest`, `MonthlyRankingJobE2ETest` 의 `populatesMvTableWithRanking`,
`replacesExistingMvOnRerun` 케이스에 given/when/then 주석이 누락되어 있었다.

**수정 내용**

누락된 4개 테스트 케이스에 given/when/then 주석 추가.
`replacesExistingMvOnRerun` 은 1차 실행(given) → 2차 실행(when) → 검증(then) 흐름이
명확히 드러나도록 구조도 함께 정리했다.

---

## 6. Interfaces Layer의 Domain enum 직접 참조 제거

`RankingV1Dto`(Interfaces Layer) 의 `RankingItemResponse.status` 필드가
`ProductStatus`(Domain enum) 를 직접 import하고 있었다.

Interfaces Layer 는 Application Layer 를 통해서만 Domain 타입을 간접 참조해야 하므로
Interfaces → Domain 직접 의존은 레이어 경계 위반이다.

**수정 내용**

- `status` 필드 타입을 `ProductStatus` → `String` 으로 변경
- `p.status()` → `p.status().name()` 으로 변환 (API 응답 형식 동일)
- `import com.loopers.domain.product.ProductStatus` 제거

---

## 7. batch.ranking 도메인 타입 패키지 이동 (domain.ranking)

`MvProductRankRepository`, `MvProductRankRow`, `ProductMetricsAggregate` 가
`com.loopers.batch.ranking` 패키지에 위치하고 있었다.

commerce-api 는 Repository 인터페이스와 도메인 VO 를 `com.loopers.domain.ranking` 에 두는 반면,
commerce-batch 의 도메인 타입은 `batch.ranking` 에 혼재하여 패키지 네이밍 일관성이 없었다.

**수정 내용**

- `com.loopers.batch.ranking` → `com.loopers.domain.ranking` 으로 패키지 이동
- 영향받는 5개 파일 import 갱신
  - `WeeklyRankingJobConfig`, `MonthlyRankingJobConfig`
  - `WeeklyRankingItemWriter`, `MonthlyRankingItemWriter`
  - `JdbcMvProductRankRepository`
- 기존 `batch/ranking` 디렉토리 삭제

이로써 commerce-batch 의 의존 방향이 commerce-api 와 동일한 구조를 갖는다:
```
batch.job.** (Job/Step/Writer) → domain.ranking (인터페이스/VO) ← infrastructure.ranking (JDBC 구현체)
```

---

## 8. 배치 스케줄 트리거 구성 (Jenkins + Cron)

### 배치와 API를 분리하는 이유

| 관심사 | 이유 |
|---|---|
| 리소스 격리 | 배치 실행 중 CPU/메모리 집중 사용이 API 응답 시간에 영향을 주지 않도록 분리 |
| 배포 독립성 | API는 트래픽에 따라 수평 확장, 배치는 스케줄 시점에만 단일 실행 |
| 장애 격리 | 배치가 OOM으로 종료되어도 API 서비스는 영향 없음 |

### 실행 방식

실무에서 대규모 인프라 없이 가장 현실적인 방식은 **Jenkins + Shell Script** 조합이다.

- Jenkins(또는 Linux crontab)가 지정 시각에 스크립트를 호출
- 스크립트가 `targetDate` 를 계산하여 배치 JAR 를 실행
- 배치 앱은 Job 완료 후 프로세스 종료 (exit code 반환)
- Jenkins 가 exit code 로 성공/실패 판단 후 슬랙 알림 등 후처리

### 실행 흐름

```
Jenkins Cron Trigger (매일 새벽 2시)
        │
        ▼
./scripts/run-weekly-ranking.sh
        │
        ├─ targetDate 결정 (인자 없으면 오늘 날짜)
        ▼
java -jar commerce-batch.jar \
  --spring.profiles.active=prd \
  --job.name=weeklyRankingJob \
  targetDate=2026-04-16
        │
        ├─ @ConditionalOnProperty → WeeklyRankingJob 빈만 로드
        ├─ Job 실행 완료
        └─ 프로세스 종료 (exit code 반환)
        │
        ▼
Jenkins → 성공/실패 판단 → 슬랙 알림 등 후처리
```

### 스크립트 구성 (`scripts/`)

| 파일 | 실행 Job | 기본 Cron |
|---|---|---|
| `run-weekly-ranking.sh` | weeklyRankingJob | 매일 새벽 2:00 |
| `run-monthly-ranking.sh` | monthlyRankingJob | 매일 새벽 2:30 |

두 스크립트 모두 아래 특성을 갖는다:
- `targetDate` 인자 생략 시 오늘 날짜 자동 설정
- `JAR_PATH`, `SPRING_PROFILE` 환경변수로 경로·프로필 재정의 가능
- `set -euo pipefail` 으로 오류 발생 시 즉시 종료 및 exit code 전달

### application.yml 설계

`spring.batch.job.name: ${job.name:NONE}` 설정이 외부 트리거 방식을 지원한다.

- 실행 시 `--job.name=weeklyRankingJob` 을 넘기면 해당 Job 빈만 로드
- `@ConditionalOnProperty` 로 Job 별 컨텍스트 격리
- `web-application-type: none` 으로 Job 완료 후 자동 종료

### Jenkins Pipeline 예시

```groovy
triggers {
    cron('0 2 * * *') // 매일 새벽 2시
}

stage('Weekly Ranking Batch') {
    steps {
        sh './scripts/run-weekly-ranking.sh'
    }
}

stage('Monthly Ranking Batch') {
    steps {
        sh './scripts/run-monthly-ranking.sh'
    }
}
```

---

## 코드 리뷰 반영 (2차)

### 1. `rank` 컬럼 JPA 인용 방식 수정

`MvProductRankWeekly` / `MvProductRankMonthly` 에서 `rank` 컬럼을 JPA 표준 인용자(`"\"rank\""`)로 선언하고 있었다.
Hibernate 6.x + MySQL Dialect 환경에서 이 방식이 백틱으로 자동 변환된다는 보장이 없고,
배치 JDBC 쪽은 이미 백틱으로 직접 발행하고 있어 인용 전략이 불일치했다.

**수정 내용**

- `@Column(name = "\"rank\"")` → `@Column(name = "`rank`")` 로 변경
- 배치 JDBC 와 API JPA 의 인용 전략을 백틱으로 통일

```java
// 수정 전
@Column(name = "\"rank\"", nullable = false)
private int rank;

// 수정 후
@Column(name = "`rank`", nullable = false)
private int rank;
```

---

### 2. `RankingAssembler` 도입 — Facade 파이프라인 중복 제거

`RankingFacade` / `WeeklyRankingFacade` / `MonthlyRankingFacade` 세 곳에 동일한 조회 파이프라인이 반복되어 있었다.

```
entries → productIds 추출 → findVisibleByIds → RankingItemInfo.of → RankingPageResult
```

차이는 저장소 타입과 baseDate 기본값(오늘/어제)뿐이었고, 나머지 로직은 100% 동일했다.
가시성 필터 정책 변경 시 세 곳을 동시에 수정해야 하는 Shotgun Surgery 안티패턴이었다.

**수정 내용**

- `RankingAssembler` 컴포넌트 신규 도입
  - 공통 파이프라인 (`entries → 가시성 필터 → RankingPageResult 조립`) 을 단일 위치로 통합
  - `KST` 상수도 `RankingAssembler.KST` 로 이동하여 세 Facade 가 공통 참조
- 세 Facade 에서 `ProductFacade` 직접 의존 제거 → `RankingAssembler` 주입으로 교체
- `Collections.unmodifiableList` → `List.toList()` 로 교체 (불변 복사본 보장)
- `RankingAssemblerTest` 신규 작성 — 조립 로직 전담 (happyPath, visibilityFilter, emptyEntries)
- 세 Facade 테스트 슬림화 — date 결정 로직 + assembler 위임 검증만 담당

```java
// 수정 후 Facade — 저장소 호출 + assembler 위임만 담당
public RankingPageResult getWeeklyRanking(LocalDate date, int pageOneBased, int size) {
    LocalDate baseDate = date != null ? date : LocalDate.now(clock.withZone(RankingAssembler.KST)).minusDays(1);
    long total = weeklyRankingRepository.getTotal(baseDate);
    List<RankingEntry> entries = weeklyRankingRepository.getTopN(baseDate, pageOneBased, size);
    return rankingAssembler.assemble(baseDate, total, entries);
}
```

---

### 3. `RankingPageQuery` 도입 — Controller 파싱 로직 중복 제거

`RankingV1Controller` / `WeeklyRankingV1Controller` / `MonthlyRankingV1Controller` 세 곳에 동일한 내용이 중복되어 있었다.

- 상수: `YYYYMMDD`, `DEFAULT_PAGE`, `DEFAULT_SIZE`, `MAX_SIZE`
- 메서드: `parseDate`, `safePage`/`safeSize` 계산, BAD_REQUEST 메시지

**수정 내용**

- `RankingPageQuery` record 신규 도입 (package-private)
  - 정적 팩토리 `of(dateStr, page, size)` — 파싱·보정 로직을 단일 위치로
  - `formattedDate(LocalDate)` — yyyyMMdd 변환 헬퍼
- 세 Controller 에서 파라미터 처리 4줄 → `RankingPageQuery.of(...)` 한 줄로 교체

```java
// 수정 전 — 세 Controller 에 동일 코드 반복
LocalDate date = parseDate(dateStr);
int safePage = Math.max(page, DEFAULT_PAGE);
int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

// 수정 후
RankingPageQuery query = RankingPageQuery.of(dateStr, page, size);
```

---

### 4. `ProductStatus` 원복

`RankingV1Dto.RankingItemResponse.status` 필드가 `String` 으로 변경되어 있었으나,
커밋 이력 확인 결과 최초 구현(`c98824a`)에서는 `ProductStatus` 였고
커밋 없이 워킹트리에서만 변경된 상태였다.

`ProductStatus` → `String` 변환 시 와이어 포맷은 동일하지만,
타입 안전성이 낮아지고 OpenAPI 스키마가 enum → string 으로 변경되어 클라이언트 계약이 훼손된다.

**수정 내용**

- `String status` → `ProductStatus status` 원복
- `p.status().name()` → `p.status()` 원복 (Jackson 이 enum name 을 기본 직렬화)

