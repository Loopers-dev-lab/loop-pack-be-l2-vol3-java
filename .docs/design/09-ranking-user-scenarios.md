# 랭킹 시스템 — 동작·시나리오 (정상 / 예외)

> **이 문서가 하는 일**: 랭킹 API와 상품 상세의 순위 필드가 **언제·어떻게 조회**되고, 잘 될 때와 문제 있을 때 **각각 무엇이 돌아오는지**를 짧게 정리한다.  
> **근거 코드·문서**: `RankingQueryService`, `RedisRankingReadRepository`, `ProductFacade`, 스트리머 Collector·점수 계산 등 / [09-ranking-redis-zset-design.md](./09-ranking-redis-zset-design.md), [09-ranking-implementation-roadmap.md](../Implementation/09-ranking-implementation-roadmap.md).

---

## 0. 용어 

### Carry-Over(이월)이 하는 일

자정이 지나 “오늘” 랭킹 Redis 키가 비어 있으면(설정에 따라), **어제 랭킹 상위 N개**만 골라 **점수를 작게 깎아서** 오늘 키에 미리 넣는다. 그래서 **어제 잘 나갔던 상품**이 오늘도 **출발선에 서게** 되고, 새벽에 목록이 텅 빈 것처럼 보이는 걸 줄이려는 목적에 가깝다.  
**여기서 옮겨 오는 것은 “어제 순위권에 있던 상품”뿐**이라, “오늘 처음 올라온 신상을 특별히 밀어준다”는 뜻은 아니다. 그래서 **신규 상품을 넓게 노출한다**는 의미의 “탐색(Exploration)” 효과는 크지 않다고 보면 된다.

### 0.1 인기 반영 vs 신규 노출 (설계 문서 용어: Exploitation / Exploration)


| 쉬운 이름                   | 뜻                                                               |
| ----------------------- | --------------------------------------------------------------- |
| **인기 반영(Exploitation)** | 조회·좋아요·판매가 쌓인 만큼 점수가 올라가 위쪽에 보이는 방식. **지금 구현의 중심**이다.           |
| **신규 노출(Exploration)**  | 신상을 **의도적으로** 위로 올리는 별도 규칙(신상 코너, 가산점 등). **랭킹 API만으로는 거의 없다.** |


**정리**: 이벤트가 한 번도 쌓이지 않은 상품은 Redis 랭킹에 없을 수 있어 **목록에도 안 나온다**. 인기 랭킹 안에서 신규를 억지로 섞기보다, **신상은 전용 탭·전용 API·전용 블록**으로 두는 편이 사용자 입장에서도 이해하기 쉽다(§4 참고). **Carry-Over**는 “어제 강했던 상품”만 오늘 키에 **작은 점수로** 깔아 주는 수준이다.

### 0.2 점수가 어떻게 나오나 (산출)

**DB 쪽 원본**: `product_metrics` — 스트리머가 Kafka 이벤트를 처리할 때마다 갱신된다.  
**Redis**: 날짜별 키 `ranking:all:{yyyyMMdd}`, 상품 ID가 member, 점수는 아래 식(가중치는 코드 `questExample` 기준).

\text{score} = w_v \cdot C_{\text{view}} + w_\ell \cdot C_{\text{like}} + w_o \cdot C_{\text{sold}}


| 기호              | 의미                                 | 현재 코드 기본값 (`questExample`) |
| --------------- | ---------------------------------- | -------------------------- |
| C_{\text{view}} | 상품 조회 누적(`PRODUCT_VIEWED`)         | 가중치 w_v = 0.1              |
| C_{\text{like}} | 좋아요 순증분(`PRODUCT_LIKE_CHANGED`)    | w_\ell = 0.2               |
| C_{\text{sold}} | 결제 완료 라인 수량 합(`PAYMENT_COMPLETED`) | w_o = 0.6                  |


**지금 점수에 안 넣는 것**: 클릭률, 전환율, 리뷰 별점, 카테고리 보정, 조회 수 제한(어뷰징 방지) 등은 **이 식에 없다**. 넣으려면 별도 설계가 필요하다.

**언제 Redis가 갱신되나**: Kafka 이벤트를 처리한 뒤 DB에 반영되고, 그다음 `RankingMetricsRedisSyncService`가 **그날 키**에 점수를 넣거나 덮어쓴다(TTL 포함).  
추가로 **새벽 배치**(`RankingReconciliationScheduler`, 기본 2시 전후)가 DB를 돌며 Redis와 맞추고, **자정 배치**(`RankingCarryoverScheduler`)가 위에서 말한 **Carry-Over**(어제 상위 N → 오늘 키, 옵션)를 수행한다.

### 0.3 아래 표에서 쓰는 “빈도·영향” 표기

정확한 %는 운영 전에는 적기 어렵다. 대신 **상대적인 크기**만 구분한다.


| 표기     | 의미                                    |
| ------ | ------------------------------------- |
| **F1** | 자주 안 겪는 경우(잘못된 파라미터, 장애 직후 등)         |
| **F2** | 가끔 겹침(자정·만료 시각, 여러 번 스크롤 등)           |
| **F3** | 많은 사용자가 같은 경로로 자주 겪음(목록 보고 상세 또 보기 등) |



| 표기     | 의미                           |
| ------ | ---------------------------- |
| **I1** | 순위 숫자 하나만 이상할 때              |
| **I2** | 한 페이지 안에서 줄·칸이 비어 보일 때       |
| **I3** | 랭킹 화면 전체가 비었을 때              |
| **I4** | 목록에서 본 순위와 상세에서 본 순위가 다를 때   |
| **I5** | 특정 상품만 유난히 위로 올라가 보일 때(조작 등) |


---

## 1. 정상으로 보이는 경우 (해피)

아래는 **API가 성공했을 때 실제로 어떤 값이 오는지**만 적는다.


| ID     | 트리거(클라이언트)                                       | 호출·저장                                                                                                                         | 관찰 가능한 결과                                                                                                 |
| ------ | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| **H1** | `GET /api/v1/rankings` (`date` 생략)               | `RankingKey.dailyAll(오늘 KST)`에 대해 `ZCARD` + `ZREVRANGE` 구간; 행은 `productRepository.findByIdInAndNotDeletedAsMap` 등으로 Hydration | HTTP 200, `data.items`에 최대 `size`개, 각 행에 `rank`(1-based, 슬라이스 내 인덱스 기반), `score`, 상품·브랜드·좋아요 수 필드         |
| **H2** | `GET /api/v1/rankings?date=yyyyMMdd`             | 동일, 키만 해당 일자                                                                                                                  | 키가 존재하고 ZSET 비어 있지 않으면 H1과 동형                                                                             |
| **H3** | `page`·`size` 합법 범위 (`page≥1`, `1≤size≤100`)     | `start=(page-1)*size`, `end=start+size-1` (상한은 `total-1`로 클램프)                                                                | `totalElements`=`ZCARD`, `totalPages`=ceil(total/size); `page`가 범위 밖이면 **빈 `items`**, `totalElements`는 유지 |
| **H4** | `GET /api/v1/products/{id}` (`date` 생략 또는 동일 일자) | 상세 본문은 캐시/DB; `rankingQueryService.findOneBasedDailyRank` → `ZREVRANK`+1                                                      | ZSET에 member 존재 시 `rankingRank` 양의 정수; 없으면 null                                                           |
| **H5** | Kafka 이벤트가 정상 처리됨                                | `ProductEventCollectorDatabaseService`가 `product_metrics` 갱신 후 커밋 뒤 Redis upsert                                              | 이후 동일 일자 키에서 해당 `productId`의 score가 재계산값으로 갱신(다음 읽기부터 반영)                                                 |
| **H6** | `POST /api/v1/rankings/snapshots?date=yyyyMMdd` (또는 생략 시 오늘) | `RankingQueryService.createSnapshot` → `RankingSnapshotRepository.materialize`: 일간 ZSET을 `ranking:snap:{date}:{uuid}`로 복제 후 TTL | HTTP **201**, `data.rankingSnapshotId`(UUID), `data.totalElements`, `data.ttlSeconds` |
| **H7** | `GET /api/v1/rankings?...&rankingSnapshotId={uuid}`      | 스냅샷 키 존재 시 해당 ZSET만 `ZCARD`/`ZREVRANGE` + 동일 Hydration                                                                  | HTTP 200, `data.dataSource`=`REDIS_SNAPSHOT`, `data.rankingSnapshotId`가 요청과 동일하게 echo; 라이브 키가 바뀌어도 **같은 uuid**로는 순서 고정 |


### 1.1 위가 성립하려면 (깨지면 §2 엣지로 이어짐)


| 가정 ID  | 내용                                  | 깨졌을 때              |
| ------ | ----------------------------------- | ------------------ |
| **A1** | Redis가 응답하고, 개수·목록·순위 조회가 에러 없이 끝남  | E-REDIS-*          |
| **A2** | Kafka 쪽에서 이벤트가 빠지지 않고(또는 배치로 곧 맞춰짐) | E-LAG-*, E-RECON-* |
| **A3** | Redis에 넣은 상품 ID가 숫자 문자열로 읽힘         | E-PARSE            |
| **A4** | 상품·브랜드가 DB에서 “삭제 안 됨”으로 조회됨         | E-HYDR-*           |
| **A5** | `date`는 비우거나 `yyyyMMdd`만 옴          | E-VAL-DATE         |
| **A6** | 상세에 순위 붙일 때 Redis 순위 조회가 됨          | E-REDIS-RANK       |
| **A7** | “인기만으로 순위 매기기”가 서비스 목표와 맞음          | §4 참고              |
| **A8** | 스냅샷 조회 시 `rankingSnapshotId`가 유효 UUID이고 키가 아직 만료 전 | E-VAL-SNAP-ID, E-SNAP-MISS |


---

## 2. 예외·경계 (엣지)

### 2.1 잘못된 입력·페이지 크기


| ID             | 조건                         | API·동작                                      | 관찰 결과                        | 빈도  | 영향  |
| -------------- | -------------------------- | ------------------------------------------- | ---------------------------- | --- | --- |
| **E-VAL-PAGE** | `page < 1`                 | Bean validation 또는 Facade                   | 400                          | F1  | I1  |
| **E-VAL-SIZE** | `size < 1` 또는 `size > 100` | `@Min`/`@Max`                               | 400                          | F1  | I1  |
| **E-VAL-DATE** | `date` 형식 불일치·달력 무효        | `RankingRequestDate` → `CoreException`      | 400, 메시지 `date는 yyyyMMdd...` | F1  | I1  |
| **E-VAL-PID**  | `productId ≤ 0`로 순위 조회 시도  | `RankingQueryService.findOneBasedDailyRank` | `CoreException`              | F1  | I1  |


### 2.2 Redis 문제·캐시


| ID                 | 조건                                                                      | 코드 경로                                                                     | 관찰 결과                                                          | 빈도  | 영향                        |
| ------------------ | ----------------------------------------------------------------------- | ------------------------------------------------------------------------- | -------------------------------------------------------------- | --- | ------------------------- |
| **E-REDIS-LIST**   | `loadPage` 중 `RedisConnectionFailureException` / `RedisSystemException` (**라이브 키** 조회) | `RankingQueryService` catch                                               | `dataSource`로 구분(REDIS / FALLBACK_LATEST / DEGRADED). fallback on이면 **DB 최신순 목록**, off면 **빈 목록** | F1  | I3                        |
| **E-REDIS-SNAP**   | 동일 예외가 **`rankingSnapshotId`가 있는** 목록 조회에서 발생                         | 스냅샷 경로는 **DB fallback 없음** → `DEGRADED` 빈 목록 쪽으로 수렴                        | `dataSource`=DEGRADED, `rankingSnapshotId` echo 가능; 라이브 `GET`(파라미터 없음)과 정책이 다름                        | F1  | I3                        |
| **E-REDIS-RANK**   | 상세 순위 조회 동일 예외                                                          | `findOneBasedDailyRank` catch                                             | `OptionalLong.empty()` → 응답 `rankingRank=null` (상세는 200일 수 있음) | F1  | I1·I4                     |
| **E-CACHE-DETAIL** | 상품 상세 **캐시 히트**                                                         | `ProductFacade`는 캐시 본문에 `rankingRank` 없이 저장 후 매 요청 `withDailyRankingRank` | 본문 필드(가격·재고)는 캐시 시각, `rankingRank`만 **현재 Redis**               | F3  | I4 (재고·가격과 순위의 시간 불일치 가능) |

> 구현 반영(해결): Redis 장애 시 동작은 §4.2 합의안대로 **관측(MDC/메트릭)** + **fallback(옵션)**으로 변경되었다.
> - `GET /api/v1/rankings` 응답 `dataSource`로 **REDIS / REDIS_SNAPSHOT / FALLBACK_LATEST / DEGRADED**를 구분한다.
> - 설정 `app.ranking.fallback-on-redis-failure`가 켜져 있으면 **DB 최신 등록순으로 대체 목록**을 내려준다(점수 `score=0`).


### 2.3 Redis와 DB 내용이 안 맞을 때


| ID                   | 조건                                | 관찰 결과                                             | 빈도  | 영향  |
| -------------------- | --------------------------------- | ------------------------------------------------- | --- | --- |
| **E-ZSET-ORPHAN**    | Redis에는 있는데 DB에서 상품·브랜드를 못 붙임     | 그 줄은 **안 보여 줌**. 순위 번호가 **연속이 아닐 수 있음**           | F2  | I2  |
| **E-TOTAL-MISMATCH** | 전체 개수는 Redis 기준, 화면 줄 수는 DB 붙은 것만 | 한 페이지에 **빈 칸**이 생길 수 있음                           | F2  | I2  |
| **E-PARSE**          | member가 비숫자                       | 파싱 실패 행 스킵                                        | F1  | I2  |
| **E-NULL-ZSET**      | 키 없음 또는 ZSET 비어 있음                | 목록 빈 배열, `totalElements=0`; 상세 `rankingRank=null` | F2  | I3  |


### 2.4 날짜·보관 기간·처리 지연


| ID                  | 조건                                   | 관찰 결과                                                               | 빈도  | 영향  |
| ------------------- | ------------------------------------ | ------------------------------------------------------------------- | --- | --- |
| **E-DATE-BOUND**    | `occurredAt` KST 일자 ≠ “처리 시각” 일자     | 동일 이벤트가 기대와 다른 `ranking:all:{yyyyMMdd}`에 쓰일 수 있음(설계상 occurredAt 귀속) | F2  | I4  |
| **E-TTL**           | 조회 `date`가 TTL(2일) 밖                 | 키 부재 → E-NULL-ZSET과 동형                                              | F2  | I3  |
| **E-LAG**           | Kafka lag, Redis 쓰기 실패 후 DLQ         | DB `product_metrics`와 Redis ZSET 불일치; 다음 이벤트·reconciliation까지 지속 가능 | F2  | I4  |
| **E-OFFSET-SHIFT**  | 동일 `page`·`size`로 재요청하되 그 사이 **라이브** ZSET 갱신 | `ZREVRANGE` 오프셋 기준이 변해 **중복·누락** 가능 (`RankingV1ApiSpec` 고지와 동일). **완화**: H6·H7 스냅샷 흐름으로 페이지 넘김 시 순서 고정 | F3  | I4  |
| **E-VAL-SNAP-ID**   | `rankingSnapshotId`가 UUID 형식이 아님              | `normalizeRankingSnapshotId` → `CoreException`                          | 400                                                          | F1  | I1  |
| **E-SNAP-MISS**     | 만료·잘못된 id로 스냅샷 키 없음                          | `rankingSnapshotRepository.exists` false → NOT_FOUND                     | 404                                                          | F2  | I1  |
| **E-SNAP-CREATE**   | 스냅샷 생성 중 Redis 실패                             | `createSnapshot` catch → `INTERNAL_ERROR`                               | 500                                                          | F1  | I3  |
| **E-RANK-MISMATCH** | 목록 조회 직후 상세 조회                       | `ZREVRANGE` 시점 ≠ `ZREVRANK` 시점                                      | F3  | I4  |


### 2.5 점수 같을 때·아직 이벤트 없을 때


| ID                 | 조건                    | 관찰 결과                                                                                                      | 빈도  | 영향              |
| ------------------ | --------------------- | ---------------------------------------------------------------------------------------------------------- | --- | --------------- |
| **E-TIE**          | 동일 `score`            | Redis는 **member 문자열 기준 정렬**; 숫자 ID를 문자열로 넣으면 **사전식 순서가 숫자 대소와 불일치**할 수 있음(`RedisRankingReadRepository` 주석) | F2  | I2·I5(공정성 논쟁 시) |
| **E-COLD-PRODUCT** | 그날 아직 조회·구매 등 이벤트가 없음 | 랭킹에 안 올라가면 목록에도 없고, 상세 순위도 null일 수 있음                                                                      | F3  | I3              |


### 2.6 품절·재고


| ID                   | 조건                   | 관찰 결과                                                                          | 빈도  | 영향                           |
| -------------------- | -------------------- | ------------------------------------------------------------------------------ | --- | ---------------------------- |
| **E-STOCK-NOFILTER** | `stockQuantity == 0` | 랭킹 API는 **재고로 걸러내지 않음** (미삭제·브랜드만 확인). 품절이면 UI에서 품절 표시·구매 비활성으로 알려 줄 수 있음 | F3  | 랭킹과 구매 가능성이 다르게 보일 수 있음(표시로 보완) |
| **E-RESTOCK**        | 재고 증가                | 점수는 조회·좋아요·판매 이벤트에 연동; 재입고만으로 Redis를 **특별히 손댈 필요는 없음**(합의 시 §4) | F2  | I4 |

> 구현 반영(해결): 랭킹 목록 응답 행에 `stockQuantity`를 포함해(품절 표시용) **UI에서 상태를 노출**할 수 있게 했다. 랭킹에서 품절 필터는 하지 않는다.


### 2.7 Redis가 죽었을 때


| ID             | 조건       | 현 구현                                          | 권장 방향(합의)                             | 빈도  | 영향  |
| -------------- | -------- | --------------------------------------------- | ---------------------------------------- | --- | --- |
| **E-FALLBACK** | Redis 다운 | `GET /rankings`: 설정에 따라 DB 최신순 fallback 또는 degraded 빈 목록. 상세 순위는 null | **구조화 로그·메트릭**으로 “진짜 비어 있음”과 **degraded**를 구분하고, 계약상 **DB 기반 fallback 목록**(예: 최신순)을 두는 방안 검토 | F1  | I3  |

> 구현 반영(해결): “그냥 빈 목록”과 “Redis 장애”를 운영에서 구분할 수 있도록 아래를 추가했다.
> - `ranking.degraded` **메트릭**(tag: `operation`)
> - 구조화 로그를 위한 **MDC**(`ranking.degraded`, `ranking.operation`)
> - Redis 장애 시 `dataSource`로 클라이언트/운영 모두 구분 가능(REDIS / FALLBACK_LATEST / DEGRADED)


### 2.8 조회·주문 조작(어뷰징) 가능성


| ID                | 조건                         | 현 구현              | UX·데이터에 미치는 영향                          | 빈도  | 영향  |
| ----------------- | -------------------------- | ----------------- | --------------------------------------- | --- | --- |
| **E-ABUSE-VIEW**  | 동일 상품에 `PRODUCT_VIEWED` 폭주 | 가중치·캡·유저 단위 디듑 없음 | C_{\text{view}} 상승 → score 상승 → ZSET 상승 | F2  | I5  |
| **E-ABUSE-ORDER** | 비정상 결제 이벤트                 | 동일                | C_{\text{sold}} 반영(가장 큰 가중치 0.6)        | F1  | I5  |


**보정 로직이 도입될 경우** 문서에 추가해야 할 항목: 이벤트 드롭 시 `product_metrics`·ZSET 갱신 주기, 제외 시 `rankingRank`/`items`에 나타나는지 여부.

### 2.9 그 밖에 헷갈리기 쉬운 것


| ID                    | 조건                                                  | 관찰 결과                                              |
| --------------------- | --------------------------------------------------- | -------------------------------------------------- |
| **E-DEDUP**           | `event_handled` 유니크로 이벤트 중복 삽입 실패                   | 해당 메시지는 메트릭·랭킹 미갱신(의도적 중복 방지)                      |
| **E-CARRY-RACE**      | Carry-Over와 실시간 ZADD가 동일 member에 경합                 | Carry-Over 주석: 이후 매트릭 기반 ZADD가 **덮어쓰기**로 수렴        |
| **E-RECON-OVERWRITE** | Reconciliation이 `last_event_occurred_at` 일자 키에 ZADD | 과거 일자 키에 대한 **의도하지 않은 키 쓰기** 가능성은 운영·데이터 정합성 검토 대상 |


---

## 3. 정상 시나리오와 예외가 서로 부딪히지 않나


| 질문                                                         | 짧은 답                                                          |
| ---------------------------------------------------------- | ------------------------------------------------------------- |
| Redis가 죽으면 목록이 비는데, “인기 없음”이랑 어떻게 구분하나                     | 지금은 응답이 비슷해 **구분이 어렵다**. **구조화 로그·메트릭·fallback** 등은 §4.2 권장 방향 참고.                           |
| 상세에서 `rankingRank`만 null인데, “순위 없음”이랑 “Redis 오류”를 어떻게 구분하나 | **지금은 구분 안 된다**. 필요하면 응답 `meta` 등을 따로 두는 식으로 설계 변경 검토.        |
| `totalElements`와 화면에 보이는 줄 수가 다른 이유                        | Redis에는 있는데 DB에서 상품을 못 붙인 경우 등으로 **줄이 덜 나올 수 있다**. 문서화된 동작이다. |
| 신규 상품은 어디에 두는 게 좋나                                          | **인기 랭킹과 분리**해 신상 전용 영역(탭·API·블록)을 두는 방향이 자연스럽다. 인기 랭킹에는 이벤트가 쌓여야 오른다.            |


---

## 4. 알고 두면 좋은 한계와 권장 방향 (합의안)

### 4.1 공정성·신상 노출

점수는 조회·좋아요·판매가 **많이 쌓일수록** 오른다. **오늘 막 등록한 상품**은 이벤트가 없으면 인기 랭킹 Redis에 없어 **목록에 안 나올 수 있다**. Carry-Over는 **어제 잘 나갔던 상품**을 오늘 키에 작게 이어 붙이는 용도라, **신상을 넓게 밀어주는 수단은 아니다**.

| 권장 방향 | 설명 |
| --------- | ---- |
| **신상은 전용 영역** | 인기 랭킹과 섞지 않고, **신상 탭 / 신상 전용 API·블록**으로 노출하는 편이 직관적이다. |
| 인기 랭킹 안에서만 조정할 때 | 카테고리·가격대 정규화, Carry-Over N·가중치 튜닝 등은 **보조**로만 검토한다. |

**구현 반영(해결)**  
- 신상 전용 조회 API를 분리했다: `GET /api/v1/products/new-arrivals` (등록 최신순)

### 4.2 Redis 장애(안정성)

Redis에 문제가 있어도 API는 **에러 대신 빈 목록처럼** 돌아올 수 있어, **진짜 인기 없음**과 **장애**를 응답만으로는 구분하기 어렵다.

| 권장 방향 | 설명 |
| --------- | ---- |
| **관측** | 구조화 로그·메트릭(예: `ranking.degraded`, 원인 태그)으로 **정상 빈 목록**과 **degraded**를 구분한다. |
| **Fallback** | 계약을 정해 **DB 기반 대체 목록**(예: 최신 등록순)을 같은 엔드포인트 또는 별도 필드로 내려 줄지 결정한다. Redis 정상 시에는 기존 ZSET 의미를 유지한다. |
| **응답 계약** | 필요 시 `meta`·헤더로 클라이언트가 대체 UI를 쓰게 할 수 있으나 **앱·API 합의**가 필요하다. |

**구현 반영(해결)**  
- Redis 장애 감지 시 `ranking.degraded` 메트릭 + MDC 로깅을 남긴다.  
- 목록은 설정(`app.ranking.fallback-on-redis-failure`)에 따라:
  - `true`: DB 최신 등록순으로 fallback (`dataSource=FALLBACK_LATEST`, `score=0`)  
  - `false`: 빈 목록 degraded (`dataSource=DEGRADED`)  
- 정상 Redis 경로는 `dataSource=REDIS`.

### 4.3 품절·재입고

랭킹 API는 **품절이어도 순위에서 빼지 않는다**(현 구현). 화면에서 **품절 상태**를 보여 주면 사용자는 구매 불가를 인지할 수 있다. 재입고 시 **랭킹 Redis를 따로 건드릴 필요는 없다** — 이벤트·점수 정책과 맞추면 된다.

| 권장 방향 | 설명 |
| --------- | ---- |
| UI | 품절 뱃지·버튼 비활성 등으로 **상태 노출**을 우선한다. |
| 랭킹 데이터 | 품절만으로 ZSET에서 제거·감점하는 **필수는 아니다**(재입고 대비·운영 단순). |

**구현 반영(해결)**  
- 랭킹 목록 응답에 `stockQuantity`를 포함해 UI에서 품절 표시가 가능하다(랭킹 필터링은 하지 않음).

### 4.4 어뷰징

**조회만 많이 쌓아도** 점수가 오를 수 있어, 부하와 순위 왜곡이 동시에 우려된다.

| 권장 방향 | 설명 |
| --------- | ---- |
| **조회 기여 상한** | **유저·세션·디바이스** 단위로 **일일 조회 기여 상한**을 둔다(IP는 보조 신호로 선택). |
| **가중치** | **조회 비중은 낮추고**, **주문·좋아요 비중을 높여** 조작 난이도를 올린다. |
| 그 외 | 게이트웨이 rate limit, 급증 알람·운영 점검 등은 기존과 같이 **선택**으로 둔다. |

**구현 반영(부분 해결)**  
- 스트리머 `PRODUCT_VIEWED` 처리에 조회 기여 상한을 추가했다.  
  - 기준: `(partitionKey, productId, yyyyMMdd)`  
  - 저장: Redis 카운터 키 `collector:view-cap:{day}:{partitionKey}:{productId}`  
  - 설정: `collector.product.view-contribution.daily-cap-per-partition-product` (`0`이면 비활성), `key-ttl-days`
- 상한 초과 시 `view_count`/랭킹 점수 반영을 건너뛰고 `kafka.collector.events.view_capped` 메트릭을 증가시킨다.

**남은 리스크**  
- `partitionKey` 품질(유저/세션/디바이스 전달 정확도)에 따라 방어 강도가 달라질 수 있다.  
- 주문·좋아요 어뷰징(`E-ABUSE-ORDER`)과 가중치 재튜닝은 별도 정책/운영 합의가 필요하다.

### 4.5 Fallback 목록의 “의미 혼동” 리스크

Redis 장애 시 fallback(최신 등록순)은 “인기 랭킹”의 의미와 다르다. 장애 구간에 유저가 본 목록을 “인기”로 오해할 수 있고, 장애 종료 시 화면이 급격히 바뀌어 **신뢰가 떨어질 수 있다**.

| 권장 방향 | 설명 |
| --------- | ---- |
| **명시적 구분** | 응답 `dataSource`(구현) + 헤더 `X-Loopers-Ranking-Data-Source`(구현) 기반으로 UI에 “임시 목록/최신순” 뱃지·문구를 노출해 의미 혼동을 줄인다. |
| **운영 알림** | `ranking.degraded` 메트릭을 알람에 연결해, fallback 빈도가 일정 수준을 넘으면 Redis 장애를 조기 인지한다. |

**구현 반영(해결)**  
- API 응답 본문에 `dataSource`(REDIS / FALLBACK_LATEST / DEGRADED)를 포함해 fallback 여부를 명시한다.  
- 동일 정보를 응답 헤더 `X-Loopers-Ranking-Data-Source`로도 내려, 클라이언트가 degraded UI를 빠르게 분기할 수 있게 했다.


---

## 5. 관련 구현·문서 링크

- 읽기: `RankingQueryService`, `RedisRankingReadRepository`, `RankingV1Controller`  
- 상세 순위: `ProductFacade#withDailyRankingRank`  
- 쓰기·점수: `RankingMetricsRedisSyncService`, `RankingScoreCalculator`, `ProductEventCollectorDatabaseService`  
- 배치: `RankingCarryoverScheduler`, `RankingReconciliationScheduler`  
- 설계: [09-ranking-redis-zset-design.md](./09-ranking-redis-zset-design.md)  
- 리스크·테스트 매핑: [09-ranking-risk-discovery.md](./09-ranking-risk-discovery.md)

